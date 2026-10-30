package com.nexo.app.modules.financeiro.application.mappers;

import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Converte apenas a identificação da variante. O schema completo é montado pelo
 * {@code GeradorSchemaFormulario}, que precisa derivar opções de enumerações e
 * endpoints de referência — lógica que não cabe num mapeamento declarativo.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FormularioMapper {

    FormularioResumoResponse toResumo(FormularioSolicitacao entity);
}
