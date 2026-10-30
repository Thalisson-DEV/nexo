package com.nexo.app.shared.infrastructure.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Cria o cliente S3 apontado para o Cloudflare R2. Pontos críticos para o R2:
 * <ul>
 *   <li>{@code region("auto")} — obrigatório pelo SDK S3, o R2 ignora a região;</li>
 *   <li>{@code endpointOverride} para o endpoint do R2;</li>
 *   <li>{@code forcePathStyle(true)} — o endpoint S3 do R2 usa path-style;</li>
 *   <li>checksums em {@code WHEN_REQUIRED} — evita o checksum padrão (CRC32) que o
 *       R2 pode rejeitar em uploads/downloads.</li>
 * </ul>
 * O bean é criado mesmo sem credenciais — a app sobe normalmente quando o R2 não está
 * configurado (como as demais integrações). O builder do SDK exige credenciais não-vazias,
 * por isso usamos um placeholder quando ausentes; a validação real ocorre no momento do uso,
 * em {@link R2ObjectStorageService#garantirConfigurado()}.
 */
@Configuration
public class R2Config {

    private static final String PLACEHOLDER = "nao-configurado";

    @Bean
    public S3Client r2S3Client(R2Properties props) {
        return S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(props.resolverEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                ouPlaceholder(props.getAccessKeyId()),
                                ouPlaceholder(props.getSecretAccessKey()))))
                .forcePathStyle(true)
                .httpClientBuilder(ApacheHttpClient.builder())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    private static String ouPlaceholder(String valor) {
        return (valor == null || valor.isBlank()) ? PLACEHOLDER : valor;
    }
}
