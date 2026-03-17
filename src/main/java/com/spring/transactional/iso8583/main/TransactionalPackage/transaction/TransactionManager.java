package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

import com.spring.transactional.iso8583.main.TransactionalPackage.channel.Space;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  TRANSACTION MANAGER — Motor del 2-Phase Commit                  ║
 * ║  Equivalente a org.jpos.transaction.TransactionManager de jPOS   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ¿Qué hace?
 *   Lee Context del Space (queue "TXN"), y por cada Context ejecuta
 *   la cadena de TransactionParticipants en dos fases:
 *
 *   FASE 1 — prepare() en orden:
 *     Llama prepare() a cada participante de la cadena activa.
 *     Si alguno devuelve ABORTED → pasa a FASE 2B (abort).
 *     Si todos devuelven PREPARED → pasa a FASE 2A (commit).
 *
 *   FASE 2A — commit() en orden:
 *     Llama commit() a cada participante que no tenga NO_JOIN.
 *
 *   FASE 2B — abort() en ORDEN INVERSO:
 *     Llama abort() a cada participante (en reversa) que no tenga NO_JOIN ni READONLY,
 *     solo hasta el punto donde prepare() ya había llegado.
 *
 * Grupos dinámicos via GroupSelector:
 *   Si un participante implementa GroupSelector, su select() decide qué grupo
 *   de participantes ejecutar a continuación. Esto permite enrutar:
 *     0200 → grupo "compra"
 *     0400 → grupo "anulacion"
 *
 * Sesiones paralelas:
 *   N sesiones corren en paralelo en un ThreadPool. Cada sesión procesa
 *   un Context distinto de forma completamente independiente.
 *
 * Config típica:
 *
 *   TransactionManager tm = new TransactionManager(space, "TXN");
 *   tm.addParticipant("default", new SelectByMTI());       // GroupSelector
 *   tm.addParticipant("compra",  new ValidarTarjeta());
 *   tm.addParticipant("compra",  new Autorizar());
 *   tm.addParticipant("compra",  new ConstruirRespuesta());
 *   tm.addParticipant("compra",  new SendResponse(space));
 *   tm.setSessions(10);
 *   tm.start();
 */
public class TransactionManager implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SLINE = "─".repeat(64);

    // Clave en el Context donde el GroupSelector guarda el grupo seleccionado
    static final String SELECTED_GROUP = "__SELECTED_GROUP__";

    // ── Configuración ─────────────────────────────────────────────────────────

    private final Space space;     // bus del que se leen los Context
    private final String queueKey;  // clave en el Space donde llegan los Context (ej: "TXN")
    private final String name;      // nombre para logging

    private int  sessions      = 4;      // hilos paralelos procesando transacciones
    private long pollTimeoutMs = 2_000L; // timeout de cada take() para chequear running

    // ── Grupos de participantes ───────────────────────────────────────────────

    // Mapa de nombre de grupo → lista ordenada de participantes.
    // "default" es el grupo inicial que siempre se ejecuta primero.
    // Los grupos adicionales se activan por el GroupSelector.
    private final Map<String, List<TransactionParticipant>> groups = new HashMap<>();

    // ── Estado ────────────────────────────────────────────────────────────────

    private final AtomicBoolean running   = new AtomicBoolean(false);
    private final AtomicLong    sessionId = new AtomicLong(0); // ID único por transacción
    private ExecutorService     executor;

    // ── Constructores ─────────────────────────────────────────────────────────

    public TransactionManager(Space space, String queueKey, String name) {
        this.space    = space;
        this.queueKey = queueKey;
        this.name     = name;
    }

    public TransactionManager(Space space, String queueKey) {
        this(space, queueKey, "txn-manager");
    }

    // ── Registro de participantes ─────────────────────────────────────────────

    /**
     * Agrega un participante al grupo especificado.
     *
     * Los participantes se ejecutan en el ORDEN en que se agregan.
     * El grupo "default" siempre se ejecuta primero.
     *
     * @param groupName nombre del grupo (ej: "default", "compra", "anulacion")
     * @param participant participante a agregar al final del grupo
     */
    public void addParticipant(String groupName, TransactionParticipant participant) {
        groups.computeIfAbsent(groupName, k -> new ArrayList<>()).add(participant);
        log.debug("  [{}] Participante '{}' agregado al grupo '{}'",
                name, participant.getClass().getSimpleName(), groupName);
    }

    /** Atajo para agregar al grupo "default". */
    public void addParticipant(TransactionParticipant participant) {
        addParticipant("default", participant);
    }

    // ── Ciclo de vida Spring ──────────────────────────────────────────────────

    @Override
    public void afterPropertiesSet() { start(); }

    @Override
    public void destroy() { stop(); }

    public void start() {
        if (running.compareAndSet(false, true)) {
            // ThreadPool con N sesiones paralelas — cada hilo procesa una transacción
            executor = Executors.newFixedThreadPool(sessions,
                    r -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        t.setName(name + "-session-" + sessionId.incrementAndGet());
                        return t;
                    });

            // Lanzar N sesiones — cada una corre su propio loop de take() → process()
            for (int i = 0; i < sessions; i++) {
                executor.submit(this::sessionLoop);
            }

            log.info("");
            log.info("  ┌{}┐", SLINE);
            log.info("  │  ▶  TRANSACTION MANAGER INICIADO");
            log.info("  │  {}", String.format("%-22s  %s", "Nombre:",    name));
            log.info("  │  {}", String.format("%-22s  %s", "Queue:",     queueKey));
            log.info("  │  {}", String.format("%-22s  %d", "Sesiones:",  sessions));
            log.info("  │  {}", String.format("%-22s  %s", "Grupos:",    groups.keySet()));
            log.info("  │  {}", String.format("%-22s  %s", "Timestamp:", LocalDateTime.now().format(DT_FMT)));
            log.info("  └{}┘", SLINE);
            log.info("");
        }
    }

    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                // Esperar hasta 5s que las sesiones activas terminen limpiamente
                if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                    executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ■  TRANSACTION MANAGER DETENIDO  │  {}", name);
        log.info("  └{}┘", SLINE);
        log.info("");
    }

    // ── sessionLoop() — loop de una sesión paralela ───────────────────────────

    /**
     * Loop de una sesión. Corre en un hilo del pool.
     *
     * Estructura:
     *   while (running) {
     *       ctx = space.take("TXN", 2s)   ← espera una transacción
     *       if (ctx != null) process(ctx)  ← ejecutar 2PC
     *   }
     *
     * N sesiones corren este mismo loop en paralelo → N transacciones
     * se procesan simultáneamente sin interferirse entre sí.
     */
    private void sessionLoop() {
        long id = sessionId.incrementAndGet(); // ID de esta sesión para logging

        log.debug("  [{}] Sesión {} iniciada", name, id);

        while (running.get()) {
            try {
                // Esperar una transacción del Space con timeout
                // El timeout permite chequear running.get() periódicamente
                Object obj = space.take(queueKey, pollTimeoutMs);
                if (obj == null) continue; // timeout normal — re-chequear running

                if (!(obj instanceof Context ctx)) {
                    log.warn("  [{}] Sesión {} recibió objeto inesperado: {}",
                            name, id, obj.getClass().getSimpleName());
                    continue;
                }

                // Procesar la transacción completa (2PC)
                process(id, ctx);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Capturar cualquier excepción inesperada para que la sesión sobreviva
                log.error("  [{}] Sesión {} error inesperado: {}", name, id, e.getMessage(), e);
            }
        }

        log.debug("  [{}] Sesión {} terminada", name, id);
    }

    // ── process() — ejecutar el 2PC completo ─────────────────────────────────

    /**
     * Ejecuta la transacción completa para un Context dado.
     *
     * 1. Determinar la cadena de participantes (grupo "default" siempre primero)
     * 2. FASE 1: prepare() en orden — recolectar resultados
     * 3. Si alguno ABORTED → FASE 2B: abort() en reversa
     * 4. Si todos PREPARED → FASE 2A: commit() en orden
     *
     * @param id  identificador de la sesión (para logging)
     * @param ctx contexto de la transacción
     */
    private void process(long id, Context ctx) {
        log.debug("  [{}] Sesión {} procesando: {}", name, id, ctx);

        // La cadena activa comienza con el grupo "default"
        // El GroupSelector puede cambiarla durante prepare()
        List<TransactionParticipant> chain = buildChain("default");

        // Guardar los resultados de prepare() para saber quién necesita abort()
        // índice → resultado de prepare() (PREPARED/ABORTED + flags)
        int[] results = new int[chain.size()];

        boolean aborted  = false;
        int     abortIdx = -1; // índice donde ocurrió el ABORTED

        // ── FASE 1: prepare() ─────────────────────────────────────────────────
        for (int i = 0; i < chain.size(); i++) {
            TransactionParticipant p = chain.get(i);

            try {
                int result = p.prepare(id, ctx);
                results[i] = result;

                // Si el participante es un GroupSelector, leer el grupo que eligió
                // y expandir la cadena con los participantes de ese grupo
                if (p instanceof GroupSelector gs) {
                    String selected = gs.select(id, ctx);
                    if (selected != null && !selected.isBlank()) {
                        // Expandir la cadena insertando los participantes del grupo seleccionado
                        // después de la posición actual
                        chain = expandChain(chain, i, selected);
                        // Redimensionar el array de resultados para el nuevo tamaño
                        results = java.util.Arrays.copyOf(results, chain.size());
                    }
                }

                // Si devolvió ABORTED → terminar la fase 1 y pasar al abort
                if ((result & ABORTED) == ABORTED) {
                    aborted  = true;
                    abortIdx = i;
                    log.info("  [{}] Sesión {} ABORTED por '{}' en índice {}",
                            name, id, p.getClass().getSimpleName(), i);
                    break;
                }

            } catch (Exception e) {
                // Una excepción en prepare() se trata como ABORTED
                results[i] = ABORTED;
                aborted     = true;
                abortIdx    = i;
                log.error("  [{}] Sesión {} excepción en prepare() de '{}': {}",
                        name, id, p.getClass().getSimpleName(), e.getMessage(), e);
                break;
            }
        }

        // ── FASE 2: commit o abort ─────────────────────────────────────────────
        if (aborted) {
            // FASE 2B: abort() en ORDEN INVERSO hasta el punto del fallo
            runAbort(id, ctx, chain, results, abortIdx);
        } else {
            // FASE 2A: commit() en orden normal
            runCommit(id, ctx, chain, results);
        }

        log.debug("  [{}] Sesión {} completada en {} ms: {}",
                name, id, ctx.getElapsedMs(), ctx);
    }

    // ── runCommit() — FASE 2A ─────────────────────────────────────────────────

    /**
     * Llama commit() a todos los participantes que no tengan NO_JOIN.
     * Se ejecuta en el mismo orden que prepare().
     */
    private void runCommit(long id, Context ctx,
                           List<TransactionParticipant> chain, int[] results) {
        for (int i = 0; i < chain.size(); i++) {
            // Saltar si el participante pidió NO_JOIN
            if ((results[i] & NO_JOIN) == NO_JOIN) continue;

            try {
                chain.get(i).commit(id, ctx);
            } catch (Exception e) {
                // Un error en commit() se loguea pero no interrumpe los demás commits
                log.error("  [{}] Sesión {} error en commit() de '{}': {}",
                        name, id, chain.get(i).getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    // ── runAbort() — FASE 2B ──────────────────────────────────────────────────

    /**
     * Llama abort() en ORDEN INVERSO a los participantes que hicieron prepare() OK
     * y no tienen NO_JOIN ni READONLY.
     *
     * Solo se abortan los que llegaron hasta prepare() — desde índice (abortIdx - 1)
     * hacia atrás (el participante en abortIdx ya devolvió ABORTED, no necesita abort).
     */
    private void runAbort(long id, Context ctx,
                          List<TransactionParticipant> chain, int[] results, int abortIdx) {
        // Recorrer en reversa desde (abortIdx - 1) hasta 0
        for (int i = abortIdx - 1; i >= 0; i--) {
            int result = results[i];

            // Saltar si el participante pidió NO_JOIN o READONLY
            if ((result & NO_JOIN)  == NO_JOIN)  continue;
            if ((result & READONLY) == READONLY) continue;

            try {
                chain.get(i).abort(id, ctx);
            } catch (Exception e) {
                log.error("  [{}] Sesión {} error en abort() de '{}': {}",
                        name, id, chain.get(i).getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    // ── buildChain() — construir cadena inicial ───────────────────────────────

    /**
     * Construye la cadena inicial de participantes para el grupo dado.
     * Devuelve una copia mutable de la lista para poder expandirla
     * cuando el GroupSelector seleccione grupos adicionales.
     */
    private List<TransactionParticipant> buildChain(String groupName) {
        List<TransactionParticipant> group = groups.get(groupName);
        if (group == null || group.isEmpty()) {
            log.warn("  [{}] Grupo '{}' no encontrado o vacío", name, groupName);
            return new ArrayList<>();
        }
        return new ArrayList<>(group); // copia mutable
    }

    // ── expandChain() — insertar grupo seleccionado dinámicamente ────────────

    /**
     * Expande la cadena insertando los participantes de los grupos seleccionados
     * DESPUÉS de la posición actual del GroupSelector.
     *
     * El GroupSelector puede devolver múltiples grupos separados por espacios:
     *   "validacion compra logging" → inserta los tres grupos en secuencia.
     *
     * @param chain    cadena actual de participantes
     * @param afterIdx índice del GroupSelector (los nuevos van después)
     * @param selected nombre(s) de grupo(s) a insertar
     * @return nueva cadena con los grupos expandidos
     */
    private List<TransactionParticipant> expandChain(
            List<TransactionParticipant> chain, int afterIdx, String selected) {

        List<TransactionParticipant> expanded = new ArrayList<>(chain.subList(0, afterIdx + 1));

        // El GroupSelector puede devolver múltiples grupos separados por espacio
        String[] groupNames = selected.trim().split("\\s+");
        for (String groupName : groupNames) {
            List<TransactionParticipant> group = groups.get(groupName);
            if (group != null && !group.isEmpty()) {
                expanded.addAll(group);
                log.debug("  [{}] Grupo '{}' expandido con {} participantes",
                        name, groupName, group.size());
            } else {
                log.warn("  [{}] GroupSelector eligió grupo '{}' que no existe", name, groupName);
            }
        }

        // Agregar los participantes que ya estaban después del GroupSelector
        if (afterIdx + 1 < chain.size()) {
            expanded.addAll(chain.subList(afterIdx + 1, chain.size()));
        }

        return expanded;
    }

    // ── Constantes locales para leer los flags ────────────────────────────────

    // Redefinidas acá para no depender de un import estático de TransactionParticipant
    private static final int ABORTED  = TransactionParticipant.ABORTED;
    private static final int NO_JOIN  = TransactionParticipant.NO_JOIN;
    private static final int READONLY = TransactionParticipant.READONLY;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public void setSessions(int sessions)          { this.sessions = sessions; }
    public void setPollTimeoutMs(long ms)          { this.pollTimeoutMs = ms; }
    public boolean isRunning()                     { return running.get(); }
    public String getName()                        { return name; }
    public Map<String, List<TransactionParticipant>> getGroups() { return groups; }
}