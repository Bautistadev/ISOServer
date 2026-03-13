package com.spring.transactional.iso8583.main.TransactionalPackage.server;


import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;

/**
 * Interfaz de callback para procesar mensajes ISO 8583 recibidos por el servidor.
 *
 * El ISOServer acepta conexiones TCP y, por cada mensaje recibido, llama a
 * onRequest() con el ISOMsg deserializado. El valor de retorno se envía de
 * vuelta al cliente como respuesta.
 *
 * Al ser @FunctionalInterface, puede implementarse con una lambda:
 *   ISOServer server = new ISOServer(8583, packager, request -> {
 *       ISOMsg response = request.createResponse();
 *       response.set(39, "00"); // Aprobar
 *       return response;
 *   });
 *
 * Equivalente a org.jpos.iso.ISORequestListener de jPOS.
 */
@FunctionalInterface
public interface ISORequestListener {

    /**
     * Procesa una solicitud ISO 8583 recibida por el servidor.
     *
     * @param request el mensaje de solicitud completamente deserializado
     * @return el mensaje de respuesta a enviar, o null para no responder
     *         (útil para mensajes de aviso que no esperan respuesta)
     */
    ISOMsg onRequest(ISOMsg request);
}