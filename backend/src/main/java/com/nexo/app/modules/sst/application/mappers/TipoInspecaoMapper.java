package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.TipoInspecaoResponse;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import org.springframework.stereotype.Component;

@Component
public class TipoInspecaoMapper {

    public TipoInspecaoResponse toResponse(TipoInspecao tipo) {
        if (tipo == null) {
            return null;
        }
        return new TipoInspecaoResponse(
                tipo.getId(),
                tipo.getNome(),
                tipo.getDescricao(),
                tipo.getOrdem(),
                tipo.isAtivo(),
                tipo.getDataCriacao(),
                tipo.getDataUltimaAlteracao()
        );
    }
}
