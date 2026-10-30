package com.nexo.app.modules.notificacao.application.usecases;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.RotaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PoliticaRoteamentoNotificacaoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AvaliadorPoliticaRoteamento {

    private final PoliticaRoteamentoNotificacaoRepository repository;
    private final UsuarioApi usuarioApi;

    @Transactional(readOnly = true)
    public Optional<UUID> resolver(UUID politicaId, String chaveGatilho,
                                  Map<String, Object> contexto) {
        PoliticaRoteamentoNotificacao politica = repository.findById(politicaId).orElse(null);
        if (politica == null || !politica.isAtivo()
                || !politica.getChaveGatilho().equals(chaveGatilho)) {
            log.warn("Política de roteamento {} ausente, inativa ou incompatível com o gatilho {}.",
                    politicaId, chaveGatilho);
            return Optional.empty();
        }

        RotaRoteamentoNotificacao padrao = null;
        RotaRoteamentoNotificacao escolhida = null;
        for (RotaRoteamentoNotificacao rota : politica.rotasAtivasOrdenadas()) {
            if (rota.isPadrao()) {
                padrao = rota;
            } else if (rota.satisfeitaPor(contexto)) {
                escolhida = rota;
                break;
            }
        }
        if (escolhida == null) {
            escolhida = padrao;
        }
        if (escolhida == null) {
            log.warn("Política de roteamento {} não possui rota padrão ativa.", politicaId);
            return Optional.empty();
        }

        UUID usuarioId = escolhida.getUsuarioId();
        if (usuarioApi.buscarContextoAudiencia(usuarioId).isEmpty()) {
            log.warn("A política {} selecionou o usuário {} inexistente ou inativo.",
                    politicaId, usuarioId);
            return Optional.empty();
        }
        return Optional.of(usuarioId);
    }
}
