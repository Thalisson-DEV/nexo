package com.nexo.app.modules.financeiro.domain;

/**
 * Natureza do serviço ou peça solicitada para um veículo da frota.
 *
 * <p>Era uma lista de opções cadastrada à mão na variante de frota. Como o
 * conjunto é estável e serve de recorte para relatórios de custo por tipo de
 * manutenção, virou enumeração fechada com coluna própria — o mesmo caminho de
 * qualquer outro campo com regra.</p>
 */
public enum NaturezaServicoFrota implements Rotulavel {

    COMPRA_E_REFORMA_DE_PNEUS("Compra e Reforma de Pneus"),
    SUSPENSAO_FREIOS("Suspensão / Freios"),
    ARREFECIMENTO("Arrefecimento"),
    TRANSMISSAO("Transmissão"),
    ELETRICA("Elétrica"),
    CHAPARIA_E_PINTURA("Chaparia e Pintura"),
    GUINCHO("Guincho"),
    HIDRAULICA("Hidráulica"),
    TAPECARIA("Tapeçaria"),
    LAVAGEM_E_LUBRIFICACAO("Lavagem e Lubrificação"),
    ACESSORIOS("Acessórios"),
    BATERIA("Bateria"),
    BORRACHARIA("Borracharia"),
    MECANICA("Mecânica"),
    PNEUMATICA("Pneumática"),
    POSTO_DE_MOLAS("Posto de Molas"),
    REFRIGERACAO_E_CLIMATIZACAO("Refrigeração e Climatização"),
    TACOGRAFO("Tacógrafo"),
    TORNEADORA("Torneadora");

    private final String rotulo;

    NaturezaServicoFrota(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }
}
