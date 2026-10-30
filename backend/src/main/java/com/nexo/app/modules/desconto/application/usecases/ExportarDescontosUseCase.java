package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.DescontoFilter;
import com.nexo.app.modules.desconto.application.dto.DescontoResponse;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportarDescontosUseCase {

    private final DescontoUseCases descontoUseCases;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    private static final int MAX_REGISTROS = 10000;
    private static final int TAMANHO_PAGINA = 500;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Exporta os dados gerais (resumo) dos descontos.
     */
    @Transactional(readOnly = true)
    public void exportarResumo(DescontoFilter filter, Writer writer) throws IOException {
        String[] headers = {
                "ID Desconto", "Data Ocorrência", "Colaborador", "Motivo",
                "Status", "Qtd Parcelas", "Valor Sugerido", "Valor Validado", "Valor Baixado"
        };
        
        CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(headers).setDelimiter(';').build();
        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginas(filter, printer, this::imprimirLinhaResumo);
        }
    }

    /**
     * Exporta os dados detalhados dos descontos.
     */
    @Transactional(readOnly = true)
    public void exportarDetalhado(DescontoFilter filter, Writer writer) throws IOException {
        String[] headers = {
                "ID Desconto", "Data Ocorrência", "Colaborador", "Motivo",
                "Status", "Qtd Parcelas", "Observações", "Qtd Itens", "Qtd Anexos",
                "Valor Sugerido", "Solicitante", "Data Criação",
                "Valor Validado", "Validador", "Data Validação",
                "Valor Baixado", "Responsável Baixa", "Data Baixa",
                "Motivo Rejeição", "Responsável Rejeição", "Data Rejeição"
        };
        
        CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(headers).setDelimiter(';').build();
        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginas(filter, printer, this::imprimirLinhaDetalhada);
        }
    }

    private void processarPaginas(DescontoFilter filter, CSVPrinter printer, 
                                  LinhaPrinter linhaPrinter) throws IOException {
        int registrosProcessados = 0;
        int numeroPagina = 0;

        while (registrosProcessados < MAX_REGISTROS) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, Sort.by("dataCriacao").descending());
            PageResponse<DescontoResponse> page = descontoUseCases.listar(filter, pageable);

            List<DescontoResponse> content = page.getContent();
            if (content.isEmpty()) {
                break;
            }

            for (DescontoResponse desconto : content) {
                if (registrosProcessados >= MAX_REGISTROS) {
                    break;
                }
                linhaPrinter.imprimir(printer, desconto);
                registrosProcessados++;
            }

            if (numeroPagina >= page.getPage().getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private void imprimirLinhaResumo(CSVPrinter printer, DescontoResponse desconto) throws IOException {
        String nomeColaborador = buscarNomeColaborador(desconto.colaboradorId());

        printer.printRecord(
                desconto.id(),
                desconto.dataOcorrencia() != null ? desconto.dataOcorrencia().format(DATE_FORMATTER) : "",
                nomeColaborador,
                desconto.motivo() != null ? desconto.motivo().descricao() : "",
                desconto.status(),
                desconto.quantidadeParcelas(),
                desconto.valorSugerido(),
                desconto.valorValidado(),
                desconto.valorBaixado()
        );
    }

    private void imprimirLinhaDetalhada(CSVPrinter printer, DescontoResponse desconto) throws IOException {
        String nomeColaborador = buscarNomeColaborador(desconto.colaboradorId());
        String nomeSolicitante = buscarNomeUsuario(desconto.usuarioSolicitanteId());
        String nomeValidador = buscarNomeUsuario(desconto.usuarioValidadorId());
        String nomeResponsavelBaixa = buscarNomeUsuario(desconto.usuarioBaixaId());
        String nomeResponsavelRejeicao = buscarNomeUsuario(desconto.usuarioRejeicaoId());

        printer.printRecord(
                desconto.id(),
                desconto.dataOcorrencia() != null ? desconto.dataOcorrencia().format(DATE_FORMATTER) : "",
                nomeColaborador,
                desconto.motivo() != null ? desconto.motivo().descricao() : "",
                desconto.status(),
                desconto.quantidadeParcelas(),
                desconto.observacoes(),
                desconto.itens() != null ? desconto.itens().size() : 0,
                desconto.anexos() != null ? desconto.anexos().size() : 0,
                desconto.valorSugerido(),
                nomeSolicitante,
                desconto.dataCriacao() != null ? desconto.dataCriacao().format(DATE_FORMATTER) : "",
                desconto.valorValidado(),
                nomeValidador,
                desconto.dataValidacao() != null ? desconto.dataValidacao().format(DATE_FORMATTER) : "",
                desconto.valorBaixado(),
                nomeResponsavelBaixa,
                desconto.dataBaixa() != null ? desconto.dataBaixa().format(DATE_FORMATTER) : "",
                desconto.motivoRejeicao(),
                nomeResponsavelRejeicao,
                desconto.dataRejeicao() != null ? desconto.dataRejeicao().format(DATE_FORMATTER) : ""
        );
    }

    private String buscarNomeColaborador(UUID id) {
        if (id == null) return "";
        return colaboradorApi.buscarPorIdResponse(id)
                .map(com.nexo.app.modules.rh.ColaboradorResponse::nomeCompleto)
                .orElse("");
    }

    private String buscarNomeUsuario(UUID id) {
        if (id == null) return "";
        return usuarioApi.buscarNomePorId(id).orElse("");
    }

    @FunctionalInterface
    private interface LinhaPrinter {
        void imprimir(CSVPrinter printer, DescontoResponse desconto) throws IOException;
    }
}
