package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Comandos idempotentes de estágio físico para adições técnicas e apoio comercial. */
@Service
@RequiredArgsConstructor
public class SeparacaoSolicitacaoTecnicaUseCases {

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final ComandoIdempotenteSolicitacaoRepository comandoRepository;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final PublicadorEventoSolicitacaoTecnica publicadorEventos;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;

    @Transactional
    public StatusSeparacao iniciar(UUID idSolicitacao, Long versaoEsperada, UUID idUsuario, String chaveIdempotencia) {
        StatusSeparacao repetida = resultadoRepetido("SEPARACAO_INICIAR", idSolicitacao,
                versaoEsperada, idUsuario, chaveIdempotencia);
        if (repetida != null) {
            return repetida;
        }
        SolicitacaoMaterial solicitacao = carregar(idSolicitacao, versaoEsperada);
        StatusSeparacao statusAnterior = solicitacao.getStatusSeparacao();
        solicitacao.iniciarSeparacao(idUsuario);
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(solicitacao, TipoEventoSolicitacao.SEPARACAO_INICIADA,
                new PayloadEventoSolicitacao("INICIAR_SEPARACAO", java.util.List.of(
                        new AlteracaoEventoSolicitacao(
                                "statusSeparacao", statusAnterior, solicitacao.getStatusSeparacao())), Map.of()),
                new ContextoRegistroEventoSolicitacao(
                        idUsuario, "USUARIO " + idUsuario, OrigemComandoTecnico.UI, null));
        registrarResultado("SEPARACAO_INICIAR", idSolicitacao, versaoEsperada, idUsuario,
                chaveIdempotencia, solicitacao.getStatusSeparacao());
        return solicitacao.getStatusSeparacao();
    }

    @Transactional
    public StatusSeparacao concluir(UUID idSolicitacao, Long versaoEsperada, UUID idUsuario, String chaveIdempotencia) {
        StatusSeparacao repetida = resultadoRepetido("SEPARACAO_CONCLUIR", idSolicitacao,
                versaoEsperada, idUsuario, chaveIdempotencia);
        if (repetida != null) {
            return repetida;
        }
        SolicitacaoMaterial solicitacao = carregar(idSolicitacao, versaoEsperada);
        StatusSeparacao statusAnterior = solicitacao.getStatusSeparacao();
        solicitacao.concluirSeparacao(idUsuario);
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(solicitacao, TipoEventoSolicitacao.SEPARACAO_CONCLUIDA,
                new PayloadEventoSolicitacao("CONCLUIR_SEPARACAO", java.util.List.of(
                        new AlteracaoEventoSolicitacao(
                                "statusSeparacao", statusAnterior, solicitacao.getStatusSeparacao())), Map.of()),
                new ContextoRegistroEventoSolicitacao(
                        idUsuario, "USUARIO " + idUsuario, OrigemComandoTecnico.UI, null));
        if (solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            publicadorEventos.publicarFilho(
                    SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_SEPARADA, solicitacao);
        }
        registrarResultado("SEPARACAO_CONCLUIR", idSolicitacao, versaoEsperada, idUsuario,
                chaveIdempotencia, solicitacao.getStatusSeparacao());
        return solicitacao.getStatusSeparacao();
    }

    @Transactional
    public StatusSeparacao pular(UUID idSolicitacao, Long versaoEsperada, UUID idUsuario, String chaveIdempotencia) {
        StatusSeparacao repetida = resultadoRepetido("SEPARACAO_PULAR", idSolicitacao,
                versaoEsperada, idUsuario, chaveIdempotencia);
        if (repetida != null) {
            return repetida;
        }
        SolicitacaoMaterial solicitacao = carregar(idSolicitacao, versaoEsperada);
        StatusSeparacao statusAnterior = solicitacao.getStatusSeparacao();
        solicitacao.pularSeparacao(idUsuario);
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(solicitacao, TipoEventoSolicitacao.SEPARACAO_CONCLUIDA,
                new PayloadEventoSolicitacao("PULAR_SEPARACAO", java.util.List.of(
                        new AlteracaoEventoSolicitacao(
                                "statusSeparacao", statusAnterior, solicitacao.getStatusSeparacao())), Map.of()),
                new ContextoRegistroEventoSolicitacao(
                        idUsuario, "USUARIO " + idUsuario, OrigemComandoTecnico.UI, null));
        if (solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            publicadorEventos.publicarFilho(
                    SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_SEPARADA, solicitacao);
        }
        registrarResultado("SEPARACAO_PULAR", idSolicitacao, versaoEsperada, idUsuario,
                chaveIdempotencia, solicitacao.getStatusSeparacao());
        return solicitacao.getStatusSeparacao();
    }

    private StatusSeparacao resultadoRepetido(
            String escopo,
            UUID idSolicitacao,
            Long versaoEsperada,
            UUID idUsuario,
            String chave
    ) {
        validarChave(chave);
        String hash = hash(escopo, idSolicitacao, versaoEsperada);
        return comandoRepository.findByEscopoAndIdUsuarioAndChave(escopo, idUsuario, chave)
                .map(comando -> {
                    if (!comando.possuiMesmoPayload(hash)) {
                        observabilidade.registrarConflito(
                                ObservabilidadeSolicitacaoTecnica.Operacao.SEPARACAO,
                                ObservabilidadeSolicitacaoTecnica.TipoConflito.IDEMPOTENCIA);
                        throw new InvarianteVioladaException(
                                "A chave de idempotencia ja foi usada com outro payload.");
                    }
                    return StatusSeparacao.valueOf(comando.getResposta().get("statusSeparacao"));
                })
                .orElse(null);
    }

    private void registrarResultado(
            String escopo,
            UUID idSolicitacao,
            Long versaoEsperada,
            UUID idUsuario,
            String chave,
            StatusSeparacao status
    ) {
        comandoRepository.save(new ComandoIdempotenteSolicitacao(
                escopo, idUsuario, chave, hash(escopo, idSolicitacao, versaoEsperada),
                idSolicitacao, Map.of("statusSeparacao", status.name())));
    }

    private void validarChave(String chave) {
        if (chave == null || chave.isBlank() || chave.length() > 200) {
            throw new InvarianteVioladaException("A chave de idempotencia e obrigatoria e deve ter ate 200 caracteres.");
        }
    }

    private String hash(String escopo, UUID idSolicitacao, Long versaoEsperada) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((escopo + "|" + idSolicitacao + "|" + versaoEsperada)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder resultado = new StringBuilder(64);
            for (byte valor : bytes) {
                resultado.append(String.format("%02x", valor));
            }
            return resultado.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    private SolicitacaoMaterial carregar(UUID idSolicitacao, Long versaoEsperada) {
        SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitacao material nao encontrada"));
        if (!Objects.equals(solicitacao.getVersao(), versaoEsperada)) {
            observabilidade.registrarConflito(
                    ObservabilidadeSolicitacaoTecnica.Operacao.SEPARACAO,
                    ObservabilidadeSolicitacaoTecnica.TipoConflito.VERSAO);
            throw new ConflitoVersaoException(
                    ConflitoVersaoException.Recurso.SOLICITACAO,
                    versaoEsperada, solicitacao.getVersao());
        }
        return solicitacao;
    }
}
