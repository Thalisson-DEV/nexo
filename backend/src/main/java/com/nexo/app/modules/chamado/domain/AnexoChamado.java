package com.nexo.app.modules.chamado.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "anexos_chamado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnexoChamado extends BaseEntity {

    @JsonBackReference("chamado-anexos")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_chamado", nullable = false)
    private Chamado chamado;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    AnexoChamado(Chamado chamado, String nomeArquivo, String contentType,
                  long tamanhoBytes, String objectKey) {
        if (chamado == null) {
            throw new InvarianteVioladaException("O chamado do anexo é obrigatório.");
        }
        this.chamado = chamado;
        this.nomeArquivo = exigir(nomeArquivo, "O nome do arquivo é obrigatório.");
        this.contentType = exigir(contentType, "O tipo do arquivo é obrigatório.");
        this.objectKey = exigir(objectKey, "A chave de armazenamento é obrigatória.");
        if (tamanhoBytes <= 0) {
            throw new InvarianteVioladaException("O anexo deve possuir conteúdo.");
        }
        this.tamanhoBytes = tamanhoBytes;
    }

    private static String exigir(String valor, String mensagem) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor.trim();
    }
}
