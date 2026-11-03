package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.FormularioApr;
import com.nexo.app.modules.obras.domain.TipoOcorrenciaTurno;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Comandos da equipe no turno de Obras. */
public final class TurnoObraRequests {

    private TurnoObraRequests() {
    }

    public record Atividade(
            @NotNull(message = "Informe a obra") UUID idObra,
            @Size(max = 1000) String observacao) {
    }

    public record Relatorio(@NotBlank(message = "Informe a observação geral") @Size(max = 4000) String observacao) {
    }

    public record Ocorrencia(
            UUID idObra,
            @NotNull(message = "Informe o tipo da ocorrência") TipoOcorrenciaTurno tipo,
            @NotBlank(message = "Descreva a ocorrência") @Size(max = 2000) String descricao,
            boolean impedeExecucao) {
    }

    public record Apr(
            @NotNull(message = "Informe a obra") UUID idObra,
            UUID idProgramacao,
            @NotBlank(message = "Informe a atividade") @Size(max = 500) String atividade,
            UUID idResponsavel,
            FormularioApr formulario) {
    }

    /** Sem turno informado, o apontamento entra no turno que a equipe teve na data, se houver. */
    public record Apontar(
            UUID idTurno,
            @NotNull(message = "Informe a data") @JsonFormat(pattern = "dd/MM/yyyy") LocalDate data,
            @NotEmpty(message = "Informe ao menos um item") @Valid List<LinhaApontamento> itens) {
    }

    public record LinhaApontamento(
            @NotNull(message = "Informe o item") UUID idItem,
            @NotNull(message = "Informe a quantidade") @DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser maior que zero") BigDecimal quantidade,
            @Size(max = 1000) String justificativa) {
    }
}
