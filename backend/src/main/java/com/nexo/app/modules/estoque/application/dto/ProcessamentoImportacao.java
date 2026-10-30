package com.nexo.app.modules.estoque.application.dto;

/** Estado final de uma etapa sincrona; o estado em andamento pertence a requisicao HTTP. */
public record ProcessamentoImportacao(Etapa etapa, Estado estado, String mensagem) {
    public enum Etapa { VALIDACAO, CONFIRMACAO }
    public enum Estado { PRONTO_PARA_CONFIRMACAO, COM_INCONSISTENCIAS, CONCLUIDO, PARCIAL, FALHOU }

    public static ProcessamentoImportacao validacao(int elegiveis, int erros) {
        if (elegiveis == 0) {
            return new ProcessamentoImportacao(Etapa.VALIDACAO, Estado.COM_INCONSISTENCIAS,
                    "Nenhum registro está pronto para importação. Corrija os erros e tente novamente.");
        }
        if (erros > 0) {
            return new ProcessamentoImportacao(Etapa.VALIDACAO, Estado.COM_INCONSISTENCIAS,
                    "Há registros com inconsistências. Veja o que precisa ser corrigido antes de continuar.");
        }
        return new ProcessamentoImportacao(Etapa.VALIDACAO, Estado.PRONTO_PARA_CONFIRMACAO,
                "Arquivo conferido com sucesso. Revise os dados antes de confirmar a importação.");
    }

    public static ProcessamentoImportacao confirmacao(int sucessos, int falhas) {
        if (sucessos == 0) {
            return new ProcessamentoImportacao(Etapa.CONFIRMACAO, Estado.FALHOU,
                    "Nenhuma movimentação de estoque foi registrada. Veja os erros e corrija os dados antes de tentar novamente.");
        }
        if (falhas > 0) {
            return new ProcessamentoImportacao(Etapa.CONFIRMACAO, Estado.PARCIAL,
                    "Algumas movimentações foram registradas. Veja os itens com erro antes de tentar novamente.");
        }
        return new ProcessamentoImportacao(Etapa.CONFIRMACAO, Estado.CONCLUIDO,
                "Todas as movimentações de estoque foram registradas com sucesso.");
    }
}
