package com.spring.transactional.iso8583.main.TransactionalPackage.channel;


import com.spring.transactional.iso8583.main.TransactionalPackage.exception.ISOException;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.ISOPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cliente ISO 8583 de alto nivel, thread-safe y con reconexión automática.
 *
 * Envuelve a ISOChannel y agrega:
 *   - Thread safety mediante ReentrantLock (múltiples hilos pueden llamar sendAndReceive())
 *   - Reconexión automática: si el canal cae, intenta reconectar antes de reintentar el envío
 *   - Gestión de ciclo de vida Spring: DisposableBean cierra el canal al destruir el contexto
 *
 * Uso típico en Spring Boot:
 *   @Bean
 *   public ISOClient isoClient(ISOPackager packager) {
 *       return new ISOClient("10.0.0.1", 8583, packager);
 *   }
 *
 *   // En un Service:
 *   ISOMsg response = isoClient.sendAndReceive(request);
 */
public class ISOClient implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ISOClient.class);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SLINE = "─".repeat(64);

    private final String host;
    private final int port;
    private final ISOPackager packager;
    private ISOChannel channel;
    private final ReentrantLock lock = new ReentrantLock();

    private int maxRetries    = 3;
    private long retryDelayMs = 1_000L;
    private int timeoutMs     = 30_000;

    public ISOClient(String host, int port, ISOPackager packager) {
        this.host = host; this.port = port; this.packager = packager;
    }

    public ISOMsg sendAndReceive(ISOMsg request) throws ISOException, IOException {
        lock.lock();
        try {
            ensureConnected();
            channel.send(request);
            return channel.receive();
        } catch (IOException e) {
            log.warn("  Error de I/O, reconectando: {}", e.getMessage());
            reconnect();
            channel.send(request);
            return channel.receive();
        } finally {
            lock.unlock();
        }
    }

    public void send(ISOMsg msg) throws ISOException, IOException {
        lock.lock();
        try { ensureConnected(); channel.send(msg); }
        finally { lock.unlock(); }
    }

    public void connect() throws IOException {
        lock.lock();
        try {
            if (channel != null && channel.isConnected()) return;

            channel = new ISOChannel(host, port, packager);
            channel.setReadTimeoutMs(timeoutMs);
            channel.connect();

            // ✅ Log detallado de conexión establecida — equivalente al jPOS "connected"
            log.info("");
            log.info("  ┌{}┐", SLINE);
            log.info("  │  ✔  CONEXIÓN ESTABLECIDA CON EL SERVIDOR");
            log.info("  │  {}", String.format("%-22s  %s", "Host remoto:",   host));
            log.info("  │  {}", String.format("%-22s  %s", "Puerto remoto:", ":" + port));
            log.info("  │  {}", String.format("%-22s  %s", "Timeout lectura:", timeoutMs + " ms"));
            log.info("  │  {}", String.format("%-22s  %s", "Timestamp:",
                    LocalDateTime.now().format(DT_FMT)));
            log.info("  └{}┘", SLINE);
            log.info("");

        } finally {
            lock.unlock();
        }
    }

    private void ensureConnected() throws IOException {
        if (channel == null || !channel.isConnected()) connect();
    }

    private void reconnect() throws IOException {
        if (channel != null) channel.close();
        IOException lastEx = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("");
                log.info("  ┌{}┐", SLINE);
                log.info("  │  ↺  RECONECTANDO  │  Intento {}/{}  │  {}:{}",
                        attempt, maxRetries, host, port);
                log.info("  └{}┘", SLINE);

                channel = new ISOChannel(host, port, packager);
                channel.setReadTimeoutMs(timeoutMs);
                channel.connect();

                log.info("");
                log.info("  ┌{}┐", SLINE);
                log.info("  │  ✔  RECONEXIÓN EXITOSA  │  {}:{}", host, port);
                log.info("  └{}┘", SLINE);
                log.info("");
                return;

            } catch (IOException e) {
                lastEx = e;
                log.warn("  Intento {} fallido: {}", attempt, e.getMessage());
                try { Thread.sleep(retryDelayMs); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw lastEx != null ? lastEx
                : new IOException("No se pudo reconectar a " + host + ":" + port);
    }

    public boolean isConnected()         { return channel != null && channel.isConnected(); }
    public void setMaxRetries(int n)     { this.maxRetries = n; }
    public void setRetryDelayMs(long ms) { this.retryDelayMs = ms; }
    public void setTimeoutMs(int ms)     { this.timeoutMs = ms; }
    public String getHost()              { return host; }
    public int getPort()                 { return port; }

    @Override
    public void destroy() {
        lock.lock();
        try {
            if (channel != null) {
                channel.close();
                log.info("");
                log.info("  ┌{}┐", SLINE);
                log.info("  │  ■  CLIENTE DESCONECTADO  │  {}:{}", host, port);
                log.info("  └{}┘", SLINE);
                log.info("");
            }
        } finally {
            lock.unlock();
        }
    }
}