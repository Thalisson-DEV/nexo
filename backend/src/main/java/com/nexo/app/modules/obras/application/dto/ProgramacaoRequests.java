package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.SituacaoIntervencao;
import com.nexo.app.modules.obras.domain.TurnoProgramacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comandos da programação. O campo {@code versao} é a versão que o usuário estava vendo: se outra pessoa
 * alterou a programação depois, a gravação é recusada com 409.
 */
public final class ProgramacaoRequests {

    private ProgramacaoRequests() {
    }

    /**
     * Prévia do diálogo de programar. Com {@code idProgramacao}, é a reprogramação: a programação não entra
     * na ocupação da equipe e o valor é o congelado nela.
     */
    public record Previa(
            List<UUID> idsItens,
            @NotNull(message = "Informe o início") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
            @NotNull(message = "Informe o fim") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
            UUID idBase,
            UUID idTipoEquipe,
            UUID idProgramacao) {
    }

    public record Programar(
            @NotEmpty(message = "Selecione ao menos um item") List<UUID> idsItens,
            @NotNull(message = "Informe a equipe") UUID idEquipe,
            @NotNull(message = "Informe o início") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
            @NotNull(message = "Informe o fim") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
            @NotBlank(message = "Informe a descrição do planejamento") @Size(max = 500) String descricao,
            @NotNull(message = "Informe se é em linha viva") Boolean linhaViva,
            @NotNull(message = "Informe se tem desligamento") Boolean desligamento,
            @NotNull(message = "Informe se finaliza a obra") Boolean finalizaObra,
            @NotNull(message = "Informe o turno") TurnoProgramacao turno,
            @NotNull(message = "Informe a situação da intervenção") SituacaoIntervencao situacaoIntervencao,
            @Size(max = 1000) String observacao) {

        public Programacao.Condicoes condicoes() {
            return new Programacao.Condicoes(descricao, linhaViva, desligamento, finalizaObra, turno, situacaoIntervencao, observacao);
        }
    }

    public record Editar(
            @NotBlank(message = "Informe a descrição do planejamento") @Size(max = 500) String descricao,
            @NotNull(message = "Informe se é em linha viva") Boolean linhaViva,
            @NotNull(message = "Informe se tem desligamento") Boolean desligamento,
            @NotNull(message = "Informe se finaliza a obra") Boolean finalizaObra,
            @NotNull(message = "Informe o turno") TurnoProgramacao turno,
            @NotNull(message = "Informe a situação da intervenção") SituacaoIntervencao situacaoIntervencao,
            @Size(max = 1000) String observacao,
            Long versao) {

        public Programacao.Condicoes condicoes() {
            return new Programacao.Condicoes(descricao, linhaViva, desligamento, finalizaObra, turno, situacaoIntervencao, observacao);
        }
    }

    public record Reprogramar(
            @NotNull(message = "Informe a equipe") UUID idEquipe,
            @NotNull(message = "Informe o início") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
            @NotNull(message = "Informe o fim") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
            @NotBlank(message = "Informe a observação da reprogramação") @Size(max = 1000) String observacao,
            Long versao) {
    }

    public record Encerrar(
            @Size(max = 1000) String observacao,
            Long versao) {
    }
}
