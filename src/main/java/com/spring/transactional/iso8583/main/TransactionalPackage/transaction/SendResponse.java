package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

import com.spring.transactional.iso8583.main.TransactionalPackage.channel.Space;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import lombok.extern.slf4j.Slf4j;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  SEND RESPONSE — Último participante de la cadena                ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ¿Qué hace?
 *   Lee el RESPONSE del Context y lo publica en el Space bajo la clave
 *   SOURCE — que es exactamente la clave que el SpaceRequestListener
 *   está esperando con su space.take(SOURCE, timeout).
 *
 *   Con ese put(), el hilo del SpaceRequestListener se desbloquea,
 *   toma el ISOMsg y lo envía al cliente por TCP a través del ISOServer.
 *
 * Posición en la cadena:
 *   Siempre el ÚLTIMO participante de cada grupo de negocio.
 *   No tiene sentido enviarlo antes de que los participantes anteriores
 *   hayan construido el RESPONSE en el Context.
 *
 * ¿Qué pasa si no hay RESPONSE?
 *   Loguea un error y no publica nada. El SpaceRequestListener esperará
 *   hasta su timeout y enviará RC=96 al cliente. Por eso es importante
 *   que algún participante anterior siempre construya el RESPONSE,
 *   incluso en casos de error (ej: ConstruirRespuesta con RC=05).
 */
@Slf4j
public class SendResponse implements TransactionParticipant {

    // Space compartido con el SpaceRequestListener
    private final Space space;

    public SendResponse(Space space) {
        this.space = space;
    }

    // ── prepare() — publicar la respuesta en el Space ────────────────────────

    /**
     * Lee RESPONSE y SOURCE del Context y hace space.put(SOURCE, RESPONSE).
     *
     * Este put() desbloquea el space.take() del SpaceRequestListener,
     * completando el ciclo de request/response del ISOServer.
     *
     * READONLY: no tiene estado propio que revertir si algo falla después.
     * NO_JOIN:  enviar la respuesta es un efecto permanente — no tiene
     *           sentido "des-enviarla" en un abort(). Si se llega hasta acá
     *           con un ABORTED, algún participante anterior ya construyó
     *           un RESPONSE con RC de error.
     */
    @Override
    public int prepare(long id, Context context) {
        ISOMsg response = context.getResponse();
        String source   = context.getSource();

        // Validar que tengamos todo lo necesario
        if (response == null) {
            log.error("  [SendResponse] Sesión {} — Context sin RESPONSE, no se puede responder", id);
            return PREPARED | READONLY | NO_JOIN; // no abortar — el listener manejará el timeout
        }

        if (source == null || source.isBlank()) {
            log.error("  [SendResponse] Sesión {} — Context sin SOURCE, no se sabe a quién responder", id);
            return PREPARED | READONLY | NO_JOIN;
        }

        // Publicar la respuesta — desbloquea el SpaceRequestListener
        space.put(source, response);

        log.debug("  [SendResponse] Sesión {} → Space key='{}' RC={}",
                id, source,
                response.hasField(39) ? response.getString(39) : "n/a");

        return PREPARED | READONLY | NO_JOIN;
    }
}