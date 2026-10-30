package com.nexo.app.shared.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test de conectividade real com o Cloudflare R2: faz um roundtrip
 * enviar → baixar → remover usando as classes de produção ({@link R2Properties},
 * {@link R2Config}, {@link R2ObjectStorageService}) e as credenciais do ambiente.
 *
 * <p>Desabilitado por padrão (faz I/O de rede). Para executar:
 * exporte as variáveis {@code R2_*} e {@code R2_SMOKE_TEST=true}, então
 * {@code mvn -Dtest=R2SmokeTest test}.</p>
 */
@EnabledIfEnvironmentVariable(named = "R2_SMOKE_TEST", matches = "true")
class R2SmokeTest {

    @Test
    @DisplayName("Roundtrip real no R2: enviar, baixar e remover um objeto")
    void roundtrip() throws Exception {
        R2Properties props = new R2Properties();
        props.setAccountId(System.getenv("R2_ACCOUNT_ID"));
        props.setAccessKeyId(System.getenv("R2_ACCESS_KEY_ID"));
        props.setSecretAccessKey(System.getenv("R2_SECRET_ACCESS_KEY"));
        props.setBucket(System.getenv("R2_BUCKET"));
        props.setEndpoint(System.getenv("R2_ENDPOINT"));

        S3Client client = new R2Config().r2S3Client(props);
        ObjectStorageService storage = new R2ObjectStorageService(client, props);

        String chave = "sst/_smoke-test/" + UUID.randomUUID() + ".txt";
        byte[] conteudo = ("ces-r2-smoke " + Instant.now()).getBytes(StandardCharsets.UTF_8);

        try {
            storage.enviar(chave, new ByteArrayInputStream(conteudo), conteudo.length, "text/plain");

            StorageObject obj = storage.baixar(chave);
            assertNotNull(obj);
            byte[] baixado;
            try (InputStream in = obj.conteudo()) {
                baixado = in.readAllBytes();
            }
            assertArrayEquals(conteudo, baixado, "O conteúdo baixado deve ser idêntico ao enviado");
        } finally {
            storage.remover(chave);
        }
    }
}
