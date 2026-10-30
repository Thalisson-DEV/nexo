package com.nexo.app.modules.sst.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AlertaDocumentoSstScheduler {

    private final DocumentoSstUseCases useCases;

    @Scheduled(cron = "${ces.sst.governanca.documentos.alertas-cron:0 10 4 * * *}")
    public void processar() {
        useCases.processarAlertas(LocalDate.now());
    }
}
