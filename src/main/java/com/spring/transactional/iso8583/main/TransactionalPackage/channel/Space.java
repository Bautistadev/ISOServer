package com.spring.transactional.iso8583.main.TransactionalPackage.channel;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bus central de mensajes estilo jPOS Q2/Space.
 *
 * En jPOS, el Space es un Map<String, BlockingQueue<Object>> compartido
 * por todos los componentes del sistema. Los componentes no se llaman
 * entre sí directamente — publican mensajes con una clave y otros
 * componentes los consumen de forma bloqueante.
 *
 * Funcionamiento:
 *
 *   Productor (ej: ChannelAdaptor recibe un ISOMsg del canal):
 *     space.put("0210-000042", msg);   // clave = MTI + STAN
 *
 *   Consumidor (ej: ISOMUX esperando la respuesta):
 *     ISOMsg resp = space.take("0210-000042", 30_000); // bloqueante con timeout
 *
 * Cada clave tiene su propia BlockingQueue, lo que permite que múltiples
 * hilos esperen respuestas distintas en paralelo sin bloquearse entre sí.
 *
 * Equivalente a: org.jpos.space.TSpace (implementación in-memory de jPOS)
 */
@Slf4j
public class Space {

   // private static final Logger log = LoggerFactory.getLogger(Space.class);

    private final String name;

    /**
     * Mapa de clave → cola de mensajes.
     *
     * ConcurrentHashMap para acceso thread-safe sin bloquear todo el mapa.
     * LinkedBlockingQueue por clave para bloqueo eficiente en take().
     *
     * Las colas se crean lazy (computeIfAbsent) al primer put() o take()
     * con esa clave, igual que hace TSpace en jPOS.
     */
    private final ConcurrentHashMap<String, BlockingQueue<Object>> entries =
            new ConcurrentHashMap<>();

    public Space(String name) {
        this.name = name;
    }

    /** Crea un Space con nombre por defecto. */
    public Space() {
        this("default-space");
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Publica un objeto en el Space bajo la clave dada.
     *
     * Si ya hay objetos en la cola de esa clave, el nuevo se encola detrás
     * (FIFO). El primer take() con esa clave consumirá el objeto más antiguo.
     *
     * @param key  clave de correlación (ej: "0210-000042", "IN-MSG", etc.)
     * @param value objeto a publicar (ISOMsg, String, evento, etc.)
     */
    public void put(String key, Object value) {
        entries.computeIfAbsent(key, k -> new LinkedBlockingQueue<>()).add(value);
        log.debug("[Space/{}] put key='{}' (queue size={})",
                name, key, entries.get(key).size());
    }

    // ── Lectura bloqueante ────────────────────────────────────────────────────

    /**
     * Consume el primer objeto disponible para la clave dada.
     *
     * Bloquea indefinidamente hasta que alguien haga put() con esa clave.
     * Equivalente a TSpace.in(key) de jPOS.
     *
     * Usar con precaución — si nadie publica en esa clave, el hilo queda
     * bloqueado para siempre. Preferir la variante con timeout.
     *
     * @param key clave a consumir
     * @return el objeto publicado (nunca null)
     */
    public Object take(String key) throws InterruptedException {
        Object value = entries.computeIfAbsent(key, k -> new LinkedBlockingQueue<>())
                .take();
        cleanIfEmpty(key);
        log.debug("[Space/{}] take key='{}'", name, key);
        return value;
    }

    /**
     * Consume el primer objeto disponible para la clave dada, con timeout.
     *
     * Equivalente a TSpace.in(key, timeout) de jPOS.
     * Es el método preferido para uso en producción — evita hilos colgados.
     *
     * @param key       clave a consumir
     * @param timeoutMs tiempo máximo de espera en milisegundos
     * @return el objeto publicado, o null si se agotó el timeout
     */
    public Object take(String key, long timeoutMs) throws InterruptedException {
        Object value = entries.computeIfAbsent(key, k -> new LinkedBlockingQueue<>())
                .poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (value != null) {
            cleanIfEmpty(key);
            log.debug("[Space/{}] take key='{}' (ok)", name, key);
        } else {
            log.debug("[Space/{}] take key='{}' (timeout después de {} ms)", name, key, timeoutMs);
        }
        return value;
    }

    // ── Lectura no destructiva ────────────────────────────────────────────────

    /**
     * Lee el primer objeto de la cola sin consumirlo (peek).
     *
     * Equivalente a TSpace.rd(key) de jPOS.
     * Útil para inspección o diagnóstico, no para flujos normales.
     *
     * @param key clave a inspeccionar
     * @return el objeto en cabeza de cola, o null si la cola está vacía
     */
    public Object peek(String key) {
        BlockingQueue<Object> queue = entries.get(key);
        return queue != null ? queue.peek() : null;
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    /**
     * Devuelve cuántos objetos hay pendientes para una clave.
     * Útil para monitoreo y diagnóstico.
     */
    public int size(String key) {
        BlockingQueue<Object> queue = entries.get(key);
        return queue != null ? queue.size() : 0;
    }

    /** Devuelve true si hay al menos un objeto disponible para la clave. */
    public boolean hasEntry(String key) {
        return size(key) > 0;
    }

    /** Descarta todos los objetos de una clave. */
    public void remove(String key) {
        entries.remove(key);
        log.debug("[Space/{}] removed key='{}'", name, key);
    }

    /** Descarta todos los objetos de todas las claves. */
    public void clear() {
        entries.clear();
        log.info("[Space/{}] limpiado completamente", name);
    }

    public String getName() { return name; }

    @Override
    public String toString() {
        return "Space[" + name + "] keys=" + entries.keySet();
    }

    // ── Helpers internos ──────────────────────────────────────────────────────

    /**
     * Elimina la cola del mapa si quedó vacía después de un take().
     * Evita acumulación de entradas muertas en el ConcurrentHashMap.
     */
    private void cleanIfEmpty(String key) {
        entries.computeIfPresent(key, (k, q) -> q.isEmpty() ? null : q);
    }
}