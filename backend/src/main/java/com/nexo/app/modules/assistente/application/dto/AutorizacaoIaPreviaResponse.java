package com.nexo.app.modules.assistente.application.dto;

/**
 * Tela de consentimento: quem pede acesso e para onde a pessoa volta.
 *
 * @param aplicativo     nome informado pelo aplicativo no cadastro (ex.: "Claude")
 * @param destino        host para onde a pessoa volta depois de permitir (ex.: "claude.ai")
 * @param podeEscrever   o perfil da pessoa tem ferramentas de cadastro/alteracao para oferecer
 */
public record AutorizacaoIaPreviaResponse(String aplicativo, String site, String destino, boolean podeEscrever) {
}
