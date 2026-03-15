package com.spring.transactional.iso8583.main.Application.configuration.servers;


import com.spring.transactional.iso8583.main.Application.listeners.FiservListener;
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

public class IsoFiservServerConfig {

    @Value("${iso.servers.fiserv.port}")
    private int serverPort;

    @Value("${iso.servers.fiserv.framing}")
    private String serverFraming;

    @Value("${iso.servers.fiserv.thread-pool-size}")
    private int threadPoolSize;

    @Value("${iso.servers.fiserv.name}")
    private String name;

    @Bean
    public ISORequestListener isoFiservRequestListener(){
        return new FiservListener(serverPort);
    }

    /**
     * Servidor ISO en el puerto 8081.
     * Recibe solicitudes de autorización (0200) y responde con aprobación (0210).
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public ISOServer isoServer8081(@Qualifier("genericIsoPackager") ISOPackager packager,@Qualifier("isoFiservRequestListener") ISORequestListener isoRequestListener) {
        FramingStrategy strategy = FramingStrategy.valueOf(serverFraming.toUpperCase());
        return new ISOServer(serverPort, packager, isoRequestListener, strategy,threadPoolSize,name); //--> HEADER DEL MENSAJE CAMBIAR DEPENDIENDO DEL TIPO DE ISO
    }

}
