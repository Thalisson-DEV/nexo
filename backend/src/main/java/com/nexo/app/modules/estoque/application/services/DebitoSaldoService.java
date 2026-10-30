package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/** Centraliza a criacao, o lock e o debito permissivo de saldos quantitativos. */
@Service
@RequiredArgsConstructor
public class DebitoSaldoService {

    private final SaldoRepository saldoRepository;
    private final EstoqueRepository estoqueRepository;

    @Transactional
    public Saldo debitar(
            Estoque estoque,
            Material material,
            CondicaoMaterial condicao,
            BigDecimal quantidade
    ) {
        Objects.requireNonNull(estoque, "O estoque de origem e obrigatorio");
        Objects.requireNonNull(material, "O material e obrigatorio");
        CondicaoMaterial condicaoEfetiva = Objects.requireNonNullElse(condicao, CondicaoMaterial.NOVO);

        Estoque estoqueBloqueado = estoqueRepository.findByIdForUpdate(estoque.getId())
                .orElseThrow(() -> new IllegalStateException("O estoque de origem nao foi encontrado."));
        Saldo saldo = saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(
                        estoque.getId(), material.getId(), condicaoEfetiva)
                .orElseGet(() -> saldoRepository.saveAndFlush(
                        new Saldo(estoqueBloqueado, material, condicaoEfetiva)));
        saldo.removerQuantidade(quantidade);
        return saldoRepository.save(saldo);
    }
}
