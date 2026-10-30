package com.nexo.app.shared.infrastructure.email;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Envio pela API HTTP do Resend.
 *
 * <p>Alternativa ao SMTP quando o volume cresce: entrega o identificador da mensagem,
 * que fica gravado em {@code logs_email} e permite rastrear bounce no painel do
 * provedor — coisa que SMTP puro não oferece.</p>
 *
 * <p>O domínio do remetente precisa estar verificado no Resend (SPF e DKIM). Enviar
 * como {@code @exemplo.com.br} sem essa verificação é recusado pela própria API, e o
 * erro devolvido chega inteiro ao registro.</p>
 */
@Slf4j
public class ResendEmailService implements ServicoEmail {

    private static final String CAMINHO_ENVIO = "/emails";

    private final EmailProperties propriedades;
    private final RestClient cliente;

    public ResendEmailService(EmailProperties propriedades, RestClient.Builder builder) {
        this.propriedades = propriedades;
        this.cliente = builder
                .baseUrl(propriedades.getResend().getUrlBase())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + propriedades.getResend().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ProvedorEmail provedor() {
        return ProvedorEmail.RESEND;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResultadoEnvioEmail enviar(MensagemEmail mensagem) {
        try {
            Map<String, Object> resposta = cliente.post()
                    .uri(CAMINHO_ENVIO)
                    .body(corpo(mensagem))
                    .retrieve()
                    .body(Map.class);

            Object id = resposta == null ? null : resposta.get("id");
            return ResultadoEnvioEmail.entregue(ProvedorEmail.RESEND,
                    id == null ? null : id.toString());

        } catch (RestClientException e) {
            log.warn("Falha ao enviar e-mail pelo Resend para {}: {}",
                    mensagem.destinatarios(), e.getMessage());
            return ResultadoEnvioEmail.falhou(ProvedorEmail.RESEND, e.getMessage());
        }
    }

    private Map<String, Object> corpo(MensagemEmail mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("from", remetenteFormatado());
        corpo.put("to", List.copyOf(mensagem.destinatarios()));
        corpo.put("subject", mensagem.assunto());
        corpo.put("html", mensagem.html());
        corpo.put("text", mensagem.texto());

        if (StringUtils.hasText(propriedades.getResponderPara())) {
            corpo.put("reply_to", propriedades.getResponderPara());
        }
        if (mensagem.temAnexos()) {
            List<Map<String, Object>> anexos = new ArrayList<>();
            for (AnexoEmail anexo : mensagem.anexos()) {
                anexos.add(Map.of(
                        "filename", anexo.nomeArquivo(),
                        "content", Base64.getEncoder().encodeToString(anexo.conteudo()),
                        "content_type", anexo.contentType()));
            }
            corpo.put("attachments", anexos);
        }
        return corpo;
    }

    /** O Resend aceita {@code Nome <endereco>}, que é o que preserva a marca na caixa. */
    private String remetenteFormatado() {
        String nome = propriedades.getRemetenteNome();
        if (!StringUtils.hasText(nome)) {
            return propriedades.getRemetente();
        }
        return nome + " <" + propriedades.getRemetente() + ">";
    }
}
