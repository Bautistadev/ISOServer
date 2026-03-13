package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

/**
 * Encapsula el resultado de la ejecución de un participante o del pipeline completo.
 *
 * El ciclo de vida de una transacción en el pipeline sigue el protocolo 2-phase commit:
 *
 *   1. Se llama a prepare() en cada participante en orden.
 *   2. Si todos retornan PREPARED → se llama a commit() en todos.
 *   3. Si alguno retorna ABORTED → se llama a abort() en orden inverso sobre los ya preparados.
 *   4. Si alguno retorna FINAL → se hace commit de los preparados hasta ese punto y se detiene.
 *
 * Los factory methods (prepared, approved, declined, aborted) hacen el código más legible.
 * Ej: return TransactionResult.declined("51", "Fondos insuficientes");
 *
 * Equivalente a los valores de retorno PREPARED/ABORTED de TransactionParticipant en jPOS.
 */
public class TransactionResult {

    public enum Status {
        /** Todo OK, continuar al siguiente participante. */
        PREPARED,
        /** Error: detener el pipeline y hacer rollback de los ya preparados. */
        ABORTED,
        /** Resultado final definitivo: commit y detener el pipeline (sin seguir al siguiente). */
        FINAL
    }

    private final Status status;
    private final String responseCode; // Código ISO campo 39. "00" = aprobado
    private final String message;      // Descripción legible para logs internos

    private TransactionResult(Status status, String responseCode, String message) {
        this.status       = status;
        this.responseCode = responseCode;
        this.message      = message;
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /** El participante procesó correctamente, se puede continuar al siguiente. */
    public static TransactionResult prepared() {
        return new TransactionResult(Status.PREPARED, "00", "OK");
    }

    /**
     * La transacción fue aprobada definitivamente.
     * Detiene el pipeline con éxito y hace commit.
     * RC "00" = aprobado según estándar ISO 8583.
     */
    public static TransactionResult approved() {
        return new TransactionResult(Status.FINAL, "00", "Approved");
    }

    /**
     * La transacción fue declinada (respuesta negativa controlada).
     * Detiene el pipeline con el código y razón indicados.
     * Ej: declined("51", "Fondos insuficientes"), declined("54", "Tarjeta expirada")
     */
    public static TransactionResult declined(String rc, String reason) {
        return new TransactionResult(Status.FINAL, rc, reason);
    }

    /**
     * Error de sistema: aborta el pipeline y hace rollback.
     * RC "96" = error del sistema (código estándar para fallas técnicas).
     */
    public static TransactionResult aborted(String reason) {
        return new TransactionResult(Status.ABORTED, "96", reason);
    }

    /**
     * Versión de abort con código específico.
     * Ej: aborted("91", "Emisor no disponible") cuando el host no responde.
     */
    public static TransactionResult aborted(String rc, String reason) {
        return new TransactionResult(Status.ABORTED, rc, reason);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Status getStatus()       { return status; }
    public String getResponseCode() { return responseCode; }
    public String getMessage()      { return message; }

    /** Verifica si se debe continuar el pipeline (aún no hay resultado final). */
    public boolean isPrepared()  { return status == Status.PREPARED; }

    /** Verifica si hubo un error de sistema y se debe hacer rollback. */
    public boolean isAborted()   { return status == Status.ABORTED; }

    /** Verifica si hay un resultado definitivo (aprobado o declinado). */
    public boolean isFinal()     { return status == Status.FINAL; }

    /** Atajo para verificar si fue aprobado (RC == "00"). */
    public boolean isApproved()  { return "00".equals(responseCode); }

    @Override
    public String toString() {
        return String.format("TransactionResult[%s, rc=%s, msg='%s']", status, responseCode, message);
    }
}