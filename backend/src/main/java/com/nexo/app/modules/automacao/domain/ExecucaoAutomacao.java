package com.nexo.app.modules.automacao.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Uma execução de automação. É registro de auditoria em si — com a versão que rodou, a
 * saída congelada do gatilho e uma etapa por passo —, por isso não passa pela auditoria
 * genérica nem por soft delete.
 */
@Entity
@Table(name = "automacao_execucoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoAutomacao {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_automacao", nullable = false, updatable = false)
    private UUID idAutomacao;

    @Column(name = "id_versao", nullable = false, updatable = false)
    private UUID idVersao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusExecucao status = StatusExecucao.NA_FILA;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo", nullable = false, length = 20, updatable = false)
    private ModoExecucao modo;

    @Column(name = "chave_disparo", nullable = false, length = 200, updatable = false)
    private String chaveDisparo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saida_gatilho", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> saidaGatilho;

    @Column(name = "id_referencia", updatable = false)
    private UUID idReferencia;

    @Column(name = "iniciada_por", updatable = false)
    private UUID iniciadaPor;

    @Column(name = "email_testador", length = 200, updatable = false)
    private String emailTestador;

    @Column(name = "lease_ate")
    private OffsetDateTime leaseAte;

    @Column(name = "retomar_em")
    private OffsetDateTime retomarEm;

    @Column(name = "aguardando", length = 30)
    private String aguardando;

    @Column(name = "cancelamento_pedido", nullable = false)
    private boolean cancelamentoPedido;

    @Column(name = "erro", length = 2000)
    private String erro;

    @Column(name = "mensagem", length = 500)
    private String mensagem;

    @Column(name = "passos_executados", nullable = false)
    private int passosExecutados;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "iniciada_em")
    private OffsetDateTime iniciadaEm;

    @Column(name = "finalizada_em")
    private OffsetDateTime finalizadaEm;

    public ExecucaoAutomacao(UUID idAutomacao, UUID idVersao, ModoExecucao modo, String chaveDisparo,
                             Map<String, Object> saidaGatilho, UUID idReferencia, UUID iniciadaPor, String emailTestador,
                             OffsetDateTime agora) {
        if (idAutomacao == null || idVersao == null || modo == null || chaveDisparo == null || chaveDisparo.isBlank()) {
            throw new InvarianteVioladaException("Execução de automação incompleta.");
        }
        this.id = UUID.randomUUID();
        this.idAutomacao = idAutomacao;
        this.idVersao = idVersao;
        this.modo = modo;
        this.chaveDisparo = chaveDisparo.length() > 200 ? chaveDisparo.substring(0, 200) : chaveDisparo;
        this.saidaGatilho = saidaGatilho;
        this.idReferencia = idReferencia;
        this.iniciadaPor = iniciadaPor;
        this.emailTestador = emailTestador;
        this.criadaEm = agora;
    }

    /** Execução que nasce já encerrada (ex.: falha antes de começar, dono sem acesso). */
    public void falharAntesDeComecar(String motivo, OffsetDateTime agora) {
        this.status = StatusExecucao.FALHA;
        this.erro = cortar(motivo, 2000);
        this.iniciadaEm = agora;
        this.finalizadaEm = agora;
    }

    public void concluir(StatusExecucao status, OffsetDateTime retomarEm, String aguardando, String erro, String mensagem,
                         int passos, OffsetDateTime agora) {
        this.status = status;
        this.retomarEm = retomarEm;
        this.aguardando = aguardando;
        this.erro = cortar(erro, 2000);
        this.mensagem = cortar(mensagem, 500);
        this.passosExecutados += passos;
        this.leaseAte = null;
        if (status.isFinal()) this.finalizadaEm = agora;
    }

    public void pedirCancelamento() {
        if (status.isFinal()) throw new InvarianteVioladaException("A execução já terminou.");
        this.cancelamentoPedido = true;
    }

    public void encerrarPorTempo(OffsetDateTime agora) {
        this.status = StatusExecucao.TEMPO_ESGOTADO;
        this.erro = "A execução passou do tempo limite configurado na automação.";
        this.finalizadaEm = agora;
        this.leaseAte = null;
    }

    public void cancelarAgora(OffsetDateTime agora) {
        this.status = StatusExecucao.CANCELADA;
        this.mensagem = "Execução cancelada";
        this.finalizadaEm = agora;
        this.leaseAte = null;
    }

    private static String cortar(String texto, int limite) {
        if (texto == null) return null;
        return texto.length() > limite ? texto.substring(0, limite) : texto;
    }
}
