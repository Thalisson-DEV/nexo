package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.application.dto.PapeisTurno;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.obras.application.dto.TurnoObraFilter;
import com.nexo.app.modules.obras.application.dto.TurnoObraResponses;
import com.nexo.app.modules.obras.domain.AnexoTurno;
import com.nexo.app.modules.obras.domain.ApontamentoExecucao;
import com.nexo.app.modules.obras.domain.AprTurno;
import com.nexo.app.modules.obras.domain.AtividadeTurno;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OcorrenciaTurno;
import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.ProgramacaoItem;
import com.nexo.app.modules.obras.domain.SituacaoLiberacao;
import com.nexo.app.modules.obras.domain.SituacaoProgramacao;
import com.nexo.app.modules.obras.domain.StatusAprTurno;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.domain.StatusProgramacao;
import com.nexo.app.modules.obras.infrastructure.persistence.AnexoTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AprTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AtividadeTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.OcorrenciaTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.RelatorioTurnoRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ComposicaoEquipeApi;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.JanelaMetaFaturamento;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.application.dto.Paged;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leituras do turno de Obras. Reúne na leitura o turno do módulo de execução com o executado e os registros de Obras;
 * nada disso fica gravado no turno. O realizado vem dos apontamentos, não das notas do comercial.
 */
@Service
@RequiredArgsConstructor
public class ConsultarTurnosObra {

    /** Sem data inicial, a consulta olha até aqui para trás. */
    static final int DIAS_MAXIMOS_SEM_DATA = 186;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final TurnoApi turnoApi;
    private final EquipeApi equipeApi;
    private final ComposicaoEquipeApi composicaoApi;
    private final ColaboradorApi colaboradorApi;
    private final VeiculoApi veiculoApi;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final ObraRepository obraRepository;
    private final ProgramacaoRepository programacaoRepository;
    private final ApontamentoExecucaoRepository apontamentoRepository;
    private final AtividadeTurnoRepository atividadeRepository;
    private final RelatorioTurnoRepository relatorioRepository;
    private final OcorrenciaTurnoRepository ocorrenciaRepository;
    private final AprTurnoRepository aprRepository;
    private final AnexoTurnoRepository anexoRepository;
    private final ProgramacaoObraUseCases programacaoUseCases;

    @Transactional(readOnly = true)
    public TurnoObraResponses.Lista listar(TurnoObraFilter filtro, Pageable pageable) {
        OffsetDateTime agora = OffsetDateTime.now(ApontamentoExecucaoUseCases.FUSO);
        LocalDate hoje = agora.toLocalDate();
        Map<UUID, EquipeResponse> equipes = equipesDeObras().stream()
                .filter(e -> filtro.idEquipe() == null || filtro.idEquipe().equals(e.id()))
                .filter(e -> SelecaoMultipla.contem(filtro.idBaseOperacional(), e.baseOperacional() != null ? e.baseOperacional().id() : null))
                .collect(Collectors.toMap(EquipeResponse::id, Function.identity()));
        LocalDate de = filtro.de() != null ? filtro.de() : hoje.minusDays(DIAS_MAXIMOS_SEM_DATA);
        ConsultaTurnos consulta = new ConsultaTurnos(equipes.keySet(), null, inicioDoDia(de),
                filtro.ate() != null ? inicioDoDia(filtro.ate().plusDays(1)) : null, null, null);
        List<TurnoResponse> turnos = turnoApi.consultar(consulta, Pageable.unpaged()).getContent();

        List<TurnoObraResponses.Linha> recorte = linhas(turnos, equipes, agora).stream()
                .filter(l -> atendeObra(l, filtro.obra()))
                .filter(l -> atendeBusca(l, filtro.q()))
                .toList();
        List<TurnoObraResponses.Linha> filtradas = recorte.stream()
                .filter(l -> filtro.status() == null || l.status() == filtro.status())
                .filter(l -> atendeMeta(l, filtro.meta()))
                .filter(l -> !Boolean.TRUE.equals(filtro.semRelatorio()) || l.semRelatorio())
                .filter(l -> !Boolean.TRUE.equals(filtro.comOcorrencia()) || l.ocorrencias() > 0)
                .sorted(ordenacao(pageable.getSort()))
                .toList();
        return new TurnoObraResponses.Lista(indicadores(recorte), pagina(filtradas, pageable), paginacao(filtradas.size(), pageable));
    }

    @Transactional(readOnly = true)
    public TurnoObraResponses.Detalhe detalhar(UUID id) {
        OffsetDateTime agora = OffsetDateTime.now(ApontamentoExecucaoUseCases.FUSO);
        TurnoResponse turno = turnoApi.buscarPorId(id);
        EquipeResponse equipe = equipeApi.buscarPorIdResponse(turno.idEquipe()).orElse(null);
        if (equipe == null || equipe.processo() == null || !obraRepository.listarProcessosComObras().contains(equipe.processo().id())) {
            throw new RecursoNaoEncontradoException("Turno não encontrado entre as equipes de Obras.");
        }
        LocalDate dia = diaDe(turno.dataInicio());
        LocalDate ultimoDia = turno.dataFim() != null ? diaDe(turno.dataFim()) : agora.toLocalDate();
        List<Programacao> programacoes = programacaoRepository.listarNoPeriodoComItens(dia, ultimoDia.isBefore(dia) ? dia : ultimoDia).stream()
                .filter(p -> p.getIdEquipe().equals(turno.idEquipe()) && p.getStatus() == StatusProgramacao.PROGRAMADA)
                .sorted(Comparator.comparing(Programacao::getNumero))
                .toList();
        List<ApontamentoExecucao> apontamentos = apontamentoRepository.listarDoTurno(id);
        List<AtividadeTurno> atividades = atividadeRepository.findByIdTurnoInAndAtivoTrue(List.of(id));
        List<OcorrenciaTurno> ocorrencias = ocorrenciaRepository.findByIdTurnoInAndAtivoTrue(List.of(id));
        List<AprTurno> aprs = aprRepository.findByIdTurnoInAndAtivoTrue(List.of(id));
        Set<UUID> idsProgramacoes = new HashSet<>();
        aprs.stream().map(AprTurno::getIdProgramacao).filter(Objects::nonNull).forEach(idsProgramacoes::add);
        programacoes.forEach(p -> idsProgramacoes.add(p.getId()));
        atividades.stream().map(AtividadeTurno::getIdProgramacao).filter(Objects::nonNull).forEach(idsProgramacoes::add);
        Map<UUID, Long> numeros = new HashMap<>();
        programacaoRepository.findAllById(idsProgramacoes).forEach(p -> numeros.put(p.getId(), p.getNumero()));
        Set<UUID> idsObras = new HashSet<>();
        programacoes.forEach(p -> idsObras.add(p.getIdObra()));
        atividades.forEach(a -> idsObras.add(a.getIdObra()));
        apontamentos.forEach(a -> idsObras.add(a.getIdObra()));
        ocorrencias.stream().map(OcorrenciaTurno::getIdObra).filter(Objects::nonNull).forEach(idsObras::add);
        aprs.stream().map(AprTurno::getIdObra).filter(Objects::nonNull).forEach(idsObras::add);
        Map<UUID, Obra> obras = new HashMap<>();
        obraRepository.findAllById(idsObras).forEach(o -> obras.put(o.getId(), o));

        Map<UUID, Map<UUID, String>> locais = new HashMap<>();
        BigDecimal realizado = apontamentos.stream().map(ApontamentoExecucao::getValorTotal).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal meta = metaFaturamentoApi.cronograma(turno.idEquipe()).metaDiaria(dia);
        List<AnexoTurno> anexos = anexoRepository.findByIdTurnoAndAtivoTrue(id);
        Map<UUID, ResumoColaborador> pessoas = resumos(turno.idsColaboradores(), aprs.stream().map(AprTurno::getIdResponsavel).toList());
        PapeisTurno papeis = turnoApi.buscarPapeis(List.of(id)).stream().findFirst()
                .orElse(new PapeisTurno(id, null, Set.of()));

        return new TurnoObraResponses.Detalhe(turno.id(), turno.idEquipe(), equipe.codigo(),
                equipe.tipoEquipe() != null ? equipe.tipoEquipe().nome() : null,
                equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                turno.idVeiculo() != null ? turno.idVeiculo() : equipe.veiculo() != null ? equipe.veiculo().id() : null,
                placa(turno, equipe, new HashMap<>()), turno.status(), turno.dataInicio(), turno.dataFim(),
                duracao(turno, agora), abertoDesdeOutroDia(turno, agora), turno.encerradoAutomaticamente(), turno.motivoEncerramento(),
                abertoPor(turno, new HashMap<>()), membros(turno, papeis, pessoas),
                realizado, meta, percentual(realizado, meta), bateu(realizado, meta),
                programacoes.stream().map(p -> paraProgramacao(p, obras.get(p.getIdObra()), agora.toLocalDate(),
                        SituacaoLiberacao.avaliar(p.getIdObra(), p.getId(), ultimoDia, ApontamentoExecucaoUseCases.FUSO, aprs, ocorrencias))).toList(),
                atividades.stream().map(a -> paraAtividade(a, obras.get(a.getIdObra()), numeros.get(a.getIdProgramacao()),
                        SituacaoLiberacao.avaliar(a.getIdObra(), a.getIdProgramacao(), ultimoDia, ApontamentoExecucaoUseCases.FUSO, aprs, ocorrencias))).toList(),
                apontamentos.stream().map(a -> paraServico(a, obras.get(a.getIdObra()),
                        locais.computeIfAbsent(a.getIdObra(), programacaoUseCases::locais))).toList(),
                relatorioRepository.findByIdTurno(id).filter(r -> r.isAtivo())
                        .map(r -> new TurnoObraResponses.RelatorioDoTurno(r.getObservacao(),
                                r.getUsuarioUltimaAlteracao() != null ? r.getUsuarioUltimaAlteracao() : r.getUsuarioCriacao(),
                                r.getDataUltimaAlteracao() != null ? r.getDataUltimaAlteracao() : r.getDataCriacao()))
                        .orElse(null),
                anexos.stream().filter(a -> a.getIdApr() == null).map(ConsultarTurnosObra::paraFoto).toList(),
                ocorrencias.stream().sorted(Comparator.comparing(OcorrenciaTurno::getDataCriacao))
                        .map(o -> new TurnoObraResponses.OcorrenciaDoTurno(o.getId(), o.getTipo(), o.getDescricao(), o.getIdObra(),
                                o.getIdObra() != null && obras.containsKey(o.getIdObra()) ? obras.get(o.getIdObra()).getNumero() : null,
                                o.isImpedeExecucao(), o.getUsuarioCriacao(), o.getDataCriacao()))
                        .toList(),
                aprs.stream().sorted(Comparator.comparing(AprTurno::getDataCriacao))
                        .map(a -> new TurnoObraResponses.AprDoTurno(a.getId(), a.getAtividade(), a.getIdObra(),
                                a.getIdObra() != null && obras.containsKey(a.getIdObra()) ? obras.get(a.getIdObra()).getNumero() : null,
                                a.getIdProgramacao(), numeros.get(a.getIdProgramacao()), a.getIdResponsavel(), a.getIdResponsavel() != null && pessoas.containsKey(a.getIdResponsavel()) ? pessoas.get(a.getIdResponsavel()).nomeCompleto() : null,
                                a.getStatus(), a.getDataConclusao(), a.getUsuarioCriacao(), a.getDataCriacao(),
                                anexos.stream().filter(x -> a.getId().equals(x.getIdApr())).map(ConsultarTurnosObra::paraFoto).toList(), a.getFormulario()))
                        .toList());
    }

    /**
     * Situação de cada equipe no dia, com o programado e o executado no ciclo da meta até o dia, no dia e no dia útil
     * anterior. Agendamento sem turno só vale até hoje e em dia de trabalho: fim de semana no meio de uma programação
     * longa não é falta, salvo quando ela começa nesse dia.
     */
    @Transactional(readOnly = true)
    public TurnoObraResponses.Boletim boletim(LocalDate data, UUID idBase) {
        OffsetDateTime agora = OffsetDateTime.now(ApontamentoExecucaoUseCases.FUSO);
        LocalDate hoje = agora.toLocalDate();
        LocalDate dia = data != null ? data : hoje;
        LocalDate anterior = diaUtilAnterior(dia);
        List<EquipeResponse> equipes = equipesDeObras().stream()
                .filter(e -> idBase == null || (e.baseOperacional() != null && idBase.equals(e.baseOperacional().id())))
                .sorted(Comparator.comparing(EquipeResponse::codigo))
                .toList();
        List<UUID> ids = equipes.stream().map(EquipeResponse::id).toList();
        if (ids.isEmpty()) {
            return new TurnoObraResponses.Boletim(dia, anterior, dia.equals(hoje), agora,
                    new TurnoObraResponses.ResumoBoletim(0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), List.of());
        }

        Map<UUID, CronogramaMetaFaturamento> metas = metaFaturamentoApi.cronogramas(ids);
        Map<UUID, LocalDate> inicioCiclo = new HashMap<>();
        for (UUID id : ids) {
            inicioCiclo.put(id, metas.getOrDefault(id, CronogramaMetaFaturamento.vazio()).janelaEm(dia)
                    .map(JanelaMetaFaturamento::inicio).orElse(dia.withDayOfMonth(1)));
        }
        LocalDate maisAntigo = inicioCiclo.values().stream().min(Comparator.naturalOrder()).orElse(dia);
        if (anterior.isBefore(maisAntigo)) {
            maisAntigo = anterior;
        }
        List<ProgramacaoRepository.ValorProgramado> programado = programacaoRepository.somarNoPeriodo(ids, maisAntigo, dia);
        Map<UUID, List<ProgramacaoRepository.ValorProgramado>> programadoPorEquipe = programado.stream()
                .collect(Collectors.groupingBy(ProgramacaoRepository.ValorProgramado::idEquipe));
        Map<UUID, List<ApontamentoExecucaoRepository.ValorNaData>> executadoPorEquipe = apontamentoRepository
                .somarPorEquipeEData(ids, maisAntigo, dia).stream()
                .collect(Collectors.groupingBy(ApontamentoExecucaoRepository.ValorNaData::idEquipe));

        OffsetDateTime inicioDia = inicioDoDia(dia);
        Map<UUID, List<TurnoResponse>> turnosPorEquipe = turnoApi.listarSobrepostosAoPeriodo(inicioDia, inicioDoDia(dia.plusDays(1)), ids).stream()
                .collect(Collectors.groupingBy(TurnoResponse::idEquipe));
        List<TurnoResponse> todos = turnosPorEquipe.values().stream().flatMap(List::stream).toList();
        Map<UUID, PapeisTurno> papeis = turnoApi.buscarPapeis(todos.stream().map(TurnoResponse::id).toList()).stream()
                .collect(Collectors.toMap(PapeisTurno::idTurno, Function.identity()));
        Map<UUID, ResumoColaborador> pessoas = resumos(todos.stream().flatMap(t -> t.idsColaboradores().stream()).toList(), List.of());

        Map<UUID, List<Programacao>> agendaPorEquipe = programacaoRepository.listarNoPeriodoComItens(dia, dia).stream()
                .filter(p -> ids.contains(p.getIdEquipe()))
                .filter(p -> p.situacaoEm(dia) != SituacaoProgramacao.CANCELADA)
                .sorted(Comparator.comparing(Programacao::getNumero))
                .collect(Collectors.groupingBy(Programacao::getIdEquipe));
        Map<UUID, Obra> obrasDaAgenda = new HashMap<>();
        obraRepository.findAllById(agendaPorEquipe.values().stream().flatMap(List::stream).map(Programacao::getIdObra).collect(Collectors.toSet()))
                .forEach(o -> obrasDaAgenda.put(o.getId(), o));
        Set<UUID> idsTurnos = todos.stream().map(TurnoResponse::id).collect(Collectors.toSet());
        Map<UUID, List<AprTurno>> aprsPorTurno = aprRepository.findByIdTurnoInAndAtivoTrue(idsTurnos).stream()
                .collect(Collectors.groupingBy(AprTurno::getIdTurno));
        Map<UUID, List<OcorrenciaTurno>> ocorrenciasPorTurno = ocorrenciaRepository.findByIdTurnoInAndAtivoTrue(idsTurnos).stream()
                .collect(Collectors.groupingBy(OcorrenciaTurno::getIdTurno));

        Map<UUID, String> placas = new HashMap<>();
        List<TurnoObraResponses.EquipeBoletim> linhas = equipes.stream().map(equipe -> {
            UUID id = equipe.id();
            List<TurnoResponse> turnos = turnosPorEquipe.getOrDefault(id, List.of());
            TurnoResponse aberto = turnos.stream().filter(t -> t.status() == StatusTurno.ABERTO)
                    .max(Comparator.comparing(TurnoResponse::dataInicio)).orElse(null);
            TurnoResponse principal = aberto != null ? aberto
                    : turnos.stream().max(Comparator.comparing(TurnoResponse::dataInicio)).orElse(null);
            TurnoObraResponses.SituacaoEquipe situacao = aberto != null ? TurnoObraResponses.SituacaoEquipe.EM_TURNO
                    : principal != null ? TurnoObraResponses.SituacaoEquipe.ENCERRADO : TurnoObraResponses.SituacaoEquipe.SEM_TURNO;
            List<TurnoObraResponses.Membro> componentes = principal != null
                    ? membros(principal, papeis.getOrDefault(principal.id(), new PapeisTurno(principal.id(), null, Set.of())), pessoas)
                    : composicaoAtual(id);

            List<ProgramacaoRepository.ValorProgramado> doProgramado = programadoPorEquipe.getOrDefault(id, List.of());
            List<ApontamentoExecucaoRepository.ValorNaData> doExecutado = executadoPorEquipe.getOrDefault(id, List.of());
            TurnoObraResponses.ValoresBoletim noDia = valores(doProgramado, doExecutado, dia, dia);
            BigDecimal meta = metas.getOrDefault(id, CronogramaMetaFaturamento.vazio()).metaDiaria(dia);
            List<Programacao> agenda = agendaPorEquipe.getOrDefault(id, List.of());
            List<Programacao> emAberto = agenda.stream().filter(p -> p.situacaoEm(dia) != SituacaoProgramacao.CONCLUIDA).toList();
            boolean diaDeTrabalho = JanelaMetaFaturamento.diaUtil(dia) || emAberto.stream().anyMatch(p -> p.getDataInicio().equals(dia));
            boolean semTurno = situacao == TurnoObraResponses.SituacaoEquipe.SEM_TURNO && !emAberto.isEmpty() && !dia.isAfter(hoje) && diaDeTrabalho;
            List<AprTurno> aprsDaEquipe = turnos.stream().flatMap(t -> aprsPorTurno.getOrDefault(t.id(), List.of()).stream()).toList();
            List<OcorrenciaTurno> ocorrenciasDaEquipe = turnos.stream().flatMap(t -> ocorrenciasPorTurno.getOrDefault(t.id(), List.of()).stream()).toList();

            return new TurnoObraResponses.EquipeBoletim(id, equipe.codigo(),
                    equipe.tipoEquipe() != null ? equipe.tipoEquipe().nome() : null,
                    equipe.baseOperacional() != null ? equipe.baseOperacional().id() : null,
                    equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                    situacao, principal != null ? principal.id() : null, principal != null ? principal.dataInicio() : null,
                    principal != null ? principal.dataFim() : null, principal != null ? duracao(principal, agora) : 0,
                    aberto != null && aberto.dataInicio().isBefore(inicioDia),
                    principal != null ? placa(principal, equipe, placas) : equipe.veiculo() != null ? equipe.veiculo().placa() : null,
                    componentes.stream().filter(TurnoObraResponses.Membro::lider).findFirst().orElse(null),
                    componentes,
                    valores(doProgramado, doExecutado, inicioCiclo.get(id), dia), noDia,
                    valores(doProgramado, doExecutado, anterior, anterior),
                    meta, percentual(noDia.executado(), meta), semTurno,
                    agenda.stream().map(p -> {
                        Obra obra = obrasDaAgenda.get(p.getIdObra());
                        List<ProgramacaoItem> itens = p.getItensAtivos();
                        return new TurnoObraResponses.AgendaDoDia(p.getId(), p.getNumero(), p.getIdObra(), obra != null ? obra.getNumero() : null,
                                obra != null ? obra.getTitulo() : null, p.getDescricao(), p.valorTotal().setScale(2, RoundingMode.HALF_UP), itens.size(),
                                (int) itens.stream().filter(v -> v.getItemPlanejado().concluido()).count(),
                                SituacaoLiberacao.avaliar(p.getIdObra(), p.getId(), dia, ApontamentoExecucaoUseCases.FUSO, aprsDaEquipe, ocorrenciasDaEquipe));
                    }).toList());
        }).toList();

        return new TurnoObraResponses.Boletim(dia, anterior, dia.equals(hoje), agora, resumirBoletim(linhas), linhas);
    }

    private static TurnoObraResponses.ResumoBoletim resumirBoletim(List<TurnoObraResponses.EquipeBoletim> linhas) {
        return new TurnoObraResponses.ResumoBoletim(linhas.size(),
                contar(linhas, TurnoObraResponses.SituacaoEquipe.EM_TURNO),
                contar(linhas, TurnoObraResponses.SituacaoEquipe.ENCERRADO),
                contar(linhas, TurnoObraResponses.SituacaoEquipe.SEM_TURNO),
                (int) linhas.stream().filter(TurnoObraResponses.EquipeBoletim::agendamentoSemTurno).count(),
                linhas.stream().map(l -> l.noDia().programado()).reduce(BigDecimal.ZERO, BigDecimal::add),
                linhas.stream().map(l -> l.noDia().executado()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static int contar(List<TurnoObraResponses.EquipeBoletim> linhas, TurnoObraResponses.SituacaoEquipe situacao) {
        return (int) linhas.stream().filter(l -> l.situacao() == situacao).count();
    }

    private static TurnoObraResponses.ValoresBoletim valores(List<ProgramacaoRepository.ValorProgramado> programado,
                                                             List<ApontamentoExecucaoRepository.ValorNaData> executado,
                                                             LocalDate de, LocalDate ate) {
        BigDecimal valorProgramado = programado.stream()
                .filter(p -> !p.inicio().isAfter(ate) && !p.fim().isBefore(de))
                .map(p -> ProjecaoEquipesObra.parteNoPeriodo(p, de, ate))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorExecutado = executado.stream()
                .filter(e -> !e.data().isBefore(de) && !e.data().isAfter(ate))
                .map(ApontamentoExecucaoRepository.ValorNaData::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new TurnoObraResponses.ValoresBoletim(valorProgramado, valorExecutado);
    }

    private List<TurnoObraResponses.Membro> composicaoAtual(UUID idEquipe) {
        return composicaoApi.buscarComposicao(idEquipe)
                .map(c -> c.membros().stream()
                        .map(m -> new TurnoObraResponses.Membro(m.idColaborador(), m.nome(), m.funcao(), m.responsavel(), false))
                        .sorted(ordemDosMembros())
                        .toList())
                .orElse(List.of());
    }

    /** Sem líder informado, vale o responsável da equipe, se ele estiver no turno. */
    private List<TurnoObraResponses.Membro> membros(TurnoResponse turno, PapeisTurno papeis, Map<UUID, ResumoColaborador> pessoas) {
        UUID lider = papeis.idLider();
        if (lider == null) {
            lider = composicaoApi.buscarComposicao(turno.idEquipe())
                    .flatMap(c -> c.membros().stream().filter(ComposicaoEquipeApi.MembroComposicao::responsavel).findFirst())
                    .map(ComposicaoEquipeApi.MembroComposicao::idColaborador)
                    .orElse(null);
        }
        UUID idLider = lider;
        return (turno.idsColaboradores() != null ? turno.idsColaboradores() : Set.<UUID>of()).stream()
                .map(id -> {
                    ResumoColaborador pessoa = pessoas.get(id);
                    return new TurnoObraResponses.Membro(id, pessoa != null ? pessoa.nomeCompleto() : "Colaborador removido",
                            pessoa != null ? pessoa.funcao() : null, id.equals(idLider), papeis.idsSubstitutos().contains(id));
                })
                .sorted(ordemDosMembros())
                .toList();
    }

    private static Comparator<TurnoObraResponses.Membro> ordemDosMembros() {
        return Comparator.comparing((TurnoObraResponses.Membro m) -> !m.lider())
                .thenComparing(TurnoObraResponses.Membro::substituto)
                .thenComparing(TurnoObraResponses.Membro::nome, String.CASE_INSENSITIVE_ORDER);
    }

    private Map<UUID, ResumoColaborador> resumos(Collection<UUID> colaboradores, Collection<UUID> outros) {
        Set<UUID> ids = new HashSet<>();
        if (colaboradores != null) {
            ids.addAll(colaboradores);
        }
        outros.stream().filter(Objects::nonNull).forEach(ids::add);
        return ids.isEmpty() ? Map.of() : colaboradorApi.buscarResumosPorIds(ids);
    }

    private List<TurnoObraResponses.Linha> linhas(List<TurnoResponse> turnos, Map<UUID, EquipeResponse> equipes, OffsetDateTime agora) {
        if (turnos.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = turnos.stream().map(TurnoResponse::id).toList();
        Map<UUID, ApontamentoExecucaoRepository.ResumoDoTurno> executado = apontamentoRepository.resumirPorTurnos(ids).stream()
                .collect(Collectors.toMap(ApontamentoExecucaoRepository.ResumoDoTurno::idTurno, Function.identity()));
        Set<UUID> comRelatorio = relatorioRepository.findByIdTurnoInAndAtivoTrue(ids).stream()
                .map(r -> r.getIdTurno()).collect(Collectors.toSet());
        Map<UUID, Long> ocorrencias = ocorrenciaRepository.findByIdTurnoInAndAtivoTrue(ids).stream()
                .collect(Collectors.groupingBy(OcorrenciaTurno::getIdTurno, Collectors.counting()));
        Map<UUID, List<AprTurno>> aprs = aprRepository.findByIdTurnoInAndAtivoTrue(ids).stream()
                .collect(Collectors.groupingBy(AprTurno::getIdTurno));
        List<AtividadeTurno> atividades = atividadeRepository.findByIdTurnoInAndAtivoTrue(ids);
        Map<UUID, String> numeros = new HashMap<>();
        obraRepository.findAllById(atividades.stream().map(AtividadeTurno::getIdObra).collect(Collectors.toSet()))
                .forEach(o -> numeros.put(o.getId(), o.getNumero()));
        Map<UUID, List<String>> obras = atividades.stream().collect(Collectors.groupingBy(AtividadeTurno::getIdTurno,
                Collectors.mapping(a -> numeros.get(a.getIdObra()), Collectors.toList())));
        Map<UUID, Set<UUID>> substitutos = turnoApi.buscarPapeis(ids).stream()
                .collect(Collectors.toMap(PapeisTurno::idTurno, PapeisTurno::idsSubstitutos));
        Map<UUID, CronogramaMetaFaturamento> metas = metaFaturamentoApi.cronogramas(equipes.keySet());
        Map<UUID, String> placas = new HashMap<>();
        Map<UUID, String> abertoPor = new HashMap<>();

        return turnos.stream().map(t -> {
            EquipeResponse equipe = equipes.get(t.idEquipe());
            ApontamentoExecucaoRepository.ResumoDoTurno resumo = executado.get(t.id());
            BigDecimal realizado = resumo != null ? resumo.valor().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            BigDecimal meta = metas.getOrDefault(t.idEquipe(), CronogramaMetaFaturamento.vazio()).metaDiaria(diaDe(t.dataInicio()));
            List<AprTurno> doTurno = aprs.getOrDefault(t.id(), List.of());
            return new TurnoObraResponses.Linha(t.id(), t.idEquipe(), equipe != null ? equipe.codigo() : null,
                    equipe != null && equipe.tipoEquipe() != null ? equipe.tipoEquipe().nome() : null,
                    equipe != null && equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                    placa(t, equipe, placas), t.status(), t.dataInicio(), t.dataFim(), duracao(t, agora),
                    abertoDesdeOutroDia(t, agora), t.encerradoAutomaticamente(), t.motivoEncerramento(), abertoPor(t, abertoPor),
                    t.idsColaboradores() != null ? t.idsColaboradores().size() : 0,
                    substitutos.getOrDefault(t.id(), Set.of()).size(),
                    realizado, meta, percentual(realizado, meta), bateu(realizado, meta),
                    t.status() == StatusTurno.FECHADO && !comRelatorio.contains(t.id()),
                    ocorrencias.getOrDefault(t.id(), 0L).intValue(), doTurno.size(),
                    (int) doTurno.stream().filter(a -> a.getStatus() == StatusAprTurno.RASCUNHO).count(),
                    resumo != null ? resumo.servicos() : 0,
                    obras.getOrDefault(t.id(), List.of()).stream().filter(Objects::nonNull).distinct().sorted().toList());
        }).toList();
    }

    static TurnoObraResponses.Indicadores indicadores(List<TurnoObraResponses.Linha> linhas) {
        return new TurnoObraResponses.Indicadores(linhas.size(),
                (int) linhas.stream().filter(l -> l.status() == StatusTurno.ABERTO).count(),
                linhas.stream().mapToLong(TurnoObraResponses.Linha::duracaoMinutos).sum(),
                (int) linhas.stream().filter(l -> l.metaDiaria() != null).count(),
                (int) linhas.stream().filter(TurnoObraResponses.Linha::bateuMeta).count(),
                (int) linhas.stream().filter(TurnoObraResponses.Linha::semRelatorio).count(),
                (int) linhas.stream().filter(l -> l.ocorrencias() > 0).count(),
                linhas.stream().mapToLong(TurnoObraResponses.Linha::servicos).sum(),
                linhas.stream().map(TurnoObraResponses.Linha::realizado).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static boolean atendeMeta(TurnoObraResponses.Linha linha, TurnoObraFilter.Meta meta) {
        if (meta == null) {
            return true;
        }
        return meta == TurnoObraFilter.Meta.BATEU ? linha.bateuMeta() : linha.metaDiaria() != null && !linha.bateuMeta();
    }

    private static boolean atendeObra(TurnoObraResponses.Linha linha, String obra) {
        return !StringUtils.hasText(obra) || linha.obras().stream().anyMatch(n -> n.contains(obra.trim()));
    }

    private static boolean atendeBusca(TurnoObraResponses.Linha linha, String q) {
        if (!StringUtils.hasText(q)) {
            return true;
        }
        String busca = q.trim().toLowerCase(Locale.ROOT);
        return contem(linha.codigoEquipe(), busca) || contem(linha.placa(), busca) || contem(linha.abertoPor(), busca)
                || linha.obras().stream().anyMatch(n -> contem(n, busca));
    }

    private static boolean contem(String valor, String busca) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(busca);
    }

    /** O meta e a equipe ordenam na tela porque a lista inteira já está em memória. */
    private static Comparator<TurnoObraResponses.Linha> ordenacao(Sort sort) {
        Comparator<TurnoObraResponses.Linha> comparador = null;
        for (Sort.Order ordem : sort) {
            Comparator<TurnoObraResponses.Linha> campo = switch (ordem.getProperty()) {
                case "dataFim" -> Comparator.comparing(TurnoObraResponses.Linha::dataFim, Comparator.nullsLast(Comparator.naturalOrder()));
                case "status" -> Comparator.comparing(TurnoObraResponses.Linha::status);
                case "equipe" -> Comparator.comparing(TurnoObraResponses.Linha::codigoEquipe, Comparator.nullsLast(Comparator.naturalOrder()));
                case "duracao" -> Comparator.comparing(TurnoObraResponses.Linha::duracaoMinutos);
                case "realizado" -> Comparator.comparing(TurnoObraResponses.Linha::realizado);
                case "meta" -> Comparator.comparing(TurnoObraResponses.Linha::percentualMeta, Comparator.nullsFirst(Comparator.naturalOrder()));
                case "dataInicio" -> Comparator.comparing(TurnoObraResponses.Linha::dataInicio);
                default -> null;
            };
            if (campo != null) {
                campo = ordem.isDescending() ? campo.reversed() : campo;
                comparador = comparador == null ? campo : comparador.thenComparing(campo);
            }
        }
        return comparador != null ? comparador : Comparator.comparing(TurnoObraResponses.Linha::dataInicio).reversed();
    }

    private static List<TurnoObraResponses.Linha> pagina(List<TurnoObraResponses.Linha> linhas, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return linhas;
        }
        int de = Math.min((int) pageable.getOffset(), linhas.size());
        return new ArrayList<>(linhas.subList(de, Math.min(de + pageable.getPageSize(), linhas.size())));
    }

    private static Paged paginacao(int total, Pageable pageable) {
        int tamanho = pageable.isPaged() ? pageable.getPageSize() : Math.max(total, 1);
        return new Paged(tamanho, pageable.isPaged() ? pageable.getPageNumber() : 0, total, (int) Math.ceil((double) total / tamanho));
    }

    private List<EquipeResponse> equipesDeObras() {
        Set<UUID> processos = Set.copyOf(obraRepository.listarProcessosComObras());
        return equipeApi.listarEquipesAtivas().stream()
                .filter(e -> e.processo() != null && processos.contains(e.processo().id()))
                .toList();
    }

    private String placa(TurnoResponse turno, EquipeResponse equipe, Map<UUID, String> placas) {
        VeiculoResponse daEquipe = equipe != null ? equipe.veiculo() : null;
        if (turno.idVeiculo() == null || (daEquipe != null && turno.idVeiculo().equals(daEquipe.id()))) {
            return daEquipe != null ? daEquipe.placa() : null;
        }
        return placas.computeIfAbsent(turno.idVeiculo(), id -> veiculoApi.buscarPorIdResponse(id).map(VeiculoResponse::placa).orElse(null));
    }

    private String abertoPor(TurnoResponse turno, Map<UUID, String> nomes) {
        if (turno.idUsuarioAbertura() == null) {
            return null;
        }
        return nomes.computeIfAbsent(turno.idUsuarioAbertura(), id -> colaboradorApi.buscarNomeCompletoPorUsuario(id).orElse(null));
    }

    private static TurnoObraResponses.ProgramacaoDoTurno paraProgramacao(Programacao p, Obra obra, LocalDate hoje, SituacaoLiberacao liberacao) {
        List<ProgramacaoItem> itens = p.getItensAtivos();
        return new TurnoObraResponses.ProgramacaoDoTurno(p.getId(), p.getNumero(), p.getIdObra(),
                obra != null ? obra.getNumero() : null, obra != null ? obra.getTitulo() : null, p.getDescricao(),
                p.getDataInicio(), p.getDataFim(), p.situacaoEm(hoje), p.valorTotal().setScale(2, RoundingMode.HALF_UP),
                itens.size(), (int) itens.stream().filter(v -> v.getItemPlanejado().concluido()).count(), liberacao);
    }

    private static TurnoObraResponses.AtividadeDoTurno paraAtividade(AtividadeTurno a, Obra obra, Long numero, SituacaoLiberacao liberacao) {
        return new TurnoObraResponses.AtividadeDoTurno(a.getId(), a.getIdObra(), obra != null ? obra.getNumero() : null,
                obra != null ? obra.getTitulo() : null, a.getIdProgramacao(), numero, a.isProgramada(), a.getObservacao(), liberacao);
    }

    private static TurnoObraResponses.ServicoApontado paraServico(ApontamentoExecucao a, Obra obra, Map<UUID, String> locais) {
        ItemPlanejado item = a.getItemPlanejado();
        KitAplicado kit = item.getKitAplicado();
        return new TurnoObraResponses.ServicoApontado(a.getId(), a.getData(), a.getIdProgramacao(), a.getIdObra(),
                obra != null ? obra.getNumero() : null, locais.get(kit.getIdPonto() != null ? kit.getIdPonto() : kit.getIdVao()),
                item.getTipo(), item.getCodigo(), item.getDescricao(), item.getUnidade(), a.getQuantidade(), a.getExcedente(),
                a.getValorTotal(), a.getJustificativa(), a.getUsuarioCriacao(), a.getDataCriacao(),
                item.getStatus() != StatusItemPlanejado.MEDIDO);
    }

    private static TurnoObraResponses.Foto paraFoto(AnexoTurno a) {
        return new TurnoObraResponses.Foto(a.getId(), a.getNomeArquivo(), a.getContentType(), a.getTamanhoBytes());
    }

    private static long duracao(TurnoResponse turno, OffsetDateTime agora) {
        OffsetDateTime fim = turno.dataFim() != null ? turno.dataFim() : agora;
        return Math.max(0, Duration.between(turno.dataInicio(), fim).toMinutes());
    }

    private static boolean abertoDesdeOutroDia(TurnoResponse turno, OffsetDateTime agora) {
        return turno.status() == StatusTurno.ABERTO && diaDe(turno.dataInicio()).isBefore(agora.toLocalDate());
    }

    private static LocalDate diaUtilAnterior(LocalDate dia) {
        LocalDate anterior = dia.minusDays(1);
        for (int i = 0; i < 10 && !JanelaMetaFaturamento.diaUtil(anterior); i++) {
            anterior = anterior.minusDays(1);
        }
        return anterior;
    }

    static boolean bateu(BigDecimal realizado, BigDecimal meta) {
        return meta != null && meta.signum() > 0 && realizado.compareTo(meta) >= 0;
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal meta) {
        return meta == null || meta.signum() == 0 ? null : parte.multiply(CEM).divide(meta, 1, RoundingMode.HALF_UP);
    }

    private static LocalDate diaDe(OffsetDateTime instante) {
        return instante.atZoneSameInstant(ApontamentoExecucaoUseCases.FUSO).toLocalDate();
    }

    private static OffsetDateTime inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(ApontamentoExecucaoUseCases.FUSO).toOffsetDateTime();
    }
}
