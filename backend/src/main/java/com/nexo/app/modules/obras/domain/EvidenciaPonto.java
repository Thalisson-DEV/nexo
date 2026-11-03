package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Uma foto do book da obra, lançada no ponto. Responde a um item do modelo da ficha ou é avulsa. Guarda o título do
 * item, para continuar legível, e o arquivo original junto com uma miniatura.
 */
@Entity
@Table(name = "evidencias_ponto_obra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenciaPonto extends BaseEntity {

    /** Item do modelo a que a foto responde. */
    public record ItemRespondido(UUID idModelo, String idItem, String titulo) {
    }

    /** O arquivo já guardado no armazenamento. */
    public record Arquivo(String nome, String contentType, long tamanhoBytes, String chaveStorage, String chaveMiniatura) {
    }

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_ponto", nullable = false, updatable = false)
    private UUID idPonto;

    @Column(name = "id_modelo", updatable = false)
    private UUID idModelo;

    @Column(name = "id_item", length = 100, updatable = false)
    private String idItem;

    @Column(name = "titulo_item", length = 150, updatable = false)
    private String tituloItem;

    @Column(name = "legenda", length = 500)
    private String legenda;

    @Column(name = "nome_arquivo", nullable = false, updatable = false)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private long tamanhoBytes;

    @Column(name = "chave_storage", nullable = false, length = 500, updatable = false)
    private String chaveStorage;

    @Column(name = "chave_miniatura", nullable = false, length = 500, updatable = false)
    private String chaveMiniatura;

    public EvidenciaPonto(UUID idObra, UUID idPonto, ItemRespondido item, String legenda, Arquivo arquivo) {
        this.idObra = Objects.requireNonNull(idObra);
        this.idPonto = Objects.requireNonNull(idPonto);
        if (item != null) {
            this.idModelo = item.idModelo();
            this.idItem = item.idItem();
            this.tituloItem = item.titulo();
        }
        this.nomeArquivo = arquivo.nome();
        this.contentType = arquivo.contentType();
        this.tamanhoBytes = arquivo.tamanhoBytes();
        this.chaveStorage = arquivo.chaveStorage();
        this.chaveMiniatura = arquivo.chaveMiniatura();
        alterarLegenda(legenda);
    }

    public void alterarLegenda(String legenda) {
        this.legenda = legenda != null && !legenda.isBlank() ? legenda.trim() : null;
    }
}
