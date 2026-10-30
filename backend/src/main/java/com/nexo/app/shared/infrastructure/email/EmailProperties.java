package com.nexo.app.shared.infrastructure.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração única do envio de e-mail, no mesmo molde de {@code R2Properties}.
 *
 * <p>Tudo mora sob o prefixo {@code ces.email} — inclusive os dados de SMTP, em vez do
 * {@code spring.mail.*} padrão. A razão é que o {@code JavaMailSender} do Spring só
 * nasce quando {@code spring.mail.host} existe, e essa condição invisível transformaria
 * um erro de configuração em um bean ausente no meio da subida. Construindo o sender a
 * partir daqui, a única chave que decide qualquer coisa é {@link #provedor}.</p>
 */
@Component
@ConfigurationProperties(prefix = "ces.email")
@Getter
@Setter
public class EmailProperties {

    private ProvedorEmail provedor = ProvedorEmail.NENHUM;

    /** Endereço no campo "De" (ex.: {@code nao-responda@exemplo.com.br}). */
    private String remetente;

    /** Nome exibido ao lado do endereço na caixa de quem recebe. */
    private String remetenteNome = "Nexo — Gestão Operacional";

    /** Para onde vai a resposta, quando diferente do remetente. Opcional. */
    private String responderPara;

    /**
     * Raiz do frontend, usada para transformar o deeplink da notificação
     * ({@code /dp/descontos/{id}}) no link absoluto que o botão do e-mail precisa.
     * Sem ela, o e-mail sai sem botão — um link relativo não tem para onde apontar
     * dentro de uma caixa de correio.
     */
    private String urlBase;

    /**
     * Logo exibido no cabeçalho. Precisa ser uma URL pública e absoluta; vazio faz o
     * cabeçalho cair na marca em texto, que é o padrão. Imagem em e-mail depende de o
     * cliente aceitar carregar remoto, então o layout nunca conta com ela para nada
     * além de decoração.
     */
    private String logoUrl;

    /** Teto de bytes somados dos anexos de um e-mail. Acima disso, viram só links. */
    private long limiteAnexosBytes = 8L * 1024 * 1024;

    private final Smtp smtp = new Smtp();
    private final Resend resend = new Resend();

    /** Se há o mínimo para tentar enviar pelo provedor escolhido. */
    public boolean isConfigurado() {
        if (!preenchido(remetente)) {
            return false;
        }
        return switch (provedor) {
            case SMTP -> smtp.isConfigurado();
            case RESEND -> preenchido(resend.getApiKey());
            case NENHUM -> false;
        };
    }

    /** Torna absoluto um deeplink relativo. Devolve nulo quando não há como. */
    public String resolverUrl(String deeplink) {
        if (!preenchido(deeplink)) {
            return null;
        }
        String destino = deeplink.trim();
        if (destino.startsWith("http://") || destino.startsWith("https://")) {
            return destino;
        }
        if (!preenchido(urlBase)) {
            return null;
        }
        String raiz = urlBase.trim();
        while (raiz.endsWith("/")) {
            raiz = raiz.substring(0, raiz.length() - 1);
        }
        return destino.startsWith("/") ? raiz + destino : raiz + "/" + destino;
    }

    @Getter
    @Setter
    public static class Smtp {
        private String host;
        private int porta = 587;
        private String usuario;
        private String senha;
        /** {@code true} para porta 465 (SMTPS). A GoDaddy aceita as duas formas. */
        private boolean ssl = false;
        private boolean startTls = true;
        /** Milissegundos de espera por conexão e por resposta do servidor. */
        private int timeoutMs = 15_000;

        public boolean isConfigurado() {
            return preenchido(host) && preenchido(usuario) && preenchido(senha);
        }
    }

    @Getter
    @Setter
    public static class Resend {
        private String apiKey;
        private String urlBase = "https://api.resend.com";
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
