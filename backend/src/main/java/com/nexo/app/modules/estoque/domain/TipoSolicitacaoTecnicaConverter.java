package com.nexo.app.modules.estoque.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoSolicitacaoTecnicaConverter
        implements AttributeConverter<TipoSolicitacaoTecnica, String> {

    @Override
    public String convertToDatabaseColumn(TipoSolicitacaoTecnica atributo) {
        return atributo == null ? null : atributo.getCodigo();
    }

    @Override
    public TipoSolicitacaoTecnica convertToEntityAttribute(String codigo) {
        return codigo == null ? null : TipoSolicitacaoTecnica.porCodigo(codigo);
    }
}
