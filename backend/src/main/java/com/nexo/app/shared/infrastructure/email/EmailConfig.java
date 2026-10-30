package com.nexo.app.shared.infrastructure.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Escolhe o adaptador de envio a partir de {@code ces.email.provedor}.
 *
 * <p>A seleção é feita em código, e não por {@code @ConditionalOnProperty} espalhado
 * pelas implementações, por causa da queda para {@link EmailDesabilitadoService}
 * quando a configuração está incompleta: uma condição por bean acertaria o provedor
 * mas deixaria a aplicação sem nenhum, e a falta só apareceria na subida, como um
 * erro de injeção sem relação aparente com e-mail.</p>
 */
@Configuration
@Slf4j
public class EmailConfig {

    @Bean
    public ServicoEmail servicoEmail(EmailProperties propriedades, RestClient.Builder builder) {
        if (propriedades.getProvedor() == ProvedorEmail.NENHUM) {
            log.info("Envio de e-mail desligado por configuração (ces.email.provedor=NENHUM).");
            return new EmailDesabilitadoService();
        }
        if (!propriedades.isConfigurado()) {
            log.warn("Provedor de e-mail '{}' selecionado sem configuração completa "
                            + "(remetente, credenciais). O envio segue desligado.",
                    propriedades.getProvedor());
            return new EmailDesabilitadoService();
        }

        log.info("Envio de e-mail ativo pelo provedor {} como '{}'.",
                propriedades.getProvedor(), propriedades.getRemetente());
        return switch (propriedades.getProvedor()) {
            case SMTP -> new SmtpEmailService(propriedades);
            case RESEND -> new ResendEmailService(propriedades, builder);
            case NENHUM -> new EmailDesabilitadoService();
        };
    }
}
