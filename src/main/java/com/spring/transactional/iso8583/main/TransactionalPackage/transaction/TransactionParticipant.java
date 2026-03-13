package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

/**
 * Contrato que implementa cada paso del pipeline de procesamiento de una transacción.
 *
 * Un pipeline típico de autorización podría tener estos participantes en orden:
 *   1. ValidateFieldsParticipant    → verifica que los campos obligatorios estén presentes
 *   2. LookupCardParticipant        → busca la tarjeta en la base de datos
 *   3. CheckLimitsParticipant       → valida montos y límites de uso
 *   4. AuthorizeParticipant         → aplica la lógica de negocio de autorización
 *   5. PersistTransactionParticipant → guarda la transacción en BD
 *   6. BuildResponseParticipant     → construye el ISOMsg de respuesta
 *
 * El protocolo 2-phase commit garantiza consistencia:
 *   - Si todos aprueban en prepare() → se llama commit() en orden directo
 *   - Si alguno falla  en prepare() → se llama abort() en orden INVERSO sobre los ya aprobados
 *     (esto permite deshacer efectos secundarios, ej: liberar reservas de saldo)
 *
 * Equivalente a org.jpos.transaction.TransactionParticipant de jPOS.
 */
public interface TransactionParticipant {

    /**
     * Fase principal de procesamiento.
     * Recibe el contexto compartido, lo enriquece (agrega datos, toma decisiones)
     * y retorna un resultado indicando qué debe hacer el pipeline.
     *
     * Reglas:
     *   - PREPARED  → todo OK, continuar al siguiente participante
     *   - FINAL     → resultado definitivo (aprobado/declinado), hacer commit y parar
     *   - ABORTED   → error, hacer rollback (abort en inverso) y parar
     *
     * Importante: este método debe ser idempotente si es posible, ya que en casos
     * de fallo del sistema podría ser reejecutado.
     *
     * @param context contexto compartido que fluye a través de todo el pipeline
     * @return el resultado de este participante
     */
    TransactionResult prepare(TransactionContext context);

    /**
     * Fase de confirmación: llamada cuando TODOS los participantes anteriores aprobaron.
     * Aquí se deben hacer los efectos finales e irreversibles:
     *   - Confirmar reserva de saldo en la base de datos
     *   - Publicar evento de transacción aprobada
     *   - Actualizar contadores de límites de uso
     *
     * Implementación por defecto: no hace nada (para participantes de solo-lectura).
     */
    default void commit(TransactionContext context) {
        // No-op por defecto — solo los participantes con efectos secundarios lo implementan
    }

    /**
     * Fase de rollback: llamada en ORDEN INVERSO si algún participante posterior abortó.
     * Aquí se deben deshacer los efectos del prepare():
     *   - Liberar saldo reservado
     *   - Cancelar notificaciones pendientes
     *   - Revertir cambios de estado
     *
     * Implementación por defecto: no hace nada (para participantes sin efectos secundarios).
     */
    default void abort(TransactionContext context) {
        // No-op por defecto — solo los participantes con estado mutable lo implementan
    }

    /**
     * Nombre del participante para logging y trazabilidad.
     * Por defecto usa el nombre simple de la clase (sin paquete).
     * Sobreescribir para nombres más descriptivos en logs.
     */
    default String getName() { return getClass().getSimpleName(); }
}