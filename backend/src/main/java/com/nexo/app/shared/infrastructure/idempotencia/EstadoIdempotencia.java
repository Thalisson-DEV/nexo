package com.nexo.app.shared.infrastructure.idempotencia;

/**
 * Estados possíveis de uma reserva de chave de idempotência.
 *
 * <p>Não existe estado de falha de propósito. Uma requisição que termina em erro
 * apaga a própria reserva, porque o erro é resposta sobre <i>a tentativa</i>, não
 * sobre <i>a intenção</i>: quem corrige o formulário e reenvia com a mesma chave
 * tem direito a ser processado. Só o sucesso é definitivo o bastante para ser
 * repetido.</p>
 *
 * @author <b>Thalisson</b>
 */
public enum EstadoIdempotencia {

    /** Reserva feita; o handler ainda está executando. Uma segunda chegada recebe 409. */
    EM_PROCESSAMENTO,

    /** A resposta gravada no registro é a verdade final desta chave e pode ser repetida. */
    CONCLUIDA
}
