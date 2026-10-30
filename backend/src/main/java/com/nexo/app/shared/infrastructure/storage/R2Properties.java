package com.nexo.app.shared.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração de acesso ao bucket Cloudflare R2 (API S3-compatível).
 * Valores vêm de variáveis de ambiente (ver {@code application.yaml}, prefixo {@code ces.r2}).
 */
@Component
@ConfigurationProperties(prefix = "ces.r2")
@Getter
@Setter
public class R2Properties {

    /** Account ID Cloudflare — usado para derivar o endpoint quando {@link #endpoint} é vazio. */
    private String accountId;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucket;
    /** Endpoint S3 do R2. Opcional: se vazio, é derivado do {@link #accountId}. */
    private String endpoint;

    /** Indica se há credenciais/bucket suficientes para operar o armazenamento. */
    public boolean isConfigurado() {
        return preenchido(accessKeyId) && preenchido(secretAccessKey) && preenchido(bucket)
                && (preenchido(endpoint) || preenchido(accountId));
    }

    /** Endpoint efetivo: o configurado ou o derivado do account-id. */
    public String resolverEndpoint() {
        if (preenchido(endpoint)) {
            return endpoint;
        }
        return "https://" + accountId + ".r2.cloudflarestorage.com";
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
