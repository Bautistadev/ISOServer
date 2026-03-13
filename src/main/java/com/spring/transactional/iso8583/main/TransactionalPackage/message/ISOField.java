package com.spring.transactional.iso8583.main.TransactionalPackage.message;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Representa un campo individual dentro de un mensaje ISO 8583.
 *
 * En ISO 8583, un mensaje se compone de hasta 128 campos numerados.
 * Cada campo tiene un número identificador y un valor, que puede ser
 * texto (String) o binario (byte[]).
 *
 * Equivalente a org.jpos.iso.ISOField de jPOS, pero sin dependencias externas.
 */
public class ISOField implements Serializable {

    private static final long serialVersionUID = 1L;

    // Número de campo dentro del mensaje ISO (del 1 al 128)
    private final int fieldNumber;

    // Valor del campo: puede ser String o byte[] según el tipo de dato
    // Se usa Object para soportar ambos tipos sin conversión forzada
    private Object value;

    /**
     * Constructor vacío: crea un campo sin valor todavía asignado.
     * Útil cuando se va a poblar el valor luego con setValue().
     */
    public ISOField(int fieldNumber) {
        this.fieldNumber = fieldNumber;
    }

    /**
     * Constructor con valor de texto.
     * Usado para la mayoría de campos alfanuméricos o numéricos del estándar.
     * Ej: PAN, monto, código de respuesta, etc.
     */
    public ISOField(int fieldNumber, String value) {
        this.fieldNumber = fieldNumber;
        this.value = value;
    }

    /**
     * Constructor con valor binario.
     * Usado para campos especiales como PIN Block (campo 52) o MAC (campo 64),
     * que se transmiten como bytes crudos sin codificación de texto.
     */
    public ISOField(int fieldNumber, byte[] value) {
        this.fieldNumber = fieldNumber;
        this.value = value;
    }

    /** Retorna el número de campo (posición dentro del mensaje ISO). */
    public int getFieldNumber() { return fieldNumber; }

    /** Retorna el valor crudo tal cual fue guardado (String o byte[]). */
    public Object getValue() { return value; }

    /** Permite reemplazar el valor del campo después de crearlo. */
    public void setValue(Object value) { this.value = value; }

    /**
     * Retorna el valor del campo siempre como String, sin importar
     * cómo fue almacenado internamente.
     *
     * - Si es null → retorna null
     * - Si ya es String → lo retorna directo
     * - Si es byte[] → lo convierte a String usando ISO-8859-1
     *   (el charset estándar en redes financieras, preserva todos los bytes)
     * - Si es cualquier otro tipo → llama a toString()
     */
    public String getString() {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof byte[]) return new String((byte[]) value, StandardCharsets.ISO_8859_1);
        return value.toString();
    }

    /**
     * Retorna el valor del campo siempre como byte array.
     * Útil al momento de serializar el mensaje para enviarlo por red.
     *
     * - Si es null → retorna null
     * - Si ya es byte[] → lo retorna directo
     * - Si es String → lo convierte a bytes con ISO-8859-1
     *   (garantiza representación 1 char = 1 byte, sin pérdida)
     */
    public byte[] getBytes() {
        if (value == null) return null;
        if (value instanceof byte[]) return (byte[]) value;
        if (value instanceof String) return ((String) value).getBytes(StandardCharsets.ISO_8859_1);
        return value.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Retorna la longitud lógica del valor del campo.
     * Para String: cantidad de caracteres.
     * Para byte[]: cantidad de bytes.
     * Se usa en el packager para calcular los indicadores de longitud (LLVAR, LLLVAR).
     */
    public int getLength() {
        if (value == null) return 0;
        if (value instanceof String) return ((String) value).length();
        if (value instanceof byte[]) return ((byte[]) value).length;
        return value.toString().length();
    }

    /**
     * Indica si el campo tiene un valor asignado.
     * Un campo "no seteado" (null) no debe incluirse en el bitmap ni en la trama.
     */
    public boolean isSet() { return value != null; }

    @Override
    public String toString() {
        return String.format("ISOField[%d]=%s", fieldNumber, value);
    }
}