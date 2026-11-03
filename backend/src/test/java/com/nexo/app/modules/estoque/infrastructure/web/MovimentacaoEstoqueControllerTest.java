package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoMovimentacaoRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoMovimentacaoResponse;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ProcessamentoImportacao;
import com.nexo.app.modules.estoque.application.services.AssinaturaTransferenciaService;
import com.nexo.app.modules.estoque.application.usecases.ImportarMovimentacaoEstoqueUseCase;
import com.nexo.app.modules.estoque.application.usecases.MovimentacaoEstoqueUseCases;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MovimentacaoEstoqueControllerTest {

    private MovimentacaoEstoqueUseCases useCases;
    private AssinaturaTransferenciaService assinaturaService;
    private ImportarMovimentacaoEstoqueUseCase importUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCases = mock(MovimentacaoEstoqueUseCases.class);
        assinaturaService = mock(AssinaturaTransferenciaService.class);
        importUseCase = mock(ImportarMovimentacaoEstoqueUseCase.class);

        MovimentacaoEstoqueController controller = new MovimentacaoEstoqueController(
                useCases, null, assinaturaService, importUseCase
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Deve validar arquivo CSV via endpoint multipart")
    void deveValidarArquivoCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "movs.csv", "text/csv", "conteudo".getBytes(StandardCharsets.UTF_8)
        );

        MovimentacaoImportacaoDTO dto = new MovimentacaoImportacaoDTO(
                2, "ENTRADA", null, null, "ALMOX CENTRAL", UUID.randomUUID(),
                "MAT-001", UUID.randomUUID(), "RADIO", true,
                new BigDecimal("2"), "SN-01, SN-02", null, null,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null, null
        );

        ImportacaoMovimentacaoResponse response = new ImportacaoMovimentacaoResponse(
                List.of(dto), 1, 1, 0, ProcessamentoImportacao.validacao(1, 0)
        );

        when(importUseCase.executarValidacao(any())).thenReturn(response);

        mockMvc.perform(multipart("/v1/movimentacoes-estoque/importar/validar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(1))
                .andExpect(jsonPath("$.totalSucesso").value(1))
                .andExpect(jsonPath("$.totalFalha").value(0))
                .andExpect(jsonPath("$.registros[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.registros[0].numSerie").value("SN-01, SN-02"));

        verify(importUseCase).executarValidacao(any());
    }

    @Test
    @DisplayName("Deve confirmar importação via POST JSON")
    void deveConfirmarImportacao() throws Exception {
        MovimentacaoImportacaoDTO dto = new MovimentacaoImportacaoDTO(
                2, "ENTRADA", null, null, "ALMOX CENTRAL", UUID.randomUUID(),
                "MAT-001", UUID.randomUUID(), "RADIO", true,
                new BigDecimal("1"), "SN-01", null, null,
                ImportacaoStatus.CONCLUIDO, null, null
        );

        ImportacaoMovimentacaoResponse response = new ImportacaoMovimentacaoResponse(
                List.of(dto), 1, 1, 0, ProcessamentoImportacao.confirmacao(1, 0)
        );

        when(importUseCase.confirmarImportacao(any(), any())).thenReturn(response);

        String jsonPayload = """
                {
                    "registros": [
                        {
                            "linha": 2,
                            "tipo": "ENTRADA",
                            "nomeEstoqueDestino": "ALMOX CENTRAL",
                            "codigoMaterial": "MAT-001",
                            "quantidade": 1,
                            "numSerie": "SN-01"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/v1/movimentacoes-estoque/importar/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucesso").value(1))
                .andExpect(jsonPath("$.registros[0].status").value("CONCLUIDO"));

        verify(importUseCase).confirmarImportacao(any(), any());
    }

    @Test
    @DisplayName("Deve baixar modelo CSV com cabeçalho attachment e UTF-8")
    void deveBaixarModeloCsv() throws Exception {
        byte[] csvBytes = "\uFEFFTipo Movimentacao;Estoque Origem".getBytes(StandardCharsets.UTF_8);
        when(importUseCase.obterModeloCsv()).thenReturn(csvBytes);

        mockMvc.perform(get("/v1/movimentacoes-estoque/importar/modelo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"modelo-importacao-movimentacoes-estoque.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(content().bytes(csvBytes));

        verify(importUseCase).obterModeloCsv();
    }
}

