package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.DocumentoDownload;
import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroFilter;
import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoRequest;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroSpecification;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repositório documental do financeiro: recebimento, busca, conferência e
 * arquivamento dos documentos.
 *
 * <p>O documento é agregado próprio, e é isso que permite anexar a nota fiscal a
 * uma compra já aprovada — situação que o desenho anterior, com o anexo preso ao
 * ciclo de vida da solicitação, tornava impossível justamente no momento em que a
 * nota chega.</p>
 *
 * <p>Quem pode conferir é questão de permissão, cobrada no endpoint
 * ({@code financeiro:documento:conferir}); o domínio cuida de <em>quando</em> cada
 * transição é possível.</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentoFinanceiroUseCases {

    /** Quem tem esta authority remove documento de terceiros. */
    private static final String PERMISSAO_GERIR = "financeiro:documento:gerir";

    private static final String PREFIXO_AVULSO = "financeiro/documentos";

    private final DocumentoFinanceiroRepository repository;
    private final SolicitacaoCompraRepository compraRepository;
    private final SolicitacaoPagamentoRepository pagamentoRepository;
    private final ArmazenamentoDocumentoService armazenamento;
    private final MontadorDocumento montador;

    // ── Recebimento ─────────────────────────────────────────────────────────────

    /**
     * Recebe um documento avulso — o boleto que chegou por e-mail, a nota que o
     * fornecedor mandou sem que houvesse pedido no sistema.
     */
    @Transactional
    public DocumentoFinanceiroResponse enviar(MultipartFile arquivo, DocumentoRequest request, UUID usuarioId) {
        DocumentoRequest dados = request == null ? DocumentoRequest.vazio() : request;
        var documento = new DocumentoFinanceiro(
                armazenamento.armazenar(PREFIXO_AVULSO, arquivo), dados.paraMetadados(), usuarioId);
        documento.deduzirCompetenciaSeAusente();
        documento.conferir(usuarioId, "Conferência automática por inclusão direta de documento avulso.");
        return montador.montar(repository.save(documento));
    }

    /**
     * Recebe um documento já vinculado a uma solicitação.
     *
     * <p>Os campos que a solicitação conhece são copiados quando o envio não os
     * informa: base, centro de custo, obra, fornecedor, valor e — no caso da nota
     * fiscal — o número que já foi digitado no formulário. É o que faz o anexo
     * rápido continuar rápido sem produzir documento sem metadado.</p>
     */
    @Transactional
    public DocumentoFinanceiroResponse enviarPara(SolicitacaoBase solicitacao, MultipartFile arquivo,
                                                  DocumentoRequest request, UUID usuarioId) {
        return enviarPara(solicitacao, List.of(arquivo), request, usuarioId).getFirst();
    }

    /**
     * Recebe um lote de documentos vinculados à mesma solicitação.
     *
     * <p>O lote reaproveita os mesmos metadados e regras do envio unitário. Se um
     * dos arquivos falhar, a transação inteira é revertida e os objetos que já
     * tiverem sido enviados ao storage são removidos.</p>
     */
    @Transactional
    public List<DocumentoFinanceiroResponse> enviarPara(SolicitacaoBase solicitacao,
                                                         List<MultipartFile> arquivos,
                                                         DocumentoRequest request,
                                                         UUID usuarioId) {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new InvarianteVioladaException("Envie ao menos um arquivo.");
        }

        DocumentoRequest dados = herdarDaSolicitacao(
                request == null ? DocumentoRequest.vazio() : request, solicitacao);
        String prefixo = "financeiro/" + (solicitacao.getTipoSolicitacao() == TipoSolicitacao.COMPRA
                ? "compras" : "pagamentos") + "/" + solicitacao.getId();

        List<String> objetosArmazenados = new ArrayList<>(arquivos.size());
        boolean limpezaAgendada = agendarLimpezaEmRollback(objetosArmazenados);
        try {
            List<DocumentoFinanceiro> documentos = new ArrayList<>(arquivos.size());
            for (MultipartFile arquivo : arquivos) {
                var dadosArquivo = armazenamento.armazenar(prefixo, arquivo);
                objetosArmazenados.add(dadosArquivo.objectKey());

                var documento = new DocumentoFinanceiro(dadosArquivo, dados.paraMetadados(), usuarioId);
                documento.vincular(solicitacao);
                documento.deduzirCompetenciaSeAusente();
                if (solicitacao.getStatus() == StatusSolicitacao.APROVADO) {
                    UUID conferidorId = solicitacao.getUsuarioDecisaoId() != null
                            ? solicitacao.getUsuarioDecisaoId() : usuarioId;
                    documento.conferir(conferidorId, "Conferência automática por inclusão em solicitação já aprovada.");
                }
                documentos.add(documento);
            }

            repository.saveAll(documentos);
            return documentos.stream().map(montador::montar).toList();
        } catch (RuntimeException exception) {
            if (!limpezaAgendada) {
                removerObjetos(objetosArmazenados);
            }
            throw exception;
        }
    }

    private boolean agendarLimpezaEmRollback(List<String> objetosArmazenados) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    removerObjetos(objetosArmazenados);
                }
            }
        });
        return true;
    }

    private void removerObjetos(List<String> objetosArmazenados) {
        objetosArmazenados.forEach(armazenamento::removerSilenciosamente);
    }

    /**
     * Completa o payload com o que a solicitação já sabe. Só preenche o que veio
     * vazio: o que a pessoa digitou no envio sempre vence, porque ela está olhando
     * o documento e o formulário pode ter sido preenchido semanas antes.
     */
    private DocumentoRequest herdarDaSolicitacao(DocumentoRequest request, SolicitacaoBase solicitacao) {
        NucleoSolicitacao nucleo = solicitacao.getNucleo();
        boolean notaFiscal = request.tipo() == TipoDocumentoFinanceiro.NOTA_FISCAL;
        return new DocumentoRequest(
                request.tipo(),
                request.numeroDocumento() != null || !notaFiscal
                        ? request.numeroDocumento() : nucleo.getNumeroNf(),
                request.chaveAcesso(),
                request.codigoPagamento(),
                request.fornecedorId() != null ? request.fornecedorId() : nucleo.getFornecedorId(),
                StringUtils.hasText(request.emissorNome())
                        ? request.emissorNome() : nomeLivreDoFornecedor(nucleo),
                request.dataEmissao(),
                request.dataVencimento() != null ? request.dataVencimento() : nucleo.getVencimento(),
                request.competencia(),
                request.valor() != null ? request.valor() : nucleo.getValor(),
                request.baseId() != null ? request.baseId() : nucleo.getBaseId(),
                request.processoId() != null ? request.processoId() : nucleo.getProcessoId(),
                request.obraId() != null ? request.obraId() : nucleo.getObraId(),
                request.descricao(),
                request.observacoes());
    }

    private static String nomeLivreDoFornecedor(NucleoSolicitacao nucleo) {
        return StringUtils.hasText(nucleo.getFornecedorNome())
                ? nucleo.getFornecedorNome() : nucleo.getFornecedorRazaoSocial();
    }

    // ── Correção ────────────────────────────────────────────────────────────────

    @Transactional
    public DocumentoFinanceiroResponse atualizar(UUID id, DocumentoRequest request) {
        var documento = buscarEntity(id);
        documento.atualizarMetadados(
                (request == null ? DocumentoRequest.vazio() : request).paraMetadados());
        documento.deduzirCompetenciaSeAusente();
        return montador.montar(repository.save(documento));
    }

    /** Aponta o documento avulso para a solicitação a que ele pertence. */
    @Transactional
    public DocumentoFinanceiroResponse vincular(UUID id, TipoSolicitacao tipo, UUID solicitacaoId) {
        var documento = buscarEntity(id);
        var solicitacao = buscarSolicitacao(tipo, solicitacaoId);
        documento.vincular(solicitacao);
        if (solicitacao.getStatus() == StatusSolicitacao.APROVADO
                && (documento.getSituacao() == SituacaoDocumento.PENDENTE || documento.getSituacao() == SituacaoDocumento.COM_PENDENCIA)) {
            UUID conferidorId = solicitacao.getUsuarioDecisaoId() != null
                    ? solicitacao.getUsuarioDecisaoId() : documento.getUsuarioEnvioId();
            documento.conferir(conferidorId, "Conferência automática por inclusão em solicitação já aprovada.");
        }
        return montador.montar(repository.save(documento));
    }

    @Transactional
    public DocumentoFinanceiroResponse desvincular(UUID id) {
        var documento = buscarEntity(id);
        documento.desvincular();
        return montador.montar(repository.save(documento));
    }

    // ── Conferência ─────────────────────────────────────────────────────────────

    /**
     * Confere automaticamente todos os documentos ativos vinculados a uma
     * solicitação que estejam aguardando conferência ou com pendência.
     * Documentos arquivados não são alterados.
     */
    @Transactional
    public void conferirDocumentosDaSolicitacao(UUID solicitacaoId, UUID usuarioId, String observacao) {
        if (solicitacaoId == null || usuarioId == null) {
            return;
        }
        var docs = repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId);
        String obs = StringUtils.hasText(observacao)
                ? observacao
                : "Conferência automática por aprovação da solicitação.";
        List<DocumentoFinanceiro> alterados = new ArrayList<>();
        for (var doc : docs) {
            if (doc.getSituacao() == SituacaoDocumento.PENDENTE || doc.getSituacao() == SituacaoDocumento.COM_PENDENCIA) {
                doc.conferir(usuarioId, obs);
                alterados.add(doc);
            }
        }
        if (!alterados.isEmpty()) {
            repository.saveAll(alterados);
        }
    }

    @Transactional
    public DocumentoFinanceiroResponse apontarPendencia(UUID id, String motivo, UUID usuarioId) {
        var documento = buscarEntity(id);
        documento.apontarPendencia(usuarioId, motivo);
        return montador.montar(repository.save(documento));
    }

    @Transactional
    public DocumentoFinanceiroResponse arquivar(UUID id) {
        var documento = buscarEntity(id);
        documento.arquivar();
        return montador.montar(repository.save(documento));
    }

    @Transactional
    public DocumentoFinanceiroResponse reabrir(UUID id) {
        var documento = buscarEntity(id);
        documento.reabrir();
        return montador.montar(repository.save(documento));
    }

    /**
     * Remove o documento do repositório.
     *
     * <p>É soft delete seguido da exclusão do objeto: a linha permanece para a
     * trilha de auditoria — quem enviou, quando, e que aquilo foi retirado — mas
     * o arquivo em si sai do storage, porque manter conteúdo fiscal de um
     * documento que alguém mandou apagar não é conservadorismo, é passivo.</p>
     */
    @Transactional
    public void remover(UUID id) {
        var documento = buscarEntity(id);
        exigirAutoria(documento);
        if (documento.getSituacao() == SituacaoDocumento.ARQUIVADO) {
            throw new InvarianteVioladaException(
                    "O documento está arquivado e não pode ser removido. Reabra-o antes, se for o caso.");
        }
        String objectKey = documento.getObjectKey();
        documento.desativar();
        repository.saveAndFlush(documento);
        armazenamento.removerSilenciosamente(objectKey);
    }

    // ── Consultas ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DocumentoFinanceiroResponse buscarPorId(UUID id) {
        return montador.montar(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentoFinanceiroResponse> listar(DocumentoFinanceiroFilter filtro, Pageable pageable) {
        var pagina = repository.findAll(DocumentoFinanceiroSpecification.comFiltros(filtro), pageable);
        var contexto = montador.novoContexto();
        return PageResponse.fromPage(pagina.map(documento -> montador.montar(documento, contexto)));
    }

    @Transactional(readOnly = true)
    public DocumentoDownload baixar(UUID id) {
        return armazenamento.baixar(buscarEntity(id));
    }

    /**
     * Remove um documento pela porta da solicitação.
     *
     * <p>Confere o vínculo antes: sem isso, o endpoint aninhado seria um caminho
     * para apagar qualquer documento do repositório informando uma solicitação
     * qualquer no caminho da URL.</p>
     */
    @Transactional
    public void removerDaSolicitacao(UUID solicitacaoId, UUID documentoId) {
        var documento = buscarEntity(documentoId);
        if (!solicitacaoId.equals(documento.getOrigem().getSolicitacaoId())) {
            throw new RecursoNaoEncontradoException("Documento não encontrado nesta solicitação.");
        }
        if (documento.getSituacao() == SituacaoDocumento.CONFERIDO) {
            throw new InvarianteVioladaException(
                    "O documento já foi conferido e não pode ser removido pela solicitação.");
        }
        remover(documentoId);
    }

    /** Baixa um documento pela porta da solicitação, conferindo o vínculo. */
    @Transactional(readOnly = true)
    public DocumentoDownload baixarDaSolicitacao(UUID solicitacaoId, UUID documentoId) {
        var documento = buscarEntity(documentoId);
        if (!solicitacaoId.equals(documento.getOrigem().getSolicitacaoId())) {
            throw new RecursoNaoEncontradoException("Documento não encontrado nesta solicitação.");
        }
        return armazenamento.baixar(documento);
    }

    /** Documentos de uma solicitação, para o detalhe dela. */
    @Transactional(readOnly = true)
    public List<DocumentoResumoResponse> listarDaSolicitacao(UUID solicitacaoId) {
        return repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId).stream()
                .map(montador::montarResumo)
                .toList();
    }

    /**
     * Documentos de várias solicitações de uma vez, indexados pela solicitação.
     * Existe para que a listagem monte a página com uma consulta, e não com uma
     * por linha.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<DocumentoResumoResponse>> mapearPorSolicitacao(Collection<UUID> solicitacaoIds) {
        if (solicitacaoIds == null || solicitacaoIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByOrigemSolicitacaoIdInAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoIds).stream()
                .collect(Collectors.groupingBy(
                        documento -> documento.getOrigem().getSolicitacaoId(),
                        Collectors.mapping(montador::montarResumo, Collectors.toList())));
    }

    /** Documento com o mesmo conteúdo já existente, quando houver. */
    @Transactional(readOnly = true)
    public DocumentoFinanceiroResponse buscarDuplicado(String hashConteudo) {
        if (!StringUtils.hasText(hashConteudo)) {
            return null;
        }
        return repository.findFirstByHashConteudoAndAtivoTrue(hashConteudo)
                .map(montador::montar)
                .orElse(null);
    }

    DocumentoFinanceiro buscarEntity(UUID id) {
        return repository.findById(id)
                .filter(DocumentoFinanceiro::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
    }

    private SolicitacaoBase buscarSolicitacao(TipoSolicitacao tipo, UUID solicitacaoId) {
        if (tipo == null || solicitacaoId == null) {
            throw new InvarianteVioladaException("Informe o tipo e o identificador da solicitação de origem.");
        }
        return tipo == TipoSolicitacao.COMPRA
                ? compraRepository.findById(solicitacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação de compra não encontrada."))
                : pagamentoRepository.findById(solicitacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação de pagamento não encontrada."));
    }

    /**
     * Retirar um documento do repositório é prerrogativa de quem o enviou. A
     * permissão de gestão existe para a contabilidade limpar envio equivocado de
     * terceiros sem depender de quem errou.
     */
    private void exigirAutoria(DocumentoFinanceiro documento) {
        UUID usuarioAtual = SecurityUtils.getUsuarioLogadoId();
        boolean autor = usuarioAtual != null && usuarioAtual.equals(documento.getUsuarioEnvioId());
        if (!autor && !SecurityUtils.possuiAutoridade(PERMISSAO_GERIR)) {
            throw new InvarianteVioladaException("Somente quem enviou o documento pode removê-lo.");
        }
    }
}
