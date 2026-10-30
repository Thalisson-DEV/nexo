package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResumoResponse;
import com.nexo.app.modules.sst.application.dto.GrupoPerguntaResponse;
import com.nexo.app.modules.sst.application.dto.OpcaoRespostaResponse;
import com.nexo.app.modules.sst.application.dto.PerguntaResponse;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.GrupoPergunta;
import com.nexo.app.modules.sst.domain.OpcaoResposta;
import com.nexo.app.modules.sst.domain.Pergunta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FormularioInspecaoMapper {

    @Mapping(target = "emUso", ignore = true)
    FormularioInspecaoResponse toResponse(FormularioInspecao entity);

    GrupoPerguntaResponse toGrupoResponse(GrupoPergunta entity);

    PerguntaResponse toPerguntaResponse(Pergunta entity);

    OpcaoRespostaResponse toOpcaoResponse(OpcaoResposta entity);

    @Mapping(target = "totalPerguntas", ignore = true)
    FormularioInspecaoResumoResponse toResumo(FormularioInspecao entity);
}
