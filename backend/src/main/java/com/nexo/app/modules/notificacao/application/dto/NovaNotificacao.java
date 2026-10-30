package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import lombok.Builder;

/**
 * Pedido de entrega dirigido ao {@code PublicadorNotificacoes}.
 *
 * <p>É a porta de entrada única do módulo: tanto o mural (fase 2) quanto o motor de
 * regras (fase 3) montam este comando em vez de instanciar {@code Notificacao}
 * diretamente. Concentrar a criação aqui é o que garante que preferência do
 * usuário, idempotência e push em tempo real sejam aplicados sempre, sem depender
 * de cada chamador lembrar.</p>
 *
 * <p>A {@code chaveIdempotencia} identifica o <em>assunto</em>, não a entrega — o
 * publicador a combina com cada destinatário. Ela deve ser estável para o mesmo
 * fato (ex.: {@code "desconto:1042:pendente"}), de modo que um reprocessamento do
 * outbox não gere notificação duplicada.</p>
 */
@Builder
public record NovaNotificacao(
        Set<UUID> destinatarios,
        OrigemNotificacao origem,
        UUID idRegra,
        UUID idReferencia,
        CategoriaNotificacao categoria,
        PrioridadeNotificacao prioridade,
        String titulo,
        String corpo,
        String deeplink,
        Map<String, Object> contexto,
        String chaveIdempotencia,
        OffsetDateTime expiraEm,

        /**
         * Pedido de que este aviso saia também por e-mail, além do sininho.
         *
         * <p>É pedido, não garantia: o publicador ainda checa o veto da categoria e a
         * preferência de cada destinatário. Quem marca isto é quem publica — a regra
         * configurada pelo administrador ou o comunicado do mural —, porque só ali se
         * sabe se o assunto merece a caixa de e-mail de alguém.</p>
         */
        boolean email,

        /**
         * Gatilho de origem, usado apenas para encontrar os anexos do registro na hora
         * de montar o e-mail. Nulo em notificações que não vieram de gatilho.
         */
        String chaveGatilho
) {

    public NovaNotificacao {
        if (destinatarios == null || destinatarios.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um destinatário para a notificação.");
        }
        if (destinatarios.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvarianteVioladaException("A lista de destinatários contém um identificador nulo.");
        }
        if (!StringUtils.hasText(chaveIdempotencia)) {
            throw new InvarianteVioladaException("A chave de idempotência da notificação é obrigatória.");
        }
        destinatarios = Set.copyOf(new LinkedHashSet<>(destinatarios));
        // Cópia defensiva que tolera valores nulos — Map.copyOf os rejeita, e um campo
        // vazio no contexto é legítimo (nem todo desconto tem data de ocorrência).
        contexto = contexto == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(contexto));
        prioridade = prioridade == null ? PrioridadeNotificacao.NORMAL : prioridade;
        origem = origem == null ? OrigemNotificacao.SISTEMA : origem;
    }
}
