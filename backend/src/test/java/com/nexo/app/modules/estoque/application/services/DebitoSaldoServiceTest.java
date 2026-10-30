package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebitoSaldoServiceTest {

    @Mock private SaldoRepository repository;
    @Mock private EstoqueRepository estoqueRepository;
    @InjectMocks private DebitoSaldoService service;

    @Test
    void inicializaSaldoAusenteBloqueiaEDebitaAteNegativo() {
        Estoque estoque = org.mockito.Mockito.mock(Estoque.class);
        Material material = org.mockito.Mockito.mock(Material.class);
        UUID estoqueId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        when(estoque.getId()).thenReturn(estoqueId);
        when(material.getId()).thenReturn(materialId);
        when(estoque.isAtivo()).thenReturn(true);
        when(material.isAtivo()).thenReturn(true);
        when(estoqueRepository.findByIdForUpdate(estoqueId)).thenReturn(Optional.of(estoque));
        Saldo saldo = new Saldo(estoque, material, CondicaoMaterial.NOVO);
        when(repository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(
                estoqueId, materialId, CondicaoMaterial.NOVO)).thenReturn(Optional.of(saldo));
        when(repository.save(any(Saldo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Saldo resultado = service.debitar(
                estoque, material, CondicaoMaterial.NOVO, new BigDecimal("4.000"));

        verify(estoqueRepository).findByIdForUpdate(estoqueId);
        assertThat(resultado.getQuantidade()).isEqualByComparingTo("-4.000");
    }
}
