package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.modules.sst.domain.*;
import com.nexo.app.modules.sst.application.mappers.TipoInspecaoMapper;
import com.nexo.app.modules.sst.application.services.GravadorEventoInspecaoService;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoInspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.INSPECOES_SEGURANCA;

/**
 * Orquestra o lançamento e a consulta de inspeções de segurança. O lançamento é
 * feito em uma única requisição (offline-first): valida o formulário e os recursos
 * externos, monta a inspeção com respostas e desvios, e a persiste já concluída.
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = INSPECOES_SEGURANCA)
public class InspecaoSegurancaUseCases {

    private final InspecaoSegurancaRepository repository;
    private final FormularioInspecaoRepository formularioRepository;
    private final TipoInspecaoRepository tipoInspecaoRepository;
    private final TipoInspecaoMapper tipoInspecaoMapper;
    private final EvidenciaInspecaoRepository evidenciaRepository;
    private final ProcessoNegocioApi processoApi;
    private final VeiculoApi veiculoApi;
    private final EquipeApi equipeApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;
    private final GravadorEventoInspecaoService gravadorEventoService;
    private final EventoInspecaoSegurancaRepository eventoRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public InspecaoSegurancaResponse registrar(RegistrarInspecaoRequest request, UUID idInspetor) {
        FormularioInspecao formulario = formularioRepository.findById(request.idFormulario())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Formulário de inspeção não encontrado com ID: " + request.idFormulario()));
        if (!formulario.isAtivo()) {
            throw new InvarianteVioladaException("O formulário selecionado está inativo.");
        }

        UUID idTipoInspecao;
        if (request.idTipoInspecao() != null) {
            UUID idSolicitado = request.idTipoInspecao();
            TipoInspecao tipo = tipoInspecaoRepository.findById(idSolicitado)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Tipo de inspeção não encontrado com ID: " + idSolicitado));
            if (!tipo.isAtivo()) {
                throw new InvarianteVioladaException("O tipo de inspeção selecionado está inativo.");
            }
            idTipoInspecao = idSolicitado;
        } else {
            TipoInspecao tipoCampo = tipoInspecaoRepository.findByNomeNormalizadoAndAtivoTrue("campo")
                    .or(() -> tipoInspecaoRepository.findByNomeNormalizado("campo"))
                    .orElseThrow(() -> new InvarianteVioladaException("Tipo de inspeção padrão 'Campo' não encontrado."));
            idTipoInspecao = tipoCampo.getId();
        }

        validarRecursosExternos(request);
        List<EvidenciaInspecao> evidencias = carregarEvidencias(request.evidencias(), idInspetor);

        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                request.idFormulario(), idTipoInspecao, request.idProcesso(), idInspetor,
                request.idVeiculo(), request.idEquipe(), request.idBaseOperacional(),
                request.dataRealizacao(), request.observacoes());
        inspecao.registrarGeolocalizacao(request.latitude(), request.longitude());

        request.colaboradoresInspecionados().forEach(inspecao::adicionarColaboradorInspecionado);

        Map<UUID, Pergunta> perguntasPorId = indexarPerguntas(formulario);
        processarRespostas(inspecao, request, perguntasPorId);
        validarObrigatorias(perguntasPorId, request);
        processarDesvios(inspecao, request, perguntasPorId);
        evidencias.forEach(inspecao::vincularEvidencia);

        inspecao.concluir();

        InspecaoSeguranca salva = repository.save(inspecao);
        gravadorEventoService.registrarInicial(salva, idInspetor);
        return hidratar(salva);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public InspecaoSegurancaResponse tratarDesvio(UUID idInspecao, UUID idDesvio, TratarDesvioRequest request) {
        InspecaoSeguranca inspecao = buscarEntidade(idInspecao);
        DesvioSeguranca desvio = inspecao.getDesvios().stream()
                .filter(d -> d.getId().equals(idDesvio))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Desvio não encontrado com ID: " + idDesvio));

        StatusDesvio statusAntigo = desvio.getStatus();
        String acaoAntiga = desvio.getAcaoCorretiva();
        java.time.LocalDate prazoAntigo = desvio.getPrazo();

        desvio.atualizarTratamento(request.status(), request.acaoCorretiva(), request.prazo());
        InspecaoSeguranca salva = repository.save(inspecao);
        gravadorEventoService.registrarTratamentoDesvio(salva, desvio, statusAntigo, acaoAntiga, prazoAntigo, SecurityUtils.getUsuarioLogadoId());
        return hidratar(salva);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public InspecaoSegurancaResponse atualizarCabecalho(UUID idInspecao, AtualizarCabecalhoInspecaoRequest request, UUID idUsuarioLogado) {
        InspecaoSeguranca inspecao = buscarEntidade(idInspecao);

        if (!inspecao.getIdInspetor().equals(idUsuarioLogado)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Apenas o responsável pela criação da inspeção pode editar seus dados.");
        }

        UUID idTipoInspecao = request.idTipoInspecao();
        TipoInspecao tipo = tipoInspecaoRepository.findById(idTipoInspecao)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Tipo de inspeção não encontrado com ID: " + idTipoInspecao));
        if (!tipo.isAtivo()) {
            throw new InvarianteVioladaException("O tipo de inspeção selecionado está inativo.");
        }

        if (!processoApi.existeProcesso(request.idProcesso())) {
            throw new RecursoNaoEncontradoException("Processo de negócio não encontrado com ID: " + request.idProcesso());
        }
        if (request.idVeiculo() != null && !veiculoApi.existeVeiculo(request.idVeiculo())) {
            throw new RecursoNaoEncontradoException("Veículo não encontrado com ID: " + request.idVeiculo());
        }
        if (request.idEquipe() != null && equipeApi.buscarPorIdResponse(request.idEquipe()).isEmpty()) {
            throw new RecursoNaoEncontradoException("Equipe não encontrada com ID: " + request.idEquipe());
        }
        if (!baseOperacionalApi.existeBase(request.idBaseOperacional())) {
            throw new RecursoNaoEncontradoException(
                    "Base operacional não encontrada com ID: " + request.idBaseOperacional());
        }
        for (UUID idColaborador : request.colaboradoresInspecionados()) {
            if (!colaboradorApi.existeColaborador(idColaborador)) {
                throw new RecursoNaoEncontradoException("Colaborador não encontrado com ID: " + idColaborador);
            }
        }

        var anterior = gravadorEventoService.capturarEstado(inspecao);

        inspecao.atualizarCabecalho(
                request.idTipoInspecao(), request.idProcesso(), request.idBaseOperacional(),
                request.idVeiculo(), request.idEquipe(), request.dataRealizacao(),
                request.observacoes(), request.latitude(), request.longitude(),
                request.colaboradoresInspecionados()
        );

        InspecaoSeguranca salva = repository.save(inspecao);
        gravadorEventoService.registrarEdicaoCabecalho(salva, anterior, idUsuarioLogado, request.justificativa());
        return hidratar(salva);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public InspecaoSegurancaResponse atualizarInspecao(UUID idInspecao, AtualizarInspecaoRequest request, UUID idUsuarioLogado) {
        InspecaoSeguranca inspecao = buscarEntidade(idInspecao);

        if (!inspecao.getIdInspetor().equals(idUsuarioLogado)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Apenas o responsável pela criação da inspeção pode editar seus dados.");
        }

        UUID idFormulario = request.idFormulario() != null ? request.idFormulario() : inspecao.getIdFormulario();
        FormularioInspecao formulario = formularioRepository.findById(idFormulario)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Formulário de inspeção não encontrado com ID: " + idFormulario));
        if (!formulario.isAtivo()) {
            throw new InvarianteVioladaException("O formulário selecionado está inativo.");
        }

        UUID idTipoInspecao = request.idTipoInspecao();
        TipoInspecao tipo = tipoInspecaoRepository.findById(idTipoInspecao)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Tipo de inspeção não encontrado com ID: " + idTipoInspecao));
        if (!tipo.isAtivo()) {
            throw new InvarianteVioladaException("O tipo de inspeção selecionado está inativo.");
        }

        if (!processoApi.existeProcesso(request.idProcesso())) {
            throw new RecursoNaoEncontradoException("Processo de negócio não encontrado com ID: " + request.idProcesso());
        }
        if (request.idVeiculo() != null && !veiculoApi.existeVeiculo(request.idVeiculo())) {
            throw new RecursoNaoEncontradoException("Veículo não encontrado com ID: " + request.idVeiculo());
        }
        if (request.idEquipe() != null && equipeApi.buscarPorIdResponse(request.idEquipe()).isEmpty()) {
            throw new RecursoNaoEncontradoException("Equipe não encontrada com ID: " + request.idEquipe());
        }
        if (!baseOperacionalApi.existeBase(request.idBaseOperacional())) {
            throw new RecursoNaoEncontradoException(
                    "Base operacional não encontrada com ID: " + request.idBaseOperacional());
        }
        for (UUID idColaborador : request.colaboradoresInspecionados()) {
            if (!colaboradorApi.existeColaborador(idColaborador)) {
                throw new RecursoNaoEncontradoException("Colaborador não encontrado com ID: " + idColaborador);
            }
        }

        Map<UUID, Pergunta> perguntasPorId = indexarPerguntas(formulario);

        // Validar respostas
        for (RegistrarInspecaoRequest.RespostaItem item : request.respostas()) {
            Pergunta pergunta = perguntasPorId.get(item.idPergunta());
            if (pergunta == null) {
                throw new InvarianteVioladaException("A pergunta " + item.idPergunta() + " não pertence ao formulário da inspeção.");
            }
            if (pergunta.getTipoResposta().permiteOpcoes()) {
                validarRespostaObjetiva(pergunta, item);
            } else if (!item.opcoesSelecionadas().isEmpty()) {
                throw new InvarianteVioladaException("A pergunta de resposta livre '" + pergunta.getEnunciado() + "' não admite opções selecionadas.");
            }
        }

        // Validar obrigatórias
        Set<UUID> respondidas = request.respostas().stream()
                .map(RegistrarInspecaoRequest.RespostaItem::idPergunta)
                .collect(Collectors.toSet());
        for (Pergunta pergunta : perguntasPorId.values()) {
            if (pergunta.isObrigatoria() && !respondidas.contains(pergunta.getId())) {
                throw new InvarianteVioladaException("A pergunta obrigatória '" + pergunta.getEnunciado() + "' não foi respondida.");
            }
        }

        // Validar desvios
        Map<String, OpcaoResposta> opcoesDesvio = new java.util.HashMap<>();
        for (RegistrarInspecaoRequest.DesvioItem item : request.desvios()) {
            Pergunta pergunta = perguntasPorId.get(item.idPergunta());
            if (pergunta == null) {
                throw new InvarianteVioladaException("A pergunta " + item.idPergunta() + " do desvio não pertence ao formulário da inspeção.");
            }
            if (item.idOpcaoResposta() == null) {
                throw new InvarianteVioladaException("O desvio deve referenciar a opção de resposta que o gerou.");
            }

            OpcaoResposta opcao = pergunta.getOpcoes().stream()
                    .filter(o -> o.getId().equals(item.idOpcaoResposta()))
                    .findFirst()
                    .orElseThrow(() -> new InvarianteVioladaException(
                            "A opção " + item.idOpcaoResposta() + " não pertence à pergunta '" + pergunta.getEnunciado() + "'."));

            if (!opcao.isGeraDesvio()) {
                throw new InvarianteVioladaException("A opção '" + opcao.getTexto() + "' não está configurada para gerar desvio.");
            }
            if (opcao.getGravidade() == null) {
                throw new InvarianteVioladaException("A opção '" + opcao.getTexto() + "' gera desvio mas não tem gravidade definida no formulário.");
            }
            opcoesDesvio.put(item.idPergunta() + ":" + item.idOpcaoResposta(), opcao);
        }

        // Validar e carregar evidências
        if (request.evidencias() == null || request.evidencias().isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve manter ao menos uma evidência fotográfica.");
        }
        List<EvidenciaInspecao> evidenciasAtualizadas = new ArrayList<>();
        Set<UUID> idsEvidenciasProcessadas = new java.util.HashSet<>();
        for (UUID idEvid : request.evidencias()) {
            if (!idsEvidenciasProcessadas.add(idEvid)) continue;

            EvidenciaInspecao evidenciaExistente = inspecao.getEvidencias().stream()
                    .filter(e -> e.getId().equals(idEvid))
                    .findFirst()
                    .orElse(null);

            if (evidenciaExistente != null) {
                evidenciasAtualizadas.add(evidenciaExistente);
            } else {
                EvidenciaInspecao pendente = evidenciaRepository.findById(idEvid)
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Evidência fotográfica não encontrada com ID: " + idEvid));
                if (!pendente.foiEnviadaPor(idUsuarioLogado)) {
                    throw new InvarianteVioladaException("A evidência " + idEvid + " foi enviada por outro usuário.");
                }
                if (pendente.estaVinculada() && (pendente.getInspecao() == null || !pendente.getInspecao().getId().equals(inspecao.getId()))) {
                    throw new InvarianteVioladaException("A evidência " + idEvid + " já foi utilizada em outra inspeção.");
                }
                evidenciasAtualizadas.add(pendente);
            }
        }

        // Capturar baseline completo anterior para auditoria
        var anterior = gravadorEventoService.capturarEstadoCompleto(inspecao);

        // Atualizar cabeçalho e colaboradores
        inspecao.atualizarCompleto(
                idFormulario, request.idTipoInspecao(), request.idProcesso(), request.idBaseOperacional(),
                request.idVeiculo(), request.idEquipe(), request.dataRealizacao(),
                request.observacoes(), request.latitude(), request.longitude(),
                request.colaboradoresInspecionados()
        );

        // Sincronizar respostas
        Set<UUID> perguntasAtuais = new java.util.HashSet<>();
        for (RegistrarInspecaoRequest.RespostaItem item : request.respostas()) {
            inspecao.sincronizarResposta(item.idPergunta(), item.opcoesSelecionadas(),
                    item.valorTexto(), item.valorNumero(), item.valorData());
            perguntasAtuais.add(item.idPergunta());
        }
        inspecao.removerRespostasNaoPresentes(perguntasAtuais);

        // Sincronizar desvios
        Set<String> chavesDesviosAtuais = new java.util.HashSet<>();
        for (RegistrarInspecaoRequest.DesvioItem item : request.desvios()) {
            String chave = item.idPergunta() + ":" + item.idOpcaoResposta();
            OpcaoResposta opcao = opcoesDesvio.get(chave);
            inspecao.sincronizarDesvio(
                    item.idPergunta(), item.idOpcaoResposta(), opcao.getGravidade(),
                    item.descricao(), item.acaoCorretiva(), item.prazo(),
                    item.colaboradores(), item.status()
            );
            chavesDesviosAtuais.add(chave);
        }
        inspecao.removerDesviosNaoPresentes(chavesDesviosAtuais);

        // Sincronizar evidências
        inspecao.sincronizarEvidencias(evidenciasAtualizadas);

        // Validação final de invariantes
        inspecao.validarInvariantesAposEdicao();

        InspecaoSeguranca salva = repository.saveAndFlush(inspecao);
        gravadorEventoService.registrarEdicaoCompleta(salva, anterior, idUsuarioLogado, request.justificativa());
        return hidratar(salva);
    }

    @Transactional(readOnly = true)
    public HistoricoInspecaoResponse obterHistorico(UUID idInspecao, Pageable pageable) {
        InspecaoSeguranca inspecao = buscarEntidade(idInspecao);
        org.springframework.data.domain.Page<EventoInspecaoSeguranca> pagina = eventoRepository.findAllByIdInspecao(idInspecao, pageable);
        List<EventoInspecaoSeguranca> todos = eventoRepository.findAllByIdInspecaoOrderBySequenciaAsc(idInspecao);

        if (todos.isEmpty()) {
            String autorNome = colaboradorApi.buscarNomeCompletoPorUsuario(inspecao.getIdInspetor())
                    .orElseGet(() -> usuarioApi.buscarNomePorId(inspecao.getIdInspetor()).orElse("Sistema"));
            Map<String, Object> snapshots = new LinkedHashMap<>();
            formularioRepository.findById(inspecao.getIdFormulario())
                    .ifPresent(f -> snapshots.put("formulario", f.getTitulo()));
            tipoInspecaoRepository.findById(inspecao.getIdTipoInspecao())
                    .ifPresent(t -> snapshots.put("tipoInspecao", t.getNome()));
            processoApi.buscarPorIdResponse(inspecao.getIdProcesso())
                    .ifPresent(p -> snapshots.put("processo", p.nome()));
            if (inspecao.getIdBaseOperacional() != null) {
                baseOperacionalApi.buscarPorIdResponse(inspecao.getIdBaseOperacional())
                        .ifPresent(b -> snapshots.put("baseOperacional", b.nome()));
            }
            if (inspecao.getIdVeiculo() != null) {
                veiculoApi.buscarPorIdResponse(inspecao.getIdVeiculo())
                        .ifPresent(v -> snapshots.put("veiculo", v.placa()));
            }
            if (inspecao.getIdEquipe() != null) {
                equipeApi.buscarPorIdResponse(inspecao.getIdEquipe())
                        .ifPresent(e -> snapshots.put("equipe", e.codigo()));
            }
            if (inspecao.getDataRealizacao() != null) {
                snapshots.put("dataRealizacao", inspecao.getDataRealizacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            }
            if (inspecao.getObservacoes() != null) {
                snapshots.put("observacoes", inspecao.getObservacoes());
            }
            snapshots.put("totalInspecionados", inspecao.getColaboradoresInspecionados().size());

            Map<String, Object> payloadMap = Map.of(
                    "comando", "REGISTRAR_INSPECAO",
                    "alteracoes", List.of(),
                    "snapshots", snapshots
            );

            var eventoSintetico = new HistoricoInspecaoResponse.Evento(
                    UUID.randomUUID(),
                    inspecao.getId(),
                    1L,
                    "REGISTRO_INICIAL",
                    new HistoricoInspecaoResponse.Autor(inspecao.getIdInspetor(), autorNome),
                    "UI",
                    null,
                    inspecao.getDataCriacao() != null ? inspecao.getDataCriacao() : inspecao.getDataRealizacao(),
                    payloadMap
            );

            return new HistoricoInspecaoResponse(
                    List.of(eventoSintetico),
                    new com.nexo.app.shared.application.dto.Paged(pageable.getPageSize(), 0, 1, 1),
                    List.of(new HistoricoInspecaoResponse.IntegridadeCadeia("INSPECAO", idInspecao, "INTEGRO", 1L, 1L))
            );
        }

        String statusIntegridade = gravadorEventoService.verificarIntegridade(todos);
        Long seqInicial = todos.isEmpty() ? 1L : todos.get(0).getSequencia();
        Long seqFinal = todos.isEmpty() ? 1L : todos.get(todos.size() - 1).getSequencia();

        List<HistoricoInspecaoResponse.Evento> eventos = pagina.getContent().stream()
                .map(this::converterEvento)
                .toList();

        return new HistoricoInspecaoResponse(
                eventos,
                new com.nexo.app.shared.application.dto.Paged(pagina.getSize(), pagina.getNumber(), pagina.getTotalElements(), pagina.getTotalPages()),
                List.of(new HistoricoInspecaoResponse.IntegridadeCadeia("INSPECAO", idInspecao, statusIntegridade, seqInicial, seqFinal))
        );
    }

    private HistoricoInspecaoResponse.Evento converterEvento(EventoInspecaoSeguranca e) {
        Object payloadObj;
        try {
            payloadObj = objectMapper.readValue(e.getPayloadCanonico(), Object.class);
        } catch (Exception ex) {
            payloadObj = Map.of();
        }
        return new HistoricoInspecaoResponse.Evento(
                e.getId(),
                e.getIdInspecao(),
                e.getSequencia(),
                e.getTipoEvento(),
                new HistoricoInspecaoResponse.Autor(e.getIdAutor(), e.getAutorSnapshot()),
                e.getOrigem(),
                e.getJustificativa(),
                e.getOcorridoEm(),
                payloadObj
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public InspecaoSegurancaResponse buscarPorId(UUID id) {
        return hidratar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<InspecaoSegurancaResponse> listar(InspecaoSegurancaFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(InspecaoSegurancaSpecification.filterBy(filter), pageable)
                        .map(this::hidratar)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString(), #idInspetor}")
    public PageResponse<InspecaoSegurancaResponse> listarPorInspetor(InspecaoSegurancaFilter filter, Pageable pageable, UUID idInspetor) {
        return listar(filter.restritoAoInspetor(idInspetor), pageable);
    }

    // --------------------------------------------------------------------- //
    //  Validações
    // --------------------------------------------------------------------- //

    private void validarRecursosExternos(RegistrarInspecaoRequest request) {
        if (!processoApi.existeProcesso(request.idProcesso())) {
            throw new RecursoNaoEncontradoException("Processo de negócio não encontrado com ID: " + request.idProcesso());
        }
        if (request.idVeiculo() != null && !veiculoApi.existeVeiculo(request.idVeiculo())) {
            throw new RecursoNaoEncontradoException("Veículo não encontrado com ID: " + request.idVeiculo());
        }
        if (request.idEquipe() != null && equipeApi.buscarPorIdResponse(request.idEquipe()).isEmpty()) {
            throw new RecursoNaoEncontradoException("Equipe não encontrada com ID: " + request.idEquipe());
        }
        if (!baseOperacionalApi.existeBase(request.idBaseOperacional())) {
            throw new RecursoNaoEncontradoException(
                    "Base operacional não encontrada com ID: " + request.idBaseOperacional());
        }
        for (UUID idColaborador : request.colaboradoresInspecionados()) {
            if (!colaboradorApi.existeColaborador(idColaborador)) {
                throw new RecursoNaoEncontradoException("Colaborador não encontrado com ID: " + idColaborador);
            }
        }
    }

    /**
     * Resgata as fotos que o inspetor já subiu e que ainda não pertencem a nenhuma
     * inspeção. O binário vem antes do lançamento para que cada foto suba isolada, com
     * retentativa própria; aqui a posse é conferida para que uma evidência não comprove
     * a inspeção de outra pessoa nem seja reciclada em duas inspeções.
     */
    private List<EvidenciaInspecao> carregarEvidencias(List<UUID> ids, UUID idInspetor) {
        List<EvidenciaInspecao> evidencias = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            EvidenciaInspecao evidencia = evidenciaRepository.findById(id)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Evidência fotográfica não encontrada com ID: " + id));
            if (!evidencia.foiEnviadaPor(idInspetor)) {
                throw new InvarianteVioladaException("A evidência " + id + " foi enviada por outro usuário.");
            }
            if (evidencia.estaVinculada()) {
                throw new InvarianteVioladaException("A evidência " + id + " já foi utilizada em outra inspeção.");
            }
            evidencias.add(evidencia);
        }
        return evidencias;
    }

    private void processarRespostas(InspecaoSeguranca inspecao, RegistrarInspecaoRequest request,
                                    Map<UUID, Pergunta> perguntasPorId) {
        for (RegistrarInspecaoRequest.RespostaItem item : request.respostas()) {
            Pergunta pergunta = perguntasPorId.get(item.idPergunta());
            if (pergunta == null) {
                throw new InvarianteVioladaException("A pergunta " + item.idPergunta() + " não pertence ao formulário da inspeção.");
            }

            if (pergunta.getTipoResposta().permiteOpcoes()) {
                validarRespostaObjetiva(pergunta, item);
            } else if (!item.opcoesSelecionadas().isEmpty()) {
                throw new InvarianteVioladaException("A pergunta de resposta livre '" + pergunta.getEnunciado() + "' não admite opções selecionadas.");
            }

            inspecao.responder(item.idPergunta(), item.opcoesSelecionadas(),
                    item.valorTexto(), item.valorNumero(), item.valorData());
        }
    }

    private void validarRespostaObjetiva(Pergunta pergunta, RegistrarInspecaoRequest.RespostaItem item) {
        if (item.opcoesSelecionadas().isEmpty()) {
            throw new InvarianteVioladaException("A pergunta objetiva '" + pergunta.getEnunciado() + "' exige a seleção de ao menos uma opção.");
        }
        if (pergunta.getTipoResposta() == TipoResposta.MULTIPLA_ESCOLHA && item.opcoesSelecionadas().size() > 1) {
            throw new InvarianteVioladaException("A pergunta '" + pergunta.getEnunciado() + "' admite apenas uma opção selecionada.");
        }
        Set<UUID> idsValidos = pergunta.getOpcoes().stream().map(OpcaoResposta::getId).collect(Collectors.toSet());
        for (UUID idOpcao : item.opcoesSelecionadas()) {
            if (!idsValidos.contains(idOpcao)) {
                throw new InvarianteVioladaException("A opção " + idOpcao + " não pertence à pergunta '" + pergunta.getEnunciado() + "'.");
            }
        }
    }

    private void validarObrigatorias(Map<UUID, Pergunta> perguntasPorId, RegistrarInspecaoRequest request) {
        Set<UUID> respondidas = request.respostas().stream()
                .map(RegistrarInspecaoRequest.RespostaItem::idPergunta)
                .collect(Collectors.toSet());

        for (Pergunta pergunta : perguntasPorId.values()) {
            if (pergunta.isObrigatoria() && !respondidas.contains(pergunta.getId())) {
                throw new InvarianteVioladaException("A pergunta obrigatória '" + pergunta.getEnunciado() + "' não foi respondida.");
            }
        }
    }

    private void processarDesvios(InspecaoSeguranca inspecao, RegistrarInspecaoRequest request,
                                  Map<UUID, Pergunta> perguntasPorId) {
        for (RegistrarInspecaoRequest.DesvioItem item : request.desvios()) {
            Pergunta pergunta = perguntasPorId.get(item.idPergunta());
            if (pergunta == null) {
                throw new InvarianteVioladaException("A pergunta " + item.idPergunta() + " do desvio não pertence ao formulário da inspeção.");
            }

            if (item.idOpcaoResposta() == null) {
                throw new InvarianteVioladaException("O desvio deve referenciar a opção de resposta que o gerou.");
            }

            OpcaoResposta opcao = pergunta.getOpcoes().stream()
                    .filter(o -> o.getId().equals(item.idOpcaoResposta()))
                    .findFirst()
                    .orElseThrow(() -> new InvarianteVioladaException(
                            "A opção " + item.idOpcaoResposta() + " não pertence à pergunta '" + pergunta.getEnunciado() + "'."));

            if (!opcao.isGeraDesvio()) {
                throw new InvarianteVioladaException("A opção '" + opcao.getTexto() + "' não está configurada para gerar desvio.");
            }

            if (opcao.getGravidade() == null) {
                throw new InvarianteVioladaException("A opção '" + opcao.getTexto() + "' gera desvio mas não tem gravidade definida no formulário.");
            }

            inspecao.registrarDesvio(item.idPergunta(), item.idOpcaoResposta(), opcao.getGravidade(),
                    item.descricao(), item.acaoCorretiva(), item.prazo(), item.colaboradores(), item.status());
        }
    }

    private Map<UUID, Pergunta> indexarPerguntas(FormularioInspecao formulario) {
        return formulario.getGrupos().stream()
                .flatMap(g -> g.getPerguntas().stream())
                .collect(Collectors.toMap(Pergunta::getId, Function.identity()));
    }

    // --------------------------------------------------------------------- //
    //  Hidratação da resposta
    // --------------------------------------------------------------------- //

    private InspecaoSegurancaResponse hidratar(InspecaoSeguranca inspecao) {
        FormularioInspecaoResumoResponse formulario = formularioRepository.findById(inspecao.getIdFormulario())
                .map(f -> new FormularioInspecaoResumoResponse(
                        f.getId(), f.getTitulo(), f.getDescricao(), f.isAtivo(),
                        f.getGrupos().stream().mapToInt(g -> g.getPerguntas().size()).sum()))
                .orElse(null);

        TipoInspecaoResponse tipoInspecao = inspecao.getIdTipoInspecao() == null ? null
                : tipoInspecaoRepository.findById(inspecao.getIdTipoInspecao())
                        .map(tipoInspecaoMapper::toResponse)
                        .orElse(null);

        var processo = inspecao.getIdProcesso() == null ? null
                : processoApi.buscarPorIdResponse(inspecao.getIdProcesso()).orElse(null);
        var processoInspetor = colaboradorApi.buscarPorUsuarioResponse(inspecao.getIdInspetor())
                .or(() -> colaboradorApi.buscarPorIdResponse(inspecao.getIdInspetor()))
                .map(com.nexo.app.modules.rh.ColaboradorResponse::processo)
                .orElse(null);
        var veiculo = inspecao.getIdVeiculo() == null ? null
                : veiculoApi.buscarPorIdResponse(inspecao.getIdVeiculo()).orElse(null);
        var equipe = inspecao.getIdEquipe() == null ? null
                : equipeApi.buscarPorIdResponse(inspecao.getIdEquipe()).orElse(null);
        var baseOperacional = inspecao.getIdBaseOperacional() == null ? null
                : baseOperacionalApi.buscarPorIdResponse(inspecao.getIdBaseOperacional()).orElse(null);

        String nomeInspetor = colaboradorApi.buscarNomeCompletoPorUsuario(inspecao.getIdInspetor())
                .orElseGet(() -> usuarioApi.buscarNomePorId(inspecao.getIdInspetor()).orElse(null));

        List<com.nexo.app.modules.rh.ColaboradorResponse> colaboradores = inspecao.getColaboradoresInspecionados().stream()
                .map(c -> colaboradorApi.buscarPorIdResponse(c.getIdColaborador()).orElse(null))
                .toList();

        List<RespostaInspecaoResponse> respostas = inspecao.getRespostas().stream()
                .map(r -> new RespostaInspecaoResponse(
                        r.getId(), r.getIdPergunta(),
                        r.getOpcoesSelecionadas().stream().map(RespostaOpcaoSelecionada::getIdOpcaoResposta).toList(),
                        r.getValorTexto(), r.getValorNumero(), r.getValorData()))
                .toList();

        List<DesvioSegurancaResponse> desvios = inspecao.getDesvios().stream()
                .map(d -> new DesvioSegurancaResponse(
                        d.getId(), d.getIdPergunta(), d.getIdOpcaoResposta(), d.getGravidade(),
                        d.getDescricao(), d.getAcaoCorretiva(), d.getPrazo(), d.getStatus(),
                        d.getDataResolucao(),
                        d.getColaboradores().stream()
                                .map(dc -> colaboradorApi.buscarPorIdResponse(dc.getIdColaborador()).orElse(null))
                                .toList(),
                        d.getAnexos().stream()
                                .map(a -> new AnexoDesvioResponse(
                                        a.getId(), a.getContexto(), a.getTipo(), a.getNomeArquivo(),
                                        a.getContentType(), a.getTamanhoBytes(), a.getDataCriacao()))
                                .toList()))
                .toList();

        List<EvidenciaInspecaoResponse> evidencias = inspecao.getEvidencias().stream()
                .map(e -> new EvidenciaInspecaoResponse(
                        e.getId(), e.getNomeArquivo(), e.getContentType(), e.getTamanhoBytes(), e.getDataCriacao()))
                .toList();

        return new InspecaoSegurancaResponse(
                inspecao.getId(), formulario, tipoInspecao, processo, processoInspetor, inspecao.getIdInspetor(), nomeInspetor,
                veiculo, equipe, baseOperacional, inspecao.getDataRealizacao(), inspecao.getStatus(),
                inspecao.getObservacoes(), inspecao.getLatitude(), inspecao.getLongitude(),
                colaboradores, respostas, desvios, evidencias);
    }

    private InspecaoSeguranca buscarEntidade(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inspeção de segurança não encontrada com ID: " + id));
    }
}
