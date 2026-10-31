package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessarImportacaoKitsServiceTest {

    @Mock
    private ImportacaoObraRepository importacaoRepository;
    @Mock
    private KitConstrutivoRepository kitRepository;
    @Mock
    private ContratoApi contratoApi;
    @Mock
    private EstoqueApi estoqueApi;
    @Mock
    private PlatformTransactionManager transacoes;

    private ProcessarImportacaoKitsService service;
    private ImportacaoObra importacao;
    private final UUID contrato = UUID.randomUUID();
    private final Map<String, KitConstrutivo> kitsGravados = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new ProcessarImportacaoKitsService(importacaoRepository, kitRepository, contratoApi, estoqueApi,
                transacoes);
        importacao = new ImportacaoObra(ImportacaoObra.Tipo.KITS, contrato, "kits.csv");
        when(importacaoRepository.findById(any())).thenReturn(Optional.of(importacao));
        lenient().when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(kitRepository.findByIdContratoAndCodigoAndAtivoTrue(eq(contrato), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(kitsGravados.get(inv.getArgument(1, String.class))));
        lenient().when(kitRepository.save(any(KitConstrutivo.class))).thenAnswer(inv -> {
            KitConstrutivo kit = inv.getArgument(0);
            kitsGravados.put(kit.getCodigo(), kit);
            return kit;
        });
        lenient().when(contratoApi.buscarAcaoAtivaPorCodigo(anyString())).thenAnswer(inv -> {
            String codigo = inv.getArgument(0, String.class);
            return codigo.startsWith("ACAO") ? Optional.of(new AcaoResponse(UUID.nameUUIDFromBytes(codigo.getBytes()),
                    codigo, codigo, true, null, null)) : Optional.empty();
        });
        lenient().when(estoqueApi.buscarMaterialAtivoPorCodigo(anyString())).thenAnswer(inv -> {
            String codigo = inv.getArgument(0, String.class);
            return codigo.startsWith("MAT") ? Optional.of(new MaterialResponse(UUID.nameUUIDFromBytes(codigo.getBytes()),
                    codigo, codigo, "UN", null, null, null, null, false, true)) : Optional.empty();
        });
    }

    @Test
    void importaPlanilhaCsvEReportaLinhasComReferenciaInexistenteSemInterromperAsDemais() {
        StringBuilder csv = new StringBuilder("Código Kit;Descrição Kit;Grupo;Tensões;Tipo Item;Código Item;Operação;Qtd;Unidade\n");
        for (int i = 1; i <= 100; i++) {
            String kit = "KIT-" + (i % 10);
            String item = switch (i) {
                case 7, 50 -> "XMAT-" + i;   // material inexistente
                case 93 -> "YMAT-" + i;      // material inexistente
                default -> (i % 2 == 0 ? "ACAO-" : "MAT-") + i;
            };
            String tipo = item.startsWith("ACAO") ? "Serviço" : "Material";
            csv.append(kit).append(";Kit ").append(kit).append(";Estrutura;15 kV;")
                    .append(tipo).append(';').append(item).append(";Instalar;1,5;UN\n");
        }

        service.processar(UUID.randomUUID(), linhas(csv.toString()));

        assertThat(importacao.getStatus()).isEqualTo(ImportacaoObra.Status.CONCLUIDA);
        assertThat(importacao.getTotalLinhas()).isEqualTo(100);
        assertThat(importacao.getInseridos()).isEqualTo(97);
        assertThat(importacao.getErros()).isEqualTo(3);
        assertThat(importacao.getDetalheErros()).extracting(ImportacaoObra.ErroLinha::linha)
                .containsExactlyInAnyOrder(8, 51, 94);
        assertThat(importacao.getDetalheErros()).allSatisfy(e -> assertThat(e.motivo()).contains("não encontrado"));
        assertThat(kitsGravados).hasSize(10);
        KitConstrutivo kit1 = kitsGravados.get("KIT-1");
        assertThat(kit1.getGrupo()).isEqualTo("ESTRUTURA");
        assertThat(kit1.getClassesTensao()).containsExactly(ClasseTensao.MT_15);
        assertThat(kit1.getItensAtivos()).allSatisfy(i -> assertThat(i.getQuantidade()).isEqualByComparingTo("1.5"));
        verify(importacaoRepository, atLeastOnce()).save(importacao);
    }

    @Test
    void reimportarAtualizaItensExistentes() {
        String csv = "CODIGO_KIT;DESCRICAO_KIT;TIPO_ITEM;CODIGO_ITEM;QUANTIDADE\nK1;Kit 1;SERVICO;ACAO-1;1\n";
        service.processar(UUID.randomUUID(), linhas(csv));

        ImportacaoObra segunda = new ImportacaoObra(ImportacaoObra.Tipo.KITS, contrato, "kits.csv");
        when(importacaoRepository.findById(any())).thenReturn(Optional.of(segunda));
        service.processar(UUID.randomUUID(), linhas("CODIGO_KIT;TIPO_ITEM;CODIGO_ITEM;QUANTIDADE\nK1;S;ACAO-1;3\n"
                ));

        assertThat(segunda.getAtualizados()).isEqualTo(1);
        assertThat(segunda.getInseridos()).isZero();
        assertThat(kitsGravados.get("K1").getDescricao()).isEqualTo("Kit 1");
        assertThat(kitsGravados.get("K1").getItensAtivos()).singleElement()
                .satisfies(i -> assertThat(i.getQuantidade()).isEqualByComparingTo(new BigDecimal("3")));
    }

    @Test
    void kitNovoSemDescricaoRejeitaTodasAsLinhasDoKit() {
        String csv = "CODIGO_KIT;TIPO_ITEM;CODIGO_ITEM;QUANTIDADE\nK9;SERVICO;ACAO-1;1\nK9;MATERIAL;MAT-1;1\n";

        service.processar(UUID.randomUUID(), linhas(csv));

        assertThat(importacao.getErros()).isEqualTo(2);
        assertThat(importacao.getDetalheErros()).allSatisfy(e -> assertThat(e.motivo()).contains("sem descrição"));
        assertThat(kitsGravados).isEmpty();
    }

    @Test
    void importaPlanilhaXlsx() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var aba = wb.createSheet("Kits");
            String[] cab = {"CODIGO_KIT", "DESCRICAO_KIT", "TIPO_ITEM", "CODIGO_ITEM", "OPERACAO", "QUANTIDADE"};
            var linha0 = aba.createRow(0);
            for (int c = 0; c < cab.length; c++) linha0.createCell(c).setCellValue(cab[c]);
            var linha1 = aba.createRow(1);
            linha1.createCell(0).setCellValue("K-XLSX");
            linha1.createCell(1).setCellValue("Kit planilha");
            linha1.createCell(2).setCellValue("MATERIAL");
            linha1.createCell(3).setCellValue("MAT-9");
            linha1.createCell(4).setCellValue("Retirar");
            linha1.createCell(5).setCellValue(2);
            wb.write(saida);
        }

        service.processar(UUID.randomUUID(), new LeitorPlanilhaImportacao().ler(saida.toByteArray(), "kits.xlsx"));

        assertThat(importacao.getInseridos()).isEqualTo(1);
        assertThat(kitsGravados.get("K-XLSX").getItensAtivos()).singleElement()
                .satisfies(i -> assertThat(i.getOperacao().name()).isEqualTo("RETIRAR"));
    }


    private static java.util.List<LeitorPlanilhaImportacao.Linha> linhas(String csv) {
        return new LeitorPlanilhaImportacao().ler(csv.getBytes(StandardCharsets.UTF_8), "planilha.csv");
    }
}
