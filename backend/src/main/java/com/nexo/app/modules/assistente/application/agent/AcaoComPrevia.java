package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;

/**
 * Ferramenta de escrita que monta a propria previa antes da confirmacao: resolve nomes em cadastros,
 * normaliza valores e aponta o que falta. O executor guarda os argumentos normalizados na acao pendente e
 * entrega a previa a interface, que a mostra como cartao de confirmacao editavel. A execucao so acontece em
 * {@link #confirmar}, chamada na requisicao do usuario que confirmou (com o contexto de seguranca dele).
 */
public interface AcaoComPrevia {

    PreviaAcao preparar(JsonNode argumentos, SemanticQueryContext seguranca) throws ToolException;

    /** Aplica os ajustes feitos no cartao ({caminho: valor}) e devolve a previa revisada. */
    PreviaAcao revisar(JsonNode argumentos, JsonNode alteracoes, SemanticQueryContext seguranca) throws ToolException;

    /**
     * Executa a acao com os ultimos ajustes do cartao (que podem trazer dados que nunca sao guardados, como
     * senhas). Nunca chamado pelo agente: somente pela confirmacao do usuario.
     */
    ResultadoAcao confirmar(JsonNode argumentos, JsonNode alteracoes, SemanticQueryContext seguranca) throws ToolException;

    /**
     * Previa pronta para confirmacao.
     *
     * @param argumentos  argumentos normalizados guardados na acao pendente (ids no lugar de nomes)
     * @param exibicao    estrutura exibida no cartao de confirmacao
     * @param paraModelo  retorno ao modelo: o que foi apresentado e o que falta
     * @param resumo      linha curta para a timeline
     * @param chave       identifica o que a acao faz; uma nova previa com a mesma chave substitui a anterior
     * @param pronta      nada falta: a confirmacao pode ser feita
     */
    record PreviaAcao(JsonNode argumentos, JsonNode exibicao, String paraModelo, String resumo, String chave,
            boolean pronta) {
    }

    /**
     * Desfecho da confirmacao.
     *
     * @param sucesso     ao menos um registro foi gravado
     * @param resultado   estrutura exibida no cartao e guardada (registros criados, falhas)
     * @param paraModelo  linha registrada no historico para o modelo saber o que aconteceu
     * @param sigiloso    entregue so na resposta da confirmacao e nunca guardado (ex.: senha provisoria)
     * @param previa      previa revisada quando ainda falta algo (nada foi gravado)
     */
    record ResultadoAcao(boolean sucesso, JsonNode resultado, String paraModelo, JsonNode sigiloso, PreviaAcao previa) {

        public boolean pendente() {
            return previa != null;
        }
    }
}
