package com.nexo.app.modules.estoque.application.events;

import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.*;
import com.nexo.app.modules.estoque.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import com.nexo.app.shared.domain.events.ExecucaoAlteradaEvent;
import com.nexo.app.shared.domain.events.ExecucaoConcluidaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecucaoConcluidaListenerTest {

    @Mock
    private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock
    private EstoqueRepository estoqueRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SaldoRepository saldoRepository;
    @Mock
    private SaldoSerieRepository serieRepository;
    @Mock
    private DebitoSaldoService debitoSaldoService;

    @InjectMocks
    private ExecucaoConcluidaListener listener;

    private final UUID idEquipe = UUID.randomUUID();
    private final UUID idExecucao = UUID.randomUUID();
    private Estoque estoque;
    private Material cabo;
    private Material medidor;

    @BeforeEach
    void setUp() {
        estoque = comId(new Estoque("CAMPO EQ-01", comId(new TipoEstoque("CAMPO"))));
        cabo = comId(new Material("CABO-01", "Cabo de cobre", "M", "MATR", null, null, false, UUID.randomUUID()));
        medidor = comId(new Material("MED-01", "Medidor", "UN", "MATR", null, null, true, UUID.randomUUID()));

        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.of(estoque));
        lenient().when(materialRepository.findById(cabo.getId())).thenReturn(Optional.of(cabo));
        lenient().when(materialRepository.findById(medidor.getId())).thenReturn(Optional.of(medidor));
        lenient().when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Aplicar mais do que o saldo apenas debita: o saldo da equipe fica negativo, sem bloquear")
    void aplicacaoAcimaDoSaldoDebitaPermissivo() {
        listener.onExecucaoConcluida(evento(cabo, "5", OperacaoMaterial.APLICADO, null));

        verify(debitoSaldoService).debitar(estoque, cabo, CondicaoMaterial.NOVO, new BigDecimal("5"));
        verify(movimentacaoRepository).save(any(MovimentacaoEstoque.class));
    }

    @Test
    @DisplayName("Aplicar série do saldo debita e baixa a série")
    void serieDoSaldoDebitaEBaixaSerie() {
        SaldoSerie serie = new SaldoSerie(estoque, medidor, "SN-1", CondicaoMaterial.NOVO);
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                estoque.getId(), medidor.getId(), "SN-1", CondicaoMaterial.NOVO)).thenReturn(Optional.of(serie));

        listener.onExecucaoConcluida(evento(medidor, "1", OperacaoMaterial.APLICADO, "SN-1"));

        verify(debitoSaldoService).debitar(estoque, medidor, CondicaoMaterial.NOVO, BigDecimal.ONE);
        assertThat(serie.isAtivo()).isFalse();
    }

    @Test
    @DisplayName("Aplicar série fora do saldo também debita, sem falhar o processamento")
    void serieForaDoSaldoDebitaSemFalhar() {
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                estoque.getId(), medidor.getId(), "SN-99", CondicaoMaterial.NOVO)).thenReturn(Optional.empty());

        listener.onExecucaoConcluida(evento(medidor, "1", OperacaoMaterial.APLICADO, "SN-99"));

        verify(debitoSaldoService).debitar(estoque, medidor, CondicaoMaterial.NOVO, BigDecimal.ONE);
        verify(serieRepository, never()).save(any());
    }

    @Test
    @DisplayName("Retirada de campo entra como sucata no estoque da equipe e não gera pendência")
    void retiradaNaoGeraPendencia() {
        listener.onExecucaoConcluida(evento(cabo, "4", OperacaoMaterial.RETIRADO, null));

        verify(saldoRepository).save(any(Saldo.class));
        verify(debitoSaldoService, never()).debitar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Retirar série que já está ativa em outro estoque não falha nem duplica o saldo serial")
    void retiradaSerieEmConflitoNaoFalha() {
        when(serieRepository.findByNumSerieAndAtivoTrue("SN-1"))
                .thenReturn(Optional.of(new SaldoSerie(estoque, medidor, "SN-1", CondicaoMaterial.NOVO)));

        listener.onExecucaoConcluida(evento(medidor, "1", OperacaoMaterial.RETIRADO, "SN-1"));

        verify(serieRepository, never()).save(any());
    }

    @Test
    @DisplayName("Material que saiu da nota volta ao saldo NOVO da equipe com movimentação de estorno")
    void aplicacaoRemovidaDaNotaVoltaAoSaldo() {
        listener.onExecucaoAlterada(alteracao(List.of(item(cabo, "3", OperacaoMaterial.APLICADO, null)), List.of()));

        verify(saldoRepository).save(argThat(s -> s.getCondicao() == CondicaoMaterial.NOVO
                && s.getQuantidade().compareTo(new BigDecimal("3")) == 0));
        verify(movimentacaoRepository).save(argThat(m -> m.getTipo() == MovimentacaoEstoque.TipoMovimentacao.ESTORNO
                && m.getEstoqueDestino() == estoque && m.getIdReferenciaOperacao().equals(idExecucao)));
        verify(debitoSaldoService, never()).debitar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Série aplicada que saiu da nota volta ao caminhão como NOVO")
    void serieRemovidaDaNotaVoltaAoCaminhao() {
        when(serieRepository.findByNumSerieAndAtivoTrue("SN-7")).thenReturn(Optional.empty());

        listener.onExecucaoAlterada(alteracao(List.of(item(medidor, "1", OperacaoMaterial.APLICADO, "SN-7")), List.of()));

        verify(serieRepository).save(argThat(s -> s.getNumSerie().equals("SN-7") && s.getCondicao() == CondicaoMaterial.NOVO));
    }

    @Test
    @DisplayName("Retirada que saiu da nota tira a sucata do saldo da equipe")
    void retiradaRemovidaDaNotaSaiDaSucata() {
        listener.onExecucaoAlterada(alteracao(List.of(item(cabo, "2", OperacaoMaterial.RETIRADO, null)), List.of()));

        verify(debitoSaldoService).debitar(estoque, cabo, CondicaoMaterial.SUCATA, new BigDecimal("2"));
        verify(movimentacaoRepository).save(argThat(m -> m.getTipo() == MovimentacaoEstoque.TipoMovimentacao.ESTORNO
                && m.getEstoqueOrigem() == estoque && m.getCondicao() == CondicaoMaterial.SUCATA));
    }

    @Test
    @DisplayName("Material que entrou na nota é baixado como numa nota nova")
    void materialAdicionadoNaNotaEBaixado() {
        listener.onExecucaoAlterada(alteracao(List.of(), List.of(item(cabo, "5", OperacaoMaterial.APLICADO, null))));

        verify(debitoSaldoService).debitar(estoque, cabo, CondicaoMaterial.NOVO, new BigDecimal("5"));
        verify(movimentacaoRepository).save(argThat(m -> m.getTipo() == MovimentacaoEstoque.TipoMovimentacao.APLICACAO));
    }

    private ExecucaoAlteradaEvent alteracao(List<ExecucaoConcluidaEvent.ItemMaterialEvent> estornos,
                                            List<ExecucaoConcluidaEvent.ItemMaterialEvent> lancamentos) {
        return new ExecucaoAlteradaEvent(idExecucao, idEquipe, UUID.randomUUID(), estornos, lancamentos);
    }

    private static ExecucaoConcluidaEvent.ItemMaterialEvent item(Material material, String quantidade,
                                                                 OperacaoMaterial operacao, String serie) {
        return new ExecucaoConcluidaEvent.ItemMaterialEvent(material.getId(), new BigDecimal(quantidade), operacao, serie);
    }

    private ExecucaoConcluidaEvent evento(Material material, String quantidade, OperacaoMaterial operacao, String serie) {
        return new ExecucaoConcluidaEvent(idExecucao, idEquipe, UUID.randomUUID(), List.of(
                new ExecucaoConcluidaEvent.ItemMaterialEvent(material.getId(), new BigDecimal(quantidade), operacao, serie)));
    }

    private static <T> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
