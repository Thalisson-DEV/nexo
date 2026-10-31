package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.KitImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportarKitsUseCaseTest {

    @Mock
    private ImportacaoObraRepository importacaoRepository;
    @Mock
    private KitConstrutivoRepository kitRepository;
    @Mock
    private ContratoApi contratoApi;
    @Mock
    private EstoqueApi estoqueApi;
    @Mock
    private ProcessarImportacaoKitsService processador;

    private ImportarKitsUseCase useCase;
    private final UUID contrato = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ImportarKitsUseCase(importacaoRepository, kitRepository, contratoApi, estoqueApi, processador,
                new LeitorPlanilhaImportacao());
        when(contratoApi.existeContrato(contrato)).thenReturn(true);
        lenient().when(kitRepository.findByIdContratoAndCodigoAndAtivoTrue(eq(contrato), anyString())).thenReturn(Optional.empty());
        lenient().when(contratoApi.buscarAcaoAtivaPorCodigo(anyString())).thenAnswer(inv -> {
            String c = inv.getArgument(0, String.class);
            return c.startsWith("ACAO") ? Optional.of(new AcaoResponse(UUID.randomUUID(), c, c, true, null, null)) : Optional.empty();
        });
        lenient().when(estoqueApi.buscarMaterialAtivoPorCodigo(anyString())).thenAnswer(inv -> {
            String c = inv.getArgument(0, String.class);
            return c.startsWith("MAT") ? Optional.of(new MaterialResponse(UUID.randomUUID(), c, c, "UN", null, null, null, null, false, true))
                    : Optional.empty();
        });
    }

    @Test
    void validacaoMarcaCadaLinhaSemGravarNada() {
        String csv = "CODIGO_KIT;DESCRICAO_KIT;TIPO_ITEM;CODIGO_ITEM;OPERACAO;QUANTIDADE\n"
                + "K1;Kit um;SERVICO;ACAO-1;INSTALAR;1\n"
                + "K1;;MATERIAL;MAT-X9;;2\n"
                + "K1;;MATERIAL;XYZ;;2\n"
                + "K2;;SERVICO;ACAO-2;Trocar;0\n";

        PreviaImportacaoObraResponse<KitImportacaoLinha> previa = useCase.validar(contrato,
                new MockMultipartFile("arquivo", "kits.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(previa.totalProcessados()).isEqualTo(4);
        assertThat(previa.totalSucesso()).isEqualTo(2);
        assertThat(previa.registros()).extracting(KitImportacaoLinha::status).containsExactly(
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO,
                ImportacaoStatus.COM_ERRO, ImportacaoStatus.COM_ERRO);
        assertThat(previa.registros().get(2).mensagemErro()).contains("Material XYZ não encontrado");
        assertThat(previa.registros().get(3).mensagemErro())
                .contains("Operação inválida").contains("quantidade maior que zero").contains("Kit novo sem descrição");
        verify(importacaoRepository, never()).save(any());
        verify(processador, never()).processar(any(), any());
    }

    @Test
    void confirmacaoRevalidaEProcessaSoAsLinhasValidas() {
        when(importacaoRepository.save(any(ImportacaoObra.class))).thenAnswer(inv -> {
            ImportacaoObra imp = inv.getArgument(0);
            ReflectionTestUtils.setField(imp, "id", UUID.randomUUID());
            return imp;
        });
        List<KitImportacaoLinha> linhas = List.of(
                new KitImportacaoLinha(2, "K1", "Kit um", null, "15 kV", "SERVICO", "ACAO-1", "INSTALAR", "1", "UN", null, null),
                new KitImportacaoLinha(3, "K1", null, null, null, "MATERIAL", "NAO-EXISTE", null, "1", "UN", null, null));

        PreviaImportacaoObraResponse<KitImportacaoLinha> resposta =
                useCase.confirmar(new ConfirmarImportacaoPlanilhaRequest<>(contrato, "kits.csv", linhas));

        assertThat(resposta.idImportacao()).isNotNull();
        assertThat(resposta.registros()).extracting(KitImportacaoLinha::status)
                .containsExactly(ImportacaoStatus.EM_PROCESSAMENTO, ImportacaoStatus.COM_ERRO);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LeitorPlanilhaImportacao.Linha>> enviadas = ArgumentCaptor.forClass(List.class);
        verify(processador).processar(eq(resposta.idImportacao()), enviadas.capture());
        assertThat(enviadas.getValue()).singleElement()
                .satisfies(l -> assertThat(l.valor("CODIGO_ITEM")).isEqualTo("ACAO-1"));
    }
}
