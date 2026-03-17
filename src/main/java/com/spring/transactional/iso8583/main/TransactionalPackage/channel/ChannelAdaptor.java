package com.spring.transactional.iso8583.main.TransactionalPackage.channel;

import com.spring.transactional.iso8583.main.TransactionalPackage.exception.ISOException;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adaptador de canal estilo jPOS ChannelAdaptor.
 *
 * En jPOS, el ChannelAdaptor es un QBean (componente del Q2 container) que:
 *   1. Mantiene la conexión TCP con un servidor remoto.
 *   2. Corre un hilo de lectura dedicado en loop bloqueante.
 *   3. Cada mensaje recibido lo publica en el Space bajo una clave configurable.
 *   4. Si la conexión cae, reconecta automáticamente con backoff.
 *
 * La clave de publicación en el Space se construye como:
 *   inKey + MTI + "-" + STAN    →  ej: "0210-000042"
 *
 * Esto permite que el ISOMUX correlacione la respuesta con el request original
 * usando el mismo STAN (campo 11 del ISO 8583).
 *
 * Uso típico con Spring Boot:
 *
 *   Space space = new Space("pagos");
 *
 *   ChannelAdaptor adaptor = new ChannelAdaptor(
 *       "10.0.0.1", 8583, packager, space
 *   );
 *   adaptor.setInKey("ISO-RESP-");   // prefijo para respuestas en el Space
 *   adaptor.setOutKey("ISO-REQ");    // clave desde donde leer requests salientes
 *   adaptor.start();                 // arranca el hilo de lectura + reconnect
 *
 * Equivalente a: org.jpos.iso.channel.ChannelAdaptor de jPOS
 */
@Slf4j
public class ChannelAdaptor implements InitializingBean, DisposableBean, Runnable {

   // private static final Logger log = LoggerFactory.getLogger(ChannelAdaptor.class);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SLINE = "─".repeat(64);

    // ── Config ────────────────────────────────────────────────────────────────

    private final String host;
    private final int    port;
    private final ISOChannel channel;
    private final Space  space;

    /** Prefijo de clave en el Space donde se publican los mensajes RECIBIDOS. */
    private String inKey  = "ISO-RESP-";

    /**
     * Clave en el Space desde donde se leen mensajes SALIENTES (opcional).
     * Si es null, el adaptador solo actúa como receptor (modo pasivo).
     * El ISOMUX publica aquí; el adaptador consume y envía por el canal.
     */
    private String outKey = null;

    private long reconnectDelayMs = 5_000L;
    private int  maxReconnectAttempts = Integer.MAX_VALUE; // reintentar indefinidamente
    private int  readTimeoutMs  = 0;   // 0 = sin timeout, bloqueante puro
    private String name;

    // ── Estado interno ────────────────────────────────────────────────────────

    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final AtomicBoolean connected  = new AtomicBoolean(false);
    private Thread readerThread;
    private Thread writerThread;

    // ── Constructores ─────────────────────────────────────────────────────────

    public ChannelAdaptor(String host, int port, ISOChannel channel, Space space) {
        this.host    = host;
        this.port    = port;
        this.channel = channel;
        this.space   = space;
        this.name    = "adaptor-" + host + ":" + port;
    }

    // ── Ciclo de vida Spring ──────────────────────────────────────────────────

    @Override
    public void afterPropertiesSet() throws Exception { start(); }

    @Override
    public void destroy() { stop(); }

    // ── Control ───────────────────────────────────────────────────────────────

    /**
     * Arranca el adaptador:
     *   - Hilo lector: se conecta y hace receive() bloqueante en loop.
     *   - Hilo escritor (opcional): lee del Space y envía por el canal.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            readerThread = new Thread(this, "iso-reader-" + host + ":" + port);
            readerThread.setDaemon(true);
            readerThread.start();

            if (outKey != null) {
                writerThread = new Thread(this::writerLoop, "iso-writer-" + host + ":" + port);
                writerThread.setDaemon(true);
                writerThread.start();
            }

            log.info("");
            log.info("  ┌{}┐", SLINE);
            log.info("  │  ▶  CHANNEL ADAPTOR INICIADO");
            log.info("  │  {}", String.format("%-22s  %s", "Nombre:",     name));
            log.info("  │  {}", String.format("%-22s  %s:%d", "Destino:", host, port));
            log.info("  │  {}", String.format("%-22s  %s", "inKey:",      inKey));
            log.info("  │  {}", String.format("%-22s  %s", "outKey:",     outKey != null ? outKey : "(solo receptor)"));
            log.info("  │  {}", String.format("%-22s  %s", "Timestamp:",  LocalDateTime.now().format(DT_FMT)));
            log.info("  └{}┘", SLINE);
            log.info("");
        }
    }

    public void stop() {
        running.set(false);
        channel.close();
        if (readerThread != null) readerThread.interrupt();
        if (writerThread != null) writerThread.interrupt();

        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ■  CHANNEL ADAPTOR DETENIDO  │  {}", name);
        log.info("  └{}┘", SLINE);
        log.info("");
    }

    // ── Hilo de lectura (Runnable) ────────────────────────────────────────────

    /**
     * Loop principal del hilo lector.
     *
     * En jPOS, ChannelAdaptor.run() hace exactamente esto:
     *   while (running) {
     *       connect si no está conectado
     *       msg = channel.receive()   // bloqueante
     *       space.put(buildKey(msg), msg)
     *   }
     *
     * Si el canal cae, espera reconnectDelayMs y reintenta.
     */
    @Override
    public void run() {
        int attempt = 0;

        while (running.get()) {
            try {
                // ── Conexión ─────────────────────────────────────────────────
                if (!channel.isConnected()) {
                    connect(++attempt);
                    attempt = 0; // reset al conectar exitosamente
                }

                // ── Lectura bloqueante ────────────────────────────────────────
                ISOMsg msg = channel.receive();

                if (msg == null) {
                    // EOF limpio — el servidor cerró la conexión
                    log.info("  [{}] EOF recibido, reconectando...", name);
                    connected.set(false);
                    channel.close();
                    continue;
                }

                // ── Publicar en el Space ──────────────────────────────────────
                String key = buildInKey(msg);
                space.put(key, msg);

                log.debug("  [{}] → Space key='{}' MTI={}", name, key, msg.getMTI());

            } catch (SocketException e) {
                if (running.get()) {
                    log.warn("  [{}] Conexión perdida: {} — reconectando...", name, e.getMessage());
                    connected.set(false);
                    channel.close();
                    try {
                        sleepBeforeReconnect();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            } catch (IOException e) {
                if (running.get()) {
                    log.error("  [{}] Error I/O: {} — reconectando...", name, e.getMessage());
                    connected.set(false);
                    channel.close();
                    try {
                        sleepBeforeReconnect();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            } catch (ISOException e) {
                // Error de parseo: loguear y seguir — no romper el loop por un mensaje malo
                log.error("  [{}] Error ISO (mensaje descartado): {}", name, e.getMessage());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Hilo de escritura (opcional) ──────────────────────────────────────────

    /**
     * Loop del hilo escritor.
     *
     * Lee mensajes del Space bajo outKey y los envía por el canal.
     * Permite que el ISOMUX o cualquier otro componente publique un ISOMsg
     * en el Space y el adaptador lo envíe al servidor de forma desacoplada.
     *
     * En jPOS esto también lo hace el ChannelAdaptor cuando tiene un outQueue.
     */
    private void writerLoop() {
        log.debug("  [{}] Writer loop iniciado, escuchando outKey='{}'", name, outKey);

        while (running.get()) {
            try {
                // Esperar un mensaje saliente en el Space (bloqueante con timeout
                // para poder chequear running.get() y no quedar colgado al hacer stop())
                Object obj = space.take(outKey, 2_000L);
                if (obj == null) continue; // timeout, re-chequear running

                if (!(obj instanceof ISOMsg msg)) {
                    log.warn("  [{}] outKey='{}' recibió objeto que no es ISOMsg: {}",
                            name, outKey, obj.getClass().getSimpleName());
                    continue;
                }

                if (!channel.isConnected()) {
                    log.warn("  [{}] Canal no conectado, descartando mensaje MTI={}",
                            name, msg.getMTI());
                    continue;
                }

                channel.send(msg);
                log.debug("  [{}] ← enviado MTI={}", name, msg.getMTI());

            } catch (ISOException | IOException e) {
                log.error("  [{}] Error enviando mensaje: {}", name, e.getMessage());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Helpers internos ──────────────────────────────────────────────────────

    /**
     * Intenta conectar el canal con logging detallado.
     * Si falla, espera reconnectDelayMs antes de que el caller reintente.
     */
    private void connect(int attempt) throws InterruptedException {
        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ↺  CONECTANDO  │  {} → {}:{}  │  Intento {}",
                name, host, port, attempt);
        log.info("  └{}┘", SLINE);

        try {
            channel.connect(host, port);
            connected.set(true);

            log.info("");
            log.info("  ┌{}┐", SLINE);
            log.info("  │  ✔  CONEXIÓN ESTABLECIDA");
            log.info("  │  {}", String.format("%-22s  %s:%d", "Destino:", host, port));
            log.info("  │  {}", String.format("%-22s  %s", "Timestamp:",
                    LocalDateTime.now().format(DT_FMT)));
            log.info("  └{}┘", SLINE);
            log.info("");

        } catch (IOException e) {
            connected.set(false);
            log.warn("  [{}] Intento {} fallido: {}", name, attempt, e.getMessage());

            if (attempt >= maxReconnectAttempts) {
                log.error("  [{}] Máximo de reintentos alcanzado ({}), deteniendo adaptador",
                        name, maxReconnectAttempts);
                running.set(false);
                return;
            }

            sleepBeforeReconnect();
        }
    }

    /**
     * Construye la clave de correlación para publicar en el Space.
     *
     * Formato: inKey + MTI + "-" + STAN
     * Ejemplo: "ISO-RESP-0210-000042"
     *
     * El ISOMUX usa la misma lógica para hacer take() y obtener la respuesta.
     * Si el mensaje no tiene STAN (campo 11), usa solo el MTI.
     */
    private String buildInKey(ISOMsg msg) {
        String mti  = msg.getMTI() != null ? msg.getMTI() : "UNKNOWN";
        String stan = msg.hasField(11) ? msg.getString(11) : "";
        return stan.isEmpty()
                ? inKey + mti
                : inKey + mti + "-" + stan;
    }

    private void sleepBeforeReconnect() throws InterruptedException {
        log.debug("  [{}] Esperando {} ms antes de reconectar...", name, reconnectDelayMs);
        Thread.sleep(reconnectDelayMs);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public void setInKey(String inKey)                       { this.inKey = inKey; }
    public void setOutKey(String outKey)                     { this.outKey = outKey; }
    public void setReconnectDelayMs(long ms)                 { this.reconnectDelayMs = ms; }
    public void setMaxReconnectAttempts(int max)             { this.maxReconnectAttempts = max; }
    public void setReadTimeoutMs(int ms)                     { this.readTimeoutMs = ms; }
    public void setName(String name)                         { this.name = name; }
    public String getName()                                  { return name; }
    public boolean isConnected()                             { return connected.get(); }
    public boolean isRunning()                               { return running.get(); }
    public Space getSpace()                                  { return space; }
    public String getInKey()                                 { return inKey; }
    public String getOutKey()                                { return outKey; }
}