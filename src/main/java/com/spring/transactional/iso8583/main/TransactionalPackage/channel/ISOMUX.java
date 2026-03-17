package com.spring.transactional.iso8583.main.TransactionalPackage.channel;

import com.spring.transactional.iso8583.main.TransactionalPackage.exception.ISOException;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multiplexor de mensajes ISO 8583 estilo jPOS ISOMUX.
 *
 * El problema que resuelve:
 *   Un canal TCP es full-duplex pero serial: si dos hilos envían requests
 *   simultáneamente, las respuestas pueden llegar en cualquier orden.
 *   El MUX correlaciona cada respuesta con su request original usando el
 *   STAN (campo 11 — System Trace Audit Number).
 *
 * Flujo de request():
 *
 *   1. Asigna un STAN único al mensaje si no tiene uno.
 *   2. Construye la clave de respuesta esperada: respKey = inKey + MTI-resp + "-" + STAN
 *      (ej: "ISO-RESP-0210-000042")
 *   3. Publica el request en el Space bajo outKey para que el ChannelAdaptor
 *      lo envíe por el canal TCP.
 *   4. Hace space.take(respKey, timeout) — bloqueante hasta recibir la respuesta.
 *   5. Devuelve la respuesta o lanza ISOException si hubo timeout.
 *
 * Múltiples hilos pueden llamar request() en paralelo — cada uno espera
 * su propia clave en el Space y no interfiere con los demás.
 *
 * Equivalente a: org.jpos.iso.MUX / ISOMUX de jPOS
 */
@Slf4j
public class ISOMUX implements DisposableBean {

   // private static final Logger log = LoggerFactory.getLogger(ISOMUX.class);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SLINE = "─".repeat(64);

    // ── Config ────────────────────────────────────────────────────────────────

    private final Space      space;
    private final ISOChannel channel;
    private final String     name;

    /**
     * Clave en el Space donde el ChannelAdaptor publica las respuestas recibidas.
     * Debe coincidir con el inKey del ChannelAdaptor.
     * Ejemplo: "ISO-RESP-"  →  clave completa = "ISO-RESP-0210-000042"
     */
    private String inKey  = "ISO-RESP-";

    /**
     * Clave en el Space donde este MUX publica los requests para que el
     * ChannelAdaptor los envíe por el canal.
     * Debe coincidir con el outKey del ChannelAdaptor.
     */
    private String outKey = "ISO-REQ";

    /** Timeout por defecto para esperar respuesta (ms). */
    private long defaultTimeoutMs = 30_000L;

    // ── Estado ────────────────────────────────────────────────────────────────

    /**
     * Contador atómico para generar STANs únicos.
     * Cicla de 1 a 999999 igual que el campo 11 de ISO 8583 (6 dígitos).
     */
    private final AtomicInteger stanCounter = new AtomicInteger(0);

    /**
     * Requests en vuelo: STAN → timestamp de envío.
     * Permite monitorear requests sin respuesta y detectar leaks.
     */
    private final ConcurrentHashMap<String, Long> inFlight = new ConcurrentHashMap<>();

    // ── Constructores ─────────────────────────────────────────────────────────

    /**
     * Constructor principal.
     *
     * @param space   el Space compartido con el ChannelAdaptor
     * @param channel el canal TCP (usado solo para verificar conectividad)
     * @param name    nombre para logging
     */
    public ISOMUX(Space space, ISOChannel channel, String name) {
        this.space   = space;
        this.channel = channel;
        this.name    = name;
    }

    public ISOMUX(Space space, ISOChannel channel) {
        this(space, channel, "iso-mux");
    }

    // ── API principal ─────────────────────────────────────────────────────────

    /**
     * Envía un request y espera la respuesta de forma bloqueante.
     *
     * Thread-safe: múltiples hilos pueden llamar este método en paralelo.
     * Cada hilo espera su propia clave en el Space de forma independiente.
     *
     * @param request  mensaje ISO a enviar (se le asigna STAN si no tiene)
     * @return la respuesta correlacionada por STAN
     * @throws ISOException si hay timeout o el canal no está conectado
     * @throws IOException  si hay error al enviar
     */
    public ISOMsg request(ISOMsg request) throws ISOException, IOException {
        return request(request, defaultTimeoutMs);
    }

    /**
     * Envía un request y espera la respuesta con timeout explícito.
     *
     * @param request   mensaje ISO a enviar
     * @param timeoutMs tiempo máximo de espera en milisegundos
     * @return la respuesta correlacionada
     * @throws ISOException si hay timeout o error
     * @throws IOException  si hay error de red
     */
    public ISOMsg request(ISOMsg request, long timeoutMs) throws ISOException, IOException {
        if (!channel.isConnected()) {
            throw new ISOException("[" + name + "] Canal no conectado — no se puede enviar");
        }

        // ── Paso 1: asignar STAN si no tiene ─────────────────────────────────
        String stan = ensureStan(request);

        // ── Paso 2: construir la clave de respuesta esperada ──────────────────
        // MTI de respuesta = MTI de request con el tercer dígito en "1"
        // 0200 → 0210,  0100 → 0110,  0400 → 0410
        String respMti = buildResponseMti(request.getMTI());
        String respKey = inKey + respMti + "-" + stan;

        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ⟶  MUX REQUEST");
        log.info("  │  {}", String.format("%-22s  %s", "MUX:",       name));
        log.info("  │  {}", String.format("%-22s  %s", "MTI:",       request.getMTI()));
        log.info("  │  {}", String.format("%-22s  %s", "STAN:",      stan));
        log.info("  │  {}", String.format("%-22s  %s", "outKey:",    outKey));
        log.info("  │  {}", String.format("%-22s  %s", "respKey:",   respKey));
        log.info("  │  {}", String.format("%-22s  %d ms", "Timeout:", timeoutMs));
        log.info("  │  {}", String.format("%-22s  %s", "Timestamp:",
                LocalDateTime.now().format(DT_FMT)));
        log.info("  └{}┘", SLINE);
        log.info("");

        // ── Paso 3: registrar en inFlight y publicar en Space ─────────────────
        inFlight.put(stan, System.currentTimeMillis());
        space.put(outKey, request);

        // ── Paso 4: esperar respuesta en el Space ─────────────────────────────
        try {
            Object obj = space.take(respKey, timeoutMs);

            if (obj == null) {
                inFlight.remove(stan);
                throw new ISOException(String.format(
                        "[%s] Timeout esperando respuesta — MTI=%s STAN=%s timeout=%d ms",
                        name, request.getMTI(), stan, timeoutMs));
            }

            if (!(obj instanceof ISOMsg response)) {
                inFlight.remove(stan);
                throw new ISOException(String.format(
                        "[%s] El Space devolvió un objeto inesperado para key='%s': %s",
                        name, respKey, obj.getClass().getSimpleName()));
            }

            inFlight.remove(stan);
            long elapsed = System.currentTimeMillis() -
                    (inFlight.getOrDefault(stan, System.currentTimeMillis()));

            log.info("");
            log.info("  ┌{}┐", SLINE);
            log.info("  │  ✔  MUX RESPONSE");
            log.info("  │  {}", String.format("%-22s  %s", "MUX:",      name));
            log.info("  │  {}", String.format("%-22s  %s", "MTI resp:", response.getMTI()));
            log.info("  │  {}", String.format("%-22s  %s", "STAN:",     stan));
            log.info("  │  {}", String.format("%-22s  %s", "RC (F39):", response.hasField(39)
                    ? response.getString(39) : "n/a"));
            log.info("  └{}┘", SLINE);
            log.info("");

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inFlight.remove(stan);
            throw new ISOException("[" + name + "] Hilo interrumpido esperando respuesta STAN=" + stan);
        }
    }

    /**
     * Envía un mensaje sin esperar respuesta (fire-and-forget).
     * Útil para mensajes de aviso (MTI 0x2xx) o Network Management.
     *
     * @param msg mensaje a enviar
     */
    public void send(ISOMsg msg) throws ISOException, IOException {
        if (!channel.isConnected())
            throw new ISOException("[" + name + "] Canal no conectado");

        ensureStan(msg);
        space.put(outKey, msg);
        log.debug("  [{}] fire-and-forget MTI={}", name, msg.getMTI());
    }

    // ── Monitoreo ─────────────────────────────────────────────────────────────

    /** Devuelve cuántos requests están esperando respuesta en este momento. */
    public int getInFlightCount() { return inFlight.size(); }

    /**
     * Devuelve los STANs de requests que llevan más de maxAgeMs sin respuesta.
     * Útil para detectar leaks o diagnosticar timeouts.
     */
    public java.util.Set<String> getStalledRequests(long maxAgeMs) {
        long now = System.currentTimeMillis();
        java.util.Set<String> stalled = new java.util.HashSet<>();
        inFlight.forEach((stan, ts) -> {
            if (now - ts > maxAgeMs) stalled.add(stan);
        });
        return stalled;
    }

    // ── Helpers internos ──────────────────────────────────────────────────────

    /**
     * Asegura que el mensaje tenga campo 11 (STAN).
     * Si ya tiene STAN, lo respeta. Si no, genera uno nuevo.
     *
     * @return el STAN asignado (siempre 6 dígitos con ceros a la izquierda)
     */
    private String ensureStan(ISOMsg msg) throws ISOException {
        if (msg.hasField(11) && msg.getString(11) != null
                && !msg.getString(11).isBlank()) {
            return msg.getString(11);
        }
        // Ciclo 1..999999 — igual que el campo 11 de ISO 8583
        int next = (stanCounter.incrementAndGet() % 1_000_000);
        if (next == 0) next = 1;
        String stan = String.format("%06d", next);
        msg.set(11, stan);
        return stan;
    }

    /**
     * Convierte el MTI de request al MTI de respuesta esperado.
     *
     * En ISO 8583, el tercer dígito del MTI indica la función:
     *   0 = Request        → respuesta esperada con tercer dígito = 1
     *   1 = Response       (ya es respuesta)
     *   2 = Advice         → respuesta = 3
     *   3 = Advice response
     *
     * Ejemplos:
     *   0200 → 0210
     *   0100 → 0110
     *   0400 → 0410
     *   0420 → 0430
     */
    private String buildResponseMti(String requestMti) {
        if (requestMti == null || requestMti.length() != 4) return requestMti;

        char[] mti = requestMti.toCharArray();
        char thirdDigit = mti[2];

        mti[2] = switch (thirdDigit) {
            case '0' -> '1'; // Request → Response
            case '2' -> '3'; // Advice  → Advice Response
            default  -> thirdDigit; // ya es respuesta, no transformar
        };

        return new String(mti);
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override
    public void destroy() {
        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ■  ISOMUX DESTRUIDO  │  {}  │  inFlight={}", name, inFlight.size());
        log.info("  └{}┘", SLINE);
        log.info("");
        inFlight.clear();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public void setInKey(String inKey)               { this.inKey = inKey; }
    public void setOutKey(String outKey)             { this.outKey = outKey; }
    public void setDefaultTimeoutMs(long ms)         { this.defaultTimeoutMs = ms; }
    public String getName()                          { return name; }
    public String getInKey()                         { return inKey; }
    public String getOutKey()                        { return outKey; }
    public long getDefaultTimeoutMs()                { return defaultTimeoutMs; }
}