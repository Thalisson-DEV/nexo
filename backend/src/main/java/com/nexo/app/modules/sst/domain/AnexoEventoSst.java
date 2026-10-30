package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Evidência anexada a um {@link EventoSst}: foto do local, PDF da CAT, boletim de
 * ocorrência, comprovação da tratativa. O binário mora no armazenamento de objetos;
 * aqui ficam apenas os metadados e a chave.
 *
 * <p>Diferente da evidência de inspeção, o anexo do evento é enviado <b>depois</b> do
 * registro: um acidente é registrado da base, no desktop, depois do fato. Não há o
 * problema de conexão de campo que justifica o pré-upload, e não haveria o que fazer
 * com arquivos órfãos de um registro que nunca chegou a existir.</p>
 */
@Entity
@Table(name = "evento_sst_anexos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnexoEventoSst extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento", nullable = false)
    private EventoSst evento;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexto", nullable = false, length = 20)
    private ContextoAnexoEvento contexto;

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

    AnexoEventoSst(EventoSst evento, ContextoAnexoEvento contexto, String nomeArquivo,
                   String contentType, long tamanhoBytes, String objectKey) {
        this.evento = Objects.requireNonNull(evento, "O evento é obrigatório");
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
