package com.spring.transactional.iso8583.main.TransactionalPackage;

import org.springframework.boot.Banner;
import org.springframework.core.env.Environment;

import java.io.PrintStream;

public class IsoBanner implements Banner {

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        out.println();
        out.println("  \u001B[36m██╗███████╗ ██████╗      █████╗ ███████╗███████╗ █████╗ \u001B[0m");
        out.println("  \u001B[36m██║██╔════╝██╔═══██╗    ██╔══██╗██╔════╝██╔════╝██╔══██╗\u001B[0m");
        out.println("  \u001B[36m██║███████╗██║   ██║    ╚█████╔╝███████╗███████╗╚█████╔╝\u001B[0m");
        out.println("  \u001B[36m██║╚════██║██║   ██║    ██╔══██╗╚════██║╚════██║██╔══██╗\u001B[0m");
        out.println("  \u001B[36m██║███████║╚██████╔╝    ╚█████╔╝███████║███████║╚█████╔╝\u001B[0m");
        out.println("  \u001B[36m╚═╝╚══════╝ ╚═════╝      ╚════╝ ╚══════╝╚══════╝ ╚════╝\u001B[0m");
        out.println();
        out.println("  \u001B[33m ██████╗ ███████╗ █████╗ ██████╗     ███████╗██████╗  █████╗ ███╗   ███╗███████╗\u001B[0m");
        out.println("  \u001B[33m██╔════╝ ██╔════╝██╔══██╗██╔══██╗    ██╔════╝██╔══██╗██╔══██╗████╗ ████║██╔════╝\u001B[0m");
        out.println("  \u001B[33m██║  ███╗█████╗  ███████║██████╔╝    █████╗  ██████╔╝███████║██╔████╔██║█████╗  \u001B[0m");
        out.println("  \u001B[33m██║   ██║██╔══╝  ██╔══██║██╔══██╗    ██╔══╝  ██╔══██╗██╔══██║██║╚██╔╝██║██╔══╝ \u001B[0m");
        out.println("  \u001B[33m╚██████╔╝███████╗██║  ██║██║  ██║    ██║     ██║  ██║██║  ██║██║ ╚═╝ ██║███████╗\u001B[0m");
        out.println("  \u001B[33m ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝    ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝     ╚═╝╚══════╝\u001B[0m");

    }

    private String getVersion(Environment env) {
        String version = env.getProperty("spring.application.version", "1.0.0");
        return "v" + version + " — Spring Boot " + getSpringBootVersion();
    }

    private String getSpringBootVersion() {
        try {
            return org.springframework.boot.SpringBootVersion.getVersion();
        } catch (Exception e) {
            return "3.x";
        }
    }

    private String padRight(String s, int length) {
        if (s == null) s = "";
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}