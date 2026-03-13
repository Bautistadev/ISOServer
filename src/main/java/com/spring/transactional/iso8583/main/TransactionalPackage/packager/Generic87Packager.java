package com.spring.transactional.iso8583.main.TransactionalPackage.packager;


import org.springframework.stereotype.Component;

/**
 * Implementación concreta del packager para ISO 8583 versión 1987.
 *
 * Define los campos estándar del protocolo ISO 8583-1987, que es el esquema
 * más usado en redes de pago de América Latina y muchos procesadores legacy.
 *
 * Para personalizar: crear una subclase o instanciar directamente y llamar
 * a addFieldDefinition() con los campos propietarios de tu esquema.
 *
 * Se registra como bean Spring con nombre "iso87Packager" para inyección.
 * Equivalente a GenericPackager configurado con el XML de ISO87APackager en jPOS.
 */

public class Generic87Packager extends ISOPackager {

    public Generic87Packager() {
        // ── Campos primarios (1-64) ───────────────────────────────────────────

        // Campo 2: PAN — Primary Account Number
        // Número de tarjeta, longitud variable hasta 19 dígitos
        // LLNUM: el indicador de longitud tiene 2 dígitos ("16" para una Visa de 16 dígitos)
        addFieldDefinition(FieldDefinition.field(2)
                .type(FieldType.LLNUM).length(19)
                .description("Primary Account Number (PAN)").mandatory().build());

        // Campo 3: Processing Code — 6 dígitos que indican tipo de operación
        // Los 2 primeros = tipo (00=compra, 01=retiro, 28=pago de servicio, etc.)
        // Los 2 siguientes = cuenta de origen, los 2 últimos = cuenta de destino
        addFieldDefinition(FieldDefinition.field(3)
                .type(FieldType.NUMERIC).length(6)
                .description("Processing Code").build());

        // Campo 4: Amount, Transaction — Monto de la transacción
        // 12 dígitos en centavos (sin punto decimal). Ej: $10.00 = "000000001000"
        addFieldDefinition(FieldDefinition.field(4)
                .type(FieldType.NUMERIC).length(12)
                .description("Amount, Transaction").build());

        // Campo 5: Amount, Settlement — Monto en moneda de liquidación
        addFieldDefinition(FieldDefinition.field(5)
                .type(FieldType.NUMERIC).length(12)
                .description("Amount, Settlement").build());

        // Campo 6: Amount, Cardholder Billing — Monto en moneda del tarjetahabiente
        addFieldDefinition(FieldDefinition.field(6)
                .type(FieldType.NUMERIC).length(12)
                .description("Amount, Cardholder Billing").build());

        // Campo 7: Transmission Date and Time — Fecha/hora en que el adquirente envió el mensaje
        // Formato: MMDDhhmmss (10 dígitos). Ej: "0315143022" = 15-Mar, 14:30:22
        addFieldDefinition(FieldDefinition.field(7)
                .type(FieldType.NUMERIC).length(10)
                .description("Transmission Date and Time").build());

        // Campo 11: STAN — System Trace Audit Number
        // Número de 6 dígitos asignado por el adquirente para identificar la transacción.
        // Único dentro del mismo día. Se usa para correlacionar solicitud ↔ respuesta.
        addFieldDefinition(FieldDefinition.field(11)
                .type(FieldType.NUMERIC).length(6)
                .description("System Trace Audit Number (STAN)").build());

        // Campo 12: Local Transaction Time — Hora local del terminal
        // Formato: hhmmss. Ej: "143022" = 14:30:22
        addFieldDefinition(FieldDefinition.field(12)
                .type(FieldType.NUMERIC).length(6)
                .description("Local Transaction Time (hhmmss)").build());

        // Campo 13: Local Transaction Date — Fecha local del terminal
        // Formato: MMDD. Ej: "0315" = 15 de marzo
        addFieldDefinition(FieldDefinition.field(13)
                .type(FieldType.NUMERIC).length(4)
                .description("Local Transaction Date (MMDD)").build());

        // Campo 14: Expiration Date — Vencimiento de la tarjeta
        // Formato: YYMM. Ej: "2512" = diciembre 2025
        addFieldDefinition(FieldDefinition.field(14)
                .type(FieldType.NUMERIC).length(4)
                .description("Expiration Date (YYMM)").build());

        // Campo 15: Settlement Date — Fecha de liquidación
        addFieldDefinition(FieldDefinition.field(15)
                .type(FieldType.NUMERIC).length(4)
                .description("Settlement Date").build());

        // Campo 18: Merchant Category Code (MCC) — Código de rubro del comercio
        // 4 dígitos del estándar ISO 18245. Ej: 5411=supermercado, 5912=farmacia
        addFieldDefinition(FieldDefinition.field(18)
                .type(FieldType.NUMERIC).length(4)
                .description("Merchant Category Code (MCC)").build());

        // Campo 22: Point of Service Entry Mode
        // Indica cómo se capturó la información de la tarjeta.
        // Ej: 051=chip, 071=contactless, 011=banda magnética, 010=manual
        addFieldDefinition(FieldDefinition.field(22)
                .type(FieldType.NUMERIC).length(3)
                .description("Point of Service Entry Mode").build());

        // Campo 23: Card Sequence Number — Número de secuencia de la tarjeta
        // Para tarjetas con múltiples instancias del mismo PAN
        addFieldDefinition(FieldDefinition.field(23)
                .type(FieldType.NUMERIC).length(3)
                .description("Card Sequence Number").build());

        // Campo 25: Point of Service Condition Code
        // Describe las condiciones del punto de servicio donde se realizó la transacción
        addFieldDefinition(FieldDefinition.field(25)
                .type(FieldType.NUMERIC).length(2)
                .description("Point of Service Condition Code").build());

        // Campo 32: Acquiring Institution ID Code
        // Identificador del banco/institución adquirente (quien recibe el pago)
        // LLNUM: longitud variable hasta 11 dígitos
        addFieldDefinition(FieldDefinition.field(32)
                .type(FieldType.LLNUM).length(11)
                .description("Acquiring Institution ID Code").build());

        // Campo 33: Forwarding Institution ID Code
        // Identificador de la institución que reenvía el mensaje (en redes multi-hop)
        addFieldDefinition(FieldDefinition.field(33)
                .type(FieldType.LLNUM).length(11)
                .description("Forwarding Institution ID Code").build());

        // Campo 35: Track 2 Data — Datos de la pista 2 de la tarjeta
        // Contiene PAN, fecha de vencimiento y datos de servicio separados por "="
        // Ej: "4111111111111111=2512101000000000"
        addFieldDefinition(FieldDefinition.field(35)
                .type(FieldType.LLVAR).length(37)
                .description("Track 2 Data").build());

        // Campo 37: Retrieval Reference Number (RRN)
        // 12 caracteres alfanuméricos asignados por el adquirente.
        // Sirve para referencias, devoluciones y conciliación.
        addFieldDefinition(FieldDefinition.field(37)
                .type(FieldType.ALPHA).length(12)
                .description("Retrieval Reference Number (RRN)").build());

        // Campo 38: Authorization Identification Response (Auth Code)
        // Código de autorización de 6 caracteres que retorna el emisor al aprobar.
        // Ej: "AUTH01", "123456"
        addFieldDefinition(FieldDefinition.field(38)
                .type(FieldType.ALPHA).length(6)
                .description("Authorization Identification Response").build());

        // Campo 39: Response Code — Código de respuesta del emisor
        // 2 caracteres: "00"=aprobado, "51"=fondos insuficientes, "54"=expirada, etc.
        addFieldDefinition(FieldDefinition.field(39)
                .type(FieldType.ALPHA).length(2)
                .description("Response Code").build());

        // Campo 41: Card Acceptor Terminal ID (TID)
        // Identificador único del terminal POS, 8 caracteres alfanuméricos
        addFieldDefinition(FieldDefinition.field(41)
                .type(FieldType.ALPHA).length(8)
                .description("Card Acceptor Terminal ID (TID)").build());

        // Campo 42: Card Acceptor ID Code (MID)
        // Identificador único del comercio, 15 caracteres alfanuméricos
        addFieldDefinition(FieldDefinition.field(42)
                .type(FieldType.ALPHA).length(15)
                .description("Card Acceptor ID Code (MID)").build());

        // Campo 43: Card Acceptor Name/Location
        // Nombre y dirección del comercio, 40 caracteres.
        // Formato típico: "NombreComercio       Ciudad     País"
        addFieldDefinition(FieldDefinition.field(43)
                .type(FieldType.ALPHA).length(40)
                .description("Card Acceptor Name/Location").build());

        // Campo 44: Additional Response Data
        // Datos adicionales que el emisor puede devolver (ej: mensaje al tarjetahabiente)
        addFieldDefinition(FieldDefinition.field(44)
                .type(FieldType.LLVAR).length(25)
                .description("Additional Response Data").build());

        // Campo 45: Track 1 Data — Pista 1 de la tarjeta (más datos que la pista 2)
        // Contiene nombre del tarjetahabiente además de PAN y vencimiento
        addFieldDefinition(FieldDefinition.field(45)
                .type(FieldType.LLVAR).length(76)
                .description("Track 1 Data").build());

        // Campo 48: Additional Data - Private
        // Campo de uso libre por el esquema propietario de cada red.
        // Muy frecuente para datos adicionales de la transacción, sub-campos TLV, etc.
        addFieldDefinition(FieldDefinition.field(48)
                .type(FieldType.LLLVAR).length(999)
                .description("Additional Data - Private").build());

        // Campo 49: Currency Code, Transaction
        // Código de moneda ISO 4217 en formato numérico. Ej: "840"=USD, "032"=ARS, "484"=MXN
        addFieldDefinition(FieldDefinition.field(49)
                .type(FieldType.ALPHA).length(3)
                .description("Currency Code, Transaction").build());

        // Campo 52: PIN Data — Bloque PIN cifrado
        // Siempre 8 bytes (64 bits) en formato binario, cifrado con la llave de la sesión
        addFieldDefinition(FieldDefinition.field(52)
                .type(FieldType.BINARY).length(8)
                .description("PIN Data").build());

        // Campo 54: Additional Amounts
        // Montos adicionales (ej: cashback, propina, saldo disponible)
        // Cada monto ocupa 20 posiciones en el formato estándar
        addFieldDefinition(FieldDefinition.field(54)
                .type(FieldType.LLLVAR).length(120)
                .description("Additional Amounts").build());

        // Campo 55: ICC Data (EMV)
        // Datos del chip EMV en formato TLV (Tag-Length-Value).
        // Contiene los datos intercambiados durante el proceso de chip (ARQC, AIP, ATC, etc.)
        addFieldDefinition(FieldDefinition.field(55)
                .type(FieldType.LLLVAR).length(999)
                .description("ICC Data (EMV)").build());

        // Campos 57-63: Reservados para uso privado/nacional
        // Cada red puede definir su propio contenido para estos campos
        for (int i = 57; i <= 63; i++)
            addFieldDefinition(FieldDefinition.field(i)
                    .type(FieldType.LLLVAR).length(999)
                    .description("Reserved Private " + i).build());

        // Campo 64: MAC Primario — Message Authentication Code
        // 8 bytes generados con 3DES/AES para verificar integridad del mensaje.
        // El receptor recalcula el MAC y lo compara para detectar alteraciones.
        addFieldDefinition(FieldDefinition.field(64)
                .type(FieldType.BINARY).length(8)
                .description("Message Authentication Code (MAC) Primary").build());

        // ── Campos secundarios (65-128) ───────────────────────────────────────

        // Campo 70: Network Management Information Code
        // 3 dígitos que identifican el tipo de mensaje de gestión de red.
        // Ej: 001=Sign-On, 002=Sign-Off, 301=Key Exchange
        addFieldDefinition(FieldDefinition.field(70)
                .type(FieldType.NUMERIC).length(3)
                .description("Network Management Information Code").build());

        // Campo 90: Original Data Elements
        // Datos del mensaje original en transacciones de reversión.
        // Contiene: MTI original + STAN original + Fecha/hora + Acquiring ID + Forwarding ID
        addFieldDefinition(FieldDefinition.field(90)
                .type(FieldType.NUMERIC).length(42)
                .description("Original Data Elements").build());

        // Campo 100: Receiving Institution ID Code
        // Identificador de la institución receptora final del mensaje
        addFieldDefinition(FieldDefinition.field(100)
                .type(FieldType.LLNUM).length(11)
                .description("Receiving Institution ID Code").build());

        // Campo 102: Account Identification 1
        // Número de cuenta origen en transferencias y pagos
        addFieldDefinition(FieldDefinition.field(102)
                .type(FieldType.LLVAR).length(28)
                .description("Account Identification 1").build());

        // Campo 103: Account Identification 2
        // Número de cuenta destino en transferencias
        addFieldDefinition(FieldDefinition.field(103)
                .type(FieldType.LLVAR).length(28)
                .description("Account Identification 2").build());

        // Campo 128: MAC Secundario
        // Segundo MAC, presente cuando el mensaje usa bitmap secundario y
        // el esquema requiere autenticación también del bitmap secundario
        addFieldDefinition(FieldDefinition.field(128)
                .type(FieldType.BINARY).length(8)
                .description("Message Authentication Code (MAC) Secondary").build());
    }
}