package com.spring.transactional.iso8583.main.Application.listeners;

import com.spring.transactional.iso8583.main.TransactionalPackage.channel.Space;
import com.spring.transactional.iso8583.main.TransactionalPackage.logs.ISOLogger;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.server.ISORequestListener;
import com.spring.transactional.iso8583.main.TransactionalPackage.transaction.Context;
import lombok.extern.slf4j.Slf4j;



/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  SPACE REQUEST LISTENER                                          ║
 * ║  Reemplaza a FiservListener integrando el ISOServer con el Space ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ¿Qué hace?
 *   Es el listener que el ISOServer llama por cada mensaje recibido.
 *   En lugar de procesar el mensaje directamente (como hacía FiservListener),
 *   lo publica en el Space y espera que el RequestProcessor lo procese
 *   en su propio hilo y devuelva la respuesta al Space.
 *
 * ¿Por qué no procesar directo como antes?
 *   Con FiservListener, la lógica de negocio corría en el hilo del
 *   ThreadPool del ISOServer. Si el procesamiento era lento (consultas a BD,
 *   llamadas a APIs externas), ese hilo quedaba ocupado y el servidor no podía
 *   aceptar nuevas conexiones.
 *
 *   Con SpaceRequestListener, el hilo del pool solo hace:
 *     1. space.put(request)       ← microsegundos
 *     2. space.take(response)     ← espera, pero en otro hilo se procesa
 *   El procesamiento pesado ocurre en el RequestProcessor, separado.
 *
 * Flujo completo:
 *   ISOServer → onRequest() → space.put(IN-0200-000042)
 *                                       ↓
 *                           RequestProcessor.process()  ← hilo propio
 *                                       ↓
 *                              space.put(RESP-0200-000042)
 *                                       ↓
 *               onRequest() ← space.take(RESP-0200-000042) → ISOServer → cliente
 *
 * Uso:
 *   ISOServer server = new ISOServer(8583, packager,
 *       new SpaceRequestListener(space, "Puerto 8583")
 *   );
 */
@Slf4j
public class FiservListener implements ISORequestListener {
    // ── Dependencias ──────────────────────────────────────────────────────────
    private final Space space;        // bus compartido con el RequestProcessor
    private final String channelName;  // nombre del puerto/canal para logging, ej: "Puerto 8583"
    private final long   timeoutMs;    // tiempo máximo esperando al processor

    // ── Configuración de claves ───────────────────────────────────────────────

    // POR DEFECTO
    // Prefijo para publicar requests entrantes en el Space.
    // La clave completa es: inKey + MTI + "-" + STAN → "IN-0200-000042"
    // DEBE coincidir con el inKey del RequestProcessor.
    private String inKey   = "IN-";

    // POR DEFECTO
    // Prefijo para leer respuestas del Space que publicó el RequestProcessor.
    // La clave completa es: respKey + MTI + "-" + STAN → "RESP-0200-000042"
    // DEBE coincidir con el respKey del RequestProcessor.
    private String respKey = "RESP-";

    // ── Constructores ─────────────────────────────────────────────────────────
    public FiservListener(Space space, String channelName, long timeoutMs) {
        this.space       = space;
        this.channelName = channelName;
        this.timeoutMs   = timeoutMs;
    }

    /** Constructor con timeout por defecto de 30 segundos. */
    public FiservListener(Space space, String channelName) {
        this(space, channelName, 30_000L);
    }

    // ── onRequest() — punto de entrada desde el ISOServer ────────────────────

    /**
     * Llamado por el ISOServer por cada mensaje que llega de un cliente/terminal.
     *
     * Este método corre en un hilo del ThreadPool del ISOServer.
     * Lo más importante: hace el mínimo trabajo posible en este hilo
     * y delega el procesamiento pesado al RequestProcessor.
     *
     * @param request mensaje ISO recibido completamente deserializado
     * @return respuesta a enviar al cliente, o null si no se debe responder
     */
    @Override
    public ISOMsg onRequest(ISOMsg request) {
        long inicio = System.currentTimeMillis();

        // Log de entrada — igual que FiservListener, el formato no cambia
        ISOLogger.logIncoming(log, request, channelName);

        // Extraer STAN y MTI para construir las claves del Space
        String stan   = resolveStan(request);
        String mti    = request.getMTI() != null ? request.getMTI() : "UNKNOWN";

        // SOURCE es la clave donde este listener espera la respuesta,
        // y la misma que SendResponse usa para publicarla.
        // Formato: respKey + MTI + "-" + STAN → "RESP-0200-000042"
        String source = respKey + mti + "-" + stan;

        // ── Publicar el request en el Space ───────────────────────────────────
        // El RequestProcessor está bloqueado en space.take(inKey+MTI, ...) y
        // se desbloquea al instante al recibir este put()
        // ✅ construye Context con REQUEST + SOURCE y lo publica
        Context ctx = new Context();
        ctx.put(Context.REQUEST, request);  // el ISOMsg adentro del Context
        ctx.put(Context.SOURCE,  source);   // "RESP-0200-000042" — SendResponse publica acá
        space.put(inKey, ctx);

        log.debug("  [{}] → Space key='{}' SOURCE='{}' MTI={} STAN={}",
                channelName, inKey, source, mti, stan);

        // ── Esperar la respuesta ──────────────────────────────────────────────
        // Este hilo se bloquea acá hasta que SendResponse haga:
        //   space.put("RESP-0200-000042", responseMsg)
        ISOMsg response = waitForResponse(source, stan, mti);

        // ── Manejar timeout ───────────────────────────────────────────────────
        // Si el processor no respondió a tiempo, construir RC=96 (System malfunction)
        // para no dejar al cliente esperando indefinidamente
        if (response == null) {
            log.error("  [{}] Timeout esperando respuesta del processor para STAN={} MTI={}",
                    channelName, stan, mti);
            response = buildTimeoutResponse(request);
        }

        // Log de salida y resultado — igual que FiservListener
        ISOLogger.logOutgoing(log, response, channelName + " → Cliente");
        ISOLogger.logResult(log, request, response, System.currentTimeMillis() - inicio);

        return response; // el ISOServer envía esto al cliente por TCP
    }

    // ── waitForResponse() — esperar la respuesta del processor ───────────────

    /**
     * Bloquea el hilo hasta que el RequestProcessor publique la respuesta en el Space.
     *
     * @param key  clave completa a esperar, ej: "RESP-0200-000042"
     * @return el ISOMsg de respuesta, o null si venció el timeout o hubo interrupción
     */
    private ISOMsg waitForResponse(String key, String stan, String mti) {
        try {
            Object obj = space.take(key, timeoutMs); // ← hilo bloqueado acá

            if (obj == null) return null; // timeout — nadie publicó en esa clave

            if (!(obj instanceof ISOMsg response)) {
                // El Space tiene algo inesperado — error de configuración
                log.error("  [{}] Objeto inesperado en key='{}': {}",
                        channelName, key, obj.getClass().getSimpleName());
                return null;
            }

            return response;

        } catch (InterruptedException e) {
            // El servidor se está apagando — terminar limpiamente
            Thread.currentThread().interrupt();
            log.warn("  [{}] Hilo interrumpido esperando respuesta STAN={}", channelName, stan);
            return null;
        }
    }

    // ── resolveStan() — obtener o generar STAN ────────────────────────────────

    /**
     * Extrae el STAN del campo 11 del mensaje.
     *
     * Si el mensaje no tiene campo 11 (situación anómala), genera un fallback
     * basado en timestamp para que la clave del Space sea igualmente única.
     * Sin una clave única, dos requests sin STAN tendrían la misma clave
     * y el Space entregaría la respuesta equivocada.
     */
    private String resolveStan(ISOMsg msg) {
        if (msg.hasField(11)) {
            String stan = msg.getString(11);
            if (stan != null && !stan.isBlank()) return stan;
        }
        // Fallback: últimos 6 dígitos del timestamp en milisegundos
        String fallback = String.valueOf(System.currentTimeMillis() % 1_000_000);
        log.warn("  [{}] Mensaje sin campo 11 (STAN), usando fallback='{}'", channelName, fallback);
        return fallback;
    }

    // ── buildTimeoutResponse() — respuesta de error por timeout ──────────────

    /**
     * Construye una respuesta de error técnico cuando el processor no responde.
     *
     * RC 96 = "System malfunction" — código estándar ISO 8583 para error interno.
     * Garantiza que el cliente siempre recibe una respuesta en lugar de que
     * la conexión quede colgada hasta que el cliente haga timeout por su lado.
     */
    private ISOMsg buildTimeoutResponse(ISOMsg request) {
        ISOMsg error = request.createResponse(); // crea el MTI de respuesta (ej: 0210 desde 0200)
        error.set(39, "96"); // RC 96 = System malfunction
        return error;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public void setInKey(String inKey)     { this.inKey = inKey; }
    public void setRespKey(String respKey) { this.respKey = respKey; }
    public String getInKey()               { return inKey; }
    public String getRespKey()             { return respKey; }
}