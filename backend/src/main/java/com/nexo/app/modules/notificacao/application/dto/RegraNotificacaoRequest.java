package com.nexo.app.modules.notificacao.application.dto;

import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Criação e edição de uma regra.
 *
 * <p>As listas informadas substituem as anteriores por completo — é o comportamento
 * que a tela de edição espera, onde o administrador vê e ajusta o conjunto inteiro.</p>
 */
public record RegraNotificacaoRequest(

        /** Chave do catálogo. Imutável depois de criada: mudar o gatilho invalidaria
         *  condições, papéis e textos de uma vez. */
        @NotBlank(message = "Escolha o gatilho da regra.")
        String chaveGatilho,

        @NotBlank(message = "O nome da regra é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String descricao,

        /** Sem valor, assume a categoria padrão do gatilho. */
        CategoriaNotificacao categoria,

        PrioridadeNotificacao prioridade,

        @NotBlank(message = "O título da notificação é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String tituloTemplate,

        @NotBlank(message = "O texto da notificação é obrigatório.")
        @Size(max = 1000, message = "O texto deve ter no máximo 1000 caracteres.")
        String corpoTemplate,

        @Size(max = 500, message = "O destino deve ter no máximo 500 caracteres.")
        String deeplinkTemplate,

        /** Obrigatório em gatilhos por limiar; ignorado nos demais. */
        @Min(value = 1, message = "O limiar deve ser de ao menos 1.")
        Integer limiar,

        @Min(value = 0, message = "O intervalo mínimo entre avisos não pode ser negativo.")
        Integer cooldownMinutos,

        /**
         * Se o aviso também sai por e-mail. Objeto, e não primitivo, para distinguir
         * "o administrador desmarcou" de "o cliente não mandou o campo": ausente vira
         * ligado, que é o padrão do canal. Categorias que não aceitam e-mail ignoram
         * o valor — quem decide isso é o domínio.
         */
        Boolean enviarEmail,

        @Valid
        List<CondicaoRegraRequest> condicoes,

        @NotEmpty(message = "Defina ao menos um destinatário.")
        @Valid
        List<DestinatarioRegraRequest> destinatarios
) {

    public RegraNotificacaoRequest {
        enviarEmail = enviarEmail == null || enviarEmail;
    }

    public record CondicaoRegraRequest(
            @NotBlank(message = "Escolha o campo da condição.")
            String campo,

            @NotNull(message = "Escolha o operador da condição.")
            OperadorCondicao operador,

            @NotBlank(message = "Informe o valor de comparação.")
            String valor
    ) {
    }

    public record DestinatarioRegraRequest(
            @NotNull(message = "Escolha o tipo de destinatário.")
            TipoDestinatarioRegra tipo,

            /** Perfil, usuário ou política, conforme o tipo. */
            UUID idAlvo,

            /** Nome da permissão ou do papel dinâmico, conforme o tipo. */
            String valorAlvo
    ) {
    }
}
