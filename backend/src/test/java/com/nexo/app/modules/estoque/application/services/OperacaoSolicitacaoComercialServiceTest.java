package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.application.dto.EntregaSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.EstornoParcialRequest;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperacaoSolicitacaoComercialServiceTest {

    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private SaldoRepository saldoRepository;
    @Mock private SaldoSerieRepository serieRepository;
    @Mock private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private AlteracoesSolicitacaoComercialService alteracoesComerciais;
    @Mock private AssinaturaSolicitacaoMaterialService assinaturas;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private EquipeApi equipeApi;
    @Mock private DebitoSaldoService debitoSaldoService;
    @Mock private EstoqueRepository estoqueRepository;
    @InjectMocks private OperacaoSolicitacaoComercialService service;

    @Test
    void entregaComercialPreservaContratoLegadoEMovimentaOrigemEDestino() {
        TipoEstoque fisico = comId(new TipoEstoque("FISICO"));
        Estoque origem = comId(new Estoque("ORIGEM", fisico));
        Estoque destino = comId(new Estoque("DESTINO", fisico));
        Material material = comId(new Material(
                "MAT-1", "Material", "UN", "MATR", null, null, false, UUID.randomUUID()));
        SolicitacaoMaterial solicitacao = comId(
                new SolicitacaoMaterial(origem, destino, UUID.randomUUID()));
        solicitacao.adicionarItem(material, new BigDecimal("10.000"));
        solicitacao.aprovar(UUID.randomUUID());
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());

        Saldo saldoOrigem = new Saldo(origem, material, CondicaoMaterial.NOVO);
        saldoOrigem.adicionarQuantidade(new BigDecimal("10.000"));
        UUID idEntregador = UUID.randomUUID();
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                origem.getId(), material.getId(), CondicaoMaterial.NOVO))
                .thenReturn(Optional.of(saldoOrigem));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                destino.getId(), material.getId(), CondicaoMaterial.NOVO))
                .thenReturn(Optional.empty());

        UUID idOperacao = service.registrarEntrega(
                solicitacao.getId(), idEntregador,
                new EntregaSolicitacaoRequest(
                        "assinatura-almox", "assinatura-equipe",
                        List.of(new EntregaSolicitacaoRequest.ItemEntregaRequest(
                                item.getId(), new BigDecimal("4.000"), null, null)),
                        List.of(), List.of()));

        assertThat(idOperacao).isNotNull();
        verify(debitoSaldoService).debitar(
                origem, material, CondicaoMaterial.NOVO, new BigDecimal("4.000"));
        assertThat(item.getQuantidadeEntregue()).isEqualByComparingTo("4.000");
        assertThat(solicitacao.getIdEntregadorAlmox()).isEqualTo(idEntregador);
        assertThat(solicitacao.getAssinaturaAlmox()).isEqualTo("assinatura-almox");
        assertThat(solicitacao.getAssinaturaEquipe()).isEqualTo("assinatura-equipe");
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getIdReferenciaOperacao()).isEqualTo(idOperacao);
        assertThat(movimento.getValue().getEstoqueOrigem()).isSameAs(origem);
        assertThat(movimento.getValue().getEstoqueDestino()).isSameAs(destino);
        verify(solicitacaoRepository).save(solicitacao);
        verify(saldoRepository).save(any(Saldo.class));
    }

    @Test
    void entregaComercialRegistraResponsavelPeloSaqueForaDaEquipe() {
        Fixture fixture = fixtureAprovada();
        UUID idColaborador = UUID.randomUUID();
        when(solicitacaoRepository.findById(fixture.solicitacao().getId()))
                .thenReturn(Optional.of(fixture.solicitacao()));
        when(colaboradorApi.buscarPorIdResponse(idColaborador)).thenReturn(Optional.of(
                new ColaboradorResponse(idColaborador, null, "Ana", "Souza", "Ana Souza",
                        null, null, null, null, null, true)));
        Saldo saldoOrigem = new Saldo(
                fixture.solicitacao().getEstoqueOrigem(), fixture.item().getMaterial(), CondicaoMaterial.NOVO);
        saldoOrigem.adicionarQuantidade(BigDecimal.ONE);
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                fixture.solicitacao().getEstoqueOrigem().getId(), fixture.item().getMaterial().getId(),
                CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldoOrigem));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                fixture.solicitacao().getEstoqueDestino().getId(), fixture.item().getMaterial().getId(),
                CondicaoMaterial.NOVO)).thenReturn(Optional.empty());

        service.registrarEntrega(
                fixture.solicitacao().getId(), UUID.randomUUID(),
                new EntregaSolicitacaoRequest(
                        "FISICA", "FISICA",
                        List.of(new EntregaSolicitacaoRequest.ItemEntregaRequest(
                                fixture.item().getId(), BigDecimal.ONE, null, null)),
                        List.of(), List.of(), idColaborador, true));

        assertThat(fixture.solicitacao().getIdColaboradorSaque()).isEqualTo(idColaborador);
        assertThat(fixture.solicitacao().isSaqueForaEquipe()).isTrue();
    }

    @Test
    void entregaComItemDuplicadoEhRejeitadaAntesDeQualquerMovimento() {
        Fixture fixture = fixtureAprovada();
        UUID idEntregador = UUID.randomUUID();
        var itemDuplicado = new EntregaSolicitacaoRequest.ItemEntregaRequest(
                fixture.item().getId(), new BigDecimal("1.000"), null, null);
        EntregaSolicitacaoRequest request = new EntregaSolicitacaoRequest(
                "assinatura-almox", "assinatura-equipe",
                List.of(itemDuplicado, itemDuplicado), List.of(), List.of());
        when(solicitacaoRepository.findById(fixture.solicitacao().getId()))
                .thenReturn(Optional.of(fixture.solicitacao()));

        assertThatThrownBy(() -> service.registrarEntrega(
                fixture.solicitacao().getId(), idEntregador, request))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("mais de uma vez na entrega");

        assertThat(fixture.item().getQuantidadeEntregue()).isEqualByComparingTo("0.000");
        verify(solicitacaoRepository, never()).save(any());
        verifyNoInteractions(saldoRepository, serieRepository, movimentacaoRepository, materialRepository, gravadorEvento);
    }

    @Test
    void entregaSubstituiItemSeriadoSemFatoPorMaterialComum() {
        TipoEstoque fisico = comId(new TipoEstoque("FISICO"));
        Estoque origem = comId(new Estoque("ORIGEM", fisico));
        Estoque destino = comId(new Estoque("DESTINO", fisico));
        Material seriado = comId(new Material(
                "SER-1", "Material seriado", "UN", "MATR", null, null, true, UUID.randomUUID()));
        Material comum = comId(new Material(
                "COM-1", "Material comum", "UN", "MATR", null, null, false, UUID.randomUUID()));
        SolicitacaoMaterial solicitacao = comId(
                new SolicitacaoMaterial(origem, destino, UUID.randomUUID()));
        solicitacao.adicionarItem(seriado, BigDecimal.ONE);
        solicitacao.aprovar(UUID.randomUUID());
        ItemSolicitacao itemSeriado = solicitacao.getItens().getFirst();
        ReflectionTestUtils.setField(itemSeriado, "id", UUID.randomUUID());

        Saldo saldoOrigem = new Saldo(origem, comum, CondicaoMaterial.NOVO);
        saldoOrigem.adicionarQuantidade(new BigDecimal("3.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(materialRepository.findById(comum.getId())).thenReturn(Optional.of(comum));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                origem.getId(), comum.getId(), CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldoOrigem));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                destino.getId(), comum.getId(), CondicaoMaterial.NOVO)).thenReturn(Optional.empty());

        service.registrarEntrega(
                solicitacao.getId(), UUID.randomUUID(),
                new EntregaSolicitacaoRequest(
                        "assinatura-almox", "assinatura-equipe", List.of(),
                        List.of(new EntregaSolicitacaoRequest.NovoItemEntregaRequest(
                                comum.getId(), new BigDecimal("3.000"), new BigDecimal("5.000"), null)),
                        List.of(itemSeriado.getId())));

        assertThat(solicitacao.getItens()).singleElement().satisfies(item -> {
            assertThat(item.getMaterial()).isSameAs(comum);
            assertThat(item.getQuantidade()).isEqualByComparingTo("5.000");
            assertThat(item.getQuantidadeEntregueBruta()).isEqualByComparingTo("3.000");
            assertThat(item.getQuantidadeEntregaLiquida()).isEqualByComparingTo("3.000");
            assertThat(item.getQuantidadePendente()).isEqualByComparingTo("2.000");
        });
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        verify(solicitacaoRepository).save(solicitacao);
    }

    @Test
    void entregaComSaldoEncerradoEhRejeitadaAntesDeQualquerMovimento() {
        Fixture fixture = fixtureAprovada();
        fixture.solicitacao().registrarEntrega(
                UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                Map.of(fixture.item().getId(), new SolicitacaoMaterial.ItemEntregaInfo(
                        new BigDecimal("4.000"), null, null)), List.of());
        fixture.solicitacao().encerrarEntregaParcialExpirada();
        var item = new EntregaSolicitacaoRequest.ItemEntregaRequest(
                fixture.item().getId(), new BigDecimal("1.000"), null, null);
        EntregaSolicitacaoRequest request = new EntregaSolicitacaoRequest(
                "assinatura-almox", "assinatura-equipe", List.of(item), List.of(), List.of());
        when(solicitacaoRepository.findById(fixture.solicitacao().getId()))
                .thenReturn(Optional.of(fixture.solicitacao()));

        assertThatThrownBy(() -> service.registrarEntrega(
                fixture.solicitacao().getId(), UUID.randomUUID(), request))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("não aceita novas entregas");

        assertThat(fixture.item().getQuantidadeEntregue()).isEqualByComparingTo("4.000");
        verify(solicitacaoRepository, never()).save(any());
        verifyNoInteractions(saldoRepository, serieRepository, movimentacaoRepository, materialRepository, gravadorEvento);
    }

    @Test
    void estornoParcialComItemDuplicadoEhRejeitadoAntesDeQualquerMovimento() {
        Fixture fixture = fixtureAprovada();
        fixture.solicitacao().registrarEntrega(
                UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                Map.of(fixture.item().getId(), new SolicitacaoMaterial.ItemEntregaInfo(
                        new BigDecimal("4.000"), null, null)), List.of());
        UUID idOperador = UUID.randomUUID();
        var itemDuplicado = new EstornoParcialRequest.ItemEstornoRequest(
                fixture.item().getId(), new BigDecimal("1.000"));
        EstornoParcialRequest request = new EstornoParcialRequest(List.of(itemDuplicado, itemDuplicado));
        when(solicitacaoRepository.findById(fixture.solicitacao().getId()))
                .thenReturn(Optional.of(fixture.solicitacao()));

        assertThatThrownBy(() -> service.registrarEstornoParcial(
                fixture.solicitacao().getId(), idOperador, request))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("mais de uma vez no estorno parcial");

        assertThat(fixture.item().getQuantidadeEntregue()).isEqualByComparingTo("4.000");
        verify(solicitacaoRepository, never()).save(any());
        verifyNoInteractions(saldoRepository, serieRepository, movimentacaoRepository, materialRepository, gravadorEvento);
    }

    @Test
    void estornoTotalMovimentaQuantidadeLiquidaEPreservaEntregaBruta() {
        Fixture fixture = fixtureAprovada();
        fixture.solicitacao().registrarEntrega(
                UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                Map.of(fixture.item().getId(), new SolicitacaoMaterial.ItemEntregaInfo(
                        new BigDecimal("10.000"), null, null)), List.of());
        Saldo saldoDestino = new Saldo(
                fixture.solicitacao().getEstoqueDestino(), fixture.item().getMaterial(), CondicaoMaterial.NOVO);
        saldoDestino.adicionarQuantidade(new BigDecimal("10.000"));
        when(solicitacaoRepository.findById(fixture.solicitacao().getId()))
                .thenReturn(Optional.of(fixture.solicitacao()));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                fixture.solicitacao().getEstoqueDestino().getId(), fixture.item().getMaterial().getId(),
                CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldoDestino));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                fixture.solicitacao().getEstoqueOrigem().getId(), fixture.item().getMaterial().getId(),
                CondicaoMaterial.NOVO)).thenReturn(Optional.empty());

        service.estornar(fixture.solicitacao().getId(), UUID.randomUUID());

        assertThat(fixture.item().getQuantidadeEntregueBruta()).isEqualByComparingTo("10.000");
        assertThat(fixture.item().getQuantidadeEntregaEstornada()).isEqualByComparingTo("10.000");
        assertThat(fixture.item().getQuantidadeEntregaLiquida()).isZero();
        assertThat(fixture.item().getQuantidadePendente()).isEqualByComparingTo("10.000");
        assertThat(fixture.solicitacao().getStatus())
                .isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ESTORNADO);
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getQuantidade()).isEqualByComparingTo("10.000");
        assertThat(movimento.getValue().getTipo()).isEqualTo(MovimentacaoEstoque.TipoMovimentacao.ESTORNO);
    }

    @Test
    void entregaNoBalcaoPodeSairDeOutroAlmoxarifado() {
        Fixture fixture = fixtureAprovada();
        SolicitacaoMaterial solicitacao = fixture.solicitacao();
        Material material = fixture.item().getMaterial();
        Estoque outroAlmox = comId(new Estoque("ALMOX-2", comId(new TipoEstoque("FISICO"))));
        Saldo saldoOutro = new Saldo(outroAlmox, material, CondicaoMaterial.NOVO);
        saldoOutro.adicionarQuantidade(BigDecimal.TEN);
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(estoqueRepository.findById(outroAlmox.getId())).thenReturn(Optional.of(outroAlmox));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                outroAlmox.getId(), material.getId(), CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldoOutro));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                solicitacao.getEstoqueDestino().getId(), material.getId(), CondicaoMaterial.NOVO))
                .thenReturn(Optional.empty());

        service.registrarEntrega(solicitacao.getId(), UUID.randomUUID(), new EntregaSolicitacaoRequest(
                "assinatura-almox", "assinatura-equipe",
                List.of(new EntregaSolicitacaoRequest.ItemEntregaRequest(
                        fixture.item().getId(), new BigDecimal("2.000"), null, null)),
                List.of(), List.of(), null, null, outroAlmox.getId()));

        assertThat(solicitacao.getEstoqueOrigem()).isSameAs(outroAlmox);
        verify(debitoSaldoService).debitar(outroAlmox, material, CondicaoMaterial.NOVO, new BigDecimal("2.000"));
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getEstoqueOrigem()).isSameAs(outroAlmox);
    }

    @Test
    void origemNaoMudaDepoisQueJaSaiuMaterial() {
        Fixture fixture = fixtureAprovada();
        SolicitacaoMaterial solicitacao = fixture.solicitacao();
        solicitacao.registrarEntrega(UUID.randomUUID(), "a", "b",
                Map.of(fixture.item().getId(), new SolicitacaoMaterial.ItemEntregaInfo(
                        new BigDecimal("4.000"), null, null)),
                List.of());
        Estoque outroAlmox = comId(new Estoque("ALMOX-2", comId(new TipoEstoque("FISICO"))));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(estoqueRepository.findById(outroAlmox.getId())).thenReturn(Optional.of(outroAlmox));

        assertThatThrownBy(() -> service.registrarEntrega(solicitacao.getId(), UUID.randomUUID(),
                new EntregaSolicitacaoRequest("a", "b",
                        List.of(new EntregaSolicitacaoRequest.ItemEntregaRequest(
                                fixture.item().getId(), BigDecimal.ONE, null, null)),
                        List.of(), List.of(), null, null, outroAlmox.getId())))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("antes da primeira entrega");
        verifyNoInteractions(debitoSaldoService);
    }

    private static Fixture fixtureAprovada() {
        TipoEstoque fisico = comId(new TipoEstoque("FISICO"));
        Estoque origem = comId(new Estoque("ORIGEM", fisico));
        Estoque destino = comId(new Estoque("DESTINO", fisico));
        Material material = comId(new Material(
                "MAT-1", "Material", "UN", "MATR", null, null, false, UUID.randomUUID()));
        SolicitacaoMaterial solicitacao = comId(
                new SolicitacaoMaterial(origem, destino, UUID.randomUUID()));
        solicitacao.adicionarItem(material, new BigDecimal("10.000"));
        solicitacao.aprovar(UUID.randomUUID());
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return new Fixture(solicitacao, item);
    }

    private record Fixture(SolicitacaoMaterial solicitacao, ItemSolicitacao item) {
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
