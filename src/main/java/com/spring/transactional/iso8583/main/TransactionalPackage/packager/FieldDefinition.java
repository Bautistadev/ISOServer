package com.spring.transactional.iso8583.main.TransactionalPackage.packager;


/**
 * Describe las propiedades de un campo específico en el esquema ISO 8583.
 *
 * El packager usa estas definiciones para saber cómo leer y escribir
 * cada campo en la trama de red. Cada campo tiene:
 *   - Un número identificador (1-128)
 *   - Un tipo de dato (ALPHA, LLVAR, BINARY, etc.)
 *   - Una longitud máxima
 *   - Una descripción legible (para logs y documentación)
 *   - Si es obligatorio o no
 *
 * Equivalente al concepto de ISOFieldPackager en jPOS, pero unificado en una sola clase.
 *
 * Uso con builder:
 *   FieldDefinition.field(2).type(FieldType.LLNUM).length(19).description("PAN").mandatory().build()
 */
public class FieldDefinition {

    private final int fieldNumber;     // Número del campo en el mensaje ISO (1-128)
    private final FieldType type;      // Tipo de dato que determina cómo se serializa
    private final int maxLength;       // Longitud máxima del valor (en chars o bytes)
    private final String description;  // Descripción legible del campo (para logs)
    private final boolean mandatory;   // Si el campo es obligatorio en este esquema

    /** Constructor completo sin mandatory (asume false por defecto). */
    public FieldDefinition(int fieldNumber, FieldType type, int maxLength, String description) {
        this(fieldNumber, type, maxLength, description, false);
    }

    /** Constructor completo. Preferred via Builder para mayor legibilidad. */
    public FieldDefinition(int fieldNumber, FieldType type, int maxLength,
                           String description, boolean mandatory) {
        this.fieldNumber = fieldNumber;
        this.type        = type;
        this.maxLength   = maxLength;
        this.description = description;
        this.mandatory   = mandatory;
    }

    public int getFieldNumber()    { return fieldNumber; }
    public FieldType getType()     { return type; }
    public int getMaxLength()      { return maxLength; }
    public String getDescription() { return description; }
    public boolean isMandatory()   { return mandatory; }

    @Override
    public String toString() {
        return String.format("FieldDefinition[%d, %s, len=%d, '%s']",
                fieldNumber, type, maxLength, description);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Punto de entrada del Builder. Uso fluido:
     *   FieldDefinition.field(4)
     *       .type(FieldType.NUMERIC)
     *       .length(12)
     *       .description("Amount, Transaction")
     *       .build();
     */
    public static Builder field(int number) { return new Builder(number); }

    public static class Builder {
        private final int number;
        private FieldType type     = FieldType.ALPHA; // Tipo por defecto: alfanumérico fijo
        private int maxLength      = 0;
        private String description = "";
        private boolean mandatory  = false;

        private Builder(int number) { this.number = number; }

        public Builder type(FieldType type)     { this.type = type;        return this; }
        public Builder length(int length)       { this.maxLength = length; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder mandatory()              { this.mandatory = true;   return this; }

        /** Construye y retorna la FieldDefinition con los parámetros acumulados. */
        public FieldDefinition build() {
            return new FieldDefinition(number, type, maxLength, description, mandatory);
        }
    }
}