package com.spring.transactional.iso8583.main.Application.configuration.servers;


import com.spring.transactional.iso8583.main.Application.listeners.FiservListener;
import com.spring.transactional.iso8583.main.Application.participants.echoParticipant;
import com.spring.transactional.iso8583.main.TransactionalPackage.channel.FramingStrategy;
import com.spring.transactional.iso8583.main.TransactionalPackage.channel.Space;
import com.spring.transactional.iso8583.main.TransactionalPackage.logs.ISOLogger;
import com.spring.transactional.iso8583.main.TransactionalPackage.message.ISOMsg;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.ISOPackager;
import com.spring.transactional.iso8583.main.TransactionalPackage.server.ISORequestListener;
import com.spring.transactional.iso8583.main.TransactionalPackage.server.ISOServer;
import com.spring.transactional.iso8583.main.TransactionalPackage.transaction.SendResponse;
import com.spring.transactional.iso8583.main.TransactionalPackage.transaction.TransactionManager;
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


    /*
    * Creamos espacio
    * */
    @Bean
    public Space txnSpace() {
        return new Space("fiserv");
    }

    /**
     * Creamos el listener del ISOServer
     * y lo publicamos en el Space bajo clave TXN
     * Luego espera la respuesta SOURCE (bloqueante con timeout de 30s)
     **/
    @Bean
    public ISORequestListener fiservListener(){
        FiservListener listenerFiserv = new FiservListener(txnSpace(),name+serverPort, 30_000);
        listenerFiserv.setInKey("TXN-"+name);
        listenerFiserv.setRespKey("RESP-");
        return listenerFiserv;
    }

    /**
     * Servidor ISO en el puerto 8081.
     * Recibe solicitudes de autorización (0200) y responde con aprobación (0210).
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public ISOServer isoServer8081(@Qualifier("genericIsoPackager") ISOPackager packager,@Qualifier("fiservListener") ISORequestListener isoRequestListener) {
        FramingStrategy strategy = FramingStrategy.valueOf(serverFraming.toUpperCase());
        return new ISOServer(serverPort, packager, isoRequestListener, strategy,threadPoolSize,name); //--> HEADER DEL MENSAJE CAMBIAR DEPENDIENDO DEL TIPO DE ISO
    }

    /**
     * INTANCIAMOS TRANSACCTION MANAGER
     * */
    @Bean
    public TransactionManager fiservTransactionManager(){
        TransactionManager txn = new TransactionManager(txnSpace(),"TXN-"+name);
        txn.setSessions(10); //10 transacciones en paralelo
        txn.addParticipant(new echoParticipant());
        txn.addParticipant(new SendResponse(txnSpace()));

        return txn;
    }

}
