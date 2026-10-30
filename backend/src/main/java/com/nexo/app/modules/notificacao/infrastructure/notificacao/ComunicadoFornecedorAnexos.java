package com.nexo.app.modules.notificacao.infrastructure.notificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.application.usecases.ComunicadoUseCases;
import com.nexo.app.modules.notificacao.domain.AnexoComunicado;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ComunicadoRepository;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;

import lombok.RequiredArgsConstructor;

/**
 * Entrega ao e-mail os arquivos do informe do mural.
 *
 * <p>É o que faz o comunicado por e-mail valer como comunicação de fato: a política
 * revisada, o cronograma de parada e o formulário chegam com a mensagem, e não como
 * promessa de que estão em algum lugar do sistema.</p>
 */
@Component
@RequiredArgsConstructor
public class ComunicadoFornecedorAnexos implements FornecedorAnexosNotificacao {

    private final ComunicadoRepository repository;

    @Override
    public boolean atende(String chaveGatilho) {
        return ComunicadoUseCases.CHAVE_ANEXOS_COMUNICADO.equals(chaveGatilho);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnexoNotificacao> anexosDe(UUID idComunicado) {
        return repository.findById(idComunicado)
                .map(comunicado -> comunicado.getAnexos().stream()
                        .filter(AnexoComunicado::isAtivo)
                        .map(anexo -> new AnexoNotificacao(
                                anexo.getNomeArquivo(), anexo.getContentType(),
                                anexo.getObjectKey(), anexo.getTamanhoBytes()))
                        .toList())
                .orElseGet(List::of);
    }
}
