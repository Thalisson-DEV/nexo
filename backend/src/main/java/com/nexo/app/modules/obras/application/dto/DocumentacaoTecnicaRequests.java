package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.EquipamentoObra;
import com.nexo.app.modules.obras.domain.RespostasChecklist;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DocumentacaoTecnicaRequests {

    private DocumentacaoTecnicaRequests() {
    }

    public record Visita(
            @NotNull(message = "Escolha o modelo") UUID idModelo,
            @NotNull(message = "Informe a data da visita") LocalDate dataVisita,
            @NotBlank(message = "Informe o responsável") @Size(max = 150) String responsavel,
            @Size(max = 2000) String observacoes,
            @NotNull List<RespostasChecklist.Informada> respostas) {
    }

    public record Ficha(
            @NotNull(message = "Escolha o modelo") UUID idModelo,
            @NotNull List<RespostasChecklist.Informada> respostas) {
    }

    public record LegendaEvidencia(@Size(max = 500) String legenda) {
    }

    public record Equipamento(
            @NotNull(message = "Informe o equipamento") EquipamentoObra.Tipo tipo,
            boolean trafoAuxiliar,
            @Size(max = 500) String observacao) {
    }
}
