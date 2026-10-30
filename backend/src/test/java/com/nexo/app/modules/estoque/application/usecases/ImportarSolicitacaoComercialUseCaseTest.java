package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoComercialRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoComercialResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoComercialImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportarSolicitacaoComercialUseCaseTest {

    @Mock
    private EstoqueRepository estoqueRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private SolicitacaoMaterialUseCases solicitacoes;

    @InjectMocks
    private ImportarSolicitacaoComercialUseCase useCase;

    private Estoque estoqueOrigem;
    private Estoque estoqueDestino;
    private Material material1;
    private Material material2;
    private UUID idUsuarioRequisitante;
    private UUID idOrigem;
    private UUID idDestino;
    private UUID idMat1;
    private UUID idMat2;
    private UUID idEquipeDestino;

    @BeforeEach
    void setUp() {
        estoqueOrigem = new Estoque("ALMOX CENTRAL", new TipoEstoque("FISICO"));
        idOrigem = UUID.randomUUID();
        ReflectionTestUtils.setField(estoqueOrigem, "id", idOrigem);

        estoqueDestino = new Estoque("EQ-01", new TipoEstoque("CAMPO"));
        idDestino = UUID.randomUUID();
        ReflectionTestUtils.setField(estoqueDestino, "id", idDestino);
        idEquipeDestino = UUID.randomUUID();
        estoqueDestino.vincularEquipe(idEquipeDestino);

        idMat1 = UUID.randomUUID();
        material1 = new Material("MAT-001", "CABO COBRE 16MM", "M", "MATR", null, null, false, UUID.randomUUID());
        ReflectionTestUtils.setField(material1, "id", idMat1);

        idMat2 = UUID.randomUUID();
        material2 = new Material("MAT-002", "CONECTOR COMPRESSAO", "UN", "MATR", null, null, false, UUID.randomUUID());
        ReflectionTestUtils.setField(material2, "id", idMat2);

        idUsuarioRequisitante = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve validar CSV de solicitações comerciais com sucesso")
    void deveValidarCsvComSucesso() {
        String csvContent = """
                Estoque Origem;Estoque Destino;Requisitante;Codigo Material;Quantidade;Data Prevista Saque;Observacao
                ALMOX CENTRAL;EQ-01;JOAO SILVA;MAT-001;10.500;31/12/2099;Pedido rotina
                ALMOX CENTRAL;EQ-01;JOAO SILVA;MAT-002;5.000;31/12/2099;Pedido rotina
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "solicitacoes.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(estoqueOrigem));
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("EQ-01")).thenReturn(Optional.of(estoqueDestino));
        when(materialRepository.findByCodigoIgnoreCase("MAT-001")).thenReturn(Optional.of(material1));
        when(materialRepository.findByCodigoIgnoreCase("MAT-002")).thenReturn(Optional.of(material2));

        ImportacaoSolicitacaoComercialResponse response = useCase.executarValidacao(file);

        assertThat(response).isNotNull();
        assertThat(response.totalProcessados()).isEqualTo(2);
        assertThat(response.totalSucesso()).isEqualTo(2);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.processamento().estado().name()).isEqualTo("PRONTO_PARA_CONFIRMACAO");
        assertThat(response.registros()).hasSize(2);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
        assertThat(response.registros().get(0).idRequisitante()).isNull();
        assertThat(response.registros().get(0).nomeRequisitante()).isNull();
    }

    @Test
    void validaModeloSemColunaRequisitante() {
        String csv = "Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Data Prevista Saque;Observacao\n"
                + "ALMOX CENTRAL;EQ-01;MAT-001;2;31/12/2099;Teste\n";
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL"))
                .thenReturn(Optional.of(estoqueOrigem));
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("EQ-01"))
                .thenReturn(Optional.of(estoqueDestino));
        when(materialRepository.findByCodigoIgnoreCase("MAT-001"))
                .thenReturn(Optional.of(material1));

        var response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "solicitacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.registros().getFirst().idRequisitante()).isNull();
    }

    @Test
    void rejeitaDestinoSemEquipeNaPrevia() {
        Estoque destinoSemEquipe = new Estoque("SEM EQUIPE", new TipoEstoque("CAMPO"));
        ReflectionTestUtils.setField(destinoSemEquipe, "id", UUID.randomUUID());
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL"))
                .thenReturn(Optional.of(estoqueOrigem));
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("SEM EQUIPE"))
                .thenReturn(Optional.of(destinoSemEquipe));
        when(materialRepository.findByCodigoIgnoreCase("MAT-001")).thenReturn(Optional.of(material1));

        String csv = "Estoque Origem;Estoque Destino;Requisitante;Codigo Material;Quantidade;Data Prevista Saque;Observacao\n"
                + "ALMOX CENTRAL;SEM EQUIPE;JOAO SILVA;MAT-001;1;31/12/2099;Teste\n";
        var response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "solicitacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().getFirst().mensagemErro()).contains("vinculado a uma equipe");
        verifyNoInteractions(solicitacoes);
    }

    @Test
    @DisplayName("Deve apontar erros de estoque e material inexistentes")
    void deveApontarErrosValidacao() {
        String csvContent = """
                Estoque Origem;Estoque Destino;Requisitante;Codigo Material;Quantidade;Data Prevista Saque;Observacao
                ESTOQUE_INEXISTENTE;EQ-01;JOAO SILVA;MAT_INEXISTENTE;0;31/12/2099;Sem quantidade
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "solicitacoes.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ESTOQUE_INEXISTENTE")).thenReturn(Optional.empty());
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("EQ-01")).thenReturn(Optional.of(estoqueDestino));
        when(materialRepository.findByCodigoIgnoreCase("MAT_INEXISTENTE")).thenReturn(Optional.empty());

        ImportacaoSolicitacaoComercialResponse response = useCase.executarValidacao(file);

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.totalSucesso()).isEqualTo(0);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(0).mensagemErro()).contains("Estoque de origem");
        assertThat(response.registros().get(0).mensagemErro()).contains("Material");
        assertThat(response.registros().get(0).mensagemErro()).contains("Quantidade deve ser maior que zero");
    }

    @Test
    @DisplayName("Deve confirmar importação agrupando itens por estoque destino")
    void deveConfirmarImportacaoComSucesso() {
        UUID idOrigem = this.idOrigem;
        UUID idDestino = this.idDestino;
        UUID idMat1 = UUID.randomUUID();
        UUID idMat2 = UUID.randomUUID();

        SolicitacaoComercialImportacaoDTO item1 = new SolicitacaoComercialImportacaoDTO(
                2, "ALMOX CENTRAL", idOrigem, "EQ-01", idDestino,
                "JOAO SILVA", idUsuarioRequisitante, "MAT-001", idMat1, "CABO",
                new BigDecimal("10.000"), LocalDate.of(2099, 12, 31), "Obs", ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );

        SolicitacaoComercialImportacaoDTO item2 = new SolicitacaoComercialImportacaoDTO(
                3, "ALMOX CENTRAL", idOrigem, "EQ-01", idDestino,
                "OUTRA PESSOA", UUID.randomUUID(), "MAT-002", idMat2, "CONECTOR",
                new BigDecimal("5.000"), LocalDate.of(2099, 12, 31), "Obs", ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );

        when(estoqueRepository.findById(idOrigem)).thenReturn(Optional.of(estoqueOrigem));
        when(estoqueRepository.findById(idDestino)).thenReturn(Optional.of(estoqueDestino));
        when(materialRepository.findById(idMat1)).thenReturn(Optional.of(material1));
        when(materialRepository.findById(idMat2)).thenReturn(Optional.of(material2));

        ConfirmarImportacaoSolicitacaoComercialRequest request = new ConfirmarImportacaoSolicitacaoComercialRequest(List.of(item1, item2));
        UUID idUsuarioLogado = UUID.randomUUID();

        ImportacaoSolicitacaoComercialResponse response = useCase.confirmarImportacao(request, idUsuarioLogado);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(2);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.processamento().estado().name()).isEqualTo("CONCLUIDO");

        ArgumentCaptor<SolicitacaoRequest> requestCaptor = ArgumentCaptor.forClass(SolicitacaoRequest.class);
        verify(solicitacoes, times(1)).criarDireta(requestCaptor.capture(), eq(idUsuarioLogado), eq(idUsuarioLogado));
        assertThat(requestCaptor.getValue().idEquipe()).isEqualTo(idEquipeDestino);
        assertThat(requestCaptor.getValue().idEstoqueDestino()).isEqualTo(estoqueDestino.getId());
        assertThat(requestCaptor.getValue().itens()).hasSize(2);
    }

    @Test
    @DisplayName("Deve separar solicitações independentes para estoques de destino diferentes")
    void deveSepararPorEstoqueDestino() {
        UUID idOrigem = this.idOrigem;
        UUID idDestino1 = this.idDestino;
        UUID idDestino2 = UUID.randomUUID();
        UUID idMat1 = UUID.randomUUID();
        UUID idMat2 = UUID.randomUUID();

        Estoque destino2 = new Estoque("EQ-02", new TipoEstoque("CAMPO"));
        ReflectionTestUtils.setField(destino2, "id", idDestino2);
        destino2.vincularEquipe(UUID.randomUUID());

        SolicitacaoComercialImportacaoDTO item1 = new SolicitacaoComercialImportacaoDTO(
                2, "ALMOX CENTRAL", idOrigem, "EQ-01", idDestino1,
                "JOAO SILVA", idUsuarioRequisitante, "MAT-001", idMat1, "CABO",
                new BigDecimal("10.000"), LocalDate.of(2099, 12, 31), "Obs", ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );

        SolicitacaoComercialImportacaoDTO item2 = new SolicitacaoComercialImportacaoDTO(
                3, "ALMOX CENTRAL", idOrigem, "EQ-02", idDestino2,
                "JOAO SILVA", idUsuarioRequisitante, "MAT-002", idMat2, "CONECTOR",
                new BigDecimal("5.000"), LocalDate.of(2099, 12, 31), "Obs", ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );

        when(estoqueRepository.findById(idOrigem)).thenReturn(Optional.of(estoqueOrigem));
        when(estoqueRepository.findById(idDestino1)).thenReturn(Optional.of(estoqueDestino));
        when(estoqueRepository.findById(idDestino2)).thenReturn(Optional.of(destino2));
        when(materialRepository.findById(idMat1)).thenReturn(Optional.of(material1));
        when(materialRepository.findById(idMat2)).thenReturn(Optional.of(material2));

        ConfirmarImportacaoSolicitacaoComercialRequest request = new ConfirmarImportacaoSolicitacaoComercialRequest(List.of(item1, item2));
        UUID idUsuarioLogado = UUID.randomUUID();

        ImportacaoSolicitacaoComercialResponse response = useCase.confirmarImportacao(request, idUsuarioLogado);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(2);

        // Deve ter chamado criarDireta 2 vezes (uma para cada estoque destino independente)
        verify(solicitacoes, times(2)).criarDireta(any(), eq(idUsuarioLogado), eq(idUsuarioLogado));
    }

    @Test
    @DisplayName("Deve gerar modelo CSV padrão com UTF-8 BOM e cabeçalhos corretos")
    void deveGerarModeloCsvPadrao() {
        byte[] csv = useCase.obterModeloCsv();
        assertThat(csv).isNotEmpty();
        String csvStr = new String(csv, StandardCharsets.UTF_8);
        assertThat(csvStr).startsWith("\uFEFFEstoque Origem;Estoque Destino;Codigo Material;Quantidade;Data Prevista Saque;Observacao\r\n");
        assertThat(csvStr).doesNotContain("Requisitante").doesNotContain("#");
        assertThat(csvStr).contains("ALMOX CENTRAL;EQ-01;MAT-001;10.500;");
    }

    @Test
    @DisplayName("Deve rejeitar arquivo com formato não CSV")
    void deveRejeitarArquivoNaoCsv() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "solicitacoes.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3}
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.executarValidacao(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato não suportado");
    }

    @Test
    @DisplayName("Deve tratar falha na confirmação síncrona apontando campo com erro no registro")
    void deveTratarFalhaNaConfirmacaoDiretaEApontarErroPorGrupo() {
        UUID idMat1 = UUID.randomUUID();
        SolicitacaoComercialImportacaoDTO item1 = new SolicitacaoComercialImportacaoDTO(
                2, "ALMOX CENTRAL", idOrigem, "EQ-01", idDestino,
                "JOAO SILVA", idUsuarioRequisitante, "MAT-001", idMat1, "CABO",
                new BigDecimal("10.000"), LocalDate.of(2099, 12, 31), "Obs", ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );

        when(estoqueRepository.findById(idOrigem)).thenReturn(Optional.of(estoqueOrigem));
        when(estoqueRepository.findById(idDestino)).thenReturn(Optional.of(estoqueDestino));
        when(materialRepository.findById(idMat1)).thenReturn(Optional.of(material1));
        doThrow(new com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException(Map.of("idEquipe", "Equipe inativa.")))
                .when(solicitacoes).criarDireta(any(), any(), any());

        ConfirmarImportacaoSolicitacaoComercialRequest request = new ConfirmarImportacaoSolicitacaoComercialRequest(List.of(item1));
        UUID idUsuarioLogado = UUID.randomUUID();

        ImportacaoSolicitacaoComercialResponse response = useCase.confirmarImportacao(request, idUsuarioLogado);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(0);
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(0).mensagemErro()).contains("Equipe: Equipe inativa.");
    }
}
