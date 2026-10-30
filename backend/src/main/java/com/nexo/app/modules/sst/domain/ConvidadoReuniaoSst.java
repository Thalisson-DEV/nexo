package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Pessoa convidada para o encontro, sem adquirir vínculo com a composição. */
@Entity
@Table(name = "convidados_reuniao_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConvidadoReuniaoSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "id_colaborador", updatable = false)
    private UUID idColaborador;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome_snapshot", nullable = false, updatable = false, length = 1024)
    private String nomeSnapshot;

    @Column(name = "funcao_snapshot", updatable = false, length = 160)
    private String funcaoSnapshot;

    @Column(name = "organizacao_snapshot", updatable = false, length = 180)
    private String organizacaoSnapshot;

    @Column(name = "presente", nullable = false)
    private boolean presente;

    public ConvidadoReuniaoSst(UUID idReuniao, UUID idColaborador, String nomeSnapshot,
                               String funcaoSnapshot, String organizacaoSnapshot, boolean presente) {
        if (idReuniao == null) throw new InvarianteVioladaException("A reunião do convidado é obrigatória.");
        if (nomeSnapshot == null || nomeSnapshot.isBlank()) {
            throw new InvarianteVioladaException("Informe o nome do convidado.");
        }
        this.idReuniao = idReuniao;
        this.idColaborador = idColaborador;
        this.nomeSnapshot = nomeSnapshot.trim();
        this.funcaoSnapshot = opcional(funcaoSnapshot);
        this.organizacaoSnapshot = opcional(organizacaoSnapshot);
        this.presente = presente;
    }

    public boolean pertenceA(UUID idReuniao) {
        return this.idReuniao.equals(idReuniao);
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
