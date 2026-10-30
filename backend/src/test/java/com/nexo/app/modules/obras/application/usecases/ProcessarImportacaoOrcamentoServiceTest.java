package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.ItemOrcamentoObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemOrcamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessarImportacaoOrcamentoServiceTest {

    @Mock
    private ImportacaoObraRepository importacaoRepository;
    @Mock
    private ObraRepository obraRepository;
    @Mock
    private ItemOrcamentoObraRepository itemRepository;
    @Mock
    private ContratoApi contratoApi;
    @Mock
    private PlatformTransactionManager transacoes;

    private ProcessarImportacaoOrcamentoService service;
    private ImportacaoObra importacao;
    private Obra obra;
    private final List<ItemOrcamentoObra> gravados = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ProcessarImportacaoOrcamentoService(importacaoRepository, obraRepository, itemRepository,
                contratoApi, new NormalizadorObraService(), new LeitorPlanilhaImportacao(), transacoes);
        importacao = new ImportacaoObra(ImportacaoObra.Tipo.ORCAMENTO, null, "orcamento.csv");
        ReflectionTestUtils.setField(importacao, "id", UUID.randomUUID());
        obra = ProcessarImportacaoCarteiraServiceTest.novaObra("3000");
        ReflectionTestUtils.setField(obra, "id", UUID.randomUUID());
        when(importacaoRepository.findById(any())).thenReturn(Optional.of(importacao));
        lenient().when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(anyString()))
                .thenAnswer(inv -> "3000".equals(inv.getArgument(0)) ? Optional.of(obra) : Optional.empty());
        lenient().when(itemRepository.findByIdObraAndAtivoTrueOrderByCodigoAsc(any()))
                .thenAnswer(inv -> gravados.stream().filter(ItemOrcamentoObra::isAtivo).toList());
        lenient().when(itemRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<ItemOrcamentoObra> itens = inv.getArgument(0);
            itens.stream().filter(i -> !gravados.contains(i)).forEach(gravados::add);
            return itens;
        });
        lenient().when(contratoApi.buscarAcaoAtivaPorCodigo(anyString())).thenAnswer(inv -> {
            String codigo = inv.getArgument(0, String.class);
            return codigo.startsWith("ACAO") ? Optional.of(new AcaoResponse(UUID.nameUUIDFromBytes(codigo.getBytes()),
                    codigo, codigo, true, null, null)) : Optional.empty();
        });
    }

    @Test
    void importaServicosEAtualizaTotaisDaObra() {
        String csv = "Número;Código Serviço;Descrição;Un;Qtd;Valor Unitário;Valor Total;Valor Material Previsto\n"
                + "3000;ACAO-1;Instalar poste;UN;2;150,00;;60.000,00\n"
                + "3000;OUTRO-9;Serviço sem ação;M;10,5;;210,00;\n"
                + "3000;ACAO-2;Sem valor;UN;1;;;\n"
                + "9999;ACAO-1;Obra inexistente;UN;1;10;;\n";
        service.processar(UUID.randomUUID(), csv.getBytes(StandardCharsets.UTF_8));

        assertThat(importacao.getStatus()).isEqualTo(ImportacaoObra.Status.CONCLUIDA);
        assertThat(importacao.getInseridos()).isEqualTo(2);
        assertThat(importacao.getErros()).isEqualTo(2);
        assertThat(importacao.getDetalheErros()).extracting(ImportacaoObra.ErroLinha::motivo)
                .anySatisfy(m -> assertThat(m).contains("valor unitário ou o valor total"))
                .anySatisfy(m -> assertThat(m).contains("Obra 9999 não encontrada"));
        assertThat(gravados).hasSize(2);
        ItemOrcamentoObra poste = gravados.stream().filter(i -> i.getCodigo().equals("ACAO-1")).findFirst().orElseThrow();
        assertThat(poste.getValorTotal()).isEqualByComparingTo("300.00");
        assertThat(poste.getIdAcao()).isNotNull();
        assertThat(gravados.stream().filter(i -> i.getCodigo().equals("OUTRO-9")).findFirst().orElseThrow().getIdAcao())
                .isNull();
        assertThat(obra.getValorMaterialPrevisto()).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    @Test
    void reimportarSubstituiOsServicosAnterioresDaObra() {
        service.processar(UUID.randomUUID(), "NUMERO;CODIGO;QUANTIDADE;VALOR_TOTAL\n3000;ACAO-1;1;100\n"
                .getBytes(StandardCharsets.UTF_8));
        ItemOrcamentoObra anterior = gravados.getFirst();

        service.processar(UUID.randomUUID(), "NUMERO;CODIGO;QUANTIDADE;VALOR_TOTAL\n3000;ACAO-3;2;400\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(anterior.isAtivo()).isFalse();
        assertThat(gravados.stream().filter(ItemOrcamentoObra::isAtivo))
                .extracting(ItemOrcamentoObra::getCodigo).containsExactly("ACAO-3");
    }
}
