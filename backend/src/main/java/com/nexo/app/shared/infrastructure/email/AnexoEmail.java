package com.nexo.app.shared.infrastructure.email;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

/**
 * Um arquivo já carregado em memória, pronto para acompanhar a mensagem.
 *
 * <p>Carrega bytes, e não uma referência ao armazenamento, porque a resolução do
 * arquivo é responsabilidade de quem monta a mensagem — o adaptador de envio não pode
 * conhecer o R2. O tamanho é limitado bem antes de chegar aqui
 * ({@code ces.email.limite-anexos-bytes}); um anexo grande vira link no corpo em vez
 * de arquivo, que é o que qualquer servidor de e-mail faria de todo modo, só que
 * rejeitando a mensagem inteira.</p>
 */
public record AnexoEmail(String nomeArquivo, String contentType, byte[] conteudo) {

    public AnexoEmail {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new InvarianteVioladaException("O anexo do e-mail precisa de um nome de arquivo.");
        }
        if (conteudo == null || conteudo.length == 0) {
            throw new InvarianteVioladaException("O anexo '" + nomeArquivo + "' está vazio.");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType;
    }

    public int tamanhoBytes() {
        return conteudo.length;
    }
}
