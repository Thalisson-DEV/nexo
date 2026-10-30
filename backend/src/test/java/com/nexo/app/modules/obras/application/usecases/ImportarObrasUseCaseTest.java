package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoObraRequest;
import com.nexo.app.modules.obras.application.dto.ImportacaoObraResponse;
import com.nexo.app.modules.obras.application.dto.ObraImportacaoDTO;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportarObrasUseCaseTest {

    @Mock
    private ObraRepository obraRepository;

    @Mock
    private StatusObraRepository statusObraRepository;

    @Mock
    private AgenteResponsavelRepository agenteRepository;

    @Mock
    private ProcessoNegocioApi processoNegocioApi;

    @Mock
    private BaseOperacionalApi baseOperacionalApi;

    @Mock
    private MunicipioApi municipioApi;

    @Mock
    private CentroFaturamentoApi centroFaturamentoApi;

    @Mock
    private SupervisorApi supervisorApi;

    @Mock
    private CoordenadorApi coordenadorApi;

    @Mock
    private EquipeApi equipeApi;

    @Mock
    private ImportarObrasAsyncService asyncService;

    private NormalizadorObraService normalizador;
    private ImportarObrasUseCase useCase;

    @BeforeEach
    void setUp() {
        normalizador = new NormalizadorObraService();
        useCase = new ImportarObrasUseCase(
                obraRepository,
                statusObraRepository,
                agenteRepository,
                processoNegocioApi,
                baseOperacionalApi,
                municipioApi,
                centroFaturamentoApi,
                supervisorApi,
                coordenadorApi,
                equipeApi,
                normalizador,
                asyncService
        );
    }

    @Test
    @DisplayName("Deve validar com sucesso arquivo CSV no formato legado")
    void deveValidarArquivoLegadoComSucesso() {
        String csvContent = """
                NumeroObra;DataInicio;DataFinal;DescricaoObra;Solicitado;AnexadoAsBuilt;Equipe;StatusObra;LongetudeObra;LatitudeObra;BaseOp;Municipio;CentroCusto;Supervisor;Coordenador;AgentesResponsaveis;AgentesResponsaveisNomes;Processo
                B-1228694;03/11/2025;30/11/2025;REDE PEDAGOGICA REMANSO (CONCESSIONARIA);true;false;EQ-01;Em execução;0;0;Remanso;Remanso;CC-01;JOAO SILVA;CARLOS OLIVEIRA;marlos.lopes@concessionaria.com.br;MARLOS PEREIRA;Obras
                B-1231009;01/11/2025;30/11/2025;CORRETIVO URBANO RMN;true;false;;Em execução;0;0;Remanso;Remanso;CC-01;JOAO SILVA;CARLOS OLIVEIRA;laiane@concessionaria.com.br;LAIANE;Corretiva
                B-1231258;01/11/2025;30/11/2025;MP NOV/25 UTD JACOBINA (41) - da empresa (RURAL);true;false;;Em execução;-00.00000;-00.00000;Jacobina;Jacobina;CC-01;JOAO SILVA;CARLOS OLIVEIRA;diorlan.reis@concessionaria.com.br;DIORLAN SOUZA;Preventiva
                1214623;03/11/2025;22/11/2025;KIT - SALINA GRANDE;true;false;EQ-02;Em execução;-419.682.287.424;-943.900.790.756;Remanso;Remanso;CC-01;JOAO SILVA;CARLOS OLIVEIRA;marlos.lopes@concessionaria.com.br;MARLOS PEREIRA;LPT
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "obras_legado.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(statusObraRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new StatusObra("EM EXECUÇÃO", 1, false)));
        when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(municipioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipeApi.buscarIdPorCodigo(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(agenteRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new com.nexo.app.modules.obras.domain.AgenteResponsavel("MARLOS PEREIRA", null, null, null)));
        when(obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenReturn(false);

        ImportacaoObraResponse response = useCase.executarValidacao(file);

        assertThat(response.totalProcessados()).isEqualTo(4);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.totalSucesso()).isEqualTo(4);

        ObraImportacaoDTO primeiro = response.registros().get(0);
        assertThat(primeiro.numero()).isEqualTo("B-1228694");
        assertThat(primeiro.titulo()).isEqualTo("REDE PEDAGOGICA REMANSO (CONCESSIONARIA)");
        assertThat(primeiro.tipoObra()).isEqualTo("OBRA");
        assertThat(primeiro.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
    }

    @Test
    @DisplayName("Deve importar toda a matriz de tipos operacionais sem confundir tipo com processo")
    void deveImportarMatrizTipoObra() {
        String csvContent = """
                Numero Obra;Titulo;Tipo Obra;Data Inicio;Data Previsao Fim;Processo;Centro Custo;Base Operacional;Municipio;Supervisor;Coordenador
                1000001;OBRA COMUM;OBRA;03/11/2025;30/11/2025;PROCESSO TECNICO;CC-01;BASE-01;REMANSO;JOAO;CARLOS
                1000002;MANUTENCAO CORRETIVA;CORRETIVA;03/11/2025;30/11/2025;PROCESSO TECNICO;CC-01;BASE-01;REMANSO;JOAO;CARLOS
                1000003;MANUTENCAO PREVENTIVA;PREVENTIVA;03/11/2025;30/11/2025;PROCESSO TECNICO;CC-01;BASE-01;REMANSO;JOAO;CARLOS
                1000004;PROGRAMA LPT;LPT;03/11/2025;30/11/2025;PROCESSO TECNICO;CC-01;BASE-01;REMANSO;JOAO;CARLOS
                """;

        when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(municipioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenReturn(false);

        ImportacaoObraResponse response = useCase.executarValidacao(new MockMultipartFile(
                "arquivo", "tipos.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.totalFalha()).isZero();
        assertThat(response.registros()).extracting(ObraImportacaoDTO::tipoObra)
                .containsExactly("OBRA", "CORRETIVA", "PREVENTIVA", "LPT");
        assertThat(response.registros()).extracting(ObraImportacaoDTO::nomeProcesso)
                .containsOnly("PROCESSO TECNICO");
    }

    @Test
    @DisplayName("Deve identificar falhas de validação quando houver duplicidade ou datas ausentes")
    void deveIdentificarErrosValidacao() {
        String csvContent = """
                NumeroObra;DataInicio;DataFinal;DescricaoObra
                B-1228694;03/11/2025;;OBRA SEM DATA FIM
                B-1228694;03/11/2025;30/11/2025;OBRA DUPLICADA
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "obras_erros.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        ImportacaoObraResponse response = useCase.executarValidacao(file);

        assertThat(response.totalProcessados()).isEqualTo(2);
        assertThat(response.totalFalha()).isEqualTo(2);
        assertThat(response.registros().get(0).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(0).mensagemErro()).contains("Data prevista de término");
        assertThat(response.registros().get(1).status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(1).mensagemErro()).contains("duplicado no arquivo");
    }

    @Test
    @DisplayName("Deve disparar persistência assíncrona ao confirmar importação")
    void deveDispararPersistenciaAoConfirmar() {
        when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(municipioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenReturn(false);

        ObraImportacaoDTO dto = new ObraImportacaoDTO(
                1, "B-1228694", "OBRA CONFIRMADA", null, null, null,
                "03/11/2025", "30/11/2025", null, null,
                "Obras", "Remanso", "Remanso", "CC-01",
                "JOAO SILVA", "CARLOS OLIVEIRA", null,
                null, null, null, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );

        ConfirmarImportacaoObraRequest request = new ConfirmarImportacaoObraRequest(List.of(dto));

        ImportacaoObraResponse response = useCase.confirmarImportacao(request);

        assertThat(response.totalProcessados()).isEqualTo(1);
        assertThat(response.totalSucesso()).isEqualTo(1);
        verify(asyncService).processarPersistencia(anyList());
    }

    @Test
    @DisplayName("Deve gerar modelo CSV de importação de obras com BOM UTF-8 e cabeçalhos corretos")
    void deveGerarModeloCsvComSucesso() {
        byte[] modeloBytes = useCase.obterModeloCsv();
        assertThat(modeloBytes).isNotEmpty();

        String modeloStr = new String(modeloBytes, StandardCharsets.UTF_8);
        assertThat(modeloStr).startsWith("\uFEFFNumero Obra;Titulo;Tipo Obra;Nota Obra;Descricao;Data Inicio;Data Previsao Fim;Valor Projeto;Processo;Centro Custo;Base Operacional;Municipio;Supervisor;Coordenador;Latitude;Longitude;Status Obra;Agente Responsavel;Codigos Equipes");
        assertThat(modeloStr).contains("# Exemplo:");
    }

    @Test
    @DisplayName("Deve validar com sucesso arquivo gerado a partir do modelo CSV de importação")
    void deveValidarArquivoCriadoAPartirDoModelo() {
        byte[] modeloBytes = useCase.obterModeloCsv();
        String modeloStr = new String(modeloBytes, StandardCharsets.UTF_8);
        // Descomenta a linha de exemplo para simular arquivo preenchido pelo usuário
        String csvContent = modeloStr.replace("# Exemplo: ", "");

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "obras_modelo_preenchido.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(statusObraRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new StatusObra("PLANEJADA", 1, false)));
        when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(municipioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipeApi.buscarIdPorCodigo(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(agenteRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new com.nexo.app.modules.obras.domain.AgenteResponsavel("MARLOS PEREIRA", null, null, null)));
        when(obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenReturn(false);

        ImportacaoObraResponse response = useCase.executarValidacao(file);

        assertThat(response.totalProcessados()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.totalSucesso()).isEqualTo(1);

        ObraImportacaoDTO registro = response.registros().get(0);
        assertThat(registro.numero()).isEqualTo("1228694");
        assertThat(registro.dataInicio()).isEqualTo("03/11/2025");
        assertThat(registro.dataPrevisaoFim()).isEqualTo("30/11/2025");
        assertThat(registro.titulo()).isEqualTo("REDE PEDAGOGICA REMANSO (CONCESSIONARIA)");
        assertThat(registro.tipoObra()).isEqualTo("OBRA");
        assertThat(registro.descricao()).isEqualTo("Obra de ampliacao de rede");
        assertThat(registro.notaObra()).isEqualTo("12345");
        assertThat(registro.valorProjeto()).isEqualTo("150000.00");
        assertThat(registro.latitude()).isEqualTo("-9.439007");
        assertThat(registro.longitude()).isEqualTo("-41.968228");
        assertThat(registro.nomeProcesso()).isEqualTo("Obras");
        assertThat(registro.nomeBaseOperacional()).isEqualTo("Remanso");
        assertThat(registro.nomeMunicipio()).isEqualTo("Remanso");
        assertThat(registro.nomeCentroCusto()).isEqualTo("CC-01");
        assertThat(registro.nomeSupervisor()).isEqualTo("JOAO SILVA");
        assertThat(registro.nomeCoordenador()).isEqualTo("CARLOS OLIVEIRA");
        assertThat(registro.nomeStatus()).isEqualTo("PLANEJADA");
        assertThat(registro.nomeAgenteResponsavel()).isEqualTo("MARLOS PEREIRA");
        assertThat(registro.codigosEquipes()).isEqualTo("EQ-01,EQ-02");
        assertThat(registro.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
    }

    @Test
    @DisplayName("Deve gerar aviso quando o agente responsável não for encontrado no cadastro")
    void deveGerarAvisoQuandoAgenteNaoEncontrado() {
        String csvContent = """
                Numero Obra;Titulo;Nota Obra;Descricao;Data Inicio;Data Previsao Fim;Valor Projeto;Processo;Centro Custo;Base Operacional;Municipio;Supervisor;Coordenador;Latitude;Longitude;Status Obra;Agente Responsavel;Codigos Equipes
                1228694;REDE PEDAGOGICA REMANSO (CONCESSIONARIA);12345;Obra de ampliacao de rede;03/11/2025;30/11/2025;150000.00;Obras;CC-01;Remanso;Remanso;JOAO SILVA;CARLOS OLIVEIRA;-9.439007;-41.968228;PLANEJADA;AGENTE INEXISTENTE;EQ-01
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "obras_sem_agente.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(statusObraRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new StatusObra("PLANEJADA", 1, false)));
        when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(municipioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipeApi.buscarIdPorCodigo(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(agenteRepository.findByNomeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenReturn(false);

        ImportacaoObraResponse response = useCase.executarValidacao(file);

        assertThat(response.totalProcessados()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(0);
        assertThat(response.totalSucesso()).isEqualTo(1);

        ObraImportacaoDTO registro = response.registros().get(0);
        assertThat(registro.status()).isEqualTo(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO);
        assertThat(registro.mensagemErro()).contains("Agente responsável 'AGENTE INEXISTENTE' não encontrado no cadastro; a obra será importada sem agente vinculado.");
    }

    @Test
    @DisplayName("Deve rejeitar importação com erro quando referências operacionais obrigatórias não forem informadas")
    void deveRejeitarLinhaSemCamposObrigatorios() {
        String csvContent = """
                Numero Obra;Titulo;Nota Obra;Descricao;Data Inicio;Data Previsao Fim;Valor Projeto;Processo;Centro Custo;Base Operacional;Municipio;Supervisor;Coordenador;Latitude;Longitude;Status Obra;Agente Responsavel;Codigos Equipes
                1228694;OBRA SEM OBRIGATORIOS;12345;Obra teste;03/11/2025;30/11/2025;150000.00;;;;;;;-9.439007;-41.968228;PLANEJADA;;EQ-01
                """;

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "obras_sem_obrigatorios.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(statusObraRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new StatusObra("PLANEJADA", 1, false)));
        when(equipeApi.buscarIdPorCodigo(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(anyString())).thenReturn(false);

        ImportacaoObraResponse response = useCase.executarValidacao(file);

        assertThat(response.totalProcessados()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.totalSucesso()).isEqualTo(0);

        ObraImportacaoDTO registro = response.registros().get(0);
        assertThat(registro.status()).isEqualTo(ImportacaoStatus.COM_ERRO);
        assertThat(registro.mensagemErro()).contains("Processo de negócio da obra é obrigatório");
        assertThat(registro.mensagemErro()).contains("Base operacional da obra é obrigatória");
        assertThat(registro.mensagemErro()).contains("Município da obra é obrigatório");
        assertThat(registro.mensagemErro()).contains("Centro de custo da obra é obrigatório");
        assertThat(registro.mensagemErro()).contains("Supervisor da obra é obrigatório");
        assertThat(registro.mensagemErro()).contains("Coordenador da obra é obrigatório");
    }
}
