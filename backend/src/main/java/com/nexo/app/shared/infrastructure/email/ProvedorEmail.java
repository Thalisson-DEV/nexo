package com.nexo.app.shared.infrastructure.email;

/**
 * Qual adaptador de envio responde pela porta {@link ServicoEmail}.
 *
 * <p>A escolha é de implantação, não de código: nenhum módulo de negócio sabe por onde
 * o e-mail sai. Trocar o e-mail corporativo por um serviço transacional — ou o
 * contrário, no dia em que a caixa da GoDaddy passar a bastar — é mudar
 * {@code ces.email.provedor} e reiniciar.</p>
 */
public enum ProvedorEmail {

    /**
     * Servidor SMTP qualquer. É o caminho do e-mail corporativo da empresa
     * ({@code smtpout.secureserver.net}, da GoDaddy), mas atende igualmente Google
     * Workspace, Microsoft 365 ou um relay interno.
     */
    SMTP,

    /** API HTTP do Resend. Melhor entregabilidade e telemetria de bounce. */
    RESEND,

    /**
     * Nada é enviado; o envio é registrado no log da aplicação.
     *
     * <p>É o padrão, e de propósito: um ambiente de desenvolvimento sem credenciais
     * não pode nem falhar ao subir nem disparar e-mail de verdade para a lista real
     * de aprovadores.</p>
     */
    NENHUM
}
