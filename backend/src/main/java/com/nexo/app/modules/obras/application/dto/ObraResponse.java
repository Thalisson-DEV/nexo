package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ObraResponse(
        UUID id,
        String numero,
        String titulo,
        TipoObra tipoObra,
        String descricao,
        String notaObra,
        BigDecimal valorProjeto,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate dataPrevisaoFim,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate dataFimEfetiva,
        Double latitude,
        Double longitude,
        UUID processoId,
        String processoNome,
        UUID baseOperacionalId,
        String baseOperacionalNome,
        UUID municipioId,
        String municipioNome,
        UUID centroCustoId,
        String centroCustoNome,
        UUID statusId,
        String statusNome,
        boolean statusConcluida,
        UUID supervisorId,
        String supervisorNome,
        UUID coordenadorId,
        String coordenadorNome,
        UUID agenteResponsavelId,
        String agenteResponsavelNome,
        String agenteResponsavelEmail,
        @Deprecated(forRemoval = true) UUID obraPaiId,
        @Deprecated(forRemoval = true) String obraPaiNumero,
        @Deprecated(forRemoval = true) String obraPaiTitulo,
        @Deprecated(forRemoval = true) TipoVinculoSubprojeto tipoVinculoPai,
        Set<UUID> equipes,
        List<ProjetoRelacionadoResponse> projetosRelacionados,
        @Deprecated(forRemoval = true) List<SubprojetoResponse> subprojetos,
        List<AnexoObraResponse> anexos,
        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao,
        String usuarioCriacao,
        String usuarioUltimaAlteracao
) {
}
