package com.nexo.app.modules.frota.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Dispara diariamente a avaliação de vencimentos de documentos e CNHs da frota. */
@Component
@RequiredArgsConstructor
public class AlertaVencimentoFrotaScheduler {

    private final AlertaVencimentoFrotaUseCases useCases;

    @Scheduled(cron = "${ces.frota.vencimentos-cron:0 20 4 * * *}", zone = "America/Sao_Paulo")
    public void processar() {
        useCases.processar(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }
}
