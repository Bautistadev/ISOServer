package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquestador del pipeline de transacciones ISO 8583.
 *
 * Gestiona la ejecución secuencial de TransactionParticipants implementando
 * el protocolo 2-phase commit simplificado:
 *
 *   FASE PREPARE (en orden):
 *     → Cada participante recibe el contexto y retorna PREPARED, FINAL o ABORTED.
 *     → Si retorna PREPARED: se agrega a la lista de "ya preparados" y se continúa.
 *     → Si retorna FINAL: commit de todos los preparados hasta ese punto, se retorna.
 *     → Si retorna ABORTED: abort en orden inverso de todos los ya preparados, se retorna.
 *
 *   Si todos terminan en PREPARED sin FINAL ni ABORTED:
 *     → Se hace commit de todos.
 *
 * Ejemplo de armado del pipeline en Spring:
 *   @Bean
 *   public TransactionManager tm() {
 *       TransactionManager tm = new TransactionManager();
 *       tm.addParticipant(new ValidateFieldsParticipant());
 *       tm.addParticipant(new AuthorizeParticipant());
 *       tm.addParticipant(new PersistParticipant());
 *       return tm;
 *   }
 *
 * Equivalente a org.jpos.transaction.TransactionManager de jPOS.
 */
@Service("isoTransactionManager")
public class TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);

    // Lista ordenada de participantes — el orden importa: se ejecutan de izquierda a derecha
    private final List<TransactionParticipant> participants = new ArrayList<>();

    /**
     * Agrega un participante al final del pipeline.
     * El orden de adición determina el orden de ejecución.
     */
    public void addParticipant(TransactionParticipant participant) {
        participants.add(participant);
    }

    /**
     * Ejecuta el pipeline completo sobre el contexto dado.
     *
     * @param context el contexto que fluirá por todos los participantes
     * @return el TransactionResult final (FINAL o ABORTED)
     */
    public TransactionResult execute(TransactionContext context) {
        log.info("[TM] Iniciando transacción id={}", context.getTransactionId());
        long start = System.currentTimeMillis();

        // Lista de participantes que completaron prepare() exitosamente.
        // Se necesita para saber a quiénes llamar en commit() o abort().
        List<TransactionParticipant> prepared = new ArrayList<>();

        // Resultado por defecto si todos terminan PREPARED sin un FINAL explícito
        TransactionResult finalResult = TransactionResult.prepared();

        // ── Fase Prepare ─────────────────────────────────────────────────────
        for (TransactionParticipant participant : participants) {
            log.debug("[TM] Ejecutando: {}", participant.getName());
            TransactionResult result;

            try {
                result = participant.prepare(context);
            } catch (Exception ex) {
                // Si un participante lanza excepción inesperada, se trata como ABORTED
                // para no dejar el pipeline en estado inconsistente
                log.error("[TM] Excepción en {}: {}", participant.getName(), ex.getMessage(), ex);
                result = TransactionResult.aborted("Error inesperado: " + ex.getMessage());
            }

            if (result.isPrepared()) {
                // Participante OK: agregarlo a la lista y seguir al siguiente
                prepared.add(participant);

            } else if (result.isFinal()) {
                // Resultado definitivo (aprobado o declinado): hacer commit y terminar.
                // Se agrega este participante a "prepared" porque su prepare() completó.
                prepared.add(participant);
                finalResult = result;
                context.setResult(finalResult);
                commitAll(prepared, context); // Confirmar todos los efectos hasta aquí
                log.info("[TM] FINAL por {} en {}ms RC={}",
                        participant.getName(), System.currentTimeMillis() - start, result.getResponseCode());
                return finalResult;

            } else {
                // ABORTED: error de sistema o validación crítica fallida.
                // Hacer rollback en orden inverso de todos los ya preparados.
                finalResult = result;
                context.setResult(finalResult);
                log.warn("[TM] ABORTADO por {} RC={} msg={}",
                        participant.getName(), result.getResponseCode(), result.getMessage());
                abortAll(prepared, context); // Rollback en orden inverso
                return finalResult;
            }
        }

        // Si se recorrieron todos los participantes sin FINAL ni ABORTED,
        // el pipeline terminó OK → commit de todos
        commitAll(prepared, context);
        context.setResult(finalResult);
        log.info("[TM] COMPLETADO en {}ms RC={}",
                System.currentTimeMillis() - start, finalResult.getResponseCode());
        return finalResult;
    }

    /**
     * Llama a commit() en todos los participantes preparados, en orden directo.
     * Los errores en commit se loguean pero NO detienen el proceso
     * (ya no se puede revertir a este punto).
     */
    private void commitAll(List<TransactionParticipant> prepared, TransactionContext context) {
        for (TransactionParticipant p : prepared) {
            try { p.commit(context); }
            catch (Exception ex) {
                log.error("[TM] Error en commit de {}: {}", p.getName(), ex.getMessage());
            }
        }
    }

    /**
     * Llama a abort() en todos los participantes preparados, en ORDEN INVERSO.
     * El orden inverso garantiza que los efectos se deshagan en el orden correcto.
     * Ej: si A reservó saldo y B lo bloqueó, primero se desbloquea (B.abort) y luego se libera (A.abort).
     */
    private void abortAll(List<TransactionParticipant> prepared, TransactionContext context) {
        for (int i = prepared.size() - 1; i >= 0; i--) {
            try { prepared.get(i).abort(context); }
            catch (Exception ex) {
                log.error("[TM] Error en abort de {}: {}", prepared.get(i).getName(), ex.getMessage());
            }
        }
    }

    /** Retorna copia inmutable de la lista de participantes (para inspección). */
    public List<TransactionParticipant> getParticipants() { return List.copyOf(participants); }

    public int getParticipantCount() { return participants.size(); }
}