package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.modules.sst.application.dto.AlertaDocumentoSstResponse;
import com.nexo.app.modules.sst.application.dto.DocumentoSstDownload;
import com.nexo.app.modules.sst.application.dto.DocumentoSstFilter;
import com.nexo.app.modules.sst.application.dto.DocumentoSstMetadataRequest;
import com.nexo.app.modules.sst.application.dto.DocumentoSstResponse;
import com.nexo.app.modules.sst.application.dto.ResumoDocumentosSstResponse;
import com.nexo.app.modules.sst.domain.AlertaValidadeDocumentoSst;
import com.nexo.app.modules.sst.domain.CategoriaDocumentoSst;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.DadosDocumentoSst;
import com.nexo.app.modules.sst.domain.DocumentoSst;
import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import com.nexo.app.modules.sst.domain.ReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.SituacaoAlertaDocumentoSst;
import com.nexo.app.modules.sst.domain.SituacaoConferenciaDocumentoSst;
import com.nexo.app.modules.sst.domain.StatusValidadeDocumentoSst;
import com.nexo.app.modules.sst.domain.TreinamentoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AlertaValidadeDocumentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CategoriaDocumentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CorpoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DocumentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DocumentoSstSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.MembroCorpoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ReuniaoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TreinamentoGovernancaSstRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentoSstUseCases {

    private final DocumentoSstRepository repository;
    private final CategoriaDocumentoSstRepository categoriaRepository;
    private final AlertaValidadeDocumentoSstRepository alertaRepository;
    private final TreinamentoGovernancaSstRepository treinamentoRepository;
    private final CorpoGovernancaSstRepository corpoRepository;
    private final MembroCorpoSstRepository membroRepository;
    private final ReuniaoGovernancaSstRepository reuniaoRepository;
    private final BaseOperacionalApi baseOperacionalApi;
    private final ColaboradorApi colaboradorApi;
    private final ArquivoDocumentoSstService arquivoService;
    private final PlatformTransactionManager transactionManager;

    public DocumentoSstResponse enviar(DocumentoSstMetadataRequest request, MultipartFile arquivo,
                                       UUID idDocumentoSubstituido, UUID ator) {
        Objects.requireNonNull(request, "Os metadados são obrigatórios.");
        exigirAtor(ator);
        ArquivoDocumentoSstService.ArquivoProcessado processado = arquivoService.processar(arquivo);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> validarParaEnvio(request, idDocumentoSubstituido,
                processado.metadados().sha256()));
        arquivoService.enviar(processado);
        try {
            return tx.execute(status -> persistir(request, processado, idDocumentoSubstituido, ator));
        } catch (RuntimeException exception) {
            try {
                arquivoService.remover(processado.metadados().objectKey());
            } catch (RuntimeException remocao) {
                exception.addSuppressed(remocao);
            }
            if (exception instanceof DataIntegrityViolationException) {
                throw new ConflitoGovernancaSstException(
                        "Este arquivo já foi registrado para o mesmo titular e categoria.");
            }
            throw exception;
        }
    }

    @Transactional
    public DocumentoSstResponse aprovar(UUID id, UUID ator) {
        DocumentoSst documento = buscar(id);
        DocumentoSst anterior = documento.getIdDocumentoSubstituido() == null ? null
                : repository.findByIdAndAtivoTrue(documento.getIdDocumentoSubstituido())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "A versão anterior do documento não foi encontrada."));
        documento.aprovar(exigirAtor(ator), anterior);
        if (anterior != null) {
            repository.save(anterior);
            superarAlertas(anterior.getId());
        }
        return montar(repository.save(documento), categorias(List.of(documento)));
    }

    @Transactional
    public DocumentoSstResponse rejeitar(UUID id, String motivo, UUID ator) {
        DocumentoSst documento = buscar(id);
        documento.rejeitar(exigirAtor(ator), motivo);
        return montar(repository.save(documento), categorias(List.of(documento)));
    }

    @Transactional
    public DocumentoSstResponse arquivar(UUID id, String motivo, UUID ator) {
        DocumentoSst documento = buscar(id);
        documento.arquivar(exigirAtor(ator), motivo);
        superarAlertas(documento.getId());
        return montar(repository.save(documento), categorias(List.of(documento)));
    }

    @Transactional(readOnly = true)
    public DocumentoSstResponse buscarPorId(UUID id) {
        DocumentoSst documento = buscar(id);
        return montar(documento, categorias(List.of(documento)));
    }

    @Transactional(readOnly = true)
    public DocumentoSstDownload baixar(UUID id) {
        return arquivoService.baixar(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentoSstResponse> listar(DocumentoSstFilter filtro, Pageable pageable) {
        Page<DocumentoSst> pagina = repository.findAll(
                DocumentoSstSpecification.comFiltros(filtro, LocalDate.now()), pageable);
        Map<UUID, CategoriaDocumentoSst> categorias = categorias(pagina.getContent());
        return PageResponse.fromPage(pagina.map(item -> montar(item, categorias)));
    }

    @Transactional(readOnly = true)
    public List<DocumentoSstResponse> historico(UUID id) {
        DocumentoSst referencia = buscar(id);
        List<DocumentoSst> historico = repository
                .findAllByCategoriaCodigoAndIdBaseAndEscopoTitularAndIdTitularOrderByDataCriacaoAsc(
                        referencia.getCategoriaCodigo(), referencia.getIdBase(),
                        referencia.getEscopoTitular(), referencia.getIdTitular());
        Map<UUID, CategoriaDocumentoSst> categorias = categorias(historico);
        return historico.stream().map(item -> montar(item, categorias)).toList();
    }

    @Transactional(readOnly = true)
    public ResumoDocumentosSstResponse resumo(DocumentoSstFilter filtro) {
        LocalDate data = LocalDate.now();
        List<DocumentoSst> documentos = repository.findAll(
                DocumentoSstSpecification.comFiltros(filtro, data));
        long regulares = documentos.stream().filter(d -> d.statusValidade(data)
                == StatusValidadeDocumentoSst.REGULAR).count();
        long aVencer = documentos.stream().filter(d -> d.statusValidade(data)
                == StatusValidadeDocumentoSst.A_VENCER).count();
        long vencidos = documentos.stream().filter(d -> d.statusValidade(data)
                == StatusValidadeDocumentoSst.VENCIDO).count();
        long semValidade = documentos.stream().filter(d -> d.statusValidade(data)
                == StatusValidadeDocumentoSst.SEM_VALIDADE).count();
        long pendentes = documentos.stream().filter(d -> d.getSituacaoConferencia()
                == SituacaoConferenciaDocumentoSst.PENDING_REVIEW).count();
        long rejeitados = documentos.stream().filter(d -> d.getSituacaoConferencia()
                == SituacaoConferenciaDocumentoSst.REJECTED).count();
        return new ResumoDocumentosSstResponse(documentos.size(), regulares, aVencer,
                vencidos, semValidade, pendentes, rejeitados, data);
    }

    @Transactional
    public List<AlertaDocumentoSstResponse> processarAlertas(LocalDate dataAvaliacao) {
        LocalDate data = dataAvaliacao == null ? LocalDate.now() : dataAvaliacao;
        List<DocumentoSst> candidatos = repository.buscarDocumentosParaAlerta(
                SituacaoConferenciaDocumentoSst.APPROVED, data);
        Map<UUID, CategoriaDocumentoSst> categorias = categorias(candidatos);
        Map<String, AlertaValidadeDocumentoSst> novos = new LinkedHashMap<>();
        for (DocumentoSst documento : candidatos) {
            CategoriaDocumentoSst categoria = categorias.get(documento.getIdCategoria());
            if (categoria == null || !documento.podeComporConformidade()) continue;
            for (Integer dias : categoria.getFaixasAlertaDias()) {
                if (documento.getDataValidade().minusDays(dias).isAfter(data)) continue;
                if (alertaRepository.existsByIdDocumentoAndDiasAntecedenciaAndDataValidade(
                        documento.getId(), dias, documento.getDataValidade())) continue;
                String chave = documento.getId() + ":" + dias + ":" + documento.getDataValidade();
                novos.put(chave, new AlertaValidadeDocumentoSst(documento.getId(), dias,
                        documento.getDataValidade(), data));
            }
        }
        return alertaRepository.saveAll(novos.values()).stream().map(this::montarAlerta).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertaDocumentoSstResponse> listarAlertas(UUID idDocumento) {
        buscar(idDocumento);
        return alertaRepository.findAllByIdDocumentoAndSituacao(
                idDocumento, SituacaoAlertaDocumentoSst.ATIVO).stream()
                .map(this::montarAlerta).toList();
    }

    private DocumentoSstResponse persistir(DocumentoSstMetadataRequest request,
                                            ArquivoDocumentoSstService.ArquivoProcessado arquivo,
                                            UUID idAnterior, UUID ator) {
        validarParaEnvio(request, idAnterior, arquivo.metadados().sha256());
        CategoriaDocumentoSst categoria = categoria(request.idCategoria());
        DocumentoSst documento = new DocumentoSst(categoria, arquivo.metadados(), dados(request),
                ator, LocalDate.now(), idAnterior);
        DocumentoSst salvo = repository.saveAndFlush(documento);
        return montar(salvo, Map.of(categoria.getId(), categoria));
    }

    private void validarParaEnvio(DocumentoSstMetadataRequest request, UUID idAnterior, String sha256) {
        CategoriaDocumentoSst categoria = categoria(request.idCategoria());
        categoria.exigirDisponivelPara(request.contexto(), request.escopoTitular());
        validarOwnership(request);
        validarTreinamento(request);
        if (repository.existsByCategoriaCodigoAndIdBaseAndEscopoTitularAndIdTitularAndSha256AndAtivoTrueAndDataArquivamentoIsNull(
                categoria.getCodigo(), request.idBase(), request.escopoTitular(), request.idTitular(), sha256)) {
            throw new ConflitoGovernancaSstException(
                    "Este arquivo já foi registrado para o mesmo titular e categoria.");
        }
        if (idAnterior != null) {
            DocumentoSst anterior = buscar(idAnterior);
            if (!anterior.podeComporConformidade() || !anterior.isAtual()) {
                throw new ConflitoGovernancaSstException(
                        "Somente a versão atual e aprovada pode receber uma substituição.");
            }
            if (!Objects.equals(anterior.getIdBase(), request.idBase())
                    || anterior.getContexto() != request.contexto()
                    || anterior.getEscopoTitular() != request.escopoTitular()
                    || !Objects.equals(anterior.getIdTitular(), request.idTitular())
                    || !Objects.equals(anterior.getCategoriaCodigo(), categoria.getCodigo())) {
                throw new InvarianteVioladaException(
                        "A substituição deve preservar categoria, contexto, base e titular da versão anterior.");
            }
        }
    }

    private void validarOwnership(DocumentoSstMetadataRequest request) {
        if (!baseOperacionalApi.existeBase(request.idBase())) {
            throw new RecursoNaoEncontradoException("Base operacional não encontrada ou inativa.");
        }
        switch (request.escopoTitular()) {
            case BASE -> {
                if (!request.idBase().equals(request.idTitular())) {
                    throw new InvarianteVioladaException("A base informada deve ser a titular do documento.");
                }
            }
            case COLABORADOR -> validarColaborador(request);
            case CORPO -> {
                CorpoGovernancaSst corpo = corpoRepository.findByIdAndAtivoTrue(request.idTitular())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Corpo de governança não encontrado."));
                if (corpo.getTipo() != request.contexto() || !corpo.baseEstaAbrangida(request.idBase())) {
                    throw new InvarianteVioladaException("O corpo não pertence ao contexto e à base informados.");
                }
            }
            case MEMBRO -> {
                MembroCorpoSst membro = membroRepository.findById(request.idTitular())
                        .filter(MembroCorpoSst::isAtivo)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Integrante não encontrado."));
                if (membro.getCorpo().getTipo() != request.contexto()
                        || !membro.getCorpo().baseEstaAbrangida(request.idBase())) {
                    throw new InvarianteVioladaException("O integrante não pertence ao contexto e à base informados.");
                }
            }
            case REUNIAO -> {
                ReuniaoGovernancaSst reuniao = reuniaoRepository.findByIdAndAtivoTrue(request.idTitular())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião não encontrada."));
                if (reuniao.getTipoCorpo() != request.contexto()
                        || !reuniao.getIdBaseOperacional().equals(request.idBase())) {
                    throw new InvarianteVioladaException("A reunião não pertence ao contexto e à base informados.");
                }
            }
        }
    }

    private void validarColaborador(DocumentoSstMetadataRequest request) {
        if (!Objects.equals(request.idTitular(), request.idColaborador())) {
            throw new InvarianteVioladaException("O colaborador deve ser o titular do documento.");
        }
        ContextoOrganizacional contexto = colaboradorApi
                .buscarContextoOrganizacional(List.of(request.idColaborador()))
                .get(request.idColaborador());
        if (contexto == null || !contexto.ativo()) {
            throw new RecursoNaoEncontradoException("Colaborador não encontrado ou inativo no RH.");
        }
        if (!request.idBase().equals(contexto.idBaseOperacional())) {
            throw new InvarianteVioladaException("O colaborador não pertence à base informada.");
        }
    }

    private void validarTreinamento(DocumentoSstMetadataRequest request) {
        if (request.idTreinamento() == null) return;
        TreinamentoGovernancaSst treinamento = treinamentoRepository
                .findByIdAndAtivoTrue(request.idTreinamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Treinamento não encontrado."));
        if (!treinamento.aplicaA(request.contexto())) {
            throw new InvarianteVioladaException("O treinamento não pertence ao contexto informado.");
        }
    }

    private DadosDocumentoSst dados(DocumentoSstMetadataRequest request) {
        return new DadosDocumentoSst(request.idBase(), request.contexto(), request.escopoTitular(),
                request.idTitular(), request.idColaborador(), request.idTreinamento(),
                request.numeroDocumento(), request.emissor(), request.uf(),
                request.referenciaNormativa(), request.dataEmissao(), request.dataValidade(),
                request.dataConclusaoTreinamento(), request.cargaHorariaMinutos(),
                request.modalidade(), request.observacoes());
    }

    private DocumentoSst buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Documento não encontrado."));
    }

    private CategoriaDocumentoSst categoria(UUID id) {
        return categoriaRepository.findByIdAndAtivoTrue(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Categoria documental não encontrada ou inativa."));
    }

    private Map<UUID, CategoriaDocumentoSst> categorias(Collection<DocumentoSst> documentos) {
        List<UUID> ids = documentos.stream().map(DocumentoSst::getIdCategoria).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return categoriaRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(CategoriaDocumentoSst::getId, Function.identity()));
    }

    private DocumentoSstResponse montar(DocumentoSst documento,
                                         Map<UUID, CategoriaDocumentoSst> categorias) {
        CategoriaDocumentoSst categoria = categorias.get(documento.getIdCategoria());
        return new DocumentoSstResponse(
                documento.getId(), documento.getIdCategoria(), documento.getCategoriaCodigo(),
                categoria == null ? documento.getCategoriaCodigo() : categoria.getNome(),
                documento.getCategoriaRevisao(), documento.getIdBase(), documento.getContexto(),
                documento.getEscopoTitular(), documento.getIdTitular(), documento.getIdColaborador(),
                documento.getIdTreinamento(), documento.getNomeArquivo(), documento.getContentType(),
                documento.getTamanhoBytes(), documento.getSha256(), documento.getNumeroDocumento(),
                documento.getEmissor(), documento.getUf(), documento.getReferenciaNormativa(),
                documento.getDataEmissao(), documento.getDataValidade(),
                documento.getDataConclusaoTreinamento(), documento.getCargaHorariaMinutos(),
                documento.getModalidade(), documento.getSituacaoConferencia(),
                documento.getMotivoRejeicao(), documento.statusValidade(LocalDate.now()),
                documento.getIdDocumentoSubstituido(), documento.getIdDocumentoSubstituto(),
                documento.isAtual(), documento.estaArquivado(), documento.getDataRetencaoAte(),
                documento.getReferenciaRetencao(), documento.getDataCriacao(), documento.getVersao());
    }

    private AlertaDocumentoSstResponse montarAlerta(AlertaValidadeDocumentoSst alerta) {
        return new AlertaDocumentoSstResponse(alerta.getId(), alerta.getIdDocumento(),
                alerta.getDiasAntecedencia(), alerta.getDataValidade(), alerta.getDataAvaliacao(),
                alerta.getSituacao());
    }

    private void superarAlertas(UUID idDocumento) {
        List<AlertaValidadeDocumentoSst> ativos = alertaRepository
                .findAllByIdDocumentoAndSituacao(idDocumento, SituacaoAlertaDocumentoSst.ATIVO);
        ativos.forEach(AlertaValidadeDocumentoSst::superar);
        alertaRepository.saveAll(ativos);
    }

    private static UUID exigirAtor(UUID ator) {
        if (ator == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        }
        return ator;
    }
}
