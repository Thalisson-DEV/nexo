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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * O que a frota sabe de um veículo além do cadastro do RH.
 *
 * <p>Uma ficha por veículo, com o mesmo id do RH em {@code idVeiculo}. Placa e base
 * são uma projeção do RH, atualizada por evento — nunca editadas aqui.</p>
 */
@Entity
@Table(name = "frota_fichas_veiculo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FichaVeiculo extends BaseEntity {

    private static final int ANO_MINIMO = 1950;

    @Column(name = "id_veiculo", nullable = false, unique = true, updatable = false)
    private UUID idVeiculo;

    @Column(name = "placa", nullable = false, length = 10)
    private String placa;

    @Column(name = "id_base_operacional")
    private UUID idBaseOperacional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_veiculo")
    private TipoVeiculo tipoVeiculo;

    @Column(name = "ano_fabricacao")
    private Integer anoFabricacao;

    @Column(name = "ano_modelo")
    private Integer anoModelo;

    @Column(name = "chassi", length = 30)
    private String chassi;

    @Column(name = "renavam", length = 20)
    private String renavam;

    @Column(name = "cor", length = 40)
    private String cor;

    @Enumerated(EnumType.STRING)
    @Column(name = "combustivel", length = 20)
    private Combustivel combustivel;

    @Column(name = "capacidade_tanque_litros", precision = 8, scale = 2)
    private BigDecimal capacidadeTanqueLitros;

    @Column(name = "consumo_referencia_km_l", precision = 6, scale = 2)
    private BigDecimal consumoReferenciaKmL;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoOperacional situacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false, length = 20)
    private RegimeVeiculo regime;

    @Column(name = "locadora", length = 150)
    private String locadora;

    @Column(name = "custo_mensal_locacao", precision = 12, scale = 2)
    private BigDecimal custoMensalLocacao;

    @Column(name = "inicio_locacao")
    private LocalDate inicioLocacao;

    @Column(name = "fim_locacao")
    private LocalDate fimLocacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor_telemetria", length = 20)
    private ProvedorTelemetria provedorTelemetria;

    @Column(name = "id_rastreador", length = 80)
    private String idRastreador;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    /** Ficha vazia para um veículo recém-cadastrado no RH. */
    public static FichaVeiculo provisionar(UUID idVeiculo, String placa, UUID idBaseOperacional) {
        var ficha = new FichaVeiculo();
        ficha.idVeiculo = Objects.requireNonNull(idVeiculo, "O veículo é obrigatório");
        ficha.situacao = SituacaoOperacional.ATIVO;
        ficha.regime = RegimeVeiculo.PROPRIO;
        ficha.sincronizarCadastro(placa, idBaseOperacional, true);
        return ficha;
    }

    /** Atualiza a projeção do cadastro do RH. */
    public void sincronizarCadastro(String placa, UUID idBaseOperacional, boolean ativoNoRh) {
        this.placa = Textos.obrigatorio(placa, "placa", 10).toUpperCase(Locale.ROOT);
        this.idBaseOperacional = idBaseOperacional;
        if (ativoNoRh && !isAtivo()) {
            ativar();
        } else if (!ativoNoRh && isAtivo()) {
            desativar();
        }
    }

    public void atualizar(DadosFichaVeiculo dados) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar a ficha de um veículo desativado.");
        }
        Objects.requireNonNull(dados, "Os dados da ficha são obrigatórios");
        if (dados.tipoVeiculo() != null && !dados.tipoVeiculo().isAtivo()
                && !dados.tipoVeiculo().equals(this.tipoVeiculo)) {
            throw new InvarianteVioladaException("O tipo de veículo informado está desativado.");
        }
        validarAnos(dados.anoFabricacao(), dados.anoModelo());
        validarNaoNegativo(dados.capacidadeTanqueLitros(), "capacidade do tanque");
        validarNaoNegativo(dados.consumoReferenciaKmL(), "consumo de referência");

        this.tipoVeiculo = dados.tipoVeiculo();
        this.anoFabricacao = dados.anoFabricacao();
        this.anoModelo = dados.anoModelo();
        this.chassi = maiusculo(Textos.opcional(dados.chassi(), 30));
        this.renavam = Textos.opcional(dados.renavam(), 20);
        this.cor = maiusculo(Textos.opcional(dados.cor(), 40));
        this.combustivel = dados.combustivel();
        this.capacidadeTanqueLitros = dados.capacidadeTanqueLitros();
        this.consumoReferenciaKmL = dados.consumoReferenciaKmL();
        this.situacao = dados.situacao() == null ? SituacaoOperacional.ATIVO : dados.situacao();
        definirRegime(dados);
        definirTelemetria(dados.provedorTelemetria(), dados.idRastreador());
        this.observacao = Textos.opcional(dados.observacao(), 1000);
    }

    public boolean isLocado() {
        return regime == RegimeVeiculo.LOCADO;
    }

    private void definirRegime(DadosFichaVeiculo dados) {
        this.regime = dados.regime() == null ? RegimeVeiculo.PROPRIO : dados.regime();
        if (regime == RegimeVeiculo.PROPRIO) {
            this.locadora = null;
            this.custoMensalLocacao = null;
            this.inicioLocacao = null;
            this.fimLocacao = null;
            return;
        }
        this.locadora = Textos.obrigatorio(dados.locadora(), "nome da locadora", 150);
        validarNaoNegativo(dados.custoMensalLocacao(), "custo mensal da locação");
        if (dados.inicioLocacao() != null && dados.fimLocacao() != null
                && dados.fimLocacao().isBefore(dados.inicioLocacao())) {
            throw new InvarianteVioladaException("O fim da locação não pode ser anterior ao início.");
        }
        this.custoMensalLocacao = dados.custoMensalLocacao();
        this.inicioLocacao = dados.inicioLocacao();
        this.fimLocacao = dados.fimLocacao();
    }

    private void definirTelemetria(ProvedorTelemetria provedor, String rastreador) {
        String id = Textos.opcional(rastreador, 80);
        if ((provedor == null) != (id == null)) {
            throw new InvarianteVioladaException(
                    "Informe o provedor de telemetria e o id do rastreador juntos, ou nenhum dos dois.");
        }
        this.provedorTelemetria = provedor;
        this.idRastreador = id;
    }

    private static void validarAnos(Integer fabricacao, Integer modelo) {
        int limite = Year.now().getValue() + 1;
        if (fabricacao != null && (fabricacao < ANO_MINIMO || fabricacao > limite)) {
            throw new InvarianteVioladaException("Ano de fabricação inválido.");
        }
        if (modelo != null && (modelo < ANO_MINIMO || modelo > limite + 1)) {
            throw new InvarianteVioladaException("Ano do modelo inválido.");
        }
        if (fabricacao != null && modelo != null && (modelo < fabricacao || modelo > fabricacao + 1)) {
            throw new InvarianteVioladaException(
                    "O ano do modelo deve ser igual ao de fabricação ou o ano seguinte.");
        }
    }

    private static void validarNaoNegativo(BigDecimal valor, String campo) {
        if (valor != null && valor.signum() < 0) {
            throw new InvarianteVioladaException("O campo " + campo + " não pode ser negativo.");
        }
    }

    private static String maiusculo(String valor) {
        return valor == null ? null : valor.toUpperCase(Locale.ROOT);
    }
}
