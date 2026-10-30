package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstFilter;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstResponse;
import com.nexo.app.modules.sst.application.dto.DiasSemAcidenteItem;
import com.nexo.app.modules.sst.application.dto.FaixaBacklogEventos;
import com.nexo.app.modules.sst.application.dto.PiramideAcidentalidadeItem;
import com.nexo.app.modules.sst.application.dto.RankingItem;
import com.nexo.app.modules.sst.application.dto.ResumoEventosDashboard;
import com.nexo.app.modules.sst.application.dto.SerieEventosItem;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoCat;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstSpecification;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard executivo de acidentalidade.
 *
 * <p>Consolida em memória os eventos do período, como os demais dashboards de SST —
 * adequado ao volume de gestão. A economia que o modelo permite é grande: como
 * atividade, gestor, base e equipe estão <b>snapshotados</b> no evento, os rankings
 * saem sem uma única ida ao RH, e só a classificação e o local da lesão (catálogos, que
 * são referência viva) precisam ter o nome resolvido.</p>
 *
 * <p>Duas regras valem para todos os indicadores: contam os eventos pela data da
 * <b>ocorrência</b> — não a do registro, senão um lançamento atrasado mudaria o mês
 * errado — e os <b>cancelados ficam de fora</b>, porque registro indevido que continua
 * contando faz a série mentir onde ela é usada para cobrar resultado.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardEventosSstUseCases {

    private static final int SCALE = 2;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String[] MESES_ABREVIADOS = {
            "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
    private static final int LIMITE_RANKING = 10;

    private final EventoSstRepository repository;
    private final CatalogoSstRepository catalogoRepository;
    private final DesvioSegurancaRepository desvioRepository;
    private final ProcessoNegocioApi processoApi;

    @Transactional(readOnly = true)
    public DashboardEventosSstResponse gerar(DashboardEventosSstFilter filter) {
        DashboardEventosSstFilter efetivo = filter == null ? DashboardEventosSstFilter.vazio() : filter;
        OffsetDateTime inicio = efetivo.inicio() != null ? efetivo.inicio() : inicioDoMesCorrente();
        OffsetDateTime fim = efetivo.fim() != null ? efetivo.fim() : agora();
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("O fim do período não pode ser anterior ao início.");
        }

        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(inicio.toLocalDate(), fim.toLocalDate()) + 1);
        GranularidadeDashboard granularidade = efetivo.granularidade() != null
                ? efetivo.granularidade()
                : GranularidadeDashboard.automatica(diasPeriodo);

        List<EventoSst> eventos = carregar(efetivo, inicio, fim);
        List<EventoSst> anteriores = carregar(efetivo,
                inicio.minusDays(diasPeriodo), inicio.minusNanos(1));

        // A base da pirâmide vem do outro lado do módulo: os desvios apontados nas
        // inspeções do mesmo período, sob os mesmos recortes organizacionais.
        long desvios = desvioRepository.count(
                DesvioSegurancaSpecification.geral(efetivo.paraDesvios(inicio, fim)));

        Nomes nomes = new Nomes(eventos);

        return new DashboardEventosSstResponse(
                inicio, fim, granularidade, OffsetDateTime.now(FUSO),
                resumo(eventos, anteriores, desvios, diasSemEventos()),
                serie(eventos, granularidade, inicio.toLocalDate(), fim.toLocalDate()),
                piramide(eventos, desvios),
                diasSemAcidente(efetivo, eventos),
                backlog(efetivo),
                ranking(eventos, EventoSst::getIdClassificacao, nomes::catalogo),
                ranking(eventos, EventoSst::getIdAtividade, id -> nomes.doEvento(id, EventoSst::getIdAtividade,
                        EventoSst::getAtividadeNome)),
                ranking(eventos, EventoSst::getIdGestor, id -> nomes.doEvento(id, EventoSst::getIdGestor,
                        EventoSst::getGestorNome)),
                ranking(eventos, EventoSst::getIdBaseOperacional, id -> nomes.doEvento(id,
                        EventoSst::getIdBaseOperacional, EventoSst::getBaseNome)),
                ranking(eventos, EventoSst::getIdEquipe, id -> nomes.doEvento(id, EventoSst::getIdEquipe,
                        EventoSst::getEquipeCodigo)),
                ranking(eventos, EventoSst::getIdProcesso, nomes::processo),
                ranking(eventos.stream().filter(EventoSst::isComLesao).toList(),
                        EventoSst::getIdLocalLesao, nomes::catalogo));
    }

    /**
     * Carrega os eventos ocorridos no intervalo, já sem os cancelados. O filtro passa
     * pela mesma {@code Specification} da listagem: o número do dashboard e a linha da
     * tabela vêm do mesmo lugar.
     */
    private List<EventoSst> carregar(DashboardEventosSstFilter filter, OffsetDateTime inicio, OffsetDateTime fim) {
        return repository.findAll(EventoSstSpecification.doPeriodo(filter.paraListagem(), null, inicio, fim))
                .stream()
                .filter(evento -> evento.getStatus().contaNosIndicadores())
                .toList();
    }

    // ===================================================================== //
    //  Indicadores
    // ===================================================================== //

    private ResumoEventosDashboard resumo(List<EventoSst> eventos, List<EventoSst> anteriores,
                                           long desvios, Long diasSemEventos) {
        long total = eventos.size();
        long comLesao = eventos.stream().filter(EventoSst::isComLesao).count();
        long comLesaoAnterior = anteriores.stream().filter(EventoSst::isComLesao).count();
        long comCat = eventos.stream().filter(EventoSst::isPossuiCat).count();
        long atrasadas = eventos.stream().filter(EventoSst::isComunicacaoAtrasada).count();
        long diasPerdidos = eventos.stream()
                .map(EventoSst::getDiasPerdidos).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        long abertos = eventos.stream().filter(evento -> evento.getStatus().permiteEdicao()).count();

        Map<TipoEventoSst, Long> porTipo = new EnumMap<>(TipoEventoSst.class);
        for (TipoEventoSst tipo : TipoEventoSst.values()) {
            porTipo.put(tipo, contarDoTipo(eventos, tipo));
        }

        BigDecimal mediaHoras = total == 0 ? BigDecimal.ZERO : dividir(
                BigDecimal.valueOf(eventos.stream().mapToLong(EventoSst::getHorasAteComunicacao).sum()),
                BigDecimal.valueOf(total));

        return new ResumoEventosDashboard(
                total, anteriores.size(),
                variacaoPercentual(total, anteriores.size()),
                diasSemEventos,
                porTipo,
                comLesao, comLesaoAnterior, variacaoPercentual(comLesao, comLesaoAnterior),
                total - comLesao,
                diasPerdidos,
                comCat, percentual(comCat, comLesao),
                mediaHoras,
                atrasadas, percentual(atrasadas, total),
                abertos, total - abertos,
                desvios,
                comLesao == 0 ? BigDecimal.ZERO
                        : dividir(BigDecimal.valueOf(desvios), BigDecimal.valueOf(comLesao)));
    }

    /** Indicador positivo global: independe do período e de todos os demais recortes. */
    private Long diasSemEventos() {
        LocalDate hoje = LocalDate.now(FUSO);
        return repository.buscarUltimaDataOcorrenciaValida(StatusEventoSst.CANCELADO)
                .map(ultima -> Math.max(0L, ChronoUnit.DAYS.between(
                        ultima.atZoneSameInstant(FUSO).toLocalDate(), hoje)))
                .orElse(null);
    }

    private List<SerieEventosItem> serie(List<EventoSst> eventos, GranularidadeDashboard granularidade,
                                         LocalDate primeiroDia, LocalDate ultimoDia) {
        Map<LocalDate, List<EventoSst>> porBalde = eventos.stream().collect(Collectors.groupingBy(
                evento -> granularidade.inicioDoBalde(evento.getDataOcorrencia().atZoneSameInstant(FUSO)
                        .toLocalDate())));

        List<SerieEventosItem> serie = new ArrayList<>();
        // Baldes vazios entram zerados: um gráfico que pula o mês sem acidente sugere
        // que não houve medição, quando o que houve foi ausência de evento.
        for (LocalDate balde = granularidade.inicioDoBalde(primeiroDia);
             !balde.isAfter(ultimoDia);
             balde = granularidade.proximoBalde(balde)) {

            List<EventoSst> doBalde = porBalde.getOrDefault(balde, List.of());
            serie.add(new SerieEventosItem(balde, rotulo(balde, granularidade),
                    doBalde.size(),
                    doBalde.stream().filter(EventoSst::isComLesao).count(),
                    doBalde.stream().map(EventoSst::getDiasPerdidos).filter(Objects::nonNull)
                            .mapToLong(Integer::longValue).sum()));
        }
        return serie;
    }

    /**
     * Os degraus, do topo para a base.
     *
     * <p>A base é o desvio de segurança apontado em inspeção — não um tipo de evento.
     * A empresa não registra quase-acidente como ocorrência; o que ela apura em volume
     * é a não-conformidade encontrada na inspeção, e é ela que sustenta a pirâmide.</p>
     */
    private List<PiramideAcidentalidadeItem> piramide(List<EventoSst> eventos, long desvios) {
        long comLesao = eventos.stream().filter(EventoSst::isComLesao).count();
        long totalPiramide = eventos.size() + desvios;
        long fatalidades = eventos.stream()
                .filter(evento -> evento.getTipoCat() == TipoCat.COMUNICACAO_OBITO)
                .count();
        long comAfastamento = eventos.stream()
                .filter(evento -> evento.getTipoCat() != TipoCat.COMUNICACAO_OBITO)
                .filter(EventoSst::isComLesao)
                .filter(evento -> evento.getDiasPerdidos() != null && evento.getDiasPerdidos() > 0)
                .count();
        long semAfastamento = eventos.stream()
                .filter(evento -> evento.getTipoCat() != TipoCat.COMUNICACAO_OBITO)
                .filter(EventoSst::isComLesao)
                .filter(evento -> evento.getDiasPerdidos() == null || evento.getDiasPerdidos() == 0)
                .count();
        long incidentes = eventos.stream().filter(evento -> !evento.isComLesao()).count();

        return List.of(
                degrau(PiramideAcidentalidadeItem.CHAVE_FATALIDADE, "Fatalidade", fatalidades,
                        totalPiramide, comLesao),
                degrau(PiramideAcidentalidadeItem.CHAVE_ACIDENTE_COM_AFASTAMENTO,
                        "Acidentes com afastamento", comAfastamento, totalPiramide, comLesao),
                degrau(PiramideAcidentalidadeItem.CHAVE_ACIDENTE_SEM_AFASTAMENTO,
                        "Acidentes sem afastamento", semAfastamento, totalPiramide, comLesao),
                degrau(PiramideAcidentalidadeItem.CHAVE_INCIDENTE, "Incidentes", incidentes,
                        totalPiramide, comLesao),
                degrau(PiramideAcidentalidadeItem.CHAVE_DESVIO, "Desvios", desvios,
                        totalPiramide, comLesao));
    }

    private static PiramideAcidentalidadeItem degrau(String chave, String rotulo, long total,
                                                      long totalPiramide, long comLesao) {
        return new PiramideAcidentalidadeItem(chave, rotulo, total,
                percentual(total, totalPiramide), razaoPorLesao(total, comLesao));
    }

    private static BigDecimal razaoPorLesao(long quantidade, long comLesao) {
        return comLesao == 0 ? BigDecimal.ZERO
                : dividir(BigDecimal.valueOf(quantidade), BigDecimal.valueOf(comLesao));
    }

    /**
     * Dias sem evento com lesão por base.
     *
     * <p>Este é o único indicador que <b>ignora o período</b>: "há quanto tempo esta base
     * não tem acidente" só se responde contra a última ocorrência de fato. O que ele não
     * pode ignorar são os <b>recortes</b> — filtrar por uma base e receber a lista inteira
     * era o defeito da primeira versão. Por isso a consulta reusa a mesma
     * {@code Specification} da listagem, apenas sem a janela de datas.</p>
     *
     * <p>O filtro de <b>tipo</b> é deliberadamente descartado aqui: o painel é sobre
     * lesão por definição, e respeitar um recorte de "quase-acidente" esvaziaria a tela
     * sem que ninguém entendesse por quê.</p>
     */
    private List<DiasSemAcidenteItem> diasSemAcidente(DashboardEventosSstFilter filtro, List<EventoSst> doPeriodo) {
        List<EventoSst> comLesaoNaSerie = repository
                .findAll(EventoSstSpecification.geral(filtro.paraListagemComTipos(tiposComLesao()), null))
                .stream()
                .filter(evento -> evento.getStatus().contaNosIndicadores())
                .toList();

        Map<UUID, Long> comLesaoNoPeriodo = doPeriodo.stream()
                .filter(EventoSst::isComLesao)
                .collect(Collectors.groupingBy(EventoSst::getIdBaseOperacional, Collectors.counting()));

        LocalDate hoje = LocalDate.now(FUSO);
        Map<UUID, EventoSst> ultimaPorBase = new LinkedHashMap<>();
        for (EventoSst evento : comLesaoNaSerie) {
            ultimaPorBase.merge(evento.getIdBaseOperacional(), evento,
                    (atual, candidato) -> candidato.getDataOcorrencia().isAfter(atual.getDataOcorrencia())
                            ? candidato : atual);
        }

        Map<UUID, DiasSemAcidenteItem> porBase = new LinkedHashMap<>();
        ultimaPorBase.forEach((idBase, ultimo) -> porBase.put(idBase, new DiasSemAcidenteItem(
                idBase, ultimo.getBaseNome(), ultimo.getDataOcorrencia(),
                ChronoUnit.DAYS.between(ultimo.getDataOcorrencia().atZoneSameInstant(FUSO).toLocalDate(), hoje),
                comLesaoNoPeriodo.getOrDefault(idBase, 0L))));

        // Bases que aparecem no período mas nunca tiveram evento com lesão entram com
        // "sem ocorrência": omiti-las esconderia justamente as que estão bem.
        for (EventoSst evento : doPeriodo) {
            porBase.computeIfAbsent(evento.getIdBaseOperacional(), id -> new DiasSemAcidenteItem(
                    id, evento.getBaseNome(), null, null, 0L));
        }

        // Menor número de dias primeiro: quem acabou de ter acidente é quem precisa de olho.
        return porBase.values().stream()
                .sorted(Comparator.comparing(DiasSemAcidenteItem::diasSemAcidente,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Eventos ainda em aberto, por quanto tempo faz que ocorreram.
     *
     * <p>Também <b>fora do período</b>, e essa é a correção que mais muda a leitura: um
     * acidente de três meses atrás ainda sem tratativa é exatamente o que a fila precisa
     * mostrar, e ele desaparecia quando o filtro era o mês corrente. Pela mesma razão a
     * idade é contada até <b>hoje</b>, e não até o fim do período consultado — senão a
     * faixa "mais de 30 dias" ficava permanentemente vazia em um filtro mensal.</p>
     */
    private List<FaixaBacklogEventos> backlog(DashboardEventosSstFilter filtro) {
        List<EventoSst> abertos = repository
                .findAll(EventoSstSpecification.geral(filtro.paraListagemAbertos(), null))
                .stream()
                .filter(evento -> evento.getStatus().permiteEdicao())
                .toList();

        LocalDate hoje = LocalDate.now(FUSO);
        return List.of(
                faixa("Até 7 dias", 0, 7, abertos, hoje),
                faixa("8 a 15 dias", 8, 15, abertos, hoje),
                faixa("16 a 30 dias", 16, 30, abertos, hoje),
                faixa("Mais de 30 dias", 31, null, abertos, hoje));
    }

    private FaixaBacklogEventos faixa(String rotulo, int minimo, Integer maximo,
                                      List<EventoSst> abertos, LocalDate referencia) {
        List<EventoSst> daFaixa = abertos.stream()
                .filter(evento -> {
                    long idade = ChronoUnit.DAYS.between(
                            evento.getDataOcorrencia().atZoneSameInstant(FUSO).toLocalDate(), referencia);
                    return idade >= minimo && (maximo == null || idade <= maximo);
                })
                .toList();

        Map<StatusEventoSst, Long> porStatus = daFaixa.stream()
                .collect(Collectors.groupingBy(EventoSst::getStatus, Collectors.counting()));

        return new FaixaBacklogEventos(rotulo, minimo, maximo, daFaixa.size(), porStatus);
    }

    private List<RankingItem> ranking(List<EventoSst> eventos, Function<EventoSst, UUID> dimensao,
                                      Function<UUID, String> nomeDe) {
        long total = eventos.size();
        Map<UUID, Long> contagem = eventos.stream()
                .map(dimensao).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return contagem.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(LIMITE_RANKING)
                .map(entrada -> new RankingItem(entrada.getKey(), nomeDe.apply(entrada.getKey()),
                        entrada.getValue(), percentual(entrada.getValue(), total)))
                .toList();
    }

    // ===================================================================== //
    //  Nomes
    // ===================================================================== //

    /**
     * Resolve os rótulos dos rankings. A maior parte já está no próprio evento (é
     * snapshot); só catálogo e processo custam consulta, e ambas são feitas uma vez.
     */
    private final class Nomes {

        private final List<EventoSst> eventos;
        private final Map<UUID, String> catalogos = new HashMap<>();
        private final Map<UUID, String> processos = new HashMap<>();

        private Nomes(List<EventoSst> eventos) {
            this.eventos = eventos;

            Set<UUID> idsCatalogo = eventos.stream()
                    .flatMap(evento -> java.util.stream.Stream.of(
                            evento.getIdClassificacao(), evento.getIdLocalLesao()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!idsCatalogo.isEmpty()) {
                catalogoRepository.findAllById(idsCatalogo)
                        .forEach(entrada -> catalogos.put(entrada.getId(), entrada.getNome()));
            }

            eventos.stream().map(EventoSst::getIdProcesso).filter(Objects::nonNull).distinct()
                    .forEach(id -> processos.put(id, processoApi.buscarPorIdResponse(id)
                            .map(ProcessoNegocioResponse::nome).orElse(null)));
        }

        private String catalogo(UUID id) {
            return catalogos.getOrDefault(id, "Não informado");
        }

        private String processo(UUID id) {
            String nome = processos.get(id);
            return nome == null ? "Não informado" : nome;
        }

        /** Rótulo que já veio copiado no evento — nenhuma consulta envolvida. */
        private String doEvento(UUID id, Function<EventoSst, UUID> chave, Function<EventoSst, String> rotulo) {
            return eventos.stream()
                    .filter(evento -> id.equals(chave.apply(evento)))
                    .map(rotulo).filter(Objects::nonNull)
                    .findFirst().orElse("Não informado");
        }
    }

    // ===================================================================== //
    //  Cálculo
    // ===================================================================== //

    private static long contarDoTipo(List<EventoSst> eventos, TipoEventoSst tipo) {
        return eventos.stream().filter(evento -> evento.getTipo() == tipo).count();
    }

    private static Set<TipoEventoSst> tiposComLesao() {
        return Arrays.stream(TipoEventoSst.values())
                .filter(TipoEventoSst::implicaLesao)
                .collect(Collectors.toSet());
    }

    private static BigDecimal percentual(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(parte).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal variacaoPercentual(long atual, long anterior) {
        if (anterior == 0) {
            return atual == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100).setScale(SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(atual - anterior).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(anterior), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal dividir(BigDecimal dividendo, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return dividendo.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    private static String rotulo(LocalDate balde, GranularidadeDashboard granularidade) {
        return switch (granularidade) {
            case DIA -> String.format("%02d/%02d", balde.getDayOfMonth(), balde.getMonthValue());
            case MES -> MESES_ABREVIADOS[balde.getMonthValue() - 1] + "/" + (balde.getYear() % 100);
            case ANO -> String.valueOf(balde.getYear());
        };
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(FUSO);
    }

    private static OffsetDateTime inicioDoMesCorrente() {
        return LocalDate.now(FUSO).withDayOfMonth(1).atTime(LocalTime.MIN).atZone(FUSO).toOffsetDateTime();
    }
}
