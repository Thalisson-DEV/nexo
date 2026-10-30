package com.nexo.app.modules.financeiro.domain;

/**
 * Razão pela qual o serviço de frota foi solicitado.
 *
 * <p>Distingue o custo previsível do evitável: {@link #MAU_USO_DESCONTO} é o que
 * pode virar desconto em folha, e {@link #DESGASTE_NATURAL} é o que se espera da
 * operação normal.</p>
 */
public enum MotivoServicoFrota implements Rotulavel {

    DESGASTE_NATURAL("Desgaste natural"),
    ADEQUACAO_DE_MELHORIA("Adequação de Melhoria"),
    PROBLEMA_MECANICO("Problema mecânico"),
    MAU_USO_DESCONTO("Mau Uso / Desconto"),
    OUTRA("Outra");

    private final String rotulo;

    MotivoServicoFrota(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }
}
