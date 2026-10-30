package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

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

/**
 * Uma entrega: a notificação como um usuário específico a vê na sua caixa.
 *
 * <p>É uma linha por destinatário (fan-out na escrita), e não um registro
 * compartilhado com estado por usuário. A escolha é deliberada: o estado de leitura
 * é individual, o texto pode ser personalizado por destinatário e a consulta que
 * mais roda no sistema — o contador do sininho — vira uma contagem de índice
 * parcial em vez de um join. O mural de comunicados faz o oposto (fan-out na
 * leitura), porque lá o conteúdo é o mesmo para todos e a audiência pode ser a
 * empresa inteira.</p>
 *
 * <p>O conteúdo é imutável depois de criado. O que muda é apenas a relação do
 * usuário com ela: lida, não lida, arquivada.</p>
 */
@Entity
@Table(name = "notificacoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacao extends BaseEntity {

    private static final int LIMITE_TITULO = 150;
    private static final int LIMITE_CORPO = 1000;
    private static final int LIMITE_DEEPLINK = 500;
    private static final int LIMITE_CHAVE = 200;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, updatable = false, length = 20)
    private OrigemNotificacao origem;

    /** Regra que produziu a entrega, quando a origem é o motor de regras. */
    @Column(name = "id_regra", updatable = false)
    private UUID idRegra;

    /** Agregado que motivou a notificação (o desconto, o desvio, o comunicado). */
    @Column(name = "id_referencia", updatable = false)
    private UUID idReferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, updatable = false, length = 30)
    private CategoriaNotificacao categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, updatable = false, length = 20)
    private PrioridadeNotificacao prioridade;

    @Column(name = "titulo", nullable = false, updatable = false, length = LIMITE_TITULO)
    private String titulo;

    @Column(name = "corpo", nullable = false, updatable = false, length = LIMITE_CORPO)
    private String corpo;

    /** Rota do frontend que resolve o assunto (ex.: {@code /descontos/1042}). */
    @Column(name = "deeplink", updatable = false, length = LIMITE_DEEPLINK)
    private String deeplink;

    /**
     * Dados estruturados do assunto (número do documento, valor, quantidade
     * agregada). Ficam em JSONB porque cada gatilho carrega um conjunto próprio e
     * criar coluna para cada um engessaria o motor de regras.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> contexto;

    @Column(name = "chave_idempotencia", nullable = false, updatable = false, length = LIMITE_CHAVE)
    private String chaveIdempotencia;

    @Column(name = "lida_em")
    private OffsetDateTime lidaEm;

    @Column(name = "arquivada_em")
    private OffsetDateTime arquivadaEm;

    /** Depois desta data a notificação some da caixa — usada por avisos temporários. */
    @Column(name = "expira_em", updatable = false)
    private OffsetDateTime expiraEm;

    public Notificacao(UUID idUsuario, OrigemNotificacao origem, UUID idRegra, UUID idReferencia,
                       CategoriaNotificacao categoria, PrioridadeNotificacao prioridade,
                       String titulo, String corpo, String deeplink, Map<String, Object> contexto,
                       String chaveIdempotencia, OffsetDateTime expiraEm) {
        if (idUsuario == null) {
            throw new InvarianteVioladaException("A notificação precisa de um destinatário.");
        }
        this.idUsuario = idUsuario;
        this.origem = exigirPreenchido(origem, "A origem da notificação é obrigatória.");
        this.categoria = exigirPreenchido(categoria, "A categoria da notificação é obrigatória.");
        this.prioridade = exigirPreenchido(prioridade, "A prioridade da notificação é obrigatória.");
        this.titulo = exigirTexto(titulo, LIMITE_TITULO, "O título da notificação");
        this.corpo = exigirTexto(corpo, LIMITE_CORPO, "O corpo da notificação");
        this.chaveIdempotencia = exigirTexto(chaveIdempotencia, LIMITE_CHAVE, "A chave de idempotência");
        this.deeplink = limitarOpcional(deeplink, LIMITE_DEEPLINK, "O deeplink da notificação");
        this.idRegra = idRegra;
        this.idReferencia = idReferencia;
        this.contexto = contexto;
        this.expiraEm = expiraEm;
    }

    /**
     * Marca como lida. Idempotente: reabrir a notificação não reescreve o instante
     * da primeira leitura, que é o dado com valor de auditoria.
     */
    public void marcarLida() {
        if (this.lidaEm == null) {
            this.lidaEm = OffsetDateTime.now();
        }
    }

    /** Devolve ao estado não lida, para o usuário que quer voltar ao assunto depois. */
    public void marcarNaoLida() {
        this.lidaEm = null;
    }

    /**
     * Tira da caixa sem apagar. Arquivar implica lida — não faria sentido a
     * notificação sumir da lista e continuar contando no sininho.
     */
    public void arquivar() {
        if (this.arquivadaEm == null) {
            this.arquivadaEm = OffsetDateTime.now();
        }
        marcarLida();
    }

    public boolean isLida() {
        return this.lidaEm != null;
    }

    public boolean isArquivada() {
        return this.arquivadaEm != null;
    }

    public boolean isExpirada() {
        return this.expiraEm != null && this.expiraEm.isBefore(OffsetDateTime.now());
    }

    private static <T> T exigirPreenchido(T valor, String mensagem) {
        if (valor == null) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor;
    }

    private static String exigirTexto(String valor, int limite, String descricao) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException(descricao + " é obrigatório.");
        }
        return limitar(valor, limite, descricao);
    }

    private static String limitarOpcional(String valor, int limite, String descricao) {
        return StringUtils.hasText(valor) ? limitar(valor, limite, descricao) : null;
    }

    private static String limitar(String valor, int limite, String descricao) {
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new InvarianteVioladaException(
                    descricao + " excede o limite de " + limite + " caracteres.");
        }
        return texto;
    }
}
