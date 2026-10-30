package com.nexo.app.modules.notificacao.application.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.nexo.app.modules.notificacao.application.dto.AnexoComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoMuralResponse;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.SegmentoComunicadoResponse;
import com.nexo.app.modules.notificacao.domain.AnexoComunicado;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.domain.InteracaoComunicado;
import com.nexo.app.modules.notificacao.domain.SegmentoComunicado;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ComunicadoMapper {

    @Mapping(target = "tipoRotulo", expression = "java(entity.getTipo().getRotulo())")
    @Mapping(target = "apresentacaoRotulo", expression = "java(entity.getApresentacao().getRotulo())")
    @Mapping(target = "statusRotulo", expression = "java(entity.getStatus().getRotulo())")
    @Mapping(target = "agendado", expression = "java(entity.estaAgendado(java.time.OffsetDateTime.now()))")
    @Mapping(target = "vigente", expression = "java(entity.estaVigente(java.time.OffsetDateTime.now()))")
    @Mapping(target = "segmentos", expression = "java(toSegmentos(entity.segmentosAtivos()))")
    @Mapping(target = "anexos", expression = "java(toAnexos(entity.anexosAtivos()))")
    ComunicadoResponse toResponse(Comunicado entity);

    /**
     * A interação pode ser nula: o mural mostra comunicados que o usuário nunca
     * tocou, e é justamente esse o caso mais comum.
     */
    // Comunicado e InteracaoComunicado herdam 'id' de BaseEntity, então a origem
    // precisa ser explícita.
    @Mapping(target = "id", source = "comunicado.id")
    @Mapping(target = "tipoRotulo", expression = "java(comunicado.getTipo().getRotulo())")
    @Mapping(target = "permiteAdiar", expression = "java(comunicado.permiteAdiar())")
    @Mapping(target = "anexos", expression = "java(toAnexos(comunicado.anexosAtivos()))")
    @Mapping(target = "visualizado",
            expression = "java(interacao != null && interacao.getVisualizadoEm() != null)")
    @Mapping(target = "confirmado", expression = "java(interacao != null && interacao.isConfirmado())")
    ComunicadoMuralResponse toMural(Comunicado comunicado, InteracaoComunicado interacao);

    @Mapping(target = "tipoAlvoRotulo", expression = "java(entity.getTipoAlvo().getRotulo())")
    SegmentoComunicadoResponse toResponse(SegmentoComunicado entity);

    AnexoComunicadoResponse toResponse(AnexoComunicado entity);

    List<SegmentoComunicadoResponse> toSegmentos(List<SegmentoComunicado> segmentos);

    List<AnexoComunicadoResponse> toAnexos(List<AnexoComunicado> anexos);
}
