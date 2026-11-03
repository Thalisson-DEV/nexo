package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses.Conflito;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses.EquipeProjetada;
import com.nexo.app.modules.obras.application.usecases.DeslocamentoObraService.Deslocamento;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository.ValorProgramado;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.JanelaMetaFaturamento;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Equipes que podem receber a programação, com capacidade diária e meta do período. Tudo é calculado na leitura:
 * jornada e plataforma vêm do cadastro da equipe, o deslocamento sai da base da equipe e a meta do cadastro de metas.
 */
@Component
@RequiredArgsConstructor
class ProjecaoEquipesObra {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final EquipeApi equipeApi;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final ProgramacaoRepository programacaoRepository;
    private final DeslocamentoObraService deslocamentoService;
    private final ObraRepository obraRepository;

    /**
     * Só equipes ativas do processo da obra, as mais desimpedidas primeiro: sem conflito, menos ocupadas e mais perto.
     * A programação em {@code idIgnorar} (a que está sendo reprogramada) não conta como ocupação.
     */
    List<EquipeProjetada> projetar(Obra obra, LocalDate inicio, LocalDate fim, UUID idBase, UUID idTipoEquipe,
                                   BigDecimal valorSelecao, UUID idIgnorar) {
        List<EquipeResponse> equipes = equipeApi.listarEquipesAtivas().stream()
                .filter(e -> e.processo() != null && obra.getIdProcesso().equals(e.processo().id()))
                .filter(e -> idBase == null || (e.baseOperacional() != null && idBase.equals(e.baseOperacional().id())))
                .filter(e -> idTipoEquipe == null || (e.tipoEquipe() != null && idTipoEquipe.equals(e.tipoEquipe().id())))
                .sorted(Comparator.comparing(EquipeResponse::codigo))
                .toList();
        if (equipes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = equipes.stream().map(EquipeResponse::id).toList();
        Map<UUID, CronogramaMetaFaturamento> metas = metaFaturamentoApi.cronogramas(ids);
        List<ValorProgramado> programacoes = programacaoRepository.somarNoPeriodo(ids, inicio, fim).stream()
                .filter(p -> !p.idProgramacao().equals(idIgnorar))
                .toList();
        Map<UUID, BigDecimal> programado = somarPorEquipe(programacoes, inicio, fim);
        Map<UUID, List<Conflito>> conflitos = conflitos(programacoes);
        Map<UUID, Optional<Deslocamento>> deslocamentos = new HashMap<>();
        int diasUteis = JanelaMetaFaturamento.diasUteis(inicio, fim);

        Comparator<EquipeProjetada> maisDesimpedida = Comparator
                .<EquipeProjetada, Boolean>comparing(e -> !e.conflitos().isEmpty())
                .thenComparing(EquipeProjetada::valorProgramadoPeriodo)
                .thenComparing(EquipeProjetada::tempoDeslocamentoMin, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EquipeProjetada::codigo);
        return equipes.stream().map(e -> {
            Optional<Deslocamento> deslocamento = e.baseOperacional() == null
                    ? Optional.empty()
                    : deslocamentos.computeIfAbsent(e.baseOperacional().id(), id -> deslocamentoService.estimarDe(e.baseOperacional(), obra));
            CronogramaMetaFaturamento cronograma = metas.getOrDefault(e.id(), CronogramaMetaFaturamento.vazio());
            BigDecimal metaPeriodo = cronograma.metaProporcional(inicio, fim);
            BigDecimal jaProgramado = programado.getOrDefault(e.id(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            return new EquipeProjetada(e.id(), e.codigo(),
                    e.tipoEquipe() != null ? e.tipoEquipe().id() : null,
                    e.tipoEquipe() != null ? e.tipoEquipe().nome() : null,
                    e.baseOperacional() != null ? e.baseOperacional().id() : null,
                    e.baseOperacional() != null ? e.baseOperacional().nome() : null,
                    deslocamento.map(Deslocamento::distanciaKm).orElse(null),
                    deslocamento.map(Deslocamento::tempoMinutos).orElse(null),
                    deslocamento.map(Deslocamento::estimado).orElse(null),
                    deslocamento.isPresent() ? null : obra.possuiCoordenadas() ? "Base sem coordenadas" : "Obra sem coordenadas",
                    e.jornadaEfetiva(), e.plataformaEfetiva(),
                    capacidadeMinutos(e.jornadaEfetiva(), e.plataformaEfetiva(), deslocamento.map(Deslocamento::tempoMinutos).orElse(null)),
                    diasUteis,
                    metaPeriodo != null && diasUteis > 0 ? metaPeriodo.divide(BigDecimal.valueOf(diasUteis), 2, RoundingMode.HALF_UP) : null,
                    metaPeriodo, jaProgramado, valorSelecao,
                    percentual(jaProgramado, metaPeriodo),
                    percentual(jaProgramado.add(valorSelecao), metaPeriodo),
                    conflitos.getOrDefault(e.id(), List.of()));
        }).sorted(maisDesimpedida).toList();
    }

    /** Jornada − plataforma − deslocamento ida e volta; nulo sem jornada cadastrada. */
    static Integer capacidadeMinutos(Integer jornada, Integer plataforma, Integer deslocamentoIda) {
        if (jornada == null || plataforma == null) {
            return null;
        }
        return jornada - plataforma - 2 * (deslocamentoIda != null ? deslocamentoIda : 0);
    }

    Map<UUID, BigDecimal> programadoNoPeriodo(List<UUID> equipes, LocalDate inicio, LocalDate fim) {
        return equipes.isEmpty() ? Map.of() : somarPorEquipe(programacaoRepository.somarNoPeriodo(equipes, inicio, fim), inicio, fim);
    }

    private static Map<UUID, BigDecimal> somarPorEquipe(List<ValorProgramado> programacoes, LocalDate inicio, LocalDate fim) {
        Map<UUID, BigDecimal> somas = new HashMap<>();
        for (ValorProgramado p : programacoes) {
            somas.merge(p.idEquipe(), parteNoPeriodo(p, inicio, fim), BigDecimal::add);
        }
        return somas;
    }

    private Map<UUID, List<Conflito>> conflitos(List<ValorProgramado> programacoes) {
        Map<UUID, String> numeros = new HashMap<>();
        obraRepository.findAllById(programacoes.stream().map(ValorProgramado::idObra).distinct().toList())
                .forEach(o -> numeros.put(o.getId(), o.getNumero()));
        Map<UUID, List<Conflito>> porEquipe = new HashMap<>();
        for (ValorProgramado p : programacoes) {
            porEquipe.computeIfAbsent(p.idEquipe(), id -> new ArrayList<>())
                    .add(new Conflito(p.numero(), numeros.get(p.idObra()), p.inicio(), p.fim(), p.turno()));
        }
        return porEquipe;
    }

    /** Programação que passa do período conta só pelos dias úteis que caem nele. */
    static BigDecimal parteNoPeriodo(ValorProgramado p, LocalDate inicio, LocalDate fim) {
        int total = JanelaMetaFaturamento.diasUteis(p.inicio(), p.fim());
        LocalDate de = p.inicio().isBefore(inicio) ? inicio : p.inicio();
        LocalDate ate = p.fim().isAfter(fim) ? fim : p.fim();
        int noPeriodo = JanelaMetaFaturamento.diasUteis(de, ate);
        if (total == 0) {
            // Programação só em fim de semana ou feriado: conta inteira no período que a contém.
            return p.valor();
        }
        return p.valor().multiply(BigDecimal.valueOf(noPeriodo)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal meta) {
        return meta == null || meta.signum() == 0 ? null : parte.multiply(CEM).divide(meta, 1, RoundingMode.HALF_UP);
    }
}
