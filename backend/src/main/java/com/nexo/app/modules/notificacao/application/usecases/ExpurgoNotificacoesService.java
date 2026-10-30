package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.infrastructure.persistence.NotificacaoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mantém a caixa de entrada em tamanho utilizável.
 *
 * <p>Uma notificação por destinatário significa que a tabela cresce com o produto
 * de usuários por eventos; sem expurgo ela vira o maior objeto do banco em poucos
 * meses. A rotina desativa (soft delete, conforme a política do projeto) o que o
 * usuário já resolveu e passou da retenção, além do que expirou por conta própria.
 * Notificação não lida nunca é expurgada por idade — some da caixa sem que ninguém
 * a tenha visto seria pior que a tabela grande.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpurgoNotificacoesService {

    private final NotificacaoRepository repository;

    @Value("${ces.notificacao.retencao-dias:90}")
    private int retencaoDias;

    @Scheduled(cron = "${ces.notificacao.expurgo-cron:0 30 3 * * *}")
    @Transactional
    public void expurgar() {
        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime corte = agora.minusDays(retencaoDias);

        int resolvidas = repository.desativarResolvidasAnterioresA(corte, agora);
        int expiradas = repository.desativarExpiradas(agora);

        if (resolvidas > 0 || expiradas > 0) {
            log.info("Expurgo de notificações: {} resolvidas há mais de {} dias e {} expiradas.",
                    resolvidas, retencaoDias, expiradas);
        }
    }
}
