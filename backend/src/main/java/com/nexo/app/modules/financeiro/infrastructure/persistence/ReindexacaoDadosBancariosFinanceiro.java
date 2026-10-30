package com.nexo.app.modules.financeiro.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Preenche incrementalmente os blind indices que nao podiam ser gerados no SQL da migration. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReindexacaoDadosBancariosFinanceiro {

    private static final int LOTE = 200;

    private final SolicitacaoCompraRepository compraRepository;
    private final SolicitacaoPagamentoRepository pagamentoRepository;
    private final FornecedorRepository fornecedorRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reindexarLegado() {
        int total = reindexarCompras() + reindexarPagamentos() + reindexarFornecedores();
        if (total > 0) {
            log.info("Blind indices financeiros reindexados: {} registros", total);
        }
    }

    private int reindexarCompras() {
        int total = 0;
        while (true) {
            var lote = compraRepository.buscarPendentesReindexacao(PageRequest.of(0, LOTE));
            if (lote.isEmpty()) {
                return total;
            }
            lote.forEach(solicitacao -> solicitacao.getNucleo().reindexarDadosPagamento());
            compraRepository.saveAllAndFlush(lote);
            total += lote.size();
        }
    }

    private int reindexarPagamentos() {
        int total = 0;
        while (true) {
            var lote = pagamentoRepository.buscarPendentesReindexacao(PageRequest.of(0, LOTE));
            if (lote.isEmpty()) {
                return total;
            }
            lote.forEach(solicitacao -> solicitacao.getNucleo().reindexarDadosPagamento());
            pagamentoRepository.saveAllAndFlush(lote);
            total += lote.size();
        }
    }

    private int reindexarFornecedores() {
        int total = 0;
        while (true) {
            var lote = fornecedorRepository.buscarPendentesReindexacao(PageRequest.of(0, LOTE));
            if (lote.isEmpty()) {
                return total;
            }
            lote.forEach(com.nexo.app.modules.financeiro.domain.Fornecedor::reindexarDadosBancarios);
            fornecedorRepository.saveAllAndFlush(lote);
            total += lote.size();
        }
    }
}
