package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetaFilter;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetaItem;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetasResponse;
import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.JanelaMeta;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.modules.sst.domain.StatusMeta;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.application.SelecaoMultipla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Indicador de cumprimento das metas de inspeção: para cada colaborador alcançado por
 * alguma meta vigente, compara a quantidade exigida na janela de apuração com o que ele
 * de fato lançou como inspetor no mesmo intervalo.
 *
 * <p>Resolução da meta aplicável: a meta individual do colaborador prevalece sobre a
 * meta da função que ele exerce.</p>
 *
 * <p>Os recortes do filtro (função, colaborador, base operacional, centro de custo,
 * processo e situação) são aplicados sobre o colaborador alcançado, aceitam múltipla
 * seleção e se combinam com E entre dimensões. Os totais consolidados consideram apenas
 * os itens que sobreviveram aos filtros — o indicador reflete exatamente o recorte que
 * está na tela.</p>
 */
@Service
@RequiredArgsConstructor
public class AcompanhamentoMetasUseCases {

    private static final int SCALE = 2;
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final MetaInspecaoRepository metaRepository;
    private final TipoInspecaoRepository tipoInspecaoRepository;
    private final InspecaoSegurancaRepository inspecaoRepository;
    private final ColaboradorApi colaboradorApi;

    /** Colaborador alcançado por uma meta, já com a janela de apuração resolvida. */
    private record Alvo(MetaInspecao meta, JanelaMeta janela, ColaboradorResponse colaborador) {}
    private record RealizacaoItem(UUID idTipoInspecao, LocalDate data) {}

    @Transactional(readOnly = true)
    public AcompanhamentoMetasResponse acompanhar(AcompanhamentoMetaFilter filter) {
        AcompanhamentoMetaFilter efetivo = filter == null ? AcompanhamentoMetaFilter.vazio() : filter;
        LocalDate referencia = efetivo.dataReferencia() != null ? efetivo.dataReferencia() : LocalDate.now(ZONA);

        Map<UUID, Alvo> alvos = resolverAlvos(referencia, efetivo);
        Map<UUID, List<RealizacaoItem>> realizacoes = carregarRealizacoes(alvos.values());

        List<AcompanhamentoMetaItem> itens = alvos.values().stream()
                .map(alvo -> montarItem(alvo, realizacoes, referencia))
                .filter(item -> SelecaoMultipla.contem(efetivo.status(), item.status()))
                .sorted(ordenacao(efetivo))
                .toList();

        long atingiram = itens.stream().filter(i -> i.status() == StatusMeta.ATINGIDA).count();
        long emAndamento = itens.stream().filter(i -> i.status() == StatusMeta.EM_ANDAMENTO).count();
        long naoAtingiram = itens.stream().filter(i -> i.status() == StatusMeta.NAO_ATINGIDA).count();
        long somaMetas = itens.stream().mapToLong(AcompanhamentoMetaItem::meta).sum();
        long somaRealizado = itens.stream().mapToLong(AcompanhamentoMetaItem::realizado).sum();

        return new AcompanhamentoMetasResponse(
                referencia, itens.size(), atingiram, emAndamento, naoAtingiram,
                somaMetas, somaRealizado,
                percent(atingiram, itens.size()),
                percent(somaRealizado, somaMetas),
                itens);
    }

    /**
     * Expande as metas vigentes em colaboradores. As metas de função são aplicadas a
     * todos os colaboradores ativos da função e depois sobrescritas, quando houver,
     * pela meta individual do colaborador.
     */
    private Map<UUID, Alvo> resolverAlvos(LocalDate referencia, AcompanhamentoMetaFilter filter) {
        List<MetaInspecao> vigentes = metaRepository.findByAtivoTrue().stream()
                .filter(meta -> SelecaoMultipla.contem(filter.idTipoInspecao(), meta.getIdTipoInspecao()))
                .filter(meta -> meta.estaVigenteEm(referencia))
                .toList();

        Map<UUID, Alvo> alvos = new LinkedHashMap<>();

        vigentes.stream()
                .filter(meta -> meta.getEscopo() == EscopoMeta.FUNCAO)
                .filter(meta -> SelecaoMultipla.contem(filter.idFuncao(), meta.getIdFuncao()))
                .forEach(meta -> colaboradorApi.listarAtivosPorFuncao(meta.getIdFuncao())
                        .forEach(colaborador -> registrar(alvos, meta, colaborador, referencia)));

        vigentes.stream()
                .filter(meta -> meta.getEscopo() == EscopoMeta.COLABORADOR)
                .forEach(meta -> colaboradorApi.buscarPorIdResponse(meta.getIdColaborador())
                        .filter(ColaboradorResponse::ativo)
                        .ifPresent(colaborador -> registrar(alvos, meta, colaborador, referencia)));

        return alvos.entrySet().stream()
                .filter(entry -> aplicaFiltros(entry.getValue(), filter))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private void registrar(Map<UUID, Alvo> alvos, MetaInspecao meta,
                           ColaboradorResponse colaborador, LocalDate referencia) {
        meta.janelaEm(referencia).ifPresent(janela ->
                alvos.put(colaborador.id(), new Alvo(meta, janela, colaborador)));
    }

    private boolean aplicaFiltros(Alvo alvo, AcompanhamentoMetaFilter filter) {
        ColaboradorResponse colaborador = alvo.colaborador();
        return SelecaoMultipla.contem(filter.idColaborador(), colaborador.id())
                && SelecaoMultipla.contem(filter.idFuncao(), idFuncao(colaborador))
                && SelecaoMultipla.contem(filter.idBaseOperacional(), idBaseOperacional(colaborador))
                && SelecaoMultipla.contem(filter.idCentroCusto(), idCentroCusto(colaborador))
                && SelecaoMultipla.contem(filter.idProcesso(), idProcesso(colaborador));
    }

    /**
     * Ordena a lista pelo critério escolhido, sempre desempatando pelo nome do colaborador
     * para a saída ficar estável entre requisições iguais. Nomes ausentes vão para o fim,
     * independentemente da direção.
     */
    private Comparator<AcompanhamentoMetaItem> ordenacao(AcompanhamentoMetaFilter filter) {
        Comparator<AcompanhamentoMetaItem> criterio = switch (filter.ordenacaoEfetiva()) {
            case PERCENTUAL -> Comparator.comparing(AcompanhamentoMetaItem::percentualAtingimento);
            case REALIZADO -> Comparator.comparingLong(AcompanhamentoMetaItem::realizado);
            case META -> Comparator.comparingInt(AcompanhamentoMetaItem::meta);
            case SALDO -> Comparator.comparingLong(AcompanhamentoMetaItem::saldo);
            case DIAS_RESTANTES -> Comparator.comparingLong(AcompanhamentoMetaItem::diasRestantes);
            case COLABORADOR -> porTexto(AcompanhamentoMetaItem::nomeColaborador);
            case FUNCAO -> porTexto(AcompanhamentoMetaItem::nomeFuncao);
            case BASE_OPERACIONAL -> porTexto(AcompanhamentoMetaItem::nomeBaseOperacional);
            case PROCESSO -> porTexto(AcompanhamentoMetaItem::nomeProcesso);
            case CENTRO_CUSTO -> porTexto(AcompanhamentoMetaItem::nomeCentroCusto);
            case STATUS -> Comparator.comparing(AcompanhamentoMetaItem::status);
        };
        if (filter.direcaoEfetiva().isDescending()) {
            criterio = criterio.reversed();
        }
        return criterio.thenComparing(porTexto(AcompanhamentoMetaItem::nomeColaborador));
    }

    /** Comparador de texto tolerante a nulos: o que não tem nome vai para o fim da lista. */
    private Comparator<AcompanhamentoMetaItem> porTexto(
            java.util.function.Function<AcompanhamentoMetaItem, String> extrator) {
        return Comparator.comparing(extrator, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private UUID idFuncao(ColaboradorResponse colaborador) {
        return colaborador.funcao() == null ? null : colaborador.funcao().id();
    }

    private UUID idBaseOperacional(ColaboradorResponse colaborador) {
        return colaborador.baseOperacional() == null ? null : colaborador.baseOperacional().id();
    }

    private UUID idProcesso(ColaboradorResponse colaborador) {
        return colaborador.processo() == null ? null : colaborador.processo().id();
    }

    private UUID idCentroCusto(ColaboradorResponse colaborador) {
        if (colaborador.baseOperacional() == null || colaborador.baseOperacional().centroFaturamento() == null) {
            return null;
        }
        return colaborador.baseOperacional().centroFaturamento().id();
    }

    private String nomeCentroCusto(ColaboradorResponse colaborador) {
        if (colaborador.baseOperacional() == null || colaborador.baseOperacional().centroFaturamento() == null) {
            return null;
        }
        return colaborador.baseOperacional().centroFaturamento().nome();
    }

    /**
     * Carrega em uma única consulta as inspeções lançadas pelos inspetores alcançados,
     * cobrindo o intervalo que abrange todas as janelas apuradas.
     */
    private Map<UUID, List<RealizacaoItem>> carregarRealizacoes(Collection<Alvo> alvos) {
        Set<UUID> inspetores = alvos.stream()
                .map(alvo -> alvo.colaborador().idUsuario())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (inspetores.isEmpty()) {
            return Map.of();
        }

        LocalDate inicio = alvos.stream().map(alvo -> alvo.janela().inicio())
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate fim = alvos.stream().map(alvo -> alvo.janela().fim())
                .max(Comparator.naturalOrder()).orElseThrow();

        Map<UUID, List<RealizacaoItem>> porInspetor = new HashMap<>();
        inspecaoRepository.listarRealizacoesPorInspetor(inspetores, inicioDoDia(inicio), inicioDoDia(fim.plusDays(1)))
                .forEach(realizacao -> porInspetor
                        .computeIfAbsent(realizacao.getIdInspetor(), id -> new ArrayList<>())
                        .add(new RealizacaoItem(
                                realizacao.getIdTipoInspecao(),
                                realizacao.getDataRealizacao().atZoneSameInstant(ZONA).toLocalDate())));
        return porInspetor;
    }

    private AcompanhamentoMetaItem montarItem(Alvo alvo, Map<UUID, List<RealizacaoItem>> realizacoes, LocalDate referencia) {
        ColaboradorResponse colaborador = alvo.colaborador();
        JanelaMeta janela = alvo.janela();
        MetaInspecao meta = alvo.meta();

        long realizado = colaborador.idUsuario() == null ? 0L
                : realizacoes.getOrDefault(colaborador.idUsuario(), List.of()).stream()
                        .filter(r -> Objects.equals(r.idTipoInspecao(), meta.getIdTipoInspecao()) && janela.contem(r.data()))
                        .count();

        long saldo = Math.max(0, meta.getQuantidade() - realizado);
        StatusMeta status = saldo == 0 ? StatusMeta.ATINGIDA
                : janela.encerradaEm(referencia) ? StatusMeta.NAO_ATINGIDA : StatusMeta.EM_ANDAMENTO;

        String nomeTipoInspecao = tipoInspecaoRepository.findById(meta.getIdTipoInspecao())
                .map(TipoInspecao::getNome).orElse(null);

        return new AcompanhamentoMetaItem(
                meta.getId(), meta.getIdTipoInspecao(), nomeTipoInspecao, meta.getEscopo(),
                colaborador.id(), colaborador.nomeCompleto(),
                colaborador.idUsuario() != null,
                idFuncao(colaborador),
                colaborador.funcao() == null ? null : colaborador.funcao().nome(),
                idBaseOperacional(colaborador),
                colaborador.baseOperacional() == null ? null : colaborador.baseOperacional().nome(),
                idCentroCusto(colaborador), nomeCentroCusto(colaborador),
                idProcesso(colaborador),
                colaborador.processo() == null ? null : colaborador.processo().nome(),
                meta.getPeriodicidade(), janela.inicio(), janela.fim(), janela.diasRestantes(referencia),
                meta.getQuantidade(), realizado, saldo,
                percent(realizado, meta.getQuantidade()), status);
    }

    private OffsetDateTime inicioDoDia(LocalDate data) {
        return data.atStartOfDay(ZONA).toOffsetDateTime();
    }

    private BigDecimal percent(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(parte)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
    }
}
