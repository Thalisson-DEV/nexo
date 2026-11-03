package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaldoObraUseCasesTest {

    @Mock private EstoqueRepository estoqueRepository;
    @Mock private SaldoRepository saldoRepository;
    @Mock private SaldoSerieRepository saldoSerieRepository;
    @Mock private ObraApi obraApi;
    @InjectMocks private SaldoObraUseCases useCases;

    private final UUID idObra = UUID.randomUUID();

    @Test
    void trazCadaMaterialPorCondicaoComAsSeriesDaquelaCondicao() {
        UUID idEstoque = UUID.randomUUID();
        Estoque estoque = mock(Estoque.class);
        when(estoque.getId()).thenReturn(idEstoque);
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.of(mock(ObraApi.ObraPublica.class)));
        when(estoqueRepository.findByIdObra(idObra)).thenReturn(Optional.of(estoque));
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(UUID.randomUUID());
        when(material.getCodigo()).thenReturn("MAT-1");
        Saldo novo = saldo(material, CondicaoMaterial.NOVO, "2");
        Saldo sucata = saldo(material, CondicaoMaterial.SUCATA, "-1");
        when(saldoRepository.findDiferentesDeZeroByEstoqueId(idEstoque)).thenReturn(List.of(novo, sucata));
        SaldoSerie b = serie(material, CondicaoMaterial.NOVO, "S-2");
        SaldoSerie a = serie(material, CondicaoMaterial.NOVO, "S-1");
        when(saldoSerieRepository.findByEstoqueIdAndAtivoTrue(idEstoque)).thenReturn(List.of(b, a));

        var resposta = useCases.buscar(idObra);

        assertThat(resposta.idEstoque()).isEqualTo(idEstoque);
        assertThat(resposta.itens()).hasSize(2);
        assertThat(resposta.itens().get(0).codigo()).isEqualTo("MAT-1");
        assertThat(resposta.itens().get(0).series()).containsExactly("S-1", "S-2");
        assertThat(resposta.itens().get(1).quantidade()).isEqualByComparingTo("-1");
        assertThat(resposta.itens().get(1).series()).isEmpty();
    }

    @Test
    void obraSemEstoqueNaoTemSaldo() {
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.of(mock(ObraApi.ObraPublica.class)));
        when(estoqueRepository.findByIdObra(idObra)).thenReturn(Optional.empty());

        var resposta = useCases.buscar(idObra);

        assertThat(resposta.idEstoque()).isNull();
        assertThat(resposta.itens()).isEmpty();
    }

    @Test
    void obraInexistenteDaErro() {
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.buscar(idObra)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Saldo saldo(Material material, CondicaoMaterial condicao, String quantidade) {
        Saldo saldo = mock(Saldo.class);
        when(saldo.getMaterial()).thenReturn(material);
        when(saldo.getCondicao()).thenReturn(condicao);
        when(saldo.getQuantidade()).thenReturn(new BigDecimal(quantidade));
        return saldo;
    }

    private SaldoSerie serie(Material material, CondicaoMaterial condicao, String numSerie) {
        SaldoSerie serie = mock(SaldoSerie.class);
        when(serie.getMaterial()).thenReturn(material);
        when(serie.getCondicao()).thenReturn(condicao);
        when(serie.getNumSerie()).thenReturn(numSerie);
        return serie;
    }
}
