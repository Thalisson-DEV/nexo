package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.EncerrarSaldoTecnicoRequest;
import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.IdempotenciaComandoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncerramentoSaldoTecnicoUseCases {

    private static final String PREFIXO_ESCOPO = "ENCERRAMENTO_TECNICO:";

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final OperacaoSolicitacaoMaterialRepository operacaoRepository;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;
    private final IdempotenciaComandoSolicitacaoService idempotencia;

    @Transactional
    public void encerrar(UUID idSolicitacao, EncerrarSaldoTecnicoRequest request, UUID idUsuario) {
        encerrarInterno(idSolicitacao, request, idUsuario, null);
    }

    @Transactional
    public void encerrar(
            UUID idSolicitacao,
            EncerrarSaldoTecnicoRequest request,
            UUID idUsuario,
            String chaveIdempotencia
    ) {
        Objects.requireNonNull(request, "Os dados do encerramento sao obrigatorios");
        Objects.requireNonNull(idUsuario, "O usuario do encerramento e obrigatorio");
        IdempotenciaComandoSolicitacaoService.Consulta<Void> consulta = idempotencia.consultar(
                PREFIXO_ESCOPO + idSolicitacao, idUsuario, chaveIdempotencia, request, Void.class,
                ObservabilidadeSolicitacaoTecnica.Operacao.ENCERRAMENTO);
        if (consulta.repetida()) {
            return;
        }
        encerrarInterno(idSolicitacao, request, idUsuario, consulta);
    }

    private void encerrarInterno(
            UUID idSolicitacao,
            EncerrarSaldoTecnicoRequest request,
            UUID idUsuario,
            IdempotenciaComandoSolicitacaoService.Consulta<Void> consulta
    ) {
        Objects.requireNonNull(request, "Os dados do encerramento sao obrigatorios");
        Objects.requireNonNull(idUsuario, "O usuario do encerramento e obrigatorio");
        SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitacao material nao encontrada"));
        if (solicitacao.getContexto() != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("O encerramento de saldo exige solicitacao tecnica.");
        }
        if (!Objects.equals(solicitacao.getVersao(), request.versaoEsperada())) {
            observabilidade.registrarConflito(
                    ObservabilidadeSolicitacaoTecnica.Operacao.ENCERRAMENTO,
                    ObservabilidadeSolicitacaoTecnica.TipoConflito.VERSAO);
            throw new ConflitoVersaoException(
                    ConflitoVersaoException.Recurso.SOLICITACAO,
                    request.versaoEsperada(), solicitacao.getVersao());
        }
        SolicitacaoMaterial.StatusSolicitacao statusAnterior = solicitacao.getStatus();
        var encerradaEmAnterior = solicitacao.getEncerradaEm();
        UUID usuarioEncerramentoAnterior = solicitacao.getIdUsuarioEncerramento();
        String motivoEncerramentoAnterior = solicitacao.getMotivoEncerramento();
        solicitacao.encerrarSaldoPendente(idUsuario, request.motivo());
        OperacaoSolicitacaoMaterial operacao = operacaoRepository.saveAndFlush(
                new OperacaoSolicitacaoMaterial(
                        solicitacao, TipoOperacaoSolicitacao.ENCERRAMENTO,
                        solicitacao.getIdEquipeSaque(), idUsuario, request.motivo(),
                        null, null, OrigemComandoTecnico.UI, null));
        solicitacaoRepository.save(solicitacao);
        List<AlteracaoEventoSolicitacao> alteracoes = new ArrayList<>();
        alteracoes.add(new AlteracaoEventoSolicitacao(
                "status", statusAnterior, solicitacao.getStatus()));
        alteracoes.add(new AlteracaoEventoSolicitacao(
                "encerradaEm", encerradaEmAnterior, solicitacao.getEncerradaEm()));
        alteracoes.add(new AlteracaoEventoSolicitacao(
                "idUsuarioEncerramento", usuarioEncerramentoAnterior, solicitacao.getIdUsuarioEncerramento()));
        alteracoes.add(new AlteracaoEventoSolicitacao(
                "motivoEncerramento", motivoEncerramentoAnterior, solicitacao.getMotivoEncerramento()));
        gravadorEvento.registrarFilho(solicitacao, TipoEventoSolicitacao.ENCERRAMENTO_CONFIRMADO,
                new PayloadEventoSolicitacao(
                        "ENCERRAR_SALDO", alteracoes, Map.of("idOperacao", operacao.getId())),
                new ContextoRegistroEventoSolicitacao(
                        idUsuario, "USUARIO " + idUsuario, OrigemComandoTecnico.UI, request.motivo()));
        if (consulta != null) {
            idempotencia.registrar(
                    consulta, solicitacao.getId(), operacao.getId(),
                    Map.of("idOperacao", operacao.getId().toString()));
        }
    }
}
