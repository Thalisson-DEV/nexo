package com.nexo.app.modules.execucao.application.usecases;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Procura turnos esquecidos a cada poucos minutos. Depois do corte, o primeiro disparo
 * encerra os do dia; os demais não encontram nada e não fazem nada.
 */
@Slf4j
@Component
public class EncerramentoAutomaticoTurnosScheduler {

    private final EncerrarTurnosEsquecidosUseCase useCase;
    private final boolean habilitado;

    public EncerramentoAutomaticoTurnosScheduler(
            EncerrarTurnosEsquecidosUseCase useCase,
            @Value("${ces.turno.encerramento-automatico.habilitado:true}") boolean habilitado) {
        this.useCase = useCase;
        this.habilitado = habilitado;
    }

    @Scheduled(cron = "${ces.turno.encerramento-automatico.cron:0 */10 * * * *}", zone = "America/Sao_Paulo")
    public void processar() {
        if (!habilitado) {
            return;
        }
        int encerrados = useCase.executar(OffsetDateTime.now());
        if (encerrados > 0) {
            log.info("Encerramento automático de turnos: {} turno(s) esquecido(s) encerrado(s).", encerrados);
        }
    }
}
