package com.nexo.app.modules.sst.domain;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sst_alertas_documento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaValidadeDocumentoSst extends BaseEntity {

    @Column(name = "id_documento", nullable = false)
    private UUID idDocumento;

    @Column(name = "dias_antecedencia", nullable = false)
    private int diasAntecedencia;

    @Column(name = "data_validade", nullable = false)
    private LocalDate dataValidade;

    @Column(name = "data_avaliacao", nullable = false)
    private LocalDate dataAvaliacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoAlertaDocumentoSst situacao = SituacaoAlertaDocumentoSst.ATIVO;

    @Column(name = "data_superacao")
    private OffsetDateTime dataSuperacao;

    public AlertaValidadeDocumentoSst(UUID idDocumento, int diasAntecedencia,
                                      LocalDate dataValidade, LocalDate dataAvaliacao) {
        if (idDocumento == null || dataValidade == null || dataAvaliacao == null) {
            throw new InvarianteVioladaException("Documento, validade e data de avaliação são obrigatórios.");
        }
        if (diasAntecedencia <= 0) {
            throw new InvarianteVioladaException("A antecedência do alerta deve ser positiva.");
        }
        this.idDocumento = idDocumento;
        this.diasAntecedencia = diasAntecedencia;
        this.dataValidade = dataValidade;
        this.dataAvaliacao = dataAvaliacao;
    }

    public void superar() {
        if (situacao == SituacaoAlertaDocumentoSst.ATIVO) {
            situacao = SituacaoAlertaDocumentoSst.SUPERADO;
            dataSuperacao = OffsetDateTime.now();
        }
    }
}
