package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusDesvio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lançamento completo de uma inspeção de segurança em uma única requisição
 * (offline-first): metadados, colaboradores inspecionados, respostas e desvios.
 *
 * <p>O inspetor é sempre o usuário logado e é resolvido na camada web — não vem
 * no corpo da requisição.</p>
 */
public record RegistrarInspecaoRequest(
        @NotNull(message = "O ID do formulário é obrigatório")
        UUID idFormulario,

        UUID idTipoInspecao,

        @NotNull(message = "O ID do processo é obrigatório")
        UUID idProcesso,

        UUID idVeiculo,
        UUID idEquipe,

        @NotNull(message = "O ID da base operacional é obrigatório")
        UUID idBaseOperacional,

        @NotNull(message = "A data de realização é obrigatória")
        OffsetDateTime dataRealizacao,

        String observacoes,

        BigDecimal latitude,
        BigDecimal longitude,

        @NotEmpty(message = "Informe ao menos um colaborador inspecionado")
        List<UUID> colaboradoresInspecionados,

        @Valid
        List<RespostaItem> respostas,

        @Valid
        List<DesvioItem> desvios,

        /**
         * IDs das fotos já enviadas em {@code POST /v1/sst/inspecoes/anexos}. Ao menos
         * uma é obrigatória: é ela que comprova que a inspeção aconteceu de fato.
         */
        @NotEmpty(message = "Anexe ao menos uma evidência fotográfica da inspeção")
        List<UUID> evidencias
) {
    public RegistrarInspecaoRequest {
        colaboradoresInspecionados = colaboradoresInspecionados == null ? List.of() : colaboradoresInspecionados;
        respostas = respostas == null ? List.of() : respostas;
        desvios = desvios == null ? List.of() : desvios;
        evidencias = evidencias == null ? List.of() : evidencias;
    }

    /**
     * Resposta a uma pergunta. Para perguntas objetivas, preencher
     * {@code opcoesSelecionadas}; para perguntas livres, o valor correspondente.
     */
    public record RespostaItem(
            @NotNull(message = "O ID da pergunta é obrigatório")
            UUID idPergunta,

            List<UUID> opcoesSelecionadas,
            String valorTexto,
            BigDecimal valorNumero,
            LocalDate valorData
    ) {
        public RespostaItem {
            opcoesSelecionadas = opcoesSelecionadas == null ? List.of() : opcoesSelecionadas;
        }
    }

    /**
     * Desvio (não-conformidade) identificado. A opção referenciada deve estar
     * marcada como geradora de desvio no formulário, e os colaboradores devem
     * estar entre os inspecionados. A gravidade não vem no lançamento — é derivada
     * da opção que gerou o desvio (pré-definida no cadastro do formulário).
     */
    public record DesvioItem(
            @NotNull(message = "O ID da pergunta do desvio é obrigatório")
            UUID idPergunta,

            UUID idOpcaoResposta,

            String descricao,
            String acaoCorretiva,
            LocalDate prazo,

            @NotEmpty(message = "Atribua o desvio a pelo menos um colaborador")
            List<UUID> colaboradores,

            StatusDesvio status
    ) {
        public DesvioItem {
            colaboradores = colaboradores == null ? List.of() : colaboradores;
            status = status == null ? StatusDesvio.ABERTO : status;
        }

        public DesvioItem(UUID idPergunta, UUID idOpcaoResposta, String descricao,
                          String acaoCorretiva, LocalDate prazo, List<UUID> colaboradores) {
            this(idPergunta, idOpcaoResposta, descricao, acaoCorretiva, prazo, colaboradores, StatusDesvio.ABERTO);
        }
    }
}
