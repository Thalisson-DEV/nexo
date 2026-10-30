package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

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
 * A trilha de cada e-mail que o sistema tentou entregar.
 *
 * <p>Existe para responder a uma pergunta que sempre aparece e que log de aplicação
 * responde mal: "o aviso do desconto chegou para quem aprova?". Log rotaciona e some;
 * esta tabela guarda destinatário, provedor, identificador externo e o erro do
 * provedor quando houve — o bastante para conferir no painel do provedor ou para
 * saber que o problema é a caixa da pessoa, não o sistema.</p>
 *
 * <p>Não guarda o corpo da mensagem. Uma retentativa remonta o e-mail a partir da
 * notificação, que continua no banco: assim a segunda tentativa carrega os anexos que
 * existem agora, e a tabela não vira um espelho de tudo que já se escreveu.</p>
 */
@Entity
@Table(name = "logs_email")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogEmail extends BaseEntity {

    /** Teto de tentativas antes de desistir. Além disso é insistir em erro estável. */
    public static final int MAXIMO_TENTATIVAS = 4;

    @Column(name = "id_notificacao", nullable = false, updatable = false)
    private UUID idNotificacao;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID idUsuario;

    @Column(name = "destinatario", nullable = false, updatable = false, length = 320)
    private String destinatario;

    @Column(name = "assunto", nullable = false, updatable = false, length = 300)
    private String assunto;

    /** Gatilho de origem, guardado para que a retentativa saiba onde buscar anexos. */
    @Column(name = "chave_gatilho", updatable = false, length = 80)
    private String chaveGatilho;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusEnvioEmail status = StatusEnvioEmail.PENDENTE;

    @Column(name = "provedor", length = 20)
    private String provedor;

    /** Identificador devolvido pelo provedor, quando ele devolve um. */
    @Column(name = "id_externo", length = 120)
    private String idExterno;

    @Column(name = "tentativas", nullable = false)
    private int tentativas = 0;

    @Column(name = "ultimo_erro", length = 1000)
    private String ultimoErro;

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    public LogEmail(UUID idNotificacao, UUID idUsuario, String destinatario,
                    String assunto, String chaveGatilho) {
        if (idNotificacao == null || idUsuario == null) {
            throw new InvarianteVioladaException(
                    "O registro de e-mail precisa da notificação e do destinatário.");
        }
        if (destinatario == null || destinatario.isBlank()) {
            throw new InvarianteVioladaException("O registro de e-mail precisa de um endereço.");
        }
        this.idNotificacao = idNotificacao;
        this.idUsuario = idUsuario;
        this.destinatario = destinatario.trim();
        this.assunto = limitar(assunto, 300);
        this.chaveGatilho = chaveGatilho;
        this.status = StatusEnvioEmail.PENDENTE;
    }

    public void registrarEnvio(String provedor, String idExterno, OffsetDateTime quando) {
        this.tentativas++;
        this.status = StatusEnvioEmail.ENVIADO;
        this.provedor = provedor;
        this.idExterno = idExterno;
        this.enviadoEm = quando;
        this.ultimoErro = null;
    }

    /**
     * Registra a recusa e decide se ainda vale insistir. Passado o teto, a linha vira
     * {@code DESISTIU} — o que tira o registro da varredura sem apagá-lo, porque a
     * pergunta "por que fulano não recebeu?" continua tendo resposta.
     */
    public void registrarFalha(String provedor, String erro) {
        this.tentativas++;
        this.provedor = provedor;
        this.ultimoErro = limitar(erro, 1000);
        this.status = this.tentativas >= MAXIMO_TENTATIVAS
                ? StatusEnvioEmail.DESISTIU : StatusEnvioEmail.FALHOU;
    }

    public boolean podeRetentar() {
        return this.status != StatusEnvioEmail.ENVIADO
                && this.status != StatusEnvioEmail.DESISTIU
                && this.tentativas < MAXIMO_TENTATIVAS;
    }

    private static String limitar(String valor, int limite) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
