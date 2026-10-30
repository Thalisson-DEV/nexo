package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaFilter;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaResponse;
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
public class ExportarInspecoesUseCase {

    private final InspecaoSegurancaUseCases inspecaoUseCases;

    private static final int MAX_REGISTROS = 10000;
    private static final int TAMANHO_PAGINA = 500;
    private static final String[] CABECALHO_RESUMO = {
                "ID Inspeção", "Data Realização", "Status", "Tipo de Inspeção", "Formulário",
                "Inspetor", "Processo", "Base Operacional", "Qtd Colaboradores", "Qtd Desvios"
        };
    private static final String[] CABECALHO_DETALHADO = {
                "ID Inspeção", "Data Realização", "Status", "Tipo de Inspeção", "Formulário",
                "Inspetor", "Processo", "Base Operacional", "Veículo", "Equipe",
                "Latitude", "Longitude", "Observações", 
                "Colaboradores Inspecionados", "Qtd Respostas", "Qtd Desvios",
                "Desvios Pendentes", "Desvios Tratados"
        };

    /**
     * Exporta os dados gerais (resumo) das inspeções.
     */
    @Transactional(readOnly = true)
    public void exportarResumo(InspecaoSegurancaFilter filter, UUID idInspetor, Writer writer) throws IOException {
        String[] headers = CABECALHO_RESUMO;
        
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader(headers)
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginas(filter, idInspetor, CsvExportacao.para(printer), this::imprimirLinhaResumo);
        }
    }

    /**
     * Exporta os dados detalhados das inspeções, incluindo observações,
     * detalhes de veículo/equipe e resumo dos desvios.
     */
    @Transactional(readOnly = true)
    public void exportarDetalhado(InspecaoSegurancaFilter filter, UUID idInspetor, Writer writer) throws IOException {
        String[] headers = CABECALHO_DETALHADO;

        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader(headers)
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginas(filter, idInspetor, CsvExportacao.para(printer), this::imprimirLinhaDetalhada);
        }
    }


    /** Mesmo conteudo de {@link #exportarResumo} em XLSX (tipos nativos, cabecalho congelado, aba "Sobre"). */
    @Transactional(readOnly = true)
    public void exportarResumoXlsx(InspecaoSegurancaFilter filter, UUID idInspetor, OutputStream saida) throws IOException {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Inspeções", CABECALHO_RESUMO)) {
            processarPaginas(filter, idInspetor, planilha::linha, this::imprimirLinhaResumo);
            planilha.procedencia(procedencia(filter, idInspetor, planilha.linhasEscritas()));
            planilha.escrever(saida);
        }
    }

    /** Mesmo conteudo de {@link #exportarDetalhado} em XLSX (tipos nativos, cabecalho congelado, aba "Sobre"). */
    @Transactional(readOnly = true)
    public void exportarDetalhadoXlsx(InspecaoSegurancaFilter filter, UUID idInspetor, OutputStream saida) throws IOException {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Inspeções", CABECALHO_DETALHADO)) {
            processarPaginas(filter, idInspetor, planilha::linha, this::imprimirLinhaDetalhada);
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

    private void processarPaginas(InspecaoSegurancaFilter filter, UUID idInspetor, LinhaExportacao saida, 
                                  LinhaPrinter linhaPrinter) throws IOException {
        int registrosProcessados = 0;
        int numeroPagina = 0;

        while (registrosProcessados < MAX_REGISTROS) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, Sort.by("dataRealizacao").descending());
            PageResponse<InspecaoSegurancaResponse> page;
            
            if (idInspetor != null) {
                page = inspecaoUseCases.listarPorInspetor(filter, pageable, idInspetor);
            } else {
                page = inspecaoUseCases.listar(filter, pageable);
            }

            List<InspecaoSegurancaResponse> content = page.getContent();
            if (content.isEmpty()) {
                break;
            }

            for (InspecaoSegurancaResponse inspecao : content) {
                if (registrosProcessados >= MAX_REGISTROS) {
                    break;
                }
                linhaPrinter.imprimir(saida, inspecao);
                registrosProcessados++;
            }

            if (numeroPagina >= page.getPage().getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private void imprimirLinhaResumo(LinhaExportacao saida, InspecaoSegurancaResponse inspecao) throws IOException {
        String tipoInspecao = inspecao.tipoInspecao() != null ? inspecao.tipoInspecao().nome() : "";
        saida.escrever(
                inspecao.id(),
                inspecao.dataRealizacao(),
                inspecao.status(),
                tipoInspecao,
                inspecao.formulario() != null ? inspecao.formulario().titulo() : "",
                inspecao.nomeInspetor(),
                inspecao.processo() != null ? inspecao.processo().nome() : "",
                inspecao.baseOperacional() != null ? inspecao.baseOperacional().nome() : "",
                inspecao.colaboradoresInspecionados().size(),
                inspecao.desvios().size()
        );
    }

    private void imprimirLinhaDetalhada(LinhaExportacao saida, InspecaoSegurancaResponse inspecao) throws IOException {
        String tipoInspecao = inspecao.tipoInspecao() != null ? inspecao.tipoInspecao().nome() : "";
        String veiculo = inspecao.veiculo() != null ? inspecao.veiculo().placa() : "";
        String equipe = inspecao.equipe() != null ? inspecao.equipe().codigo() : "";
        
        String colaboradores = inspecao.colaboradoresInspecionados().stream()
                .map(c -> c != null ? c.nomeCompleto() : "")
                .collect(Collectors.joining(", "));
                
        long desviosPendentes = inspecao.desvios().stream()
                .filter(d -> d.status() != com.nexo.app.modules.sst.domain.StatusDesvio.RESOLVIDO)
                .count();
                
        long desviosTratados = inspecao.desvios().stream()
                .filter(d -> d.status() == com.nexo.app.modules.sst.domain.StatusDesvio.RESOLVIDO)
                .count();

        saida.escrever(
                inspecao.id(),
                inspecao.dataRealizacao(),
                inspecao.status(),
                tipoInspecao,
                inspecao.formulario() != null ? inspecao.formulario().titulo() : "",
                inspecao.nomeInspetor(),
                inspecao.processo() != null ? inspecao.processo().nome() : "",
                inspecao.baseOperacional() != null ? inspecao.baseOperacional().nome() : "",
                veiculo,
                equipe,
                inspecao.latitude(),
                inspecao.longitude(),
                inspecao.observacoes(),
                colaboradores,
                inspecao.respostas().size(),
                inspecao.desvios().size(),
                desviosPendentes,
                desviosTratados
        );
    }

    @FunctionalInterface
    private interface LinhaPrinter {
        void imprimir(LinhaExportacao saida, InspecaoSegurancaResponse inspecao) throws IOException;
    }
}
