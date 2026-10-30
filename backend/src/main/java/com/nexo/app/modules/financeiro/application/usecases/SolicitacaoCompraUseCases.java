package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.DocumentoDownload;
import com.nexo.app.modules.financeiro.application.dto.DocumentoRequest;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.EventoSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraSpecification;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ciclo de vida das solicitações de compra.
 *
 * <p>Orquestra o que o domínio e os serviços de apoio decidem: o
 * {@link ValidadorSolicitacao} confere o preenchimento contra o formulário, a
 * entidade garante as transições e o {@link MontadorSolicitacao} devolve a
 * resposta pronta para a tela. Nenhuma regra de negócio nova nasce aqui.</p>
 */
@Service
@RequiredArgsConstructor
public class SolicitacaoCompraUseCases {

    /** Quem tem esta authority atua sobre solicitações de terceiros. */
    private static final String PERMISSAO_ADMINISTRAR = "financeiro:compra:administrar";

    private final SolicitacaoCompraRepository repository;
    private final ResolvedorBuscaGlobalSolicitacaoFinanceira resolvedorBuscaGlobal;
    private final FormularioSolicitacaoUseCases formularioUseCases;
    private final ValidadorSolicitacao validador;
    private final ResolvedorReferencias resolvedor;
    private final MontadorSolicitacao montador;
    private final DocumentoFinanceiroUseCases documentos;
    private final EventosSolicitacaoFinanceira eventos;
    private final HistoricoSolicitacaoFinanceira historico;
    private final SolicitacaoPagamentoRepository pagamentoRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── Preenchimento ───────────────────────────────────────────────────────────

    /**
     * Cria a solicitação como rascunho. O rascunho existe para que o solicitante
     * anexe orçamentos e complete o formulário em etapas sem ocupar a fila da
     * diretoria — a validação completa só é cobrada na submissão.
     */
    @Transactional
    public SolicitacaoCompraResponse criar(SolicitacaoRequest request, UUID usuarioSolicitanteId) {
        var formulario = formularioUseCases.buscarEntity(request.formularioId());
        var valores = validador.validar(formulario, request.valoresOuVazio());

        var compra = repository.save(new SolicitacaoCompra(formulario, montarNucleo(valores), usuarioSolicitanteId));
        historico.registrar(compra, TipoEventoSolicitacao.CRIADA, null, usuarioSolicitanteId,
                compra.getNucleo().getValor(), null);
        return montador.montar(compra);
    }

    @Transactional
    public SolicitacaoCompraResponse atualizar(UUID id, SolicitacaoRequest request) {
        var compra = buscarEntity(id);
        exigirAutoria(compra);
        compra.exigirEditavel();

        // A variante informada precisa ser respeitada: o formulário pode ter sido
        // trocado entre a abertura e a edição.
        var formulario = formularioUseCases.buscarEntity(request.formularioId());
        if (!formulario.getId().equals(compra.getFormulario().getId())) {
            throw new InvarianteVioladaException(
                    "Não é possível trocar a variante de formulário de uma solicitação já aberta.");
        }

        compra.atualizarConteudo(montarNucleo(validador.validar(formulario, request.valoresOuVazio())));
        return montador.montar(repository.save(compra));
    }

    /** Envia o rascunho para autorização. */
    @Transactional
    public SolicitacaoCompraResponse submeter(UUID id) {
        var compra = buscarEntity(id);
        exigirAutoria(compra);
        // Submeter o que já está submetido é inofensivo por desenho, mas não é fato
        // novo: anunciar de novo colocaria a mesma solicitação na frente do aprovador
        // toda vez que alguém clicasse duas vezes.
        boolean eraRascunho = compra.getStatus() == StatusSolicitacao.RASCUNHO;
        compra.submeter();
        var resposta = montador.montar(repository.save(compra));

        if (eraRascunho) {
            historico.registrar(compra, TipoEventoSolicitacao.SUBMETIDA, StatusSolicitacao.RASCUNHO,
                    SecurityUtils.getUsuarioLogadoId(), compra.getNucleo().getValor(), null);
            eventPublisher.publishEvent(eventos.submetida(compra));
        }
        return resposta;
    }

    // ── Decisão ─────────────────────────────────────────────────────────────────

    @Transactional
    public SolicitacaoCompraResponse aprovar(UUID id, AprovacaoRequest request, UUID usuarioDecisaoId) {
        var compra = buscarEntity(id);
        compra.aprovar(usuarioDecisaoId, request == null ? null : request.valorAprovado(),
                request == null ? null : request.observacao());
        documentos.conferirDocumentosDaSolicitacao(
                id, usuarioDecisaoId, request == null ? null : request.observacao());
        var resposta = montador.montar(repository.save(compra));
        historico.registrar(compra, TipoEventoSolicitacao.APROVADA, StatusSolicitacao.SOLICITADO,
                usuarioDecisaoId, compra.getValorAprovado(), compra.getObservacaoDecisao());

        eventPublisher.publishEvent(eventos.aprovada(compra));
        return resposta;
    }

    @Transactional
    public SolicitacaoCompraResponse rejeitar(UUID id, String motivo, UUID usuarioDecisaoId) {
        var compra = buscarEntity(id);
        compra.rejeitar(usuarioDecisaoId, motivo);
        var resposta = montador.montar(repository.save(compra));
        historico.registrar(compra, TipoEventoSolicitacao.REJEITADA, StatusSolicitacao.SOLICITADO,
                usuarioDecisaoId, null, compra.getMotivoRejeicao());

        eventPublisher.publishEvent(eventos.rejeitada(compra));
        return resposta;
    }

    @Transactional
    public SolicitacaoCompraResponse cancelar(UUID id, String motivo, UUID usuarioId) {
        var compra = buscarEntity(id);
        exigirAutoria(compra);
        var statusAnterior = compra.getStatus();
        compra.cancelar(usuarioId, motivo);
        var resposta = montador.montar(repository.save(compra));
        historico.registrar(compra, TipoEventoSolicitacao.CANCELADA, statusAnterior, usuarioId, null,
                compra.getMotivoCancelamento());
        return resposta;
    }

    /**
     * Devolve uma compra aprovada ou rejeitada à fila de decisão.
     *
     * <p>Pagamentos abertos a partir da compra se apoiam na aprovação dela; enquanto
     * algum estiver em andamento ou aprovado, a decisão não pode ser desfeita —
     * eles precisam ser cancelados ou rejeitados antes.</p>
     */
    @Transactional
    public SolicitacaoCompraResponse desfazerDecisao(UUID id, String motivo, UUID usuarioId) {
        var compra = buscarEntity(id);
        List<Long> pagamentos = pagamentoRepository.numerosVinculadosEmAndamento(id);
        if (!pagamentos.isEmpty()) {
            throw new InvarianteVioladaException("A compra tem pagamento vinculado em andamento ("
                    + pagamentos.stream().map(numero -> "nº " + numero).collect(Collectors.joining(", "))
                    + "). Cancele ou rejeite o pagamento antes de desfazer a decisão.");
        }
        var anterior = compra.desfazerDecisao(motivo);
        var resposta = montador.montar(repository.save(compra));
        historico.registrar(compra, TipoEventoSolicitacao.DECISAO_DESFEITA, anterior.statusAnterior(), usuarioId,
                anterior.valorAprovadoAnterior(), motivo);

        eventPublisher.publishEvent(eventos.decisaoDesfeita(compra, anterior, motivo, usuarioId));
        return resposta;
    }

    @Transactional(readOnly = true)
    public List<EventoSolicitacaoResponse> historico(UUID id) {
        return historico.listar(buscarEntity(id).getId());
    }

    // ── Documentos ──────────────────────────────────────────────────────────────

    /**
     * Anexa um documento à compra.
     *
     * <p>Deliberadamente <b>sem</b> {@code exigirEditavel()} e sem exigir autoria:
     * o orçamento chega antes da decisão, mas a nota fiscal e o boleto chegam
     * depois dela, e quem os recebe costuma ser o financeiro, não quem abriu o
     * pedido. Quem pode anexar é definido pela permissão do endpoint.</p>
     */
    @Transactional
    public SolicitacaoCompraResponse adicionarDocumento(UUID id, List<MultipartFile> arquivos,
                                                        TipoDocumentoFinanceiro tipo, UUID usuarioId) {
        var compra = buscarEntity(id);
        documentos.enviarPara(compra, arquivos, DocumentoRequest.deTipo(tipo), usuarioId);
        return montador.montar(compra);
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
    public SolicitacaoCompraResponse buscarPorId(UUID id) {
        return montador.montar(buscarEntity(id));
    }

    /** Busca pelo número sequencial, que é como as equipes se referem à solicitação. */
    @Transactional(readOnly = true)
    public SolicitacaoCompraResponse buscarPorNumero(Long numero) {
        return montador.montar(repository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Solicitação de compra nº " + numero + " não encontrada.")));
    }

    @Transactional(readOnly = true)
    public PageResponse<SolicitacaoCompraResponse> listar(SolicitacaoCompraFilter filtro, Pageable pageable) {
        var idsEncontrados = resolvedorBuscaGlobal.resolver(filtro == null ? null : filtro.buscaGlobal());
        var spec = SolicitacaoCompraSpecification.comFiltros(
                filtro, SecurityUtils.getUsuarioLogadoId(), idsEncontrados);
        return montador.montarPagina(
                repository.findAll(spec, OrdenacaoSolicitacaoFinanceira.normalizar(pageable)), montador::montar);
    }

    SolicitacaoCompra buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação de compra não encontrada."));
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

    /**
     * Alterar o conteúdo é prerrogativa de quem abriu a solicitação. A permissão de
     * administração existe para o financeiro corrigir pedidos de terceiros sem
     * precisar cancelar e refazer.
     */
    private void exigirAutoria(SolicitacaoCompra compra) {
        UUID usuarioAtual = SecurityUtils.getUsuarioLogadoId();
        boolean autor = usuarioAtual != null && usuarioAtual.equals(compra.getUsuarioSolicitanteId());
        if (!autor && !SecurityUtils.possuiAutoridade(PERMISSAO_ADMINISTRAR)) {
            throw new InvarianteVioladaException("Somente quem abriu a solicitação pode alterá-la.");
        }
    }
}
