package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueCampoEquipeProvisioningServiceTest {

    @Mock private EstoqueRepository estoqueRepository;
    @Mock private TipoEstoqueRepository tipoEstoqueRepository;

    @Test
    void criaEstoqueCampoComCodigoBaseEEquipe() {
        UUID idEquipe = UUID.randomUUID();
        UUID idBase = UUID.randomUUID();
        TipoEstoque campo = new TipoEstoque("CAMPO");
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.empty());
        when(tipoEstoqueRepository.findByNomeIgnoreCase("CAMPO")).thenReturn(Optional.of(campo));
        when(estoqueRepository.saveAndFlush(any(Estoque.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().provisionar(idEquipe, "eq-01", idBase);

        ArgumentCaptor<Estoque> captor = ArgumentCaptor.forClass(Estoque.class);
        verify(estoqueRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNomeIdentificador()).isEqualTo("EQ-01");
        assertThat(captor.getValue().getIdEquipe()).isEqualTo(idEquipe);
        assertThat(captor.getValue().getIdBaseOperacional()).isEqualTo(idBase);
        assertThat(captor.getValue().isTipoCampo()).isTrue();
    }

    @Test
    void reutilizaEstoqueCampoExistenteSemDuplicar() {
        UUID idEquipe = UUID.randomUUID();
        Estoque existente = new Estoque("EQ-01", new TipoEstoque("CAMPO"));
        existente.vincularEquipe(idEquipe);
        existente.vincularBaseOperacional(UUID.randomUUID());
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.of(existente));

        Estoque resultado = service().provisionar(idEquipe, "EQ-01", UUID.randomUUID());

        assertThat(resultado).isSameAs(existente);
        verify(estoqueRepository, never()).saveAndFlush(any());
        verify(tipoEstoqueRepository, never()).findByNomeIgnoreCase(any());
    }

    @Test
    void rejeitaTipoCampoAusenteOuInativo() {
        UUID idEquipe = UUID.randomUUID();
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.empty());
        when(tipoEstoqueRepository.findByNomeIgnoreCase("CAMPO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().provisionar(idEquipe, "EQ-01", UUID.randomUUID()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("CAMPO");
    }

    private EstoqueCampoEquipeProvisioningService service() {
        return new EstoqueCampoEquipeProvisioningService(estoqueRepository, tipoEstoqueRepository);
    }
}
