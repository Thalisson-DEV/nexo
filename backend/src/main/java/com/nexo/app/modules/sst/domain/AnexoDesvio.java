package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Evidência (anexo) de um {@link DesvioSeguranca}: uma fotografia ou documento
 * (PDF/Word) que comprova a não-conformidade ou a ação corretiva. O conteúdo
 * binário é armazenado no R2; aqui guarda-se apenas os metadados e a chave do objeto.
 */
@Entity
@Table(name = "anexos_desvio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnexoDesvio extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_desvio", nullable = false)
    private DesvioSeguranca desvio;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexto", nullable = false, length = 20)
    private ContextoAnexo contexto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoAnexo tipo;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    AnexoDesvio(DesvioSeguranca desvio, ContextoAnexo contexto, String nomeArquivo,
                String contentType, long tamanhoBytes, String objectKey) {
        this.desvio = Objects.requireNonNull(desvio, "Desvio é obrigatório");
        this.contexto = Objects.requireNonNull(contexto, "O contexto do anexo é obrigatório");
        this.nomeArquivo = exigir(nomeArquivo, "O nome do arquivo é obrigatório.");
        this.contentType = exigir(contentType, "O content type do anexo é obrigatório.");
        this.objectKey = exigir(objectKey, "A chave do objeto é obrigatória.");
        if (tamanhoBytes <= 0) {
            throw new InvarianteVioladaException("O tamanho do anexo deve ser maior que zero.");
        }
        this.tamanhoBytes = tamanhoBytes;
        this.tipo = TipoAnexo.deContentType(contentType);
    }

    private static String exigir(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor;
    }
}
