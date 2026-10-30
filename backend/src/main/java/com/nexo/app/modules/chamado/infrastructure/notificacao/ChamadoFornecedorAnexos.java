package com.nexo.app.modules.chamado.infrastructure.notificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.chamado.domain.AnexoChamado;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;

import lombok.RequiredArgsConstructor;

/**
 * Entrega ao e-mail as evidências anexadas ao chamado.
 *
 * <p>Vale sobretudo na abertura: a equipe de suporte recebe o print do erro junto com
 * o aviso, em vez de precisar entrar na fila para descobrir do que se trata.</p>
 *
 * <p>Nem todo gatilho de chamado tem anexo útil — {@code chamado.resolvido} traz os
 * mesmos arquivos da abertura, já conhecidos de quem acompanhou. Isso não é filtrado
 * aqui: quem decide se aquele aviso sai por e-mail é a regra, e uma regra que não
 * envia e-mail nunca chega a perguntar por anexos.</p>
 */
@Component
@RequiredArgsConstructor
public class ChamadoFornecedorAnexos implements FornecedorAnexosNotificacao {

    private final ChamadoRepository repository;

    @Override
    public boolean atende(String chaveGatilho) {
        return chaveGatilho != null && chaveGatilho.startsWith("chamado.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnexoNotificacao> anexosDe(UUID idChamado) {
        return repository.findById(idChamado)
                .map(chamado -> chamado.getAnexos().stream()
                        .filter(AnexoChamado::isAtivo)
                        .map(anexo -> new AnexoNotificacao(
                                anexo.getNomeArquivo(), anexo.getContentType(),
                                anexo.getObjectKey(), anexo.getTamanhoBytes()))
                        .toList())
                .orElseGet(List::of);
    }
}
