package com.nexo.app.modules.financeiro.application.dto;

import java.util.Set;
import java.util.UUID;

/**
 * IDs encontrados nos cadastros de referência a partir de um termo de busca.
 *
 * <p>As solicitações financeiras guardam referências como UUIDs para não
 * duplicar os cadastros de RH, contratos e IAM. A busca global resolve o texto
 * nesses módulos e entrega somente os IDs para a especificação JPA.</p>
 */
public record IdsBuscaSolicitacaoFinanceira(
        Set<UUID> fornecedores,
        Set<UUID> colaboradores,
        Set<UUID> veiculos,
        Set<UUID> bases,
        Set<UUID> processos,
        Set<UUID> obras,
        Set<UUID> usuarios,
        Set<UUID> compras
) {

    public IdsBuscaSolicitacaoFinanceira {
        fornecedores = copiar(fornecedores);
        colaboradores = copiar(colaboradores);
        veiculos = copiar(veiculos);
        bases = copiar(bases);
        processos = copiar(processos);
        obras = copiar(obras);
        usuarios = copiar(usuarios);
        compras = copiar(compras);
    }

    public static IdsBuscaSolicitacaoFinanceira vazio() {
        return new IdsBuscaSolicitacaoFinanceira(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static Set<UUID> copiar(Set<UUID> ids) {
        return ids == null ? Set.of() : Set.copyOf(ids);
    }
}
