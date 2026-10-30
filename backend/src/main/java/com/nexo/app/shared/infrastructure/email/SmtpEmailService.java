package com.nexo.app.shared.infrastructure.email;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Envio por servidor SMTP — o caminho da caixa corporativa da empresa na GoDaddy.
 *
 * <p>O {@code JavaMailSender} é construído aqui, e não herdado da autoconfiguração do
 * Spring, para que a única chave que liga o envio seja {@code ces.email.provedor}.
 * Detalhe da GoDaddy que costuma custar uma tarde: a porta 465 exige SSL direto
 * ({@code ces.email.smtp.ssl=true}) e a 587 exige STARTTLS — as duas funcionam, mas
 * não com a mesma configuração.</p>
 */
@Slf4j
public class SmtpEmailService implements ServicoEmail {

    private final EmailProperties propriedades;
    private final JavaMailSender remetente;

    public SmtpEmailService(EmailProperties propriedades) {
        this.propriedades = propriedades;
        this.remetente = construirSender(propriedades.getSmtp());
    }

    @Override
    public ProvedorEmail provedor() {
        return ProvedorEmail.SMTP;
    }

    @Override
    public ResultadoEnvioEmail enviar(MensagemEmail mensagem) {
        try {
            MimeMessage mime = remetente.createMimeMessage();
            // multipart obrigatório quando há anexo; e mesmo sem anexo o corpo sai em
            // duas partes (texto e HTML), então o helper é sempre multipart.
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            helper.setFrom(propriedades.getRemetente(), propriedades.getRemetenteNome());
            helper.setTo(mensagem.destinatarios().toArray(String[]::new));
            helper.setSubject(mensagem.assunto());
            helper.setText(mensagem.texto(), mensagem.html());

            if (StringUtils.hasText(propriedades.getResponderPara())) {
                helper.setReplyTo(propriedades.getResponderPara());
            }
            for (AnexoEmail anexo : mensagem.anexos()) {
                helper.addAttachment(anexo.nomeArquivo(),
                        new ByteArrayDataSource(anexo.conteudo(), anexo.contentType()));
            }

            remetente.send(mime);
            // SMTP não devolve identificador; o Message-ID gerado pelo servidor não volta
            // ao cliente, então o registro fica sem id externo — e isso é esperado.
            return ResultadoEnvioEmail.entregue(ProvedorEmail.SMTP, null);

        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.warn("Falha ao enviar e-mail por SMTP para {}: {}",
                    mensagem.destinatarios(), e.getMessage());
            return ResultadoEnvioEmail.falhou(ProvedorEmail.SMTP, descrever(e));
        }
    }

    private static JavaMailSender construirSender(EmailProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPorta());
        sender.setUsername(smtp.getUsuario());
        sender.setPassword(smtp.getSenha());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStartTls() && !smtp.isSsl()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(smtp.getTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(smtp.getTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(smtp.getTimeoutMs()));
        if (smtp.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }

    /**
     * A causa raiz costuma estar na exceção aninhada — a de cima diz apenas "falha ao
     * enviar", e é justamente a de baixo que nomeia credencial recusada ou porta
     * fechada.
     */
    private static String descrever(Exception e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return e.getClass().getSimpleName() + ": " + causa.getMessage();
    }
}
