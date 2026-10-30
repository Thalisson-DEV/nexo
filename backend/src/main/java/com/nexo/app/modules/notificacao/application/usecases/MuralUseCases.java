package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.iam.ContextoAudienciaUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoMuralResponse;
import com.nexo.app.modules.notificacao.application.mappers.ComunicadoMapper;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.domain.InteracaoComunicado;
import com.nexo.app.modules.notificacao.domain.TipoAlvoSegmento;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ComunicadoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.InteracaoComunicadoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * O mural do ponto de vista de quem lê.
 *
 * <p>Aqui mora o comportamento que o usuário descreveu: o comunicado aparece, ele
 * fecha e some — ou escolhe rever depois. Nada disso escreve nada até que ele aja:
 * a {@link InteracaoComunicado} é criada no primeiro clique, não na publicação.</p>
 */
@Service
@RequiredArgsConstructor
public class MuralUseCases {

    /** Adiamento padrão quando o cliente não informa até quando adiar. */
    private static final int HORAS_ADIAMENTO_PADRAO = 24;

    private final ComunicadoRepository comunicadoRepository;
    private final InteracaoComunicadoRepository interacaoRepository;
    private final ComunicadoUseCases comunicadoUseCases;
    private final ComunicadoMapper mapper;
    private final UsuarioApi usuarioApi;

    /**
     * O que este usuário deve ver agora: publicados, dentro da janela, que o
     * alcançam e que ele ainda não tirou da frente.
     */
    @Transactional(readOnly = true)
    public List<ComunicadoMuralResponse> mural() {
        UUID idUsuario = usuarioAtual();
        ContextoAudienciaUsuario contexto = usuarioApi.buscarContextoAudiencia(idUsuario)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "Não foi possível identificar o usuário autenticado."));

        OffsetDateTime agora = OffsetDateTime.now();
        List<Comunicado> comunicados = comunicadoRepository.buscarMuralDoUsuario(
                idUsuario, contexto.idPerfil(), contexto.tipoUsuario(), agora,
                TipoAlvoSegmento.TODOS, TipoAlvoSegmento.PERFIL,
                TipoAlvoSegmento.USUARIO, TipoAlvoSegmento.TIPO_USUARIO);

        if (comunicados.isEmpty()) {
            return List.of();
        }

        Map<UUID, InteracaoComunicado> interacoes = interacoesDoUsuario(idUsuario, comunicados);
        return comunicados.stream()
                .map(comunicado -> mapper.toMural(comunicado, interacoes.get(comunicado.getId())))
                .toList();
    }

    /** Registra que o usuário abriu o comunicado, sem tirá-lo do mural. */
    @Transactional
    public void registrarVisualizacao(UUID idComunicado) {
        InteracaoComunicado interacao = interacaoDoUsuario(idComunicado);
        interacao.registrarVisualizacao();
        interacaoRepository.save(interacao);
    }

    /** "Não exibir novamente." */
    @Transactional
    public void dispensar(UUID idComunicado) {
        InteracaoComunicado interacao = interacaoDoUsuario(idComunicado);
        interacao.dispensar();
        interacaoRepository.save(interacao);
    }

    /** "Ver depois." Sem data informada, adia por 24 horas. */
    @Transactional
    public void adiar(UUID idComunicado, OffsetDateTime adiarAte) {
        InteracaoComunicado interacao = interacaoDoUsuario(idComunicado);
        OffsetDateTime destino = adiarAte != null
                ? adiarAte
                : OffsetDateTime.now().plusHours(HORAS_ADIAMENTO_PADRAO);
        interacao.adiarAte(destino);
        interacaoRepository.save(interacao);
    }

    /** "Li e entendi." Registra a confirmação e tira do mural. */
    @Transactional
    public void confirmar(UUID idComunicado) {
        InteracaoComunicado interacao = interacaoDoUsuario(idComunicado);
        interacao.confirmar();
        interacaoRepository.save(interacao);
    }

    /**
     * A interação existente, ou uma nova ainda não persistida. Exige o comunicado
     * vigente: agir sobre um que saiu do ar significa tela desatualizada, e criar a
     * interação nesse caso sujaria o relatório de confirmação.
     */
    private InteracaoComunicado interacaoDoUsuario(UUID idComunicado) {
        UUID idUsuario = usuarioAtual();
        Comunicado comunicado = comunicadoUseCases.exigirVigente(idComunicado, OffsetDateTime.now());
        return interacaoRepository.findByComunicadoIdAndIdUsuario(idComunicado, idUsuario)
                .orElseGet(() -> new InteracaoComunicado(comunicado, idUsuario));
    }

    private Map<UUID, InteracaoComunicado> interacoesDoUsuario(UUID idUsuario, List<Comunicado> comunicados) {
        List<UUID> ids = comunicados.stream().map(Comunicado::getId).toList();
        Map<UUID, InteracaoComunicado> indice = new HashMap<>();
        interacaoRepository.findByIdUsuarioAndComunicadoIdIn(idUsuario, ids)
                .forEach(interacao -> indice.put(interacao.getComunicado().getId(), interacao));
        return indice;
    }

    private UUID usuarioAtual() {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        }
        return idUsuario;
    }
}
