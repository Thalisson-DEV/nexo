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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Kit aplicado a um ponto ou vão, com cópia dos itens do catálogo no momento da aplicação.
 *
 * <p>No ponto, o multiplicador é a quantidade de kits e vale para todos os itens. No vão, é a metragem e vale só
 * para os itens medidos em metro: cabo e lançamento acompanham o comprimento; alças e laços ficam fixos.</p>
 */
@Entity
@Table(name = "kits_aplicados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KitAplicado extends BaseEntity {

    private static final Set<String> UNIDADES_DE_METRO = Set.of("M", "MT", "MTS", "METRO", "METROS");

    /** Item do catálogo já resolvido, com código, descrição e unidade da Ação ou do Material. */
    public record ModeloItem(TipoItemKit tipo, UUID idReferencia, String codigo, String descricao,
                             OperacaoItemKit operacao, String unidade, BigDecimal quantidade) {
    }

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "id_ponto")
    private UUID idPonto;

    @Column(name = "id_vao")
    private UUID idVao;

    @Column(name = "id_kit", nullable = false)
    private UUID idKit;

    @Column(name = "id_contrato", nullable = false)
    private UUID idContrato;

    @Column(name = "codigo_kit", nullable = false, length = 40)
    private String codigoKit;

    @Column(name = "descricao_kit", nullable = false, length = 300)
    private String descricaoKit;

    @Column(name = "grupo_kit", length = 60)
    private String grupoKit;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false, length = 20)
    private OperacaoItemKit operacao;

    @Column(name = "multiplicador", nullable = false, precision = 14, scale = 4)
    private BigDecimal multiplicador;

    @Column(name = "executado_antes", nullable = false)
    private boolean executadoAntes;

    @OneToMany(mappedBy = "kitAplicado", cascade = CascadeType.ALL)
    private List<ItemPlanejado> itens = new ArrayList<>();

    private KitAplicado(UUID idObra, UUID idPonto, UUID idVao, KitConstrutivo kit, OperacaoItemKit operacao,
                        BigDecimal multiplicador, List<ModeloItem> modelos) {
        if (modelos == null || modelos.isEmpty()) {
            throw new InvarianteVioladaException("O kit " + kit.getCodigo() + " está vazio e não pode ser aplicado.");
        }
        validarMultiplicador(multiplicador);
        this.idObra = Objects.requireNonNull(idObra);
        this.idPonto = idPonto;
        this.idVao = idVao;
        this.idKit = kit.getId();
        this.idContrato = kit.getIdContrato();
        this.codigoKit = kit.getCodigo();
        this.descricaoKit = kit.getDescricao();
        this.grupoKit = kit.getGrupo();
        this.operacao = operacao != null ? operacao : OperacaoItemKit.INSTALAR;
        this.multiplicador = multiplicador;
        for (ModeloItem modelo : modelos) {
            OperacaoItemKit operacaoItem = this.operacao == OperacaoItemKit.RETIRAR
                    ? OperacaoItemKit.RETIRAR
                    : modelo.operacao();
            itens.add(new ItemPlanejado(this, modelo, operacaoItem, fator(modelo.unidade())));
        }
    }

    public static KitAplicado noPonto(UUID idObra, UUID idPonto, KitConstrutivo kit, OperacaoItemKit operacao,
                                      BigDecimal quantidade, List<ModeloItem> modelos) {
        return new KitAplicado(idObra, Objects.requireNonNull(idPonto), null, kit, operacao,
                quantidade != null ? quantidade : BigDecimal.ONE, modelos);
    }

    public static KitAplicado noVao(UUID idObra, UUID idVao, KitConstrutivo kit, OperacaoItemKit operacao,
                                    BigDecimal metragem, List<ModeloItem> modelos) {
        return new KitAplicado(idObra, null, Objects.requireNonNull(idVao), kit, operacao,
                metragem != null ? metragem : BigDecimal.ONE, modelos);
    }

    public boolean aplicadoEmVao() {
        return idVao != null;
    }

    public List<ItemPlanejado> getItensAtivos() {
        return itens.stream().filter(ItemPlanejado::isAtivo).toList();
    }

    /**
     * Muda a quantidade de kits ou a metragem e recalcula o previsto. Só vale enquanto nada foi programado.
     */
    public void alterarMultiplicador(BigDecimal multiplicador) {
        validarMultiplicador(multiplicador);
        validarSemAndamento("alterar a quantidade do kit");
        this.multiplicador = multiplicador;
        getItensAtivos().forEach(item -> item.recalcular(fator(item.getUnidade())));
    }

    public void remover() {
        validarSemAndamento("remover o kit");
        getItensAtivos().forEach(ItemPlanejado::desativar);
        desativar();
    }

    public void removerItem(UUID idItem) {
        ItemPlanejado item = buscarItem(idItem);
        item.validarPendente("remover");
        item.desativar();
        if (getItensAtivos().isEmpty()) {
            desativar();
        }
    }

    public void alterarQuantidadeItem(UUID idItem, BigDecimal quantidade) {
        buscarItem(idItem).alterarPrevista(quantidade);
    }

    /**
     * Marca o kit como executado antes do sistema: sai do pendente, não pode ser programado e conta como executado.
     */
    public void marcarExecutadoAntes(boolean executado) {
        if (executado == executadoAntes) {
            return;
        }
        if (executado) {
            validarSemAndamento("marcar o kit como executado antes do sistema");
            getItensAtivos().forEach(ItemPlanejado::concluirAntesDoSistema);
        } else {
            getItensAtivos().forEach(ItemPlanejado::voltarParaPendente);
        }
        this.executadoAntes = executado;
    }

    /** Lista o que impede mexer no kit; vazia quando o kit só tem itens pendentes ou foi executado antes do sistema. */
    public List<ItemPlanejado> itensBloqueantes() {
        if (executadoAntes) {
            return List.of();
        }
        return getItensAtivos().stream().filter(i -> i.getStatus() != StatusItemPlanejado.PENDENTE).toList();
    }

    private BigDecimal fator(String unidade) {
        return !aplicadoEmVao() || medidoEmMetro(unidade) ? multiplicador : BigDecimal.ONE;
    }

    public static boolean medidoEmMetro(String unidade) {
        return unidade != null && UNIDADES_DE_METRO.contains(unidade.trim().toUpperCase(Locale.ROOT));
    }

    private void validarSemAndamento(String acao) {
        List<ItemPlanejado> bloqueantes = itensBloqueantes();
        if (!bloqueantes.isEmpty()) {
            ItemPlanejado item = bloqueantes.get(0);
            throw new InvarianteVioladaException("Não é possível " + acao + " " + codigoKit + ": o item "
                    + item.getDescricao() + " está " + item.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    private static void validarMultiplicador(BigDecimal multiplicador) {
        if (multiplicador == null || multiplicador.signum() <= 0) {
            throw new InvarianteVioladaException("A quantidade do kit deve ser maior que zero.");
        }
    }

    private ItemPlanejado buscarItem(UUID idItem) {
        return getItensAtivos().stream()
                .filter(i -> Objects.equals(i.getId(), idItem))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Item não encontrado no kit " + codigoKit + "."));
    }
}
