package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeApi.EquipePublica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportarSolicitacoesMaterialUseCaseTest {

    private SolicitacaoMaterialRepository repository;
    private UsuarioApi usuarioApi;
    private ObraApi obraApi;
    private EquipeApi equipeApi;
    private ColaboradorApi colaboradorApi;

    private ExportarSolicitacoesMaterialUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(SolicitacaoMaterialRepository.class);
        usuarioApi = mock(UsuarioApi.class);
        obraApi = mock(ObraApi.class);
        equipeApi = mock(EquipeApi.class);
        colaboradorApi = mock(ColaboradorApi.class);

        useCase = new ExportarSolicitacoesMaterialUseCase(
                repository,
                Optional.of(usuarioApi),
                Optional.of(obraApi),
                Optional.of(equipeApi),
                Optional.of(colaboradorApi)
        );
    }

    @Test
    void exportarResumoComercialGeraApenasColunasComerciais() throws Exception {
        UUID idRequisitante = UUID.randomUUID();
        when(usuarioApi.buscarNomePorId(idRequisitante)).thenReturn(Optional.of("joao.silva"));

        SolicitacaoMaterial comercial = novaSolicitacaoComercial(idRequisitante, new BigDecimal("10.000"));

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comercial)));

        StringWriter writer = new StringWriter();
        SolicitacaoFilter filter = new SolicitacaoFilter(
                null, null, null, null, null, null, null,
                ContextoSolicitacaoMaterial.COMERCIAL,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        useCase.exportarResumo(filter, null, false, writer);

        String csv = writer.toString();
        String[] linhas = csv.split("\r?\n");

        assertThat(linhas).hasSizeGreaterThanOrEqualTo(2);
        // Deve conter colunas comerciais
        assertThat(linhas[0]).contains("ID Solicitação;Contexto;Status;Data Solicitação");
        assertThat(linhas[0]).contains("Estoque Origem;Estoque Destino;Requisitante");
        // NÃO deve conter colunas técnicas
        assertThat(linhas[0]).doesNotContain("Número Obra");
        assertThat(linhas[0]).doesNotContain("Tipo Técnico");
        assertThat(linhas[0]).doesNotContain("Número Reserva");

        assertThat(linhas[1]).contains("COMERCIAL");
        assertThat(linhas[1]).contains("joao.silva");
        assertThat(linhas[1]).contains("10"); // total solicitada
    }

    @Test
    void exportarResumoTecnicoGeraApenasColunasTecnicas() throws Exception {
        UUID idObra = UUID.randomUUID();
        UUID idEquipe = UUID.randomUUID();
        UUID idColab = UUID.randomUUID();

        ObraPublica obra = mock(ObraPublica.class);
        when(obra.numero()).thenReturn("OB-100");
        when(obra.titulo()).thenReturn("Expansão Rede Leste");
        when(obra.tipoObra()).thenReturn(TipoObraPublico.OBRA);
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.of(obra));

        EquipePublica equipe = mock(EquipePublica.class);
        when(equipe.codigo()).thenReturn("EQ-01");
        when(equipeApi.buscarPublicaPorId(idEquipe)).thenReturn(Optional.of(equipe));

        ColaboradorResponse colab = mock(ColaboradorResponse.class);
        when(colab.nomeCompleto()).thenReturn("Carlos Operador");
        when(colaboradorApi.buscarPorIdResponse(idColab)).thenReturn(Optional.of(colab));

        SolicitacaoMaterial tecnica = novaSolicitacaoTecnica(idObra, idEquipe, idColab);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tecnica)));

        StringWriter writer = new StringWriter();
        SolicitacaoFilter filter = new SolicitacaoFilter(
                null, null, null, null, null, null, null,
                ContextoSolicitacaoMaterial.TECNICO,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        useCase.exportarResumo(filter, null, false, writer);

        String csv = writer.toString();
        String[] linhas = csv.split("\r?\n");

        assertThat(linhas).hasSizeGreaterThanOrEqualTo(2);
        // Deve conter colunas técnicas
        assertThat(linhas[0]).contains("Tipo Técnico;Etapa Operacional;Status Separação");
        assertThat(linhas[0]).contains("Almoxarifado Atendimento;Número Obra;Título Obra");
        assertThat(linhas[0]).contains("Código Equipe Saque;Colaborador Saque;Saque Fora da Equipe;Número Reserva");
        // NÃO deve conter colunas comerciais
        assertThat(linhas[0]).doesNotContain("Estoque Destino");
        assertThat(linhas[0]).doesNotContain("Requisitante");

        assertThat(linhas[1]).contains("TECNICO");
        assertThat(linhas[1]).contains("OB-100");
        assertThat(linhas[1]).contains("Expansão Rede Leste");
        assertThat(linhas[1]).contains("EQ-01");
        assertThat(linhas[1]).contains("Carlos Operador");
    }

    @Test
    void exportarResumoConsolidadoGeraLayoutCanonicoSemColunasVazias() throws Exception {
        UUID idRequisitante = UUID.randomUUID();
        when(usuarioApi.buscarNomePorId(idRequisitante)).thenReturn(Optional.of("joao.silva"));

        SolicitacaoMaterial comercial = novaSolicitacaoComercial(idRequisitante, new BigDecimal("15.000"));

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comercial)));

        StringWriter writer = new StringWriter();
        // Contexto null = TODOS
        SolicitacaoFilter filter = new SolicitacaoFilter(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        useCase.exportarResumo(filter, null, false, writer);

        String csv = writer.toString();
        String[] linhas = csv.split("\r?\n");

        assertThat(linhas).hasSizeGreaterThanOrEqualTo(2);
        assertThat(linhas[0]).contains("ID Solicitação;Processo;Status;Tipo Operação;Data Solicitação;Data Atendimento;Origem;Destino / Aplicação;Solicitante / Equipe;Documento / Referência;Observações");
        assertThat(linhas[1]).contains("COMERCIAL");
        assertThat(linhas[1]).contains("Solicitação Direta");
        assertThat(linhas[1]).contains("joao.silva");
    }

    @Test
    void exportarDetalhadoComercialGeraLinhaPorItem() throws Exception {
        UUID idRequisitante = UUID.randomUUID();
        when(usuarioApi.buscarNomePorId(idRequisitante)).thenReturn(Optional.of("maria.gestora"));

        SolicitacaoMaterial solicitacao = novaSolicitacaoComercial(idRequisitante, new BigDecimal("5.000"));

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(solicitacao)));

        StringWriter writer = new StringWriter();
        SolicitacaoFilter filter = new SolicitacaoFilter(
                null, null, null, null, null, null, null,
                ContextoSolicitacaoMaterial.COMERCIAL,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        useCase.exportarDetalhado(filter, null, false, writer);

        String csv = writer.toString();
        String[] linhas = csv.split("\r?\n");

        assertThat(linhas).hasSizeGreaterThanOrEqualTo(2);
        assertThat(linhas[0]).contains("ID Item;Código Material;Descrição Material;Unidade Medida");
        assertThat(linhas[1]).contains("CABO-ALU-01");
        assertThat(linhas[1]).contains("Cabo Alumínio 50mm");
        assertThat(linhas[1]).contains("SERIE-ABC-123");
    }

    @Test
    void exportarZipResumoGeraArquivoComAmbosOsCSVs() throws Exception {
        UUID idRequisitante = UUID.randomUUID();
        SolicitacaoMaterial comercial = novaSolicitacaoComercial(idRequisitante, new BigDecimal("2.000"));

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comercial)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        useCase.exportarZipResumo(null, null, false, out);

        byte[] zipBytes = out.toByteArray();
        assertThat(zipBytes).isNotEmpty();

        // Descompacta e valida entradas
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertThat(entries).containsExactlyInAnyOrder(
                "solicitacoes_comerciais_resumo.csv",
                "solicitacoes_tecnicas_resumo.csv"
        );
    }

    private SolicitacaoMaterial novaSolicitacaoComercial(UUID idRequisitante, BigDecimal quantidade) {
        Estoque origem = new Estoque("ALMOX_CENTRAL", new TipoEstoque("ALMOXARIFADO"));
        ReflectionTestUtils.setField(origem, "id", UUID.randomUUID());
        Estoque destino = new Estoque("VEIC_01", new TipoEstoque("VEICULO"));
        ReflectionTestUtils.setField(destino, "id", UUID.randomUUID());

        SolicitacaoMaterial s = new SolicitacaoMaterial(
                origem,
                destino,
                idRequisitante
        );
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(s, "status", SolicitacaoMaterial.StatusSolicitacao.APROVADO);

        Material mat = new Material("CABO-ALU-01", "Cabo Alumínio 50mm", "M", "MATR", null, null, true, UUID.randomUUID());
        s.adicionarItem(mat, quantidade);
        ReflectionTestUtils.setField(s.getItens().get(0), "id", UUID.randomUUID());
        ReflectionTestUtils.setField(s.getItens().get(0), "numSerie", "SERIE-ABC-123");

        return s;
    }

    private SolicitacaoMaterial novaSolicitacaoTecnica(UUID idObra, UUID idEquipe, UUID idColaborador) {
        Estoque almox = new Estoque("ALMOX_REGIONAL", new TipoEstoque("ALMOXARIFADO"));
        ReflectionTestUtils.setField(almox, "id", UUID.randomUUID());
        SolicitacaoMaterial s;
        try {
            java.lang.reflect.Constructor<SolicitacaoMaterial> ctor = SolicitacaoMaterial.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            s = ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(s, "contexto", ContextoSolicitacaoMaterial.TECNICO);
        ReflectionTestUtils.setField(s, "status", SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        ReflectionTestUtils.setField(s, "tipoTecnico", TipoSolicitacaoTecnica.ADICAO_COMUM);
        ReflectionTestUtils.setField(s, "idObra", idObra);
        ReflectionTestUtils.setField(s, "idEquipeSaque", idEquipe);
        ReflectionTestUtils.setField(s, "idColaboradorSaque", idColaborador);
        ReflectionTestUtils.setField(s, "ocorrencia", "OC-9876");
        ReflectionTestUtils.setField(s, "almoxarifadoAtendimento", almox);
        ReflectionTestUtils.setField(s, "dataSolicitacao", OffsetDateTime.now());
        ReflectionTestUtils.setField(s, "dataPrevistaRetirada", LocalDate.now().plusDays(2));
        ReflectionTestUtils.setField(s, "observacao", "Observação técnica da solicitação");
        ReflectionTestUtils.setField(s, "idRequisitante", UUID.randomUUID());

        return s;
    }
}
