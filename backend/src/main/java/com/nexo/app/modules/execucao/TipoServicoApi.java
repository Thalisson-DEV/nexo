package com.nexo.app.modules.execucao;

import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;

import java.util.List;

public interface TipoServicoApi {

    /** Todos os tipos de serviço, inclusive os desativados, para nomear lançamentos antigos. */
    List<TipoServicoResponse> listarTodos();
}
