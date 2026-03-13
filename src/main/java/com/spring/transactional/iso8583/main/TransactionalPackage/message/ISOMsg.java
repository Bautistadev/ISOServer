package com.spring.transactional.iso8583.main.TransactionalPackage.message;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Representa un mensaje ISO 8583 completo, listo para ser procesado o transmitido.
 *
 * La estructura de un mensaje ISO 8583 es:
 *   [MTI] + [Bitmap primario] + [Bitmap secundario opcional] + [Campos]
 *
 * - MTI (Message Type Indicator): 4 dígitos que identifican el tipo de mensaje.
 *   Ej: 0200 = solicitud de autorización, 0210 = respuesta de autorización.
 *
 * - Bitmap: campo de 64 bits (primario) o 128 bits (primario + secundario).
 *   Cada bit indica si el campo correspondiente está presente en el mensaje.
 *   Bit 1 encendido = hay bitmap secundario.
 *
 * - Campos: hasta 128 campos numerados, cada uno con su tipo y longitud.
 *
 * Equivalente a org.jpos.iso.ISOMsg de jPOS, sin dependencias externas.
 */
public class ISOMsg implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    // Constantes para indicar la dirección del mensaje en el canal
    public static final int INCOMING = 0; // Mensaje recibido desde la red
    public static final int OUTGOING = 1; // Mensaje a enviar por la red

    // MTI: Message Type Indicator — identifica el tipo y versión del mensaje
    // Formato: 4 dígitos decimales. Ej: "0200", "0210", "0800"
    private String mti;

    // Mapa de campos: clave = número de campo (1-128), valor = ISOField con su contenido
    // Se usa HashMap para acceso O(1) por número de campo
    private final Map<Integer, ISOField> fields;

    // Bitmap de 128 bits: cada posición indica si ese campo está presente
    // BitSet de Java maneja esto eficientemente (internamente son longs)
    // Bit 0 del BitSet = campo 1 del ISO (hay un offset de -1)
    private final BitSet bitmap;

    // Indica si el mensaje fue recibido (INCOMING) o se va a enviar (OUTGOING)
    private int direction;

    // Marca de tiempo de creación del mensaje, útil para medir tiempos de respuesta
    private long timestamp;

    // Metadata extra del mensaje (header de transporte, IP origen, etc.)
    // No forma parte del estándar ISO, pero es útil para trazabilidad interna
    private Map<String, Object> header;

    /** Constructor base: inicializa el mensaje vacío sin MTI ni campos. */
    public ISOMsg() {
        this.fields    = new HashMap<>();
        this.bitmap    = new BitSet(128);
        this.direction = INCOMING;
        this.timestamp = System.currentTimeMillis();
        this.header    = new HashMap<>();
    }

    /**
     * Constructor con MTI.
     * Uso típico: new ISOMsg("0200") para crear una solicitud de autorización.
     */
    public ISOMsg(String mti) {
        this();
        this.mti = mti;
    }

    // ── MTI ──────────────────────────────────────────────────────────────────

    public String getMTI() { return mti; }

    /**
     * Asigna el MTI validando que tenga exactamente 4 caracteres.
     * El formato es: [versión ISO][clase][función][origen].
     * Ej: 0200 → versión 0 (1987), clase 2 (financiero), función 0 (solicitud), origen 0 (adquirente)
     */
    public void setMTI(String mti) {
        if (mti == null || mti.length() != 4)
            throw new IllegalArgumentException("MTI debe tener exactamente 4 caracteres: " + mti);
        this.mti = mti;
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    /**
     * Agrega o reemplaza un campo de texto en el mensaje.
     * Al setear un campo, se enciende el bit correspondiente en el bitmap.
     * El bitmap usa índice base-0, pero los campos ISO son base-1, por eso fieldNumber - 1.
     */
    public void set(int fieldNumber, String value) {
        validateFieldNumber(fieldNumber);
        if (value != null) {
            fields.put(fieldNumber, new ISOField(fieldNumber, value));
            bitmap.set(fieldNumber - 1); // Bit 0 = campo 1, bit 1 = campo 2, etc.
        }
    }

    /**
     * Agrega o reemplaza un campo binario (byte[]) en el mensaje.
     * Usado para campos como PIN Block (F52) o MAC (F64/F128).
     */
    public void set(int fieldNumber, byte[] value) {
        validateFieldNumber(fieldNumber);
        if (value != null) {
            fields.put(fieldNumber, new ISOField(fieldNumber, value));
            bitmap.set(fieldNumber - 1);
        }
    }

    /**
     * Agrega un ISOField ya construido al mensaje.
     * Útil para pasar campos entre mensajes sin recrearlos.
     * Ej: copiar el PAN de la solicitud a la respuesta.
     */
    public void set(ISOField field) {
        if (field != null) {
            validateFieldNumber(field.getFieldNumber());
            fields.put(field.getFieldNumber(), field);
            bitmap.set(field.getFieldNumber() - 1);
        }
    }

    /**
     * Elimina un campo del mensaje y apaga su bit en el bitmap.
     * Si el campo no existía, no hace nada (operación idempotente).
     */
    public void unset(int fieldNumber) {
        fields.remove(fieldNumber);
        bitmap.clear(fieldNumber - 1);
    }

    /**
     * Elimina múltiples campos de una sola vez.
     * Ej: msg.unset(52, 64) para eliminar PIN y MAC antes de loguear.
     */
    public void unset(int... fieldNumbers) {
        for (int fn : fieldNumbers) unset(fn);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** Retorna el ISOField completo o null si el campo no está presente. */
    public ISOField getField(int fieldNumber)  { return fields.get(fieldNumber); }

    /**
     * Retorna el valor del campo como String, o null si no existe.
     * Es el getter más usado en la práctica cotidiana.
     * Ej: String pan = msg.getString(2);
     */
    public String getString(int fieldNumber) {
        ISOField f = fields.get(fieldNumber);
        return f != null ? f.getString() : null;
    }

    /**
     * Retorna el valor del campo como byte[], o null si no existe.
     * Usado principalmente para campos binarios (PIN, MAC, datos EMV).
     */
    public byte[] getBytes(int fieldNumber) {
        ISOField f = fields.get(fieldNumber);
        return f != null ? f.getBytes() : null;
    }

    /**
     * Verifica si un campo está presente en el mensaje.
     * Comprueba AMBAS condiciones: que exista en el mapa Y que su bit esté encendido.
     * Esto garantiza consistencia entre el bitmap y los datos reales.
     */
    public boolean hasField(int fieldNumber) {
        return fields.containsKey(fieldNumber) && bitmap.get(fieldNumber - 1);
    }

    /**
     * Verifica que TODOS los campos indicados estén presentes.
     * Útil para validar que un mensaje tiene todos los campos obligatorios.
     * Ej: if (!msg.hasFields(2, 3, 4, 11, 41)) throw new ISOException("Faltan campos");
     */
    public boolean hasFields(int... fieldNumbers) {
        for (int fn : fieldNumbers) if (!hasField(fn)) return false;
        return true;
    }

    // ── Bitmap ───────────────────────────────────────────────────────────────

    /** Retorna una copia del bitmap (para no exponer el interno mutable). */
    public BitSet getBitmap() { return (BitSet) bitmap.clone(); }

    /**
     * Convierte los campos 1-64 del bitmap a 8 bytes para transmisión.
     * El formato en red es big-endian: el bit más significativo del primer byte
     * corresponde al campo 1, y así sucesivamente.
     */
    public byte[] getPrimaryBitmapBytes()   { return bitmapToBytes(bitmap, 0, 64); }

    /**
     * Convierte los campos 65-128 del bitmap a 8 bytes.
     * Solo se incluye en la trama si hay al menos un campo > 64 presente.
     */
    public byte[] getSecondaryBitmapBytes() { return bitmapToBytes(bitmap, 64, 128); }

    /**
     * Indica si el mensaje usa el bitmap secundario (campos 65-128).
     * nextSetBit(64) retorna el índice del primer bit encendido desde la posición 64.
     * Si ese índice es >= 64, entonces hay campos del bitmap secundario activos.
     */
    public boolean hasSecondaryBitmap() { return bitmap.nextSetBit(64) >= 64; }

    /**
     * Convierte una porción del BitSet a un array de 8 bytes.
     * Para cada bit activo en el rango [from, to), enciende el bit
     * correspondiente en el byte de resultado usando OR con máscara.
     *
     * @param bs    el bitmap completo
     * @param from  inicio del rango (0 para primario, 64 para secundario)
     * @param to    fin del rango (64 para primario, 128 para secundario)
     */
    private byte[] bitmapToBytes(BitSet bs, int from, int to) {
        byte[] result = new byte[8];
        for (int i = from; i < to && i < bs.size(); i++) {
            if (bs.get(i)) {
                // (i - from) / 8  → índice del byte en el resultado
                // (i - from) % 8  → posición del bit dentro del byte (0=más significativo)
                result[(i - from) / 8] |= (byte) (0x80 >> ((i - from) % 8));
            }
        }
        return result;
    }

    // ── Dirección / Metadata ─────────────────────────────────────────────────

    public int  getDirection()              { return direction; }
    public void setDirection(int direction) { this.direction = direction; }
    public long getTimestamp()              { return timestamp; }
    public void setTimestamp(long ts)       { this.timestamp = ts; }

    /**
     * Determina si el mensaje es una solicitud según el tercer dígito del MTI.
     * Por convención ISO 8583:
     *   - 0 = solicitud (request)
     *   - 1 = respuesta (response)
     *   - 2 = aviso/advice (solicitud sin esperar respuesta)
     *   - 3 = respuesta a aviso
     *   - 4 = notificación
     *   - 5 = respuesta a notificación
     * Ej: 02[0]0 → solicitud, 02[1]0 → respuesta
     */
    public boolean isRequest() {
        if (mti == null || mti.length() < 3) return false;
        char c = mti.charAt(2); // Tercer dígito del MTI
        return c == '0' || c == '2' || c == '4';
    }

    public boolean isResponse() { return !isRequest(); }

    // Getters/setters del mapa de header (metadata de transporte, no campos ISO)
    public Map<String, Object> getHeader()            { return header; }
    public void   setHeader(String key, Object value) { header.put(key, value); }
    public Object getHeader(String key)               { return header.get(key); }

    // ── Utilidades ───────────────────────────────────────────────────────────

    /** Retorna el conjunto de números de campos presentes en el mensaje. */
    public Set<Integer> getSetFields() { return fields.keySet(); }

    /** Retorna el número de campo más alto presente. Útil para saber si hay campos > 64. */
    public int getMaxField() {
        return fields.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * Genera el MTI de respuesta a partir del MTI de solicitud.
     * El tercer dígito se incrementa en 1 para convertir solicitud → respuesta.
     * Ej: 0200 → 0210, 0100 → 0110, 0420 → 0430
     */
    public String toResponseMTI() {
        if (mti == null || mti.length() != 4) return mti;
        char[] chars = mti.toCharArray();
        chars[2] = (char)(chars[2] + 1); // Incrementa tercer dígito: '0' → '1'
        return new String(chars);
    }

    /**
     * Crea un mensaje de respuesta preconfigurado a partir de esta solicitud.
     * Copia automáticamente los campos de "eco" (los que el emisor debe devolver
     * igual que los recibió para que el adquirente pueda correlacionar la respuesta).
     *
     * Campos de eco estándar:
     *   2=PAN, 3=Processing Code, 4=Monto, 7=Fecha/hora, 11=STAN,
     *   12=Hora local, 13=Fecha local, 37=RRN, 41=Terminal, 42=Merchant, 49=Moneda
     */
    public ISOMsg createResponse() {
        ISOMsg response = new ISOMsg(toResponseMTI());
        response.setDirection(OUTGOING); // La respuesta va hacia afuera
        // Campos estándar que siempre se copian de solicitud a respuesta
        int[] common = {2, 3, 4, 5, 6, 7, 11, 12, 13, 15, 18, 22, 25, 32, 37, 41, 42, 43, 49};
        for (int f : common) {
            if (hasField(f)) response.set(getField(f));
        }
        return response;
    }

    @Override
    public ISOMsg clone() {
        try { return (ISOMsg) super.clone(); }
        catch (CloneNotSupportedException e) { throw new RuntimeException(e); }
    }

    /**
     * Valida que el número de campo esté dentro del rango permitido por ISO 8583.
     * El estándar define 128 campos como máximo (64 primarios + 64 secundarios).
     */
    private void validateFieldNumber(int n) {
        if (n < 1 || n > 128)
            throw new IllegalArgumentException("Campo inválido: " + n + ". Debe estar entre 1 y 128.");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ISOMsg{\n  MTI=").append(mti).append("\n");
        fields.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("  ").append(e.getValue()).append("\n"));
        return sb.append("}").toString();
    }
}