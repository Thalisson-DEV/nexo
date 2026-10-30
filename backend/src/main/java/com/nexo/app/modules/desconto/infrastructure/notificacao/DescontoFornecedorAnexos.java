package com.nexo.app.modules.desconto.infrastructure.notificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.desconto.domain.DescontoAnexo;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;

import lombok.RequiredArgsConstructor;

/**
 * Entrega ao e-mail os documentos do termo de desconto.
 *
 * <p>É o caso que motivou a porta: quem valida um desconto precisa ver o termo, e
 * abrir o sistema para isso é justamente o atrito que o aviso por e-mail deveria
 * poupar. Com os arquivos anexados, a decisão cabe na leitura da mensagem e o link
 * fica para o registro da decisão.</p>
 *
 * <p>Transação própria e somente leitura: quem chama é o envio de e-mail, já fora da
 * transação de negócio e em outra thread, então a coleção preguiçosa de anexos
 * precisa de uma sessão aberta aqui.</p>
 */
@Component
@RequiredArgsConstructor
public class DescontoFornecedorAnexos implements FornecedorAnexosNotificacao {

    private final DescontoRepository repository;

    @Override
    public boolean atende(String chaveGatilho) {
        return chaveGatilho != null && chaveGatilho.startsWith("desconto.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnexoNotificacao> anexosDe(UUID idDesconto) {
        return repository.findById(idDesconto)
                .map(desconto -> desconto.getAnexos().stream()
                        .filter(DescontoAnexo::isAtivo)
                        // 'url' guarda a chave do objeto no R2, não um endereço HTTP —
                        // o nome do campo é herança do armazenamento anterior.
                        .map(anexo -> new AnexoNotificacao(
                                anexo.getNomeArquivo(), anexo.getContentType(),
                                anexo.getUrl(), anexo.getTamanhoBytes()))
                        .toList())
                .orElseGet(List::of);
    }
}
