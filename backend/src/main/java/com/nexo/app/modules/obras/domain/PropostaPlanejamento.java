package com.nexo.app.modules.obras.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O que o Copiloto leu numa página do projeto, já convertido para coordenadas da página.
 *
 * @param referencia identifica a sugestão dentro da proposta (é o número lido na folha)
 * @param confianca  de 0 a 1, informada pelo modelo
 * @param conflito   número que já existe na obra: exige outro número antes de gravar
 */
public record PropostaPlanejamento(List<PontoSugerido> pontos, List<VaoSugerido> vaos, List<String> avisos) {

    public record PontoSugerido(String referencia, String numero, TipoPontoPlanejamento tipo, int pagina, BigDecimal x,
                                BigDecimal y, String barramento, List<String> textosLidos, double confianca,
                                boolean conflito, List<KitSugerido> kits) {
    }

    public record VaoSugerido(String referenciaA, String referenciaB, BigDecimal metragem, List<String> textosLidos,
                              double confianca, List<KitSugerido> kits) {
    }

    /**
     * @param marcado o kit vem pré-selecionado: o texto lido apontou só para ele no catálogo do contrato
     */
    public record KitSugerido(UUID idKit, String codigo, String descricao, String textoLido, boolean marcado) {
    }
}
