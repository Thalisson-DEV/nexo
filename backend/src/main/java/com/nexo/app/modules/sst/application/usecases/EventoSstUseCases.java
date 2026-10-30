package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.sst.application.dto.EnvolvidoEventoRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstFilter;
import com.nexo.app.modules.sst.application.dto.EventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstResumoResponse;
import com.nexo.app.modules.sst.application.dto.MotivoEventoRequest;
import com.nexo.app.modules.sst.application.dto.RotulosEventoSst;
import com.nexo.app.modules.sst.application.dto.TratativaEventoRequest;
import com.nexo.app.modules.sst.application.mappers.EventoSstMapper;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.DadosEventoSst;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.SnapshotAtividade;
import com.nexo.app.modules.sst.domain.SnapshotEnvolvido;
import com.nexo.app.modules.sst.domain.SnapshotOperacional;
import com.nexo.app.modules.sst.domain.SnapshotTecnico;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.EventoSstConcluidoEvent;
import com.nexo.app.shared.domain.events.EventoSstRegistradoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registro, correção e ciclo de vida dos eventos de SST.
 *
 * <p>O trabalho central daqui é <b>congelar o contexto</b>: o payload manda
 * identificadores, e este caso de uso resolve, nos cadastros de origem, os textos que
 * ficarão gravados no evento — nome do gestor, da base, código da equipe, placa, nome e
 * função de cada pessoa. Depois disso o evento é autossuficiente: uma página inteira de
 * listagem é montada sem uma única ida ao RH, e trocar o gestor de uma atividade amanhã
 * não reescreve nenhum registro passado.</p>
 *
 * <p>A exceção deliberada são os catálogos (classificação, lesão, agente) e o processo:
 * esses continuam sendo referência viva, e seus nomes são resolvidos na leitura, em
 * lote, para que corrigir um nome corrija em todo evento que aponta para ele.</p>
 *
 * <p>Deliberadamente <b>sem cache</b>, como a listagem de desvios: o evento muda de
 * estado por vários caminhos (tratativa, conclusão, cancelamento, anexo) e uma fila de
 * acidentes mostrando um estado vencido é pior do que uma consulta a mais.</p>
 *
 * <p>A repetição de um registro é barrada pelo protocolo {@code Idempotency-Key}, que
 * vive no filtro compartilhado: a mesma chave devolve a resposta gravada em vez de
 * gravar um segundo acidente igual.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventoSstUseCases {

    /** A fila de acidentes se lê pelo que aconteceu por último. */
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Direction.DESC, "dataOcorrencia");

    private final EventoSstRepository repository;
    private final AtividadeSstRepository atividadeRepository;
    private final CatalogoSstRepository catalogoRepository;
    private final EventoSstMapper mapper;
    private final ColaboradorApi colaboradorApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final EquipeApi equipeApi;
    private final VeiculoApi veiculoApi;
    private final ProcessoNegocioApi processoApi;
    private final ApplicationEventPublisher eventPublisher;

    // ===================================================================== //
    //  Escrita
    // ===================================================================== //

    @Transactional
    public EventoSstResponse registrar(EventoSstRequest request) {
        Montagem montagem = montar(request, null);

        EventoSst evento = EventoSst.registrar(gerarNumeroRegistro(), montagem.dados(),
                montagem.atividade(), montagem.operacional(), montagem.tecnicos(), montagem.envolvidos());
        EventoSst salvo = repository.save(evento);

        // O log carrega o número de registro e nunca o nome de quem se acidentou.
        log.info("Evento de SST {} registrado: tipo={}, base={}, atividade={}",
                salvo.getNumeroRegistro(), salvo.getTipo(), salvo.getBaseNome(), salvo.getAtividadeNome());

        eventPublisher.publishEvent(new EventoSstRegistradoEvent(
                salvo.getId(), salvo.getNumeroRegistro(), salvo.getTipo().name(), salvo.getTipo().getRotulo(),
                salvo.getAtividadeNome(), salvo.getBaseNome(), salvo.getDataOcorrencia(),
                salvo.isComLesao(), salvo.isComunicacaoAtrasada(), salvo.getIdGestor(), idsDosTecnicos(salvo)));

        return montarResposta(salvo);
    }

    /**
     * Executa exatamente a montagem e as invariantes do cadastro sem consumir
     * sequência nem persistir. É a etapa de pré-visualização da importação CSV.
     */
    @Transactional(readOnly = true)
    public void validarParaRegistro(EventoSstRequest request) {
        Montagem montagem = montar(request, null);
        EventoSst.registrar("VALIDACAO", montagem.dados(), montagem.atividade(),
                montagem.operacional(), montagem.tecnicos(), montagem.envolvidos());
    }

    @Transactional
    public EventoSstResponse atualizar(UUID id, EventoSstRequest request) {
        EventoSst evento = buscarEntidade(id);
        Montagem montagem = montar(request, evento);

        evento.atualizar(montagem.dados(), montagem.atividade(), montagem.operacional(),
                montagem.tecnicos(), montagem.envolvidos());
        persistirAlteracoes();

        return montarResposta(evento);
    }

    @Transactional
    public EventoSstResponse iniciarTratativa(UUID id, TratativaEventoRequest request) {
        EventoSst evento = buscarEntidade(id);
        evento.iniciarTratativa(request == null ? null : request.tratativa());
        persistirAlteracoes();

        log.info("Evento de SST {} entrou em tratativa", evento.getNumeroRegistro());
        return montarResposta(evento);
    }

    @Transactional
    public EventoSstResponse concluir(UUID id, TratativaEventoRequest request) {
        EventoSst evento = buscarEntidade(id);
        evento.concluir(request == null ? null : request.tratativa());
        persistirAlteracoes();

        log.info("Evento de SST {} concluído", evento.getNumeroRegistro());
        eventPublisher.publishEvent(new EventoSstConcluidoEvent(
                evento.getId(), evento.getNumeroRegistro(), evento.getTipo().name(),
                evento.getTipo().getRotulo(), evento.getAtividadeNome(), evento.getBaseNome(),
                evento.getTratativa(), evento.getIdGestor(), idsDosTecnicos(evento)));

        return montarResposta(evento);
    }

    @Transactional
    public EventoSstResponse cancelar(UUID id, MotivoEventoRequest request) {
        EventoSst evento = buscarEntidade(id);
        evento.cancelar(request == null ? null : request.motivo());
        persistirAlteracoes();

        log.info("Evento de SST {} cancelado", evento.getNumeroRegistro());
        return montarResposta(evento);
    }

    @Transactional
    public EventoSstResponse reabrir(UUID id, MotivoEventoRequest request) {
        EventoSst evento = buscarEntidade(id);
        evento.reabrir(request == null ? null : request.motivo());
        persistirAlteracoes();

        log.info("Evento de SST {} reaberto", evento.getNumeroRegistro());
        return montarResposta(evento);
    }

    /** Remoção é soft delete: nenhuma linha de acidente sai fisicamente do banco. */
    @Transactional
    public void desativar(UUID id) {
        EventoSst evento = buscarEntidade(id);
        evento.desativar();
        persistirAlteracoes();

        log.info("Evento de SST {} desativado", evento.getNumeroRegistro());
    }

    // ===================================================================== //
    //  Leitura
    // ===================================================================== //

    @Transactional(readOnly = true)
    public EventoSstResponse buscarPorId(UUID id) {
        return montarResposta(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventoSstResumoResponse> listar(EventoSstFilter filter, Pageable pageable) {
        Page<EventoSst> pagina = repository.findAll(
                EventoSstSpecification.geral(filter, resolverNomeDeColaborador(filter)), ordenar(pageable));

        Map<UUID, String> nomes = nomesDeCatalogo(pagina.getContent());
        return PageResponse.fromPage(pagina.map(evento -> mapper.toResumo(evento,
                new RotulosEventoSst(nomes.get(evento.getIdClassificacao()), null, null, null, null))));
    }

    /**
     * Eventos que atendem ao filtro, sem paginação — base dos indicadores e da
     * exportação, para que dashboard, planilha e tabela nunca discordem sobre o que
     * entra na conta.
     */
    @Transactional(readOnly = true)
    public List<EventoSst> carregarParaAnalise(EventoSstFilter filter, OffsetDateTime inicio, OffsetDateTime fim) {
        return repository.findAll(EventoSstSpecification.doPeriodo(
                filter, resolverNomeDeColaborador(filter), inicio, fim), MAIS_RECENTES);
    }

    /**
     * Traduz o trecho de nome em ids dentro do RH — a comparação parcial acontece lá,
     * porque é lá que existe a coluna normalizada de busca. {@code null} significa que
     * o filtro não foi pedido; lista vazia significa que ninguém casou.
     */
    private List<UUID> resolverNomeDeColaborador(EventoSstFilter filter) {
        if (filter == null || filter.nomeColaborador() == null || filter.nomeColaborador().isBlank()) {
            return null;
        }
        return colaboradorApi.buscarIdsPorTrechoDoNome(filter.nomeColaborador().trim());
    }

    private Pageable ordenar(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), MAIS_RECENTES);
    }

    // ===================================================================== //
    //  Montagem do snapshot
    // ===================================================================== //

    /** O que o domínio precisa receber pronto: dados, contexto congelado e pessoas. */
    private record Montagem(DadosEventoSst dados, SnapshotAtividade atividade, SnapshotOperacional operacional,
                            List<SnapshotTecnico> tecnicos, List<SnapshotEnvolvido> envolvidos) {
    }

    private Montagem montar(EventoSstRequest request, EventoSst existente) {
        AtividadeSst atividade = buscarAtividade(request.idAtividade(), existente);

        validarCatalogo(request.idClassificacao(), existente == null ? null : existente.getIdClassificacao(),
                TipoCatalogoSst.CLASSIFICACAO_EVENTO, "classificação do evento");
        validarCatalogo(request.idLocalLesao(), existente == null ? null : existente.getIdLocalLesao(),
                TipoCatalogoSst.LOCAL_LESAO, "local da lesão");
        validarCatalogo(request.idNaturezaLesao(), existente == null ? null : existente.getIdNaturezaLesao(),
                TipoCatalogoSst.NATUREZA_LESAO, "natureza da lesão");
        validarCatalogo(request.idAgenteCausador(), existente == null ? null : existente.getIdAgenteCausador(),
                TipoCatalogoSst.AGENTE_CAUSADOR, "agente causador");
        validarProcesso(request.idProcesso());

        return new Montagem(
                new DadosEventoSst(request.tipo(), request.dataOcorrencia(), request.dataComunicacao(),
                        request.idClassificacao(), request.idProcesso(), request.telemetria(), request.filmagem(),
                        request.localOcorrencia(), request.latitude(), request.longitude(), request.descricao(),
                        request.idLocalLesao(), request.idNaturezaLesao(), request.idAgenteCausador(),
                        request.diasPerdidos(), request.possuiCat(), request.tipoCat(), request.numeroCat(),
                        request.dataEmissaoCat()),
                montarAtividade(request, atividade),
                montarOperacional(request),
                montarTecnicos(request.idsTecnicos()),
                montarEnvolvidos(request.envolvidos()));
    }

    /**
     * Copia atividade e gestor. O gestor do evento é o da atividade, salvo quando o
     * registro informa outro — uma cobertura de férias, tipicamente —, e nesse caso o
     * evento fica marcado como tendo gestor substituto, sem alterar o cadastro.
     */
    private SnapshotAtividade montarAtividade(EventoSstRequest request, AtividadeSst atividade) {
        UUID idGestor = request.idGestor() != null ? request.idGestor() : atividade.getIdGestor();
        // Substituto pressupõe titular. Quando a atividade não tem gestor cadastrado, o
        // informado no evento não está cobrindo ninguém — é simplesmente o responsável, e
        // marcá-lo como substituto poria um selo de exceção no caso mais comum da carga inicial.
        boolean substituto = atividade.getIdGestor() != null
                && request.idGestor() != null
                && !request.idGestor().equals(atividade.getIdGestor());

        if (idGestor == null) {
            // Sem gestor no cadastro e sem gestor no payload: o domínio recusa com a
            // mensagem que explica as duas saídas.
            return new SnapshotAtividade(request.idAtividade(), atividade.getNome(), null, null, null, false);
        }

        ResumoColaborador gestor = buscarColaborador(idGestor, "gestor da atividade");
        if (!gestor.ativo()) {
            throw new InvarianteVioladaException(
                    "O gestor informado está inativo no RH. Informe outro responsável pelo evento.");
        }
        return new SnapshotAtividade(request.idAtividade(), atividade.getNome(),
                idGestor, gestor.nomeCompleto(), gestor.funcao(), substituto);
    }

    private SnapshotOperacional montarOperacional(EventoSstRequest request) {
        BaseOperacionalResponse base = baseOperacionalApi.buscarPorIdResponse(request.idBaseOperacional())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Base operacional não encontrada com ID: " + request.idBaseOperacional()));

        String equipeCodigo = null;
        if (request.idEquipe() != null) {
            EquipeResponse equipe = equipeApi.buscarPorIdResponse(request.idEquipe())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Equipe não encontrada com ID: " + request.idEquipe()));
            equipeCodigo = equipe.codigo();
        }

        String placa = null;
        if (request.idVeiculo() != null) {
            VeiculoResponse veiculo = veiculoApi.buscarPorIdResponse(request.idVeiculo())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Veículo não encontrado com ID: " + request.idVeiculo()));
            placa = veiculo.placa();
        }

        return new SnapshotOperacional(base.id(), base.nome(),
                request.idEquipe(), equipeCodigo, request.idVeiculo(), placa);
    }

    private List<SnapshotTecnico> montarTecnicos(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> distintos = new LinkedHashSet<>(ids);
        Map<UUID, ResumoColaborador> resumos = colaboradorApi.buscarResumosPorIds(distintos);

        List<SnapshotTecnico> tecnicos = new ArrayList<>(distintos.size());
        for (UUID id : distintos) {
            ResumoColaborador resumo = resumos.get(id);
            if (resumo == null) {
                throw new InvarianteVioladaException("Colaborador não encontrado para ser técnico de segurança: " + id);
            }
            if (!resumo.ativo()) {
                throw new InvarianteVioladaException(
                        "O técnico de segurança informado está inativo no RH: " + resumo.funcao());
            }
            tecnicos.add(new SnapshotTecnico(id, resumo.nomeCompleto(), resumo.funcao()));
        }
        return tecnicos;
    }

    /**
     * Copia as pessoas. Para colaborador, o retrato vem do <b>cadastro</b> e não do que
     * o cliente mandou — o formulário não decide como a pessoa se chamava. Para
     * terceiro, o payload é a única fonte que existe.
     */
    private List<SnapshotEnvolvido> montarEnvolvidos(List<EnvolvidoEventoRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<UUID> idsColaboradores = requests.stream()
                .filter(e -> e.tipo() == TipoEnvolvido.COLABORADOR)
                .map(EnvolvidoEventoRequest::idColaborador)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, ResumoColaborador> resumos = idsColaboradores.isEmpty()
                ? Map.of()
                : colaboradorApi.buscarResumosPorIds(idsColaboradores);

        List<SnapshotEnvolvido> envolvidos = new ArrayList<>(requests.size());
        for (EnvolvidoEventoRequest request : requests) {
            if (request.tipo() == TipoEnvolvido.COLABORADOR) {
                if (request.idColaborador() == null) {
                    // Deixa o domínio recusar com a mensagem que orienta a saída (registrar como terceiro).
                    envolvidos.add(new SnapshotEnvolvido(TipoEnvolvido.COLABORADOR, request.papel(),
                            null, request.nome(), request.funcao(), null, null));
                    continue;
                }
                ResumoColaborador resumo = resumos.get(request.idColaborador());
                if (resumo == null) {
                    throw new InvarianteVioladaException(
                            "Colaborador não encontrado entre os envolvidos: " + request.idColaborador());
                }
                envolvidos.add(SnapshotEnvolvido.colaborador(request.papel(), request.idColaborador(),
                        resumo.nomeCompleto(), resumo.funcao(), resumo.baseOperacional()));
            } else {
                envolvidos.add(SnapshotEnvolvido.terceiro(request.papel(),
                        request.nome(), request.funcao(), request.empresa()));
            }
        }
        return envolvidos;
    }

    private AtividadeSst buscarAtividade(UUID idAtividade, EventoSst existente) {
        AtividadeSst atividade = atividadeRepository.findById(idAtividade)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Atividade de SST não encontrada com ID: " + idAtividade));

        boolean jaEraAEscolhida = existente != null && idAtividade.equals(existente.getIdAtividade());
        if (!atividade.isAtivo() && !jaEraAEscolhida) {
            throw new InvarianteVioladaException(
                    "A atividade \"" + atividade.getNome() + "\" está desativada e não pode ser usada em novos eventos.");
        }
        return atividade;
    }

    /**
     * Confere que a entrada existe e pertence ao vocabulário certo — a garantia que a
     * chave estrangeira não dá quando os quatro catálogos dividem uma tabela.
     *
     * <p>A entrada precisa estar <b>ativa</b> apenas quando é uma escolha nova. Uma
     * correção de descrição em um evento antigo não deve ser barrada porque a
     * classificação usada há dois anos foi desativada desde então.</p>
     */
    private void validarCatalogo(UUID id, UUID idAtual, TipoCatalogoSst tipo, String rotulo) {
        if (id == null) {
            return;
        }
        if (id.equals(idAtual)) {
            CatalogoSst entrada = catalogoRepository.findById(id)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Entrada de catálogo não encontrada com ID: " + id));
            exigirTipo(entrada, tipo, rotulo);
            return;
        }
        if (!catalogoRepository.existsByIdAndTipoAndAtivoTrue(id, tipo)) {
            throw new InvarianteVioladaException(
                    "A entrada informada como " + rotulo + " não existe, está desativada ou pertence a outro "
                            + "vocabulário (esperado: " + tipo.getRotulo() + ").");
        }
    }

    private void exigirTipo(CatalogoSst entrada, TipoCatalogoSst tipo, String rotulo) {
        if (!entrada.isDoTipo(tipo)) {
            throw new InvarianteVioladaException(
                    "A entrada informada como " + rotulo + " pertence a outro vocabulário (esperado: "
                            + tipo.getRotulo() + ").");
        }
    }

    private void validarProcesso(UUID idProcesso) {
        if (idProcesso != null && !processoApi.existeProcesso(idProcesso)) {
            throw new RecursoNaoEncontradoException(
                    "Processo de negócio não encontrado ou inativo com ID: " + idProcesso);
        }
    }

    private ResumoColaborador buscarColaborador(UUID id, String papel) {
        ResumoColaborador resumo = colaboradorApi.buscarResumosPorIds(Set.of(id)).get(id);
        if (resumo == null) {
            throw new InvarianteVioladaException("Colaborador não encontrado para ser " + papel + ": " + id);
        }
        return resumo;
    }

    /**
     * Gera o identificador legível do registro. O ano é o do registro, e o sequencial
     * vem da sequence do banco — que não repete número nem depois de uma transação
     * desfeita, condição para um identificador citado em CAT e em processo.
     */
    private String gerarNumeroRegistro() {
        return String.format("EVT-%d-%06d", OffsetDateTime.now().getYear(), repository.proximoNumeroSequencial());
    }

    // ===================================================================== //
    //  Apoio
    // ===================================================================== //

    private EventoSst buscarEntidade(UUID id) {
        return repository.findOne(EventoSstSpecification.porId(id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento de SST não encontrado com ID: " + id));
    }

    /**
     * Grava o agregado já gerenciado por <em>dirty checking</em>.
     *
     * <p>É {@code flush()} e não {@code save()} de propósito: {@code save()} em uma
     * entidade gerenciada cai em {@code merge()}, que persiste <b>cópias</b> dos filhos
     * transientes e deixa as instâncias em mãos sem id — o filho recém-criado volta com
     * {@code id == null} para quem montou a resposta.</p>
     */
    private void persistirAlteracoes() {
        repository.flush();
    }

    private EventoSstResponse montarResposta(EventoSst evento) {
        return mapper.toResponse(evento, rotulosDe(evento));
    }

    private RotulosEventoSst rotulosDe(EventoSst evento) {
        Map<UUID, String> nomes = nomesDeCatalogo(List.of(evento));
        String processo = evento.getIdProcesso() == null ? null
                : processoApi.buscarPorIdResponse(evento.getIdProcesso())
                .map(ProcessoNegocioResponse::nome).orElse(null);

        return new RotulosEventoSst(
                nomes.get(evento.getIdClassificacao()),
                nomes.get(evento.getIdLocalLesao()),
                nomes.get(evento.getIdNaturezaLesao()),
                nomes.get(evento.getIdAgenteCausador()),
                processo);
    }

    /**
     * Resolve, em uma consulta só, os nomes de todas as entradas de catálogo citadas
     * por uma lista de eventos. Entradas desativadas continuam sendo resolvidas: um
     * evento antigo não pode perder a classificação que tinha.
     */
    private Map<UUID, String> nomesDeCatalogo(Collection<EventoSst> eventos) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (EventoSst evento : eventos) {
            adicionarSeExiste(ids, evento.getIdClassificacao());
            adicionarSeExiste(ids, evento.getIdLocalLesao());
            adicionarSeExiste(ids, evento.getIdNaturezaLesao());
            adicionarSeExiste(ids, evento.getIdAgenteCausador());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> nomes = new HashMap<>();
        catalogoRepository.findAllById(ids).forEach(entrada -> nomes.put(entrada.getId(), entrada.getNome()));
        return nomes;
    }

    private static void adicionarSeExiste(Set<UUID> ids, UUID id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static Set<UUID> idsDosTecnicos(EventoSst evento) {
        return evento.getTecnicos().stream()
                .map(tecnico -> tecnico.getIdColaborador())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
