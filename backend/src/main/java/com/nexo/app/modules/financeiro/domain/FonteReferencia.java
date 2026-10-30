package com.nexo.app.modules.financeiro.domain;

/**
 * Cadastros que podem alimentar um campo do tipo {@link TipoCampo#REFERENCIA}.
 *
 * <p>Cada fonte publica o endpoint de listagem que o frontend deve consumir para
 * montar o combo. Assim o formulário permanece moldável sem que a tela precise
 * saber, em código, de onde vem cada lista.</p>
 */
public enum FonteReferencia {

    BASE_OPERACIONAL("/v1/bases-operacionais", "Bases operacionais"),

    /** Processos de negócio do RH — é o "Setor / Centro de Custo" dos formulários. */
    PROCESSO("/v1/processos-negocio", "Setores / centros de custo"),

    FORNECEDOR("/v1/financeiro/fornecedores", "Fornecedores"),

    COLABORADOR("/v1/rh/colaboradores", "Colaboradores"),

    VEICULO("/v1/veiculos", "Veículos"),

    /** Contratos de obra — atende o campo "OBRA" das solicitações de obras civis. */
    OBRA("/v1/contratos", "Obras / contratos"),

    /** Solicitações de compra aprovadas, para vincular a uma solicitação de pagamento. */
    SOLICITACAO_COMPRA("/v1/financeiro/solicitacoes-compra?status=APROVADO", "Solicitações de compra aprovadas");

    private final String endpointOpcoes;
    private final String descricao;

    FonteReferencia(String endpointOpcoes, String descricao) {
        this.endpointOpcoes = endpointOpcoes;
        this.descricao = descricao;
    }

    /** Endpoint REST que o frontend consome para popular as opções deste campo. */
    public String getEndpointOpcoes() {
        return endpointOpcoes;
    }

    public String getDescricao() {
        return descricao;
    }
}
