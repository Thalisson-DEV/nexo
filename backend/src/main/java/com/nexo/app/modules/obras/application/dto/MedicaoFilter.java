package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.StatusMedicao;
import com.nexo.app.modules.obras.domain.TipoMedicao;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Filtros da lista de medições. Competência de e até são meses ({@code 2026-11}); base, município e supervisor são os
 * da obra; {@code geradaDe} e {@code geradaAte}, dias da geração; {@code q} busca pelo número da medição (#12) ou pelo
 * número e título da obra.
 */
public record MedicaoFilter(UUID idObra, List<TipoMedicao> tipos, List<StatusMedicao> status, YearMonth competenciaDe,
                            YearMonth competenciaAte, List<UUID> idsBases, List<UUID> idsMunicipios,
                            List<UUID> idsSupervisores, String usuario, LocalDate geradaDe, LocalDate geradaAte, String q) {
}
