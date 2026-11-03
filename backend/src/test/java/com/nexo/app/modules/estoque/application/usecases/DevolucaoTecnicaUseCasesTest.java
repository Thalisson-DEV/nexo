package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.DevolucaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevolucaoTecnicaUseCasesTest {

    @Mock private MovimentacaoEstoqueRepository repository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private SaldoRepository saldoRepository;
    @Mock private SaldoSerieRepository serieRepository;
    @Mock private DebitoSaldoService debitoSaldoService;
    @Mock private ReferenciaTecnicaValidator referenciaValidator;
    @Mock private EstoqueObraProvisioningService estoqueObraService;

    @InjectMocks private DevolucaoTecnicaUseCases useCases;

    private final UUID idObra = UUID.randomUUID();
    private final UUID idEquipe = UUID.randomUUID();
    private final UUID idDestino = UUID.randomUUID();
    private final UUID idMaterial = UUID.randomUUID();
    private final UUID idOperador = UUID.randomUUID();
    private final UUID idEstoqueObra = UUID.randomUUID();

    private Estoque estoqueObra;
    private Estoque destino;
    private Material material;

    @BeforeEach
    void preparar() {
        estoqueObra = mock(Estoque.class);
        when(estoqueObra.getId()).thenReturn(idEstoqueObra);
        when(estoqueObra.isAtivo()).thenReturn(true);

        TipoEstoque fisico = mock(TipoEstoque.class);
        when(fisico.getNome()).thenReturn("FISICO");
        destino = mock(Estoque.class);
        when(destino.getId()).thenReturn(idDestino);
        when(destino.isAtivo()).thenReturn(true);
        when(destino.getTipoEstoque()).thenReturn(fisico);

        material = mock(Material.class);
        when(material.getId()).thenReturn(idMaterial);
        when(material.getCodigo()).thenReturn("MAT-1");
        when(material.isAtivo()).thenReturn(true);

        ObraApi.ObraPublica obra = mock(ObraApi.ObraPublica.class);
        when(obra.id()).thenReturn(idObra);
        when(obra.tipoObra()).thenReturn(ObraApi.TipoObraPublico.OBRA);
        ReferenciaTecnicaValidator.ContextoTecnicoValidado contexto = mock(ReferenciaTecnicaValidator.ContextoTecnicoValidado.class);
        when(contexto.obra()).thenReturn(obra);
        when(referenciaValidator.validar(idObra, null, idEquipe, null)).thenReturn(contexto);
        when(estoqueObraService.resolverOuProvisionar(any())).thenReturn(estoqueObra);
        when(estoqueRepository.findById(idDestino)).thenReturn(Optional.of(destino));
        when(materialRepository.findById(idMaterial)).thenReturn(Optional.of(material));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any())).thenReturn(Optional.empty());
    }

    private DevolucaoTecnicaRequest pedido(TipoSolicitacaoTecnica tipo, String quantidade, String serie, Boolean guardar) {
        return new DevolucaoTecnicaRequest(idObra, null, idEquipe, idDestino, List.of(
                new DevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(
                        idMaterial, tipo, new BigDecimal(quantidade), CondicaoMaterial.NOVO, serie, guardar)));
    }

    private void saldoNaObra(String quantidade) {
        Saldo saldo = new Saldo(estoqueObra, material, CondicaoMaterial.NOVO);
        saldo.adicionarQuantidade(new BigDecimal(quantidade));
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(idEstoqueObra, idMaterial, CondicaoMaterial.NOVO))
                .thenReturn(Optional.of(saldo));
    }

    private List<MovimentacaoEstoque> movimentos(int quantos) {
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(repository, times(quantos)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void comSaldoNaObra_soDebitaEGravaODevolucaoComOTipoEscolhido() {
        saldoNaObra("10");

        useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, "4", null, null), idOperador);

        verify(debitoSaldoService).debitar(estoqueObra, material, CondicaoMaterial.NOVO, new BigDecimal("4"));
        verify(saldoRepository, never()).saveAndFlush(any());
        MovimentacaoEstoque devolucao = movimentos(1).get(0);
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.DEVOLUCAO, devolucao.getTipo());
        assertEquals(TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, devolucao.getTipoTecnico());
        assertEquals(idEquipe, devolucao.getIdEquipe());
        assertEquals(idObra, devolucao.getIdObra());
    }

    @Test
    void foraDoEstoqueDaObra_lancaEntradaDoQueFaltaAntesDeDevolver() {
        saldoNaObra("1");

        useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "4", null, true), idOperador);

        ArgumentCaptor<Saldo> saldo = ArgumentCaptor.forClass(Saldo.class);
        verify(saldoRepository).saveAndFlush(saldo.capture());
        assertEquals(0, new BigDecimal("4").compareTo(saldo.getValue().getQuantidade()));
        verify(debitoSaldoService).debitar(estoqueObra, material, CondicaoMaterial.NOVO, new BigDecimal("4"));

        List<MovimentacaoEstoque> movimentos = movimentos(2);
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.ENTRADA_AVULSA, movimentos.get(0).getTipo());
        assertEquals(0, new BigDecimal("3").compareTo(movimentos.get(0).getQuantidade()));
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.DEVOLUCAO, movimentos.get(1).getTipo());
        assertEquals(0, new BigDecimal("4").compareTo(movimentos.get(1).getQuantidade()));
    }

    @Test
    void materialSemNenhumSaldo_entraInteiroComoEntrada() {
        useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "2", null, true), idOperador);

        assertEquals(0, new BigDecimal("2").compareTo(movimentos(2).get(0).getQuantidade()));
    }

    @Test
    void naoGuardar_baixaNoDestinoSemSomarSaldo() {
        saldoNaObra("5");

        useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "5", null, false), idOperador);

        verify(saldoRepository, never()).save(any(Saldo.class));
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.BAIXA_AVULSA, movimentos(2).get(1).getTipo());
    }

    @Test
    void recusaTipoDeAdicao() {
        assertThrows(InvarianteVioladaException.class,
                () -> useCases.devolver(pedido(TipoSolicitacaoTecnica.ADICAO_COMUM, "1", null, true), idOperador));
        verify(debitoSaldoService, never()).debitar(any(), any(), any(), any());
    }

    @Test
    void recusaDestinoQueNaoEFisico() {
        TipoEstoque campo = mock(TipoEstoque.class);
        when(campo.getNome()).thenReturn("CAMPO");
        when(destino.getTipoEstoque()).thenReturn(campo);

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "1", null, true), idOperador));
    }

    @Test
    void serieQueNaoEstavaNaObraEhCriadaEVaiParaODestino() {
        when(material.isDetalhaSerie()).thenReturn(true);
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                eq(idEstoqueObra), eq(idMaterial), eq("S-1"), eq(CondicaoMaterial.NOVO))).thenReturn(Optional.empty());
        when(serieRepository.findAllByNumSerieInAndAtivoTrue(List.of("S-1"))).thenReturn(List.of());

        useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "1", " S-1 ", true), idOperador);

        ArgumentCaptor<SaldoSerie> serie = ArgumentCaptor.forClass(SaldoSerie.class);
        verify(serieRepository).save(serie.capture());
        assertEquals(destino, serie.getValue().getEstoque());
        assertEquals("S-1", serie.getValue().getNumSerie());
    }

    @Test
    void serieAtivaEmOutroEstoqueNaoPodeSerDevolvida() {
        when(material.isDetalhaSerie()).thenReturn(true);
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        SaldoSerie outra = mock(SaldoSerie.class);
        when(outra.getMaterial()).thenReturn(material);
        when(serieRepository.findAllByNumSerieInAndAtivoTrue(List.of("S-1"))).thenReturn(List.of(outra));

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "1", "S-1", true), idOperador));
    }

    @Test
    void materialComSerieExigeQuantidadeUm() {
        when(material.isDetalhaSerie()).thenReturn(true);

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.devolver(pedido(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, "2", "S-1", true), idOperador));
    }
}
