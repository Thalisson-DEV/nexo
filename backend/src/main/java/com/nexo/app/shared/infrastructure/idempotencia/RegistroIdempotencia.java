package com.nexo.app.shared.infrastructure.idempotencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de uma requisição de escrita endereçada por {@code Idempotency-Key}.
 *
 * <p>Deliberadamente <b>não</b> estende {@code BaseEntity}: isto é registro de
 * protocolo HTTP, não entidade de negócio. Não tem soft delete (a linha some no
 * expurgo, e deve mesmo sumir), não tem auditoria JSONB (auditar o registro-de-que-
 * algo-foi-registrado não acrescenta trilha) e não tem optimistic locking — a única
 * concorrência possível é a do INSERT, resolvida pela chave primária.</p>
 *
 * <p>A escrita desta entidade é feita por SQL nativo em
 * {@link RegistroIdempotenciaRepository#reservar}: a exclusão mútua entre duas
 * requisições simultâneas precisa acontecer <i>dentro</i> do INSERT, e não em um
 * SELECT-antes-de-INSERT, cuja janela é exatamente a do duplo clique.</p>
 *
 * @author <b>Thalisson</b>
 */
@Entity
@Getter
@Table(name = "idempotencia_requisicoes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroIdempotencia {

    @Id
    @Column(name = "chave", length = 128, nullable = false, updatable = false)
    private String chave;

    /**
     * Dono da chave. A chave é escolhida pelo cliente; sem este escopo, uma chave
     * adivinhada devolveria a alguém a resposta gravada de outra pessoa. Na releitura,
     * chave de terceiro é tratada como chave inexistente.
     */
    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    @Column(name = "metodo", length = 10, nullable = false)
    private String metodo;

    @Column(name = "caminho", length = 512, nullable = false)
    private String caminho;

    /**
     * SHA-256 de {@code metodo|caminho|corpo}. Nulo quando o corpo não pôde ser
     * digerido sem custo (multipart), caso em que a conferência se limita ao par
     * método/caminho.
     */
    @Column(name = "digest_requisicao", length = 64)
    private String digestRequisicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoIdempotencia estado;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "corpo_resposta")
    private String corpoResposta;

    @Column(name = "data_criacao", nullable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "data_conclusao")
    private OffsetDateTime dataConclusao;

    @Column(name = "data_expiracao", nullable = false)
    private OffsetDateTime dataExpiracao;

    /** Uma requisição só pode ter a resposta repetida depois de ter concluído com sucesso. */
    public boolean concluida() {
        return estado == EstadoIdempotencia.CONCLUIDA;
    }

    /**
     * Confere se a requisição que chega agora é mesmo a que gerou este registro.
     *
     * <p>Devolver a resposta do desconto A para quem pediu o desconto B seria pior que
     * duplicar — seria mentir sobre o que foi gravado. Quando o digest é nulo (multipart),
     * a conferência recai sobre método e caminho, que já isolam o recurso alvo.</p>
     */
    public boolean correspondeA(String metodo, String caminho, String digest) {
        if (!this.metodo.equals(metodo) || !this.caminho.equals(caminho)) {
            return false;
        }
        return this.digestRequisicao == null || digest == null || this.digestRequisicao.equals(digest);
    }

    public boolean pertenceA(UUID idUsuario) {
        return this.idUsuario.equals(idUsuario);
    }
}
