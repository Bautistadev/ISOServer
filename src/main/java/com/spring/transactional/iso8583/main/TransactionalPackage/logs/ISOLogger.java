package com.spring.transactional.iso8583.main.TransactionalPackage.logs;

import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.util.ISOUtils;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ISOLogger {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String DLINE = "═".repeat(64);
    private static final String SLINE = "─".repeat(64);

    private ISOLogger() {}

    // ── Mensaje entrante ──────────────────────────────────────────────────

    public static void logIncoming(Logger log, ISOMsg msg, String source) {
        if (!log.isInfoEnabled()) return;
        log.info("");
        log.info("  ╔{}╗", DLINE);
        log.info("  ║  ◀  MENSAJE RECIBIDO  [ {} ]", source);
        log.info("  ╠{}╣", DLINE);
        logCabecera(log, msg);
        log.info("  ╠{}╣", SLINE);
        logCampos(log, msg);
        log.info("  ╚{}╝", DLINE);
        log.info("");
    }

    // ── Mensaje saliente ──────────────────────────────────────────────────

    public static void logOutgoing(Logger log, ISOMsg msg, String destination) {
        if (!log.isInfoEnabled()) return;
        log.info("");
        log.info("  ╔{}╗", DLINE);
        log.info("  ║  ▶  MENSAJE ENVIADO  [ {} ]", destination);
        log.info("  ╠{}╣", DLINE);
        logCabecera(log, msg);
        log.info("  ╠{}╣", SLINE);
        logCampos(log, msg);
        log.info("  ╚{}╝", DLINE);
        log.info("");
    }

    // ── Resultado ─────────────────────────────────────────────────────────

    public static void logResult(Logger log, ISOMsg request, ISOMsg response, long elapsedMs) {
        if (!log.isInfoEnabled()) return;

        String rc       = response.getString(39);
        boolean ok      = ISOUtils.isApproved(rc);
        String estado   = ok ? "✔  APROBADO" : "✘  DECLINADO";

        log.info("");
        log.info("  ╔{}╗", DLINE);
        log.info("  ║  {}", estado);
        log.info("  ╠{}╣", DLINE);
        log.info("  ║  {}",  fila("MTI Solicitud",   nvl(request.getMTI())));
        log.info("  ║  {}",  fila("MTI Respuesta",   nvl(response.getMTI())));
        log.info("  ║  {}",  fila("STAN",             nvl(request.getString(11))));
        log.info("  ║  {}",  fila("RRN",              nvl(response.getString(37))));
        log.info("  ║  {}",  fila("Auth Code",        nvl(response.getString(38))));
        log.info("  ║  {}",  fila("Response Code",    nvl(rc)));
        log.info("  ║  {}",  fila("Descripción",
                ISOUtils.getResponseCodeDescription(rc)));
        log.info("  ║  {}",  fila("Tiempo respuesta", elapsedMs + " ms"));
        log.info("  ╚{}╝", DLINE);
        log.info("");
    }

    // ── Error ─────────────────────────────────────────────────────────────

    public static void logError(Logger log, String operacion, String mensaje, Throwable causa) {
        log.error("");
        log.error("  ╔{}╗", DLINE);
        log.error("  ║  ✘  ERROR  [ {} ]", operacion);
        log.error("  ╠{}╣", DLINE);
        log.error("  ║  {}", fila("Operación", operacion));
        log.error("  ║  {}", fila("Mensaje",   mensaje));
        if (causa != null)
            log.error("  ║  {}", fila("Causa",
                    causa.getClass().getSimpleName() + ": " + causa.getMessage()));
        log.error("  ╚{}╝", DLINE);
        log.error("");
    }

    // ── Servidor iniciado ─────────────────────────────────────────────────

    public static void logServerStart(Logger log, int port, int threadPoolSize) {
        log.info("");
        log.info("  ╔{}╗", DLINE);
        log.info("  ║  ●  SERVIDOR ISO 8583 INICIADO");
        log.info("  ╠{}╣", DLINE);
        log.info("  ║  {}", fila("Puerto",       ":" + port));
        log.info("  ║  {}", fila("Thread Pool",  threadPoolSize + " hilos"));
        log.info("  ║  {}", fila("Timestamp",    now()));
        log.info("  ║  {}", fila("Transporte",   "TCP — header 4 bytes big-endian"));
        log.info("  ╚{}╝", DLINE);
        log.info("");
    }

    // ── Nueva conexión ────────────────────────────────────────────────────

    public static void logNewConnection(Logger log, int port, String remote) {
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ⟶  NUEVA CONEXIÓN  │  Puerto: {}  │  Origen: {}", port, remote);
        log.info("  └{}┘", SLINE);
    }

    // ── Conexión cerrada ──────────────────────────────────────────────────

    public static void logConnectionClosed(Logger log, int port, String remote, long sessionMs) {
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ✕  CONEXIÓN CERRADA  │  Puerto: {}  │  Origen: {}  │  Duración: {} ms",
                port, remote, sessionMs);
        log.info("  └{}┘", SLINE);
    }

    // ── Helpers internos ──────────────────────────────────────────────────

    private static void logCabecera(Logger log, ISOMsg msg) {
        log.info("  ║  {}", fila("MTI",              nvl(msg.getMTI())));
        log.info("  ║  {}", fila("Tipo",             describeMTI(msg.getMTI())));
        log.info("  ║  {}", fila("Timestamp",        now()));
        log.info("  ║  {}", fila("Campos presentes", String.valueOf(msg.getSetFields().size())));
        log.info("  ║  {}", fila("Bitmap secundario",
                msg.hasSecondaryBitmap() ? "Sí (campos 65-128)" : "No"));
    }

    private static void logCampos(Logger log, ISOMsg msg) {
        Map<Integer, String> nombres = fieldNames();

        log.info("  ║  CAMPOS:");
        // Cabecera de la tabla — construida con String.format
        log.info("  ║  {}", String.format("  %-4s  %-35s  %s", "F#", "Descripción", "Valor"));
        log.info("  ║  {}", SLINE);

        msg.getSetFields().stream().sorted().forEach(fn -> {
            String nombre = nombres.getOrDefault(fn, "Campo " + fn);
            String valor  = formatFieldValue(msg, fn);
            // String.format para alinear columnas correctamente
            log.info("  ║  {}", String.format("  F%-3d  %-35s  %s", fn, nombre, valor));
        });
    }

    /**
     * Construye una fila con label alineado a la izquierda (20 chars) y su valor.
     * Reemplaza el {:<20} de Python que SLF4J no soporta.
     */
    private static String fila(String label, String valor) {
        return String.format("%-22s  %s", label + ":", nvl(valor));
    }

    private static String formatFieldValue(ISOMsg msg, int fn) {
        String valor = msg.getString(fn);
        if (valor == null) return "[null]";
        return switch (fn) {
            case 2  -> ISOUtils.maskPAN(valor);
            case 35 -> maskTrack2(valor);
            case 52 -> "[PIN BLOCK — BINARIO 8 bytes]";
            case 64, 128 -> "[MAC — BINARIO 8 bytes]";
            case 39 -> valor + "  →  " + ISOUtils.getResponseCodeDescription(valor);
            case 3  -> valor + "  →  " + describeProcessingCode(valor);
            case 22 -> valor + "  →  " + describeEntryMode(valor);
            default -> valor.length() > 45 ? valor.substring(0, 42) + "..." : valor;
        };
    }

    private static String maskTrack2(String track2) {
        int sep = track2.indexOf('=');
        if (sep < 0) return ISOUtils.maskPAN(track2);
        return ISOUtils.maskPAN(track2.substring(0, sep)) + track2.substring(sep);
    }

    private static String describeMTI(String mti) {
        if (mti == null) return "Desconocido";
        return switch (mti) {
            case "0100" -> "Solicitud de Autorización";
            case "0110" -> "Respuesta de Autorización";
            case "0200" -> "Solicitud de Compra (Financial Request)";
            case "0210" -> "Respuesta de Compra (Financial Response)";
            case "0220" -> "Aviso de Compra (Financial Advice)";
            case "0400" -> "Solicitud de Reverso";
            case "0410" -> "Respuesta de Reverso";
            case "0800" -> "Gestión de Red (Network Management)";
            case "0810" -> "Respuesta Gestión de Red";
            default     -> "MTI no reconocido";
        };
    }

    private static String describeProcessingCode(String pc) {
        if (pc == null || pc.length() < 2) return "";
        return switch (pc.substring(0, 2)) {
            case "00" -> "Compra";
            case "01" -> "Retiro de efectivo";
            case "09" -> "Compra con cashback";
            case "20" -> "Devolución / Crédito";
            case "28" -> "Pago de servicio";
            case "31" -> "Consulta de saldo";
            case "38" -> "Cambio de PIN";
            default   -> "Código " + pc.substring(0, 2);
        };
    }

    private static String describeEntryMode(String mode) {
        if (mode == null) return "";
        return switch (mode) {
            case "010" -> "Manual";
            case "011" -> "Banda magnética";
            case "051" -> "Chip EMV";
            case "071" -> "Contactless / NFC";
            case "090" -> "Banda magnética (fallback)";
            default    -> "Modo " + mode;
        };
    }

    private static Map<Integer, String> fieldNames() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(2,   "PAN");
        m.put(3,   "Processing Code");
        m.put(4,   "Amount, Transaction");
        m.put(5,   "Amount, Settlement");
        m.put(7,   "Transmission Date & Time");
        m.put(11,  "STAN");
        m.put(12,  "Local Time (hhmmss)");
        m.put(13,  "Local Date (MMDD)");
        m.put(14,  "Expiration Date (YYMM)");
        m.put(18,  "Merchant Category Code");
        m.put(22,  "POS Entry Mode");
        m.put(25,  "POS Condition Code");
        m.put(32,  "Acquiring Institution ID");
        m.put(35,  "Track 2 Data");
        m.put(37,  "Retrieval Reference Number");
        m.put(38,  "Authorization Code");
        m.put(39,  "Response Code");
        m.put(41,  "Terminal ID (TID)");
        m.put(42,  "Merchant ID (MID)");
        m.put(43,  "Merchant Name / Location");
        m.put(48,  "Additional Data Private");
        m.put(49,  "Currency Code");
        m.put(52,  "PIN Data");
        m.put(55,  "ICC Data (EMV)");
        m.put(64,  "MAC Primary");
        m.put(70,  "Network Mgmt Info Code");
        m.put(90,  "Original Data Elements");
        m.put(100, "Receiving Institution ID");
        m.put(102, "Account ID 1");
        m.put(103, "Account ID 2");
        m.put(128, "MAC Secondary");
        return m;
    }

    private static String now() {
        return LocalDateTime.now().format(DT_FMT);
    }

    private static String nvl(String s) {
        return s != null ? s : "[no presente]";
    }
}