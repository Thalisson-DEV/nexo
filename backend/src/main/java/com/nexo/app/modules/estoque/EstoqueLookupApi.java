package com.nexo.app.modules.estoque;

import com.nexo.app.shared.application.dto.LookupOptionDto;

import java.util.List;

/** Catalogos fechados do estoque consumidos pelo bootstrap de seletores. */
public interface EstoqueLookupApi {

    List<LookupOptionDto> listarTiposMovimentacao();

    List<LookupOptionDto> listarTiposSolicitacao();

    List<LookupOptionDto> listarStatusSolicitacao();
}
