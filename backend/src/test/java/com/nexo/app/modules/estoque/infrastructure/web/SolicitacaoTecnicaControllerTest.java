package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.CriacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.PlanejarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.KanbanSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.usecases.EncerramentoSaldoTecnicoUseCases;
import com.nexo.app.modules.estoque.application.usecases.ImportacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.KanbanSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.OperacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.ReservaSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.SeparacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoMaterialUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolicitacaoTecnicaControllerTest {

    private SolicitacaoTecnicaUseCases solicitacoes;
    private SolicitacaoMaterialUseCases consultas;
    private ReservaSolicitacaoTecnicaUseCases reservas;
    private OperacaoSolicitacaoTecnicaUseCases operacoes;
    private EncerramentoSaldoTecnicoUseCases encerramentos;
    private ImportacaoSolicitacaoTecnicaUseCases importacoes;
    private KanbanSolicitacaoTecnicaUseCases kanban;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        solicitacoes = mock(SolicitacaoTecnicaUseCases.class);
        consultas = mock(SolicitacaoMaterialUseCases.class);
        reservas = mock(ReservaSolicitacaoTecnicaUseCases.class);
        operacoes = mock(OperacaoSolicitacaoTecnicaUseCases.class);
        encerramentos = mock(EncerramentoSaldoTecnicoUseCases.class);
        importacoes = mock(ImportacaoSolicitacaoTecnicaUseCases.class);
        kanban = mock(KanbanSolicitacaoTecnicaUseCases.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SolicitacaoTecnicaController(
                solicitacoes, consultas, reservas, mock(SeparacaoSolicitacaoTecnicaUseCases.class),
                operacoes, encerramentos, importacoes, kanban))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void criaSolicitacoesIndependentesComTipoInformadoPorItem() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        when(solicitacoes.criar(any(), eq(idUsuario), eq(OrigemComandoTecnico.UI), eq("criacao-1")))
                .thenReturn(new CriacaoSolicitacaoTecnicaResponse(List.of(
                        new CriacaoSolicitacaoTecnicaResponse.SolicitacaoCriada(
                                idSolicitacao, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "222"))));

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsuarioLogadoId).thenReturn(idUsuario);
            mockMvc.perform(post("/v1/solicitacoes-material/tecnicas")
                            .header("Idempotency-Key", "criacao-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"idObra":"%s","idAlmoxarifadoAtendimento":"%s","itens":[
                                      {"idMaterial":"%s","tipoMovimentacao":"DEVOLUCAO_COMUM","quantidade":1.000}
                                    ]}
                                    """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.solicitacoesCriadas[0].id").value(idSolicitacao.toString()))
                    .andExpect(jsonPath("$.solicitacoesCriadas[0].tipo").value("DEVOLUCAO_COMUM"))
                    .andExpect(jsonPath("$.idAgrupador").doesNotExist());
        }
        verify(solicitacoes).criar(any(), eq(idUsuario), eq(OrigemComandoTecnico.UI), eq("criacao-1"));
    }

    @Test
    void rejeitaSubmissaoTecnicaSemItensAntesDoCasoDeUso() throws Exception {
        mockMvc.perform(post("/v1/solicitacoes-material/tecnicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"idObra\":\"" + UUID.randomUUID() + "\"," +
                                "\"idAlmoxarifadoAtendimento\":\"" + UUID.randomUUID() + "\"," +
                                "\"itens\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(solicitacoes);
    }

    @Test
    void planejaUmaSolicitacaoPeloIdDaRaizEDevolveEnvelopeAtualizado() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        when(consultas.buscarPorIdCompleto(idSolicitacao)).thenReturn(null);

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsuarioLogadoId).thenReturn(idUsuario);
            mockMvc.perform(put("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/planejamento", idSolicitacao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"versaoEsperada":3,
                                     "idObra":"11111111-1111-1111-1111-111111111111",
                                     "idAlmoxarifadoAtendimento":"22222222-2222-2222-2222-222222222222",
                                     "ocorrencia":null,"idEquipe":null,"dataPrevista":null,"observacao":null,
                                     "itens":[{"idItem":null,"idMaterial":"44444444-4444-4444-4444-444444444444",
                                               "tipoMovimentacao":"ADICAO_COMUM","quantidade":1.000}],
                                     "justificativa":"ajuste de planejamento"}
                                    """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.solicitacaoAtualizada").doesNotExist());
        }
        verify(solicitacoes).replanejar(eq(idSolicitacao), any(PlanejarSolicitacaoTecnicaRequest.class),
                eq(idUsuario), eq(OrigemComandoTecnico.UI));
        verify(consultas).buscarPorIdCompleto(idSolicitacao);
    }

    @Test
    void reservaAceitaAgenteResponsavelOpcional() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsuarioLogadoId).thenReturn(idUsuario);
            mockMvc.perform(put("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/reserva", idSolicitacao)
                            .header("Idempotency-Key", "reserva-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"versaoEsperada":3,"numero":"123456","dataCriacao":"2026-08-31"}
                                    """))
                    .andExpect(status().isOk());
        }
        verify(reservas).registrarOuAlterar(eq(idSolicitacao), any(RegistrarReservaTecnicaRequest.class),
                eq(idUsuario), eq(OrigemComandoTecnico.UI), eq("reserva-1"));
    }

    @Test
    void comandosComChaveExigemIdempotencia() throws Exception {
        UUID idSolicitacao = UUID.randomUUID();
        mockMvc.perform(post("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/entregas", idSolicitacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versaoEsperada\":0,\"itens\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/encerramento", idSolicitacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versaoEsperada\":3,\"motivo\":\"saldo pendente\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(operacoes, encerramentos);
    }

    @Test
    void disponibilizaModelosEValidacaoMultipartDaImportacao() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        when(importacoes.modeloCsv()).thenReturn("cabecalho".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(importacoes.validar(any(), eq(idUsuario))).thenReturn(new ImportacaoSolicitacaoTecnicaResponse(
                "token", "a".repeat(64), java.time.OffsetDateTime.now().plusMinutes(30),
                1, 1, 1, 0, 0, List.of()));
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "solicitacoes.csv", "text/csv", "conteudo".getBytes());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsuarioLogadoId).thenReturn(idUsuario);
            mockMvc.perform(get("/v1/solicitacoes-material/importacoes/modelo"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"modelo-importacao-solicitacoes-tecnicas.csv\""))
                    .andExpect(content().bytes("cabecalho".getBytes()));
            mockMvc.perform(multipart("/v1/solicitacoes-material/importacoes/validacao").file(arquivo))
                    .andExpect(status().isOk());
        }
        verify(importacoes).validar(any(), eq(idUsuario));
    }

    @Test
    void retornaKanbanSolicitacoesTecnicasComSucesso() throws Exception {
        when(kanban.obter(any())).thenReturn(new KanbanSolicitacaoTecnicaResponse(List.of()));

        mockMvc.perform(get("/v1/solicitacoes-material/tecnicas/kanban")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colunas").isArray());

        verify(kanban).obter(any());
    }
}