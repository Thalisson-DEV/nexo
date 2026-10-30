package com.nexo.app.modules.assistente.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * IA externa que o usuario conectou ao Nexo (servidor MCP). A IA age em nome do usuario e enxerga so
 * o que ele enxerga; cadastrar e alterar registros depende de {@link #permiteEscrita}. Desconectar
 * invalida todas as credenciais da conexao na requisicao seguinte.
 */
@Entity
@Table(name = "assistente_conexao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConexaoIa extends BaseEntity {

    public static final int NOME_MAXIMO = 120;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 10)
    private TipoConexaoIa tipo;

    @Column(name = "nome", nullable = false, length = NOME_MAXIMO)
    private String nome;

    @Column(name = "client_id", updatable = false, length = 80)
    private String clientId;

    @Column(name = "permite_escrita", nullable = false)
    private boolean permiteEscrita;

    /** Inicio da chave para a pessoa reconhecer qual e qual; nunca a chave inteira. */
    @Column(name = "prefixo_chave", updatable = false, length = 24)
    private String prefixoChave;

    @Column(name = "expira_em")
    private OffsetDateTime expiraEm;

    @Column(name = "revogada_em")
    private OffsetDateTime revogadaEm;

    @Column(name = "revogada_por")
    private UUID revogadaPor;

    @AuditIgnore
    @Column(name = "ultimo_uso_em")
    private OffsetDateTime ultimoUsoEm;

    @AuditIgnore
    @Column(name = "ultimo_ip", length = 64)
    private String ultimoIp;

    @AuditIgnore
    @Column(name = "aplicativo", length = 120)
    private String aplicativo;

    @AuditIgnore
    @Column(name = "total_chamadas", nullable = false)
    private long totalChamadas;

    @Column(name = "conversa_id")
    private UUID conversaId;

    @Column(name = "mensagem_id")
    private UUID mensagemId;

    private ConexaoIa(UUID usuarioId, TipoConexaoIa tipo, String nome, String clientId, boolean permiteEscrita,
            String prefixoChave, OffsetDateTime expiraEm) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.nome = nomeLimpo(nome);
        this.clientId = clientId;
        this.permiteEscrita = permiteEscrita;
        this.prefixoChave = prefixoChave;
        this.expiraEm = expiraEm;
    }

    public static ConexaoIa porLogin(UUID usuarioId, ClienteConexaoIa cliente, boolean permiteEscrita) {
        Objects.requireNonNull(cliente, "cliente");
        return new ConexaoIa(usuarioId, TipoConexaoIa.OAUTH, cliente.getNome(), cliente.getClientId(), permiteEscrita,
                null, null);
    }

    public static ConexaoIa porChave(UUID usuarioId, String nome, boolean permiteEscrita, String prefixoChave,
            OffsetDateTime expiraEm) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("Dê um nome para a chave, como \"Claude do notebook\".");
        }
        if (expiraEm == null || !expiraEm.isAfter(OffsetDateTime.now())) {
            throw new InvarianteVioladaException("Escolha por quanto tempo a chave vale.");
        }
        return new ConexaoIa(usuarioId, TipoConexaoIa.CHAVE, nome, null, permiteEscrita, prefixoChave, expiraEm);
    }

    public boolean isRevogada() {
        return revogadaEm != null;
    }

    public boolean isVencida(OffsetDateTime agora) {
        return expiraEm != null && !expiraEm.isAfter(agora);
    }

    /** Conectada: nao desconectada, nao vencida e nao excluida. */
    public boolean isUtilizavel(OffsetDateTime agora) {
        return isAtivo() && !isRevogada() && !isVencida(agora);
    }

    public boolean pertenceA(UUID usuario) {
        return usuarioId.equals(usuario);
    }

    public void revogar(UUID por) {
        if (isRevogada()) return;
        this.revogadaEm = OffsetDateTime.now();
        this.revogadaPor = por;
    }

    /** Conversa tecnica onde ficam os resultados e a auditoria das chamadas desta conexao. */
    public void associarConversa(UUID conversaId, UUID mensagemId) {
        this.conversaId = Objects.requireNonNull(conversaId, "conversaId");
        this.mensagemId = Objects.requireNonNull(mensagemId, "mensagemId");
    }

    public boolean temConversa() {
        return conversaId != null && mensagemId != null;
    }

    private static String nomeLimpo(String nome) {
        String limpo = nome == null || nome.isBlank() ? "IA conectada" : nome.strip().replaceAll("\\s+", " ");
        return limpo.length() <= NOME_MAXIMO ? limpo : limpo.substring(0, NOME_MAXIMO);
    }
}
