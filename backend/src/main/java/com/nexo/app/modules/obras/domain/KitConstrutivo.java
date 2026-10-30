package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kit construtivo de um contrato: conjunto de serviços e materiais aplicado de uma vez num ponto do planejamento.
 *
 * <p>Sem classes de tensão informadas, o kit vale para qualquer tensão. Itens removidos são desativados
 * (soft delete) para manter o histórico das obras onde o kit já foi aplicado.</p>
 */
@Entity
@Table(name = "kits_construtivos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KitConstrutivo extends BaseEntity {

    @Column(name = "id_contrato", nullable = false)
    private UUID idContrato;

    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @Column(name = "descricao", nullable = false, length = 300)
    private String descricao;

    @Column(name = "grupo", length = 60)
    private String grupo;

    @ElementCollection
    @CollectionTable(name = "kits_construtivos_tensoes", joinColumns = @JoinColumn(name = "id_kit"))
    @Enumerated(EnumType.STRING)
    @Column(name = "classe_tensao", length = 20)
    private Set<ClasseTensao> classesTensao = new HashSet<>();

    @OneToMany(mappedBy = "kit", cascade = CascadeType.ALL)
    private List<ItemKit> itens = new ArrayList<>();

    public KitConstrutivo(UUID idContrato, String codigo, String descricao, String grupo, Set<ClasseTensao> classesTensao) {
        if (idContrato == null) {
            throw new InvarianteVioladaException("O contrato do kit é obrigatório.");
        }
        this.idContrato = idContrato;
        this.codigo = normalizarCodigo(codigo);
        atualizarDados(descricao, grupo, classesTensao);
    }

    public static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new InvarianteVioladaException("O código do kit é obrigatório.");
        }
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        if (normalizado.length() > 40) {
            throw new InvarianteVioladaException("O código do kit deve ter até 40 caracteres.");
        }
        return normalizado;
    }

    public void atualizarDados(String descricao, String grupo, Set<ClasseTensao> classesTensao) {
        validarAtivo();
        if (descricao == null || descricao.isBlank()) {
            throw new InvarianteVioladaException("A descrição do kit é obrigatória.");
        }
        this.descricao = descricao.trim();
        this.grupo = grupo != null && !grupo.isBlank() ? grupo.trim().toUpperCase(Locale.ROOT) : null;
        Set<ClasseTensao> novas = classesTensao != null ? Set.copyOf(classesTensao) : Set.of();
        this.classesTensao.clear();
        this.classesTensao.addAll(novas);
    }

    public ItemKit adicionarItem(TipoItemKit tipo, UUID idReferencia, OperacaoItemKit operacao,
                                 String unidade, BigDecimal quantidade) {
        validarAtivo();
        if (buscarItemAtivo(tipo, idReferencia, operacao).isPresent()) {
            throw new InvarianteVioladaException("O kit " + codigo + " já possui esse item com a mesma operação.");
        }
        ItemKit item = new ItemKit(this, tipo, idReferencia, operacao, unidade, quantidade);
        itens.add(item);
        return item;
    }

    /**
     * Cria o item ou atualiza quantidade/unidade se já existir com a mesma referência e operação.
     *
     * @return true quando o item foi criado
     */
    public boolean adicionarOuAtualizarItem(TipoItemKit tipo, UUID idReferencia, OperacaoItemKit operacao,
                                            String unidade, BigDecimal quantidade) {
        validarAtivo();
        Optional<ItemKit> existente = buscarItemAtivo(tipo, idReferencia, operacao);
        if (existente.isPresent()) {
            existente.get().alterar(operacao, unidade, quantidade);
            return false;
        }
        itens.add(new ItemKit(this, tipo, idReferencia, operacao, unidade, quantidade));
        return true;
    }

    public void alterarItem(UUID idItem, OperacaoItemKit operacao, String unidade, BigDecimal quantidade) {
        validarAtivo();
        buscarItem(idItem).alterar(operacao, unidade, quantidade);
    }

    public void removerItem(UUID idItem) {
        validarAtivo();
        buscarItem(idItem).desativar();
    }

    public List<ItemKit> getItensAtivos() {
        return itens.stream().filter(ItemKit::isAtivo).toList();
    }

    public boolean aplicavelEm(ClasseTensao classeTensao) {
        return classeTensao == null || classesTensao.isEmpty() || classesTensao.contains(classeTensao);
    }

    /**
     * Garante que o kit pode ser aplicado num ponto: ativo, com itens e compatível com a tensão da obra.
     */
    public void validarAplicacao(ClasseTensao classeTensaoObra) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("O kit " + codigo + " está desativado e não pode ser aplicado.");
        }
        if (getItensAtivos().isEmpty()) {
            throw new InvarianteVioladaException("O kit " + codigo + " está vazio e não pode ser aplicado.");
        }
        if (!aplicavelEm(classeTensaoObra)) {
            throw new InvarianteVioladaException("O kit " + codigo + " não se aplica à classe de tensão "
                    + classeTensaoObra.getRotulo() + ".");
        }
    }

    private Optional<ItemKit> buscarItemAtivo(TipoItemKit tipo, UUID idReferencia, OperacaoItemKit operacao) {
        return itens.stream()
                .filter(ItemKit::isAtivo)
                .filter(i -> i.referencia(tipo, idReferencia, operacao))
                .findFirst();
    }

    private ItemKit buscarItem(UUID idItem) {
        return itens.stream()
                .filter(ItemKit::isAtivo)
                .filter(i -> Objects.equals(i.getId(), idItem))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Item não encontrado no kit " + codigo + "."));
    }

    private void validarAtivo() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um kit desativado.");
        }
    }
}
