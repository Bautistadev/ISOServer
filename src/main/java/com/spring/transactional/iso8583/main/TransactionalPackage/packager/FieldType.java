package com.spring.transactional.iso8583.main.TransactionalPackage.packager;


/**
 * Enumera todos los tipos de dato que puede tener un campo ISO 8583.
 *
 * Cada tipo define cómo se serializa y deserializa el campo en la trama de red.
 * En jPOS cada tipo era una clase separada (IFE_NUMERIC, IFE_LLVAR, etc.).
 * Aquí se unifica en un enum para mayor simplicidad.
 */
public enum FieldType {

    /**
     * ALPHA — Alfanumérico de longitud fija.
     * Si el valor es más corto que maxLength, se rellena con ESPACIOS a la DERECHA.
     * Si es más largo, se trunca.
     * Ej: campo 41 (Terminal ID, 8 chars): "TERM1" → "TERM1   "
     */
    ALPHA,

    /**
     * NUMERIC — Numérico de longitud fija.
     * Si el valor es más corto que maxLength, se rellena con CEROS a la IZQUIERDA.
     * Ej: campo 3 (Processing Code, 6 dígitos): "500" → "000500"
     */
    NUMERIC,

    /**
     * LLVAR — Variable, indicador de longitud de 2 dígitos (hasta 99 chars).
     * Formato en trama: [LL][VALOR]
     * Ej: campo 35 (Track 2, hasta 37): "4111...=2512" → "12" + "4111...=2512"
     * El "LL" indica la longitud real del valor que sigue.
     */
    LLVAR,

    /**
     * LLLVAR — Variable, indicador de longitud de 3 dígitos (hasta 999 chars).
     * Formato en trama: [LLL][VALOR]
     * Ej: campo 48 (Datos privados): "HOLA" → "004" + "HOLA"
     */
    LLLVAR,

    /**
     * LLNUM — Numérico variable con indicador de 2 dígitos.
     * Igual que LLVAR pero el contenido es solo dígitos.
     * Ej: campo 2 (PAN): "4111111111111111" → "16" + "4111111111111111"
     */
    LLNUM,

    /**
     * LLLNUM — Numérico variable con indicador de 3 dígitos.
     * Para numéricos variables más largos (hasta 999 dígitos).
     */
    LLLNUM,

    /**
     * BINARY — Binario de longitud fija.
     * El valor se serializa como representación hexadecimal (1 byte = 2 chars hex).
     * Ej: campo 52 (PIN Block, 8 bytes): 8 bytes → "0123456789ABCDEF" (16 chars)
     */
    BINARY,

    /**
     * LLBINARY — Binario de longitud variable con indicador de 2 dígitos.
     * Formato: [LL (cantidad de bytes)][VALOR en hex]
     * Ej: 3 bytes {0xAB, 0xCD, 0xEF} → "03" + "ABCDEF"
     */
    LLBINARY,

    /**
     * BITMAP — Campo de bitmap.
     * Se usa internamente para los campos 1 (bitmap primario) y 65 (bitmap secundario).
     * Normalmente no se define en los fieldDefs del usuario.
     */
    BITMAP,

    /**
     * MTI — Tipo especial para el Message Type Indicator.
     * Siempre son 4 bytes ASCII/numérico al inicio de la trama.
     */
    MTI,

    /**
     * BITMAP_HEX — Bitmap representado como cadena hexadecimal en la trama.
     * Esta implementación usa este formato: 8 bytes = 16 caracteres hex.
     */
    BITMAP_HEX,

    /**
     * PADDING — Campo de relleno que se ignora al leer.
     * Algunos esquemas propietarios insertan bytes de relleno entre campos.
     */
    PADDING
}