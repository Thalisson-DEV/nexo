package com.nexo.app.modules.desconto.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "desconto_anexo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DescontoAnexo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desconto_id", nullable = false)
    private Desconto desconto;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    public DescontoAnexo(Desconto desconto, String nomeArquivo, String url, String contentType, Long tamanhoBytes) {
        if (desconto == null) throw new IllegalArgumentException("O desconto associado não pode ser nulo.");
        if (!StringUtils.hasText(nomeArquivo)) throw new IllegalArgumentException("O nome do arquivo não pode ser vazio.");
        if (!StringUtils.hasText(url)) throw new IllegalArgumentException("A URL do anexo não pode ser vazia.");
        if (!StringUtils.hasText(contentType)) throw new IllegalArgumentException("O tipo de conteúdo não pode ser vazio.");
        if (tamanhoBytes == null || tamanhoBytes < 0) throw new IllegalArgumentException("O tamanho em bytes é inválido.");
        
        this.desconto = desconto;
        this.nomeArquivo = nomeArquivo;
        this.url = url;
        this.contentType = contentType;
        this.tamanhoBytes = tamanhoBytes;
    }
}
