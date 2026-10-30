package com.nexo.app.shared.infrastructure.storage;

import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

/**
 * Implementação de {@link ObjectStorageService} sobre o Cloudflare R2 (API S3) usando
 * o AWS SDK v2. Traduz falhas do provedor em {@link ArmazenamentoException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class R2ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final R2Properties props;

    @Override
    public void enviar(String chave, InputStream conteudo, long tamanhoBytes, String contentType) {
        garantirConfigurado();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(chave)
                    .contentType(contentType)
                    .contentLength(tamanhoBytes)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(conteudo, tamanhoBytes));
        } catch (S3Exception e) {
            log.error("Falha ao enviar objeto '{}' para o R2: {}", chave, e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            throw new ArmazenamentoException("Não foi possível enviar o arquivo ao armazenamento.", e);
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao enviar objeto '{}' para o R2", chave, e);
            throw new ArmazenamentoException("Não foi possível enviar o arquivo ao armazenamento.", e);
        }
    }

    @Override
    public StorageObject baixar(String chave) {
        garantirConfigurado();
        try {
            ResponseInputStream<GetObjectResponse> resposta = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(chave)
                    .build());
            GetObjectResponse meta = resposta.response();
            return new StorageObject(resposta, meta.contentType(), meta.contentLength() == null ? 0L : meta.contentLength());
        } catch (NoSuchKeyException e) {
            throw new ArmazenamentoException("Arquivo não encontrado no armazenamento (chave: " + chave + ").", e);
        } catch (S3Exception e) {
            log.error("Falha ao baixar objeto '{}' do R2: {}", chave, e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            throw new ArmazenamentoException("Não foi possível recuperar o arquivo do armazenamento.", e);
        }
    }

    @Override
    public void remover(String chave) {
        garantirConfigurado();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(chave)
                    .build());
        } catch (S3Exception e) {
            log.error("Falha ao remover objeto '{}' do R2: {}", chave, e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            throw new ArmazenamentoException("Não foi possível remover o arquivo do armazenamento.", e);
        }
    }

    private void garantirConfigurado() {
        if (!props.isConfigurado()) {
            throw new ArmazenamentoException(
                    "Armazenamento R2 não configurado. Defina R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY e R2_BUCKET.");
        }
    }
}
