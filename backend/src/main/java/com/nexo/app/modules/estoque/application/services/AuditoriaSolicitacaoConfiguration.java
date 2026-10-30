package com.nexo.app.modules.estoque.application.services;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuditoriaSolicitacaoConfiguration {

    @Bean
    Clock auditoriaSolicitacaoClock() {
        return Clock.systemDefaultZone();
    }
}
