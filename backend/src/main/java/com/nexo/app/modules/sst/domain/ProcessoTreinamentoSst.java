package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processos_treinamento_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessoTreinamentoSst extends BaseEntity {
    @Column(nullable = false, length = 80) private String codigo;
    @Column(nullable = false, length = 180) private String nome;
    @Column(name = "versao_referencia", nullable = false) private int versaoReferencia;
    @Column(length = 255) private String fonte;
    @Column(name = "vigente_desde") private LocalDate vigenteDesde;
    @Column(nullable = false) private boolean confirmado;
    @Column(name = "id_usuario_confirmacao") private UUID idUsuarioConfirmacao;
    @Column(name = "confirmado_em") private OffsetDateTime confirmadoEm;

    public ProcessoTreinamentoSst(String codigo, String nome, String fonte) {
        this.codigo = texto(codigo, 80).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        this.nome = texto(nome, 180);
        this.fonte = opcional(fonte, 255);
        this.versaoReferencia = 1;
    }
    public void confirmar(LocalDate vigenteDesde, UUID ator) {
        this.vigenteDesde = Objects.requireNonNull(vigenteDesde, "A vigência é obrigatória.");
        this.idUsuarioConfirmacao = Objects.requireNonNull(ator, "O responsável é obrigatório.");
        this.confirmadoEm = OffsetDateTime.now(); this.confirmado = true;
    }
    private static String texto(String v,int l){ if(v==null||v.isBlank()||v.trim().length()>l) throw new InvarianteVioladaException("Valor obrigatório inválido."); return v.trim(); }
    private static String opcional(String v,int l){ return v==null||v.isBlank()?null:texto(v,l); }
}
