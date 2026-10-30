package com.nexo.app.modules.financeiro.infrastructure.notificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraSubmetidaEvent;

import lombok.RequiredArgsConstructor;

/** Entrega ao e-mail de avaliação os documentos vinculados à solicitação. */
@Component
@RequiredArgsConstructor
public class FinanceiroFornecedorAnexos implements FornecedorAnexosNotificacao {

    private final DocumentoFinanceiroRepository repository;

    @Override
    public boolean atende(String chaveGatilho) {
        return SolicitacaoFinanceiraSubmetidaEvent.CHAVE_GATILHO.equals(chaveGatilho);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnexoNotificacao> anexosDe(UUID idSolicitacao) {
        return repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(idSolicitacao)
                .stream()
                .filter(DocumentoFinanceiro::isAtivo)
                .map(documento -> new AnexoNotificacao(
                        documento.getNomeArquivo(),
                        documento.getContentType(),
                        documento.getObjectKey(),
                        documento.getTamanhoBytes()))
                .toList();
    }
}
