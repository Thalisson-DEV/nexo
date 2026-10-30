package com.nexo.app.modules.notificacao.application.usecases;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.domain.DestinatarioRegra;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduz os destinatários configurados numa regra na lista concreta de usuários.
 *
 * <p>O caso interessante é {@code PERMISSAO}: a regra guarda o nome da permissão
 * ({@code desconto:aprovar}), não uma lista de pessoas. Quem entra ou sai da
 * atribuição passa a receber ou deixa de receber automaticamente — a regra não
 * apodrece a cada mudança de time, que é o destino de toda notificação configurada
 * com lista fixa.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResolvedorDestinatariosRegra {

    private final UsuarioApi usuarioApi;
    private final AvaliadorPoliticaRoteamento avaliadorPolitica;

    /**
     * @param papeis papéis já resolvidos pelo evento; vazio em regras por limiar,
     *               que não têm registro de origem de onde extrair papel.
     */
    public Set<UUID> resolver(RegraNotificacao regra, Map<String, Object> contexto,
                              Map<String, Set<UUID>> papeis) {
        Set<UUID> destinatarios = new LinkedHashSet<>();
        for (DestinatarioRegra destinatario : regra.destinatariosAtivos()) {
            destinatarios.addAll(expandir(regra, destinatario, contexto, papeis));
        }
        return destinatarios;
    }

    private Set<UUID> expandir(RegraNotificacao regra, DestinatarioRegra destinatario,
                               Map<String, Object> contexto, Map<String, Set<UUID>> papeis) {
        return switch (destinatario.getTipo()) {
            case PERMISSAO -> new LinkedHashSet<>(
                    usuarioApi.buscarIdsAtivosComPermissao(destinatario.getValorAlvo()));
            case PERFIL -> new LinkedHashSet<>(
                    usuarioApi.buscarIdsAtivosPorPerfil(destinatario.getIdAlvo()));
            case USUARIO -> new LinkedHashSet<>(Set.of(destinatario.getIdAlvo()));
            case POLITICA -> avaliadorPolitica
                    .resolver(destinatario.getIdAlvo(), regra.getChaveGatilho(), contexto)
                    .map(Set::of)
                    .orElseGet(Set::of);
            case PAPEL_DINAMICO -> papelDoEvento(destinatario, papeis);
        };
    }

    private Set<UUID> papelDoEvento(DestinatarioRegra destinatario, Map<String, Set<UUID>> papeis) {
        Set<UUID> ocupantes = papeis == null ? null : papeis.get(destinatario.getValorAlvo());
        if (ocupantes == null || ocupantes.isEmpty()) {
            // Acontece quando o evento não preencheu o papel (ex.: registro sem
            // responsável). Não é erro de configuração: a regra simplesmente não
            // alcança ninguém desta vez.
            log.debug("O evento não trouxe ocupantes para o papel '{}'.", destinatario.getValorAlvo());
            return Set.of();
        }
        return new LinkedHashSet<>(ocupantes);
    }
}
