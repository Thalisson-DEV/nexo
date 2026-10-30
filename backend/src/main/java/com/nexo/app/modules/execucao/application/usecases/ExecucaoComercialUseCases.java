package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.NotaResumida;
import com.nexo.app.modules.execucao.application.dto.*;
import com.nexo.app.modules.execucao.application.mappers.ExecucaoComercialMapper;
import com.nexo.app.modules.execucao.domain.ExecucaoAcao;
import com.nexo.app.modules.execucao.domain.AlteracaoLancamentos;
import com.nexo.app.modules.execucao.domain.DadosNota;
import com.nexo.app.modules.execucao.domain.DetalhesEventoNota;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.ExecucaoMaterial;
import com.nexo.app.modules.execucao.domain.MaterialLancado;
import com.nexo.app.modules.execucao.domain.OrigemAlteracaoNota;
import com.nexo.app.modules.execucao.domain.OrigemMaterial;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.modules.execucao.domain.Turno;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoSpecification;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.ExecucaoAlteradaEvent;
import com.nexo.app.shared.domain.events.ExecucaoConcluidaEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static com.nexo.app.shared.infrastructure.config.CacheNames.EXECUCOES;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = EXECUCOES)
public class ExecucaoComercialUseCases implements ExecucaoComercialApi {

    private final ExecucaoComercialRepository repository;
    private final TurnoRepository turnoRepository;
    private final ExecucaoComercialMapper mapper;
    private final EquipeApi equipeApi;
    private final MunicipioApi municipioApi;
    private final ContratoApi contratoApi;
    private final EstoqueApi estoqueApi;
    private final TipoServicoUseCases tipoServicoUseCases;
    private final ApplicationEventPublisher eventPublisher;
    private final CorteDiarioDoTurno corteDiario;
    private final HistoricoNotaService historico;

    private static final ZoneId FUSO = ZoneId.of("America/Bahia");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Depois disso, só o escritório altera a nota. */
    static final Duration PRAZO_ALTERACAO_CAMPO = Duration.ofDays(7);
    /**
     * Lança uma execução de campo completa em uma única operação: metadados,
     * ações e materiais. A execução é persistida já no status CONCLUIDO.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public ExecucaoComercialResponse registrar(RegistrarExecucaoRequest request, UUID idUsuarioOperador) {
        Turno turno = turnoDoLancamento(request);

        // Sem tipo só chega o que estava na fila offline de versões antigas do app.
        if (request.idTipoServico() != null) {
            tipoServicoUseCases.validarParaLancamento(request.idTipoServico());
        }

        ExecucaoComercial execucao = new ExecucaoComercial(
                request.referenciaNota(),
                request.idEquipe(),
                turno.getId(),
                request.idMunicipio(),
                request.dataLancamento(),
                request.latitude(),
                request.longitude(),
                request.idTipoServico(),
                request.tipoNota()
        );


        for (UUID idAcao : request.acoes()) {
            if (!contratoApi.existeAcao(idAcao)) {
                throw new RecursoNaoEncontradoException("Ação não encontrada com o ID: " + idAcao);
            }
            execucao.adicionarAcao(idAcao);
        }

        for (RegistrarExecucaoRequest.MaterialItem material : request.materiais()) {
            // Material da Concessionária é só registro: não passa pelas regras do saldo da equipe.
            if (material.origem() == OrigemMaterial.EQUIPE) {
                estoqueApi.validarDisponibilidadeMaterialEquipe(
                        material.idMaterial(),
                        material.numSerie(),
                        request.idEquipe(),
                        material.operacao()
                );
            } else if (estoqueApi.buscarMaterialPorIdResponse(material.idMaterial()).isEmpty()) {
                throw new RecursoNaoEncontradoException("Material não encontrado: " + material.idMaterial());
            }
            execucao.adicionarMaterial(
                    material.idMaterial(),
                    material.quantidade(),
                    material.operacao(),
                    material.numSerie(),
                    material.origem()
            );
        }

        BigDecimal valorTotal = calcularValorFaturado(execucao, request.dataLancamento());
        execucao.concluir(valorTotal);

        repository.save(execucao);
        historico.registrarLancamento(execucao, idUsuarioOperador, origemDo(idUsuarioOperador));

        // Publica evento para Auditoria/Movimentação de Estoque
        publicarEventoConclusao(execucao, idUsuarioOperador);

        return hidratar(execucao);
    }

    /**
     * Troca o número provisório da nota avulsa pelo número oficial. Faturamento,
     * materiais e turno não mudam: só a identificação da nota.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public ExecucaoComercialResponse informarNumeroOficial(UUID id, InformarNumeroOficialRequest request,
                                                           UUID idUsuario) {
        ExecucaoComercial execucao = buscarEntidade(id);
        String anterior = execucao.getReferenciaNota();
        execucao.informarNumeroOficial(request.referenciaNota(), idUsuario, OffsetDateTime.now());
        repository.save(execucao);
        historico.registrarNumeroOficial(execucao, anterior, idUsuario);
        return hidratar(execucao);
    }

    /**
     * Corrige uma nota já sincronizada. A equipe da nota altera pelo app até
     * {@link #PRAZO_ALTERACAO_CAMPO} depois do lançamento, e só serviços, materiais e o
     * número; o escritório altera a qualquer tempo, e também o tipo da nota, o tipo de
     * serviço, o município e a data do lançamento. A nota segue CONCLUIDO, com o valor
     * recalculado pela tabela vigente na data do lançamento; a diferença vai para a
     * linha do tempo e o estoque estorna o que saiu e baixa o que entrou.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public ExecucaoComercialResponse alterar(UUID id, AlterarExecucaoRequest request, UUID idUsuario) {
        // Reenvio da fila do aparelho: a alteração já foi aplicada.
        Optional<UUID> jaAplicada = historico.execucaoDaOperacao(request.idOperacao());
        if (jaAplicada.isPresent()) {
            if (!jaAplicada.get().equals(id)) {
                throw new InvarianteVioladaException("Esta alteração já foi registrada em outra nota.");
            }
            return hidratar(buscarEntidade(id));
        }

        ExecucaoComercial execucao = buscarEntidade(id);
        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime alteradoEm = request.alteradoEm() == null || request.alteradoEm().isAfter(agora)
                ? agora
                : request.alteradoEm();
        OrigemAlteracaoNota origem = validarQuemAltera(execucao, idUsuario, alteradoEm);
        DadosNota dadosAntes = execucao.dados();
        DadosNota dadosNovos = request.dados();
        if (origem == OrigemAlteracaoNota.CAMPO && mudaDadosDoEscritorio(dadosAntes, dadosNovos)) {
            throw new InvarianteVioladaException("Pelo app dá para corrigir serviços, materiais e o número da nota. "
                    + "Tipo, serviço, município e data são com o escritório.");
        }
        if (dadosNovos.idTipoServico() != null && !dadosNovos.idTipoServico().equals(dadosAntes.idTipoServico())) {
            tipoServicoUseCases.validarParaLancamento(dadosNovos.idTipoServico());
        }
        if (dadosNovos.idMunicipio() != null && !dadosNovos.idMunicipio().equals(dadosAntes.idMunicipio())
                && municipioApi.buscarPorIdResponse(dadosNovos.idMunicipio()).isEmpty()) {
            throw new RecursoNaoEncontradoException("Município não encontrado: " + dadosNovos.idMunicipio());
        }

        for (UUID idAcao : request.acoes()) {
            if (!contratoApi.existeAcao(idAcao)) {
                throw new RecursoNaoEncontradoException("Ação não encontrada com o ID: " + idAcao);
            }
        }
        List<MaterialLancado> materiais = request.materiais().stream()
                .map(m -> new MaterialLancado(m.idMaterial(), m.quantidade(), m.operacao(), m.numSerie(), m.origem()))
                .toList();

        BigDecimal valorAnterior = execucao.getValorTotalFaturado();
        // O tipo muda antes dos materiais: material da Concessionária só entra em ocorrência.
        boolean dadosMudaram = execucao.corrigirDados(dadosNovos, idUsuario, agora);
        AlteracaoLancamentos alteracao = execucao.aplicarLancamentos(request.acoes(), materiais);
        if (!dadosMudaram && alteracao.isVazia()) {
            throw new InvarianteVioladaException("Nada mudou na nota.");
        }
        execucao.validarMateriaisDoTipo();
        for (MaterialLancado entrou : alteracao.materiaisAdicionados()) {
            if (entrou.origem() == OrigemMaterial.EQUIPE) {
                estoqueApi.validarDisponibilidadeMaterialEquipe(entrou.idMaterial(), entrou.numSerie(),
                        execucao.getIdEquipe(), entrou.operacao());
            } else if (estoqueApi.buscarMaterialPorIdResponse(entrou.idMaterial()).isEmpty()) {
                throw new RecursoNaoEncontradoException("Material não encontrado: " + entrou.idMaterial());
            }
        }
        execucao.registrarAlteracao(calcularValorFaturado(execucao, execucao.getDataLancamento()), idUsuario, alteradoEm);
        repository.save(execucao);

        historico.registrarAlteracao(execucao, alteracao, dadosAlterados(dadosAntes, execucao.dados()), request.motivo(),
                valorAnterior, idUsuario, origem, request.idOperacao(), alteradoEm);
        publicarEventoAlteracao(execucao, alteracao, idUsuario);
        return hidratar(execucao);
    }

    private static boolean mudaDadosDoEscritorio(DadosNota antes, DadosNota novos) {
        return (novos.tipoNota() != null && novos.tipoNota() != antes.tipoNota())
                || (novos.idTipoServico() != null && !novos.idTipoServico().equals(antes.idTipoServico()))
                || (novos.idMunicipio() != null && !novos.idMunicipio().equals(antes.idMunicipio()))
                || (novos.dataLancamento() != null && !novos.dataLancamento().isEqual(antes.dataLancamento()));
    }

    /** O que mudou na identificação da nota, em texto, para a linha do tempo. */
    private List<DetalhesEventoNota.DadoAlterado> dadosAlterados(DadosNota antes, DadosNota depois) {
        List<DetalhesEventoNota.DadoAlterado> dados = new ArrayList<>();
        if (!Objects.equals(antes.referenciaNota(), depois.referenciaNota())) {
            dados.add(new DetalhesEventoNota.DadoAlterado("Número da nota", antes.referenciaNota(), depois.referenciaNota()));
        }
        if (antes.tipoNota() != depois.tipoNota()) {
            dados.add(new DetalhesEventoNota.DadoAlterado("Tipo da nota", rotulo(antes.tipoNota()), rotulo(depois.tipoNota())));
        }
        if (!Objects.equals(antes.idTipoServico(), depois.idTipoServico())) {
            dados.add(new DetalhesEventoNota.DadoAlterado("Tipo de serviço", tipoServico(antes.idTipoServico()),
                    tipoServico(depois.idTipoServico())));
        }
        if (!Objects.equals(antes.idMunicipio(), depois.idMunicipio())) {
            dados.add(new DetalhesEventoNota.DadoAlterado("Município", municipio(antes.idMunicipio()),
                    municipio(depois.idMunicipio())));
        }
        if (!antes.dataLancamento().isEqual(depois.dataLancamento())) {
            dados.add(new DetalhesEventoNota.DadoAlterado("Data do lançamento",
                    DATA_HORA.format(antes.dataLancamento().atZoneSameInstant(FUSO)),
                    DATA_HORA.format(depois.dataLancamento().atZoneSameInstant(FUSO))));
        }
        return dados;
    }

    private static String rotulo(TipoNota tipo) {
        return switch (tipo) {
            case COMUM -> "Comum";
            case AVULSA -> "Avulsa";
            case OCORRENCIA -> "Ocorrência comercial";
        };
    }

    private String tipoServico(UUID id) {
        if (id == null) return null;
        try {
            var tipo = tipoServicoUseCases.buscarPorId(id);
            return tipo.codigo() == null ? tipo.descricao() : tipo.codigo() + " · " + tipo.descricao();
        } catch (RecursoNaoEncontradoException e) {
            return "Tipo removido";
        }
    }

    private String municipio(UUID id) {
        return id == null ? null : municipioApi.buscarPorIdResponse(id).map(m -> m.nome()).orElse("Município removido");
    }

    @Transactional(readOnly = true)
    public List<EventoHistoricoNotaResponse> historico(UUID id) {
        ExecucaoComercial execucao = buscarEntidade(id);
        String equipe = equipeApi.buscarPorIdResponse(execucao.getIdEquipe()).map(e -> e.codigo()).orElse(null);
        return historico.listar(execucao, equipe);
    }

    /**
     * Quem tem equipe é campo: só altera nota da própria equipe e dentro do prazo.
     * Quem não tem equipe é escritório.
     */
    private OrigemAlteracaoNota validarQuemAltera(ExecucaoComercial execucao, UUID idUsuario, OffsetDateTime alteradoEm) {
        Optional<UUID> equipeDoUsuario = idUsuario == null ? Optional.empty() : equipeApi.buscarIdEquipePorUsuario(idUsuario);
        if (equipeDoUsuario.isEmpty()) {
            return OrigemAlteracaoNota.ESCRITORIO;
        }
        if (!equipeDoUsuario.get().equals(execucao.getIdEquipe())) {
            throw new AccessDeniedException("Só a equipe que lançou a nota pode alterá-la pelo app.");
        }
        if (alteradoEm.isAfter(execucao.getDataLancamento().plus(PRAZO_ALTERACAO_CAMPO))) {
            throw new InvarianteVioladaException("Notas lançadas há mais de " + PRAZO_ALTERACAO_CAMPO.toDays()
                    + " dias só podem ser alteradas pelo escritório.");
        }
        return OrigemAlteracaoNota.CAMPO;
    }

    private OrigemAlteracaoNota origemDo(UUID idUsuario) {
        return idUsuario != null && equipeApi.buscarIdEquipePorUsuario(idUsuario).isPresent()
                ? OrigemAlteracaoNota.CAMPO
                : OrigemAlteracaoNota.ESCRITORIO;
    }

    private BigDecimal calcularValorFaturado(ExecucaoComercial execucao, OffsetDateTime dataLancamento) {
        if (execucao.getAcoes().isEmpty()) {
            return BigDecimal.ZERO;
        }

        UUID idContrato = equipeApi.buscarContratoDaEquipe(execucao.getIdEquipe())
                .orElseThrow(() -> new InvarianteVioladaException(
                        "Equipe " + execucao.getIdEquipe() + " não possui contrato vinculado. Vincule o contrato antes de lançar a execução."));

        List<UUID> idAcoes = execucao.getAcoes().stream()
                .map(ExecucaoAcao::getIdAcao)
                .toList();

        return contratoApi.calcularValorFaturado(idAcoes, idContrato, dataLancamento.toLocalDate());
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public ExecucaoComercialResponse buscarPorId(UUID id) {
        return hidratar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<ExecucaoComercialResponse> listar(ExecucaoFilter filter, Pageable pageable) {
        Page<ExecucaoComercial> pagina = repository.findAll(ExecucaoSpecification.filterBy(filter), pageable);
        return PageResponse.fromPage(
                new PageImpl<>(hidratar(pagina.getContent()), pagina.getPageable(), pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString(), #idUsuarioLogado}")
    public PageResponse<ExecucaoComercialResponse> listarPorUsuarioLogado(ExecucaoFilter filter, Pageable pageable, UUID idUsuarioLogado) {
        UUID idEquipe = equipeApi.buscarIdEquipePorUsuario(idUsuarioLogado)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada para o usuário logado."));

        // Cria um novo filtro incluindo o ID da equipe identificada
        ExecucaoFilter filtroComEquipe = new ExecucaoFilter(
                filter.referenciaNota(),
                filter.referencia(),
                idEquipe,
                filter.idTurno(),
                filter.idMunicipio(),
                filter.dataLancamentoDe(),
                filter.dataLancamentoAte(),
                filter.apenasHoje(),
                filter.status(),
                filter.idAcao(),
                filter.idMaterial(),
                filter.operacaoMaterial(),
                filter.numSerieMaterial(),
                filter.idTipoServico(),
                filter.semTipoServico(),
                filter.buscaGlobal(),
                filter.tipoNota(),
                filter.aguardandoNumeroOficial(),
                filter.alterada()
        );

        return listar(filtroComEquipe, pageable);
    }

    private ExecucaoComercialResponse hidratar(ExecucaoComercial entity) {
        return hidratar(List.of(entity)).getFirst();
    }

    /**
     * Monta as respostas de várias notas buscando de uma vez as equipes, ações, materiais e tipos
     * de serviço que elas citam. Montada nota a nota, uma página de 20 notas passava de 200 consultas.
     */
    private List<ExecucaoComercialResponse> hidratar(List<ExecucaoComercial> entidades) {
        if (entidades.isEmpty()) {
            return List.of();
        }
        Map<UUID, TipoServicoResponse> tipos = entidades.stream().anyMatch(e -> e.getIdTipoServico() != null)
                ? porId(tipoServicoUseCases.listarTodos(), TipoServicoResponse::id)
                : Map.of();
        Map<UUID, EquipeResponse> equipes = porId(equipeApi.buscarPorIdsResponse(
                entidades.stream().map(ExecucaoComercial::getIdEquipe).filter(Objects::nonNull).distinct().toList()),
                EquipeResponse::id);
        Map<UUID, AcaoResponse> acoes = porId(contratoApi.buscarAcoesPorIdsResponse(
                entidades.stream().flatMap(e -> e.getAcoes().stream()).map(ExecucaoAcao::getIdAcao).distinct().toList()),
                AcaoResponse::id);
        Map<UUID, MaterialResponse> materiais = porId(estoqueApi.buscarMateriaisPorIdsResponse(
                entidades.stream().flatMap(e -> e.getMateriais().stream()).map(ExecucaoMaterial::getIdMaterial).distinct().toList()),
                MaterialResponse::id);

        return entidades.stream()
                .map(e -> hidratar(e, tipos, equipes, acoes, materiais))
                .toList();
    }

    private static <T> Map<UUID, T> porId(List<T> itens, Function<T, UUID> id) {
        Map<UUID, T> porId = new HashMap<>();
        itens.forEach(item -> porId.putIfAbsent(id.apply(item), item));
        return porId;
    }

    private ExecucaoComercialResponse hidratar(ExecucaoComercial entity, Map<UUID, TipoServicoResponse> tipos,
                                               Map<UUID, EquipeResponse> equipes, Map<UUID, AcaoResponse> acoes,
                                               Map<UUID, MaterialResponse> materiais) {
        var response = mapper.toResponse(entity);
        var tipoServico = entity.getIdTipoServico() == null ? null : tipos.get(entity.getIdTipoServico());
        var equipe = equipes.get(entity.getIdEquipe());
        var municipio = entity.getIdMunicipio() == null
                ? null
                : municipioApi.buscarPorIdResponse(entity.getIdMunicipio()).orElse(null);

        var acoesHidratadas = entity.getAcoes().stream()
                .map(a -> new ExecucaoAcaoResponse(a.getId(), acoes.get(a.getIdAcao())))
                .toList();

        var materiaisHidratados = entity.getMateriais().stream()
                .map(m -> new ExecucaoMaterialResponse(
                        m.getId(),
                        materiais.get(m.getIdMaterial()),
                        m.getQuantidade(),
                        m.getOperacao(),
                        m.getNumSerie(),
                        m.getOrigem()
                )).toList();

        return new ExecucaoComercialResponse(
                response.id(),
                response.referenciaNota(),
                entity.getIdTurno(),
                tipoServico,
                equipe,
                municipio,
                response.latitude(),
                response.longitude(),
                response.dataLancamento(),
                response.statusExecucao(),
                response.valorTotalFaturado(),
                response.idUsuarioAlteracao(),
                response.dataAlteracao(),
                acoesHidratadas,
                materiaisHidratados,
                entity.getTipoNota(),
                entity.getReferenciaProvisoria(),
                entity.getDataNumeroOficial(),
                entity.getTotalAlteracoes()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecucaoComercialResponse> listarPorTurno(UUID idTurno) {
        ExecucaoFilter filter = new ExecucaoFilter(
                null, null, null, idTurno, null,
                null, null, false, null, null, null, null, null, null, null, null, null, null, null
        );
        return hidratar(repository.findAll(ExecucaoSpecification.filterBy(filter)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LancamentoResumido> listarLancamentosResumidos(OffsetDateTime inicio, OffsetDateTime fim,
                                                              Collection<UUID> idsEquipes) {
        if (idsEquipes == null || idsEquipes.isEmpty()) {
            return List.of();
        }
        return repository.listarLancamentosResumidos(inicio, fim, idsEquipes, StatusExecucao.CONCLUIDO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExecucaoComercialResponse> listarConcluidasDasEquipes(Collection<UUID> idsEquipes, OffsetDateTime inicio,
                                                                     OffsetDateTime fim, UUID idTipoServico,
                                                                     boolean semTipoServico, Pageable pageable) {
        if (idsEquipes == null || idsEquipes.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<ExecucaoComercial> spec = (root, query, cb) -> {
            var predicados = new ArrayList<Predicate>();
            predicados.add(cb.isTrue(root.get("ativo")));
            predicados.add(cb.equal(root.get("statusExecucao"), StatusExecucao.CONCLUIDO));
            predicados.add(root.get("idEquipe").in(idsEquipes));
            predicados.add(cb.greaterThanOrEqualTo(root.get("dataLancamento"), inicio));
            predicados.add(cb.lessThan(root.get("dataLancamento"), fim));
            if (idTipoServico != null) {
                predicados.add(cb.equal(root.get("idTipoServico"), idTipoServico));
            } else if (semTipoServico) {
                predicados.add(cb.isNull(root.get("idTipoServico")));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(this::hidratar);
    }

    /**
     * Turno aberto para a equipe continua sendo pré-requisito do lançamento. A exceção é
     * o lançamento feito antes do corte num turno que o servidor encerrou sozinho: ele
     * estava na fila do aparelho e pertence àquele turno, não ao turno aberto agora.
     */
    private Turno turnoDoLancamento(RegistrarExecucaoRequest request) {
        if (request.idTurno() != null) {
            var encerrado = turnoRepository.findById(request.idTurno())
                    .filter(t -> t.isAtivo() && t.isEncerradoAutomaticamente())
                    .filter(t -> t.getIdEquipe().equals(request.idEquipe()))
                    .filter(t -> !request.dataLancamento().isAfter(corteDiario.corteDoTurno(t.getDataInicio())));
            if (encerrado.isPresent()) {
                encerrado.get().registrarLancamentoTardio(request.dataLancamento());
                return turnoRepository.save(encerrado.get());
            }
        }
        return turnoRepository.findFirstByIdEquipeAndStatusAndAtivoTrue(request.idEquipe(), StatusTurno.ABERTO)
                .orElseThrow(() -> new InvarianteVioladaException("Não é possível lançar uma execução sem um turno aberto para a equipe."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaResumida> resumirNotas(Collection<UUID> idsExecucoes) {
        if (idsExecucoes == null || idsExecucoes.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> tipos = new HashMap<>();
        tipoServicoUseCases.listarTodos().forEach(t -> tipos.put(t.id(), t.codigo() + " · " + t.descricao()));
        return repository.findAllById(idsExecucoes).stream()
                .map(e -> new NotaResumida(e.getId(), e.getReferenciaNota(),
                        e.getIdTipoServico() == null ? null : tipos.get(e.getIdTipoServico()), e.getDataLancamento()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LancamentoDoTurno> listarLancamentosDoTurno(UUID idTurno) {
        return repository.findByIdTurnoAndAtivoTrueOrderByDataLancamentoAsc(idTurno).stream()
                .map(e -> new LancamentoDoTurno(e.getId(), e.getReferenciaNota(), e.getDataLancamento(),
                        e.getStatusExecucao(), e.getValorTotalFaturado(), e.getLatitude(), e.getLongitude()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProducaoDoTurno> resumirPorTurnos(Collection<UUID> idsTurnos) {
        if (idsTurnos == null || idsTurnos.isEmpty()) {
            return List.of();
        }
        return repository.resumirPorTurnos(idsTurnos, StatusExecucao.CONCLUIDO);
    }

    private ExecucaoComercial buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução não encontrada com ID: " + id));
    }

    /** Material da Concessionária não mexe no saldo: o estoque só vê a diferença do material da equipe. */
    private void publicarEventoAlteracao(ExecucaoComercial execucao, AlteracaoLancamentos alteracao, UUID idUsuario) {
        var estornos = itensDaEquipe(alteracao.materiaisRemovidos());
        var lancamentos = itensDaEquipe(alteracao.materiaisAdicionados());
        if (estornos.isEmpty() && lancamentos.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new ExecucaoAlteradaEvent(execucao.getId(), execucao.getIdEquipe(), idUsuario,
                estornos, lancamentos));
    }

    private static List<ExecucaoConcluidaEvent.ItemMaterialEvent> itensDaEquipe(List<MaterialLancado> materiais) {
        return materiais.stream()
                .filter(m -> m.origem() == OrigemMaterial.EQUIPE)
                .map(m -> new ExecucaoConcluidaEvent.ItemMaterialEvent(m.idMaterial(), m.quantidade(), m.operacao(), m.numSerie()))
                .toList();
    }

    private void publicarEventoConclusao(ExecucaoComercial execucao, UUID idUsuarioOperador) {
        // Material da Concessionária fica só no registro da execução: nem aplicação nem retirada
        // chegam ao estoque da equipe.
        List<ExecucaoConcluidaEvent.ItemMaterialEvent> itens = execucao.getMateriaisDaEquipe().stream()
                .map(m -> new ExecucaoConcluidaEvent.ItemMaterialEvent(
                        m.getIdMaterial(),
                        m.getQuantidade(),
                        m.getOperacao(),
                        m.getNumSerie()
                )).toList();

        eventPublisher.publishEvent(new ExecucaoConcluidaEvent(
                execucao.getId(),
                execucao.getIdEquipe(),
                idUsuarioOperador,
                itens
        ));
    }
}
