package com.spring.transactional.iso8583.main.TransactionalPackage.exception;


/**
 * Excepción base para todos los errores del framework ISO 8583.
 *
 * Se lanza en situaciones como:
 *   - MTI nulo o inválido al empaquetar
 *   - Campo presente en la trama sin definición registrada en el packager
 *   - Trama demasiado corta o mal formada al desempaquetar
 *   - Tipo de campo no soportado por la operación
 *
 * Opcionalmente transporta un responseCode ISO (campo 39) para que
 * el manejador pueda retornar el código de error correcto al emisor.
 *
 * Equivalente a org.jpos.iso.ISOException de jPOS.
 */
public class ISOException extends Exception {

    private static final long serialVersionUID = 1L;

    // Código de respuesta ISO (campo 39) asociado al error.
    // Ej: "30" = error de formato, "96" = error del sistema
    private String responseCode;

    /** Excepción con solo mensaje descriptivo. */
    public ISOException(String message) { super(message); }

    /** Excepción con causa encadenada (para wrapping de excepciones de bajo nivel). */
    public ISOException(String message, Throwable cause) { super(message, cause); }

    /**
     * Excepción con mensaje y código de respuesta ISO.
     * Útil cuando el error debe traducirse directamente en un campo 39.
     * Ej: new ISOException("PAN inválido", "14")
     */
    public ISOException(String message, String responseCode) {
        super(message);
        this.responseCode = responseCode;
    }

    public String getResponseCode()        { return responseCode; }
    public void setResponseCode(String rc) { this.responseCode = rc; }
}