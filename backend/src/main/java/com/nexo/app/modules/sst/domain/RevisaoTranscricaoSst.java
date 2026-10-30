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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Revisão imutável do texto de transcrição. Confirmar não altera o conteúdo. */
@Entity
@Table(name = "revisoes_transcricao_sst", uniqueConstraints =
        @UniqueConstraint(name = "uk_revisao_transcricao_numero", columnNames = {"id_reuniao", "numero"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisaoTranscricaoSst extends BaseEntity {

    public static final int CONTEUDO_MAXIMO = 60_000;

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, updatable = false, length = 30)
    private OrigemTranscricaoSst origem;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "conteudo", nullable = false, updatable = false, columnDefinition = "text")
    private String conteudo;

    @Column(name = "hash_conteudo", nullable = false, updatable = false, length = 64)
    private String hashConteudo;

    @Column(name = "confirmada_em")
    private OffsetDateTime confirmadaEm;

    @Column(name = "id_usuario_confirmacao")
    private UUID idUsuarioConfirmacao;

    public RevisaoTranscricaoSst(UUID idReuniao, int numero, OrigemTranscricaoSst origem,
                                 String conteudo, String hashConteudo) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        if (numero <= 0) throw new InvarianteVioladaException("O número da revisão deve ser positivo.");
        this.numero = numero;
        this.origem = Objects.requireNonNull(origem, "A origem da transcrição é obrigatória.");
        this.conteudo = validarConteudo(conteudo);
        this.hashConteudo = exigirHash(hashConteudo);
    }

    public void confirmar(UUID idUsuario) {
        if (confirmadaEm != null) {
            throw new InvarianteVioladaException("Esta revisão da transcrição já está confirmada.");
        }
        this.idUsuarioConfirmacao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem confirmou a transcrição.");
        this.confirmadaEm = OffsetDateTime.now();
    }

    public boolean confirmada() {
        return confirmadaEm != null;
    }

    public boolean pertenceA(UUID idReuniao) {
        return this.idReuniao.equals(idReuniao);
    }

    private static String validarConteudo(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new InvarianteVioladaException("Informe o texto da transcrição.");
        }
        String texto = conteudo.trim();
        if (texto.length() > CONTEUDO_MAXIMO) {
            throw new InvarianteVioladaException(
                    "A transcrição excede o limite de " + CONTEUDO_MAXIMO + " caracteres.");
        }
        return texto;
    }

    private static String exigirHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash da transcrição é inválido.");
        }
        return hash;
    }
}
