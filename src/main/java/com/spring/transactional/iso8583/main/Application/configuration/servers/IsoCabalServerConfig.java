package com.spring.transactional.iso8583.main.Application.configuration.servers;

import com.spring.transactional.iso8583.main.Application.listeners.CabalListener;
import com.spring.transactional.iso8583.main.TransactionalPackage.channel.FramingStrategy;
import com.spring.transactional.iso8583.main.TransactionalPackage.logs.ISOLogger;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.ISOPackager;
import com.spring.transactional.iso8583.main.TransactionalPackage.server.ISORequestListener;
import com.spring.transactional.iso8583.main.TransactionalPackage.server.ISOServer;
import com.spring.transactional.iso8583.main.TransactionalPackage.util.ISOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class IsoCabalServerConfig {

    @Value("${iso.servers.cabal.port}")
    private int serverPort;

    @Value("${iso.servers.cabal.framing}")
    private String serverFraming;

    @Value("${iso.servers.cabal.thread-pool-size}")
    private int threadPoolSize;

    @Value("${iso.servers.cabal.name}")
    private String name;

    @Bean
    public ISORequestListener cabalListener(){
        return new CabalListener(8082);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ISOServer isoServercabal(@Qualifier("isoPackager")  ISOPackager packager ,@Qualifier("cabalListener") ISORequestListener isoRequestListener) {
        FramingStrategy strategy = FramingStrategy.valueOf(serverFraming.toUpperCase());
        return new ISOServer(serverPort, packager, isoRequestListener, strategy,threadPoolSize,name); //--> HEADER DEL MENSAJE CAMBIAR DEPENDIENDO DEL TIPO DE ISO
    }

    /**
     * Enruta el mensaje al handler correcto según el MTI.
     */
    private ISOMsg procesarMensaje(ISOMsg request) {
        String mti = request.getMTI();
        if (mti == null) {
            ISOMsg err = request.createResponse();
            err.set(39, "30");
            return err;
        }

        return switch (mti) {
            case "0100" -> handlePreAuth(request);
            case "0200" -> handleAutorizacion(request);
            case "0400" -> handleReverso(request);
            case "0420" -> handleReversalAdvice(request);
            case "0600" -> handleConsultaSaldo(request);
            case "0800" -> handleNetworkManagement(request);
            default     -> handleDesconocido(request);
        };
    }

// ── 0100 — Pre-autorización ───────────────────────────────────────────

    private ISOMsg handlePreAuth(ISOMsg request) {
        log.info("  [0100] Pre-autorización | PAN={} | Monto={}",
                ISOUtils.maskPAN(request.getString(2)),
                request.getString(4));

        ISOMsg response = request.createResponse(); // → 0110
        response.set(38, "PREA01");   // Código de pre-autorización
        response.set(39, "00");       // Aprobado
        if (!response.hasField(37)) response.set(37, ISOUtils.generateRRN());
        return response;
    }

// ── 0200 — Autorización ───────────────────────────────────────────────

    private ISOMsg handleAutorizacion(ISOMsg request) {
        log.info("  [0200] Autorización | PAN={} | Monto={}",
                ISOUtils.maskPAN(request.getString(2)),
                request.getString(4));

        ISOMsg response = request.createResponse(); // → 0210
        response.set(38, "AUTH81");
        response.set(39, "00");
        if (!response.hasField(37)) response.set(37, ISOUtils.generateRRN());
        return response;
    }

// ── 0400 — Reverso ────────────────────────────────────────────────────

    private ISOMsg handleReverso(ISOMsg request) {
        log.info("  [0400] Reverso | PAN={} | F90={}",
                ISOUtils.maskPAN(request.getString(2)),
                request.getString(90));

        ISOMsg response = request.createResponse(); // → 0410
        response.set(39, "00");
        if (!response.hasField(37)) response.set(37, ISOUtils.generateRRN());
        return response;
    }

// ── 0420 — Reversal Advice ────────────────────────────────────────────

    private ISOMsg handleReversalAdvice(ISOMsg request) {
        log.info("  [0420] Reversal Advice | PAN={} | F90={}",
                ISOUtils.maskPAN(request.getString(2)),
                request.getString(90));

        ISOMsg response = request.createResponse(); // → 0430
        response.set(39, "00");
        if (!response.hasField(37)) response.set(37, ISOUtils.generateRRN());
        return response;
    }

// ── 0600 — Consulta de Saldo ──────────────────────────────────────────

    private ISOMsg handleConsultaSaldo(ISOMsg request) {
        log.info("  [0600] Consulta de saldo | PAN={}",
                ISOUtils.maskPAN(request.getString(2)));

        ISOMsg response = request.createResponse(); // → 0610
        response.set(39, "00");

        // Campo 54: Additional Amounts — saldo disponible y saldo ledger
        // Formato por bloque de 20 chars:
        //   TipoCuenta(2) + TipoCuenta(2) + Moneda(3) + Signo(1) + Monto(12)
        // 20 = cuenta corriente / saldo disponible = $50,000.00
        // 40 = cuenta corriente / saldo ledger     = $52,000.00
        response.set(54, "20" + "20" + "032" + "C" + "000005000000"
                + "40" + "20" + "032" + "C" + "000005200000");

        if (!response.hasField(37)) response.set(37, ISOUtils.generateRRN());
        return response;
    }

// ── 0800 — Network Management ─────────────────────────────────────────

    private ISOMsg handleNetworkManagement(ISOMsg request) {
        String networkCode = request.getString(70);
        log.info("  [0800] Network Management | Código={}",  networkCode);

        ISOMsg response = request.createResponse(); // → 0810
        response.set(70, networkCode);
        response.set(39, "00");

        if ("101".equals(networkCode)) {
            log.info("  [0800/101] Key Exchange procesado");
            // En producción: procesar la llave del F48 y retornar la llave de sesión
            response.set(48, "FEDCBA9876543210FEDCBA9876543210"); // Llave de respuesta simulada
        }

        return response;
    }

// ── MTI desconocido ───────────────────────────────────────────────────

    private ISOMsg handleDesconocido(ISOMsg request) {
        log.warn("  MTI no reconocido: {}", request.getMTI());
        ISOMsg response = request.createResponse();
        response.set(39, "12"); // Transacción inválida
        return response;
    }
}
