package com.nexo.app.modules.notificacao.application.usecases;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.application.dto.PreferenciaNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.PreferenciasNotificacaoRequest;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.PreferenciaNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PreferenciaNotificacaoRepository;
import com.nexo.app.shared.infrastructure.config.CacheNames;

import lombok.RequiredArgsConstructor;

/**
 * Preferências de recebimento do usuário.
 *
 * <p>Fica em cache porque é consultada uma vez por destinatário a cada publicação —
 * é a leitura mais quente do módulo — e só muda por ação explícita do próprio
 * usuário, que já derruba a entrada.</p>
 */
@Service
@RequiredArgsConstructor
public class PreferenciaNotificacaoUseCases {

    private final PreferenciaNotificacaoRepository repository;

    /**
     * Todas as categorias, com o valor efetivo de cada uma. Categorias que o
     * usuário nunca tocou aparecem com o padrão (ligado na aplicação), de modo que
     * a tela de preferências se monta direto da resposta e o publicador não precisa
     * distinguir "sem linha" de "ligado".
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.NOTIFICACAO_PREFERENCIAS, key = "#idUsuario")
    public List<PreferenciaNotificacaoResponse> listar(UUID idUsuario) {
        Map<CategoriaNotificacao, PreferenciaNotificacao> salvas = indexarPorCategoria(idUsuario);

        List<PreferenciaNotificacaoResponse> resposta = new ArrayList<>(CategoriaNotificacao.values().length);
        for (CategoriaNotificacao categoria : CategoriaNotificacao.values()) {
            PreferenciaNotificacao preferencia = salvas.get(categoria);
            resposta.add(new PreferenciaNotificacaoResponse(
                    categoria,
                    categoria.getRotulo(),
                    preferencia == null || preferencia.isInApp(),
                    // Ausência de linha significa ligado, igual ao sininho. Este método
                    // devolve todas as categorias, então é aqui — e não no publicador —
                    // que o padrão do canal é decidido de fato.
                    categoria.permiteEmail() && (preferencia == null || preferencia.isEmail()),
                    categoria.permiteEmail()));
        }
        return resposta;
    }

    /**
     * Aplica um envio parcial: só as categorias presentes na requisição são
     * alteradas. Cria a linha na primeira vez que o usuário mexe na categoria.
     */
    @Transactional
    @CacheEvict(value = CacheNames.NOTIFICACAO_PREFERENCIAS, key = "#idUsuario")
    public List<PreferenciaNotificacaoResponse> salvar(UUID idUsuario, PreferenciasNotificacaoRequest request) {
        Map<CategoriaNotificacao, PreferenciaNotificacao> salvas = indexarPorCategoria(idUsuario);

        for (PreferenciasNotificacaoRequest.ItemPreferencia item : request.preferencias()) {
            PreferenciaNotificacao preferencia = salvas.get(item.categoria());
            if (preferencia == null) {
                repository.save(new PreferenciaNotificacao(
                        idUsuario, item.categoria(), item.inApp(), item.email()));
                continue;
            }
            preferencia.ajustar(item.inApp(), item.email());
            // Uma preferência desativada por expurgo de usuário volta a valer quando
            // ele mesmo a reconfigura.
            preferencia.ativar();
            repository.save(preferencia);
        }

        return listar(idUsuario);
    }

    private Map<CategoriaNotificacao, PreferenciaNotificacao> indexarPorCategoria(UUID idUsuario) {
        Map<CategoriaNotificacao, PreferenciaNotificacao> indice = new EnumMap<>(CategoriaNotificacao.class);
        repository.findByIdUsuario(idUsuario).forEach(p -> indice.put(p.getCategoria(), p));
        return indice;
    }
}
