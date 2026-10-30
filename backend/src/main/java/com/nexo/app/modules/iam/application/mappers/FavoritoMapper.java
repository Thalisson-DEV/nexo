package com.nexo.app.modules.iam.application.mappers;

import com.nexo.app.modules.iam.application.dto.FavoritoResponse;
import com.nexo.app.modules.iam.domain.Favorito;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FavoritoMapper {

    FavoritoResponse toResponse(Favorito favorito);

    List<FavoritoResponse> toResponseList(List<Favorito> favoritos);
}
