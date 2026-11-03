package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.EstruturaChecklist;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModeloChecklistObraRequest(
        @NotNull(message = "Informe a finalidade") ModeloChecklistObra.Finalidade finalidade,
        @NotBlank(message = "Informe o título") @Size(max = 150) String titulo,
        @Size(max = 500) String descricao,
        @NotNull(message = "Inclua ao menos um grupo") EstruturaChecklist estrutura
) {
}
