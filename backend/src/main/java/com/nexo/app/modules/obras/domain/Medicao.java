package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Serviços concluídos de uma obra levados à cobrança. O item medido fica como Medido e não entra em outra medição
 * enquanto esta não for estornada. Quantidade e valor vêm dos apontamentos, com o valor congelado na programação.
 *
 * <p>Estornar não apaga: a medição fica no histórico como estornada, com a justificativa, e os itens voltam a
 * concluídos para entrar numa próxima medição.</p>
 */
@Entity
@Table(name = "medicoes_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medicao extends BaseEntity {

    /** Item concluído com o que foi apontado nele: quantidade, valor e o dia do último apontamento. */
    public record ItemAMedir(ItemPlanejado item, BigDecimal quantidade, BigDecimal valorTotal, LocalDate concluidoEm) {
    }

    @Column(name = "numero", nullable = false, updatable = false)
    private Long numero;

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoMedicao tipo;

    /** Primeiro dia do mês de referência. */
    @Column(name = "competencia", nullable = false, updatable = false)
    private LocalDate competencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusMedicao status = StatusMedicao.GERADA;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "justificativa_estorno", length = 1000)
    private String justificativaEstorno;

    @Column(name = "data_estorno")
    private OffsetDateTime dataEstorno;

    @Column(name = "usuario_estorno", length = 255)
    private String usuarioEstorno;

    @OneToMany(mappedBy = "medicao", cascade = CascadeType.ALL)
    private List<MedicaoItem> itens = new ArrayList<>();

    /**
     * Na medição por ciclo entra só o que foi concluído até o fim da competência; a parcial usa a competência do
     * dia em que é gerada. O número só é tirado depois de validar, para tentativa recusada não deixar buraco.
     */
    public static Medicao gerar(LongSupplier numero, UUID idObra, TipoMedicao tipo, YearMonth competencia, LocalDate hoje,
                                List<ItemAMedir> itens, String observacao) {
        Objects.requireNonNull(tipo, "tipo");
        if (competencia == null) {
            throw new InvarianteVioladaException("Informe a competência da medição.");
        }
        if (competencia.isAfter(YearMonth.from(hoje))) {
            throw new InvarianteVioladaException("A competência da medição não pode ser no futuro.");
        }
        if (itens == null || itens.isEmpty()) {
            throw new InvarianteVioladaException("Não há serviço concluído pendente de medição nesta seleção.");
        }
        if (tipo == TipoMedicao.CICLO) {
            LocalDate fim = competencia.atEndOfMonth();
            itens.stream().filter(i -> i.concluidoEm() != null && i.concluidoEm().isAfter(fim)).findFirst()
                    .ifPresent(i -> {
                        throw new InvarianteVioladaException("O serviço " + i.item().getDescricao()
                                + " foi concluído depois do fim do ciclo e fica para o próximo.");
                    });
        }
        Medicao medicao = new Medicao();
        medicao.idObra = Objects.requireNonNull(idObra);
        medicao.tipo = tipo;
        medicao.competencia = competencia.atDay(1);
        medicao.observacao = observacao != null && !observacao.isBlank() ? observacao.trim() : null;
        for (ItemAMedir item : itens) {
            if (!Objects.equals(item.item().getKitAplicado().getIdObra(), idObra)) {
                throw new InvarianteVioladaException("O serviço " + item.item().getDescricao() + " é de outra obra.");
            }
            medicao.itens.add(new MedicaoItem(medicao, item));
        }
        medicao.numero = numero.getAsLong();
        medicao.valorTotal = medicao.itens.stream()
                .map(MedicaoItem::getValorTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return medicao;
    }

    public YearMonth getCompetenciaMes() {
        return YearMonth.from(competencia);
    }

    public boolean estornada() {
        return status == StatusMedicao.ESTORNADA;
    }

    public void estornar(String justificativa, String usuario, OffsetDateTime quando) {
        if (estornada()) {
            throw new InvarianteVioladaException("A medição #" + numero + " já foi estornada.");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new InvarianteVioladaException("Informe a justificativa do estorno.");
        }
        itens.stream().filter(MedicaoItem::isAtivo).forEach(MedicaoItem::estornar);
        this.status = StatusMedicao.ESTORNADA;
        this.justificativaEstorno = justificativa.trim();
        this.usuarioEstorno = usuario;
        this.dataEstorno = quando;
    }
}
