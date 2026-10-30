package com.nexo.app.modules.assistente.application.cadastro;

import java.util.List;

/**
 * Campo de um cadastro, derivado do DTO de entrada do caso de uso (ou declarado pelo modulo, nos
 * formularios dinamicos).
 *
 * @param chave         nome do campo no DTO
 * @param rotulo        nome exibido ("Base operacional")
 * @param tipo          tipo do valor
 * @param obrigatorio   o caso de uso recusa sem ele
 * @param descricao     orientacao ao modelo e dica no cartao
 * @param opcoes        valores possiveis (tipo OPCAO)
 * @param referencia    tipo do cadastro referenciado (tipo REFERENCIA)
 * @param filhos        campos de cada item (tipo LISTA de objetos ou OBJETO)
 * @param multiplo      lista de valores simples (ex.: varios colaboradores)
 * @param tamanhoMaximo limite de caracteres, quando houver
 * @param oculto        preenchido pelo sistema; nao aparece ao modelo nem no cartao
 */
public record CampoDescritor(
        String chave,
        String rotulo,
        TipoCampo tipo,
        boolean obrigatorio,
        String descricao,
        List<Opcao> opcoes,
        String referencia,
        List<CampoDescritor> filhos,
        boolean multiplo,
        Integer tamanhoMaximo,
        boolean oculto) {

    public CampoDescritor {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
        filhos = filhos == null ? List.of() : List.copyOf(filhos);
    }

    public static CampoDescritor simples(String chave, String rotulo, TipoCampo tipo, boolean obrigatorio, String descricao) {
        return new CampoDescritor(chave, rotulo, tipo, obrigatorio, descricao, List.of(), null, List.of(), false, null, false);
    }

    public static CampoDescritor referencia(String chave, String rotulo, String referencia, boolean obrigatorio, String descricao) {
        return new CampoDescritor(chave, rotulo, TipoCampo.REFERENCIA, obrigatorio, descricao, List.of(), referencia,
                List.of(), false, null, false);
    }

    public static CampoDescritor opcoes(String chave, String rotulo, List<Opcao> opcoes, boolean obrigatorio, String descricao) {
        return new CampoDescritor(chave, rotulo, TipoCampo.OPCAO, obrigatorio, descricao, opcoes, null, List.of(), false,
                null, false);
    }

    public CampoDescritor comMultiplo(boolean valor) {
        return new CampoDescritor(chave, rotulo, tipo, obrigatorio, descricao, opcoes, referencia, filhos, valor,
                tamanhoMaximo, oculto);
    }
}
