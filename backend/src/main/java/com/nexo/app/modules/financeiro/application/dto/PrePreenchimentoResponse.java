package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Valores herdados de uma compra aprovada, prontos para semear o formulário de
 * pagamento.
 *
 * <p>O servidor já confere que a compra está aprovada e que há saldo, e devolve
 * apenas os campos que existem na variante de pagamento escolhida — a tela apenas
 * aplica o mapa sobre o formulário, sem decidir o que herdar.</p>
 */
@Schema(description = "Pré-preenchimento a partir de uma compra aprovada")
public record PrePreenchimentoResponse(

        @Schema(description = "Compra de origem, com o saldo disponível")
        SolicitacaoCompraResumoResponse compra,

        @Schema(description = "Valores a aplicar no formulário, indexados pela chave do campo")
        Map<String, CampoValorResponse> valores,

        @Schema(description = "Chaves que o servidor recalcula na submissão e que a tela deve exibir "
                + "como somente leitura", example = "[\"fornecedorId\", \"baseId\"]")
        List<String> camposHerdados
) {}
