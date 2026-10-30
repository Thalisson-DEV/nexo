package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesFilter;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse.Material;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse.ResumoAnterior;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse.Solicitacao;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.SubEtapaKanban;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.PainelSolicitacoesRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.SolicitacaoAnaliticaProjection;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Painel analítico das solicitações de material (processos comercial e técnico). Devolve as
 * solicitações do recorte com os marcos de tempo, a etapa operacional (mesma regra do Kanban) e as
 * quantidades atendidas; o cliente calcula indicadores, rankings e leituras sobre essas linhas, e a
 * exportação ({@link ExportarPainelSolicitacoesUseCase}) parte das mesmas linhas.
 */
@Service
@RequiredArgsConstructor
public class PainelSolicitacoesUseCase {

    static final int LIMITE_SOLICITACOES = 5000;
    static final int LIMITE_MATERIAIS = 400;
    private static final int LIMITE_OBRAS_RESOLVIDAS = 150;
    private static final Duration PERIODO_PADRAO = Duration.ofDays(30);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final List<UUID> SEM_EQUIPES = List.of(new UUID(0L, 0L));

    private final PainelSolicitacoesRepository repository;
    private final EquipeApi equipeApi;
    private final ObraApi obraApi;

    @Transactional(readOnly = true)
    public PainelSolicitacoesResponse obter(PainelSolicitacoesFilter filtro) {
        return obter(filtro, true);
    }

    /** @param incluirBacklog traz também as abertas criadas antes do período (marcadas em {@code anteriorAoPeriodo}). */
    @Transactional(readOnly = true)
    public PainelSolicitacoesResponse obter(PainelSolicitacoesFilter filtro, boolean incluirBacklog) {
        Recorte r = recorte(filtro);
        OffsetDateTime agora = OffsetDateTime.now(FUSO);
        if (r.semEquipes()) {
            return new PainelSolicitacoesResponse(agora, r.inicio(), r.fim(), LIMITE_SOLICITACOES, false,
                    List.of(), List.of(), List.of());
        }

        List<SolicitacaoAnaliticaProjection> linhas = repository.listarSolicitacoes(
                iso(r.inicio()), iso(r.fim()), incluirBacklog, r.contexto(), r.filtrarPorEquipes(), r.idsEquipes(),
                r.idAlmoxarifado(), LIMITE_SOLICITACOES + 1);
        boolean truncado = linhas.size() > LIMITE_SOLICITACOES;
        if (truncado) linhas = linhas.subList(0, LIMITE_SOLICITACOES);

        Map<UUID, Equipe> equipes = resolverEquipes(linhas);
        Map<UUID, String> obras = resolverObras(linhas);
        List<Solicitacao> solicitacoes = linhas.stream().map(l -> paraSolicitacao(l, equipes, obras)).toList();

        List<Material> materiais = repository.listarMateriais(
                        iso(r.inicio()), iso(r.fim()), r.contexto(), r.filtrarPorEquipes(), r.idsEquipes(),
                        r.idAlmoxarifado(), LIMITE_MATERIAIS)
                .stream()
                .map(m -> new Material(m.getContexto(), m.getSentido(), m.getCodigo(), m.getDescricao(),
                        m.getUnidade(), valor(m.getSolicitacoes()), decimal(m.getQuantidadeSolicitada()),
                        decimal(m.getQuantidadeAtendida())))
                .toList();

        Duration duracao = Duration.between(r.inicio(), r.fim());
        List<ResumoAnterior> anterior = repository.resumirPeriodo(
                        iso(r.inicio().minus(duracao)), iso(r.inicio()), r.contexto(), r.filtrarPorEquipes(),
                        r.idsEquipes(), r.idAlmoxarifado())
                .stream()
                .map(p -> new ResumoAnterior(p.getContexto(), valor(p.getTotal()), valor(p.getConcluidas()),
                        valor(p.getRejeitadas()), p.getCicloMedioHoras(), p.getAprovacaoMediaHoras()))
                .toList();

        return new PainelSolicitacoesResponse(agora, r.inicio(), r.fim(), LIMITE_SOLICITACOES, truncado,
                solicitacoes, materiais, anterior);
    }

    // ------------------------------------------------------------------ recorte

    private record Recorte(OffsetDateTime inicio, OffsetDateTime fim, String contexto, boolean filtrarPorEquipes,
                           List<UUID> idsEquipes, String idAlmoxarifado, boolean semEquipes) {
    }

    private Recorte recorte(PainelSolicitacoesFilter f) {
        OffsetDateTime fim = f.dataFim() != null ? f.dataFim() : OffsetDateTime.now(FUSO);
        OffsetDateTime inicio = f.dataInicio() != null ? f.dataInicio() : fim.minus(PERIODO_PADRAO);
        if (inicio.isAfter(fim)) {
            OffsetDateTime troca = inicio;
            inicio = fim;
            fim = troca;
        }
        String contexto = f.contexto() == null ? null : f.contexto().name();
        String almox = f.idAlmoxarifado() == null ? null : f.idAlmoxarifado().toString();

        List<UUID> ids = null;
        if (f.idBaseOperacional() != null || f.idProcesso() != null) {
            ids = equipeApi.buscarIdsPorFiltros(f.idBaseOperacional(), f.idProcesso());
        }
        if (f.idEquipe() != null) {
            ids = ids == null ? List.of(f.idEquipe()) : ids.stream().filter(f.idEquipe()::equals).toList();
        }
        boolean filtrar = ids != null;
        boolean vazio = filtrar && ids.isEmpty();
        return new Recorte(inicio, fim, contexto, filtrar, filtrar && !vazio ? ids : SEM_EQUIPES, almox, vazio);
    }

    // ------------------------------------------------------------------ montagem das linhas

    private record Equipe(String codigo, String base, String processo) {
    }

    private Map<UUID, Equipe> resolverEquipes(List<SolicitacaoAnaliticaProjection> linhas) {
        Map<UUID, Equipe> mapa = new HashMap<>();
        for (EquipeResponse e : equipeApi.listarEquipesAtivas()) {
            mapa.put(e.id(), new Equipe(e.codigo(),
                    e.baseOperacional() == null ? null : e.baseOperacional().nome(),
                    e.processo() == null ? null : e.processo().nome()));
        }
        linhas.stream().map(SolicitacaoAnaliticaProjection::getIdEquipe).filter(Objects::nonNull).distinct()
                .filter(id -> !mapa.containsKey(id))
                .forEach(id -> equipeApi.buscarPublicaPorId(id)
                        .ifPresent(e -> mapa.put(id, new Equipe(e.codigo(), null, null))));
        return mapa;
    }

    private Map<UUID, String> resolverObras(List<SolicitacaoAnaliticaProjection> linhas) {
        Map<UUID, String> mapa = new HashMap<>();
        linhas.stream().map(SolicitacaoAnaliticaProjection::getIdObra).filter(Objects::nonNull).distinct()
                .limit(LIMITE_OBRAS_RESOLVIDAS)
                .forEach(id -> obraApi.buscarPorId(id).ifPresent(o -> mapa.put(id, o.numero())));
        return mapa;
    }

    private Solicitacao paraSolicitacao(SolicitacaoAnaliticaProjection l, Map<UUID, Equipe> equipes, Map<UUID, String> obras) {
        boolean tecnico = "TECNICO".equals(l.getContexto());
        TipoSolicitacaoTecnica tipo = tecnico ? tipoTecnico(l.getTipoTecnico()) : null;
        LocalDate prevista = l.getDataPrevista() == null ? null : LocalDate.parse(l.getDataPrevista());
        OffsetDateTime solicitada = instante(l.getDataSolicitacao());
        SubEtapaKanban etapa = SubEtapaKanban.calcular(new SubEtapaKanban.Fatos(
                enumOuNulo(StatusSolicitacao.class, l.getStatus()),
                enumOuNulo(ContextoSolicitacaoMaterial.class, l.getContexto()),
                l.getEncerradaEm() != null,
                Boolean.TRUE.equals(l.getTemSaldoPendente()),
                tipo,
                enumOuNulo(StatusSeparacao.class, l.getStatusSeparacao()),
                prevista != null,
                Boolean.TRUE.equals(l.getSaqueForaEquipe()),
                l.getIdEquipeSaque() != null,
                excecaoReserva(l),
                Boolean.TRUE.equals(l.getTemReserva())));
        Equipe equipe = l.getIdEquipe() == null ? null : equipes.get(l.getIdEquipe());
        String motivos = juntar(l.getMotivoPendencia(), l.getMotivosPendenciaItens());
        boolean foraPrazo = prevista != null && solicitada != null
                && prevista.isBefore(solicitada.atZoneSameInstant(FUSO).toLocalDate().plusDays(2));
        return new Solicitacao(
                l.getId(), l.getContexto(), l.getStatus(),
                etapa == null ? null : etapa.name(),
                etapa == null ? null : etapa.getMacroEtapa().name(),
                tipo == null ? null : tipo.getCodigo(),
                tecnico ? l.getTipoObra() : null,
                tecnico ? l.getOcorrencia() : null,
                l.getIdObra(),
                l.getIdObra() == null ? null : obras.get(l.getIdObra()),
                tecnico ? l.getStatusSeparacao() : null,
                solicitada,
                instante(l.getDataVerificacao()),
                instante(l.getDataEntrega()),
                prevista,
                instante(l.getSeparacaoIniciadaEm()),
                instante(l.getSeparacaoConcluidaEm()),
                instante(l.getEncerradaEm()),
                l.getEstoqueOrigem(), l.getEstoqueDestino(),
                l.getIdAlmoxarifado(), l.getAlmoxarifado(),
                l.getIdEquipe(),
                equipe == null ? null : equipe.codigo(),
                equipe == null ? null : equipe.base(),
                equipe == null ? null : equipe.processo(),
                l.getMotivoRejeicao(),
                motivos != null || valor(l.getItensComPendencia()) > 0,
                motivos,
                foraPrazo,
                Boolean.TRUE.equals(l.getSaqueForaEquipe()),
                Boolean.TRUE.equals(l.getTemReserva()),
                valor(l.getItens()),
                decimal(l.getQuantidadeSolicitada()),
                decimal(l.getQuantidadeAtendida()),
                Boolean.TRUE.equals(l.getAnteriorAoPeriodo()));
    }

    /** Mesma regra de SolicitacaoMaterial.isExcecaoReserva(). */
    private static boolean excecaoReserva(SolicitacaoAnaliticaProjection l) {
        TipoObraSnapshot tipoObra = enumOuNulo(TipoObraSnapshot.class, l.getTipoObra());
        return tipoObra == TipoObraSnapshot.CORRETIVA || tipoObra == TipoObraSnapshot.PREVENTIVA
                || (l.getOcorrencia() != null && !l.getOcorrencia().isBlank())
                || l.getIdOcorrencia() != null;
    }

    private static TipoSolicitacaoTecnica tipoTecnico(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        try {
            return TipoSolicitacaoTecnica.porCodigo(codigo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOuNulo(Class<E> tipo, String nome) {
        if (nome == null) return null;
        try {
            return Enum.valueOf(tipo, nome);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String juntar(String daSolicitacao, String dosItens) {
        List<String> nomes = new ArrayList<>();
        if (daSolicitacao != null && !daSolicitacao.isBlank()) nomes.add(daSolicitacao);
        if (dosItens != null && !dosItens.isBlank()) {
            for (String n : dosItens.split(", ")) if (!nomes.contains(n)) nomes.add(n);
        }
        return nomes.isEmpty() ? null : String.join(", ", nomes);
    }

    // ------------------------------------------------------------------ utilitários

    private static String iso(OffsetDateTime data) {
        return data.toString();
    }

    private static OffsetDateTime instante(Long epochMs) {
        return epochMs == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), FUSO);
    }

    private static long valor(Long v) {
        return v == null ? 0L : v;
    }

    private static BigDecimal decimal(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
