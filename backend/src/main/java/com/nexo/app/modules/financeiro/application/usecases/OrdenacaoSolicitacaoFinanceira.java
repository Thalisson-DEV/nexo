package com.nexo.app.modules.financeiro.application.usecases;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Traduz nomes publicos de ordenacao para os caminhos persistidos da solicitacao. */
final class OrdenacaoSolicitacaoFinanceira {

    private OrdenacaoSolicitacaoFinanceira() {
    }

    static Pageable normalizar(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort sort = Sort.by(pageable.getSort().stream()
                .map(ordem -> ordem.withProperty(mapear(ordem.getProperty())))
                .toList());
        return pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : Pageable.unpaged(sort);
    }

    private static String mapear(String propriedade) {
        return switch (propriedade) {
            case "fornecedor", "fornecedor.nome", "fornecedorNome" -> "nucleo.fornecedorOrdenacao";
            default -> propriedade;
        };
    }
}
