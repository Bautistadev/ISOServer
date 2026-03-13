package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;



import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Contenedor de estado compartido que fluye a través del pipeline de transacción.
 *
 * Actúa como un mapa de clave-valor que todos los participantes pueden leer y escribir.
 * Es el mecanismo de comunicación entre participantes: uno agrega datos al contexto
 * y el siguiente los consume, sin acoplamiento directo entre ellos.
 *
 * Ejemplo de flujo típico:
 *   1. LookupCardParticipant      → ctx.put("card", cardEntity)
 *   2. CheckLimitsParticipant     → CardEntity card = ctx.get("card", CardEntity.class)
 *   3. AuthorizeParticipant       → ctx.setResponse(approvedMsg)
 *   4. BuildResponseParticipant   → ISOMsg resp = ctx.getResponse()
 *
 * Las claves estándar (REQUEST, RESPONSE, etc.) están predefinidas como constantes
 * para evitar errores de tipeo y facilitar el uso.
 *
 * Equivalente a org.jpos.transaction.Context de jPOS.
 */
public class TransactionContext {

    // ── Claves estándar del contexto ─────────────────────────────────────────
    public static final String REQUEST   = "REQUEST";   // ISOMsg de solicitud entrante
    public static final String RESPONSE  = "RESPONSE";  // ISOMsg de respuesta a enviar
    public static final String RESULT    = "RESULT";    // TransactionResult final del pipeline
    public static final String SOURCE    = "SOURCE";    // Origen de la transacción (IP, canal, etc.)
    public static final String TIMESTAMP = "TIMESTAMP"; // Marca de tiempo de inicio

    // Almacén interno de datos del contexto
    // No es thread-safe intencionalmente: un contexto pertenece a una sola transacción/hilo
    private final Map<String, Object> attributes = new HashMap<>();

    // Timestamp de creación para medir tiempos de procesamiento
    private final long createdAt;

    // ID único de la transacción para correlacionar logs de todos los participantes
    private String transactionId;

    public TransactionContext() {
        this.createdAt     = System.currentTimeMillis();
        // ID por defecto basado en timestamp; en producción usar UUID o STAN del mensaje
        this.transactionId = String.valueOf(createdAt);
    }

    /**
     * Constructor con ID explícito.
     * Ej: new TransactionContext(msg.getString(11)) para usar el STAN como ID de traza.
     */
    public TransactionContext(String transactionId) {
        this();
        this.transactionId = transactionId;
    }

    // ── API genérica de atributos ─────────────────────────────────────────────

    /** Guarda un valor en el contexto con la clave indicada. Reemplaza si ya existía. */
    public void put(String key, Object value)   { attributes.put(key, value); }

    /** Retorna el valor crudo (Object) o null si no existe. */
    public Object get(String key)               { return attributes.get(key); }

    /** Verifica si una clave existe en el contexto. */
    public boolean has(String key)              { return attributes.containsKey(key); }

    /** Elimina una clave del contexto. */
    public void remove(String key)              { attributes.remove(key); }

    /**
     * Retorna el valor casteado al tipo indicado, o null si no existe.
     * Lanza ClassCastException si el tipo no coincide.
     * Ej: CardEntity card = ctx.get("card", CardEntity.class);
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object val = attributes.get(key);
        return val == null ? null : type.cast(val);
    }

    /**
     * Versión Optional del getter tipado: útil con streams o para evitar null checks.
     * Ej: ctx.getOptional("card", CardEntity.class).ifPresent(card -> ...)
     */
    public <T> Optional<T> getOptional(String key, Class<T> type) {
        return Optional.ofNullable(get(key, type));
    }

    // ── Helpers específicos para flujo ISO ────────────────────────────────────

    /** Guarda el mensaje de solicitud ISO recibido. Llamado típicamente al inicio del pipeline. */
    public void    setRequest(ISOMsg msg)         { put(REQUEST, msg); }

    /** Retorna el mensaje de solicitud. Todos los participantes pueden leerlo. */
    public ISOMsg  getRequest()                   { return get(REQUEST, ISOMsg.class); }

    /** Guarda el mensaje de respuesta que se enviará de vuelta al origen. */
    public void    setResponse(ISOMsg msg)        { put(RESPONSE, msg); }

    /** Retorna el mensaje de respuesta (construido por algún participante). */
    public ISOMsg  getResponse()                  { return get(RESPONSE, ISOMsg.class); }

    /** Guarda el resultado final del pipeline (para acceso posterior al execute()). */
    public void    setResult(TransactionResult r) { put(RESULT, r); }

    /** Retorna el resultado final del pipeline. */
    public TransactionResult getResult()          { return get(RESULT, TransactionResult.class); }

    // ── Metadata ─────────────────────────────────────────────────────────────

    public String getTransactionId()          { return transactionId; }
    public void   setTransactionId(String id) { this.transactionId = id; }
    public long   getCreatedAt()              { return createdAt; }

    /**
     * Retorna cuántos milisegundos han pasado desde que se creó el contexto.
     * Útil para medir tiempos de procesamiento end-to-end.
     */
    public long getElapsedMs() { return System.currentTimeMillis() - createdAt; }

    @Override
    public String toString() {
        return String.format("TransactionContext[id=%s, elapsedMs=%d, keys=%s]",
                transactionId, getElapsedMs(), attributes.keySet());
    }
}