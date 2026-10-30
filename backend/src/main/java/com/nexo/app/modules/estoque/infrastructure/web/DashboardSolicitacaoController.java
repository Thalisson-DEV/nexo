package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.DashboardSolicitacaoFilter;
import com.nexo.app.modules.estoque.application.dto.DashboardSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesFilter;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse;
import com.nexo.app.modules.estoque.application.usecases.DashboardSolicitacaoUseCases;
import com.nexo.app.modules.estoque.application.usecases.ExportarPainelSolicitacoesUseCase;
import com.nexo.app.modules.estoque.application.usecases.PainelSolicitacoesUseCase;
import com.nexo.app.modules.estoque.doc.DashboardSolicitacaoControllerDoc;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/v1/dashboard/solicitacoes")
@RequiredArgsConstructor
public class DashboardSolicitacaoController implements DashboardSolicitacaoControllerDoc {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardSolicitacaoUseCases useCases;
    private final PainelSolicitacoesUseCase painelUseCase;
    private final ExportarPainelSolicitacoesUseCase exportarUseCase;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<DashboardSolicitacaoResponse> obterMetricas(@ParameterObject DashboardSolicitacaoFilter filter) {
        return ResponseEntity.ok(useCases.obterMetricas(filter));
    }

    @Override
    @GetMapping("/analitico")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<PainelSolicitacoesResponse> obterPainel(@ParameterObject PainelSolicitacoesFilter filter) {
        return ResponseEntity.ok(painelUseCase.obter(filter));
    }

    /** CSV (padrão, com BOM) ou XLSX (`formato=xlsx`), no mesmo modelo de inspeções e desvios. */
    @Override
    @GetMapping("/analitico/exportar")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<StreamingResponseBody> exportarPainel(@ParameterObject PainelSolicitacoesFilter filter,
                                                                @ParameterObject ExportarPainelSolicitacoesRequest pedido) {
        boolean planilha = pedido.planilha();
        String nome = ExportarPainelSolicitacoesUseCase.nomeArquivo(filter, pedido) + (planilha ? ".xlsx" : ".csv");
        // Os dados são lidos aqui, antes de a resposta começar; o streaming só escreve os bytes.
        ExportarPainelSolicitacoesUseCase.Exportacao exportacao = exportarUseCase.montar(filter, pedido);
        StreamingResponseBody corpo = planilha
                ? saida -> ExportarPainelSolicitacoesUseCase.escreverXlsx(exportacao, saida)
                : saida -> {
                    Writer writer = new OutputStreamWriter(saida, StandardCharsets.UTF_8);
                    writer.write('\uFEFF'); // BOM: acentos corretos no Excel
                    ExportarPainelSolicitacoesUseCase.escreverCsv(exportacao, writer);
                };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(planilha ? XLSX : MediaType.parseMediaType("text/csv"))
                .body(corpo);
    }
}
