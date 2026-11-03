package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemPlanejadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitAplicadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Relatório de medição detalhado: lê a planilha orçado × faturado, reconhece as obras pelo número e gera, para as
 * escolhidas, um pacote ZIP com Excel, PDF e as fotos do book de cada obra. Nada da planilha é gravado; a tela guarda
 * o arquivo entre a análise e a geração.
 */
@Service
@RequiredArgsConstructor
public class RelatorioMedicaoDetalhadoUseCases {

    static final int LIMITE_OBRAS = 10;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ObraRepository obraRepository;
    private final KitAplicadoRepository kitRepository;
    private final ItemPlanejadoRepository itemRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final ApontamentoExecucaoRepository apontamentoRepository;
    private final MedicaoObraUseCases medicoes;
    private final GeracaoAsBuiltService asBuilt;
    private final DocumentoTecnicoPdf pdf;

    public byte[] modelo() {
        return PlanilhaOrcadoFaturado.modelo();
    }

    @Transactional(readOnly = true)
    public MedicaoResponses.AnaliseOrcadoFaturado analisar(MultipartFile arquivo) {
        PlanilhaOrcadoFaturado.Conteudo conteudo = PlanilhaOrcadoFaturado.ler(ArquivoImportacao.bytes(arquivo));
        Map<String, List<PlanilhaOrcadoFaturado.LinhaOrcada>> maoDeObra = PlanilhaOrcadoFaturado.porObra(conteudo.maoDeObra(), PlanilhaOrcadoFaturado.LinhaOrcada::obra);
        Map<String, List<PlanilhaOrcadoFaturado.LinhaOrcada>> materiais = PlanilhaOrcadoFaturado.porObra(conteudo.materiais(), PlanilhaOrcadoFaturado.LinhaOrcada::obra);
        Map<String, List<PlanilhaOrcadoFaturado.LinhaFaturada>> faturado = PlanilhaOrcadoFaturado.porObra(conteudo.faturado(), PlanilhaOrcadoFaturado.LinhaFaturada::obra);
        Map<String, List<String>> erros = new HashMap<>();
        List<String> errosSemObra = new ArrayList<>();
        for (PlanilhaOrcadoFaturado.Erro erro : conteudo.erros()) {
            if (erro.obra() == null) {
                errosSemObra.add(erro.texto());
            } else {
                erros.computeIfAbsent(erro.obra().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(erro.texto());
            }
        }
        Set<String> numeros = new LinkedHashSet<>();
        numeros.addAll(maoDeObra.keySet());
        numeros.addAll(materiais.keySet());
        numeros.addAll(faturado.keySet());
        numeros.addAll(erros.keySet());
        Map<String, Obra> obras = obrasPorNumero(numeros);
        Map<UUID, String> municipios = medicoes.municipios(obras.values());

        List<MedicaoResponses.ObraNaPlanilha> reconhecidas = new ArrayList<>();
        List<String> naoIdentificadas = new ArrayList<>();
        for (String numero : numeros) {
            Obra obra = obras.get(numero);
            if (obra == null) {
                naoIdentificadas.add(numero);
                continue;
            }
            List<PlanilhaOrcadoFaturado.LinhaOrcada> mo = maoDeObra.getOrDefault(numero, List.of());
            List<PlanilhaOrcadoFaturado.LinhaOrcada> mat = materiais.getOrDefault(numero, List.of());
            List<PlanilhaOrcadoFaturado.LinhaFaturada> fat = faturado.getOrDefault(numero, List.of());
            reconhecidas.add(new MedicaoResponses.ObraNaPlanilha(obra.getId(), obra.getNumero(), obra.getTitulo(),
                    municipios.get(obra.getIdMunicipio()), mo.size(), mat.size(), fat.size(),
                    somar(mo, PlanilhaOrcadoFaturado.LinhaOrcada::valorVigente), somar(mat, PlanilhaOrcadoFaturado.LinhaOrcada::valorVigente),
                    somar(fat, PlanilhaOrcadoFaturado.LinhaFaturada::valorConsiderado),
                    fat.stream().map(PlanilhaOrcadoFaturado.LinhaFaturada::data).max(Comparator.naturalOrder()).orElse(null),
                    (int) fat.stream().filter(PlanilhaOrcadoFaturado.LinhaFaturada::paraConferir).count(),
                    erros.getOrDefault(numero, List.of())));
        }
        reconhecidas.sort(Comparator.comparing(MedicaoResponses.ObraNaPlanilha::numero));
        return new MedicaoResponses.AnaliseOrcadoFaturado(reconhecidas, naoIdentificadas, errosSemObra,
                conteudo.maoDeObra().size(), conteudo.materiais().size(), conteudo.faturado().size());
    }

    /** Pacote ZIP com uma pasta por obra: Excel, PDF e, se pedido, as fotos do book. */
    @Transactional(readOnly = true)
    public byte[] gerar(MultipartFile arquivo, List<UUID> idsObras, LocalDate dataCorte, String fiscal, String responsavel,
                        boolean incluirFotos) {
        if (idsObras == null || idsObras.isEmpty()) {
            throw new InvarianteVioladaException("Escolha ao menos uma obra.");
        }
        Set<UUID> escolhidas = new LinkedHashSet<>(idsObras);
        if (escolhidas.size() > LIMITE_OBRAS) {
            throw new InvarianteVioladaException("Gere o relatório de até " + LIMITE_OBRAS + " obras por pacote.");
        }
        LocalDate hoje = LocalDate.now(MedicaoObraUseCases.FUSO);
        LocalDate corte = dataCorte != null ? dataCorte : hoje;
        if (corte.isAfter(hoje)) {
            throw new InvarianteVioladaException("A data de corte não pode ser futura.");
        }
        PlanilhaOrcadoFaturado.Conteudo conteudo = PlanilhaOrcadoFaturado.ler(ArquivoImportacao.bytes(arquivo));
        if (conteudo.erros().stream().anyMatch(e -> e.obra() == null)) {
            throw new InvarianteVioladaException("A planilha tem linhas sem o número da obra. Corrija e envie de novo.");
        }
        List<Obra> obras = obraRepository.findAllById(escolhidas).stream().filter(Obra::isAtivo)
                .sorted(Comparator.comparing(Obra::getNumero)).toList();
        if (obras.size() != escolhidas.size()) {
            throw new InvarianteVioladaException("Uma das obras escolhidas não foi encontrada.");
        }
        for (Obra obra : obras) {
            long comErro = conteudo.erros().stream().filter(e -> e.obra().equalsIgnoreCase(obra.getNumero())).count();
            if (comErro > 0) {
                throw new InvarianteVioladaException("A obra " + obra.getNumero() + " tem " + comErro + (comErro == 1 ? " linha" : " linhas")
                        + " com erro na planilha. Corrija e envie de novo.");
            }
        }
        Map<UUID, String> municipios = medicoes.municipios(obras);
        String fiscalInformado = texto(fiscal);
        String responsavelInformado = texto(responsavel);

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Obra obra : obras) {
                RelatorioMedicaoDetalhado relatorio = montar(obra, municipios.get(obra.getIdMunicipio()), conteudo, corte,
                        fiscalInformado, responsavelInformado);
                Map<String, List<DocumentoTecnicoPdf.Foto>> fotos = incluirFotos ? fotos(obra) : Map.of();
                String pasta = "relatorio-medicao-" + arquivoSeguro(obra.getNumero()) + "/";
                adicionar(zip, pasta + "relatorio-medicao-" + arquivoSeguro(obra.getNumero()) + ".xlsx", planilha(relatorio));
                adicionar(zip, pasta + "relatorio-medicao-" + arquivoSeguro(obra.getNumero()) + ".pdf", pdf.relatorioMedicao(relatorio, fotos));
                for (Map.Entry<String, List<DocumentoTecnicoPdf.Foto>> doPonto : fotos.entrySet()) {
                    int n = 1;
                    for (DocumentoTecnicoPdf.Foto foto : doPonto.getValue()) {
                        adicionar(zip, pasta + "fotos/ponto-" + arquivoSeguro(doPonto.getKey()) + "-" + String.format("%02d", n++) + ".jpg", foto.jpeg());
                    }
                }
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível montar o pacote do relatório.", e);
        }
    }

    /**
     * Junta orçado e faturado da planilha com o planejado e o executado da obra, por código e operação. Zeros à esquerda
     * não diferenciam códigos, porque a planilha costuma perdê-los.
     */
    RelatorioMedicaoDetalhado montar(Obra obra, String municipio, PlanilhaOrcadoFaturado.Conteudo conteudo, LocalDate corte,
                                     String fiscal, String responsavel) {
        String numero = obra.getNumero();
        Map<String, Acumulado> maoDeObra = new LinkedHashMap<>();
        Map<String, Acumulado> materiais = new LinkedHashMap<>();
        conteudo.maoDeObra().stream().filter(l -> l.obra().equalsIgnoreCase(numero))
                .forEach(l -> acumulado(maoDeObra, l.codigo(), l.operacao(), l.descricao(), l.unidade()).orcar(l));
        conteudo.materiais().stream().filter(l -> l.obra().equalsIgnoreCase(numero))
                .forEach(l -> acumulado(materiais, l.codigo(), l.operacao(), l.descricao(), l.unidade()).orcar(l));
        List<PlanilhaOrcadoFaturado.LinhaFaturada> faturado = conteudo.faturado().stream()
                .filter(l -> l.obra().equalsIgnoreCase(numero) && !l.data().isAfter(corte))
                .sorted(Comparator.comparing(PlanilhaOrcadoFaturado.LinhaFaturada::data).thenComparing(PlanilhaOrcadoFaturado.LinhaFaturada::linha))
                .toList();
        // O não aprovado não soma; aparece só em "Faturado para conferir".
        faturado.stream().filter(PlanilhaOrcadoFaturado.LinhaFaturada::aprovada)
                .forEach(l -> acumulado(maoDeObra, l.codigo(), l.operacao(), l.descricao(), null).faturar(l));

        Map<UUID, ApontamentoExecucaoRepository.ExecucaoDoItem> execucao = apontamentoRepository.somarPorItemDaObraAte(obra.getId(), corte).stream()
                .collect(Collectors.toMap(ApontamentoExecucaoRepository.ExecucaoDoItem::idItem, Function.identity()));
        for (KitAplicado kit : kitRepository.listarComItens(obra.getId())) {
            for (ItemPlanejado item : kit.getItensAtivos()) {
                Map<String, Acumulado> destino = item.getTipo() == TipoItemKit.SERVICO ? maoDeObra : materiais;
                Acumulado acumulado = acumulado(destino, item.getCodigo(), item.getOperacao(), item.getDescricao(), item.getUnidade());
                acumulado.planejar(item.getQuantidadePrevista(), item.getCodigo());
                ApontamentoExecucaoRepository.ExecucaoDoItem executado = execucao.get(item.getId());
                if (executado != null) {
                    acumulado.executar(executado.quantidade(), item.getTipo() == TipoItemKit.SERVICO ? executado.valor() : null);
                }
            }
        }
        List<MedicaoResponses.MedicaoResumo> medicoesDaObra = medicoes.resumosDaObra(obra).stream()
                .filter(m -> m.dataCriacao() == null || !m.dataCriacao().atZoneSameInstant(MedicaoObraUseCases.FUSO).toLocalDate().isAfter(corte))
                .toList();
        return new RelatorioMedicaoDetalhado(obra, municipio, corte, fiscal, responsavel, linhas(maoDeObra), linhas(materiais),
                faturado, medicoesDaObra);
    }

    private static Acumulado acumulado(Map<String, Acumulado> mapa, String codigo, OperacaoItemKit operacao, String descricao, String unidade) {
        String chave = codigo.trim().toUpperCase(Locale.ROOT).replaceFirst("^0+(?=.)", "") + "|" + operacao;
        Acumulado acumulado = mapa.computeIfAbsent(chave, k -> new Acumulado(codigo.trim(), operacao));
        if (acumulado.descricao == null && descricao != null) {
            acumulado.descricao = descricao;
        }
        if (acumulado.unidade == null && unidade != null) {
            acumulado.unidade = unidade;
        }
        return acumulado;
    }

    private static List<RelatorioMedicaoDetalhado.Linha> linhas(Map<String, Acumulado> mapa) {
        return mapa.values().stream().map(Acumulado::linha)
                .sorted(Comparator.comparing(RelatorioMedicaoDetalhado.Linha::codigo).thenComparing(RelatorioMedicaoDetalhado.Linha::operacao))
                .toList();
    }

    /** Somas de um código e operação; cada campo fica nulo enquanto a fonte dele não aparece. */
    private static final class Acumulado {
        private String codigo;
        private final OperacaoItemKit operacao;
        private String descricao;
        private String unidade;
        private BigDecimal orcadoQuantidade;
        private BigDecimal orcadoValor;
        private BigDecimal planejadoQuantidade;
        private BigDecimal executadoQuantidade;
        private BigDecimal executadoValor;
        private BigDecimal faturadoQuantidade;
        private BigDecimal faturadoValor;

        Acumulado(String codigo, OperacaoItemKit operacao) {
            this.codigo = codigo;
            this.operacao = operacao;
        }

        void orcar(PlanilhaOrcadoFaturado.LinhaOrcada linha) {
            orcadoQuantidade = mais(orcadoQuantidade, linha.quantidade());
            orcadoValor = mais(orcadoValor, linha.valorVigente());
        }

        void faturar(PlanilhaOrcadoFaturado.LinhaFaturada linha) {
            faturadoQuantidade = mais(faturadoQuantidade, linha.quantidadeConsiderada());
            faturadoValor = mais(faturadoValor, linha.valorConsiderado());
        }

        /** O código do sistema prevalece sobre o da planilha, que pode ter perdido zeros. */
        void planejar(BigDecimal quantidade, String codigoDoSistema) {
            planejadoQuantidade = mais(planejadoQuantidade, quantidade);
            codigo = codigoDoSistema.trim();
        }

        void executar(BigDecimal quantidade, BigDecimal valor) {
            executadoQuantidade = mais(executadoQuantidade, quantidade);
            if (valor != null) {
                executadoValor = mais(executadoValor, valor);
            }
        }

        RelatorioMedicaoDetalhado.Linha linha() {
            return new RelatorioMedicaoDetalhado.Linha(codigo, operacao, descricao, unidade, orcadoQuantidade, orcadoValor, planejadoQuantidade,
                    executadoQuantidade, executadoValor, faturadoQuantidade, faturadoValor);
        }

        private static BigDecimal mais(BigDecimal atual, BigDecimal valor) {
            if (valor == null) {
                return atual;
            }
            return atual == null ? valor : atual.add(valor);
        }
    }

    /** Fotos do book dos pontos executados, pelo número do ponto. */
    private Map<String, List<DocumentoTecnicoPdf.Foto>> fotos(Obra obra) throws IOException {
        Set<UUID> executados = Set.copyOf(itemRepository.pontosComExecucao(obra.getId()));
        if (executados.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<DocumentoTecnicoPdf.Foto>> porPonto = asBuilt.fotos(obra.getId(), executados);
        Map<String, List<DocumentoTecnicoPdf.Foto>> porNumero = new LinkedHashMap<>();
        for (PontoPlanejamento ponto : pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(obra.getId())) {
            List<DocumentoTecnicoPdf.Foto> fotos = porPonto.get(ponto.getId());
            if (fotos != null && !fotos.isEmpty()) {
                porNumero.merge(ponto.getNumero(), fotos, (a, b) -> {
                    List<DocumentoTecnicoPdf.Foto> juntas = new ArrayList<>(a);
                    juntas.addAll(b);
                    return juntas;
                });
            }
        }
        return porNumero;
    }

    byte[] planilha(RelatorioMedicaoDetalhado r) {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Resumo", "Indicador", "Valor (R$)");
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            planilha.linha("Orçado de mão de obra", r.orcadoMaoDeObra());
            planilha.linha("Orçado de materiais", r.orcadoMateriais());
            planilha.linha("Executado até a data de corte", r.executado());
            planilha.linha("Faturado até a data de corte", r.faturadoTotal());
            planilha.linha("Executado a faturar", r.aFaturar());
            planilha.linha("Saldo do orçamento de mão de obra", r.saldoOrcado());
            planilha.linha("Medido no sistema", r.medidoNoSistema());

            planilha.aba("Mão de obra", "Código", "Serviço", "Operação", "Unidade", "Orçado", "Orçado (R$)", "Planejado",
                    "Executado", "Executado (R$)", "Faturado", "Faturado (R$)", "A faturar (R$)", "Saldo do orçado (R$)", "Acima do orçado");
            for (RelatorioMedicaoDetalhado.Linha l : r.maoDeObra()) {
                planilha.linha(l.codigo(), l.descricao(), operacao(l.operacao()), l.unidade(), l.orcadoQuantidade(), l.orcadoValor(),
                        l.planejadoQuantidade(), l.executadoQuantidade(), l.executadoValor(), l.faturadoQuantidade(), l.faturadoValor(),
                        l.aFaturar(), l.saldoOrcado(), l.acimaDoOrcado() ? "Sim" : null);
            }
            planilha.aba("Materiais", "Código", "Material", "Operação", "Unidade", "Orçado", "Orçado (R$)", "Planejado",
                    "Aplicado/retirado", "Acima do orçado");
            for (RelatorioMedicaoDetalhado.Linha l : r.materiais()) {
                planilha.linha(l.codigo(), l.descricao(), operacao(l.operacao()), l.unidade(), l.orcadoQuantidade(), l.orcadoValor(),
                        l.planejadoQuantidade(), l.executadoQuantidade(), l.acimaDoOrcado() ? "Sim" : null);
            }
            planilha.aba("Faturado", "Data da medição", "Código", "Descrição", "Operação", "Quantidade", "Valor (R$)", "Situação",
                    "Débito/crédito", "Considerado (R$)", "Conferir", "Linha na planilha");
            for (PlanilhaOrcadoFaturado.LinhaFaturada l : r.faturado()) {
                planilha.linha(l.data(), l.codigo(), l.descricao(), operacao(l.operacao()), l.quantidade(), l.valorTotal(), l.situacao(),
                        l.credito() ? "Crédito" : "Débito", l.valorConsiderado(), l.paraConferir() ? "Sim" : null, l.linha());
            }
            planilha.aba("Medições", "Medição", "Tipo", "Competência", "Situação", "Valor (R$)", "Serviços", "Gerada em", "Gerada por");
            for (MedicaoResponses.MedicaoResumo m : r.medicoes()) {
                planilha.linha("#" + m.numero(), m.tipo() == null ? null : (m.tipo().name().equals("CICLO") ? "Ciclo" : "Parcial"),
                        m.competencia() != null ? m.competencia().format(DateTimeFormatter.ofPattern("MM/yyyy")) : null,
                        m.status().name().equals("GERADA") ? "Gerada" : "Estornada", m.valorTotal(), m.servicos(), m.dataCriacao(), m.usuarioCriacao());
            }
            Map<String, String> sobre = new LinkedHashMap<>();
            sobre.put("Relatório", "Medição detalhada da obra " + r.obra().getNumero());
            sobre.put("Obra", r.obra().getTitulo());
            sobre.put("Município", r.municipio());
            sobre.put("Data de corte", r.dataCorte().format(DATA));
            sobre.put("Fiscal", r.fiscal());
            sobre.put("Responsável", r.responsavel());
            sobre.put("Orçado e faturado", "Da planilha enviada. O orçado usa o valor atualizado quando houver. O faturado soma os lançamentos aprovados até a data de corte; crédito subtrai.");
            sobre.put("Planejado e executado", "Do sistema. O executado soma os apontamentos até a data de corte, pelo valor congelado na programação.");
            sobre.put("Gerado em", LocalDate.now(MedicaoObraUseCases.FUSO).format(DATA));
            planilha.procedencia(sobre);
            planilha.escrever(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar a planilha do relatório.", e);
        }
    }

    private Map<String, Obra> obrasPorNumero(Collection<String> numeros) {
        if (numeros.isEmpty()) {
            return Map.of();
        }
        return obraRepository.findByNumeroInAndAtivoTrue(numeros).stream()
                .collect(Collectors.toMap(o -> o.getNumero().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    }

    private static <T> BigDecimal somar(List<T> linhas, Function<T, BigDecimal> valor) {
        return linhas.stream().map(valor).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void adicionar(ZipOutputStream zip, String nome, byte[] conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo);
        zip.closeEntry();
    }

    static String operacao(OperacaoItemKit operacao) {
        return operacao == null ? null : operacao == OperacaoItemKit.INSTALAR ? "Instalar" : "Retirar";
    }

    private static String arquivoSeguro(String texto) {
        return texto.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
