package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest.Conteudo;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest.Nivel;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest.Situacao;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesFilter;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse.Solicitacao;
import com.nexo.app.modules.estoque.domain.SubEtapaKanban;
import com.nexo.app.modules.estoque.infrastructure.persistence.PainelSolicitacoesRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.ItemAnaliticoProjection;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.shared.application.exportacao.CsvExportacao;
import com.nexo.app.shared.application.exportacao.LinhaExportacao;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Exportação do painel de solicitações no mesmo modelo de inspeções e desvios: uma aba de dados
 * (ou CSV) escolhida por conteúdo e nível de detalhe, mais a aba "Sobre" com a procedência. Parte
 * das mesmas linhas do painel ({@link PainelSolicitacoesUseCase}), então os números conferem.
 */
@Service
@RequiredArgsConstructor
public class ExportarPainelSolicitacoesUseCase {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int LOTE_ITENS = 1000;

    private final PainelSolicitacoesUseCase painelUseCase;
    private final PainelSolicitacoesRepository repository;
    private final EquipeApi equipeApi;

    /** Conteúdo pronto para escrever: nome da aba, cabeçalho, linhas e procedência. */
    public record Exportacao(String aba, String[] cabecalho, List<Object[]> linhas, Map<String, String> procedencia) {
    }

    /**
     * Lê e monta o conteúdo da exportação. Roda na thread da requisição (com usuário e transação), antes de a
     * resposta começar: uma falha aqui volta como Problem Details, em vez de corromper um download já iniciado.
     */
    @Transactional(readOnly = true)
    public Exportacao montar(PainelSolicitacoesFilter filtro, ExportarPainelSolicitacoesRequest pedido) {
        PainelSolicitacoesResponse painel = painelUseCase.obter(filtro, pedido.incluirBacklogOuPadrao());
        LocalDate hoje = LocalDate.now(FUSO);
        List<Solicitacao> linhas = painel.solicitacoes().stream()
                .filter(s -> pedido.situacao() == null || naSituacao(s, pedido.situacao(), hoje))
                .toList();
        Conteudo conteudo = pedido.conteudoOuPadrao();
        Nivel nivel = pedido.nivelOuPadrao();

        Exportacao base = switch (conteudo) {
            case SOLICITACOES -> nivel == Nivel.DETALHADO ? itens(linhas, hoje) : solicitacoes(linhas, hoje);
            case MATERIAIS -> materiais(linhas, nivel == Nivel.DETALHADO);
            case EQUIPES -> consolidado("Equipes", "Equipe", linhas, hoje,
                    s -> s.equipe() == null ? "Sem equipe" : s.equipe(), true);
            case ALMOXARIFADOS -> consolidado("Almoxarifados", "Almoxarifado", linhas, hoje,
                    s -> s.almoxarifado() == null ? "Não informado" : s.almoxarifado(), false);
        };
        return new Exportacao(base.aba(), base.cabecalho(), base.linhas(),
                procedencia(filtro, pedido, painel, linhas.size(), base.linhas().size()));
    }

    /** Só escreve: sem consulta ao banco, pode rodar no streaming da resposta. */
    public static void escreverCsv(Exportacao e, Writer writer) throws IOException {
        CSVFormat formato = CSVFormat.Builder.create().setHeader(e.cabecalho()).setDelimiter(';').build();
        try (CSVPrinter printer = new CSVPrinter(writer, formato)) {
            LinhaExportacao saida = CsvExportacao.para(printer);
            for (Object[] linha : e.linhas()) saida.escrever(linha);
        }
    }

    /** Só escreve: sem consulta ao banco, pode rodar no streaming da resposta. */
    public static void escreverXlsx(Exportacao e, OutputStream saida) throws IOException {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx(e.aba(), e.cabecalho())) {
            for (Object[] linha : e.linhas()) planilha.linha(linha);
            planilha.procedencia(e.procedencia());
            planilha.escrever(saida);
        }
    }

    /** Nome base do arquivo (sem extensão) para o Content-Disposition. */
    public static String nomeArquivo(PainelSolicitacoesFilter filtro, ExportarPainelSolicitacoesRequest pedido) {
        String processo = filtro.contexto() == null ? "geral" : filtro.contexto().name().toLowerCase();
        return "solicitacoes_" + processo + "_" + pedido.conteudoOuPadrao().name().toLowerCase() + "_"
                + pedido.nivelOuPadrao().name().toLowerCase() + "_" + LocalDate.now(FUSO);
    }

    // ------------------------------------------------------------------ conteúdos

    private static final String[] CABECALHO_SOLICITACOES = {
            "Processo", "Tipo", "Solicitada em", "Status", "Etapa", "Situação", "Data prevista", "Atrasada",
            "Dias de atraso", "Aprovada em", "Entregue em", "Ciclo (h)", "Aprovação (h)", "Separação (h)",
            "Em aberto há (dias)", "Equipe", "Base operacional", "Processo de negócio", "Almoxarifado",
            "Estoque de origem", "Estoque de destino", "Obra", "Tipo de obra", "Ocorrência", "Reserva",
            "Itens", "Qtd. solicitada", "Qtd. atendida", "% atendido", "Pendência", "Motivos de pendência",
            "Motivo de rejeição", "Prazo de saque menor que 2 dias", "Saque fora da equipe",
            "Criada antes do período", "ID"
    };

    private Exportacao solicitacoes(List<Solicitacao> linhas, LocalDate hoje) {
        List<Object[]> saida = new ArrayList<>(linhas.size());
        for (Solicitacao s : linhas) {
            Situacao situacao = situacao(s);
            saida.add(new Object[]{
                    rotuloContexto(s.contexto()), rotuloTipo(s.tipoTecnico()), s.dataSolicitacao(), s.status(),
                    rotuloEtapa(s.etapa()), rotuloSituacao(s, hoje), s.dataPrevista(), atrasada(s, hoje),
                    atrasada(s, hoje) ? diasDeAtraso(s, hoje) : null, s.dataVerificacao(), s.dataEntrega(),
                    situacao == Situacao.CONCLUIDA ? horas(s.dataSolicitacao(), s.dataEntrega()) : null,
                    horas(s.dataSolicitacao(), s.dataVerificacao()),
                    horas(s.separacaoIniciadaEm(), s.separacaoConcluidaEm()),
                    situacao == Situacao.ABERTA ? diasEmAberto(s) : null,
                    s.equipe(), s.baseOperacional(), s.processo(), s.almoxarifado(), s.estoqueOrigem(),
                    s.estoqueDestino(), s.numeroObra(), s.tipoObra(), s.ocorrencia(),
                    "TECNICO".equals(s.contexto()) ? s.temReserva() : null, s.itens(), s.quantidadeSolicitada(),
                    s.quantidadeAtendida(), percentual(s.quantidadeAtendida(), s.quantidadeSolicitada()),
                    s.possuiPendencia(), s.motivosPendencia(), s.motivoRejeicao(), s.foraPrazoSaque(),
                    s.saqueForaEquipe(), s.anteriorAoPeriodo(), s.id().toString()
            });
        }
        return new Exportacao("Solicitações", CABECALHO_SOLICITACOES, saida, Map.of());
    }

    private static final String[] CABECALHO_ITENS = {
            "Processo", "Tipo", "Solicitada em", "Etapa", "Situação", "Data prevista", "Atrasada", "Equipe",
            "Base operacional", "Almoxarifado", "Estoque de destino", "Obra", "Código do material",
            "Descrição do material", "Unidade", "Número de série", "Qtd. solicitada", "Qtd. atendida",
            "Qtd. pendente", "% atendido", "Motivo de pendência do item", "Justificativa da pendência",
            "ID da solicitação"
    };

    private Exportacao itens(List<Solicitacao> linhas, LocalDate hoje) {
        Map<UUID, Solicitacao> porId = new LinkedHashMap<>();
        linhas.forEach(s -> porId.put(s.id(), s));
        List<Object[]> saida = new ArrayList<>();
        for (ItemAnaliticoProjection i : carregarItens(new ArrayList<>(porId.keySet()))) {
            Solicitacao s = porId.get(i.getIdSolicitacao());
            if (s == null) continue;
            BigDecimal solicitada = decimal(i.getQuantidadeSolicitada());
            BigDecimal atendida = decimal(i.getQuantidadeAtendida());
            saida.add(new Object[]{
                    rotuloContexto(s.contexto()), rotuloTipo(s.tipoTecnico()), s.dataSolicitacao(),
                    rotuloEtapa(s.etapa()), rotuloSituacao(s, hoje), s.dataPrevista(), atrasada(s, hoje), s.equipe(),
                    s.baseOperacional(), s.almoxarifado(), s.estoqueDestino(), s.numeroObra(), i.getCodigo(),
                    i.getDescricao(), i.getUnidade(), i.getNumeroSerie(), solicitada, atendida,
                    solicitada.subtract(atendida).max(BigDecimal.ZERO), percentual(atendida, solicitada),
                    i.getMotivoPendencia(), i.getJustificativaPendencia(), s.id().toString()
            });
        }
        return new Exportacao("Itens das solicitações", CABECALHO_ITENS, saida, Map.of());
    }

    private static final String[] CABECALHO_MATERIAIS = {
            "Código", "Descrição", "Unidade", "Solicitações", "Qtd. solicitada", "Qtd. atendida", "Qtd. pendente",
            "% atendido"
    };

    private static final String[] CABECALHO_MATERIAIS_DETALHADO = {
            "Processo", "Sentido", "Código", "Descrição", "Unidade", "Solicitações", "Qtd. solicitada",
            "Qtd. atendida", "Qtd. pendente", "% atendido"
    };

    /** Materiais das solicitações exportadas (sem as recusadas), somados por código ou por processo e sentido. */
    private Exportacao materiais(List<Solicitacao> linhas, boolean detalhado) {
        Map<UUID, Solicitacao> porId = new HashMap<>();
        linhas.stream().filter(s -> situacao(s) != Situacao.RECUSADA).forEach(s -> porId.put(s.id(), s));
        Map<String, AcumuladoMaterial> mapa = new LinkedHashMap<>();
        for (ItemAnaliticoProjection i : carregarItens(new ArrayList<>(porId.keySet()))) {
            Solicitacao s = porId.get(i.getIdSolicitacao());
            if (s == null) continue;
            String sentido = devolucao(s) ? "Devolução" : "Saída";
            String chave = detalhado ? s.contexto() + "|" + sentido + "|" + i.getCodigo() : i.getCodigo();
            AcumuladoMaterial a = mapa.computeIfAbsent(chave, k -> new AcumuladoMaterial(
                    rotuloContexto(s.contexto()), sentido, i.getCodigo(), i.getDescricao(), i.getUnidade()));
            a.solicitacoes.add(s.id());
            a.solicitada = a.solicitada.add(decimal(i.getQuantidadeSolicitada()));
            a.atendida = a.atendida.add(decimal(i.getQuantidadeAtendida()));
        }
        List<Object[]> saida = mapa.values().stream()
                .sorted(Comparator.comparingInt((AcumuladoMaterial a) -> a.solicitacoes.size()).reversed()
                        .thenComparing(a -> a.codigo))
                .map(a -> {
                    BigDecimal pendente = a.solicitada.subtract(a.atendida).max(BigDecimal.ZERO);
                    Double pct = percentual(a.atendida, a.solicitada);
                    return detalhado
                            ? new Object[]{a.processo, a.sentido, a.codigo, a.descricao, a.unidade, a.solicitacoes.size(),
                                    a.solicitada, a.atendida, pendente, pct}
                            : new Object[]{a.codigo, a.descricao, a.unidade, a.solicitacoes.size(), a.solicitada,
                                    a.atendida, pendente, pct};
                })
                .toList();
        return new Exportacao("Materiais", detalhado ? CABECALHO_MATERIAIS_DETALHADO : CABECALHO_MATERIAIS,
                saida, Map.of());
    }

    private static final class AcumuladoMaterial {
        final String processo;
        final String sentido;
        final String codigo;
        final String descricao;
        final String unidade;
        final java.util.Set<UUID> solicitacoes = new java.util.HashSet<>();
        BigDecimal solicitada = BigDecimal.ZERO;
        BigDecimal atendida = BigDecimal.ZERO;

        AcumuladoMaterial(String processo, String sentido, String codigo, String descricao, String unidade) {
            this.processo = processo;
            this.sentido = sentido;
            this.codigo = codigo;
            this.descricao = descricao;
            this.unidade = unidade;
        }
    }

    /** Equipes ou almoxarifados: volume, conclusão, fila, atrasos e ciclo de cada um. */
    private Exportacao consolidado(String aba, String rotulo, List<Solicitacao> linhas, LocalDate hoje,
                                   Function<Solicitacao, String> chave, boolean comBase) {
        Map<String, Consolidado> mapa = new HashMap<>();
        for (Solicitacao s : linhas) {
            Consolidado c = mapa.computeIfAbsent(chave.apply(s), k -> new Consolidado(s));
            c.total++;
            Situacao situacao = situacao(s);
            switch (situacao) {
                case CONCLUIDA -> c.concluidas++;
                case RECUSADA -> c.recusadas++;
                default -> c.abertas++;
            }
            if (atrasada(s, hoje)) c.atrasadas++;
            if (s.possuiPendencia()) c.comPendencia++;
            if (s.anteriorAoPeriodo()) c.anteriores++;
            Double ciclo = situacao == Situacao.CONCLUIDA ? horas(s.dataSolicitacao(), s.dataEntrega()) : null;
            if (ciclo != null) c.ciclos.add(ciclo);
            c.solicitada = c.solicitada.add(s.quantidadeSolicitada());
            c.atendida = c.atendida.add(s.quantidadeAtendida());
        }
        List<String> cabecalho = new ArrayList<>(List.of(rotulo));
        if (comBase) cabecalho.addAll(List.of("Base operacional", "Processo de negócio"));
        cabecalho.addAll(List.of("Solicitações", "Concluídas", "Em aberto", "Atrasadas", "Recusadas",
                "Com pendência", "Anteriores ao período", "% concluído", "Ciclo mediano (h)", "Ciclo médio (h)",
                "Qtd. solicitada", "Qtd. atendida", "% atendido"));
        List<Object[]> saida = mapa.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Consolidado> e) -> e.getValue().total).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(e -> {
                    Consolidado c = e.getValue();
                    List<Object> l = new ArrayList<>();
                    l.add(e.getKey());
                    if (comBase) {
                        l.add(c.exemplo.baseOperacional());
                        l.add(c.exemplo.processo());
                    }
                    long decididas = c.total - c.recusadas;
                    l.addAll(java.util.Arrays.asList(c.total, c.concluidas, c.abertas, c.atrasadas, c.recusadas,
                            c.comPendencia, c.anteriores,
                            decididas > 0 ? Math.round(c.concluidas * 1000.0 / decididas) / 10.0 : null,
                            mediana(c.ciclos), media(c.ciclos), c.solicitada, c.atendida,
                            percentual(c.atendida, c.solicitada)));
                    return l.toArray();
                })
                .toList();
        return new Exportacao(aba, cabecalho.toArray(String[]::new), saida, Map.of());
    }

    private static final class Consolidado {
        final Solicitacao exemplo;
        long total;
        long concluidas;
        long abertas;
        long atrasadas;
        long recusadas;
        long comPendencia;
        long anteriores;
        final List<Double> ciclos = new ArrayList<>();
        BigDecimal solicitada = BigDecimal.ZERO;
        BigDecimal atendida = BigDecimal.ZERO;

        Consolidado(Solicitacao exemplo) {
            this.exemplo = exemplo;
        }
    }

    // ------------------------------------------------------------------ procedência

    private Map<String, String> procedencia(PainelSolicitacoesFilter filtro, ExportarPainelSolicitacoesRequest pedido,
                                            PainelSolicitacoesResponse painel, int solicitacoes, int linhas) {
        List<EquipeResponse> ativas = equipeApi.listarEquipesAtivas();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Relatório", "Painel de solicitações de material");
        info.put("Conteúdo", switch (pedido.conteudoOuPadrao()) {
            case SOLICITACOES -> pedido.nivelOuPadrao() == Nivel.DETALHADO ? "Itens das solicitações (um material por linha)" : "Solicitações (uma por linha)";
            case MATERIAIS -> pedido.nivelOuPadrao() == Nivel.DETALHADO ? "Materiais por processo e sentido" : "Materiais consolidados por código";
            case EQUIPES -> "Consolidado por equipe";
            case ALMOXARIFADOS -> "Consolidado por almoxarifado";
        });
        info.put("Processo", filtro.contexto() == null ? "Comercial e técnico" : rotuloContexto(filtro.contexto().name()));
        info.put("Período (data da solicitação)", painel.inicio().atZoneSameInstant(FUSO).format(DATA_HORA) + " a "
                + painel.fim().atZoneSameInstant(FUSO).format(DATA_HORA));
        info.put("Abertas de antes do período", pedido.incluirBacklogOuPadrao() ? "Incluídas" : "Não incluídas");
        info.put("Situação", pedido.situacao() == null ? "Todas" : switch (pedido.situacao()) {
            case ABERTA -> "Em aberto";
            case ATRASADA -> "Atrasadas (data prevista vencida)";
            case CONCLUIDA -> "Concluídas";
            case RECUSADA -> "Recusadas (rejeitadas ou canceladas)";
        });
        info.put("Base operacional", nome(filtro.idBaseOperacional(), ativas.stream().map(EquipeResponse::baseOperacional)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toMap(b -> b.id(), b -> b.nome(), (a, b) -> a)), "Todas"));
        info.put("Processo de negócio", nome(filtro.idProcesso(), ativas.stream().map(EquipeResponse::processo)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toMap(p -> p.id(), p -> p.nome(), (a, b) -> a)), "Todos"));
        info.put("Equipe", nome(filtro.idEquipe(), ativas.stream()
                .collect(java.util.stream.Collectors.toMap(EquipeResponse::id, EquipeResponse::codigo, (a, b) -> a)), "Todas"));
        info.put("Almoxarifado", filtro.idAlmoxarifado() == null ? "Todos" : painel.solicitacoes().stream()
                .filter(s -> filtro.idAlmoxarifado().equals(s.idAlmoxarifado())).map(Solicitacao::almoxarifado)
                .filter(Objects::nonNull).findFirst().orElse(filtro.idAlmoxarifado().toString()));
        info.put("Solicitações consideradas", solicitacoes + (painel.truncado()
                ? " (recorte limitado às " + painel.limite() + " mais recentes)" : ""));
        info.put("Linhas exportadas", String.valueOf(linhas));
        info.put("Definições", "Atrasada = aberta com a data prevista de retirada/devolução vencida. Ciclo = da abertura à "
                + "entrega ou devolução final. Etapa segue a regra do Kanban.");
        info.put("Gerado em", painel.geradoEm().atZoneSameInstant(FUSO).format(DATA_HORA));
        return info;
    }

    // ------------------------------------------------------------------ regras

    private List<ItemAnaliticoProjection> carregarItens(List<UUID> ids) {
        List<ItemAnaliticoProjection> itens = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += LOTE_ITENS) {
            itens.addAll(repository.listarItens(ids.subList(i, Math.min(ids.size(), i + LOTE_ITENS))));
        }
        return itens;
    }

    static Situacao situacao(Solicitacao s) {
        if ("CONCLUIDO".equals(s.etapa())) return Situacao.CONCLUIDA;
        if ("REJEITADO".equals(s.etapa()) || "CANCELADO".equals(s.etapa())) return Situacao.RECUSADA;
        return Situacao.ABERTA;
    }

    static boolean atrasada(Solicitacao s, LocalDate hoje) {
        return situacao(s) == Situacao.ABERTA && s.dataPrevista() != null && s.dataPrevista().isBefore(hoje);
    }

    private static boolean naSituacao(Solicitacao s, Situacao filtro, LocalDate hoje) {
        return filtro == Situacao.ATRASADA ? atrasada(s, hoje) : situacao(s) == filtro;
    }

    private static String rotuloSituacao(Solicitacao s, LocalDate hoje) {
        return switch (situacao(s)) {
            case CONCLUIDA -> "Concluída";
            case RECUSADA -> "Recusada";
            default -> atrasada(s, hoje) ? "Atrasada" : "Em aberto";
        };
    }

    private static boolean devolucao(Solicitacao s) {
        return "222".equals(s.tipoTecnico()) || "922".equals(s.tipoTecnico());
    }

    private static long diasDeAtraso(Solicitacao s, LocalDate hoje) {
        return java.time.temporal.ChronoUnit.DAYS.between(s.dataPrevista(), hoje);
    }

    private static Long diasEmAberto(Solicitacao s) {
        return s.dataSolicitacao() == null ? null : Duration.between(s.dataSolicitacao(), OffsetDateTime.now(FUSO)).toDays();
    }

    private static Double horas(OffsetDateTime de, OffsetDateTime ate) {
        if (de == null || ate == null || ate.isBefore(de)) return null;
        return Math.round(Duration.between(de, ate).toMinutes() / 6.0) / 10.0;
    }

    private static Double percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() <= 0 || parte == null) return null;
        return Math.min(100.0, Math.round(parte.doubleValue() / total.doubleValue() * 1000.0) / 10.0);
    }

    private static Double mediana(List<Double> valores) {
        if (valores.isEmpty()) return null;
        List<Double> ordenados = valores.stream().sorted().toList();
        int meio = ordenados.size() / 2;
        double m = ordenados.size() % 2 == 1 ? ordenados.get(meio) : (ordenados.get(meio - 1) + ordenados.get(meio)) / 2;
        return Math.round(m * 10.0) / 10.0;
    }

    private static Double media(List<Double> valores) {
        return valores.isEmpty() ? null
                : Math.round(valores.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10.0) / 10.0;
    }

    private static String nome(UUID id, Map<UUID, String> nomes, String todos) {
        return id == null ? todos : nomes.getOrDefault(id, id.toString());
    }

    private static String rotuloContexto(String contexto) {
        return "TECNICO".equals(contexto) ? "Técnico" : "COMERCIAL".equals(contexto) ? "Comercial" : contexto;
    }

    private static String rotuloTipo(String codigo) {
        if (codigo == null) return null;
        return switch (codigo) {
            case "221" -> "221 · Adição comum";
            case "921" -> "921 · Adição concreto";
            case "222" -> "222 · Devolução comum";
            case "922" -> "922 · Devolução concreto";
            default -> codigo;
        };
    }

    private static String rotuloEtapa(String etapa) {
        if (etapa == null) return null;
        try {
            return SubEtapaKanban.valueOf(etapa).getLabel();
        } catch (IllegalArgumentException e) {
            return etapa;
        }
    }

    private static BigDecimal decimal(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
