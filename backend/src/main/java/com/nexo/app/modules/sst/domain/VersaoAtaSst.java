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

/** Versão numerada e imutável de uma ata. Correções sempre criam outra entidade. */
@Entity
@Table(name = "versoes_ata_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersaoAtaSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, updatable = false, length = 20)
    private OrigemVersaoAtaSst origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusVersaoAtaSst status;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "conteudo", nullable = false, updatable = false, columnDefinition = "text")
    private String conteudo;

    @Column(name = "sha256_conteudo", nullable = false, updatable = false, length = 64)
    private String sha256Conteudo;

    @Column(name = "id_versao_base", updatable = false)
    private UUID idVersaoBase;

    @Column(name = "id_revisao_transcricao", updatable = false)
    private UUID idRevisaoTranscricao;

    @Column(name = "id_modelo_ata", nullable = false, updatable = false)
    private UUID idModeloAta;

    @Column(name = "revisao_modelo_ata", nullable = false, updatable = false)
    private int revisaoModeloAta;

    @AuditIgnore
    @Column(name = "snapshot_modelo_ata", nullable = false, updatable = false, columnDefinition = "text")
    private String snapshotModeloAta;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "instrucao_correcao", updatable = false, columnDefinition = "text")
    private String instrucaoCorrecao;

    @Column(name = "versao_prompt", updatable = false, length = 40)
    private String versaoPrompt;

    @Column(name = "id_autor_ou_solicitante", nullable = false, updatable = false)
    private UUID idAutorOuSolicitante;

    @Column(name = "aprovada_em")
    private OffsetDateTime aprovadaEm;

    @Column(name = "id_usuario_aprovacao")
    private UUID idUsuarioAprovacao;

    public VersaoAtaSst(
            UUID idReuniao,
            int numero,
            OrigemVersaoAtaSst origem,
            String conteudo,
            String sha256Conteudo,
            UUID idVersaoBase,
            UUID idRevisaoTranscricao,
            UUID idModeloAta,
            int revisaoModeloAta,
            String snapshotModeloAta,
            String instrucaoCorrecao,
            String versaoPrompt,
            UUID idAutorOuSolicitante) {
        if (numero < 1) throw new InvarianteVioladaException("O número da versão da ata deve ser positivo.");
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.numero = numero;
        this.origem = Objects.requireNonNull(origem, "A origem da ata é obrigatória.");
        this.conteudo = exigirTexto(conteudo, 80_000, "Informe o conteúdo da ata.");
        this.sha256Conteudo = exigirHash(sha256Conteudo);
        this.idVersaoBase = idVersaoBase;
        this.idRevisaoTranscricao = idRevisaoTranscricao;
        this.idModeloAta = Objects.requireNonNull(idModeloAta, "O modelo usado na ata é obrigatório.");
        if (revisaoModeloAta < 1) throw new InvarianteVioladaException("A revisão do modelo é inválida.");
        this.revisaoModeloAta = revisaoModeloAta;
        this.snapshotModeloAta = exigirTexto(snapshotModeloAta, 20_000,
                "O snapshot do modelo usado na ata é obrigatório.");
        this.instrucaoCorrecao = opcional(instrucaoCorrecao, 4_000);
        this.versaoPrompt = opcional(versaoPrompt, 40);
        this.idAutorOuSolicitante = Objects.requireNonNull(idAutorOuSolicitante,
                "Não foi possível identificar o autor ou solicitante.");
        this.status = StatusVersaoAtaSst.RASCUNHO;
        if (origem == OrigemVersaoAtaSst.IA && idRevisaoTranscricao == null) {
            throw new InvarianteVioladaException("A ata assistida exige uma transcrição confirmada.");
        }
    }

    public void aprovar(UUID idUsuario) {
        if (status != StatusVersaoAtaSst.RASCUNHO) {
            throw new InvarianteVioladaException("Somente um rascunho pode ser aprovado.");
        }
        this.idUsuarioAprovacao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem aprovou a ata.");
        this.aprovadaEm = OffsetDateTime.now();
        this.status = StatusVersaoAtaSst.APROVADA;
    }

    public boolean pertenceA(UUID reuniaoId) {
        return idReuniao.equals(reuniaoId);
    }

    public boolean aprovada() {
        return status == StatusVersaoAtaSst.APROVADA;
    }

    private static String exigirHash(String valor) {
        if (valor == null || !valor.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash do conteúdo da ata é inválido.");
        }
        return valor;
    }

    private static String exigirTexto(String valor, int maximo, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        String texto = valor.trim();
        if (texto.length() > maximo) {
            throw new InvarianteVioladaException("O texto excede o limite de " + maximo + " caracteres.");
        }
        return texto;
    }

    private static String opcional(String valor, int maximo) {
        return valor == null || valor.isBlank() ? null : exigirTexto(valor, maximo, "O texto é obrigatório.");
    }
}
