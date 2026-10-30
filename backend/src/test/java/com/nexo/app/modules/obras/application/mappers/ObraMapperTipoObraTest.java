package com.nexo.app.modules.obras.application.mappers;

import com.nexo.app.modules.obras.application.dto.ProjetoRelacionadoResponse;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObraMapperTipoObraTest {

    private final ObraMapper mapper = org.mapstruct.factory.Mappers.getMapper(ObraMapper.class);

    @Test
    void deveExporTipoNosContratosDetalhadoEResumido() {
        Obra obra = new Obra(
                "7654321", "OBRA LPT", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new StatusObra("PLANEJADA", 1, false),
                null, null, TipoObra.LPT
        );

        assertThat(mapper.toResponse(obra, null, null, null, null, null, null, List.of()).tipoObra())
                .isEqualTo(TipoObra.LPT);
        assertThat(mapper.toResumo(obra, null, null, null, null, null, null, 0).tipoObra())
                .isEqualTo(TipoObra.LPT);
    }

    @Test
    void deveAdaptarProjetoRelacionadoParaAliasLegadoSemDuplicarVinculo() {
        UUID idRelacionado = UUID.randomUUID();
        ProjetoRelacionadoResponse relacionado = new ProjetoRelacionadoResponse(
                UUID.randomUUID(), idRelacionado, "7000001", "PROJETO MEDIDOR", TipoObra.OBRA,
                TipoVinculoProjeto.MEDIDOR, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null,
                null, "PLANEJADA", true
        );

        var response = mapper.toResponse(obra(UUID.randomUUID()), null, null, null, null, null, null,
                List.of(relacionado));
        var resumo = mapper.toResumo(obra(UUID.randomUUID()), null, null, null, null, null, null, 1);

        assertThat(response.projetosRelacionados()).containsExactly(relacionado);
        assertThat(response.subprojetos()).singleElement()
                .satisfies(alias -> assertThat(alias.id()).isEqualTo(idRelacionado));
        assertThat(resumo.totalProjetosRelacionados()).isEqualTo(1);
        assertThat(resumo.totalSubprojetos()).isEqualTo(1);
    }

    private Obra obra(UUID id) {
        Obra obra = new Obra(
                "7654321", "OBRA LPT", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new StatusObra("PLANEJADA", 1, false),
                null, null, TipoObra.LPT
        );
        org.springframework.test.util.ReflectionTestUtils.setField(obra, "id", id);
        return obra;
    }
}
