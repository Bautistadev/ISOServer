package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  CONTEXT — Contenedor de datos de una transacción               ║
 * ║  Equivalente a org.jpos.transaction.Context de jPOS             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ¿Qué es?
 *   Un Map tipado que viaja por toda la cadena de TransactionParticipants.
 *   Cada participante puede leer datos que puso el anterior y dejar datos
 *   para los siguientes — sin que los participantes se llamen entre sí.
 *
 * ¿Por qué un Map y no un objeto tipado?
 *   Flexibilidad: cada participante puede agregar los datos que necesite
 *   sin modificar la clase Context. El contrato son las claves String,
 *   no los campos de la clase.
 *
 * Claves estándar (constantes en esta clase):
 *   REQUEST  → el ISOMsg recibido del cliente/terminal
 *   RESPONSE → el ISOMsg a enviar de vuelta (lo construyen los participantes)
 *   SOURCE   → String con el STAN + MTI para que SendResponse lo devuelva al Space
 *   RESULT   → resultado final de la transacción ("APPROVED", "REJECTED", etc.)
 *
 * Ejemplo de uso:
 *   ctx.put(Context.REQUEST,  isoMsg);
 *   ctx.put(Context.RESPONSE, responseMsg);
 *   ctx.put("ACCOUNT_DATA",   accountInfo);   // clave custom del dominio
 *
 *   ISOMsg req = ctx.get(Context.REQUEST);
 */
public class Context {

    // ── Claves estándar ───────────────────────────────────────────────────────

    /** ISOMsg recibido del cliente. Lo pone el SpaceRequestListener antes de encolar. */
    public static final String REQUEST  = "REQUEST";

    /** ISOMsg a enviar al cliente. Lo construyen los participantes de negocio. */
    public static final String RESPONSE = "RESPONSE";

    /**
     * Clave de correlación para devolver la respuesta al Space.
     * Formato: "RESP-" + MTI + "-" + STAN  →  ej: "RESP-0200-000042"
     * Lo usa SendResponse para hacer space.put(SOURCE, RESPONSE).
     */
    public static final String SOURCE   = "SOURCE";

    /** Resultado legible de la transacción. Ej: "APPROVED", "REJECTED", "ERROR". */
    public static final String RESULT   = "RESULT";

    /** Timestamp de inicio de la transacción en milisegundos (System.currentTimeMillis). */
    public static final String START_TIME = "START_TIME";

    // ── Almacenamiento interno ────────────────────────────────────────────────

    // ConcurrentHashMap: los participantes pueden correr en hilos distintos
    // (aunque el TM los corre en secuencia, algunas implementaciones los paralelizan)
    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public Context() {
        // Registrar timestamp de creación automáticamente
        data.put(START_TIME, System.currentTimeMillis());
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Almacena un valor bajo la clave dada.
     * Si la clave ya existe, sobreescribe el valor anterior.
     */
    public void put(String key, Object value) {
        if (value == null) {
            data.remove(key); // null equivale a borrar la clave
        } else {
            data.put(key, value);
        }
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Obtiene el valor para la clave dada.
     * Devuelve null si la clave no existe.
     *
     * El cast al tipo esperado es responsabilidad del llamador.
     * Usar los métodos tipados getMsg(), getString(), getLong() para mayor seguridad.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * Obtiene el valor o devuelve defaultValue si la clave no existe.
     * Útil para valores opcionales con fallback.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }

    // ── Métodos tipados para las claves más comunes ───────────────────────────

    /** Obtiene el REQUEST como ISOMsg. Null si no fue seteado. */
    public ISOMsg getRequest() {
        return get(REQUEST);
    }

    /** Obtiene el RESPONSE como ISOMsg. Null si todavía no fue construido. */
    public ISOMsg getResponse() {
        return get(RESPONSE);
    }

    /** Obtiene la clave de correlación SOURCE. */
    public String getSource() {
        return get(SOURCE);
    }

    /** Obtiene un valor String. Null si no existe o no es String. */
    public String getString(String key) {
        Object v = data.get(key);
        return v instanceof String s ? s : (v != null ? v.toString() : null);
    }

    /** Obtiene un valor Long. Null si no existe. */
    public Long getLong(String key) {
        Object v = data.get(key);
        return v instanceof Long l ? l : null;
    }

    /** true si la clave existe y tiene un valor no null. */
    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    /** Elimina una clave del contexto. */
    public void remove(String key) {
        data.remove(key);
    }

    /**
     * Tiempo transcurrido desde la creación del contexto en milisegundos.
     * Útil para medir la duración total de la transacción en el último participante.
     */
    public long getElapsedMs() {
        Long start = getLong(START_TIME);
        return start != null ? System.currentTimeMillis() - start : -1;
    }

    @Override
    public String toString() {
        ISOMsg req  = getRequest();
        ISOMsg resp = getResponse();
        return String.format("Context[MTI=%s STAN=%s RESULT=%s elapsed=%dms]",
                req  != null ? req.getMTI()  : "?",
                req  != null && req.hasField(11) ? req.getString(11) : "?",
                getString(RESULT),
                getElapsedMs());
    }
}