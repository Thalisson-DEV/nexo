package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DebitoSaldoServiceConcurrencyTest {
    @Autowired private DebitoSaldoService debitoSaldoService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TipoEstoqueRepository tipoEstoqueRepository;
    @Autowired private EstoqueRepository estoqueRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private SaldoRepository saldoRepository;

    @Test
    void debitosSimultaneosDeSaldoAusenteNaoPerdemAtualizacaoNemDuplicamLinha() throws Exception {
        TipoEstoque tipo = tipoEstoqueRepository.findByNomeIgnoreCase("FISICO")
                .orElseGet(() -> tipoEstoqueRepository.save(new TipoEstoque("FISICO")));
        Estoque estoque = estoqueRepository.save(new Estoque("CONCORRENCIA-" + UUID.randomUUID(), tipo));
        Material material = materialRepository.save(new Material("CON-" + UUID.randomUUID(),
                "Material teste concorrencia", "UN", null, null, null, false, UUID.randomUUID()));
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<?> primeiro = CompletableFuture.runAsync(() -> debitar(inicio, estoque, material), executor);
            CompletableFuture<?> segundo = CompletableFuture.runAsync(() -> debitar(inicio, estoque, material), executor);
            inicio.countDown();
            CompletableFuture.allOf(primeiro, segundo).get(20, TimeUnit.SECONDS);
        }
        assertThat(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                estoque.getId(), material.getId(), CondicaoMaterial.NOVO).orElseThrow().getQuantidade())
                .isEqualByComparingTo(new BigDecimal("-2"));
    }

    private void debitar(CountDownLatch inicio, Estoque estoque, Material material) {
        try {
            inicio.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        transactionTemplate.executeWithoutResult(status -> debitoSaldoService.debitar(
                estoque, material, CondicaoMaterial.NOVO, BigDecimal.ONE));
    }
}
