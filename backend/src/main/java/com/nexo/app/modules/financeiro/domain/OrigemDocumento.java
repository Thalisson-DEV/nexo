package com.nexo.app.modules.financeiro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Solicitação de onde o documento veio, quando veio de alguma.
 *
 * <p>É uma referência solta, não uma associação: o documento é agregado próprio e
 * sobrevive ao ciclo da solicitação — a nota fiscal chega depois da aprovação,
 * quando a solicitação já não admite alteração. Amarrar os dois com chave
 * estrangeira gerenciada faria o documento herdar as travas de estado dela, que é
 * exatamente o problema que o repositório resolve.</p>
 *
 * <p>O número viaja junto e denormalizado porque é por ele que as equipes se
 * referem à solicitação; sem ele, listar mil documentos exigiria mil consultas só
 * para escrever "Compra nº 1042" na tela.</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrigemDocumento {

    public static final OrigemDocumento VAZIA = new OrigemDocumento();

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_tipo", length = 20)
    private TipoSolicitacao tipo;

    @Column(name = "origem_solicitacao_id")
    private UUID solicitacaoId;

    @Column(name = "origem_numero")
    private Long numero;

    public OrigemDocumento(TipoSolicitacao tipo, UUID solicitacaoId, Long numero) {
        this.tipo = Objects.requireNonNull(tipo, "O tipo da solicitação de origem é obrigatório.");
        this.solicitacaoId = Objects.requireNonNull(solicitacaoId, "A solicitação de origem é obrigatória.");
        this.numero = numero;
    }

    /** Origem de uma solicitação já carregada, que é de onde vem o número. */
    public static OrigemDocumento de(SolicitacaoBase solicitacao) {
        return new OrigemDocumento(solicitacao.getTipoSolicitacao(), solicitacao.getId(), solicitacao.getNumero());
    }

    public static OrigemDocumento semVinculo() {
        return VAZIA;
    }

    /** {@code true} quando o documento é avulso — recebido sem passar por solicitação. */
    public boolean vazia() {
        return solicitacaoId == null;
    }
}
