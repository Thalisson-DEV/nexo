package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoMovimentacaoRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoMovimentacaoResponse;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoImportacaoDTO;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportarMovimentacaoEstoqueUseCaseTest {

    @Mock
    private EstoqueRepository estoqueRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private SaldoSerieRepository serieRepository;

    @Mock
    private SaldoRepository saldoRepository;

    @Mock
    private MovimentacaoEstoqueUseCases movimentacaoEstoqueUseCases;

    @InjectMocks
    private ImportarMovimentacaoEstoqueUseCase useCase;

    private Estoque almoxCentral;
    private Estoque baseNorte;
    private Material matSerial;
    private Material matComum;
    private UUID idAlmox;
    private UUID idBaseNorte;
    private UUID idMatSerial;
    private UUID idMatComum;
    private UUID idOperador;

    @BeforeEach
    void setUp() {
        almoxCentral = new Estoque("ALMOX CENTRAL", new TipoEstoque("FISICO"));
        idAlmox = UUID.randomUUID();
        ReflectionTestUtils.setField(almoxCentral, "id", idAlmox);

        baseNorte = new Estoque("BASE NORTE", new TipoEstoque("FISICO"));
        idBaseNorte = UUID.randomUUID();
        ReflectionTestUtils.setField(baseNorte, "id", idBaseNorte);

        idMatSerial = UUID.randomUUID();
        matSerial = new Material("MAT-RADIO", "RADIO COMUNICADOR", "UN", "MATR", null, null, true, UUID.randomUUID());
        ReflectionTestUtils.setField(matSerial, "id", idMatSerial);

        idMatComum = UUID.randomUUID();
        matComum = new Material("MAT-CABO", "CABO DE COBRE 16MM", "M", "MATR", null, null, false, UUID.randomUUID());
        ReflectionTestUtils.setField(matComum, "id", idMatComum);

        idOperador = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve validar CSV com entradas, saídas e transferências, aceitando múltiplos seriais na mesma linha")
    void deveValidarCsvComSucesso() {
        String csvContent = """
                Tipo Movimentacao;Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie;Condicao;Observacao
                ENTRADA;;ALMOX CENTRAL;MAT-RADIO;3;RAD-001, RAD-002, RAD-003;NOVO;Entrada novos radios
                SAIDA;ALMOX CENTRAL;;MAT-CABO;15.5;;NOVO;Baixa para aplicacao
                TRANSFERENCIA;ALMOX CENTRAL;BASE NORTE;MAT-RADIO;1;RAD-004;NOVO;Transferencia entre bases
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("BASE NORTE")).thenReturn(Optional.of(baseNorte));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(materialRepository.findByCodigoIgnoreCase("MAT-CABO")).thenReturn(Optional.of(matComum));

        // Para ENTRADA, seriais não devem constar no sistema
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-001")).thenReturn(Optional.empty());
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-002")).thenReturn(Optional.empty());
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-003")).thenReturn(Optional.empty());

        // Para TRANSFERÊNCIA, serial RAD-004 deve constar na origem com condição NOVO
        SaldoSerie saldoSerieOrigem = mock(SaldoSerie.class);
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                idAlmox, idMatSerial, "RAD-004", CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldoSerieOrigem));

        // Para SAÍDA não-serializada, deve haver saldo suficiente na origem
        Saldo saldoCabo = new Saldo(almoxCentral, matComum, CondicaoMaterial.NOVO);
        saldoCabo.adicionarQuantidade(new BigDecimal("100.000"));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(idAlmox, idMatComum, CondicaoMaterial.NOVO))
                .thenReturn(Optional.of(saldoCabo));

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(file);

        assertThat(response).isNotNull();
        assertThat(response.totalProcessados()).isEqualTo(3);
        assertThat(response.totalSucesso()).isEqualTo(3);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.processamento().estado().name()).isEqualTo("PRONTO_PARA_CONFIRMACAO");

        // Checa primeira linha: Entrada de 3 seriais
        MovimentacaoImportacaoDTO linha1 = response.registros().get(0);
        assertThat(linha1.tipo()).isEqualTo("ENTRADA");
        assertThat(linha1.idEstoqueDestino()).isEqualTo(idAlmox);
        assertThat(linha1.idMaterial()).isEqualTo(idMatSerial);
        assertThat(linha1.quantidade()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(linha1.numSerie()).isEqualTo("RAD-001, RAD-002, RAD-003");
        assertThat(linha1.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);

        // Checa segunda linha: Saída sem serial
        MovimentacaoImportacaoDTO linha2 = response.registros().get(1);
        assertThat(linha2.tipo()).isEqualTo("SAIDA");
        assertThat(linha2.idEstoqueOrigem()).isEqualTo(idAlmox);
        assertThat(linha2.idMaterial()).isEqualTo(idMatComum);
        assertThat(linha2.quantidade()).isEqualByComparingTo(new BigDecimal("15.5"));
        assertThat(linha2.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);

        // Checa terceira linha: Transferência com 1 serial
        MovimentacaoImportacaoDTO linha3 = response.registros().get(2);
        assertThat(linha3.tipo()).isEqualTo("TRANSFERENCIA");
        assertThat(linha3.idEstoqueOrigem()).isEqualTo(idAlmox);
        assertThat(linha3.idEstoqueDestino()).isEqualTo(idBaseNorte);
        assertThat(linha3.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
    }

    @Test
    @DisplayName("Deve extrair números de série com separadores flexíveis (vírgula, ponto e vírgula, barra, espaço, pipe, quebra de linha)")
    void deveAceitarVariosFormatosDeSeparadoresDeSerie() {
        List<String> r1 = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("SN-01, SN-02, SN-03");
        assertThat(r1).containsExactly("SN-01", "SN-02", "SN-03");

        List<String> r2 = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("SN-01;SN-02;SN-03");
        assertThat(r2).containsExactly("SN-01", "SN-02", "SN-03");

        List<String> r3 = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("SN-01|SN-02|SN-03");
        assertThat(r3).containsExactly("SN-01", "SN-02", "SN-03");

        List<String> r4 = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("SN-01/SN-02/SN-03");
        assertThat(r4).containsExactly("SN-01", "SN-02", "SN-03");

        List<String> r5 = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("SN-01\nSN-02\r\nSN-03");
        assertThat(r5).containsExactly("SN-01", "SN-02", "SN-03");

        List<String> r6 = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("SN-01 SN-02 SN-03");
        assertThat(r6).containsExactly("SN-01", "SN-02", "SN-03");
    }

    @Test
    @DisplayName("Deve inferir quantidade automaticamente quando omitida para material serializado")
    void deveInferirQuantidadeQuandoOmitidaParaMaterialComSerie() {
        String csv = """
                Tipo Movimentacao;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie
                ENTRADA;ALMOX CENTRAL;MAT-RADIO;;SN-100, SN-200, SN-300
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(serieRepository.findByNumSerieAndAtivoTrue(anyString())).thenReturn(Optional.empty());

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.registros().get(0).quantidade()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
    }

    @Test
    @DisplayName("Deve apontar erro quando a quantidade informada divergir da contagem de números de série")
    void deveApontarErroQuandoQuantidadeDivergeDosSeriais() {
        String csv = """
                Tipo Movimentacao;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie
                ENTRADA;ALMOX CENTRAL;MAT-RADIO;5;SN-100, SN-200
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(0).campoErro()).isEqualTo("quantidade");
        assertThat(response.registros().get(0).mensagemErro()).contains("diverge da quantidade de seriais");
    }

    @Test
    @DisplayName("Deve apontar erro quando número de série for duplicado na mesma linha")
    void deveApontarErroQuandoSerialDuplicadoNaMesmaLinha() {
        String csv = """
                Tipo Movimentacao;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie
                ENTRADA;ALMOX CENTRAL;MAT-RADIO;2;SN-100, SN-100
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(0).mensagemErro()).contains("duplicado na mesma linha");
    }

    @Test
    @DisplayName("Deve apontar erro quando número de série for repetido em mais de uma linha da planilha")
    void deveApontarErroQuandoSerialRepetidoEmOutraLinha() {
        String csv = """
                Tipo Movimentacao;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie
                ENTRADA;ALMOX CENTRAL;MAT-RADIO;1;SN-100
                ENTRADA;ALMOX CENTRAL;MAT-RADIO;1;SN-100
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(serieRepository.findByNumSerieAndAtivoTrue("SN-100")).thenReturn(Optional.empty());

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(1).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(1).mensagemErro()).contains("repetido na linha 2");
    }

    @Test
    @DisplayName("Deve apontar erro quando serial já consta no sistema para operação de ENTRADA")
    void deveApontarErroQuandoSerialJaConstaNoSistemaEmEntrada() {
        String csv = """
                Tipo Movimentacao;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie
                ENTRADA;ALMOX CENTRAL;MAT-RADIO;1;SN-EXISTENTE
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(serieRepository.findByNumSerieAndAtivoTrue("SN-EXISTENTE")).thenReturn(Optional.of(mock(SaldoSerie.class)));

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).mensagemErro()).contains("já consta ativo no sistema");
    }

    @Test
    @DisplayName("Deve apontar erro quando serial não for localizado na origem para operação de SAIDA")
    void deveApontarErroQuandoSerialNaoLocalizadoNaOrigemEmSaida() {
        String csv = """
                Tipo Movimentacao;Estoque Origem;Codigo Material;Quantidade;Numeros de Serie
                SAIDA;ALMOX CENTRAL;MAT-RADIO;1;SN-NAO-EXISTE
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                idAlmox, idMatSerial, "SN-NAO-EXISTE", CondicaoMaterial.NOVO)).thenReturn(Optional.empty());

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).mensagemErro()).contains("não localizado no estoque de origem");
    }

    @Test
    @DisplayName("Deve apontar erro quando transferência for solicitada com condição diferente de NOVO")
    void deveApontarErroQuandoTransferenciaComCondicaoInvalida() {
        String csv = """
                Tipo Movimentacao;Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Condicao
                TRANSFERENCIA;ALMOX CENTRAL;BASE NORTE;MAT-CABO;10;SUCATA
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("BASE NORTE")).thenReturn(Optional.of(baseNorte));
        when(materialRepository.findByCodigoIgnoreCase("MAT-CABO")).thenReturn(Optional.of(matComum));

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).campoErro()).isEqualTo("condicao");
        assertThat(response.registros().get(0).mensagemErro()).contains("apenas para materiais na condição NOVO");
    }

    @Test
    @DisplayName("Deve apontar erro quando material não detalha série mas recebe números de série")
    void deveApontarErroQuandoMaterialComumRecebeSeriais() {
        String csv = """
                Tipo Movimentacao;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie
                ENTRADA;ALMOX CENTRAL;MAT-CABO;1;SN-123
                """;

        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-CABO")).thenReturn(Optional.of(matComum));

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "movimentacoes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).mensagemErro()).contains("não permite controle por número de série");
    }

    @Test
    @DisplayName("Deve confirmar importação agrupando por operação e expandindo seriais da mesma linha")
    void deveConfirmarImportacaoComSucesso() {
        MovimentacaoImportacaoDTO linhaEntrada = new MovimentacaoImportacaoDTO(
                2, "ENTRADA", null, null, "ALMOX CENTRAL", idAlmox,
                "MAT-RADIO", idMatSerial, "RADIO", true,
                new BigDecimal("2"), "RAD-001, RAD-002", CondicaoMaterial.NOVO, "Obs",
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null, null
        );

        MovimentacaoImportacaoDTO linhaSaida = new MovimentacaoImportacaoDTO(
                3, "SAIDA", "ALMOX CENTRAL", idAlmox, null, null,
                "MAT-CABO", idMatComum, "CABO", false,
                new BigDecimal("10"), null, CondicaoMaterial.NOVO, "Obs",
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null, null
        );

        when(estoqueRepository.findById(idAlmox)).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findById(idMatSerial)).thenReturn(Optional.of(matSerial));
        when(materialRepository.findById(idMatComum)).thenReturn(Optional.of(matComum));
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-001")).thenReturn(Optional.empty());
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-002")).thenReturn(Optional.empty());

        Saldo saldoCabo = new Saldo(almoxCentral, matComum, CondicaoMaterial.NOVO);
        saldoCabo.adicionarQuantidade(new BigDecimal("100.000"));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(idAlmox, idMatComum, CondicaoMaterial.NOVO))
                .thenReturn(Optional.of(saldoCabo));

        ConfirmarImportacaoMovimentacaoRequest request = new ConfirmarImportacaoMovimentacaoRequest(
                List.of(linhaEntrada, linhaSaida)
        );

        ImportacaoMovimentacaoResponse response = useCase.confirmarImportacao(request, idOperador);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(2);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.processamento().estado().name()).isEqualTo("CONCLUIDO");

        // Verifica chamada de entradaLote com os 2 seriais expandidos em itens individuais
        ArgumentCaptor<MovimentacaoBulkRequest> captorEntrada = ArgumentCaptor.forClass(MovimentacaoBulkRequest.class);
        verify(movimentacaoEstoqueUseCases).entradaLote(captorEntrada.capture(), eq(idOperador));
        MovimentacaoBulkRequest reqEntrada = captorEntrada.getValue();
        assertThat(reqEntrada.idEstoqueDestino()).isEqualTo(idAlmox);
        assertThat(reqEntrada.itens()).hasSize(2);
        assertThat(reqEntrada.itens().get(0).numSerie()).isEqualTo("RAD-001");
        assertThat(reqEntrada.itens().get(0).quantidade()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(reqEntrada.itens().get(1).numSerie()).isEqualTo("RAD-002");
        assertThat(reqEntrada.itens().get(1).quantidade()).isEqualByComparingTo(BigDecimal.ONE);

        // Verifica chamada de baixaLote com item não serializado
        ArgumentCaptor<MovimentacaoBulkRequest> captorBaixa = ArgumentCaptor.forClass(MovimentacaoBulkRequest.class);
        verify(movimentacaoEstoqueUseCases).baixaLote(captorBaixa.capture(), eq(idOperador));
        MovimentacaoBulkRequest reqBaixa = captorBaixa.getValue();
        assertThat(reqBaixa.idEstoqueOrigem()).isEqualTo(idAlmox);
        assertThat(reqBaixa.itens()).hasSize(1);
        assertThat(reqBaixa.itens().get(0).quantidade()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(reqBaixa.itens().get(0).numSerie()).isNull();
    }

    @Test
    @DisplayName("Deve gerar modelo CSV com cabeçalhos padrão e exemplos comentados com #")
    void deveGerarModeloCsvComExemplos() {
        byte[] csv = useCase.obterModeloCsv();
        assertThat(csv).isNotEmpty();
        String conteudo = new String(csv, StandardCharsets.UTF_8);
        assertThat(conteudo).startsWith("\uFEFFTipo Movimentacao;Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie;Condicao;Observacao\r\n");
        assertThat(conteudo).contains("# Exemplo: ENTRADA;;ALMOX CENTRAL;MAT-001;2;SN-1001, SN-1002;");
        assertThat(conteudo).contains("# Exemplo: SAIDA;ALMOX CENTRAL;;MAT-002;5;;");
        assertThat(conteudo).contains("# Exemplo: TRANSFERENCIA;ALMOX CENTRAL;BASE SUL;MAT-001;1;SN-1003;");
    }

    @Test
    @DisplayName("Deve rejeitar arquivo que não seja CSV")
    void deveRejeitarArquivoNaoCsv() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "planilha.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> useCase.executarValidacao(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato não suportado");
    }

    @Test
    @DisplayName("Deve validar CSV com encoding Windows-1252 e caracteres acentuados gerados pelo Excel")
    void deveValidarCsvWindows1252ComAcentos() {
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-999")).thenReturn(Optional.empty());

        // String com caracteres acentuados típicos do Excel em português (Windows-1252)
        String csvContent = "Tipo Movimentação;Estoque Origem;Estoque Destino;Código Material;Quantidade;Números de Série;Condição;Observação\r\n" +
                "ENTRADA;;ALMOX CENTRAL;MAT-RADIO;1;RAD-999;NOVO;Ajuste de operação extraordinária\r\n";

        byte[] bytes1252 = csvContent.getBytes(java.nio.charset.Charset.forName("Windows-1252"));
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "excel_movimentacoes.csv", "text/csv", bytes1252
        );

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(file);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.registros().get(0).tipo()).isEqualTo("ENTRADA");
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
    }

    @Test
    @DisplayName("Deve extrair múltiplos números de série separados por tab, barra, quebra de linha e remover aspas")
    void deveExtrairNumerosSerieComDelimitadoresDiversos() {
        List<String> seriais = ImportarMovimentacaoEstoqueUseCase.extrairNumerosSerie("\"SN-01\"\t\"SN-02\"/SN-03\nSN-04\\SN-05;SN-06,SN-07");
        assertThat(seriais).containsExactly("SN-01", "SN-02", "SN-03", "SN-04", "SN-05", "SN-06", "SN-07");
    }

    @Test
    @DisplayName("Deve validar saldo insuficiente para materiais não-serializados em saída e transferência")
    void deveValidarSaldoInsuficienteMaterialNaoSerializado() {
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-CABO")).thenReturn(Optional.of(matComum));

        // Mock saldo: almoxCentral tem apenas 5 metros de cabo
        Saldo saldo = new Saldo(almoxCentral, matComum, CondicaoMaterial.NOVO);
        saldo.adicionarQuantidade(new BigDecimal("5.000"));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(idAlmox, idMatComum, CondicaoMaterial.NOVO))
                .thenReturn(Optional.of(saldo));

        // Usuário tenta dar baixa de 20 metros (mais do que os 5 disponíveis)
        String csvContent = """
                Tipo Movimentacao;Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie;Condicao;Observacao
                SAIDA;ALMOX CENTRAL;;MAT-CABO;20.000;;NOVO;Baixa excessiva
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "baixa.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(file);

        assertThat(response).isNotNull();
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(0).campoErro()).isEqualTo("quantidade");
        assertThat(response.registros().get(0).mensagemErro()).contains("Saldo insuficiente no estoque de origem");
        assertThat(response.registros().get(0).mensagemErro()).contains("Saldo disponível: 5.000, solicitado: 20.000");
    }

    @Test
    @DisplayName("Deve aceitar saída de material não-serializado quando saldo é suficiente")
    void deveAceitarSaidaComSaldoSuficiente() {
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-CABO")).thenReturn(Optional.of(matComum));

        Saldo saldo = new Saldo(almoxCentral, matComum, CondicaoMaterial.NOVO);
        saldo.adicionarQuantidade(new BigDecimal("50.000"));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(idAlmox, idMatComum, CondicaoMaterial.NOVO))
                .thenReturn(Optional.of(saldo));

        String csvContent = """
                Tipo Movimentacao;Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie;Condicao;Observacao
                SAIDA;ALMOX CENTRAL;;MAT-CABO;20.000;;NOVO;Baixa com saldo suficiente
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "baixa_ok.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(file);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
    }

    @Test
    @DisplayName("Deve ignorar comentários com # e linhas vazias no início ao detectar separador")
    void deveIgnorarComentariosNoInicioAoDetectarSeparador() {
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX CENTRAL")).thenReturn(Optional.of(almoxCentral));
        when(materialRepository.findByCodigoIgnoreCase("MAT-RADIO")).thenReturn(Optional.of(matSerial));
        when(serieRepository.findByNumSerieAndAtivoTrue("RAD-001")).thenReturn(Optional.empty());

        // CSV começa com linhas em branco e comentários contendo vírgulas, mas usa ponto e vírgula como delimitador
        String csvContent = """
                # Comentário de instrução com várias, vírgulas, para testar a detecção
                # Outro comentário: exemplo 1, 2, 3
                
                Tipo Movimentacao;Estoque Origem;Estoque Destino;Codigo Material;Quantidade;Numeros de Serie;Condicao;Observacao
                ENTRADA;;ALMOX CENTRAL;MAT-RADIO;1;RAD-001;NOVO;Entrada válida
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "com_comentarios.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        ImportacaoMovimentacaoResponse response = useCase.executarValidacao(file);

        assertThat(response).isNotNull();
        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.registros().get(0).tipo()).isEqualTo("ENTRADA");
    }
}

