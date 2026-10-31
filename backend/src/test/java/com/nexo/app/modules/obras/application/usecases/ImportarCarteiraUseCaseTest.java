package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.application.dto.CarteiraImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.OrcamentoImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportarCarteiraUseCaseTest {

    @Mock private ImportacaoObraRepository importacaoRepository;
    @Mock private ObraRepository obraRepository;
    @Mock private StatusObraRepository statusRepository;
    @Mock private ContratoApi contratoApi;
    @Mock private ProcessoNegocioApi processoNegocioApi;
    @Mock private BaseOperacionalApi baseOperacionalApi;
    @Mock private MunicipioApi municipioApi;
    @Mock private CentroFaturamentoApi centroFaturamentoApi;
    @Mock private SupervisorApi supervisorApi;
    @Mock private CoordenadorApi coordenadorApi;
    @Mock private ProcessarImportacaoCarteiraService processadorCarteira;
    @Mock private ProcessarImportacaoOrcamentoService processadorOrcamento;
    @Mock private AuditorAware<String> auditor;

    private ImportarCarteiraUseCase useCase;
    private final UUID contrato = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ImportarCarteiraUseCase(importacaoRepository, obraRepository, statusRepository, contratoApi,
                processoNegocioApi, baseOperacionalApi, municipioApi, centroFaturamentoApi, supervisorApi, coordenadorApi,
                new NormalizadorObraService(), new LeitorPlanilhaImportacao(), processadorCarteira, processadorOrcamento, auditor);
        lenient().when(contratoApi.existeContrato(contrato)).thenReturn(true);
        lenient().when(obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenAnswer(inv ->
                "1000".equals(inv.getArgument(0)) ? Optional.of(ProcessarImportacaoCarteiraServiceTest.novaObra("1000")) : Optional.empty());
        UUID qualquer = UUID.randomUUID();
        lenient().when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(municipioApi.buscarIdPorNome(anyString())).thenReturn(Optional.empty());
        lenient().when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(qualquer));
    }

    @Test
    void carteiraDiferenciaObraExistenteDeNovaEApontaOsErros() {
        String csv = "NUMERO;TITULO;TENSAO;PRIORIDADE;PROCESSO_NEGOCIO;BASE;MUNICIPIO;CENTRO_CUSTO;SUPERVISOR;COORDENADOR;DATA_INICIO;PRAZO\n"
                + "1000;;15 kV;Alta;;;;;;;;\n"
                + "2000;Nova;BT;;Expansão;Base;Inexistente;CC;Sup;Coord;01/11/2026;30/06/2027\n"
                + "1000;;500 kV;Talvez;;;;;;;;\n";

        PreviaImportacaoObraResponse<CarteiraImportacaoLinha> previa = useCase.validarCarteira(contrato,
                new MockMultipartFile("arquivo", "carteira.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        CarteiraImportacaoLinha existente = previa.registros().get(0);
        assertThat(existente.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
        assertThat(existente.obraNova()).isFalse();
        CarteiraImportacaoLinha nova = previa.registros().get(1);
        assertThat(nova.obraNova()).isTrue();
        assertThat(nova.mensagemErro()).contains("Município não encontrado");
        assertThat(previa.registros().get(2).mensagemErro())
                .contains("Obra repetida").contains("Classe de tensão inválida").contains("Prioridade inválida");
        verify(processadorCarteira, never()).processar(any(), any(), any());
    }

    @Test
    void orcamentoAvisaCodigoSemAcaoSemBloquearALinha() {
        lenient().when(contratoApi.buscarAcaoAtivaPorCodigo(anyString())).thenAnswer(inv ->
                "ACAO-1".equals(inv.getArgument(0)) ? Optional.of(new AcaoResponse(UUID.randomUUID(), "ACAO-1", "x", true, null, null)) : Optional.empty());
        String csv = "NUMERO;CODIGO_SERVICO;QUANTIDADE;VALOR_UNITARIO;VALOR_TOTAL\n"
                + "1000;ACAO-1;2;10;\n"
                + "1000;OUTRO;1;;50\n"
                + "9999;ACAO-1;1;;\n";

        PreviaImportacaoObraResponse<OrcamentoImportacaoLinha> previa = useCase.validarOrcamento(
                new MockMultipartFile("arquivo", "orc.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(previa.registros()).extracting(OrcamentoImportacaoLinha::status).containsExactly(
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, ImportacaoStatus.COM_ERRO);
        assertThat(previa.registros().get(1).mensagemErro()).startsWith("Aviso:");
        assertThat(previa.registros().get(2).mensagemErro()).contains("Obra 9999 não encontrada").contains("valor unitário ou o valor total");
        assertThat(previa.totalFalha()).isEqualTo(1);
    }
}
