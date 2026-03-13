package com.spring.transactional.iso8583.main.TransactionalPackage.channel;


import com.spring.transactional.iso8583.main.TransactionalPackage.exception.ISOException;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.FieldDefinition;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.ISOPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Canal TCP para mensajes ISO 8583, con soporte para múltiples estrategias de framing.
 *
 * La estrategia de framing se inyecta en el constructor y determina cómo se
 * delimitan los mensajes en el stream TCP. Ver {@link FramingStrategy} para opciones.
 *
 * Uso básico:
 *   // Con header de 2 bytes (más común en producción):
 *   ISOChannel channel = new ISOChannel("host", 8583, packager, FramingStrategy.HEADER_2);
 *
 *   // Sin header (para clientes legacy/jPOS raw):
 *   ISOChannel channel = new ISOChannel("host", 8583, packager, FramingStrategy.RAW);
 *
 *   // Con header de 4 bytes (default anterior):
 *   ISOChannel channel = new ISOChannel("host", 8583, packager, FramingStrategy.HEADER_4);
 */
public class ISOChannel implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ISOChannel.class);

    private final ISOPackager packager;
    private final FramingStrategy framing; // Estrategia de delimitación de mensajes

    private Socket socket;
    private DataInputStream  in;
    private DataOutputStream out;
    private volatile boolean connected = false;

    private String host;
    private int port;
    private int readTimeoutMs = 30_000;

    // ── Constructores ─────────────────────────────────────────────────────────

    /**
     * Constructor mínimo sin host/puerto (para uso como servidor con attach()).
     * Usa HEADER_2 como estrategia por defecto (la más común en producción).
     */
    public ISOChannel(ISOPackager packager) {
        this(packager, FramingStrategy.HEADER_2);
    }

    /** Constructor mínimo con estrategia explícita. */
    public ISOChannel(ISOPackager packager, FramingStrategy framing) {
        this.packager = packager;
        this.framing  = framing;
    }

    /** Constructor cliente con host/puerto. Usa HEADER_2 por defecto. */
    public ISOChannel(String host, int port, ISOPackager packager) {
        this(host, port, packager, FramingStrategy.HEADER_2);
    }

    /** Constructor cliente completo con estrategia explícita. */
    public ISOChannel(String host, int port, ISOPackager packager, FramingStrategy framing) {
        this.host     = host;
        this.port     = port;
        this.packager = packager;
        this.framing  = framing;
    }

    // ── Conexión ─────────────────────────────────────────────────────────────

    public void connect() throws IOException { connect(host, port); }

    public void connect(String host, int port) throws IOException {
        log.info("[Channel] Conectando a {}:{} con framing={}", host, port, framing);
        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(readTimeoutMs);
        this.in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.connected = true;
    }

    /** Vincula un socket ya aceptado por el servidor. Usado por ISOServer. */
    public void attach(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(readTimeoutMs);
        this.in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.connected = true;
        log.debug("[Channel] Attached framing={}", framing);
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    /**
     * Serializa y envía un ISOMsg aplicando el framing configurado.
     *
     * HEADER_4: [4 bytes longitud big-endian][cuerpo]
     * HEADER_2: [2 bytes longitud big-endian][cuerpo]
     * RAW:      [cuerpo] (sin ningún prefijo)
     */
    public synchronized void send(ISOMsg msg) throws IOException, ISOException {
        assertConnected();
        byte[] data = packager.pack(msg);

        switch (framing) {

            case HEADER_4 -> {
                // Escribir 4 bytes big-endian con la longitud
                // DataOutputStream.writeInt() ya escribe big-endian
                out.writeInt(data.length);
            }

            case HEADER_2 -> {
                // Escribir 2 bytes big-endian con la longitud
                // Limitado a 65535 bytes, más que suficiente para ISO 8583
                if (data.length > 65535)
                    throw new ISOException("Mensaje demasiado largo para HEADER_2: " + data.length + " bytes");
                out.write((data.length >> 8) & 0xFF); // Byte alto (bits 8-15)
                out.write(data.length & 0xFF);         // Byte bajo (bits 0-7)
            }

            case RAW -> {
                // Sin header: enviar solo el cuerpo del mensaje
                // El receptor debe parsear el mensaje directamente desde el MTI
            }
        }

        out.write(data);
        out.flush();
        log.debug("[Channel/{}] Enviados {} bytes MTI={}", framing, data.length, msg.getMTI());
    }

    // ── Receive ──────────────────────────────────────────────────────────────

    /**
     * Lee un mensaje completo del stream aplicando el framing configurado.
     *
     * HEADER_4/HEADER_2: lee el header de longitud, luego exactamente esa cantidad de bytes.
     * RAW: parsea el stream directamente campo por campo usando las definiciones del packager.
     */
    public ISOMsg receive() throws IOException, ISOException {
        assertConnected();
        try {
            return switch (framing) {
                case HEADER_4 -> receiveWithHeader4();
                case HEADER_2 -> receiveWithHeader2();
                case RAW      -> receiveRaw();
            };
        } catch (SocketException se) {
            // Socket cerrado por el otro extremo → marcar como desconectado
            connected = false;
            throw se;
        }
    }

    // ── Implementaciones de receive por estrategia ────────────────────────────

    /**
     * Lee un mensaje con header de 4 bytes.
     * readInt() lee exactamente 4 bytes y los interpreta como int big-endian.
     */
    private ISOMsg receiveWithHeader4() throws IOException, ISOException {
        int length = in.readInt(); // 4 bytes → int big-endian

        if (length <= 0 || length > 65535)
            throw new ISOException("Longitud inválida (HEADER_4): " + length);

        byte[] data = new byte[length];
        in.readFully(data); // Garantiza leer exactamente `length` bytes
        log.debug("[Channel/HEADER_4] Recibidos {} bytes", length);
        return packager.unpack(data);
    }

    /**
     * Lee un mensaje con header de 2 bytes.
     * Reconstruye el int manualmente desde los 2 bytes leídos.
     */
    private ISOMsg receiveWithHeader2() throws IOException, ISOException {
        int hi = in.read(); // Byte alto
        int lo = in.read(); // Byte bajo
        if (hi == -1 || lo == -1)
            throw new IOException("Conexión cerrada al leer header HEADER_2");

        int length = (hi << 8) | lo; // Reconstruir longitud: big-endian 2 bytes → int

        if (length <= 0 || length > 65535)
            throw new ISOException("Longitud inválida (HEADER_2): " + length);

        byte[] data = new byte[length];
        in.readFully(data);
        log.debug("[Channel/HEADER_2] Recibidos {} bytes", length);
        return packager.unpack(data);
    }

    /**
     * Lee un mensaje sin header de longitud (modo RAW).
     *
     * Como no hay prefijo de longitud, el receptor debe parsear el stream
     * incrementalmente sabiendo el formato exacto de cada campo:
     *
     *   1. Leer 4 bytes fijos → MTI
     *   2. Leer 16 bytes fijos → bitmap primario (en hex ASCII)
     *   3. Si bit 1 activo: leer 16 bytes fijos → bitmap secundario
     *   4. Para cada bit activo del 2 al 128: leer el campo según su FieldDefinition
     *
     * Al final, reconstruye la trama completa como String y la pasa al packager
     * para que genere el ISOMsg de forma unificada (sin duplicar lógica de parseo).
     */
    private ISOMsg receiveRaw() throws IOException, ISOException {
        StringBuilder raw = new StringBuilder();

        // ── Paso 1: MTI — siempre 4 bytes ASCII fijos ──────────────────────
        byte[] mtiBytes = new byte[4];
        in.readFully(mtiBytes);
        String mti = new String(mtiBytes, StandardCharsets.ISO_8859_1);
        raw.append(mti);

        // ── Paso 2: Bitmap primario — siempre 16 chars hex ASCII ───────────
        // (8 bytes binarios representados como 16 caracteres hexadecimales)
        byte[] primaryHexBytes = new byte[16];
        in.readFully(primaryHexBytes);
        String primaryHex = new String(primaryHexBytes, StandardCharsets.ISO_8859_1);
        raw.append(primaryHex);

        // Convertir hex → bytes → BitSet para evaluar qué campos están presentes
        byte[] primaryRaw = hexToBytes(primaryHex);

        // ── Paso 3: Bitmap secundario (si bit 0 del bitmap primario está activo) ─
        // Bit 0 del BitSet = bit más significativo del primer byte = campo 1 del ISO
        // = flag que indica la presencia del bitmap secundario
        boolean hasSecondary = (primaryRaw[0] & 0x80) != 0;
        String secondaryHex  = "";

        if (hasSecondary) {
            byte[] secondaryHexBytes = new byte[16];
            in.readFully(secondaryHexBytes);
            secondaryHex = new String(secondaryHexBytes, StandardCharsets.ISO_8859_1);
            raw.append(secondaryHex);
        }

        // ── Reconstruir el BitSet completo (primario + secundario) ──────────
        BitSet bitmap = bytesToBitSet(primaryRaw);
        if (hasSecondary) {
            BitSet secondary = bytesToBitSet(hexToBytes(secondaryHex));
            // Mapear los 64 bits del bitmap secundario a las posiciones 64-127 del bitmap total
            for (int i = 0; i < 64; i++)
                if (secondary.get(i)) bitmap.set(64 + i);
        }

        // ── Paso 4: Leer cada campo presente según el bitmap ────────────────
        // Bit i del BitSet (base-0) = campo i+1 del ISO (base-1)
        // El campo 1 es el bitmap secundario (ya leído), empezar desde el bit 1 = campo 2
        for (int i = 1; i < 128; i++) {
            int fieldNumber = i + 1;
            if (bitmap.get(i)) {
                FieldDefinition def = packager.getFieldDefinition(fieldNumber);
                if (def == null)
                    throw new ISOException("RAW: sin definición para campo " + fieldNumber
                            + " presente en la trama");
                // Leer del stream exactamente los bytes de este campo y añadirlos al buffer
                raw.append(readFieldFromStream(def));
            }
        }

        log.debug("[Channel/RAW] Trama leída: {} chars, MTI={}", raw.length(), mti);

        // Delegar el parseo final al packager (única fuente de verdad del formato)
        return packager.unpack(raw.toString());
    }

    /**
     * Lee del stream de entrada exactamente los bytes correspondientes a un campo,
     * según su tipo y longitud definidos en el FieldDefinition.
     *
     * Retorna el fragmento de trama tal como debe aparecer en el String que
     * luego se pasa a packager.unpack() — incluyendo el indicador de longitud
     * para campos variables (LLVAR, LLLVAR, etc.).
     *
     * @param def definición del campo a leer
     * @return fragmento de trama del campo (indicador de longitud + valor si aplica)
     */
    private String readFieldFromStream(FieldDefinition def) throws IOException, ISOException {
        return switch (def.getType()) {

            case ALPHA, NUMERIC -> {
                // Longitud fija: leer exactamente maxLength bytes
                byte[] data = new byte[def.getMaxLength()];
                in.readFully(data);
                yield new String(data, StandardCharsets.ISO_8859_1);
            }

            case LLVAR, LLNUM -> {
                // Leer 2 bytes ASCII del indicador de longitud ("09", "37", etc.)
                byte[] lenBytes = new byte[2];
                in.readFully(lenBytes);
                String lenStr = new String(lenBytes, StandardCharsets.ISO_8859_1);
                int len = Integer.parseInt(lenStr); // Convertir "09" → 9

                // Leer exactamente `len` bytes del valor
                byte[] data = new byte[len];
                in.readFully(data);
                // Retornar indicador + valor (el packager necesita ambos)
                yield lenStr + new String(data, StandardCharsets.ISO_8859_1);
            }

            case LLLVAR, LLLNUM -> {
                // Leer 3 bytes ASCII del indicador de longitud ("009", "999", etc.)
                byte[] lenBytes = new byte[3];
                in.readFully(lenBytes);
                String lenStr = new String(lenBytes, StandardCharsets.ISO_8859_1);
                int len = Integer.parseInt(lenStr);

                byte[] data = new byte[len];
                in.readFully(data);
                yield lenStr + new String(data, StandardCharsets.ISO_8859_1);
            }

            case BINARY -> {
                // Binario fijo: cada byte se representa como 2 chars hex en la trama
                // → leer maxLength * 2 bytes ASCII
                byte[] data = new byte[def.getMaxLength() * 2];
                in.readFully(data);
                yield new String(data, StandardCharsets.ISO_8859_1);
            }

            case LLBINARY -> {
                // 2 bytes de indicador (cantidad de BYTES, no de chars hex)
                byte[] lenBytes = new byte[2];
                in.readFully(lenBytes);
                String lenStr = new String(lenBytes, StandardCharsets.ISO_8859_1);
                int len = Integer.parseInt(lenStr); // len = cantidad de bytes binarios

                // Cada byte binario ocupa 2 chars hex en la trama
                byte[] data = new byte[len * 2];
                in.readFully(data);
                yield lenStr + new String(data, StandardCharsets.ISO_8859_1);
            }

            default -> throw new ISOException(
                    "RAW: tipo de campo no soportado en lectura incremental: " + def.getType());
        };
    }

    // ── Helpers internos ─────────────────────────────────────────────────────

    /**
     * Convierte String hexadecimal a byte array.
     * Ej: "421000" → {0x42, 0x10, 0x00}
     */
    private byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2)
            data[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }

    /**
     * Convierte byte array a BitSet donde bit 0 = bit más significativo del byte 0.
     * Ej: 0x80 = 10000000 → bit 0 activo
     *     0x40 = 01000000 → bit 1 activo
     */
    private BitSet bytesToBitSet(byte[] bytes) {
        BitSet bs = new BitSet(bytes.length * 8);
        for (int i = 0; i < bytes.length * 8; i++)
            if ((bytes[i / 8] & (0x80 >> (i % 8))) != 0) bs.set(i);
        return bs;
    }

    // ── Estado y Config ───────────────────────────────────────────────────────

    public boolean isConnected() { return connected && socket != null && !socket.isClosed(); }

    private void assertConnected() throws IOException {
        if (!isConnected())
            throw new IOException("Canal no conectado [" + host + ":" + port + "] framing=" + framing);
    }

    public void setReadTimeoutMs(int ms) { this.readTimeoutMs = ms; }
    public String getHost()              { return host; }
    public int getPort()                 { return port; }
    public FramingStrategy getFraming()  { return framing; }

    @Override
    public void close() {
        connected = false;
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException e) { log.warn("[Channel/{}] Error al cerrar: {}", framing, e.getMessage()); }
    }
}