package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.StatusMedicao;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitAplicadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fechamento de uma ou várias obras: serviços e materiais lançados × concluídos, com o valor executado, kits lançados
 * × concluídos e as medições geradas. Aceita os códigos colados de uma coluna de planilha.
 */
@Service
@RequiredArgsConstructor
public class FechamentoObraUseCases {

    static final int LIMITE_OBRAS = 200;

    private final ObraRepository obraRepository;
    private final KitAplicadoRepository kitRepository;
    private final ApontamentoExecucaoRepository apontamentoRepository;
    private final KitConstrutivoUseCases kitUseCases;
    private final MedicaoObraUseCases medicoes;
    private final LocaisPlanejamento locais;

    /** Separa por linha, vírgula, ponto e vírgula, tabulação ou espaço; mantém a ordem e tira repetidos. */
    static List<String> codigos(String texto) {
        if (texto == null) {
            return List.of();
        }
        Set<String> codigos = new LinkedHashSet<>();
        for (String parte : texto.split("[\\r\\n,;\\t ]+")) {
            String codigo = parte.trim();
            if (!codigo.isEmpty()) {
                codigos.add(codigo);
            }
        }
        return List.copyOf(codigos);
    }

    @Transactional(readOnly = true)
    public MedicaoResponses.Fechamento gerar(String texto) {
        List<String> codigos = codigos(texto);
        if (codigos.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um código de obra.");
        }
        if (codigos.size() > LIMITE_OBRAS) {
            throw new InvarianteVioladaException("Gere o fechamento de até " + LIMITE_OBRAS + " obras por vez.");
        }
        Set<String> buscados = new LinkedHashSet<>(codigos);
        codigos.forEach(c -> buscados.add(c.toUpperCase(Locale.ROOT)));
        Map<String, Obra> porNumero = obraRepository.findByNumeroInAndAtivoTrue(buscados).stream()
                .collect(Collectors.toMap(o -> o.getNumero().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        List<Obra> obras = new ArrayList<>();
        List<String> naoEncontrados = new ArrayList<>();
        for (String codigo : codigos) {
            Obra obra = porNumero.get(codigo.toUpperCase(Locale.ROOT));
            if (obra == null) {
                naoEncontrados.add(codigo);
            } else if (!obras.contains(obra)) {
                obras.add(obra);
            }
        }
        Map<UUID, BigDecimal> executado = new HashMap<>();
        if (!obras.isEmpty()) {
            for (Object[] linha : apontamentoRepository.somarValorPorObra(obras.stream().map(Obra::getId).toList())) {
                executado.put((UUID) linha[0], (BigDecimal) linha[1]);
            }
        }
        Map<UUID, String> municipios = medicoes.municipios(obras);
        Map<UUID, Map<UUID, BigDecimal>> valoresPorContrato = new HashMap<>();
        LocalDate hoje = LocalDate.now(MedicaoObraUseCases.FUSO);
        List<MedicaoResponses.FechamentoObra> fechamentos = obras.stream()
                .map(o -> fechamento(o, municipios.get(o.getIdMunicipio()), executado.getOrDefault(o.getId(), BigDecimal.ZERO),
                        idContrato -> valoresPorContrato.computeIfAbsent(idContrato, id -> kitUseCases.valoresVigentes(id, hoje))))
                .toList();
        return new MedicaoResponses.Fechamento(fechamentos, naoEncontrados);
    }

    private MedicaoResponses.FechamentoObra fechamento(Obra obra, String municipio, BigDecimal valorExecutado,
                                                       Function<UUID, Map<UUID, BigDecimal>> valoresDoContrato) {
        List<KitAplicado> kits = kitRepository.listarComItens(obra.getId());
        Map<UUID, BigDecimal> valorPorItem = apontamentoRepository.somarPorItemDaObra(obra.getId()).stream()
                .filter(e -> e.valor() != null)
                .collect(Collectors.toMap(ApontamentoExecucaoRepository.ExecucaoDoItem::idItem, ApontamentoExecucaoRepository.ExecucaoDoItem::valor));
        Map<UUID, String> locaisDaObra = locais.daObra(obra.getId());

        Map<String, BigDecimal[]> servicos = new LinkedHashMap<>();
        Map<String, BigDecimal[]> materiais = new LinkedHashMap<>();
        Map<String, ItemPlanejado> exemplo = new HashMap<>();
        Map<String, KitResumo> porKit = new LinkedHashMap<>();
        BigDecimal planejado = BigDecimal.ZERO;
        int concluidos = 0;
        boolean algumaExecucao = false;
        for (KitAplicado kit : kits) {
            List<ItemPlanejado> itens = kit.getItensAtivos();
            boolean concluido = kit.isExecutadoAntes() || (!itens.isEmpty() && itens.stream().allMatch(ItemPlanejado::concluido));
            concluidos += concluido ? 1 : 0;
            KitResumo resumo = porKit.computeIfAbsent(kit.getCodigoKit(), c -> new KitResumo(kit.getDescricaoKit()));
            resumo.lancados++;
            resumo.concluidos += concluido ? 1 : 0;
            String local = LocaisPlanejamento.local(locaisDaObra, kit.getIdPonto(), kit.getIdVao());
            if (local != null) {
                resumo.locais.add(local);
            }
            Map<UUID, BigDecimal> valores = kit.getIdContrato() != null ? valoresDoContrato.apply(kit.getIdContrato()) : Map.of();
            for (ItemPlanejado item : itens) {
                algumaExecucao |= item.temExecucao();
                String chave = item.getCodigo() + "|" + item.getOperacao() + "|" + item.getUnidade();
                exemplo.putIfAbsent(chave, item);
                Map<String, BigDecimal[]> destino = item.getTipo() == TipoItemKit.SERVICO ? servicos : materiais;
                BigDecimal[] soma = destino.computeIfAbsent(chave, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                soma[0] = soma[0].add(item.getQuantidadePrevista());
                soma[1] = soma[1].add(item.getQuantidadeExecutada());
                soma[2] = soma[2].add(valorPorItem.getOrDefault(item.getId(), BigDecimal.ZERO));
                if (item.getTipo() == TipoItemKit.SERVICO && item.getIdAcao() != null && valores.get(item.getIdAcao()) != null) {
                    planejado = planejado.add(valores.get(item.getIdAcao()).multiply(item.getQuantidadePrevista()));
                }
            }
        }
        List<MedicaoResponses.MedicaoResumo> medicoesDaObra = medicoes.resumosDaObra(obra);
        BigDecimal medido = medicoesDaObra.stream().filter(m -> m.status() == StatusMedicao.GERADA)
                .map(MedicaoResponses.MedicaoResumo::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        String situacao = kits.isEmpty() || (!algumaExecucao && concluidos == 0) ? "NAO_INICIADA"
                : concluidos == kits.size() ? "CONCLUIDA" : "EM_ANDAMENTO";

        return new MedicaoResponses.FechamentoObra(obra.getId(), obra.getNumero(), obra.getTitulo(), municipio, situacao,
                kits.size(), concluidos, planejado.setScale(2, RoundingMode.HALF_UP), valorExecutado, medido,
                (int) medicoesDaObra.stream().filter(m -> m.status() == StatusMedicao.GERADA).count(),
                servicos.entrySet().stream().map(e -> {
                    ItemPlanejado i = exemplo.get(e.getKey());
                    return new MedicaoResponses.LinhaServico(i.getCodigo(), i.getDescricao(), i.getOperacao(), i.getUnidade(),
                            e.getValue()[0], e.getValue()[1], e.getValue()[2]);
                }).sorted(Comparator.comparing(MedicaoResponses.LinhaServico::descricao, Comparator.nullsLast(Comparator.naturalOrder()))).toList(),
                materiais.entrySet().stream().map(e -> {
                    ItemPlanejado i = exemplo.get(e.getKey());
                    return new MedicaoResponses.LinhaMaterial(i.getCodigo(), i.getDescricao(), i.getOperacao(), i.getUnidade(),
                            e.getValue()[0], e.getValue()[1]);
                }).sorted(Comparator.comparing(MedicaoResponses.LinhaMaterial::descricao, Comparator.nullsLast(Comparator.naturalOrder()))).toList(),
                porKit.entrySet().stream()
                        .map(e -> new MedicaoResponses.LinhaKit(e.getKey(), e.getValue().descricao, e.getValue().lancados,
                                e.getValue().concluidos, List.copyOf(e.getValue().locais)))
                        .sorted(Comparator.comparing(MedicaoResponses.LinhaKit::codigoKit, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList(),
                medicoesDaObra);
    }

    private static final class KitResumo {
        final String descricao;
        int lancados;
        int concluidos;
        final Set<String> locais = new TreeSet<>();

        KitResumo(String descricao) {
            this.descricao = descricao;
        }
    }

    /** Uma aba por bloco, com a obra em cada linha, para filtrar no Excel. */
    @Transactional(readOnly = true)
    public byte[] exportar(String texto) {
        MedicaoResponses.Fechamento fechamento = gerar(texto);
        DateTimeFormatter mes = DateTimeFormatter.ofPattern("MM/yyyy");
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Resumo", "Obra", "Título", "Município", "Situação", "Kits lançados",
                "Kits concluídos", "Valor planejado (R$)", "Valor executado (R$)", "Valor medido (R$)", "Medições ativas");
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (MedicaoResponses.FechamentoObra o : fechamento.obras()) {
                planilha.linha(o.numero(), o.titulo(), o.municipio(), situacao(o.situacao()), o.kitsLancados(), o.kitsConcluidos(),
                        o.valorPlanejado(), o.valorExecutado(), o.valorMedido(), o.medicoesAtivas());
            }
            planilha.aba("Serviços", "Obra", "Código", "Serviço", "Operação", "Unidade", "Lançado", "Executado", "Valor executado (R$)");
            for (MedicaoResponses.FechamentoObra o : fechamento.obras()) {
                for (MedicaoResponses.LinhaServico s : o.servicos()) {
                    planilha.linha(o.numero(), s.codigo(), s.descricao(), operacao(s.operacao()), s.unidade(), s.lancado(), s.executado(), s.valorExecutado());
                }
            }
            planilha.aba("Materiais", "Obra", "Código", "Material", "Operação", "Unidade", "Lançado", "Aplicado/retirado");
            for (MedicaoResponses.FechamentoObra o : fechamento.obras()) {
                for (MedicaoResponses.LinhaMaterial m : o.materiais()) {
                    planilha.linha(o.numero(), m.codigo(), m.descricao(), operacao(m.operacao()), m.unidade(), m.lancado(), m.aplicado());
                }
            }
            planilha.aba("Kits", "Obra", "Kit", "Descrição", "Lançados", "Concluídos", "Locais");
            for (MedicaoResponses.FechamentoObra o : fechamento.obras()) {
                for (MedicaoResponses.LinhaKit k : o.kits()) {
                    planilha.linha(o.numero(), k.codigoKit(), k.descricao(), k.lancados(), k.concluidos(), String.join(", ", k.locais()));
                }
            }
            planilha.aba("Medições", "Obra", "Medição", "Tipo", "Competência", "Situação", "Valor (R$)", "Serviços", "Gerada em", "Gerada por",
                    "Estorno");
            for (MedicaoResponses.FechamentoObra o : fechamento.obras()) {
                for (MedicaoResponses.MedicaoResumo m : o.medicoes()) {
                    planilha.linha(o.numero(), "#" + m.numero(), m.tipo() == null ? null : (m.tipo().name().equals("CICLO") ? "Ciclo" : "Parcial"),
                            m.competencia() != null ? m.competencia().format(mes) : null,
                            m.status() == StatusMedicao.GERADA ? "Gerada" : "Estornada", m.valorTotal(), m.servicos(),
                            m.dataCriacao(), m.usuarioCriacao(), m.justificativaEstorno());
                }
            }
            Map<String, String> sobre = new LinkedHashMap<>();
            sobre.put("Relatório", "Fechamento de obra");
            sobre.put("Obras", String.valueOf(fechamento.obras().size()));
            sobre.put("Códigos não encontrados", String.join(", ", fechamento.naoEncontrados()));
            sobre.put("Valor planejado", "Valor vigente hoje dos serviços planejados; o executado e o medido usam o valor congelado na programação.");
            sobre.put("Gerado em", LocalDate.now(MedicaoObraUseCases.FUSO).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            planilha.procedencia(sobre);
            planilha.escrever(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar a planilha do fechamento.", e);
        }
    }

    private static String situacao(String situacao) {
        return switch (Objects.requireNonNullElse(situacao, "")) {
            case "CONCLUIDA" -> "Concluída";
            case "EM_ANDAMENTO" -> "Em andamento";
            default -> "Não iniciada";
        };
    }

    private static String operacao(OperacaoItemKit operacao) {
        return operacao == null ? null : operacao.name().charAt(0) + operacao.name().substring(1).toLowerCase(Locale.ROOT);
    }
}
