package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.usecases.EncerramentoSaldoTecnicoUseCases;
import com.nexo.app.modules.estoque.application.usecases.HistoricoSolicitacaoUseCases;
import com.nexo.app.modules.estoque.application.usecases.ImportacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.OperacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.ReservaSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.SeparacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoMaterialUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.services.OperacaoSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.services.AssinaturaSolicitacaoMaterialService;
import com.nexo.app.modules.estoque.application.usecases.ExportarSolicitacoesMaterialUseCase;
import com.nexo.app.modules.estoque.application.usecases.ImportarSolicitacaoComercialUseCase;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.Paged;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolicitacaoTecnicaFeatureFlagTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SolicitacaoTecnicaController.class, SolicitacaoController.class)
            .withBean(SolicitacaoTecnicaUseCases.class, () -> mock(SolicitacaoTecnicaUseCases.class))
            .withBean(ReservaSolicitacaoTecnicaUseCases.class, () -> mock(ReservaSolicitacaoTecnicaUseCases.class))
            .withBean(SeparacaoSolicitacaoTecnicaUseCases.class, () -> mock(SeparacaoSolicitacaoTecnicaUseCases.class))
            .withBean(OperacaoSolicitacaoTecnicaUseCases.class, () -> mock(OperacaoSolicitacaoTecnicaUseCases.class))
            .withBean(EncerramentoSaldoTecnicoUseCases.class, () -> mock(EncerramentoSaldoTecnicoUseCases.class))
            .withBean(HistoricoSolicitacaoUseCases.class, () -> mock(HistoricoSolicitacaoUseCases.class))
            .withBean(ImportacaoSolicitacaoTecnicaUseCases.class, () -> mock(ImportacaoSolicitacaoTecnicaUseCases.class))
            .withBean(SolicitacaoMaterialUseCases.class, () -> mock(SolicitacaoMaterialUseCases.class))
            .withBean(OperacaoSolicitacaoComercialService.class,
                    () -> mock(OperacaoSolicitacaoComercialService.class))
            .withBean(AssinaturaSolicitacaoMaterialService.class,
                    () -> mock(AssinaturaSolicitacaoMaterialService.class))
            .withBean(ExportarSolicitacoesMaterialUseCase.class,
                    () -> mock(ExportarSolicitacoesMaterialUseCase.class))
            .withBean(ImportarSolicitacaoComercialUseCase.class,
                    () -> mock(ImportarSolicitacaoComercialUseCase.class))
            .withBean(com.nexo.app.modules.estoque.application.usecases.PendenciaSolicitacaoUseCases.class,
                    () -> mock(com.nexo.app.modules.estoque.application.usecases.PendenciaSolicitacaoUseCases.class))
            .withBean(com.nexo.app.modules.estoque.application.usecases.KanbanSolicitacaoTecnicaUseCases.class,
                    () -> mock(com.nexo.app.modules.estoque.application.usecases.KanbanSolicitacaoTecnicaUseCases.class))
            .withBean(com.nexo.app.modules.estoque.application.usecases.ConsumoAnteriorSolicitacaoUseCase.class,
                    () -> mock(com.nexo.app.modules.estoque.application.usecases.ConsumoAnteriorSolicitacaoUseCase.class))
            .withBean(com.nexo.app.modules.estoque.application.usecases.ReservaSerieSolicitacaoUseCase.class,
                    () -> mock(com.nexo.app.modules.estoque.application.usecases.ReservaSerieSolicitacaoUseCase.class));

    @Test
    void ausenteMantemTecnicoDesligadoPorPadraoSeguro() {
        runner.run(contexto -> {
            assertThat(contexto).doesNotHaveBean(SolicitacaoTecnicaController.class);
            assertThat(contexto).hasSingleBean(SolicitacaoController.class);
        });
    }

    @Test
    void desabilitadoRemoveRotaTecnicaEPreservaHttpComercial() {
        runner.withPropertyValues("ces.estoque.solicitacoes-tecnicas.enabled=false")
                .run(contexto -> {
                    assertThat(contexto).doesNotHaveBean(SolicitacaoTecnicaController.class);
                    assertThat(contexto).hasSingleBean(SolicitacaoController.class);

                    SolicitacaoMaterialUseCases useCases = contexto.getBean(SolicitacaoMaterialUseCases.class);
                    when(useCases.listar(any(), any())).thenReturn(
                            new PageResponse<>(List.of(), new Paged(20, 0, 0, 0)));
                    MockMvc http = MockMvcBuilders
                            .standaloneSetup(contexto.getBean(SolicitacaoController.class))
                            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                            .build();

                    assertThatCode(() -> {
                        http.perform(get("/v1/solicitacoes-material"))
                                .andExpect(status().isOk());
                        http.perform(post("/v1/solicitacoes-material/importacoes/confirmacao"))
                                .andExpect(status().isNotFound());
                    }).doesNotThrowAnyException();
                });

        assertThat(SolicitacaoController.class.getAnnotation(ConditionalOnProperty.class)).isNull();
    }

    @Test
    void habilitadoPublicaPontoDeEntradaTecnico() {
        runner.withPropertyValues("ces.estoque.solicitacoes-tecnicas.enabled=true")
                .run(contexto -> assertThat(contexto).hasSingleBean(SolicitacaoTecnicaController.class));
    }
}
