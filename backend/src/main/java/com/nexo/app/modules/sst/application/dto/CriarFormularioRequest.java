package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.TipoResposta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Criação/atualização da estrutura completa de um formulário de inspeção
 * (formulário cru + grupos + perguntas + opções) em uma única requisição.
 */
public record CriarFormularioRequest(
        @NotBlank(message = "O título do formulário é obrigatório")
        String titulo,

        String descricao,

        @Valid
        @NotEmpty(message = "O formulário deve possuir ao menos um grupo")
        List<GrupoRequest> grupos
) {
    public CriarFormularioRequest {
        grupos = grupos == null ? List.of() : grupos;
    }

    public record GrupoRequest(
            @NotBlank(message = "O título do grupo é obrigatório")
            String titulo,

            @Valid
            @NotEmpty(message = "O grupo deve possuir ao menos uma pergunta")
            List<PerguntaRequest> perguntas
    ) {
        public GrupoRequest {
            perguntas = perguntas == null ? List.of() : perguntas;
        }
    }

    public record PerguntaRequest(
            @NotBlank(message = "O enunciado da pergunta é obrigatório")
            String enunciado,

            @NotNull(message = "O tipo de resposta é obrigatório")
            TipoResposta tipoResposta,

            boolean obrigatoria,

            @Valid
            List<OpcaoRequest> opcoes
    ) {
        public PerguntaRequest {
            opcoes = opcoes == null ? List.of() : opcoes;
        }
    }

    public record OpcaoRequest(
            @NotBlank(message = "O texto da opção é obrigatório")
            String texto,

            boolean geraDesvio,

            /**
             * Gravidade pré-definida do desvio gerado por esta opção. Obrigatória
             * quando {@code geraDesvio} é verdadeiro (validado no domínio); ignorada
             * caso contrário.
             */
            GravidadeDesvio gravidade,

            boolean padrao
    ) {
        public OpcaoRequest(String texto, boolean geraDesvio, GravidadeDesvio gravidade) {
            this(texto, geraDesvio, gravidade, false);
        }
    }
}
