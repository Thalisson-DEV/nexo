package com.nexo.app.modules.notificacao;

import java.util.List;

/**
 * Um fato a que o sistema sabe reagir.
 *
 * @param modo     OCORRENCIA (evento publicado por um módulo) ou LIMIAR (contagem lida periodicamente)
 * @param papeis   pessoas que o próprio evento identifica (ex.: solicitante)
 */
public record GatilhoCatalogado(String chave, String nome, String descricao, String modo, String categoria,
                                List<String> variaveis, List<String> papeis) {
}
