package com.spring.transactional.iso8583.main.TransactionalPackage.channel;

public enum FramingStrategy {
    /**
     * HEADER_4: Header binario de 4 bytes big-endian con la longitud del cuerpo.
     *
     * Trama: [B0 B1 B2 B3][...cuerpo ISO...]
     *   B0-B3 = longitud del cuerpo como int de 32 bits big-endian
     *
     * Ej: mensaje de 87 bytes → header = [0x00, 0x00, 0x00, 0x57]
     *
     * Ventaja: soporta mensajes de hasta ~2GB.
     * Uso: implementaciones propias, sistemas internos.
     */
    HEADER_4,

    /**
     * HEADER_2: Header binario de 2 bytes big-endian con la longitud del cuerpo.
     *
     * Trama: [B0 B1][...cuerpo ISO...]
     *   B0-B1 = longitud del cuerpo como short de 16 bits big-endian
     *
     * Ej: mensaje de 87 bytes → header = [0x00, 0x57]
     *
     * Ventaja: el más común en redes financieras reales (Visa, Mastercard, etc.).
     * Límite: mensajes de hasta 65535 bytes (más que suficiente para ISO 8583).
     * Uso: mayoría de procesadores y switches de pago.
     */
    HEADER_2,

    /**
     * RAW: Sin header de longitud. El servidor lee campo por campo según el bitmap.
     *
     * Trama: [...cuerpo ISO directamente, sin prefijo...]
     *   El receptor parsea el MTI, luego el bitmap, luego cada campo
     *   leyendo exactamente los bytes que corresponden a cada tipo de campo.
     *
     * Ventaja: compatible con clientes legacy/jPOS que no envían header.
     * Desventaja: más complejo de implementar en el receptor (requiere parseo incremental).
     * Uso: terminales POS legacy, algunos ATMs, clientes jPOS con NACChannel sin header.
     *
     * Este es el formato que causó el error en los logs:
     *   "Longitud inválida: 808464432" = 0x30323030 = "0200" en ASCII
     *   → el servidor intentó leer 4 bytes de header pero recibió el MTI directamente.
     */
    RAW
}
