package com.nexo.app.modules.assistente.application.dto;

/**
 * O que a tela precisa para orientar a conexao.
 *
 * @param urlServidor    endereco que a pessoa cola no Claude, ChatGPT...
 * @param podeEscrever   o perfil tem alguma ferramenta de cadastro/alteracao disponivel
 * @param ferramentas    quantas ferramentas a IA vai enxergar com o perfil da pessoa (so leitura)
 * @param ferramentasEscrita quantas delas cadastram ou alteram registros
 */
public record ConexaoIaInfoResponse(String urlServidor, boolean podeEscrever, int ferramentas, int ferramentasEscrita) {
}
