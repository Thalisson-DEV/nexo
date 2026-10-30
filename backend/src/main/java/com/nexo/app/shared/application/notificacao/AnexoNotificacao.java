package com.nexo.app.shared.application.notificacao;

/**
 * Um arquivo do registro que originou a notificação, descrito sem os bytes.
 *
 * <p>Carrega a chave no armazenamento, e não o conteúdo, porque quem responde à porta
 * está dentro de uma consulta ao banco e não pode trazer megabytes junto. Quem baixa
 * de fato é o envio de e-mail, já fora da transação e com teto de tamanho.</p>
 */
public record AnexoNotificacao(String nomeArquivo, String contentType,
                               String chaveObjeto, long tamanhoBytes) {
}
