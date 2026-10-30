package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.infrastructure.persistence.CodigoAutorizacaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CredencialConexaoIaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Cada conexao OAuth gera um token de acesso por hora e um de renovacao a cada troca. Uma vez por dia
 * apaga os codigos de autorizacao vencidos e as credenciais vencidas ou ja trocadas ha mais de uma semana
 * (so o hash fica no banco; nada disso volta a valer).
 */
@Component
public class LimpezaConexoesIa {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimpezaConexoesIa.class);
    static final Duration CARENCIA_CODIGO = Duration.ofDays(1);
    static final Duration CARENCIA_CREDENCIAL = Duration.ofDays(7);

    private final CodigoAutorizacaoIaRepository codigos;
    private final CredencialConexaoIaRepository credenciais;

    public LimpezaConexoesIa(CodigoAutorizacaoIaRepository codigos, CredencialConexaoIaRepository credenciais) {
        this.codigos = codigos;
        this.credenciais = credenciais;
    }

    @Scheduled(cron = "${ces.conexoes-ia.limpeza-cron:0 40 3 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void limpar() {
        OffsetDateTime agora = OffsetDateTime.now();
        int codigosApagados = codigos.apagarVencidosAntesDe(agora.minus(CARENCIA_CODIGO));
        int credenciaisApagadas = credenciais.apagarInuteisAntesDe(agora.minus(CARENCIA_CREDENCIAL));
        if (codigosApagados + credenciaisApagadas > 0) {
            LOGGER.info("Conexoes de IA: {} codigo(s) e {} credencial(is) antigas apagados", codigosApagados, credenciaisApagadas);
        }
    }
}
