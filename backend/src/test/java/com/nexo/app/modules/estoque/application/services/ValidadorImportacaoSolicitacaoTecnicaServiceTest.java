package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.obras.AgenteResponsavelApi;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ValidadorImportacaoSolicitacaoTecnicaServiceTest {

    @Mock private ObraApi obraApi;
    @Mock private EquipeApi equipeApi;
    @Mock private AgenteResponsavelApi agenteApi;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MaterialRepository materialRepository;
    private ValidadorImportacaoSolicitacaoTecnicaService service;
    private ObraPublica obra;
    private Estoque almoxarifado;
    private Material comum;
    private Material concreto;

    @BeforeEach
    void setUp() {
        service = new ValidadorImportacaoSolicitacaoTecnicaService(
                obraApi, equipeApi, agenteApi, estoqueRepository, materialRepository);
        UUID idEquipe = UUID.randomUUID();
        obra = new ObraPublica(UUID.randomUUID(), "OBRA-1", "Corretiva", TipoObraPublico.CORRETIVA,
                true, UUID.randomUUID(), "TECNICO", true, UUID.randomUUID(), Set.of(idEquipe), null, List.of());
        almoxarifado = comId(new Estoque("ALMOX", comId(new TipoEstoque("FISICO"))));
        comum = material("COMUM");
        concreto = material("CONCRETO");
        when(obraApi.buscarPorNumero("OBRA-1")).thenReturn(Optional.of(obra));
        when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX"))
                .thenReturn(Optional.of(almoxarifado));
        lenient().when(materialRepository.findByCodigoIgnoreCase("COMUM")).thenReturn(Optional.of(comum));
        lenient().when(materialRepository.findByCodigoIgnoreCase("CONCRETO")).thenReturn(Optional.of(concreto));
    }

    @Test
    void agrupaPorObraTipoEDataPrevistaSeparandoEmSolicitacoesDistintas() {
        var previa = service.validar(List.of(
                linha(2, "221", "COMUM", "1.125"),
                linha(3, "921", "CONCRETO", "2"),
                linha(4, "222", "COMUM", "3"),
                linha(5, "922", "CONCRETO", "4")));

        assertThat(previa.solicitacoesPrevistas()).hasSize(4);
        assertThat(previa.solicitacoesPrevistas()).extracting(item -> item.chave())
                .containsExactly("OBRA-1 - ALMOX - 221", "OBRA-1 - ALMOX - 921", "OBRA-1 - ALMOX - 222", "OBRA-1 - ALMOX - 922");
        assertThat(previa.solicitacoesPrevistas()).allSatisfy(grupo -> {
            assertThat(grupo.confirmavel()).isTrue();
            assertThat(grupo.ocorrencia()).isEqualTo("OS 123");
            assertThat(grupo.subgrupos()).hasSize(1);
        });
    }

    @Test
    void agrupaComDataPrevistaInformada() {
        Map<String, String> valores1 = new LinkedHashMap<>();
        ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS.forEach(campo -> valores1.put(campo, ""));
        valores1.put("Numero Obra", "OBRA-1");
        valores1.put("Ocorrencia OC OS", "OS 123");
        valores1.put("Almoxarifado Atendimento", "ALMOX");
        valores1.put("Data Prevista", "2026-09-30");
        valores1.put("Tipo Movimentacao", "221");
        valores1.put("Codigo Material", "COMUM");
        valores1.put("Quantidade", "5.000");

        Map<String, String> valores2 = new LinkedHashMap<>(valores1);
        valores2.put("Data Prevista", "2026-10-15");
        valores2.put("Quantidade", "2.000");

        var previa = service.validar(List.of(
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(2, valores1, Set.of()),
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(3, valores2, Set.of())));

        assertThat(previa.solicitacoesPrevistas()).hasSize(2);
        assertThat(previa.solicitacoesPrevistas()).extracting(item -> item.chave())
                .containsExactly("OBRA-1 - ALMOX - 221 - 2026-09-30", "OBRA-1 - ALMOX - 221 - 2026-10-15");
    }

    @Test
    void consolidaMaterialNaoSeriadoRepetidoComPrecisaoESinalizaAviso() {
        var previa = service.validar(List.of(
                linha(2, "221", "COMUM", "1.125"),
                linha(3, "221", "COMUM", "2.250")));

        var grupo = previa.solicitacoesPrevistas().getFirst();
        assertThat(grupo.confirmavel()).isTrue();
        assertThat(grupo.possuiAvisos()).isTrue();
        assertThat(grupo.subgrupos().getFirst().itens()).singleElement().satisfies(item -> {
            assertThat(item.quantidade()).isEqualByComparingTo("3.375");
            assertThat(item.linhasOrigem()).containsExactly(2, 3);
        });
        assertThat(grupo.mensagens()).extracting(mensagem -> mensagem.codigo())
                .contains("MATERIAL_DUPLICADO_CONSOLIDADO");
    }

    @Test
    void divergenciaDeOcorrenciaInvalidaTodoAgrupamentoSemCadastrarOcorrencia() {
        var primeira = linha(2, "221", "COMUM", "1");
        var segunda = linha(3, "221", "CONCRETO", "1");
        Map<String, String> valores = new LinkedHashMap<>(segunda.valores());
        valores.put("Ocorrencia OC OS", "OC 999");

        var previa = service.validar(List.of(primeira,
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(3, valores, Set.of())));

        assertThat(previa.solicitacoesPrevistas().getFirst().confirmavel()).isFalse();
        assertThat(previa.solicitacoesPrevistas().getFirst().mensagens()).extracting(mensagem -> mensagem.codigo())
                .contains("CABECALHO_DIVERGENTE");
    }

    @Test
    void linhasIguaisComEquipesDiferentesGeramSolicitacoesIndependentesSemDuplicidade() {
        UUID idEquipe1 = UUID.randomUUID();
        UUID idEquipe2 = UUID.randomUUID();
        lenient().when(equipeApi.buscarIdPorCodigo("EQ-01")).thenReturn(Optional.of(idEquipe1));
        lenient().when(equipeApi.buscarAtivaPorId(idEquipe1)).thenReturn(Optional.of(
                new EquipeApi.EquipePublica(idEquipe1, "EQ-01", true, null, null, null, null)));
        lenient().when(equipeApi.buscarIdPorCodigo("EQ-02")).thenReturn(Optional.of(idEquipe2));
        lenient().when(equipeApi.buscarAtivaPorId(idEquipe2)).thenReturn(Optional.of(
                new EquipeApi.EquipePublica(idEquipe2, "EQ-02", true, null, null, null, null)));

        Map<String, String> valores1 = new LinkedHashMap<>();
        ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS.forEach(campo -> valores1.put(campo, ""));
        valores1.put("Numero Obra", "OBRA-1");
        valores1.put("Ocorrencia OC OS", "OS 123");
        valores1.put("Almoxarifado Atendimento", "ALMOX");
        valores1.put("Codigo Equipe", "EQ-01");
        valores1.put("Data Prevista", "2026-09-30");
        valores1.put("Tipo Movimentacao", "221");
        valores1.put("Codigo Material", "COMUM");
        valores1.put("Quantidade", "5.000");

        Map<String, String> valores2 = new LinkedHashMap<>(valores1);
        valores2.put("Codigo Equipe", "EQ-02");

        var previa = service.validar(List.of(
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(2, valores1, Set.of()),
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(3, valores2, Set.of())));

        assertThat(previa.solicitacoesPrevistas()).hasSize(2);
        assertThat(previa.solicitacoesPrevistas()).extracting(item -> item.chave())
                .containsExactly("OBRA-1 - ALMOX - EQ-01 - 221 - 2026-09-30", "OBRA-1 - ALMOX - EQ-02 - 221 - 2026-09-30");
        assertThat(previa.solicitacoesPrevistas()).allSatisfy(sol -> {
            assertThat(sol.confirmavel()).isTrue();
            assertThat(sol.mensagens()).isEmpty();
            assertThat(sol.subgrupos().getFirst().itens()).singleElement().satisfies(item -> {
                assertThat(item.quantidade()).isEqualByComparingTo("5.000");
                assertThat(item.codigoMaterial()).isEqualTo("COMUM");
            });
        });
    }

    @Test
    void linhasIguaisComReservasDiferentesGeramSolicitacoesIndependentesSemDuplicidade() {
        Map<String, String> valores1 = new LinkedHashMap<>();
        ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS.forEach(campo -> valores1.put(campo, ""));
        valores1.put("Numero Obra", "OBRA-1");
        valores1.put("Ocorrencia OC OS", "OS 123");
        valores1.put("Almoxarifado Atendimento", "ALMOX");
        valores1.put("Data Prevista", "2026-09-30");
        valores1.put("Tipo Movimentacao", "221");
        valores1.put("Codigo Material", "COMUM");
        valores1.put("Quantidade", "5.000");
        valores1.put("Numero Reserva", "1001");
        valores1.put("Data Criacao Reserva", "2026-09-20");

        Map<String, String> valores2 = new LinkedHashMap<>(valores1);
        valores2.put("Numero Reserva", "1002");

        var previa = service.validar(List.of(
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(2, valores1, Set.of()),
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(3, valores2, Set.of())));

        assertThat(previa.solicitacoesPrevistas()).hasSize(2);
        assertThat(previa.solicitacoesPrevistas()).extracting(item -> item.chave())
                .containsExactly("OBRA-1 - ALMOX - 1001 - 221 - 2026-09-30", "OBRA-1 - ALMOX - 1002 - 221 - 2026-09-30");
        assertThat(previa.solicitacoesPrevistas()).allSatisfy(sol -> {
            assertThat(sol.confirmavel()).isTrue();
            assertThat(sol.mensagens()).isEmpty();
            assertThat(sol.subgrupos().getFirst().itens()).singleElement().satisfies(item -> {
                assertThat(item.quantidade()).isEqualByComparingTo("5.000");
                assertThat(item.codigoMaterial()).isEqualTo("COMUM");
            });
        });
    }

    @Test
    void linhasIguaisComAlmoxarifadosDiferentesGeramSolicitacoesIndependentesSemDuplicidade() {
        Estoque almox2 = comId(new Estoque("ALMOX-2", comId(new TipoEstoque("FISICO"))));
        lenient().when(estoqueRepository.findByNomeIdentificadorIgnoreCase("ALMOX-2")).thenReturn(Optional.of(almox2));

        Map<String, String> valores1 = new LinkedHashMap<>();
        ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS.forEach(campo -> valores1.put(campo, ""));
        valores1.put("Numero Obra", "OBRA-1");
        valores1.put("Ocorrencia OC OS", "OS 123");
        valores1.put("Almoxarifado Atendimento", "ALMOX");
        valores1.put("Data Prevista", "2026-09-30");
        valores1.put("Tipo Movimentacao", "221");
        valores1.put("Codigo Material", "COMUM");
        valores1.put("Quantidade", "5.000");

        Map<String, String> valores2 = new LinkedHashMap<>(valores1);
        valores2.put("Almoxarifado Atendimento", "ALMOX-2");

        var previa = service.validar(List.of(
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(2, valores1, Set.of()),
                new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(3, valores2, Set.of())));

        assertThat(previa.solicitacoesPrevistas()).hasSize(2);
        assertThat(previa.solicitacoesPrevistas()).extracting(item -> item.chave())
                .containsExactly("OBRA-1 - ALMOX - 221 - 2026-09-30", "OBRA-1 - ALMOX-2 - 221 - 2026-09-30");
        assertThat(previa.solicitacoesPrevistas()).allSatisfy(sol -> {
            assertThat(sol.confirmavel()).isTrue();
            assertThat(sol.mensagens()).isEmpty();
            assertThat(sol.subgrupos().getFirst().itens()).singleElement().satisfies(item -> {
                assertThat(item.quantidade()).isEqualByComparingTo("5.000");
                assertThat(item.codigoMaterial()).isEqualTo("COMUM");
            });
        });
    }

    private LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo linha(
            int numero,
            String tipo,
            String material,
            String quantidade
    ) {
        Map<String, String> valores = new LinkedHashMap<>();
        ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS.forEach(campo -> valores.put(campo, ""));
        valores.put("Numero Obra", "OBRA-1");
        valores.put("Ocorrencia OC OS", "  os   123 ");
        valores.put("Almoxarifado Atendimento", "ALMOX");
        valores.put("Tipo Movimentacao", tipo);
        valores.put("Codigo Material", material);
        valores.put("Quantidade", quantidade);
        return new LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo(numero, valores, Set.of());
    }

    private Material material(String codigo) {
        return comId(new Material(codigo, "Material " + codigo, "UN", "MATR", null, null,
                false, UUID.randomUUID()));
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
