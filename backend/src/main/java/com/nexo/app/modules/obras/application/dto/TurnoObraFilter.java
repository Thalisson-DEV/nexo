package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Filtros da listagem de turnos de Obras. Equipe, base, obra, período e busca definem o recorte que os indicadores
 * resumem; status, meta, relatório e ocorrência só escolhem quais turnos do recorte a tabela mostra.
 */
public record TurnoObraFilter(
        UUID idEquipe,
        @Parameter(description = "Bases operacionais (repetir o parâmetro ou separar por vírgula)")
        List<UUID> idBaseOperacional,
        StatusTurno status,
        @Parameter(description = "BATEU ou NAO_BATEU; turno sem meta não entra em nenhum dos dois")
        Meta meta,
        @Parameter(description = "Número da obra em que o turno teve atividade (trecho)")
        String obra,
        @Parameter(description = "Abertos a partir deste dia (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate de,
        @Parameter(description = "Abertos até este dia, inclusive (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate ate,
        Boolean semRelatorio,
        Boolean comOcorrencia,
        @Parameter(description = "Busca por equipe, placa, quem abriu ou obra")
        String q
) {

    public enum Meta {
        BATEU,
        NAO_BATEU
    }

    public TurnoObraFilter {
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
    }
}
