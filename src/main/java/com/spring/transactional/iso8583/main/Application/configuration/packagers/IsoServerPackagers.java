package com.spring.transactional.iso8583.main.Application.configuration.packagers;

import com.spring.transactional.iso8583.main.TransactionalPackage.packager.Generic87Packager;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.GenericPackager;
import com.spring.transactional.iso8583.main.TransactionalPackage.packager.ISOPackager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IsoServerPackagers {
    /**
     * Packager compartido por ambos servidores.
     * Un solo bean alcanza porque ISOPackager no tiene estado mutable.
     */
    @Bean
    public ISOPackager isoPackager() {
        return new Generic87Packager();
    }
    @Bean
    public ISOPackager genericIsoPackager() {
        return new GenericPackager(); // en lugar de Generic87Packager
    }

}
