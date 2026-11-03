package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Uma geração do As Built. O arquivo fica nos anexos da obra, e as versões anteriores continuam disponíveis.
 */
@Entity
@Table(name = "documentos_as_built")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoAsBuilt extends BaseEntity {

    public enum Status {
        PROCESSANDO,
        CONCLUIDO,
        ERRO
    }

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "numero_versao", nullable = false, updatable = false)
    private int numeroVersao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PROCESSANDO;

    @Column(name = "id_anexo")
    private UUID idAnexo;

    @Column(name = "mensagem_erro", length = 500)
    private String mensagemErro;

    public DocumentoAsBuilt(UUID idObra, int numeroVersao) {
        this.idObra = Objects.requireNonNull(idObra);
        this.numeroVersao = numeroVersao;
    }

    public void concluir(UUID idAnexo) {
        this.status = Status.CONCLUIDO;
        this.idAnexo = Objects.requireNonNull(idAnexo);
    }

    public void falhar(String mensagem) {
        this.status = Status.ERRO;
        this.mensagemErro = mensagem;
    }
}
