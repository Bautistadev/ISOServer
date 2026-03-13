package com.spring.transactional.iso8583.main.TransactionalPackage.packager;


import com.spring.transactional.iso8583.main.TransactionalPackage.exception.ISOException;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOField;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase abstracta base para empaquetar y desempaquetar mensajes ISO 8583.
 *
 * "Empaquetar" = convertir un ISOMsg (objeto Java) en byte[] para enviarlo por red.
 * "Desempaquetar" = parsear un byte[] recibido y reconstruir un ISOMsg.
 *
 * El formato de la trama resultante es:
 *   [MTI 4 chars] + [Bitmap primario 16 hex chars] + [Bitmap secundario 16 hex chars, si aplica]
 *   + [Campo 2 serializado] + [Campo 3 serializado] + ... + [Campo N serializado]
 *
 * Las clases concretas (ej: Generic87Packager) heredan de esta y llaman
 * a addFieldDefinition() para registrar los campos de su esquema específico.
 *
 * Equivalente a org.jpos.iso.ISOPackager de jPOS.
 */
public abstract class ISOPackager {

    // Mapa de definiciones: clave = número de campo, valor = su definición (tipo, longitud, etc.)
    // Se puebla en el constructor de las subclases mediante addFieldDefinition()
    protected final Map<Integer, FieldDefinition> fieldDefs = new HashMap<>();

    /** Registra la definición de un campo. Llamado por subclases en su constructor. */
    public void addFieldDefinition(FieldDefinition def) { fieldDefs.put(def.getFieldNumber(), def); }

    /** Retorna la definición de un campo o null si no está registrado. */
    public FieldDefinition getFieldDefinition(int n)    { return fieldDefs.get(n); }

    // ── Pack ─────────────────────────────────────────────────────────────────

    /**
     * Convierte un ISOMsg en un array de bytes listo para transmitir por TCP.
     *
     * Pasos:
     *   1. Serializa el MTI (4 chars ASCII)
     *   2. Construye el bitmap primario (campos 1-64) como 8 bytes → 16 chars hex
     *      Si hay campos > 64, enciende el bit 1 del bitmap primario para avisar
     *      que viene el bitmap secundario.
     *   3. Si hay campos > 64, serializa el bitmap secundario (otros 16 chars hex)
     *   4. Serializa cada campo presente (en orden ascendente), según su FieldDefinition
     */
    public byte[] pack(ISOMsg msg) throws ISOException {
        if (msg.getMTI() == null)
            throw new ISOException("MTI no puede ser nulo al empaquetar");

        StringBuilder sb = new StringBuilder();

        // Paso 1: MTI (siempre 4 caracteres, ej: "0200")
        sb.append(msg.getMTI());

        // Paso 2: Bitmap primario
        byte[] primaryBitmap = msg.getPrimaryBitmapBytes();
        // Si hay bitmap secundario, el bit más significativo del byte 0 debe estar encendido.
        // Eso corresponde al "campo 1" del ISO, que señala la existencia del bitmap secundario.
        if (msg.hasSecondaryBitmap()) primaryBitmap[0] |= 0x80;
        sb.append(bytesToHex(primaryBitmap)); // Convierte 8 bytes a 16 chars hex

        // Paso 3: Bitmap secundario (solo si hay campos > 64)
        if (msg.hasSecondaryBitmap())
            sb.append(bytesToHex(msg.getSecondaryBitmapBytes()));

        // Paso 4: Serializar cada campo presente (campo 1 es el bitmap, se salta)
        for (int i = 2; i <= 128; i++) {
            if (msg.hasField(i)) {
                FieldDefinition def = fieldDefs.get(i);
                if (def == null)
                    throw new ISOException("Sin definición para campo " + i +
                            " — registrarlo en el packager antes de empaquetar");
                sb.append(packField(msg.getField(i), def));
            }
        }

        // Convierte todo el StringBuilder a bytes usando ISO-8859-1
        // (garantiza 1 char = 1 byte, sin pérdidas para caracteres extendidos)
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Serializa un campo individual según su tipo de dato.
     * El resultado es una String que se añade al StringBuilder de la trama.
     *
     * ALPHA   → padding de espacios a la derecha hasta maxLength
     * NUMERIC → padding de ceros a la izquierda hasta maxLength
     * LLVAR   → "XX" + valor  (XX = longitud real en 2 dígitos)
     * LLLVAR  → "XXX" + valor (XXX = longitud real en 3 dígitos)
     * BINARY  → representación hexadecimal del byte array
     * LLBINARY→ "XX" + hex del byte array
     */
    private String packField(ISOField field, FieldDefinition def) throws ISOException {
        String value = field.getString();
        if (value == null) value = "";

        return switch (def.getType()) {
            case ALPHA    -> padRight(value, def.getMaxLength(), ' ');
            case NUMERIC  -> padLeft(value, def.getMaxLength(), '0');
            // Para LLVAR/LLNUM: el indicador de longitud son 2 dígitos decimales
            case LLVAR, LLNUM   -> String.format("%02d%s", value.length(), value);
            // Para LLLVAR/LLLNUM: el indicador de longitud son 3 dígitos decimales
            case LLLVAR, LLLNUM -> String.format("%03d%s", value.length(), value);
            case BINARY   -> bytesToHex(field.getBytes());
            case LLBINARY -> {
                byte[] b = field.getBytes();
                // Indicador = cantidad de bytes (no de chars hex)
                yield String.format("%02d%s", b.length, bytesToHex(b));
            }
            default -> throw new ISOException("Tipo no soportado para empaquetar: " + def.getType());
        };
    }

    // ── Unpack ───────────────────────────────────────────────────────────────

    /** Sobrecarga para recibir directamente byte[] desde el socket. */
    public ISOMsg unpack(byte[] data) throws ISOException {
        return unpack(new String(data, StandardCharsets.ISO_8859_1));
    }

    /**
     * Parsea una trama String y reconstruye el ISOMsg.
     * El proceso es el inverso exacto del pack():
     *
     *   1. Lee los 4 primeros chars como MTI
     *   2. Lee los siguientes 16 chars como bitmap primario (en hex)
     *   3. Si el bit 1 está encendido, lee otros 16 chars como bitmap secundario
     *   4. Itera los bits del bitmap: por cada bit activo (del 2 al 128),
     *      parsea el campo correspondiente según su FieldDefinition
     *
     * El "offset" rastrea la posición actual de lectura dentro de la trama.
     */
    public ISOMsg unpack(String raw) throws ISOException {
        if (raw == null || raw.length() < 20)
            throw new ISOException("Mensaje demasiado corto: longitud=" +
                    (raw != null ? raw.length() : 0));

        ISOMsg msg = new ISOMsg();
        int offset = 0;

        // Paso 1: MTI — primeros 4 caracteres
        msg.setMTI(raw.substring(0, 4));
        offset += 4;

        // Paso 2: Bitmap primario — 16 chars hex = 8 bytes = 64 bits
        // hexToBytes convierte "4210001000000000" → byte[]{0x42, 0x10, ...}
        // bytesToBitSet convierte esos bytes en un BitSet de 64 posiciones
        BitSet bitmap = bytesToBitSet(hexToBytes(raw.substring(offset, offset + 16)));
        offset += 16;

        // Paso 3: Si bit 0 está encendido → hay bitmap secundario (campos 65-128)
        if (bitmap.get(0)) {
            BitSet secondary = bytesToBitSet(hexToBytes(raw.substring(offset, offset + 16)));
            // Los bits del bitmap secundario se mapean a posiciones 64-127 del bitmap principal
            for (int i = 0; i < 64; i++)
                if (secondary.get(i)) bitmap.set(64 + i);
            offset += 16;
        }

        // Paso 4: Leer cada campo cuyos bits están activos en el bitmap
        // i va de 1 a 127 (índice base-0), lo que corresponde a campos 2-128
        // (el campo 1 es el bitmap secundario, no un dato real)
        for (int i = 1; i < 128; i++) {
            int fieldNumber = i + 1; // Convertir índice bitmap (0-based) → número campo (1-based)
            if (bitmap.get(i)) {
                FieldDefinition def = fieldDefs.get(fieldNumber);
                if (def == null)
                    throw new ISOException("Sin definición para campo " + fieldNumber +
                            " que está presente en la trama");
                // consumed[0] se usa como "parámetro de salida" para saber cuántos
                // caracteres consumió la lectura de este campo, y avanzar el offset
                int[] consumed = {0};
                msg.set(fieldNumber, unpackField(raw, offset, def, consumed));
                offset += consumed[0]; // Avanzar el cursor al siguiente campo
            }
        }
        return msg;
    }

    /**
     * Deserializa un campo individual desde la posición `offset` de la trama.
     * Escribe en consumed[0] cuántos caracteres de la trama consumió este campo,
     * para que el llamador pueda avanzar el cursor correctamente.
     */
    private String unpackField(String raw, int offset, FieldDefinition def, int[] consumed)
            throws ISOException {
        return switch (def.getType()) {
            case ALPHA, NUMERIC -> {
                // Longitud fija: leer exactamente maxLength caracteres
                consumed[0] = def.getMaxLength();
                yield raw.substring(offset, offset + def.getMaxLength());
            }
            case LLVAR, LLNUM -> {
                // Leer 2 dígitos de longitud, luego esa cantidad de caracteres
                int len = Integer.parseInt(raw.substring(offset, offset + 2));
                consumed[0] = 2 + len; // 2 del indicador + len del valor
                yield raw.substring(offset + 2, offset + 2 + len);
            }
            case LLLVAR, LLLNUM -> {
                // Leer 3 dígitos de longitud, luego esa cantidad de caracteres
                int len = Integer.parseInt(raw.substring(offset, offset + 3));
                consumed[0] = 3 + len; // 3 del indicador + len del valor
                yield raw.substring(offset + 3, offset + 3 + len);
            }
            case BINARY -> {
                // Longitud fija en bytes: cada byte ocupa 2 chars hex en la trama
                consumed[0] = def.getMaxLength() * 2;
                yield raw.substring(offset, offset + def.getMaxLength() * 2);
            }
            case LLBINARY -> {
                // 2 dígitos indican cantidad de BYTES, luego esa cantidad * 2 chars hex
                int len = Integer.parseInt(raw.substring(offset, offset + 2));
                consumed[0] = 2 + len * 2;
                yield raw.substring(offset + 2, offset + 2 + len * 2);
            }
            default -> throw new ISOException("Tipo no soportado para desempaquetar: " + def.getType());
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Rellena con el carácter pad a la IZQUIERDA hasta alcanzar la longitud deseada.
     * Si el String ya es más largo, toma los últimos `length` caracteres (trunca por izquierda).
     * Usado para NUMERIC: "123" con length=6 → "000123"
     */
    protected String padLeft(String s, int length, char pad) {
        if (s.length() >= length) return s.substring(s.length() - length);
        return String.valueOf(pad).repeat(length - s.length()) + s;
    }

    /**
     * Rellena con el carácter pad a la DERECHA hasta alcanzar la longitud deseada.
     * Si el String es más largo, trunca por la derecha.
     * Usado para ALPHA: "TERM1" con length=8 → "TERM1   "
     */
    protected String padRight(String s, int length, char pad) {
        if (s.length() >= length) return s.substring(0, length);
        return s + String.valueOf(pad).repeat(length - s.length());
    }

    /**
     * Convierte un byte array a String hexadecimal en mayúsculas.
     * Cada byte se representa con exactamente 2 dígitos hex.
     * Ej: {0x42, 0x10, 0x00} → "421000"
     */
    protected String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02X", b));
        return hex.toString();
    }

    /**
     * Convierte una cadena hexadecimal a byte array.
     * Ej: "421000" → {0x42, 0x10, 0x00}
     * La longitud del String siempre debe ser par (2 chars por byte).
     */
    protected byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2)
            data[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }

    /**
     * Convierte un byte array a un BitSet donde cada bit representa un campo ISO.
     *
     * El bit más significativo del primer byte (0x80) → bit 0 del BitSet = campo 1.
     * Ej: byte 0x42 = 0100 0010 → bits 1 y 6 activos → campos 2 y 7 presentes.
     *
     * La máscara 0x80 >> (i % 8) va desplazando el bit testigo de izquierda a derecha:
     *   i=0: 0x80=10000000, i=1: 0x40=01000000, ..., i=7: 0x01=00000001
     */
    protected BitSet bytesToBitSet(byte[] bytes) {
        BitSet bs = new BitSet(bytes.length * 8);
        for (int i = 0; i < bytes.length * 8; i++)
            if ((bytes[i / 8] & (0x80 >> (i % 8))) != 0) bs.set(i);
        return bs;
    }
}