package com.nexo.app.modules.assistente.application.cadastro;

import java.util.List;

/**
 * Implementado por cada modulo de negocio para dizer ao Copiloto o que ele pode cadastrar ali: as
 * operacoes (sempre pelos casos de uso do proprio modulo) e os cadastros que elas referenciam.
 */
public interface ProvedorCadastros {

    List<OperacaoCadastro<?>> operacoes();

    default List<ReferenciaCadastro> referencias() {
        return List.of();
    }
}
