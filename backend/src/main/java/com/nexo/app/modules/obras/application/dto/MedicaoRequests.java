package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.TipoMedicao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Comandos das medições e do pré-fechamento. */
public final class MedicaoRequests {

    private MedicaoRequests() {
    }

    /**
     * Sem itens escolhidos, entram todos os serviços pendentes de medição (no ciclo, os concluídos até o fim da
     * competência). A competência só é obrigatória no ciclo; na parcial vale o mês corrente.
     */
    public record Gerar(@NotNull TipoMedicao tipo,
                        @JsonFormat(pattern = "yyyy-MM") YearMonth competencia,
                        List<UUID> idsItens,
                        @Size(max = 1000) String observacao) {
    }

    public record Estornar(@NotBlank @Size(max = 1000) String justificativa) {
    }

    /** Códigos de obra colados de planilha: um por linha, ou separados por vírgula, ponto e vírgula ou tabulação. */
    public record Fechamento(@NotBlank @Size(max = 20000) String codigos) {
    }
}
