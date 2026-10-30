package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.EstoqueLookupApi;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class EstoqueLookupCatalogUseCase implements EstoqueLookupApi {

    @Override
    public List<LookupOptionDto> listarTiposMovimentacao() {
        return options(TipoMovimentacao.values());
    }

    @Override
    public List<LookupOptionDto> listarTiposSolicitacao() {
        return options(TipoSolicitacaoTecnica.values());
    }

    @Override
    public List<LookupOptionDto> listarStatusSolicitacao() {
        return options(StatusSolicitacao.values());
    }

    private static List<LookupOptionDto> options(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(value -> new LookupOptionDto(value.name(), humanize(value.name())))
                .toList();
    }

    private static String humanize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
