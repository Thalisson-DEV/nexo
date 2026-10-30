package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.EstoqueRequest;
import com.nexo.app.modules.estoque.application.mappers.EstoqueMapper;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueObraProtecaoUseCasesTest {

    @Mock private EstoqueRepository estoqueRepository;
    @Mock private TipoEstoqueRepository tipoEstoqueRepository;
    @Mock private SaldoRepository saldoRepository;
    @Mock private SaldoSerieRepository saldoSerieRepository;
    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private EstoqueMapper mapper;

    private EstoqueUseCases useCases;

    @BeforeEach
    void configurar() {
        useCases = new EstoqueUseCases(
                estoqueRepository, tipoEstoqueRepository, saldoRepository,
                saldoSerieRepository, solicitacaoRepository, mapper);
    }

    @Test
    void impedeSubstituirTipoDoEstoquePatrimonialDaObra() {
        UUID idEstoque = UUID.randomUUID();
        UUID idTipoFisico = UUID.randomUUID();
        Estoque estoqueObra = estoqueObra();
        when(estoqueRepository.findById(idEstoque)).thenReturn(Optional.of(estoqueObra));
        when(tipoEstoqueRepository.findById(idTipoFisico)).thenReturn(Optional.of(new TipoEstoque("FISICO")));

        assertThatThrownBy(() -> useCases.atualizar(idEstoque,
                new EstoqueRequest("OBRA PROTEGIDA", idTipoFisico, UUID.randomUUID(), null)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("tipo substituido");

        verify(estoqueRepository, never()).save(any());
    }

    @Test
    void impedeDesativarEstoqueDeObraComSaldoPositivo() {
        UUID idEstoque = UUID.randomUUID();
        Estoque estoqueObra = estoqueObra();
        when(estoqueRepository.findById(idEstoque)).thenReturn(Optional.of(estoqueObra));
        when(saldoRepository.existsSaldoPositivoByEstoqueId(idEstoque)).thenReturn(true);

        assertThatThrownBy(() -> useCases.desativar(idEstoque))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("nao pode ser desativado");

        verify(estoqueRepository, never()).save(any());
    }

    @Test
    void impedeDesativarEstoqueDeObraComSerieAtiva() {
        UUID idEstoque = UUID.randomUUID();
        Estoque estoqueObra = estoqueObra();
        when(estoqueRepository.findById(idEstoque)).thenReturn(Optional.of(estoqueObra));
        when(saldoSerieRepository.existsByEstoqueIdAndAtivoTrue(idEstoque)).thenReturn(true);

        assertThatThrownBy(() -> useCases.desativar(idEstoque))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("nao pode ser desativado");
    }

    @Test
    void impedeDesativarEstoqueDeObraComSolicitacaoTecnicaAberta() {
        UUID idEstoque = UUID.randomUUID();
        Estoque estoqueObra = estoqueObra();
        when(estoqueRepository.findById(idEstoque)).thenReturn(Optional.of(estoqueObra));
        when(solicitacaoRepository.existsByIdObraAndContextoAndEncerradaEmIsNullAndStatusIn(
                eq(estoqueObra.getIdObra()), eq(ContextoSolicitacaoMaterial.TECNICO), any(Collection.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> useCases.desativar(idEstoque))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("nao pode ser desativado");
    }

    @Test
    void permiteDesativarEstoqueDeObraSemCustodiaNemSolicitacaoAberta() {
        UUID idEstoque = UUID.randomUUID();
        Estoque estoqueObra = estoqueObra();
        when(estoqueRepository.findById(idEstoque)).thenReturn(Optional.of(estoqueObra));

        assertThatCode(() -> useCases.desativar(idEstoque)).doesNotThrowAnyException();

        verify(solicitacaoRepository).existsByIdObraAndContextoAndEncerradaEmIsNullAndStatusIn(
                eq(estoqueObra.getIdObra()), eq(ContextoSolicitacaoMaterial.TECNICO), any(Collection.class));
        verify(estoqueRepository).save(estoqueObra);
    }

    private static Estoque estoqueObra() {
        Estoque estoque = new Estoque("OBRA PROTEGIDA", new TipoEstoque("OBRA"));
        estoque.vincularObra(UUID.randomUUID());
        return estoque;
    }
}
