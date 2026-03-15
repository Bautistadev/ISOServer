package com.spring.transactional.iso8583.main.TransactionalPackage.server;

import com.spring.transactional.iso8583.main.TransactionalPackage.channel.FramingStrategy;
import com.spring.transactional.iso8583.main.TransactionalPackage.channel.ISOChannel;
import com.spring.transactional.iso8583.main.TransactionalPackage.exception.ISOException;
import com.spring.transactional.iso8583.main.TransactionalPackage.logs.ISOLogger;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.ISOPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor TCP multi-hilo para mensajes ISO 8583.
 *
 * Arquitectura:
 *   - Un hilo dedicado (acceptLoop) escucha en el puerto y acepta conexiones entrantes.
 *   - Por cada conexión aceptada, delega el manejo a un hilo del ThreadPool.
 *   - Cada hilo del pool maneja una conexión completa (puede recibir múltiples mensajes
 *     en la misma sesión TCP, útil para conexiones persistentes con terminales/ATMs).
 *
 * Integración Spring:
 *   - InitializingBean: start() se llama automáticamente al crear el bean
 *   - DisposableBean: stop() se llama automáticamente al apagar el contexto Spring
 *
 * Equivalente a org.jpos.iso.ISOServer de jPOS.
 */


import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ISOServer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ISOServer.class);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SLINE = "─".repeat(64);

    private int port;
    private ISOPackager packager;
    private ISORequestListener listener;
    private String name;

    private ServerSocket serverSocket;
    private ExecutorService executor;

    private FramingStrategy framing;

    private final AtomicBoolean  running       = new AtomicBoolean(false);
    private final AtomicInteger  activeClients = new AtomicInteger(0);

    private int threadPoolSize;

    // ✅ Sin timeout de lectura — la conexión se mantiene viva indefinidamente
    //    Solo se cierra si el cliente cierra el socket (EOF) o hay un error real
    private static final int READ_TIMEOUT_MS = 0;

    // Intervalo para verificar que el socket sigue vivo (TCP keepalive a nivel app)
    private static final int KEEPALIVE_CHECK_MS = 5_000;

    //DEFAULTS
    public ISOServer(int port, ISOPackager packager, ISORequestListener listener) {
        this(port, packager, listener, FramingStrategy.HEADER_2,10);
    }

    public ISOServer(int port, ISOPackager packager,
                     ISORequestListener listener, FramingStrategy framing,int threadPoolSize) {
        this.port     = port;
        this.packager = packager;
        this.listener = listener;
        this.framing  = framing;// Agregar campo: private final FramingStrategy framing;
        this.threadPoolSize = threadPoolSize;
        this.name = "iso-server-"+port;
    }

    public ISOServer(int port, ISOPackager packager,
                     ISORequestListener listener, FramingStrategy framing,int threadPoolSize,String name) {
        this.port     = port;
        this.packager = packager;
        this.listener = listener;
        this.framing  = framing;
        this.threadPoolSize = threadPoolSize;
        this.name     = name; // default si no se setea
    }

    public String getName()        { return name; }
    public void   setName(String n){ this.name = n; }

    @Override
    public void afterPropertiesSet() throws Exception { start(); }

    public void start() throws IOException {
        if (running.get()) return;
        serverSocket = new ServerSocket(port, 50);
        executor     = Executors.newFixedThreadPool(threadPoolSize);
        running.set(true);

        Thread t = new Thread(this::acceptLoop, "iso-accept-" + port);
        t.setDaemon(true);
        t.start();

        ISOLogger.logServerStart(log, port, threadPoolSize,framing,name);
    }

    @Override
    public void destroy() { stop(); }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException e) {
            log.warn("  Error cerrando ServerSocket :{} → {}", port, e.getMessage());
        }
        if (executor != null) executor.shutdownNow();

        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ■  SERVIDOR DETENIDO  │  Puerto: :{}", port);
        log.info("  └{}┘", SLINE);
        log.info("");
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                configureSocket(clientSocket);
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get())
                    log.error("  Error aceptando conexión en :{} → {}", port, e.getMessage());
            }
        }
    }

    /**
     * Configura el socket para mantener la conexión viva indefinidamente.
     *
     * SO_KEEPALIVE  → TCP enviará paquetes keepalive a nivel OS para detectar
     *                 desconexiones silenciosas (ej: cable desconectado, crash)
     * SO_TIMEOUT=0  → sin timeout de lectura — el servidor espera para siempre
     *                 hasta que llegue un mensaje o el cliente cierre el socket
     * TCP_NODELAY   → deshabilita el algoritmo de Nagle para menor latencia
     */
    private void configureSocket(Socket socket) throws SocketException {
        socket.setKeepAlive(true);       // TCP keepalive a nivel OS
        socket.setSoTimeout(READ_TIMEOUT_MS); // 0 = sin timeout (bloqueante)
        socket.setTcpNoDelay(true);      // sin buffering de paquetes pequeños
    }

    private void handleClient(Socket clientSocket) {
        String remote     = clientSocket.getRemoteSocketAddress().toString();
        long  sessionStart = System.currentTimeMillis();
        int   clientNum    = activeClients.incrementAndGet();

        // ✅ Log de nueva conexión — se dispara al conectar, no al recibir el primer mensaje
        log.info("");
        log.info("  ┌{}┐", SLINE);
        log.info("  │  ⟶  NUEVA CONEXIÓN ACEPTADA");
        log.info("  │  {}", String.format("%-24s  :%s",    "Canal:", name));
        log.info("  │  {}", String.format("%-24s  :%d",    "Puerto servidor:", port));
        log.info("  │  {}", String.format("%-24s  %s",     "Cliente origen:",  remote));
        log.info("  │  {}", String.format("%-24s  %d",     "Clientes activos:", clientNum));
        log.info("  │  {}", String.format("%-24s  %s",     "Timestamp:",
                LocalDateTime.now().format(DT_FMT)));
        log.info("  │  {}", String.format("%-24s  %s",     "Keep-alive:",      "Activado (sin timeout)"));
        log.info("  └{}┘", SLINE);
        log.info("");

        ISOChannel channel = new ISOChannel(packager,framing);
        String closeReason = "Desconocido";

        try {
            channel.attach(clientSocket);

            // ✅ Bucle persistente — se mantiene vivo hasta que el cliente
            //    cierre la conexión (EOF) o haya un error real de red
            while (running.get() && channel.isConnected()) {
                try {
                    // receive() es bloqueante sin timeout — espera indefinidamente
                    ISOMsg request = channel.receive();

                    if (request == null) {
                        // EOF — el cliente cerró la conexión limpiamente
                        closeReason = "Cliente cerró la conexión (EOF)";
                        break;
                    }

                    ISOMsg response = listener.onRequest(request);
                    if (response != null) channel.send(response);

                } catch (SocketTimeoutException e) {
                    // No debería ocurrir con timeout=0, pero por si acaso
                    log.warn("  [{}] Timeout inesperado — continuando...", remote);

                } catch (SocketException e) {
                    // Connection reset, broken pipe, etc. — el cliente desapareció
                    closeReason = "Error de red: " + e.getMessage();
                    break;

                } catch (IOException e) {
                    if (!channel.isConnected()) {
                        closeReason = "Conexión perdida: " + e.getMessage();
                    } else {
                        closeReason = "Error I/O: " + e.getMessage();
                        log.error("  [{}] Error procesando mensaje: {}", remote, e.getMessage());
                    }
                    break;

                } catch (ISOException e) {
                    // Error de parseo ISO — loguear y seguir con el siguiente mensaje
                    log.error("  [{}] Error ISO (se continúa): {}", remote, e.getMessage());
                }
            }

            if (!running.get()) closeReason = "Servidor detenido";

        } catch (Exception e) {
            closeReason = "Error inesperado: " + e.getMessage();
            log.error("  [{}] Error en sesión: {}", remote, e.getMessage());
        } finally {
            channel.close();
            int remaining = activeClients.decrementAndGet();
            long duration = System.currentTimeMillis() - sessionStart;

            // ✅ Log de cierre con motivo real
            log.info("");
            log.info("  ┌{}┐", SLINE);
            log.info("  │  ✕  CONEXIÓN CERRADA");
            log.info("  │  {}", String.format("%-24s  :%s",   "Canal:",   name));
            log.info("  │  {}", String.format("%-24s  :%d",   "Puerto servidor:",   port));
            log.info("  │  {}", String.format("%-24s  %s",    "Cliente origen:",    remote));
            log.info("  │  {}", String.format("%-24s  %s",    "Motivo cierre:",     closeReason));
            log.info("  │  {}", String.format("%-24s  %d ms", "Duración sesión:",   duration));
            log.info("  │  {}", String.format("%-24s  %d",    "Clientes restantes:", remaining));
            log.info("  └{}┘", SLINE);
            log.info("");
        }
    }

    public boolean isRunning()           { return running.get(); }
    public int getPort()                 { return port; }
    public int getActiveClients()        { return activeClients.get(); }
    public void setThreadPoolSize(int n) { this.threadPoolSize = n; }
}