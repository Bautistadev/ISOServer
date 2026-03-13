package com.spring.transactional.iso8583.main.TransactionalPackage.packager;

import org.springframework.stereotype.Component;

@Component("genericPackager")
public class GenericPackager extends ISOPackager {

    public GenericPackager() {
        // F00 | MTI
        addFieldDefinition(FieldDefinition.field(0)  .type(FieldType.MTI)    .length(4)
                .description("MESSAGE TYPE INDICATOR").build());

        // F01 | Bitmap
        addFieldDefinition(FieldDefinition.field(1)  .type(FieldType.BITMAP) .length(8)
                .description("BITMAP").build());

        // F02 | PAN | IFA_LLNUM 19
        addFieldDefinition(FieldDefinition.field(2)  .type(FieldType.LLNUM)  .length(19)
                .description("PRIMARY ACCOUNT NUMBER (PAN)").build());

        // F03 | Processing Code | IFA_NUMERIC 6
        addFieldDefinition(FieldDefinition.field(3)  .type(FieldType.NUMERIC).length(6)
                .description("PROCESSING CODE").build());

        // F04 | Amount, Transaction | IFA_NUMERIC 12
        addFieldDefinition(FieldDefinition.field(4)  .type(FieldType.NUMERIC).length(12)
                .description("AMOUNT, TRANSACTION").build());

        // F05 | Amount, Settlement | IFA_NUMERIC 12
        addFieldDefinition(FieldDefinition.field(5)  .type(FieldType.NUMERIC).length(12)
                .description("AMOUNT, SETTLEMENT").build());

        // F06 | Amount, Cardholder Billing | IFA_NUMERIC 12
        addFieldDefinition(FieldDefinition.field(6)  .type(FieldType.NUMERIC).length(12)
                .description("AMOUNT, CARDHOLDER BILLING").build());

        // F07 | Transmission Date and Time | IFA_NUMERIC 10
        addFieldDefinition(FieldDefinition.field(7)  .type(FieldType.NUMERIC).length(10)
                .description("TRANSMISSION DATE AND TIME").build());

        // F08 | Amount, Cardholder Billing Fee | IFA_NUMERIC 8
        addFieldDefinition(FieldDefinition.field(8)  .type(FieldType.NUMERIC).length(8)
                .description("AMOUNT, CARDHOLDER BILLING FEE").build());

        // F09 | Conversion Rate, Settlement | IFA_NUMERIC 8
        addFieldDefinition(FieldDefinition.field(9)  .type(FieldType.NUMERIC).length(8)
                .description("CONVERSION RATE, SETTLEMENT").build());

        // F10 | Conversion Rate, Cardholder Billing | IFA_NUMERIC 8
        addFieldDefinition(FieldDefinition.field(10) .type(FieldType.NUMERIC).length(8)
                .description("CONVERSION RATE, CARDHOLDER BILLING").build());

        // F11 | STAN | IFA_NUMERIC 6
        addFieldDefinition(FieldDefinition.field(11) .type(FieldType.NUMERIC).length(6)
                .description("SYSTEM TRACE AUDIT NUMBER (STAN)").build());

        // F12 | Time, Local Transaction | IFA_NUMERIC 6
        addFieldDefinition(FieldDefinition.field(12) .type(FieldType.NUMERIC).length(6)
                .description("TIME, LOCAL TRANSACTION").build());

        // F13 | Date, Local Transaction | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(13) .type(FieldType.NUMERIC).length(4)
                .description("DATE, LOCAL TRANSACTION").build());

        // F14 | Date, Expiration | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(14) .type(FieldType.NUMERIC).length(4)
                .description("DATE, EXPIRATION").build());

        // F15 | Date, Settlement | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(15) .type(FieldType.NUMERIC).length(4)
                .description("DATE, SETTLEMENT").build());

        // F16 | Date, Conversion | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(16) .type(FieldType.NUMERIC).length(4)
                .description("DATE, CONVERSION").build());

        // F17 | Date, Capture | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(17) .type(FieldType.NUMERIC).length(4)
                .description("DATE, CAPTURE").build());

        // F18 | MCC | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(18) .type(FieldType.NUMERIC).length(4)
                .description("MERCHANT TYPE (MCC)").build());

        // F19 | Country Code, Acquiring Institution | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(19) .type(FieldType.NUMERIC).length(3)
                .description("COUNTRY CODE, ACQUIRING INSTITUTION").build());

        // F20 | Country Code, PAN | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(20) .type(FieldType.NUMERIC).length(3)
                .description("COUNTRY CODE, PRIMARY ACCOUNT NUMBER").build());

        // F21 | Country Code, Forwarding Institution | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(21) .type(FieldType.NUMERIC).length(3)
                .description("COUNTRY CODE, FORWARDING INSTITUTION").build());

        // F22 | POS Entry Mode | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(22) .type(FieldType.NUMERIC).length(3)
                .description("POINT OF SERVICE ENTRY MODE").build());

        // F23 | Card Sequence Number | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(23) .type(FieldType.NUMERIC).length(3)
                .description("CARD SEQUENCE NUMBER").build());

        // F24 | NII | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(24) .type(FieldType.NUMERIC).length(3)
                .description("NETWORK INTERNATIONAL IDENTIFIER (NII)").build());

        // F25 | POS Condition Code | IFA_NUMERIC 2
        addFieldDefinition(FieldDefinition.field(25) .type(FieldType.NUMERIC).length(2)
                .description("POINT OF SERVICE CONDITION CODE").build());

        // F26 | POS PIN Capture Code | IFA_NUMERIC 2
        addFieldDefinition(FieldDefinition.field(26) .type(FieldType.NUMERIC).length(2)
                .description("POINT OF SERVICE PIN CAPTURE CODE").build());

        // F27 | Auth ID Response Length | IFA_NUMERIC 1
        addFieldDefinition(FieldDefinition.field(27) .type(FieldType.NUMERIC).length(1)
                .description("AUTHORIZATION IDENTIFICATION RESPONSE LENGTH").build());

        // F28 | Amount, Transaction Fee | IFA_NUMERIC 9
        addFieldDefinition(FieldDefinition.field(28) .type(FieldType.NUMERIC).length(9)
                .description("AMOUNT, TRANSACTION FEE").build());

        // F29 | Amount, Settlement Fee | IFA_NUMERIC 9
        addFieldDefinition(FieldDefinition.field(29) .type(FieldType.NUMERIC).length(9)
                .description("AMOUNT, SETTLEMENT FEE").build());

        // F30 | Amount, Transaction Processing Fee | IFA_NUMERIC 9
        addFieldDefinition(FieldDefinition.field(30) .type(FieldType.NUMERIC).length(9)
                .description("AMOUNT, TRANSACTION PROCESSING FEE").build());

        // F31 | Amount, Settlement Processing Fee | IFA_NUMERIC 9
        addFieldDefinition(FieldDefinition.field(31) .type(FieldType.NUMERIC).length(9)
                .description("AMOUNT, SETTLEMENT PROCESSING FEE").build());

        // F32 | Acquiring Institution ID | IFA_LLNUM 11
        addFieldDefinition(FieldDefinition.field(32) .type(FieldType.LLNUM)  .length(11)
                .description("ACQUIRING INSTITUTION IDENTIFICATION CODE").build());

        // F33 | Forwarding Institution ID | IFA_LLNUM 11
        addFieldDefinition(FieldDefinition.field(33) .type(FieldType.LLNUM)  .length(11)
                .description("FORWARDING INSTITUTION IDENTIFICATION CODE").build());

        // F34 | PAN, Extended | IFA_LLCHAR 28
        addFieldDefinition(FieldDefinition.field(34) .type(FieldType.LLVAR)  .length(28)
                .description("PRIMARY ACCOUNT NUMBER, EXTENDED").build());

        // F35 | Track 2 Data | IFA_LLCHAR 37
        addFieldDefinition(FieldDefinition.field(35) .type(FieldType.LLVAR)  .length(37)
                .description("TRACK 2 DATA").build());

        // F36 | Track 3 Data | IFA_LLLCHAR 104
        addFieldDefinition(FieldDefinition.field(36) .type(FieldType.LLLVAR) .length(104)
                .description("TRACK 3 DATA").build());

        // F37 | RRN | IF_CHAR 12
        addFieldDefinition(FieldDefinition.field(37) .type(FieldType.ALPHA)  .length(12)
                .description("RETRIEVAL REFERENCE NUMBER (RRN)").build());

        // F38 | Authorization ID Response | IF_CHAR 6
        addFieldDefinition(FieldDefinition.field(38) .type(FieldType.ALPHA)  .length(6)
                .description("AUTHORIZATION IDENTIFICATION RESPONSE").build());

        // F39 | Response Code | IF_CHAR 2
        addFieldDefinition(FieldDefinition.field(39) .type(FieldType.ALPHA)  .length(2)
                .description("RESPONSE CODE").build());

        // F40 | Service Restriction Code | IF_CHAR 3
        addFieldDefinition(FieldDefinition.field(40) .type(FieldType.ALPHA)  .length(3)
                .description("SERVICE RESTRICTION CODE").build());

        // F41 | Terminal ID | IF_CHAR 8
        addFieldDefinition(FieldDefinition.field(41) .type(FieldType.ALPHA)  .length(8)
                .description("CARD ACCEPTOR TERMINAL IDENTIFICATION (TID)").build());

        // F42 | Merchant ID | IF_CHAR 15
        addFieldDefinition(FieldDefinition.field(42) .type(FieldType.ALPHA)  .length(15)
                .description("CARD ACCEPTOR IDENTIFICATION CODE (MID)").build());

        // F43 | Merchant Name/Location | IF_CHAR 40
        addFieldDefinition(FieldDefinition.field(43) .type(FieldType.ALPHA)  .length(40)
                .description("CARD ACCEPTOR NAME/LOCATION").build());

        // F44 | Additional Response Data | IFA_LLCHAR 25
        addFieldDefinition(FieldDefinition.field(44) .type(FieldType.LLVAR)  .length(25)
                .description("ADDITIONAL RESPONSE DATA").build());

        // F45 | Track 1 Data | IFA_LLCHAR 76
        addFieldDefinition(FieldDefinition.field(45) .type(FieldType.LLVAR)  .length(76)
                .description("TRACK 1 DATA").build());

        // F46 | Additional Data - ISO | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(46) .type(FieldType.LLLVAR) .length(999)
                .description("ADDITIONAL DATA - ISO").build());

        // F47 | Additional Data - National | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(47) .type(FieldType.LLLVAR) .length(999)
                .description("ADDITIONAL DATA - NATIONAL").build());

        // F48 | Additional Data - Private | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(48) .type(FieldType.LLLVAR) .length(999)
                .description("ADDITIONAL DATA - PRIVATE").build());

        // F49 | Currency Code, Transaction | IF_CHAR 3
        addFieldDefinition(FieldDefinition.field(49) .type(FieldType.ALPHA)  .length(3)
                .description("CURRENCY CODE, TRANSACTION").build());

        // F50 | Currency Code, Settlement | IF_CHAR 3
        addFieldDefinition(FieldDefinition.field(50) .type(FieldType.ALPHA)  .length(3)
                .description("CURRENCY CODE, SETTLEMENT").build());

        // F51 | Currency Code, Cardholder Billing | IF_CHAR 3
        addFieldDefinition(FieldDefinition.field(51) .type(FieldType.ALPHA)  .length(3)
                .description("CURRENCY CODE, CARDHOLDER BILLING").build());

        // F52 | PIN Data | IFA_BINARY 8
        addFieldDefinition(FieldDefinition.field(52) .type(FieldType.BINARY) .length(8)
                .description("PERSONAL IDENTIFICATION NUMBER (PIN) DATA").build());

        // F53 | Security Related Control Information | IFA_NUMERIC 16
        addFieldDefinition(FieldDefinition.field(53) .type(FieldType.NUMERIC).length(16)
                .description("SECURITY RELATED CONTROL INFORMATION").build());

        // F54 | Additional Amounts | IFA_LLLCHAR 120
        addFieldDefinition(FieldDefinition.field(54) .type(FieldType.LLLVAR) .length(120)
                .description("ADDITIONAL AMOUNTS").build());

        // F55 | ICC Data (EMV) | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(55) .type(FieldType.LLLVAR) .length(999)
                .description("ICC DATA (EMV)").build());

        // F56–F63 | Reserved ISO / National / Private | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(56) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO").build());
        addFieldDefinition(FieldDefinition.field(57) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL").build());
        addFieldDefinition(FieldDefinition.field(58) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL").build());
        addFieldDefinition(FieldDefinition.field(59) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL").build());
        addFieldDefinition(FieldDefinition.field(60) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE").build());
        addFieldDefinition(FieldDefinition.field(61) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE").build());
        addFieldDefinition(FieldDefinition.field(62) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE").build());
        addFieldDefinition(FieldDefinition.field(63) .type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE").build());

        // F64 | MAC Primary | IFA_BINARY 8
        addFieldDefinition(FieldDefinition.field(64) .type(FieldType.BINARY) .length(8)
                .description("MESSAGE AUTHENTICATION CODE (MAC)").build());

        // F65 | Bitmap Extended | IFA_BINARY 8
        addFieldDefinition(FieldDefinition.field(65) .type(FieldType.BINARY) .length(8)
                .description("BITMAP, EXTENDED (TERTIARY)").build());

        // F66 | Settlement Code | IFA_NUMERIC 1
        addFieldDefinition(FieldDefinition.field(66) .type(FieldType.NUMERIC).length(1)
                .description("SETTLEMENT CODE").build());

        // F67 | Extended Payment Code | IFA_NUMERIC 2
        addFieldDefinition(FieldDefinition.field(67) .type(FieldType.NUMERIC).length(2)
                .description("EXTENDED PAYMENT CODE").build());

        // F68 | Receiving Institution Country Code | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(68) .type(FieldType.NUMERIC).length(3)
                .description("RECEIVING INSTITUTION COUNTRY CODE").build());

        // F69 | Settlement Institution Country Code | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(69) .type(FieldType.NUMERIC).length(3)
                .description("SETTLEMENT INSTITUTION COUNTRY CODE").build());

        // F70 | Network Management Information Code | IFA_NUMERIC 3
        addFieldDefinition(FieldDefinition.field(70) .type(FieldType.NUMERIC).length(3)
                .description("NETWORK MANAGEMENT INFORMATION CODE").build());

        // F71 | Message Number | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(71) .type(FieldType.NUMERIC).length(4)
                .description("MESSAGE NUMBER").build());

        // F72 | Message Number, Last | IFA_NUMERIC 4
        addFieldDefinition(FieldDefinition.field(72) .type(FieldType.NUMERIC).length(4)
                .description("MESSAGE NUMBER, LAST").build());

        // F73 | Date, Action | IFA_NUMERIC 6
        addFieldDefinition(FieldDefinition.field(73) .type(FieldType.NUMERIC).length(6)
                .description("DATE, ACTION").build());

        // F74–F81 | Totalizadores | IFA_NUMERIC 10
        addFieldDefinition(FieldDefinition.field(74) .type(FieldType.NUMERIC).length(10)
                .description("CREDITS, NUMBER").build());
        addFieldDefinition(FieldDefinition.field(75) .type(FieldType.NUMERIC).length(10)
                .description("CREDITS, REVERSAL NUMBER").build());
        addFieldDefinition(FieldDefinition.field(76) .type(FieldType.NUMERIC).length(10)
                .description("DEBITS, NUMBER").build());
        addFieldDefinition(FieldDefinition.field(77) .type(FieldType.NUMERIC).length(10)
                .description("DEBITS, REVERSAL NUMBER").build());
        addFieldDefinition(FieldDefinition.field(78) .type(FieldType.NUMERIC).length(10)
                .description("TRANSFER, NUMBER").build());
        addFieldDefinition(FieldDefinition.field(79) .type(FieldType.NUMERIC).length(10)
                .description("TRANSFER, REVERSAL NUMBER").build());
        addFieldDefinition(FieldDefinition.field(80) .type(FieldType.NUMERIC).length(10)
                .description("INQUIRIES, NUMBER").build());
        addFieldDefinition(FieldDefinition.field(81) .type(FieldType.NUMERIC).length(10)
                .description("AUTHORIZATIONS, NUMBER").build());

        // F82–F85 | Fee Amounts | IFA_NUMERIC 12
        addFieldDefinition(FieldDefinition.field(82) .type(FieldType.NUMERIC).length(12)
                .description("CREDITS, PROCESSING FEE AMOUNT").build());
        addFieldDefinition(FieldDefinition.field(83) .type(FieldType.NUMERIC).length(12)
                .description("CREDITS, TRANSACTION FEE AMOUNT").build());
        addFieldDefinition(FieldDefinition.field(84) .type(FieldType.NUMERIC).length(12)
                .description("DEBITS, PROCESSING FEE AMOUNT").build());
        addFieldDefinition(FieldDefinition.field(85) .type(FieldType.NUMERIC).length(12)
                .description("DEBITS, TRANSACTION FEE AMOUNT").build());

        // F86–F89 | Amounts | IFA_NUMERIC 16
        addFieldDefinition(FieldDefinition.field(86) .type(FieldType.NUMERIC).length(16)
                .description("CREDITS, AMOUNT").build());
        addFieldDefinition(FieldDefinition.field(87) .type(FieldType.NUMERIC).length(16)
                .description("CREDITS, REVERSAL AMOUNT").build());
        addFieldDefinition(FieldDefinition.field(88) .type(FieldType.NUMERIC).length(16)
                .description("DEBITS, AMOUNT").build());
        addFieldDefinition(FieldDefinition.field(89) .type(FieldType.NUMERIC).length(16)
                .description("DEBITS, REVERSAL AMOUNT").build());

        // F90 | Original Data Elements | IFA_NUMERIC 42
        addFieldDefinition(FieldDefinition.field(90) .type(FieldType.NUMERIC).length(42)
                .description("ORIGINAL DATA ELEMENTS").build());

        // F91 | File Update Code | IF_CHAR 1
        addFieldDefinition(FieldDefinition.field(91) .type(FieldType.ALPHA)  .length(1)
                .description("FILE UPDATE CODE").build());

        // F92 | File Security Code | IF_CHAR 2
        addFieldDefinition(FieldDefinition.field(92) .type(FieldType.ALPHA)  .length(2)
                .description("FILE SECURITY CODE").build());

        // F93 | Response Indicator | IF_CHAR 5
        addFieldDefinition(FieldDefinition.field(93) .type(FieldType.ALPHA)  .length(5)
                .description("RESPONSE INDICATOR").build());

        // F94 | Service Indicator | IF_CHAR 7
        addFieldDefinition(FieldDefinition.field(94) .type(FieldType.ALPHA)  .length(7)
                .description("SERVICE INDICATOR").build());

        // F95 | Replacement Amounts | IF_CHAR 42
        addFieldDefinition(FieldDefinition.field(95) .type(FieldType.ALPHA)  .length(42)
                .description("REPLACEMENT AMOUNTS").build());

        // F96 | Message Security Code | IFA_BINARY 8
        addFieldDefinition(FieldDefinition.field(96) .type(FieldType.BINARY) .length(8)
                .description("MESSAGE SECURITY CODE").build());

        // F97 | Amount, Net Settlement | IF_CHAR 17
        addFieldDefinition(FieldDefinition.field(97) .type(FieldType.ALPHA)  .length(17)
                .description("AMOUNT, NET SETTLEMENT").build());

        // F98 | Payee | IF_CHAR 25
        addFieldDefinition(FieldDefinition.field(98) .type(FieldType.ALPHA)  .length(25)
                .description("PAYEE").build());

        // F99 | Settlement Institution ID | IFA_LLNUM 11
        addFieldDefinition(FieldDefinition.field(99) .type(FieldType.LLNUM)  .length(11)
                .description("SETTLEMENT INSTITUTION IDENTIFICATION CODE").build());

        // F100 | Receiving Institution ID | IFA_LLNUM 11
        addFieldDefinition(FieldDefinition.field(100).type(FieldType.LLNUM)  .length(11)
                .description("RECEIVING INSTITUTION IDENTIFICATION CODE").build());

        // F101 | File Name | IFA_LLCHAR 17
        addFieldDefinition(FieldDefinition.field(101).type(FieldType.LLVAR)  .length(17)
                .description("FILE NAME").build());

        // F102 | Account Identification 1 | IFA_LLCHAR 28
        addFieldDefinition(FieldDefinition.field(102).type(FieldType.LLVAR)  .length(28)
                .description("ACCOUNT IDENTIFICATION 1").build());

        // F103 | Account Identification 2 | IFA_LLCHAR 28
        addFieldDefinition(FieldDefinition.field(103).type(FieldType.LLVAR)  .length(28)
                .description("ACCOUNT IDENTIFICATION 2").build());

        // F104 | Transaction Description | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(104).type(FieldType.LLLVAR) .length(999)
                .description("TRANSACTION DESCRIPTION").build());

        // F105–F111 | Reserved ISO Use | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(105).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());
        addFieldDefinition(FieldDefinition.field(106).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());
        addFieldDefinition(FieldDefinition.field(107).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());
        addFieldDefinition(FieldDefinition.field(108).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());
        addFieldDefinition(FieldDefinition.field(109).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());
        addFieldDefinition(FieldDefinition.field(110).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());
        addFieldDefinition(FieldDefinition.field(111).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED ISO USE").build());

        // F112–F119 | Reserved National Use | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(112).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(113).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(114).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(115).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(116).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(117).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(118).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());
        addFieldDefinition(FieldDefinition.field(119).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED NATIONAL USE").build());

        // F120–F127 | Reserved Private - Fiserv | IFA_LLLCHAR 999
        addFieldDefinition(FieldDefinition.field(120).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(121).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(122).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(123).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(124).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(125).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(126).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());
        addFieldDefinition(FieldDefinition.field(127).type(FieldType.LLLVAR) .length(999)
                .description("RESERVED PRIVATE - Fiserv").build());

        // F128 | MAC Secondary | IFA_BINARY 8
        addFieldDefinition(FieldDefinition.field(128).type(FieldType.BINARY) .length(8)
                .description("MESSAGE AUTHENTICATION CODE (MAC) - SECONDARY").build());
    }
}