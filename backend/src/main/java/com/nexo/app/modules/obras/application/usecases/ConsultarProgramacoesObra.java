package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.EventoProgramacaoFilter;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.domain.EventoProgramacao;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.SituacaoProgramacao;
import com.nexo.app.modules.obras.domain.StatusProgramacao;
import com.nexo.app.modules.obras.infrastructure.persistence.EventoProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.EventoProgramacaoSpecification;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Leituras transversais da programação: linha do tempo por equipe e histórico de mudanças. */
@Service
@RequiredArgsConstructor
public class ConsultarProgramacoesObra {

    static final long PERIODO_MAXIMO_DIAS = 186;
    private static final int LIMITE_OBRAS_A_PROGRAMAR = 50;

    private final ProgramacaoRepository repository;
    private final EventoProgramacaoRepository eventoRepository;
    private final ObraRepository obraRepository;
    private final EquipeApi equipeApi;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final ProjecaoEquipesObra projecao;
    private final ResumoPlanejamentoObras resumos;
    private final MunicipioApi municipioApi;

    /**
     * Equipes do período por código. Entram também as sem programação, dos processos que têm obras: é assim que se vê
     * quem está livre. Equipe de outro processo só aparece se tiver programação.
     */
    @Transactional(readOnly = true)
    public ProgramacaoResponses.Gantt gantt(LocalDate inicio, LocalDate fim, UUID idBase, UUID idTipoEquipe, UUID idEquipe) {
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("O fim do período não pode ser antes do início.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) > PERIODO_MAXIMO_DIAS) {
            throw new InvarianteVioladaException("O período do Gantt deve ter até " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        List<Programacao> programacoes = repository.listarNoPeriodoComItens(inicio, fim);
        Set<UUID> comProgramacao = programacoes.stream().map(Programacao::getIdEquipe).collect(Collectors.toSet());
        Set<UUID> processos = Set.copyOf(obraRepository.listarProcessosComObras());
        List<EquipeResponse> todas = new ArrayList<>(equipeApi.listarEquipesAtivas().stream()
                .filter(e -> e.processo() != null && processos.contains(e.processo().id()))
                .toList());
        Set<UUID> jaListadas = todas.stream().map(EquipeResponse::id).collect(Collectors.toSet());
        List<UUID> faltantes = comProgramacao.stream().filter(id -> !jaListadas.contains(id)).toList();
        if (!faltantes.isEmpty()) {
            todas.addAll(equipeApi.buscarPorIdsResponse(faltantes));
        }
        Map<UUID, EquipeResponse> equipes = todas.stream()
                .filter(e -> idEquipe == null || idEquipe.equals(e.id()))
                .filter(e -> idBase == null || (e.baseOperacional() != null && idBase.equals(e.baseOperacional().id())))
                .filter(e -> idTipoEquipe == null || (e.tipoEquipe() != null && idTipoEquipe.equals(e.tipoEquipe().id())))
                .collect(Collectors.toMap(EquipeResponse::id, e -> e));
        LocalDate hoje = LocalDate.now();
        List<Programacao> atrasadasAntes = repository.listarAbertasTerminadasAntes(inicio.isBefore(hoje) ? inicio : hoje).stream()
                .filter(p -> equipes.containsKey(p.getIdEquipe()))
                .filter(p -> p.situacaoEm(hoje) == SituacaoProgramacao.ATRASADA)
                .sorted(Comparator.comparing(Programacao::getDataFim))
                .toList();
        Map<UUID, Obra> obras = new HashMap<>();
        obraRepository.findAllById(Stream.concat(programacoes.stream(), atrasadasAntes.stream()).map(Programacao::getIdObra).collect(Collectors.toSet()))
                .forEach(o -> obras.put(o.getId(), o));

        Map<UUID, String> municipios = new HashMap<>();
        obras.values().stream().map(Obra::getIdMunicipio).distinct().forEach(id ->
                municipios.put(id, municipioApi.buscarPorIdResponse(id).map(MunicipioResponse::nome).orElse(null)));
        Map<UUID, String> codigos = new HashMap<>();
        todas.forEach(e -> codigos.put(e.id(), e.codigo()));
        Map<UUID, List<Programacao>> porEquipe = programacoes.stream()
                .filter(p -> equipes.containsKey(p.getIdEquipe()))
                .collect(Collectors.groupingBy(Programacao::getIdEquipe));
        List<UUID> ids = List.copyOf(equipes.keySet());
        Map<UUID, BigDecimal> programado = projecao.programadoNoPeriodo(ids, inicio, fim);
        Map<UUID, CronogramaMetaFaturamento> metas = ids.isEmpty() ? Map.of() : metaFaturamentoApi.cronogramas(ids);
        List<ProgramacaoResponses.LinhaGantt> linhas = equipes.values().stream()
                .map(equipe -> {
                    List<ProgramacaoResponses.BarraGantt> barras = porEquipe.getOrDefault(equipe.id(), List.of()).stream()
                            .sorted(Comparator.comparing(Programacao::getDataInicio))
                            .map(p -> barra(p, obras.get(p.getIdObra()), hoje, municipios, codigos))
                            .toList();
                    BigDecimal valor = programado.getOrDefault(equipe.id(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    CronogramaMetaFaturamento cronograma = metas.getOrDefault(equipe.id(), CronogramaMetaFaturamento.vazio());
                    return new ProgramacaoResponses.LinhaGantt(equipe.id(), equipe.codigo(),
                            equipe.tipoEquipe() != null ? equipe.tipoEquipe().nome() : null,
                            equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                            valor, ProjecaoEquipesObra.percentual(valor, cronograma.metaProporcional(inicio, fim)),
                            equipe.supervisor() != null && equipe.supervisor().colaborador() != null ? equipe.supervisor().colaborador().nomeCompleto() : null,
                            equipe.coordenador() != null && equipe.coordenador().colaborador() != null ? equipe.coordenador().colaborador().nomeCompleto() : null,
                            barras);
                })
                .sorted(Comparator.comparing(ProgramacaoResponses.LinhaGantt::codigo))
                .toList();
        return new ProgramacaoResponses.Gantt(inicio, fim, linhas,
                atrasadasAntes.stream().map(p -> barra(p, obras.get(p.getIdObra()), hoje, municipios, codigos)).toList());
    }

    /** Obras abertas com serviço por programar, as de prazo mais curto primeiro; prazo regulado manda sobre o previsto. */
    @Transactional(readOnly = true)
    public List<ProgramacaoResponses.ObraAProgramar> obrasAProgramar(UUID idBase) {
        List<Obra> obras = obraRepository.findAllById(resumos.obrasComPendencia()).stream()
                .filter(Obra::isAtivo)
                .filter(o -> !o.getStatusObra().isConcluida())
                .filter(o -> idBase == null || idBase.equals(o.getIdBaseOperacional()))
                .toList();
        Map<UUID, ProgramacaoResponses.ResumoPlanejamento> porObra = resumos.calcular(obras.stream().map(Obra::getId).toList());
        return obras.stream()
                .sorted(Comparator.comparing(ConsultarProgramacoesObra::prazoQueManda, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Obra::getNumero))
                .limit(LIMITE_OBRAS_A_PROGRAMAR)
                .map(o -> {
                    ProgramacaoResponses.ResumoPlanejamento r = porObra.get(o.getId());
                    return new ProgramacaoResponses.ObraAProgramar(o.getId(), o.getNumero(), o.getTitulo(),
                            o.getPrioridade() != null ? o.getPrioridade().name() : null, o.getDataPrevisaoFim(), o.getPrazoRegulado(),
                            r.valorPendente(), r.servicosPendentes(), r.pontosSemKit(), r.percentualProgramado());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramacaoResponses.ResumoPlanejamento> resumos(List<UUID> idsObras) {
        return List.copyOf(resumos.calcular(idsObras).values());
    }

    private static LocalDate prazoQueManda(Obra obra) {
        return obra.getPrazoRegulado() != null ? obra.getPrazoRegulado() : obra.getDataPrevisaoFim();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramacaoResponses.Evento> historico(EventoProgramacaoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(eventoRepository
                .findAll(EventoProgramacaoSpecification.filterBy(filtro), pageable)
                .map(ConsultarProgramacoesObra::paraResponse));
    }

    private static ProgramacaoResponses.BarraGantt barra(Programacao p, Obra obra, LocalDate hoje, Map<UUID, String> municipios, Map<UUID, String> codigos) {
        return new ProgramacaoResponses.BarraGantt(p.getId(), p.getNumero(), p.getIdObra(),
                obra != null ? obra.getNumero() : null, obra != null ? obra.getTitulo() : null,
                p.getDataInicio(), p.getDataFim(), p.getTurno(), p.situacaoEm(hoje),
                p.valorTotal().setScale(2, RoundingMode.HALF_UP), p.getItensAtivos().size(), p.getVersao(),
                p.getIdEquipe(), codigos.get(p.getIdEquipe()), p.isLinhaViva(), p.isDesligamento(), p.isFinalizaObra(),
                p.getSituacaoIntervencao(), obra != null ? municipios.get(obra.getIdMunicipio()) : null,
                obra != null ? obra.getPrioridade() : null);
    }

    private static ProgramacaoResponses.Evento paraResponse(EventoProgramacao e) {
        return new ProgramacaoResponses.Evento(e.getId(), e.getDataCriacao(), e.getTipo(), e.getIdProgramacao(),
                e.getNumeroProgramacao(), e.getIdObra(), e.getNumeroObra(), e.getEquipeOrigem(), e.getEquipeDestino(),
                e.getInicioOriginal(), e.getFimOriginal(), e.getInicioNovo(), e.getFimNovo(),
                e.getUsuarioCriacao(), e.getObservacao());
    }
}
