package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.mappers.NotificacaoMapper;
import com.nexo.app.modules.notificacao.domain.LogEmail;
import com.nexo.app.modules.notificacao.domain.StatusEnvioEmail;
import com.nexo.app.modules.notificacao.infrastructure.persistence.LogEmailRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.NotificacaoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retenta os e-mails que não saíram.
 *
 * <p>Existe porque a causa mais comum de falha é temporária: servidor SMTP fora do ar
 * por alguns minutos, limite de taxa do provedor, rede instável. Sem esta varredura,
 * uma indisponibilidade de dez minutos custaria em silêncio todos os avisos daquele
 * intervalo — e ninguém saberia, porque a notificação apareceu normalmente no
 * sininho.</p>
 *
 * <p>O intervalo entre tentativas cresce com o número delas: o registro só volta a ser
 * elegível depois de um tempo proporcional ao que já se tentou. Insistir de quinze em
 * quinze minutos contra uma credencial vencida não entrega nada e ainda arrisca a
 * reputação do domínio no provedor.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReenvioEmailsService {

    /** Minutos de espera após a n-ésima tentativa: 5, 20, 60. */
    private static final int[] ESPERA_POR_TENTATIVA = {5, 20, 60};

    /** Teto por rodada, para que uma fila represada não vire uma rajada de envios. */
    private static final int LOTE = 50;

    private final LogEmailRepository logRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoMapper notificacaoMapper;
    private final EnvioEmailNotificacao envio;

    /** Corte que separa um PENDENTE abandonado de um que está sendo enviado agora. */
    @Value("${ces.email.pendente-abandonado-minutos:10}")
    private int pendenteAbandonadoMinutos;

    @Scheduled(cron = "${ces.email.reenvio-cron:0 */10 * * * *}")
    public void reenviar() {
        OffsetDateTime agora = OffsetDateTime.now();
        List<LogEmail> candidatos = logRepository.buscarParaReenvio(
                StatusEnvioEmail.FALHOU, StatusEnvioEmail.PENDENTE,
                agora.minusMinutes(pendenteAbandonadoMinutos),
                LogEmail.MAXIMO_TENTATIVAS, PageRequest.of(0, LOTE));

        int reenviados = 0;
        for (LogEmail registro : candidatos) {
            if (!esperouOBastante(registro, agora)) {
                continue;
            }
            if (processar(registro)) {
                reenviados++;
            }
        }
        if (reenviados > 0) {
            log.info("Varredura de e-mails: {} de {} candidatos foram retentados.",
                    reenviados, candidatos.size());
        }
    }

    /**
     * Uma tentativa por registro, isolada das demais: um e-mail problemático não pode
     * interromper o lote — mesmo princípio que o {@code AplicadorRegra} aplica às
     * regras. Sem transação envolvente, pela razão explicada em
     * {@link EnvioEmailNotificacao}.
     */
    private boolean processar(LogEmail registro) {
        try {
            NotificacaoResponse notificacao = notificacaoRepository
                    .findById(registro.getIdNotificacao())
                    .map(notificacaoMapper::toResponse)
                    .orElse(null);

            if (notificacao == null) {
                // Notificação expurgada antes de o e-mail sair. Insistir não tem como
                // dar certo, e a linha fica marcada explicando por quê.
                registro.registrarFalha(registro.getProvedor(),
                        "A notificação de origem não existe mais; envio abandonado.");
                logRepository.save(registro);
                return false;
            }

            envio.retentar(registro, notificacao);
            return true;

        } catch (RuntimeException e) {
            log.error("Falha inesperada ao retentar o e-mail {}.", registro.getId(), e);
            return false;
        }
    }

    private static boolean esperouOBastante(LogEmail registro, OffsetDateTime agora) {
        int tentativas = registro.getTentativas();
        if (tentativas == 0) {
            return true;
        }
        int indice = Math.min(tentativas - 1, ESPERA_POR_TENTATIVA.length - 1);
        OffsetDateTime elegivelEm = registro.getDataUltimaAlteracao() == null
                ? registro.getDataCriacao()
                : registro.getDataUltimaAlteracao();
        return elegivelEm.plusMinutes(ESPERA_POR_TENTATIVA[indice]).isBefore(agora);
    }
}
