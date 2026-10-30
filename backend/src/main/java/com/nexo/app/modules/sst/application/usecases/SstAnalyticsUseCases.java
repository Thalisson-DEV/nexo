package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.sst.SstAnalyticsApi;
import com.nexo.app.modules.sst.application.dto.ApuracaoMetaPeriodo;
import com.nexo.app.modules.sst.application.dto.ContagemPorTipoEvento;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SstAnalyticsUseCases implements SstAnalyticsApi {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_MESES = 60;
    private static final int MAX_RANKING = 100;

    private final InspecaoSegurancaRepository inspecaoRepository;
    private final EventoSstRepository eventoRepository;
    private final ApurarMetasPeriodoUseCase apurarMetas;
    private final EquipeApi equipeApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    @Override
    public MediaMensalInspecoes calcularMediaMensalInspecoes(LocalDate mesReferencia, int quantidadeMeses) {
        LocalDate referencia = mesReferencia == null ? LocalDate.now(ZONE_ID) : mesReferencia;
        int meses = validarLimite(quantidadeMeses, 1, MAX_MESES, "quantidade de meses");
        LocalDate mesFinal = referencia.withDayOfMonth(1);
        LocalDate mesInicial = mesFinal.minusMonths(meses - 1L);
        OffsetDateTime inicio = mesInicial.atStartOfDay(ZONE_ID).toOffsetDateTime();
        OffsetDateTime fim = mesFinal.plusMonths(1).atStartOfDay(ZONE_ID).toOffsetDateTime();

        long total = inspecaoRepository.countAtivasNaoCanceladasNoPeriodo(inicio, fim);
        BigDecimal media = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(meses), 2, RoundingMode.HALF_UP);

        return new MediaMensalInspecoes(
                "sst.inspecoes.media_mensal",
                "1.0.0",
                mesInicial,
                mesFinal,
                meses,
                total,
                media,
                OffsetDateTime.now(ZONE_ID));
    }

    @Override
    public RankingEquipesDesvios calcularRankingEquipesDesvios(
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            int limite) {
        validarPeriodo(inicioInclusivo, fimExclusivo);
        int limiteSeguro = validarLimite(limite, 1, MAX_RANKING, "limite");

        List<EquipeDesvios> equipes = inspecaoRepository
                .agruparDesviosPorEquipe(inicioInclusivo, fimExclusivo)
                .stream()
                .map(row -> new EquipeDesvios(
                        row.getIdEquipe(),
                        nomeEquipe(row),
                        row.getTotalDesvios(),
                        row.getTotalInspecoes(),
                        taxaPorCem(row.getTotalDesvios(), row.getTotalInspecoes())))
                .sorted(Comparator.comparingLong(EquipeDesvios::totalDesvios).reversed()
                        .thenComparing(EquipeDesvios::nomeEquipe))
                .limit(limiteSeguro)
                .toList();

        long amostra = equipes.stream().mapToLong(EquipeDesvios::totalInspecoes).sum();
        return new RankingEquipesDesvios(
                "sst.desvios.ranking_equipes",
                "1.0.0",
                inicioInclusivo,
                fimExclusivo,
                equipes,
                amostra,
                OffsetDateTime.now(ZONE_ID));
    }

    @Override
    public AtingimentoMetasInspecoes calcularAtingimentoMetasInspecoes(
            LocalDate inicioInclusivo, LocalDate fimInclusivo, int limite) {
        LocalDate fim = fimInclusivo == null ? LocalDate.now(ZONE_ID) : fimInclusivo;
        LocalDate inicio = inicioInclusivo == null ? fim.withDayOfMonth(1) : inicioInclusivo;
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Periodo invalido: inicio deve ser anterior ou igual ao fim");
        }
        int limiteSeguro = validarLimite(limite, 1, MAX_RANKING, "limite");

        ApuracaoMetaPeriodo apuracao = apurarMetas.apurar(inicio, fim, null, null, null);
        OffsetDateTime inicioInstante = inicio.atStartOfDay(ZONE_ID).toOffsetDateTime();
        OffsetDateTime fimInstante = fim.plusDays(1).atStartOfDay(ZONE_ID).toOffsetDateTime();

        Map<UUID, Long> realizadoPorInspetor = contarPorInspetor(apuracao, inicioInstante, fimInstante);
        long totalRealizado = inspecaoRepository.countAtivasNaoCanceladasNoPeriodo(inicioInstante, fimInstante);

        List<InspetorMeta> inspetores = apuracao.metaPorInspetor().entrySet().stream()
                .map(entrada -> {
                    BigDecimal meta = entrada.getValue().setScale(2, RoundingMode.HALF_UP);
                    long realizado = realizadoPorInspetor.getOrDefault(entrada.getKey(), 0L);
                    return new InspetorMeta(entrada.getKey(), nomeInspetor(entrada.getKey()), meta, realizado,
                            percentual(BigDecimal.valueOf(realizado), meta),
                            BigDecimal.valueOf(realizado).compareTo(meta) >= 0);
                })
                .sorted(Comparator.comparing(InspetorMeta::percentualAtingimento)
                        .thenComparing(inspetor -> inspetor.nomeInspetor() == null ? "" : inspetor.nomeInspetor()))
                .limit(limiteSeguro)
                .toList();

        BigDecimal meta = apuracao.metaTotal().setScale(2, RoundingMode.HALF_UP);
        return new AtingimentoMetasInspecoes(
                "sst.inspecoes.atingimento_metas", "1.0.0", inicio, fim,
                apuracao.possuiMeta(), meta, totalRealizado,
                percentual(BigDecimal.valueOf(totalRealizado), meta),
                apuracao.colaboradoresComMeta(),
                inspetores.stream().filter(InspetorMeta::atingiuMeta).count(),
                inspetores, OffsetDateTime.now(ZONE_ID));
    }

    @Override
    public CoberturaColaboradores calcularCoberturaColaboradores(
            OffsetDateTime inicioInclusivo, OffsetDateTime fimExclusivo) {
        validarPeriodo(inicioInclusivo, fimExclusivo);

        long inspecionados = inspecaoRepository
                .contarColaboradoresInspecionadosNoPeriodo(inicioInclusivo, fimExclusivo);
        long ativos = colaboradorApi.contarAtivos(null, null);
        long inspecoes = inspecaoRepository.countAtivasNaoCanceladasNoPeriodo(inicioInclusivo, fimExclusivo);

        return new CoberturaColaboradores(
                "sst.colaboradores.cobertura_inspecoes", "1.0.0",
                inicioInclusivo, fimExclusivo, inspecionados, ativos,
                percentual(BigDecimal.valueOf(inspecionados), BigDecimal.valueOf(ativos)),
                inspecoes, OffsetDateTime.now(ZONE_ID));
    }

    @Override
    public Acidentalidade calcularAcidentalidade(OffsetDateTime inicioInclusivo, OffsetDateTime fimExclusivo) {
        validarPeriodo(inicioInclusivo, fimExclusivo);

        List<ContagemPorTipoEvento> contagens = eventoRepository.agruparPorTipoNoPeriodo(
                StatusEventoSst.CANCELADO, inicioInclusivo, fimExclusivo);

        long total = contagens.stream().mapToLong(ContagemPorTipoEvento::total).sum();
        long comLesao = contagens.stream().filter(c -> c.tipo().implicaLesao())
                .mapToLong(ContagemPorTipoEvento::total).sum();
        long diasPerdidos = contagens.stream().mapToLong(ContagemPorTipoEvento::diasPerdidos).sum();
        long comCat = contagens.stream().mapToLong(ContagemPorTipoEvento::comCat).sum();

        // Todos os tipos aparecem, inclusive zerados: "não houve acidente com lesão" é
        // uma resposta, e um tipo ausente da lista pareceria falta de dado.
        List<EventosPorTipo> porTipo = Arrays.stream(TipoEventoSst.values())
                .map(tipo -> contagens.stream().filter(c -> c.tipo() == tipo).findFirst()
                        .map(c -> new EventosPorTipo(tipo.name(), tipo.getRotulo(), c.total(),
                                c.diasPerdidos(), c.comCat()))
                        .orElseGet(() -> new EventosPorTipo(tipo.name(), tipo.getRotulo(), 0, 0, 0)))
                .toList();

        return new Acidentalidade(
                "sst.eventos.acidentalidade", "1.0.0", inicioInclusivo, fimExclusivo,
                total, comLesao, diasPerdidos, comCat, porTipo,
                OffsetDateTime.now(ZONE_ID));
    }

    private Map<UUID, Long> contarPorInspetor(
            ApuracaoMetaPeriodo apuracao, OffsetDateTime inicio, OffsetDateTime fim) {
        if (apuracao.metaPorInspetor().isEmpty()) {
            return Map.of();
        }
        return inspecaoRepository
                .listarRealizacoesPorInspetor(apuracao.metaPorInspetor().keySet(), inicio, fim)
                .stream()
                .collect(Collectors.groupingBy(
                        InspecaoSegurancaRepository.RealizacaoInspetorProjection::getIdInspetor,
                        Collectors.counting()));
    }

    private String nomeInspetor(UUID idInspetor) {
        return colaboradorApi.buscarNomeCompletoPorUsuario(idInspetor)
                .or(() -> usuarioApi.buscarNomePorId(idInspetor))
                .orElse(idInspetor.toString());
    }

    private BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private String nomeEquipe(InspecaoSegurancaRepository.EquipeDesviosProjection row) {
        return equipeApi.buscarPorIdResponse(row.getIdEquipe())
                .map(equipe -> equipe.codigo())
                .filter(codigo -> !codigo.isBlank())
                .orElse(row.getIdEquipe().toString());
    }

    private BigDecimal taxaPorCem(long desvios, long inspecoes) {
        if (inspecoes == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(desvios)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(inspecoes), 2, RoundingMode.HALF_UP);
    }

    private int validarLimite(int valor, int minimo, int maximo, String campo) {
        if (valor < minimo || valor > maximo) {
            throw new IllegalArgumentException(campo + " deve estar entre " + minimo + " e " + maximo);
        }
        return valor;
    }

    private void validarPeriodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new IllegalArgumentException("Periodo invalido: inicio deve ser anterior ao fim");
        }
    }
}
