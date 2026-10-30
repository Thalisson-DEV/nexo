package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.AnexoEventoResponse;
import com.nexo.app.modules.sst.application.dto.EnvolvidoEventoResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstResumoResponse;
import com.nexo.app.modules.sst.application.dto.RotulosEventoSst;
import com.nexo.app.modules.sst.application.dto.TecnicoEventoResponse;
import com.nexo.app.modules.sst.domain.AnexoEventoSst;
import com.nexo.app.modules.sst.domain.EnvolvidoEvento;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.TecnicoSegurancaEvento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Converte o agregado do evento nas respostas da API.
 *
 * <p>Duas fontes de propósito: o evento traz tudo o que é dele (inclusive os snapshots,
 * que são texto pronto), e {@link RotulosEventoSst} traz os nomes das entradas que o
 * evento apenas referencia. É o que evita reconstruir uma resposta de cinquenta campos
 * à mão só para preencher cinco nomes.</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventoSstMapper {

    @Mapping(target = "classificacaoNome", source = "rotulos.classificacaoNome")
    @Mapping(target = "localLesaoNome", source = "rotulos.localLesaoNome")
    @Mapping(target = "naturezaLesaoNome", source = "rotulos.naturezaLesaoNome")
    @Mapping(target = "agenteCausadorNome", source = "rotulos.agenteCausadorNome")
    @Mapping(target = "processoNome", source = "rotulos.processoNome")
    @Mapping(target = "id", source = "evento.id")
    @Mapping(target = "tecnicos", source = "evento.tecnicos")
    @Mapping(target = "envolvidos", source = "evento.envolvidos")
    @Mapping(target = "anexos", source = "evento.anexos")
    EventoSstResponse toResponse(EventoSst evento, RotulosEventoSst rotulos);

    @Mapping(target = "classificacaoNome", source = "rotulos.classificacaoNome")
    @Mapping(target = "id", source = "evento.id")
    EventoSstResumoResponse toResumo(EventoSst evento, RotulosEventoSst rotulos);

    TecnicoEventoResponse toTecnico(TecnicoSegurancaEvento tecnico);

    EnvolvidoEventoResponse toEnvolvido(EnvolvidoEvento envolvido);

    AnexoEventoResponse toAnexo(AnexoEventoSst anexo);
}
