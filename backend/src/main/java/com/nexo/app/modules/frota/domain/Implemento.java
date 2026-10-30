package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Year;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Equipamento que se acopla a um veículo (cesto aéreo, guindauto, reboque...).
 *
 * <p>Tem identidade própria porque troca de veículo ao longo da vida: o histórico de
 * onde esteve fica em {@link AcoplamentoImplemento}.</p>
 */
@Entity
@Table(name = "frota_implementos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Implemento extends BaseEntity {

    @Column(name = "identificacao", nullable = false, length = 60)
    private String identificacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_implemento", nullable = false)
    private TipoImplemento tipoImplemento;

    @Column(name = "fabricante", length = 80)
    private String fabricante;

    @Column(name = "modelo", length = 100)
    private String modelo;

    @Column(name = "numero_serie", length = 80)
    private String numeroSerie;

    @Column(name = "ano_fabricacao")
    private Integer anoFabricacao;

    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoOperacional situacao;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    public Implemento(String identificacao, TipoImplemento tipo, String fabricante, String modelo,
                      String numeroSerie, Integer anoFabricacao, UUID idBaseOperacional, String observacao) {
        this.situacao = SituacaoOperacional.ATIVO;
        preencher(identificacao, tipo, fabricante, modelo, numeroSerie, anoFabricacao, idBaseOperacional, observacao);
    }

    public void atualizar(String identificacao, TipoImplemento tipo, String fabricante, String modelo,
                          String numeroSerie, Integer anoFabricacao, UUID idBaseOperacional,
                          SituacaoOperacional situacao, String observacao) {
        exigirAtivo();
        preencher(identificacao, tipo, fabricante, modelo, numeroSerie, anoFabricacao, idBaseOperacional, observacao);
        this.situacao = situacao == null ? this.situacao : situacao;
    }

    /** Implemento baixado ou desativado não pode ser acoplado. */
    public void exigirAcoplavel() {
        exigirAtivo();
        if (situacao == SituacaoOperacional.BAIXADO) {
            throw new InvarianteVioladaException("Implemento baixado não pode ser acoplado.");
        }
    }

    private void exigirAtivo() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("O implemento está desativado.");
        }
    }

    private void preencher(String identificacao, TipoImplemento tipo, String fabricante, String modelo,
                           String numeroSerie, Integer anoFabricacao, UUID idBaseOperacional, String observacao) {
        Objects.requireNonNull(tipo, "O tipo de implemento é obrigatório");
        if (!tipo.isAtivo() && !tipo.equals(this.tipoImplemento)) {
            throw new InvarianteVioladaException("O tipo de implemento informado está desativado.");
        }
        if (idBaseOperacional == null) {
            throw new InvarianteVioladaException("Informe a base operacional do implemento.");
        }
        int limite = Year.now().getValue() + 1;
        if (anoFabricacao != null && (anoFabricacao < 1950 || anoFabricacao > limite)) {
            throw new InvarianteVioladaException("Ano de fabricação inválido.");
        }
        this.identificacao = Textos.obrigatorio(identificacao, "identificação do implemento", 60)
                .toUpperCase(Locale.ROOT);
        this.tipoImplemento = tipo;
        this.fabricante = maiusculo(Textos.opcional(fabricante, 80));
        this.modelo = maiusculo(Textos.opcional(modelo, 100));
        this.numeroSerie = Textos.opcional(numeroSerie, 80);
        this.anoFabricacao = anoFabricacao;
        this.idBaseOperacional = idBaseOperacional;
        this.observacao = Textos.opcional(observacao, 1000);
    }

    private static String maiusculo(String valor) {
        return valor == null ? null : valor.toUpperCase(Locale.ROOT);
    }
}
