package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.CampoValorResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoDownload;
import com.nexo.app.modules.financeiro.application.dto.DocumentoRequest;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.EventoSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.PrePreenchimentoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.CampoFormulario;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ciclo de vida das solicitações de pagamento.
 *
 * <p>Acrescenta ao fluxo comum o vínculo opcional com uma compra aprovada: uma
 * referência que serve para rastrear a origem e para poupar digitação, sem amarrar
 * os valores dos dois documentos. Quem autoriza vê de qual compra o pagamento veio
 * e decide.</p>
 */
@Service
@RequiredArgsConstructor
public class SolicitacaoPagamentoUseCases {

    /** Quem tem esta authority atua sobre solicitações de terceiros. */
    private static final String PERMISSAO_ADMINISTRAR = "financeiro:pagamento:administrar";

    /** Campos que o pagamento copia da compra vinculada, quando a variante os possui. */
    private static final List<CampoCanonico> HERDADOS_DA_COMPRA = List.of(
            CampoCanonico.SOLICITANTE_COLABORADOR,
            CampoCanonico.BASE,
            CampoCanonico.PROCESSO,
            CampoCanonico.OBRA,
            CampoCanonico.FORNECEDOR,
            CampoCanonico.FORNECEDOR_NOME,
            CampoCanonico.FORNECEDOR_RAZAO_SOCIAL,
            CampoCanonico.DESCRICAO,
            CampoCanonico.FORMA_PAGAMENTO,
            CampoCanonico.DADOS_PAGAMENTO);

    private final SolicitacaoPagamentoRepository repository;
    private final ResolvedorBuscaGlobalSolicitacaoFinanceira resolvedorBuscaGlobal;
    private final SolicitacaoCompraUseCases compraUseCases;
    private final FormularioSolicitacaoUseCases formularioUseCases;
    private final ValidadorSolicitacao validador;
    private final ResolvedorReferencias resolvedor;
    private final MontadorSolicitacao montador;
    private final DocumentoFinanceiroUseCases documentos;
    private final EventosSolicitacaoFinanceira eventos;
    private final HistoricoSolicitacaoFinanceira historico;
    private final ApplicationEventPublisher eventPublisher;

    // ── Preenchimento ───────────────────────────────────────────────────────────

    @Transactional
    public SolicitacaoPagamentoResponse criar(SolicitacaoRequest request, UUID usuarioSolicitanteId) {
        var formulario = formularioUseCases.buscarEntity(request.formularioId());
        var valores = validador.validar(formulario, request.valoresOuVazio());

        var pagamento = repository.save(new SolicitacaoPagamento(formulario, montarNucleo(valores), usuarioSolicitanteId));
        historico.registrar(pagamento, TipoEventoSolicitacao.CRIADA, null, usuarioSolicitanteId,
                pagamento.getNucleo().getValor(), null);
        return montador.montar(pagamento);
    }

    @Transactional
    public SolicitacaoPagamentoResponse atualizar(UUID id, SolicitacaoRequest request) {
        var pagamento = buscarEntity(id);
        exigirAutoria(pagamento);
        pagamento.exigirEditavel();

        var formulario = formularioUseCases.buscarEntity(request.formularioId());
        if (!formulario.getId().equals(pagamento.getFormulario().getId())) {
            throw new InvarianteVioladaException(
                    "Não é possível trocar a variante de formulário de uma solicitação já aberta.");
        }

        pagamento.atualizarConteudo(montarNucleo(validador.validar(formulario, request.valoresOuVazio())));
        return montador.montar(repository.save(pagamento));
    }

    @Transactional
    public SolicitacaoPagamentoResponse submeter(UUID id) {
        var pagamento = buscarEntity(id);
        exigirAutoria(pagamento);
        // Ver a nota em SolicitacaoCompraUseCases.submeter: reenvio não é fato novo.
        boolean eraRascunho = pagamento.getStatus() == StatusSolicitacao.RASCUNHO;
        pagamento.submeter();
        var resposta = montador.montar(repository.save(pagamento));

        if (eraRascunho) {
            historico.registrar(pagamento, TipoEventoSolicitacao.SUBMETIDA, StatusSolicitacao.RASCUNHO,
                    SecurityUtils.getUsuarioLogadoId(), pagamento.getNucleo().getValor(), null);
            eventPublisher.publishEvent(eventos.submetida(pagamento));
        }
        return resposta;
    }

    // ── Decisão ─────────────────────────────────────────────────────────────────

    @Transactional
    public SolicitacaoPagamentoResponse aprovar(UUID id, AprovacaoRequest request, UUID usuarioDecisaoId) {
        var pagamento = buscarEntity(id);
        pagamento.aprovar(usuarioDecisaoId, request == null ? null : request.valorAprovado(),
                request == null ? null : request.observacao());
        documentos.conferirDocumentosDaSolicitacao(
                id, usuarioDecisaoId, request == null ? null : request.observacao());
        var resposta = montador.montar(repository.save(pagamento));
        historico.registrar(pagamento, TipoEventoSolicitacao.APROVADA, StatusSolicitacao.SOLICITADO,
                usuarioDecisaoId, pagamento.getValorAprovado(), pagamento.getObservacaoDecisao());

        eventPublisher.publishEvent(eventos.aprovada(pagamento));
        return resposta;
    }

    @Transactional
    public SolicitacaoPagamentoResponse rejeitar(UUID id, String motivo, UUID usuarioDecisaoId) {
        var pagamento = buscarEntity(id);
        pagamento.rejeitar(usuarioDecisaoId, motivo);
        var resposta = montador.montar(repository.save(pagamento));
        historico.registrar(pagamento, TipoEventoSolicitacao.REJEITADA, StatusSolicitacao.SOLICITADO,
                usuarioDecisaoId, null, pagamento.getMotivoRejeicao());

        eventPublisher.publishEvent(eventos.rejeitada(pagamento));
        return resposta;
    }

    @Transactional
    public SolicitacaoPagamentoResponse cancelar(UUID id, String motivo, UUID usuarioId) {
        var pagamento = buscarEntity(id);
        exigirAutoria(pagamento);
        var statusAnterior = pagamento.getStatus();
        pagamento.cancelar(usuarioId, motivo);
        var resposta = montador.montar(repository.save(pagamento));
        historico.registrar(pagamento, TipoEventoSolicitacao.CANCELADA, statusAnterior, usuarioId, null,
                pagamento.getMotivoCancelamento());
        return resposta;
    }

    /** Devolve um pagamento aprovado ou rejeitado à fila de decisão. */
    @Transactional
    public SolicitacaoPagamentoResponse desfazerDecisao(UUID id, String motivo, UUID usuarioId) {
        var pagamento = buscarEntity(id);
        var anterior = pagamento.desfazerDecisao(motivo);
        var resposta = montador.montar(repository.save(pagamento));
        historico.registrar(pagamento, TipoEventoSolicitacao.DECISAO_DESFEITA, anterior.statusAnterior(), usuarioId,
                anterior.valorAprovadoAnterior(), motivo);

        eventPublisher.publishEvent(eventos.decisaoDesfeita(pagamento, anterior, motivo, usuarioId));
        return resposta;
    }

    @Transactional(readOnly = true)
    public List<EventoSolicitacaoResponse> historico(UUID id) {
        return historico.listar(buscarEntity(id).getId());
    }

    // ── Pré-preenchimento a partir da compra ────────────────────────────────────

    /**
     * Devolve os valores que a variante de pagamento pode copiar de uma compra
     * aprovada, já filtrados pelos campos que ela realmente possui.
     *
     * <p>É conveniência, não regra: os campos vêm preenchidos e continuam
     * editáveis, porque o que se paga pode legitimamente diferir do que se
     * autorizou comprar — outro fornecedor equivalente, valor final negociado.</p>
     */
    @Transactional(readOnly = true)
    public PrePreenchimentoResponse prePreencher(UUID idCompra, UUID idFormularioPagamento) {
        var compra = compraUseCases.buscarEntity(idCompra);
        if (!compra.isAprovada()) {
            throw new InvarianteVioladaException(
                    "A solicitação de compra nº " + compra.getNumero() + " ainda não foi aprovada.");
        }

        FormularioSolicitacao formulario = formularioUseCases.buscarEntity(idFormularioPagamento);
        Map<String, CampoValorResponse> valores = new LinkedHashMap<>();
        List<String> herdados = new ArrayList<>();

        for (CampoCanonico canonico : HERDADOS_DA_COMPRA) {
            CampoFormulario campo = formulario.buscarCampoPorCanonico(canonico).orElse(null);
            if (campo == null) {
                continue;
            }
            Object bruto = compra.getNucleo().valorDe(canonico);
            if (bruto == null) {
                continue;
            }
            valores.put(campo.getChave(), montador.valorLegivel(campo, bruto));
            herdados.add(campo.getChave());
        }

        sugerir(formulario, CampoCanonico.VALOR, compra.valorVigente(), valores, null);
        sugerir(formulario, CampoCanonico.SOLICITACAO_COMPRA, compra.getId(), valores, herdados);
        sugerir(formulario, CampoCanonico.POSSUI_COMPRA_PREVIA, Boolean.TRUE, valores, herdados);

        return new PrePreenchimentoResponse(montador.montarResumo(compra), valores, List.copyOf(herdados));
    }

    // ── Documentos ──────────────────────────────────────────────────────────────

    /**
     * Anexa um documento ao pagamento. Ver a nota em
     * {@link SolicitacaoCompraUseCases#adicionarDocumento}: é permitido depois da
     * aprovação, porque é aí que a nota fiscal e o boleto chegam.
     */
    @Transactional
    public SolicitacaoPagamentoResponse adicionarDocumento(UUID id, List<MultipartFile> arquivos,
                                                           TipoDocumentoFinanceiro tipo, UUID usuarioId) {
        var pagamento = buscarEntity(id);
        documentos.enviarPara(pagamento, arquivos, DocumentoRequest.deTipo(tipo), usuarioId);
        return montador.montar(pagamento);
    }

    @Transactional
    public void removerDocumento(UUID id, UUID idDocumento) {
        documentos.removerDaSolicitacao(id, idDocumento);
    }

    @Transactional(readOnly = true)
    public DocumentoDownload baixarDocumento(UUID id, UUID idDocumento) {
        return documentos.baixarDaSolicitacao(id, idDocumento);
    }

    @Transactional(readOnly = true)
    public List<DocumentoResumoResponse> listarDocumentos(UUID id) {
        return documentos.listarDaSolicitacao(id);
    }

    // ── Consultas ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SolicitacaoPagamentoResponse buscarPorId(UUID id) {
        return montador.montar(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    public SolicitacaoPagamentoResponse buscarPorNumero(Long numero) {
        return montador.montar(repository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Solicitação de pagamento nº " + numero + " não encontrada.")));
    }

    @Transactional(readOnly = true)
    public PageResponse<SolicitacaoPagamentoResponse> listar(SolicitacaoPagamentoFilter filtro, Pageable pageable) {
        var idsEncontrados = resolvedorBuscaGlobal.resolver(filtro == null ? null : filtro.buscaGlobal());
        var spec = SolicitacaoPagamentoSpecification.comFiltros(
                filtro, SecurityUtils.getUsuarioLogadoId(), idsEncontrados);
        return montador.montarPagina(
                repository.findAll(spec, OrdenacaoSolicitacaoFinanceira.normalizar(pageable)), montador::montar);
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────

    /**
     * Registra uma sugestão para um campo, se a variante o possuir.
     *
     * @param herdados lista a marcar, ou nulo quando o valor é apenas um ponto de
     *                 partida que o solicitante deve poder mudar à vontade
     */
    private void sugerir(FormularioSolicitacao formulario, CampoCanonico canonico, Object valor,
                         Map<String, CampoValorResponse> valores, List<String> herdados) {
        formulario.buscarCampoPorCanonico(canonico).ifPresent(campo -> {
            valores.put(campo.getChave(), montador.valorLegivel(campo, valor));
            if (herdados != null) {
                herdados.add(campo.getChave());
            }
        });
    }

    SolicitacaoPagamento buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação de pagamento não encontrada."));
    }

    private NucleoSolicitacao montarNucleo(ValoresCanonicos valores) {
        UUID fornecedorId = valores.referencia(CampoCanonico.FORNECEDOR);
        String nome = resolvedor.buscarFornecedor(fornecedorId)
                .map(Fornecedor::getNomeFantasia)
                .orElseGet(() -> {
                    String fantasia = valores.texto(CampoCanonico.FORNECEDOR_NOME);
                    return fantasia != null ? fantasia
                            : valores.texto(CampoCanonico.FORNECEDOR_RAZAO_SOCIAL);
                });
        return NucleoSolicitacao.de(valores, nome);
    }

    private void exigirAutoria(SolicitacaoPagamento pagamento) {
        UUID usuarioAtual = SecurityUtils.getUsuarioLogadoId();
        boolean autor = usuarioAtual != null && usuarioAtual.equals(pagamento.getUsuarioSolicitanteId());
        if (!autor && !SecurityUtils.possuiAutoridade(PERMISSAO_ADMINISTRAR)) {
            throw new InvarianteVioladaException("Somente quem abriu a solicitação pode alterá-la.");
        }
    }
}
