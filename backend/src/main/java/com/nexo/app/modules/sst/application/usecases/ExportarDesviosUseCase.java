package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.DesvioSegurancaResponse;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaFilter;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaResponse;
import com.nexo.app.modules.sst.application.dto.OpcaoRespostaResponse;
import com.nexo.app.modules.sst.application.dto.PerguntaResponse;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import com.nexo.app.shared.application.exportacao.CsvExportacao;
import com.nexo.app.shared.application.exportacao.LinhaExportacao;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportarDesviosUseCase {

    private final InspecaoSegurancaUseCases inspecaoUseCases;
    private final FormularioInspecaoUseCases formularioUseCases;

    private static final int MAX_REGISTROS = 10000;
    private static final int TAMANHO_PAGINA = 500;
    private static final String[] CABECALHO_RESUMO = {
                "ID Desvio", "ID Inspeção", "Status Inspeção",
                "Gravidade", "Status Desvio", "Prazo", "Qtd Colaboradores Envolvidos", "Qtd Anexos"
        };
    private static final String[] CABECALHO_DETALHADO = {
                "ID Desvio", "ID Inspeção", "Data Inspeção", "Inspetor",
                "Formulário", "Processo", "Base Operacional", "Gravidade", "Status Desvio",
                "Pergunta", "Resposta (Opção)", "Descrição do Desvio",
                "Ação Corretiva", "Prazo", "Colaboradores Envolvidos", "Qtd Anexos"
        };

    /**
     * Exporta os dados gerais (resumo) dos desvios.
     */
    @Transactional(readOnly = true)
    public void exportarResumo(InspecaoSegurancaFilter filter, UUID idInspetor, Writer writer) throws IOException {
        String[] headers = CABECALHO_RESUMO;
        
        CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(headers).setDelimiter(';').build();
        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginasDesvios(filter, idInspetor, CsvExportacao.para(printer), this::imprimirLinhaDesvioResumo);
        }
    }

    /**
     * Exporta os dados detalhados dos desvios.
     */
    @Transactional(readOnly = true)
    public void exportarDetalhado(InspecaoSegurancaFilter filter, UUID idInspetor, Writer writer) throws IOException {
        String[] headers = CABECALHO_DETALHADO;
        
        CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(headers).setDelimiter(';').build();
        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginasDesvios(filter, idInspetor, CsvExportacao.para(printer), this::imprimirLinhaDesvioDetalhado);
        }
    }


    /** Mesmo conteudo de {@link #exportarResumo} em XLSX (tipos nativos, cabecalho congelado, aba "Sobre"). */
    @Transactional(readOnly = true)
    public void exportarResumoXlsx(InspecaoSegurancaFilter filter, UUID idInspetor, OutputStream saida) throws IOException {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Desvios", CABECALHO_RESUMO)) {
            processarPaginasDesvios(filter, idInspetor, planilha::linha, this::imprimirLinhaDesvioResumo);
            planilha.procedencia(procedencia(filter, idInspetor, planilha.linhasEscritas()));
            planilha.escrever(saida);
        }
    }

    /** Mesmo conteudo de {@link #exportarDetalhado} em XLSX (tipos nativos, cabecalho congelado, aba "Sobre"). */
    @Transactional(readOnly = true)
    public void exportarDetalhadoXlsx(InspecaoSegurancaFilter filter, UUID idInspetor, OutputStream saida) throws IOException {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Desvios", CABECALHO_DETALHADO)) {
            processarPaginasDesvios(filter, idInspetor, planilha::linha, this::imprimirLinhaDesvioDetalhado);
            planilha.procedencia(procedencia(filter, idInspetor, planilha.linhasEscritas()));
            planilha.escrever(saida);
        }
    }

    private static java.util.Map<String, String> procedencia(InspecaoSegurancaFilter filter, UUID idInspetor, int linhas) {
        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("Gerado em", java.time.OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        info.put("Registros", linhas + (linhas >= MAX_REGISTROS ? " (limite de " + MAX_REGISTROS + " atingido)" : ""));
        info.put("Realizadas de", filter.dataRealizacaoDe() == null ? "—" : filter.dataRealizacaoDe().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        info.put("Realizadas até", filter.dataRealizacaoAte() == null ? "—" : filter.dataRealizacaoAte().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        info.put("Somente minhas inspeções", idInspetor != null ? "sim" : "não");
        return info;
    }

    private void processarPaginasDesvios(InspecaoSegurancaFilter filter, UUID idInspetor, LinhaExportacao saida, 
                                         DesvioPrinter desvioPrinter) throws IOException {
        int registrosProcessados = 0;
        int numeroPagina = 0;
        InspecaoSegurancaFilter filtroDesvios = filter.somenteComDesvios();

        while (registrosProcessados < MAX_REGISTROS) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, Sort.by("dataRealizacao").descending());
            PageResponse<InspecaoSegurancaResponse> page;
            
            if (idInspetor != null) {
                page = inspecaoUseCases.listarPorInspetor(filtroDesvios, pageable, idInspetor);
            } else {
                page = inspecaoUseCases.listar(filtroDesvios, pageable);
            }

            List<InspecaoSegurancaResponse> content = page.getContent();
            if (content.isEmpty()) {
                break;
            }

            for (InspecaoSegurancaResponse inspecao : content) {
                for (DesvioSegurancaResponse desvio : inspecao.desvios()) {
                    if (!SelecaoMultipla.contem(filter.gravidadeDesvio(), desvio.gravidade())) {
                        continue;
                    }
                    if (registrosProcessados >= MAX_REGISTROS) {
                        return; // Excedeu o máximo, interrompe completamente
                    }
                    desvioPrinter.imprimir(saida, inspecao, desvio);
                    registrosProcessados++;
                }
            }

            if (numeroPagina >= page.getPage().getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private void imprimirLinhaDesvioResumo(LinhaExportacao saida, InspecaoSegurancaResponse inspecao, DesvioSegurancaResponse desvio) throws IOException {
        saida.escrever(
                desvio.id(),
                inspecao.id(),
                inspecao.status(),
                desvio.gravidade(),
                desvio.status(),
                desvio.prazo(),
                desvio.colaboradores() != null ? desvio.colaboradores().size() : 0,
                desvio.anexos() != null ? desvio.anexos().size() : 0
        );
    }

    private void imprimirLinhaDesvioDetalhado(LinhaExportacao saida, InspecaoSegurancaResponse inspecao, DesvioSegurancaResponse desvio) throws IOException {
        String textoPergunta = "";
        String textoOpcao = "";
        
        if (inspecao.formulario() != null && inspecao.formulario().id() != null) {
            FormularioInspecaoResponse form = formularioUseCases.buscarPorId(inspecao.formulario().id());
            if (form != null && form.grupos() != null) {
                PerguntaResponse pergunta = form.grupos().stream()
                        .flatMap(g -> g.perguntas().stream())
                        .filter(p -> p.id().equals(desvio.idPergunta()))
                        .findFirst().orElse(null);
                        
                if (pergunta != null) {
                    textoPergunta = pergunta.enunciado();
                    if (desvio.idOpcaoResposta() != null && pergunta.opcoes() != null) {
                        OpcaoRespostaResponse opcao = pergunta.opcoes().stream()
                                .filter(o -> o.id().equals(desvio.idOpcaoResposta()))
                                .findFirst().orElse(null);
                        if (opcao != null) {
                            textoOpcao = opcao.texto();
                        }
                    }
                }
            }
        }
        
        String colaboradores = desvio.colaboradores() != null ? 
                desvio.colaboradores().stream()
                        .map(c -> c != null ? c.nomeCompleto() : "")
                        .collect(Collectors.joining(", ")) : "";

        saida.escrever(
                desvio.id(),
                inspecao.id(),
                inspecao.dataRealizacao(),
                inspecao.nomeInspetor(),
                inspecao.formulario() != null ? inspecao.formulario().titulo() : "",
                inspecao.processo() != null ? inspecao.processo().nome() : "",
                inspecao.baseOperacional() != null ? inspecao.baseOperacional().nome() : "",
                desvio.gravidade(),
                desvio.status(),
                textoPergunta,
                textoOpcao,
                desvio.descricao(),
                desvio.acaoCorretiva(),
                desvio.prazo(),
                colaboradores,
                desvio.anexos() != null ? desvio.anexos().size() : 0
        );
    }

    @FunctionalInterface
    private interface DesvioPrinter {
        void imprimir(LinhaExportacao saida, InspecaoSegurancaResponse inspecao, DesvioSegurancaResponse desvio) throws IOException;
    }
}
