package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.ReservaSerieSolicitacao;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSerieSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservaSerieSolicitacaoUseCaseTest {

    private final SolicitacaoMaterialRepository solicitacoes = mock(SolicitacaoMaterialRepository.class);
    private final SaldoSerieRepository series = mock(SaldoSerieRepository.class);
    private final ReservaSerieSolicitacaoRepository reservas = mock(ReservaSerieSolicitacaoRepository.class);
    private final ReservaSerieSolicitacaoUseCase useCase = new ReservaSerieSolicitacaoUseCase(solicitacoes, series, reservas);

    private final UUID idUsuario = UUID.randomUUID();
    private Estoque almoxarifado;
    private Material medidor;
    private SolicitacaoMaterial solicitacao;
    private ItemSolicitacao item;

    @BeforeEach
    void preparar() {
        almoxarifado = estoque("ALMOXARIFADO");
        medidor = mock(Material.class);
        when(medidor.getId()).thenReturn(UUID.randomUUID());
        when(medidor.getCodigo()).thenReturn("MED");
        when(medidor.isDetalhaSerie()).thenReturn(true);
        when(medidor.isAtivo()).thenReturn(true);
        solicitacao = solicitacao(almoxarifado);
        item = item(solicitacao, "3");
        when(reservas.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void reservaAsSeriesNovasDaOrigemEDevolveAsQueValem() {
        SaldoSerie a = serie("MD-1");
        SaldoSerie b = serie("MD-2");

        List<String> resultado = useCase.definir(solicitacao.getId(), item.getId(), List.of(" MD-2 ", "MD-1", ""), idUsuario);

        assertThat(resultado).containsExactly("MD-1", "MD-2");
        assertThat(a.getReservaVigente()).isPresent();
        assertThat(b.getReservaVigente().get().getItemSolicitacao()).isSameAs(item);
    }

    @Test
    void aNovaListaSubstituiAAnteriorELiberaOQueSaiu() {
        SaldoSerie a = serie("MD-1");
        serie("MD-2");
        useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-1", "MD-2"), idUsuario);

        List<String> resultado = useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-2"), idUsuario);

        assertThat(resultado).containsExactly("MD-2");
        assertThat(a.getReservaVigente()).isEmpty();
    }

    @Test
    void recusaSerieQueNaoEstaNaOrigem() {
        assertThatThrownBy(() -> useCase.definir(solicitacao.getId(), item.getId(), List.of("XX-9"), idUsuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("XX-9")
                .hasMessageContaining("ALMOXARIFADO");
    }

    @Test
    void recusaSerieReservadaParaOutraSolicitacao() {
        SaldoSerie a = serie("MD-1");
        SolicitacaoMaterial outra = solicitacao(almoxarifado);
        ItemSolicitacao itemOutra = item(outra, "1");
        useCase.definir(outra.getId(), itemOutra.getId(), List.of("MD-1"), idUsuario);

        assertThatThrownBy(() -> useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-1"), idUsuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("reservada para a solicitação #" + outra.getId().toString().substring(0, 8));
        assertThat(a.getReservaVigente().get().getItemSolicitacao()).isSameAs(itemOutra);
    }

    @Test
    void naoReservaMaisSeriesDoQueOItemTemAEntregar() {
        assertThatThrownBy(() -> useCase.definir(solicitacao.getId(), item.getId(), List.of("A", "B", "C", "D"), idUsuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("no máximo 3");
    }

    @Test
    void recusaSolicitacaoQueNaoAceitaEntrega() {
        when(solicitacao.aceitaReservaDeSerie()).thenReturn(false);

        assertThatThrownBy(() -> useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-1"), idUsuario))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void aSerieReservadaNaoSaiPorOutroCaminhoMasSaiNaEntregaDaPropriaSolicitacao() {
        SaldoSerie a = serie("MD-1");
        useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-1"), idUsuario);
        Estoque equipe = estoque("EQUIPE 01");

        assertThatThrownBy(() -> a.transferirPara(equipe))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("MD-1 está reservada");
        assertThatThrownBy(a::desativar).isInstanceOf(InvarianteVioladaException.class);

        a.cumprirReservaDa(solicitacao);
        a.transferirPara(equipe);
        assertThat(a.getEstoque()).isSameAs(equipe);
    }

    @Test
    void aReservaCaducaQuandoOItemNaoTemMaisOQueEntregar() {
        SaldoSerie a = serie("MD-1");
        useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-1"), idUsuario);

        when(item.getQuantidadePendente()).thenReturn(BigDecimal.ZERO);

        assertThat(a.getReservaVigente()).isEmpty();
        a.transferirPara(estoque("EQUIPE 01"));
    }

    @Test
    void aplicacaoEmCampoLiberaAReserva() {
        SaldoSerie a = serie("MD-1");
        useCase.definir(solicitacao.getId(), item.getId(), List.of("MD-1"), idUsuario);

        a.liberarReservas();
        a.desativar();

        assertThat(a.isAtivo()).isFalse();
    }

    private SaldoSerie serie(String numSerie) {
        SaldoSerie serie = new SaldoSerie(almoxarifado, medidor, numSerie, CondicaoMaterial.NOVO);
        UUID idOrigem = almoxarifado.getId();
        UUID idMaterial = medidor.getId();
        when(series.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                eq(idOrigem), eq(idMaterial), eq(numSerie), eq(CondicaoMaterial.NOVO)))
                .thenReturn(Optional.of(serie));
        return serie;
    }

    private static Estoque estoque(String nome) {
        Estoque e = mock(Estoque.class);
        when(e.getId()).thenReturn(UUID.randomUUID());
        when(e.getNomeIdentificador()).thenReturn(nome);
        when(e.isAtivo()).thenReturn(true);
        return e;
    }

    private SolicitacaoMaterial solicitacao(Estoque origem) {
        SolicitacaoMaterial s = mock(SolicitacaoMaterial.class);
        when(s.getId()).thenReturn(UUID.randomUUID());
        when(s.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(s.aceitaReservaDeSerie()).thenReturn(true);
        when(s.getEstoqueOrigem()).thenReturn(origem);
        when(s.getItens()).thenReturn(new ArrayList<>());
        when(solicitacoes.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private ItemSolicitacao item(SolicitacaoMaterial s, String pendente) {
        ItemSolicitacao i = mock(ItemSolicitacao.class);
        List<ReservaSerieSolicitacao> doItem = new ArrayList<>();
        when(i.getId()).thenReturn(UUID.randomUUID());
        when(i.isAtivo()).thenReturn(true);
        when(i.getSolicitacao()).thenReturn(s);
        when(i.getMaterial()).thenReturn(medidor);
        when(i.getQuantidadePendente()).thenReturn(new BigDecimal(pendente));
        when(i.getReservasSerie()).thenReturn(doItem);
        when(i.getSeriesReservadas()).thenAnswer(inv -> doItem.stream()
                .filter(ReservaSerieSolicitacao::isVigente)
                .map(r -> r.getSaldoSerie().getNumSerie())
                .sorted()
                .toList());
        s.getItens().add(i);
        return i;
    }
}
