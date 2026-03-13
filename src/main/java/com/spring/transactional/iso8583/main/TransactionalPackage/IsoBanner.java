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
        out.println();
        out.println("  \u001B[90m╔══════════════════════════════════════════════════════════════════╗\u001B[0m");
        out.println("  \u001B[90m║\u001B[0m  \u001B[32m▸ Protocol   \u001B[0m  ISO 8583 — Financial Transaction Framework       \u001B[90m║\u001B[0m");
        out.println("  \u001B[90m║\u001B[0m  \u001B[32m▸ Version    \u001B[0m  " + padRight(getVersion(environment), 42) + "\u001B[90m║\u001B[0m");
        out.println("  \u001B[90m║\u001B[0m  \u001B[32m▸ Packager   \u001B[0m  ISO 8583-1987 (Generic87Packager)              \u001B[90m║\u001B[0m");
        out.println("  \u001B[90m║\u001B[0m  \u001B[32m▸ Servers    \u001B[0m  \u001B[32m● \u001B[0mPort 8081  \u001B[33m● \u001B[0mPort 8082                        \u001B[90m║\u001B[0m");
        out.println("  \u001B[90m║\u001B[0m  \u001B[32m▸ Transport  \u001B[0m  TCP — 4-byte length header framing             \u001B[90m║\u001B[0m");
        out.println("  \u001B[90m║\u001B[0m  \u001B[32m▸ Fields     \u001B[0m  Up to 128 fields / Primary + Secondary bitmap  \u001B[90m║\u001B[0m");
        out.println("  \u001B[90m╚══════════════════════════════════════════════════════════════════╝\u001B[0m");
        out.println();
        out.println("  \u001B[90m  No jPOS dependencies — Pure Spring Boot implementation\u001B[0m");
        out.println("  \u001B[90m  ─────────────────────────────────────────────────────\u001B[0m");
        out.println();
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