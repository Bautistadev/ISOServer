package com.spring.transactional.iso8583.main.TransactionalPackage.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilidades estáticas para construir y manipular mensajes ISO 8583.
 *
 * Centraliza operaciones comunes como:
 *   - Generación de STANs y RRNs
 *   - Manejo de fechas/horas en formato ISO
 *   - Validación y enmascarado de PANs
 *   - Conversión de montos
 *   - Operaciones de bits y padding
 *   - Lookup de códigos de respuesta
 *
 * Clase utilitaria (constructor privado, todos los métodos son static).
 * Equivalente a org.jpos.iso.ISOUtil de jPOS.
 */
public final class ISOUtils {

    // Contador atómico thread-safe para generación de STANs.
    // AtomicInteger.incrementAndGet() es una operación atómica sin necesidad de synchronized.
    private static final AtomicInteger stanCounter = new AtomicInteger(0);

    // Formatters pre-compilados (inmutables y thread-safe en Java 8+)
    private static final DateTimeFormatter TIME_FMT     = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MMddHHmmss");

    /** Constructor privado: esta clase no debe instanciarse. */
    private ISOUtils() {}

    // ── STAN ─────────────────────────────────────────────────────────────────

    /**
     * Genera el próximo STAN (System Trace Audit Number) de 6 dígitos.
     * Se incrementa secuencialmente y vuelve a 0 después de 999999.
     *
     * Thread-safe gracias a AtomicInteger.
     * En producción, considerar persistir el contador en Redis o BD para
     * evitar duplicados si la aplicación se reinicia.
     */
    public static String generateSTAN() {
        // % 1_000_000: mantener siempre en el rango 0-999999
        return String.format("%06d", stanCounter.incrementAndGet() % 1_000_000);
    }

    // ── Fecha/Hora ───────────────────────────────────────────────────────────

    /** Retorna la hora actual en formato hhmmss para el campo 12. Ej: "143022" */
    public static String localTime()            { return LocalDateTime.now().format(TIME_FMT); }

    /** Retorna la fecha actual en formato MMdd para el campo 13. Ej: "0315" */
    public static String localDate()            { return LocalDateTime.now().format(DATE_FMT); }

    /** Retorna fecha+hora en formato MMddhhmmss para el campo 7. Ej: "0315143022" */
    public static String transmissionDateTime() { return LocalDateTime.now().format(DATETIME_FMT); }

    // ── PAN ──────────────────────────────────────────────────────────────────

    /**
     * Enmascara un PAN para logs y pantallas, mostrando solo los primeros 6 y últimos 4 dígitos.
     * El estándar PCI-DSS exige que el PAN completo no aparezca en logs.
     *
     * Ej: "4111111111111111" → "411111******1111"
     *     "374251018720058" (15 dígitos Amex) → "374251*****0058"
     */
    public static String maskPAN(String pan) {
        if (pan == null || pan.length() < 10) return pan; // PAN demasiado corto para enmascarar
        // Primeros 6 (BIN) + asteriscos + últimos 4
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }

    /**
     * Valida un PAN usando el algoritmo de Luhn (también conocido como "Módulo 10").
     * Todos los PANs de tarjetas de pago válidos cumplen con este algoritmo.
     *
     * Algoritmo:
     *   1. Desde el último dígito, alternando: duplicar dígitos en posiciones pares
     *   2. Si el dígito duplicado > 9, restar 9
     *   3. Sumar todos los dígitos
     *   4. El número es válido si la suma es divisible por 10
     */
    public static boolean luhnCheck(String pan) {
        if (pan == null || pan.isBlank()) return false;
        int sum = 0;
        boolean alternate = false; // Alterna entre dígitos que se duplican y los que no
        for (int i = pan.length() - 1; i >= 0; i--) {
            int n = Character.digit(pan.charAt(i), 10);
            if (n < 0) return false; // Carácter no numérico → PAN inválido
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9; // Si duplicar da > 9, sumar los dígitos del resultado
            }
            sum += n;
            alternate = !alternate; // Alternar para el próximo dígito
        }
        return sum % 10 == 0; // Válido si la suma total es múltiplo de 10
    }

    /**
     * Extrae el BIN (Bank Identification Number) de un PAN: los primeros 6 dígitos.
     * El BIN identifica al emisor (banco/red) de la tarjeta y se usa para ruteo.
     * Ej: "411111..." → "411111" (Visa)
     */
    public static String getBIN(String pan) {
        return (pan == null || pan.length() < 6) ? pan : pan.substring(0, 6);
    }

    // ── Monto ────────────────────────────────────────────────────────────────

    /**
     * Convierte un monto en centavos a la representación ISO de 12 dígitos.
     * ISO 8583 envía montos sin punto decimal, siempre en la unidad más pequeña.
     *
     * Ej: $1500.00 → 150000 centavos → "000000150000"
     *     $0.99    → 99 centavos     → "000000000099"
     */
    public static String amountToISO(long amountInCents) {
        return String.format("%012d", amountInCents);
    }

    /**
     * Convierte el campo de monto ISO (12 chars) de vuelta a centavos.
     * Ej: "000000150000" → 150000L (= $1500.00)
     */
    public static long isoToAmount(String isoAmount) {
        return isoAmount == null || isoAmount.isBlank() ? 0L : Long.parseLong(isoAmount.strip());
    }

    // ── Hex ──────────────────────────────────────────────────────────────────

    /**
     * Convierte bytes a String hexadecimal en mayúsculas.
     * Útil para loguear datos binarios (PIN blocks, MACs, datos EMV).
     * Ej: {0x42, 0x10, 0xFF} → "4210FF"
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /**
     * Convierte String hexadecimal a bytes.
     * La longitud del String debe ser par (2 chars por byte).
     * Ej: "4210FF" → {0x42, 0x10, 0xFF}
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0)
            throw new IllegalArgumentException("String hexadecimal inválido: " + hex);
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2)
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return result;
    }

    // ── Padding ──────────────────────────────────────────────────────────────

    /** Rellena a la izquierda con `pad` hasta `length` chars. Ej: zeropad("123", 6) → "000123" */
    public static String padLeft(String s, int length, char pad) {
        if (s == null) s = "";
        if (s.length() >= length) return s.substring(s.length() - length);
        return String.valueOf(pad).repeat(length - s.length()) + s;
    }

    /** Rellena a la derecha con `pad` hasta `length` chars. Ej: padRight("ABC", 8, ' ') → "ABC     " */
    public static String padRight(String s, int length, char pad) {
        if (s == null) s = "";
        if (s.length() >= length) return s.substring(0, length);
        return s + String.valueOf(pad).repeat(length - s.length());
    }

    /** Atajo para padding de ceros a la izquierda (el más frecuente en ISO). */
    public static String zeropad(String s, int length) { return padLeft(s, length, '0'); }

    // ── RRN ──────────────────────────────────────────────────────────────────

    /**
     * Genera un RRN (Retrieval Reference Number) numérico de 12 dígitos.
     * El RRN lo asigna el adquirente y sirve para localizar la transacción
     * en conciliaciones y disputas.
     *
     * En producción: usar una secuencia de BD o UUID truncado para garantizar unicidad real.
     */
    public static String generateRRN() {
        // Módulo 10^12 para obtener siempre 12 dígitos del timestamp en milisegundos
        return String.format("%012d", System.currentTimeMillis() % 1_000_000_000_000L);
    }

    // ── Response Code ─────────────────────────────────────────────────────────

    /** Retorna true si el código de respuesta indica aprobación. */
    public static boolean isApproved(String rc) { return "00".equals(rc); }

    /**
     * Retorna la descripción en español del código de respuesta ISO 8583 (campo 39).
     * Solo incluye los códigos más comunes del estándar y algunos de uso regional.
     *
     * Referencia completa: ISO 8583-1:2003, Anexo A.
     */
    public static String getResponseCodeDescription(String rc) {
        if (rc == null) return "Desconocido";
        return switch (rc) {
            case "00" -> "Aprobado";                            // Transacción exitosa
            case "01" -> "Consulte al emisor";                 // Requiere llamada al emisor
            case "04" -> "Recoger tarjeta";                    // Tarjeta reportada por el emisor
            case "05" -> "No honor";                           // Denegación genérica del emisor
            case "12" -> "Transacción inválida";               // El tipo de transacción no está permitido
            case "13" -> "Monto inválido";                     // Monto cero o fuera de rango
            case "14" -> "Número de tarjeta inválido";         // PAN no existe en el emisor
            case "30" -> "Error de formato";                   // Mensaje mal formado
            case "41" -> "Tarjeta perdida";                    // Tarjeta reportada como perdida
            case "43" -> "Tarjeta robada";                     // Tarjeta reportada como robada
            case "51" -> "Fondos insuficientes";               // Saldo menor al monto solicitado
            case "54" -> "Tarjeta expirada";                   // Fecha de vencimiento pasada
            case "55" -> "PIN incorrecto";                     // PIN no coincide
            case "57" -> "Transacción no permitida";           // No autorizada para este PAN
            case "61" -> "Límite de monto excedido";           // Supera el límite diario/por transacción
            case "65" -> "Límite de frecuencia excedido";      // Demasiadas transacciones en el período
            case "75" -> "Intentos de PIN excedidos";          // Demasiados PINs incorrectos
            case "91" -> "Emisor no disponible";               // Host del emisor sin respuesta
            case "96" -> "Error del sistema";                  // Error técnico interno
            default   -> "Código desconocido: " + rc;
        };
    }
}