package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.events.DestinatarioNotificacao;
import com.nexo.app.modules.notificacao.domain.LogEmail;
import com.nexo.app.modules.notificacao.infrastructure.persistence.LogEmailRepository;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.ResultadoEnvioEmail;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * O envio de fato: resolve os endereços, abre os registros, manda e conclui.
 *
 * <p>Roda deliberadamente <b>sem transação envolvente</b>. Cada gravação do
 * {@code LogEmail} confirma sozinha, o que é o que dá sentido ao estado
 * {@code PENDENTE}: a linha existe no banco antes de o provedor ser chamado, então
 * uma queda no meio do envio deixa rastro em vez de silêncio. Uma transação em volta
 * faria o oposto — só confirmaria depois do envio, e a única situação em que o
 * registro importa de verdade é justamente aquela em que ele não chegaria a
 * existir.</p>
 *
 * <p>Para notificações distribuídas a múltiplos usuários, os destinatários são
 * consolidados em um único envio de e-mail com múltiplos endereços no cabeçalho 'To'.
 * Isso economiza requisições a provedores externos (ex.: Resend), viabiliza respostas
 * conjuntas entre colegas e respeita o limite estrito de 50 destinatários por requisição
 * através de particionamento automático em sub-lotes.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvioEmailNotificacao {

    /** Teto aceito pelo provedor Resend no campo 'to' em uma única chamada. */
    private static final int LIMITE_DESTINATARIOS_POR_LOTE = 50;

    private final UsuarioApi usuarioApi;
    private final LogEmailRepository logRepository;
    private final MontadorEmailNotificacao montador;
    private final ServicoEmail servicoEmail;

    /**
     * Primeira tentativa para destinatário único.
     */
    public void enviar(UUID idUsuario, NotificacaoResponse notificacao, String chaveGatilho) {
        if (idUsuario == null || notificacao == null) {
            return;
        }
        enviar(List.of(new DestinatarioNotificacao(idUsuario, notificacao.id())), notificacao, chaveGatilho);
    }

    /**
     * Primeira tentativa consolidada para um ou mais destinatários.
     */
    public void enviar(List<DestinatarioNotificacao> destinatarios, NotificacaoResponse notificacao,
                       String chaveGatilho) {
        if (destinatarios == null || destinatarios.isEmpty() || notificacao == null) {
            return;
        }

        Set<UUID> idsUsuarios = destinatarios.stream()
                .map(DestinatarioNotificacao::idUsuario)
                .collect(Collectors.toSet());

        Map<UUID, String> emailsPorUsuario = usuarioApi.buscarEmailsPorIds(idsUsuarios);

        List<LogEmail> registrosParaEnviar = new ArrayList<>();

        for (DestinatarioNotificacao dest : destinatarios) {
            String endereco = emailsPorUsuario.get(dest.idUsuario());
            if (!StringUtils.hasText(endereco)) {
                // Comum entre usuários de campo, e não é falha: a pessoa é alcançável pelo
                // sininho. Registrar isso como erro encheria a trilha de ruído previsível.
                log.debug("Usuário {} não tem e-mail cadastrado; notificação {} fica só no sininho.",
                        dest.idUsuario(), dest.idNotificacao());
                continue;
            }
            if (dest.idNotificacao() != null
                    && logRepository.existsByIdNotificacaoAndIdUsuario(dest.idNotificacao(), dest.idUsuario())) {
                log.debug("E-mail da notificação {} para o usuário {} já foi registrado; ignorando.",
                        dest.idNotificacao(), dest.idUsuario());
                continue;
            }

            LogEmail registro = new LogEmail(
                    dest.idNotificacao() != null ? dest.idNotificacao() : notificacao.id(),
                    dest.idUsuario(),
                    endereco,
                    notificacao.titulo(),
                    chaveGatilho);
            registrosParaEnviar.add(registro);
        }

        if (registrosParaEnviar.isEmpty()) {
            return;
        }

        logRepository.saveAll(registrosParaEnviar);

        if (montador.exigeEnvioIndividual(chaveGatilho)) {
            for (LogEmail registro : registrosParaEnviar) {
                despacharIndividual(registro, notificacao, registro.getDestinatario(), chaveGatilho);
            }
            return;
        }
        for (int i = 0; i < registrosParaEnviar.size(); i += LIMITE_DESTINATARIOS_POR_LOTE) {
            List<LogEmail> lote = registrosParaEnviar.subList(
                    i, Math.min(i + LIMITE_DESTINATARIOS_POR_LOTE, registrosParaEnviar.size()));
            despacharLote(lote, notificacao, chaveGatilho);
        }
    }

    /**
     * Nova tentativa sobre um registro que já existe, vinda da varredura. Reconstrói a
     * mensagem a partir da notificação — de modo que a segunda tentativa leve os
     * anexos que existem agora, e não os que existiam quando a primeira falhou.
     */
    public void retentar(LogEmail registro, NotificacaoResponse notificacao) {
        despacharIndividual(registro, notificacao, registro.getDestinatario(), registro.getChaveGatilho());
    }

    private void despacharLote(List<LogEmail> lote, NotificacaoResponse notificacao, String chaveGatilho) {
        Set<String> enderecos = lote.stream()
                .map(LogEmail::getDestinatario)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        MensagemEmail mensagem = montador.montar(enderecos, notificacao, chaveGatilho);
        ResultadoEnvioEmail resultado = servicoEmail.enviar(mensagem);

        OffsetDateTime agora = OffsetDateTime.now();
        if (resultado.sucesso()) {
            for (LogEmail registro : lote) {
                registro.registrarEnvio(resultado.provedor().name(), resultado.idExterno(), agora);
            }
        } else {
            for (LogEmail registro : lote) {
                registro.registrarFalha(resultado.provedor().name(), resultado.erro());
            }
            log.warn("E-mail da notificação {} para {} destinatários ({}) falhou: {}",
                    notificacao.id(), lote.size(), enderecos, resultado.erro());
        }
        logRepository.saveAll(lote);
    }

    private void despacharIndividual(LogEmail registro, NotificacaoResponse notificacao,
                                     String endereco, String chaveGatilho) {
        // Sempre com o destinatário: se o gatilho tiver ações pessoais, o link é dele.
        MensagemEmail mensagem = montador.montarPara(endereco, registro.getIdUsuario(), notificacao, chaveGatilho);
        ResultadoEnvioEmail resultado = servicoEmail.enviar(mensagem);

        if (resultado.sucesso()) {
            registro.registrarEnvio(resultado.provedor().name(), resultado.idExterno(),
                    OffsetDateTime.now());
        } else {
            registro.registrarFalha(resultado.provedor().name(), resultado.erro());
            log.warn("E-mail da notificação {} para {} falhou na tentativa {}: {}",
                    notificacao.id(), endereco, registro.getTentativas(), resultado.erro());
        }
        logRepository.save(registro);
    }
}
