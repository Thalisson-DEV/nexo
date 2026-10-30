package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** Resultado versionado da etapa leve de normalização. Nunca substitui a transcrição original. */
@Entity
@Table(name = "normalizacoes_transcricao_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NormalizacaoTranscricaoSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "id_revisao_transcricao", nullable = false, updatable = false)
    private UUID idRevisaoTranscricao;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(name = "hash_insumos", nullable = false, updatable = false, length = 64)
    private String hashInsumos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusNormalizacaoTranscricaoSst status;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "conteudo_estruturado", columnDefinition = "text")
    private String conteudoEstruturado;

    @Column(name = "sha256_conteudo", length = 64)
    private String sha256Conteudo;

    @Column(name = "versao_modelo", nullable = false, updatable = false, length = 80)
    private String versaoModelo;

    @Column(name = "id_solicitante", nullable = false, updatable = false)
    private UUID idSolicitante;

    @Column(name = "codigo_falha", length = 60)
    private String codigoFalha;

    @Column(name = "concluida_em")
    private OffsetDateTime concluidaEm;

    @Column(name = "confirmada_em")
    private OffsetDateTime confirmadaEm;

    @Column(name = "id_usuario_confirmacao")
    private UUID idUsuarioConfirmacao;

    public NormalizacaoTranscricaoSst(UUID idReuniao, UUID idRevisaoTranscricao, int numero,
                                      UUID clientRequestId, String hashInsumos,
                                      String versaoModelo, UUID idSolicitante) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.idRevisaoTranscricao = Objects.requireNonNull(idRevisaoTranscricao,
                "A revisão original é obrigatória.");
        if (numero < 1) throw new InvarianteVioladaException("O número da normalização deve ser positivo.");
        this.numero = numero;
        this.clientRequestId = Objects.requireNonNull(clientRequestId, "clientRequestId é obrigatório.");
        this.hashInsumos = exigirHash(hashInsumos, "O hash dos insumos é inválido.");
        this.versaoModelo = texto(versaoModelo, 80, "A versão do modelo é obrigatória.");
        this.idSolicitante = Objects.requireNonNull(idSolicitante, "O solicitante é obrigatório.");
        this.status = StatusNormalizacaoTranscricaoSst.EM_PROCESSAMENTO;
    }

    public void concluir(String conteudoEstruturado, String sha256Conteudo) {
        exigirProcessando();
        this.conteudoEstruturado = texto(conteudoEstruturado, 80_000,
                "O conteúdo normalizado é obrigatório.");
        this.sha256Conteudo = exigirHash(sha256Conteudo, "O hash da normalização é inválido.");
        this.status = StatusNormalizacaoTranscricaoSst.RASCUNHO;
        this.concluidaEm = OffsetDateTime.now();
    }

    public void falhar(String codigo) {
        exigirProcessando();
        if (codigo == null || !codigo.matches("[A-Z0-9_]{3,60}")) {
            throw new InvarianteVioladaException("O código da falha é inválido.");
        }
        this.codigoFalha = codigo;
        this.status = StatusNormalizacaoTranscricaoSst.FALHA;
        this.concluidaEm = OffsetDateTime.now();
    }

    public void confirmar(UUID idUsuario) {
        if (status != StatusNormalizacaoTranscricaoSst.RASCUNHO) {
            throw new InvarianteVioladaException("Somente uma normalização em rascunho pode ser confirmada.");
        }
        this.idUsuarioConfirmacao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem confirmou a normalização.");
        this.confirmadaEm = OffsetDateTime.now();
        this.status = StatusNormalizacaoTranscricaoSst.CONFIRMADA;
    }

    public boolean mesmosInsumos(String hash) {
        return hashInsumos.equals(hash);
    }

    private void exigirProcessando() {
        if (status != StatusNormalizacaoTranscricaoSst.EM_PROCESSAMENTO) {
            throw new InvarianteVioladaException("A tentativa de normalização já foi encerrada.");
        }
    }

    private static String exigirHash(String valor, String mensagem) {
        if (valor == null || !valor.matches("[0-9a-f]{64}")) throw new InvarianteVioladaException(mensagem);
        return valor;
    }

    private static String texto(String valor, int limite, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String normalizado = valor.trim();
        if (normalizado.length() > limite) throw new InvarianteVioladaException("O texto excede " + limite + " caracteres.");
        return normalizado;
    }
}
