package com.nexo.app.modules.automacao.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Um pedido de decisão aberto por uma automação. A execução fica esperando até alguém
 * da lista de aprovadores responder (ou o prazo vencer).
 */
@Entity
@Table(name = "automacao_aprovacoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AprovacaoAutomacao {

    public static final String PENDENTE = "PENDENTE";
    public static final String DECIDIDA = "DECIDIDA";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_execucao", nullable = false, updatable = false)
    private UUID idExecucao;

    @Column(name = "id_automacao", nullable = false, updatable = false)
    private UUID idAutomacao;

    @Column(name = "no_id", nullable = false, length = 40, updatable = false)
    private String noId;

    @Column(name = "iteracao", nullable = false, length = 400, updatable = false)
    private String iteracao;

    @Column(name = "titulo", nullable = false, length = 200, updatable = false)
    private String titulo;

    @Column(name = "detalhes", updatable = false)
    private String detalhes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opcoes", nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<String> opcoes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aprovadores", nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<String> aprovadores;

    @Column(name = "status", nullable = false, length = 20)
    private String status = PENDENTE;

    @Column(name = "resposta", length = 120)
    private String resposta;

    @Column(name = "comentario", length = 1000)
    private String comentario;

    @Column(name = "respondida_por")
    private UUID respondidaPor;

    @Column(name = "respondida_em")
    private OffsetDateTime respondidaEm;

    @Column(name = "expira_em", updatable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;

    public AprovacaoAutomacao(UUID idExecucao, UUID idAutomacao, String noId, String iteracao, String titulo,
                              String detalhes, List<String> opcoes, List<UUID> aprovadores, OffsetDateTime expiraEm,
                              OffsetDateTime agora) {
        this.id = UUID.randomUUID();
        this.idExecucao = idExecucao;
        this.idAutomacao = idAutomacao;
        this.noId = noId;
        this.iteracao = iteracao == null ? "" : iteracao;
        this.titulo = titulo.length() > 200 ? titulo.substring(0, 200) : titulo;
        this.detalhes = detalhes;
        this.opcoes = List.copyOf(opcoes);
        this.aprovadores = aprovadores.stream().map(UUID::toString).toList();
        this.expiraEm = expiraEm;
        this.criadaEm = agora;
    }

    public boolean podeResponder(UUID usuarioId) {
        return usuarioId != null && aprovadores.contains(usuarioId.toString());
    }

    public void responder(UUID usuarioId, String opcao, String comentario, OffsetDateTime agora) {
        if (!PENDENTE.equals(status)) throw new InvarianteVioladaException("Esta aprovação já foi encerrada.");
        if (!podeResponder(usuarioId)) throw new InvarianteVioladaException("Você não está entre os aprovadores deste pedido.");
        String escolhida = opcoes.stream().filter(o -> o.equalsIgnoreCase(opcao == null ? "" : opcao.strip())).findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Escolha uma das opções: " + String.join(", ", opcoes)));
        this.status = DECIDIDA;
        this.resposta = escolhida;
        this.comentario = comentario == null ? null : (comentario.length() > 1000 ? comentario.substring(0, 1000) : comentario);
        this.respondidaPor = usuarioId;
        this.respondidaEm = agora;
    }

    public void encerrar(String novoStatus) {
        if (PENDENTE.equals(status)) this.status = novoStatus;
    }
}
