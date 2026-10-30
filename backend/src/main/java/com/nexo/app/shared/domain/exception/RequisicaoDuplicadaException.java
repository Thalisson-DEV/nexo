package com.nexo.app.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * A mesma intenção de escrita chegou duas vezes e a primeira ainda está sendo atendida.
 *
 * <p>É o duplo clique: dois cliques com milissegundos de diferença, e o primeiro ainda
 * não voltou. O segundo é <b>recusado</b>, não enfileirado — quem espera pelo desfecho
 * é o usuário, na tela, e não o servidor, segurando uma conexão.</p>
 *
 * <p>409 e não 429: o problema não é frequência, é que este pedido específico já está
 * em andamento. O cliente que receber isto deve aguardar e consultar o recurso, nunca
 * reenviar com uma chave nova — reenviar com chave nova é justamente o que produz a
 * duplicata.</p>
 *
 * @author <b>Thalisson</b>
 */
public class RequisicaoDuplicadaException extends BaseBusinessException {

    public RequisicaoDuplicadaException(String detail) {
        super("Requisição Já em Processamento", detail, HttpStatus.CONFLICT, "requisicao-duplicada");
    }
}
