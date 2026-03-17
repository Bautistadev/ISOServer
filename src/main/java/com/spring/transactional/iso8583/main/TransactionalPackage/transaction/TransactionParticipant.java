package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  TRANSACTION PARTICIPANT — Interfaz base del 2-Phase Commit      ║
 * ║  Equivalente a org.jpos.transaction.TransactionParticipant       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ¿Qué es?
 *   La unidad mínima de trabajo dentro de una transacción.
 *   Cada paso del procesamiento (validar tarjeta, autorizar, loguear, etc.)
 *   es un TransactionParticipant distinto.
 *
 * ¿Cómo funciona el 2-Phase Commit?
 *
 *   FASE 1 — prepare():
 *     El TransactionManager llama prepare() a cada participante en orden.
 *     Cada uno lee/escribe en el Context y devuelve:
 *       PREPARED  → "estoy listo, podés commitear"
 *       ABORTED   → "encontré un error, abortá todo"
 *
 *     Si todos devuelven PREPARED → se ejecuta la FASE 2 con commit().
 *     Si alguno devuelve ABORTED  → se ejecuta abort() en reversa para todos
 *     los que ya habían hecho PREPARED.
 *
 *   FASE 2A — commit():
 *     Solo se llama si TODOS los participantes devolvieron PREPARED.
 *     Es el momento de confirmar cambios permanentes (BD, notificaciones, etc.)
 *     Se llama en el MISMO ORDEN que prepare().
 *
 *   FASE 2B — abort():
 *     Se llama si algún participante devolvió ABORTED o si prepare() lanzó excepción.
 *     Se llama en ORDEN INVERSO para los participantes que ya hicieron prepare() OK.
 *     Es el "rollback" — deshacer lo que se hizo en prepare().
 *
 * Ejemplo de cadena para un 0200:
 *
 *   1. ValidarCamposObligatorios  → prepare() verifica campos 2,3,4,11,49
 *   2. ValidarTarjeta             → prepare() consulta si la tarjeta existe y no está bloqueada
 *   3. ReservarSaldo              → prepare() reserva el monto en la cuenta
 *   4. RegistrarTransaccion       → prepare() graba en la BD
 *   5. ConstruirRespuesta         → prepare() arma el ISOMsg de respuesta con RC=00
 *
 *   Si el paso 3 falla (saldo insuficiente):
 *     → abort() de paso 2: nada que revertir (solo leyó)
 *     → abort() de paso 1: nada que revertir (solo leyó)
 *
 * Flags de resultado (combinables con OR bit a bit):
 *
 *   PREPARED  = 0  →  listo para commit
 *   ABORTED   = 1  →  error, abortar
 *   NO_JOIN   = 16 →  no llamar a commit/abort de este participante
 *   READONLY  = 32 →  no llamar a abort (solo leyó datos, nada que revertir)
 *
 * Ejemplo de uso de flags combinados:
 *   return PREPARED | READONLY;  // listo, pero no necesita abort
 *   return PREPARED | NO_JOIN;   // listo, pero no quiere recibir commit ni abort
 */
public interface TransactionParticipant {

    // ── Flags de resultado de prepare() ──────────────────────────────────────

    /** El participante está listo. La transacción puede continuar. */
    int PREPARED = 0;

    /** El participante encontró un error. La transacción debe abortarse. */
    int ABORTED  = 1;

    /**
     * No llamar a commit() ni abort() de este participante.
     * Útil para participantes que solo leen datos y no tienen nada que confirmar.
     * Se combina con OR: return PREPARED | NO_JOIN
     */
    int NO_JOIN  = 16;

    /**
     * No llamar a abort() de este participante.
     * Útil para participantes que solo leen — no hicieron cambios que revertir.
     * Se combina con OR: return PREPARED | READONLY
     */
    int READONLY = 32;

    // ── Métodos del 2PC ───────────────────────────────────────────────────────

    /**
     * FASE 1 — Preparar la transacción.
     *
     * Leer datos del Context, ejecutar la lógica del paso, y escribir los
     * resultados en el Context para que los participantes siguientes los usen.
     *
     * Devolver:
     *   PREPARED             → todo OK, listo para commit
     *   PREPARED | READONLY  → todo OK, pero no necesita abort si algo falla después
     *   PREPARED | NO_JOIN   → todo OK, pero no quiere commit ni abort
     *   ABORTED              → error, abortar y ejecutar abort() en reversa
     *
     * ⚠ No hacer cambios permanentes acá (no commitear a BD, no enviar mensajes).
     *   Los cambios permanentes van en commit(). prepare() debe ser reversible.
     *   Excepción: si el participante es READONLY, puede leer BD sin problema.
     *
     * @param id      identificador único de la sesión (para logging)
     * @param context mapa compartido de datos de la transacción
     * @return combinación de flags PREPARED/ABORTED/NO_JOIN/READONLY
     */
    int prepare(long id, Context context);

    /**
     * FASE 2A — Confirmar la transacción.
     *
     * Solo se llama si TODOS los participantes devolvieron PREPARED en prepare().
     * Acá se hacen los cambios permanentes: commitear a BD, enviar notificaciones,
     * publicar eventos, etc.
     *
     * Default: no hace nada. Sobreescribir solo si el participante necesita commit.
     *
     * @param id      identificador único de la sesión
     * @param context mapa compartido de datos de la transacción
     */
    default void commit(long id, Context context) {
        // No-op por defecto — sobreescribir si el participante hace cambios permanentes
    }

    /**
     * FASE 2B — Revertir la transacción.
     *
     * Solo se llama si algún participante devolvió ABORTED.
     * Se llama en ORDEN INVERSO para revertir lo que hizo prepare().
     *
     * Default: no hace nada. Sobreescribir si prepare() hizo cambios reversibles.
     *
     * @param id      identificador único de la sesión
     * @param context mapa compartido de datos de la transacción
     */
    default void abort(long id, Context context) {
        // No-op por defecto — sobreescribir si prepare() hizo cambios que revertir
    }
}