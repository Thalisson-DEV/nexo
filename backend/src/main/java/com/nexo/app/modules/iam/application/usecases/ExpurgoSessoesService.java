package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.infrastructure.persistence.SessaoAutenticacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Remove sessões encerradas há mais de 30 dias. A janela mantém o histórico recente para
 * investigar um aparelho revogado por reuso de token; depois disso a linha só ocupa espaço.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpurgoSessoesService {

    private static final int RETENCAO_DIAS = 30;

    private final SessaoAutenticacaoRepository repository;

    @Scheduled(cron = "${ces.security.sessao.expurgo-cron:0 15 4 * * *}")
    @Transactional
    public void expurgar() {
        int removidas = repository.expurgarEncerradasAntesDe(OffsetDateTime.now().minusDays(RETENCAO_DIAS));
        if (removidas > 0) {
            log.info("Expurgo de sessões: {} sessões encerradas há mais de {} dias.", removidas, RETENCAO_DIAS);
        }
    }
}
