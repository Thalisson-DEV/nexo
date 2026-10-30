package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorDetalheResponse;
import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorSolicitacaoResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.NotaResumida;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsumoAnteriorSolicitacaoUseCaseTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 16, 12, 0, 0, 0, ZoneOffset.UTC);

    private final SolicitacaoMaterialRepository solicitacoes = mock(SolicitacaoMaterialRepository.class);
    private final ItemSolicitacaoRepository itens = mock(ItemSolicitacaoRepository.class);
    private final MovimentacaoEstoqueRepository movimentacoes = mock(MovimentacaoEstoqueRepository.class);
    private final SaldoRepository saldos = mock(SaldoRepository.class);
    private final ExecucaoComercialApi execucoes = mock(ExecucaoComercialApi.class);
    private final ConsumoAnteriorSolicitacaoUseCase useCase = new ConsumoAnteriorSolicitacaoUseCase(
            solicitacoes, itens, movimentacoes, saldos, execucoes, Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));

    private final UUID idEstoque = UUID.randomUUID();
    private final UUID cabo = UUID.randomUUID();
    private final UUID medidor = UUID.randomUUID();
    private SolicitacaoMaterial atual;

    private Estoque estoque;

    @BeforeEach
    void preparar() {
        estoque = estoque(idEstoque, "EQUIPE 01");
        atual = solicitacao(UUID.randomUUID(), AGORA.minusDays(1), null);
        when(atual.getEstoqueDestino()).thenReturn(estoque);
        when(atual.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        ItemSolicitacao itemCabo = item(atual, cabo, "100", "0");
        ItemSolicitacao itemMedidor = item(atual, medidor, "5", "0");
        List<ItemSolicitacao> itensAtuais = new ArrayList<>(List.of(itemCabo, itemMedidor));
        when(atual.getItens()).thenReturn(itensAtuais);
        when(solicitacoes.findById(atual.getId())).thenReturn(Optional.of(atual));
        when(saldos.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), eq(CondicaoMaterial.NOVO))).thenReturn(Optional.empty());
    }

    @Test
    void somaOQueFoiAplicadoEDevolvidoDesdeAEntregaDaUltimaSolicitacao() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(20);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(22), entregaAnterior);
        SolicitacaoMaterial maisAntiga = solicitacao(UUID.randomUUID(), AGORA.minusDays(60), AGORA.minusDays(59));
        List<ItemSolicitacao> entregues = List.of(item(anterior, cabo, "60", "50"), item(maisAntiga, cabo, "80", "80"));
        UUID idAtual = atual.getId();
        OffsetDateTime dataAtual = atual.getDataSolicitacao();
        when(itens.findEntreguesAnteriores(eq(idEstoque), eq(idAtual), eq(dataAtual), anyCollection(), any())).thenReturn(entregues);
        UUID idAnterior = anterior.getId();
        when(movimentacoes.primeiraDataPorSolicitacaoEMaterial(anyCollection(), eq(TipoMovimentacao.ENTREGA)))
                .thenReturn(List.<Object[]>of(new Object[]{idAnterior, cabo, entregaAnterior}));
        when(movimentacoes.saidasDoEstoqueDesde(eq(idEstoque), anyCollection(), anyCollection(), eq(entregaAnterior)))
                .thenReturn(List.of(
                        new Object[]{cabo, TipoMovimentacao.APLICACAO, AGORA.minusDays(10), new BigDecimal("30")},
                        new Object[]{cabo, TipoMovimentacao.APLICACAO, AGORA.minusDays(3), new BigDecimal("12.5")},
                        new Object[]{cabo, TipoMovimentacao.DEVOLUCAO, AGORA.minusDays(2), new BigDecimal("4")}));
        Saldo saldo = mock(Saldo.class);
        when(saldo.isAtivo()).thenReturn(true);
        when(saldo.getQuantidade()).thenReturn(new BigDecimal("3.5"));
        when(saldos.findByEstoqueIdAndMaterialIdAndCondicao(idEstoque, cabo, CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldo));

        ConsumoAnteriorSolicitacaoResponse r = useCase.consultar(atual.getId());

        assertThat(r.consultadoAte()).isEqualTo(AGORA);
        ConsumoAnteriorSolicitacaoResponse.Item doCabo = r.itens().get(0);
        assertThat(doCabo.idMaterial()).isEqualTo(cabo);
        assertThat(doCabo.ultimaSolicitacao().id()).isEqualTo(anterior.getId());
        assertThat(doCabo.ultimaSolicitacao().dataEntrega()).isEqualTo(entregaAnterior);
        assertThat(doCabo.ultimaSolicitacao().quantidadeRecebida()).isEqualByComparingTo("50");
        assertThat(doCabo.quantidadeAplicada()).isEqualByComparingTo("42.5");
        assertThat(doCabo.quantidadeDevolvida()).isEqualByComparingTo("4");
        assertThat(doCabo.saldoAtual()).isEqualByComparingTo("3.5");
        assertThat(doCabo.saldoAntes()).isEqualByComparingTo("3.5");

        ConsumoAnteriorSolicitacaoResponse.Item doMedidor = r.itens().get(1);
        assertThat(doMedidor.ultimaSolicitacao()).isNull();
        assertThat(doMedidor.quantidadeAplicada()).isNull();
        assertThat(doMedidor.saldoAtual()).isEqualByComparingTo("0");
    }

    @Test
    void materialQueSaiuDeNotaAlteradaNaoContaComoAplicado() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(20);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(22), entregaAnterior);
        List<ItemSolicitacao> entregues = List.of(item(anterior, cabo, "60", "50"));
        when(itens.findEntreguesAnteriores(any(), any(), any(), anyCollection(), any())).thenReturn(entregues);
        UUID idAnterior = anterior.getId();
        when(movimentacoes.primeiraDataPorSolicitacaoEMaterial(anyCollection(), eq(TipoMovimentacao.ENTREGA)))
                .thenReturn(List.<Object[]>of(new Object[]{idAnterior, cabo, entregaAnterior}));
        when(movimentacoes.saidasDoEstoqueDesde(any(), anyCollection(), anyCollection(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{cabo, TipoMovimentacao.APLICACAO, AGORA.minusDays(10), new BigDecimal("30")}));
        when(movimentacoes.estornosDeNotaNoEstoqueDesde(any(), anyCollection(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{cabo, AGORA.minusDays(5), new BigDecimal("3")}));

        ConsumoAnteriorSolicitacaoResponse r = useCase.consultar(atual.getId());

        assertThat(r.itens().get(0).quantidadeAplicada()).isEqualByComparingTo("27");
    }

    @Test
    void aJanelaTerminaNaPrimeiraEntregaDaSolicitacaoAtual() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(20);
        OffsetDateTime entregaAtual = AGORA.minusHours(5);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(22), entregaAnterior);
        List<ItemSolicitacao> entregues = List.of(item(anterior, cabo, "60", "60"));
        when(itens.findEntreguesAnteriores(any(), any(), any(), anyCollection(), any())).thenReturn(entregues);
        UUID idAnterior = anterior.getId();
        UUID idAtual = atual.getId();
        when(movimentacoes.primeiraDataPorSolicitacaoEMaterial(anyCollection(), eq(TipoMovimentacao.ENTREGA)))
                .thenReturn(List.of(
                        new Object[]{idAnterior, cabo, entregaAnterior},
                        new Object[]{idAtual, cabo, entregaAtual}));
        when(movimentacoes.saidasDoEstoqueDesde(any(), anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(
                        new Object[]{cabo, TipoMovimentacao.APLICACAO, AGORA.minusDays(1), new BigDecimal("10")},
                        new Object[]{cabo, TipoMovimentacao.APLICACAO, AGORA.minusHours(1), new BigDecimal("99")}));

        ConsumoAnteriorSolicitacaoResponse r = useCase.consultar(idAtual);

        assertThat(r.consultadoAte()).isEqualTo(entregaAtual);
        assertThat(r.itens().get(0).quantidadeAplicada()).isEqualByComparingTo("10");
    }

    @Test
    void recusaSolicitacaoTecnica() {
        when(atual.getContexto()).thenReturn(ContextoSolicitacaoMaterial.TECNICO);

        assertThatThrownBy(() -> useCase.consultar(atual.getId())).isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void detalhaAsMovimentacoesDoPeriodoEFechaAContaDoSaldo() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(20);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(22), entregaAnterior);
        when(anterior.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        prepararAnterior(anterior, entregaAnterior, null);
        Estoque almoxarifado = estoque(UUID.randomUUID(), "ALMOXARIFADO");
        Estoque outraEquipe = estoque(UUID.randomUUID(), "EQUIPE 02");
        UUID idNota = UUID.randomUUID();
        MovimentacaoEstoque entrega = mov(TipoMovimentacao.ENTREGA, almoxarifado, estoque, "50", CondicaoMaterial.NOVO, entregaAnterior, null);
        when(entrega.getSolicitacao()).thenReturn(anterior);
        MovimentacaoEstoque aplicacao = mov(TipoMovimentacao.APLICACAO, estoque, null, "30", CondicaoMaterial.NOVO, AGORA.minusDays(10), idNota);
        MovimentacaoEstoque recebida = mov(TipoMovimentacao.TRANSFERENCIA, outraEquipe, estoque, "5", CondicaoMaterial.NOVO, AGORA.minusDays(8), UUID.randomUUID());
        MovimentacaoEstoque devolucao = mov(TipoMovimentacao.DEVOLUCAO, estoque, almoxarifado, "4", CondicaoMaterial.NOVO, AGORA.minusDays(5), UUID.randomUUID());
        MovimentacaoEstoque retirada = mov(TipoMovimentacao.RETIRADA, null, estoque, "1", CondicaoMaterial.SUCATA, AGORA.minusDays(4), idNota);
        MovimentacaoEstoque enviada = mov(TipoMovimentacao.TRANSFERENCIA, estoque, outraEquipe, "7.5", CondicaoMaterial.NOVO, AGORA.minusDays(2), UUID.randomUUID());
        when(movimentacoes.doMaterialNoEstoqueDesde(idEstoque, cabo, entregaAnterior))
                .thenReturn(List.of(entrega, aplicacao, recebida, devolucao, retirada, enviada));
        when(execucoes.resumirNotas(anyCollection()))
                .thenReturn(List.of(new NotaResumida(idNota, "NS-123", "CL · LIGAÇÃO NOVA", AGORA.minusDays(10))));
        saldoNovo(cabo, "13.5");

        ConsumoAnteriorDetalheResponse r = useCase.detalhar(atual.getId(), cabo);

        assertThat(r.inicio()).isEqualTo(entregaAnterior);
        assertThat(r.fim()).isEqualTo(AGORA);
        // 13.5 hoje = saldo antes + 50 − 30 + 5 − 4 − 7.5 (a sucata não entra na conta do saldo novo)
        assertThat(r.saldoInicial()).isEqualByComparingTo("0");
        assertThat(r.saldoFinal()).isEqualByComparingTo("13.5");
        assertThat(r.movimentacoes()).extracting(m -> m.quantidade().toPlainString())
                .containsExactly("50", "-30", "5", "-4", "1", "-7.5");
        ConsumoAnteriorDetalheResponse.Movimentacao doEntrega = r.movimentacoes().get(0);
        assertThat(doEntrega.solicitacao().id()).isEqualTo(anterior.getId());
        assertThat(doEntrega.contraparte().nomeIdentificador()).isEqualTo("ALMOXARIFADO");
        ConsumoAnteriorDetalheResponse.Movimentacao naNota = r.movimentacoes().get(1);
        assertThat(naNota.nota().referencia()).isEqualTo("NS-123");
        assertThat(naNota.nota().tipoServico()).isEqualTo("CL · LIGAÇÃO NOVA");
        assertThat(naNota.contraparte()).isNull();
        assertThat(r.movimentacoes().get(5).contraparte().nomeIdentificador()).isEqualTo("EQUIPE 02");
    }

    @Test
    void oDetalheParaNaEntregaDaSolicitacaoAtualEOSaldoFinalDesfazOQueVeioDepois() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(20);
        OffsetDateTime entregaAtual = AGORA.minusDays(1);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(22), entregaAnterior);
        prepararAnterior(anterior, entregaAnterior, entregaAtual);
        Estoque almoxarifado = estoque(UUID.randomUUID(), "ALMOXARIFADO");
        MovimentacaoEstoque entrega = mov(TipoMovimentacao.ENTREGA, almoxarifado, estoque, "50", CondicaoMaterial.NOVO, entregaAnterior, null);
        MovimentacaoEstoque aplicacao = mov(TipoMovimentacao.APLICACAO, estoque, null, "45", CondicaoMaterial.NOVO, AGORA.minusDays(3), null);
        MovimentacaoEstoque novaEntrega = mov(TipoMovimentacao.ENTREGA, almoxarifado, estoque, "100", CondicaoMaterial.NOVO, entregaAtual, null);
        when(movimentacoes.doMaterialNoEstoqueDesde(idEstoque, cabo, entregaAnterior)).thenReturn(List.of(entrega, aplicacao, novaEntrega));
        saldoNovo(cabo, "105");

        ConsumoAnteriorDetalheResponse r = useCase.detalhar(atual.getId(), cabo);

        assertThat(r.fim()).isEqualTo(entregaAtual);
        assertThat(r.movimentacoes()).hasSize(2);
        assertThat(r.saldoInicial()).isEqualByComparingTo("0");
        assertThat(r.saldoFinal()).isEqualByComparingTo("5");
        assertThat(r.saldoAtual()).isEqualByComparingTo("105");
    }

    @Test
    void semSolicitacaoAnteriorODetalheExplicaOSaldoPelosUltimosDias() {
        when(itens.findEntreguesAnteriores(any(), any(), any(), anyCollection(), any())).thenReturn(List.of());
        when(movimentacoes.primeiraDataPorSolicitacaoEMaterial(anyCollection(), eq(TipoMovimentacao.ENTREGA))).thenReturn(List.of());
        Estoque outraEquipe = estoque(UUID.randomUUID(), "EQUIPE 02");
        MovimentacaoEstoque recebida = mov(TipoMovimentacao.TRANSFERENCIA, outraEquipe, estoque, "15", CondicaoMaterial.NOVO, AGORA.minusDays(6), UUID.randomUUID());
        MovimentacaoEstoque aplicacao = mov(TipoMovimentacao.APLICACAO, estoque, null, "3", CondicaoMaterial.NOVO, AGORA.minusDays(2), null);
        when(movimentacoes.doMaterialNoEstoqueDesde(idEstoque, medidor, AGORA.minusDays(90))).thenReturn(List.of(recebida, aplicacao));
        saldoNovo(medidor, "12");

        ConsumoAnteriorDetalheResponse r = useCase.detalhar(atual.getId(), medidor);

        assertThat(r.semSolicitacaoAnterior()).isTrue();
        assertThat(r.inicio()).isEqualTo(AGORA.minusDays(90));
        assertThat(r.saldoInicial()).isEqualByComparingTo("0");
        assertThat(r.saldoFinal()).isEqualByComparingTo("12");
        assertThat(r.movimentacoes()).extracting(m -> m.quantidade().toPlainString()).containsExactly("15", "-3");
    }

    @Test
    void materialComSerieDesmembradoSomaTodasAsLinhasDaUltimaSolicitacao() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(3);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(4), entregaAnterior);
        SolicitacaoMaterial maisAntiga = solicitacao(UUID.randomUUID(), AGORA.minusDays(30), AGORA.minusDays(29));
        List<ItemSolicitacao> entregues = new ArrayList<>();
        for (int i = 0; i < 18; i++) entregues.add(item(anterior, medidor, "1", "1"));
        entregues.add(item(maisAntiga, medidor, "40", "40"));
        when(itens.findEntreguesAnteriores(any(), any(), any(), anyCollection(), any())).thenReturn(entregues);
        UUID idAnterior = anterior.getId();
        when(movimentacoes.primeiraDataPorSolicitacaoEMaterial(anyCollection(), eq(TipoMovimentacao.ENTREGA)))
                .thenReturn(List.<Object[]>of(new Object[]{idAnterior, medidor, entregaAnterior}));
        when(movimentacoes.saidasDoEstoqueDesde(any(), anyCollection(), anyCollection(), any())).thenReturn(List.of());

        ConsumoAnteriorSolicitacaoResponse r = useCase.consultar(atual.getId());

        ConsumoAnteriorSolicitacaoResponse.UltimaSolicitacao ultima = r.itens().get(1).ultimaSolicitacao();
        assertThat(ultima.id()).isEqualTo(idAnterior);
        assertThat(ultima.quantidadeSolicitada()).isEqualByComparingTo("18");
        assertThat(ultima.quantidadeRecebida()).isEqualByComparingTo("18");
    }

    @Test
    void oResumoTrazOSaldoDeAntesDaUltimaEntregaParaFecharAConta() {
        OffsetDateTime entregaAnterior = AGORA.minusDays(3);
        SolicitacaoMaterial anterior = solicitacao(UUID.randomUUID(), AGORA.minusDays(4), entregaAnterior);
        prepararAnterior(anterior, entregaAnterior, null);
        when(movimentacoes.saidasDoEstoqueDesde(any(), anyCollection(), anyCollection(), any())).thenReturn(List.of());
        Estoque almoxarifado = estoque(UUID.randomUUID(), "ALMOXARIFADO");
        MovimentacaoEstoque entrega = mov(TipoMovimentacao.ENTREGA, almoxarifado, estoque, "50", CondicaoMaterial.NOVO, entregaAnterior, null);
        MovimentacaoEstoque aplicacao = mov(TipoMovimentacao.APLICACAO, estoque, null, "45", CondicaoMaterial.NOVO, AGORA.minusDays(1), null);
        when(movimentacoes.doMaterialNoEstoqueDesde(idEstoque, cabo, entregaAnterior)).thenReturn(List.of(entrega, aplicacao));
        saldoNovo(cabo, "7");

        ConsumoAnteriorSolicitacaoResponse r = useCase.consultar(atual.getId());

        // 7 hoje = 2 antes + 50 recebidos − 45 aplicados
        assertThat(r.itens().get(0).saldoAntes()).isEqualByComparingTo("2");
        assertThat(r.itens().get(1).saldoAntes()).isNull();
    }

    @Test
    void recusaDetalharMaterialForaDaSolicitacao() {
        assertThatThrownBy(() -> useCase.detalhar(atual.getId(), UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private void prepararAnterior(SolicitacaoMaterial anterior, OffsetDateTime entregaAnterior, OffsetDateTime entregaAtual) {
        List<ItemSolicitacao> entregues = List.of(item(anterior, cabo, "60", "50"));
        when(itens.findEntreguesAnteriores(any(), any(), any(), anyCollection(), any())).thenReturn(entregues);
        List<Object[]> primeiras = new ArrayList<>();
        primeiras.add(new Object[]{anterior.getId(), cabo, entregaAnterior});
        if (entregaAtual != null) primeiras.add(new Object[]{atual.getId(), cabo, entregaAtual});
        when(movimentacoes.primeiraDataPorSolicitacaoEMaterial(anyCollection(), eq(TipoMovimentacao.ENTREGA))).thenReturn(primeiras);
    }

    private void saldoNovo(UUID idMaterial, String quantidade) {
        Saldo saldo = mock(Saldo.class);
        when(saldo.isAtivo()).thenReturn(true);
        when(saldo.getQuantidade()).thenReturn(new BigDecimal(quantidade));
        when(saldos.findByEstoqueIdAndMaterialIdAndCondicao(idEstoque, idMaterial, CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldo));
    }

    private static Estoque estoque(UUID id, String nome) {
        Estoque e = mock(Estoque.class);
        when(e.getId()).thenReturn(id);
        when(e.getNomeIdentificador()).thenReturn(nome);
        return e;
    }

    private static MovimentacaoEstoque mov(TipoMovimentacao tipo, Estoque origem, Estoque destino, String quantidade,
                                           CondicaoMaterial condicao, OffsetDateTime quando, UUID idReferencia) {
        MovimentacaoEstoque m = mock(MovimentacaoEstoque.class);
        when(m.getId()).thenReturn(UUID.randomUUID());
        when(m.getTipo()).thenReturn(tipo);
        when(m.getEstoqueOrigem()).thenReturn(origem);
        when(m.getEstoqueDestino()).thenReturn(destino);
        when(m.getQuantidade()).thenReturn(new BigDecimal(quantidade));
        when(m.getCondicao()).thenReturn(condicao);
        when(m.getDataHora()).thenReturn(quando);
        when(m.getIdReferenciaOperacao()).thenReturn(idReferencia);
        return m;
    }

    private static SolicitacaoMaterial solicitacao(UUID id, OffsetDateTime data, OffsetDateTime entrega) {
        SolicitacaoMaterial s = mock(SolicitacaoMaterial.class);
        when(s.getId()).thenReturn(id);
        when(s.getDataSolicitacao()).thenReturn(data);
        when(s.getDataEntrega()).thenReturn(entrega);
        return s;
    }

    private static ItemSolicitacao item(SolicitacaoMaterial solicitacao, UUID idMaterial, String pedida, String recebida) {
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(idMaterial);
        ItemSolicitacao item = mock(ItemSolicitacao.class);
        when(item.getMaterial()).thenReturn(material);
        when(item.getSolicitacao()).thenReturn(solicitacao);
        when(item.isAtivo()).thenReturn(true);
        when(item.getQuantidade()).thenReturn(new BigDecimal(pedida));
        when(item.getQuantidadeEntregaLiquida()).thenReturn(new BigDecimal(recebida));
        return item;
    }
}
