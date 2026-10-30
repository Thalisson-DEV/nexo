package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.obras.application.dto.ItemKitRequest;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoRequest;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoResponse;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitConstrutivoUseCasesTest {

    @Mock
    private KitConstrutivoRepository repository;
    @Mock
    private ContratoApi contratoApi;
    @Mock
    private EstoqueApi estoqueApi;
    @InjectMocks
    private KitConstrutivoUseCases useCases;

    private final UUID contrato = UUID.randomUUID();
    private final UUID acao = UUID.randomUUID();
    private final UUID material = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(contratoApi.existeContrato(contrato)).thenReturn(true);
        lenient().when(contratoApi.existeAcao(acao)).thenReturn(true);
        lenient().when(estoqueApi.buscarMaterialPorIdResponse(material)).thenReturn(Optional.of(
                new MaterialResponse(material, "MAT-1", "Cruzeta", "UN", null, null, null, null, false, true)));
        lenient().when(contratoApi.buscarContratoResumo(contrato))
                .thenReturn(Optional.of(new ContratoApi.ContratoResumo(contrato, "CT-01", "Cliente", true)));
        lenient().when(contratoApi.buscarAcoesPorIdsResponse(anyCollection()))
                .thenReturn(List.of(new AcaoResponse(acao, "A-01", "Instalar estrutura BT", true, null, null)));
        lenient().when(estoqueApi.buscarMateriaisPorIdsResponse(anyCollection())).thenReturn(List.of());
        lenient().when(repository.save(any(KitConstrutivo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criaKitEValorizaSomenteServicosPeloValorVigente() {
        when(contratoApi.listarAcoesComValor(eq(contrato), any(LocalDate.class))).thenReturn(List.of(
                new ContratoApi.AcaoValorItem(acao, "A-01", "Instalar estrutura BT", new BigDecimal("150.00"))));

        KitConstrutivoResponse response = useCases.criar(new KitConstrutivoRequest(contrato, "lmi-0688", "Estrutura",
                "Estrutura", Set.of(), List.of(
                        new ItemKitRequest(null, TipoItemKit.SERVICO, acao, OperacaoItemKit.INSTALAR, "UN", new BigDecimal("2")),
                        new ItemKitRequest(null, TipoItemKit.MATERIAL, material, OperacaoItemKit.INSTALAR, "UN", new BigDecimal("4")))));

        assertThat(response.codigo()).isEqualTo("LMI-0688");
        assertThat(response.numeroContrato()).isEqualTo("CT-01");
        assertThat(response.quantidadeItens()).isEqualTo(2);
        assertThat(response.valor()).isEqualByComparingTo("300.00");
        assertThat(response.servicosSemValor()).isZero();
        assertThat(response.itens()).filteredOn(i -> i.tipo() == TipoItemKit.SERVICO)
                .singleElement().satisfies(i -> {
                    assertThat(i.codigoReferencia()).isEqualTo("A-01");
                    assertThat(i.valorTotal()).isEqualByComparingTo("300.00");
                });
    }

    @Test
    void servicoSemValorVigenteFicaForaDaSomaESinalizado() {
        when(contratoApi.listarAcoesComValor(eq(contrato), any(LocalDate.class))).thenReturn(List.of());

        KitConstrutivoResponse response = useCases.criar(new KitConstrutivoRequest(contrato, "K1", "Kit", null, null,
                List.of(new ItemKitRequest(null, TipoItemKit.SERVICO, acao, null, null, BigDecimal.ONE))));

        assertThat(response.valor()).isEqualByComparingTo("0");
        assertThat(response.servicosSemValor()).isEqualTo(1);
    }

    @Test
    void valorUsaADataDeReferenciaInformada() {
        LocalDate data = LocalDate.of(2025, 1, 10);
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);
        kit.adicionarItem(TipoItemKit.SERVICO, acao, null, null, BigDecimal.ONE);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(kit));
        when(contratoApi.listarAcoesComValor(contrato, data)).thenReturn(List.of(
                new ContratoApi.AcaoValorItem(acao, "A-01", "x", new BigDecimal("90.00"))));

        KitConstrutivoResponse response = useCases.buscarPorId(id, data);

        assertThat(response.valor()).isEqualByComparingTo("90.00");
        assertThat(response.dataReferenciaValor()).isEqualTo(data);
    }

    @Test
    void rejeitaCodigoDuplicadoAtivoNoContrato() {
        when(repository.existsByIdContratoAndCodigoAndAtivoTrue(contrato, "K1")).thenReturn(true);

        assertThatThrownBy(() -> useCases.criar(new KitConstrutivoRequest(contrato, "k1", "Kit", null, null, List.of())))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("K1");
        verify(repository, never()).save(any());
    }

    @Test
    void rejeitaItemComAcaoOuMaterialInexistente() {
        UUID acaoInexistente = UUID.randomUUID();
        when(contratoApi.existeAcao(acaoInexistente)).thenReturn(false);

        assertThatThrownBy(() -> useCases.criar(new KitConstrutivoRequest(contrato, "K1", "Kit", null, null,
                List.of(new ItemKitRequest(null, TipoItemKit.SERVICO, acaoInexistente, null, null, BigDecimal.ONE)))))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void naoPermiteMudarContratoOuCodigoNaEdicao() {
        KitConstrutivo kit = new KitConstrutivo(contrato, "K1", "Kit", null, null);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(kit));

        assertThatThrownBy(() -> useCases.atualizar(id, new KitConstrutivoRequest(UUID.randomUUID(), "K1", "Kit", null, null, null)))
                .hasMessageContaining("contrato");
        assertThatThrownBy(() -> useCases.atualizar(id, new KitConstrutivoRequest(contrato, "K2", "Kit", null, null, null)))
                .hasMessageContaining("código");
    }
}
