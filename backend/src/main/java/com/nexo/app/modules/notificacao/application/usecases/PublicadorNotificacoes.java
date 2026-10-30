package com.nexo.app.modules.notificacao.application.usecases;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.application.dto.PreferenciaNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.events.DestinatarioNotificacao;
import com.nexo.app.modules.notificacao.application.events.EmailSolicitadoEvent;
import com.nexo.app.modules.notificacao.application.events.NotificacaoEntregueEvent;
import com.nexo.app.modules.notificacao.application.mappers.NotificacaoMapper;
import com.nexo.app.modules.notificacao.domain.Notificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.NotificacaoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Porta de entrada única para criar notificações.
 *
 * <p>Tudo o que precisa valer para toda notificação — respeitar o silenciamento do
 * usuário, não entregar duas vezes o mesmo fato, empurrar em tempo real depois do
 * commit — mora aqui. As fases seguintes (mural e motor de regras) montam um
 * {@link NovaNotificacao} e chamam este serviço; nenhuma delas instancia a entidade
 * por conta própria, justamente para não haver caminho que escape dessas regras.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicadorNotificacoes {

    private final NotificacaoRepository repository;
    private final PreferenciaNotificacaoUseCases preferencias;
    private final NotificacaoMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Entrega o comando a cada destinatário e devolve quantas notificações foram de
     * fato gravadas — o número pode ser menor que o de destinatários por
     * silenciamento ou por duplicata já existente, e ambos os casos são normais.
     */
    @Transactional
    public int publicar(NovaNotificacao comando) {
        int entregues = 0;
        List<DestinatarioNotificacao> destinatariosEmail = new ArrayList<>();
        NotificacaoResponse modeloResposta = null;

        for (UUID destinatario : comando.destinatarios()) {
            if (!aceitaReceber(destinatario, comando)) {
                continue;
            }
            if (repository.existsByIdUsuarioAndChaveIdempotencia(destinatario, comando.chaveIdempotencia())) {
                log.debug("Notificação '{}' já entregue ao usuário {}; ignorando reprocessamento.",
                        comando.chaveIdempotencia(), destinatario);
                continue;
            }

            Notificacao notificacao = repository.save(new Notificacao(
                    destinatario,
                    comando.origem(),
                    comando.idRegra(),
                    comando.idReferencia(),
                    comando.categoria(),
                    comando.prioridade(),
                    comando.titulo(),
                    comando.corpo(),
                    comando.deeplink(),
                    comando.contexto(),
                    comando.chaveIdempotencia(),
                    comando.expiraEm()));

            NotificacaoResponse resposta = mapper.toResponse(notificacao);
            modeloResposta = resposta;
            eventPublisher.publishEvent(new NotificacaoEntregueEvent(destinatario, resposta));

            if (aceitaEmail(destinatario, comando)) {
                destinatariosEmail.add(new DestinatarioNotificacao(destinatario, notificacao.getId()));
            }
            entregues++;
        }

        if (!destinatariosEmail.isEmpty()) {
            eventPublisher.publishEvent(
                    new EmailSolicitadoEvent(destinatariosEmail, modeloResposta, comando.chaveGatilho()));
        }

        return entregues;
    }

    /**
     * Silenciamento vale por categoria e por usuário, mas nunca sobre prioridade
     * crítica — a preferência existe para cortar ruído, não para desligar aviso
     * obrigatório.
     */
    private boolean aceitaReceber(UUID destinatario, NovaNotificacao comando) {
        if (comando.prioridade().ignoraPreferencia()) {
            return true;
        }
        return preferencias.listar(destinatario).stream()
                .filter(preferencia -> preferencia.categoria() == comando.categoria())
                .findFirst()
                .map(PreferenciaNotificacaoResponse::inApp)
                .orElse(true);
    }

    /**
     * Três crivos em ordem, e a ordem importa.
     *
     * <p>Primeiro o pedido de quem publicou: e-mail é exceção, não o padrão de toda
     * notificação. Depois o veto da categoria, que é do domínio e não se negocia —
     * marcação não vira e-mail nem que a regra peça. Só então a preferência do
     * usuário, com a mesma exceção que já vale no sininho: prioridade crítica fura o
     * silenciamento, porque aviso obrigatório não é ruído.</p>
     *
     * <p>Sem linha de preferência o e-mail sai. O contrário — exigir que cada pessoa
     * ligue o canal — faria a regra recém-configurada pelo administrador não alcançar
     * ninguém, e isso se pareceria com defeito, não com escolha.</p>
     */
    private boolean aceitaEmail(UUID destinatario, NovaNotificacao comando) {
        if (!comando.email() || !comando.categoria().permiteEmail()) {
            return false;
        }
        if (comando.prioridade().ignoraPreferencia()) {
            return true;
        }
        return preferencias.listar(destinatario).stream()
                .filter(preferencia -> preferencia.categoria() == comando.categoria())
                .findFirst()
                .map(PreferenciaNotificacaoResponse::email)
                .orElse(true);
    }
}
