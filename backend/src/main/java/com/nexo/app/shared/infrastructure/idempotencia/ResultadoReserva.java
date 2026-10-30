package com.nexo.app.shared.infrastructure.idempotencia;

/**
 * O que fazer com uma requisição que chegou carimbada com uma chave de idempotência.
 *
 * <p>Modelado como tipo selado para que o filtro seja obrigado a decidir sobre os quatro
 * desfechos possíveis: nenhum deles é o caso "raro" que se pode esquecer.</p>
 *
 * @author <b>Thalisson</b>
 */
public sealed interface ResultadoReserva {

    /** Chave inédita (ou reserva vencida, retomada): siga e execute de verdade. */
    record Prosseguir() implements ResultadoReserva {}

    /**
     * Esta intenção já foi atendida com sucesso. Devolva exatamente a mesma resposta —
     * é o que torna a retentativa indistinguível de um sucesso de primeira viagem e
     * dispensa o cliente de ter um caminho de código só para retentar.
     */
    record Repetir(RegistroIdempotencia registro) implements ResultadoReserva {}

    /**
     * A requisição original ainda está em voo. É o duplo clique clássico: os dois
     * cliques chegam com milissegundos de diferença e o primeiro ainda não terminou.
     * O segundo é recusado, não enfileirado — quem espera é o usuário, não o servidor.
     */
    record EmVoo() implements ResultadoReserva {}

    /**
     * A chave já pertence a outra operação. Repetir a resposta guardada aqui seria
     * afirmar que gravamos algo que não gravamos; recusar é a única saída honesta.
     */
    record Conflitante() implements ResultadoReserva {}
}
