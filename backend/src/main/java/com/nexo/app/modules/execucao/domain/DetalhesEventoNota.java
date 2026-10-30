package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.application.dto.OperacaoMaterial;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O conteúdo de um evento da linha do tempo, com código e nome congelados no momento
 * do evento: a ação ou o material podem mudar de nome depois, o histórico não.
 *
 * <p>No LANCADA, "adicionados" é tudo o que a nota trouxe de campo. No ALTERADA, é a
 * diferença, e {@code dadosAlterados} traz o que mudou na identificação da nota (número,
 * tipo, serviço, município, data), já em texto. No NUMERO_OFICIAL, só os números mudam.</p>
 */
public record DetalhesEventoNota(
        List<AcaoRegistrada> acoesAdicionadas,
        List<AcaoRegistrada> acoesRemovidas,
        List<MaterialRegistrado> materiaisAdicionados,
        List<MaterialRegistrado> materiaisRemovidos,
        String referenciaAnterior,
        String referenciaNova,
        List<DadoAlterado> dadosAlterados
) {
    public DetalhesEventoNota {
        acoesAdicionadas = acoesAdicionadas == null ? List.of() : List.copyOf(acoesAdicionadas);
        acoesRemovidas = acoesRemovidas == null ? List.of() : List.copyOf(acoesRemovidas);
        materiaisAdicionados = materiaisAdicionados == null ? List.of() : List.copyOf(materiaisAdicionados);
        materiaisRemovidos = materiaisRemovidos == null ? List.of() : List.copyOf(materiaisRemovidos);
        dadosAlterados = dadosAlterados == null ? List.of() : List.copyOf(dadosAlterados);
    }

    public DetalhesEventoNota(List<AcaoRegistrada> acoesAdicionadas, List<AcaoRegistrada> acoesRemovidas,
                              List<MaterialRegistrado> materiaisAdicionados, List<MaterialRegistrado> materiaisRemovidos,
                              String referenciaAnterior, String referenciaNova) {
        this(acoesAdicionadas, acoesRemovidas, materiaisAdicionados, materiaisRemovidos, referenciaAnterior,
                referenciaNova, null);
    }

    public static DetalhesEventoNota numeroOficial(String anterior, String novo) {
        return new DetalhesEventoNota(null, null, null, null, anterior, novo);
    }

    /** Um dado da nota que mudou, com os valores já como a tela mostra. */
    public record DadoAlterado(String rotulo, String anterior, String novo) {
    }

    public record AcaoRegistrada(UUID idAcao, String codigo, String nome) {
    }

    public record MaterialRegistrado(
            UUID idMaterial,
            String codigo,
            String descricao,
            String unidadeMedida,
            BigDecimal quantidade,
            OperacaoMaterial operacao,
            String numSerie,
            OrigemMaterial origem
    ) {
    }
}
