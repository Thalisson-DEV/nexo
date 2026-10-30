package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstoqueLookupCatalogUseCaseTest {

    private final EstoqueLookupCatalogUseCase useCase = new EstoqueLookupCatalogUseCase();

    @Test
    void publicaTiposDeMovimentacaoComValorELabelSemEntidades() {
        List<LookupOptionDto> options = useCase.listarTiposMovimentacao();

        assertThat(options).hasSize(TipoMovimentacao.values().length)
                .extracting(LookupOptionDto::value)
                .containsExactlyElementsOf(Arrays.stream(TipoMovimentacao.values()).map(Enum::name).toList());
        assertThat(options.getFirst().label()).isEqualTo("Entrada avulsa");
    }

    @Test
    void publicaTiposEStatusesDoFluxoTecnico() {
        assertThat(useCase.listarTiposSolicitacao()).hasSize(TipoSolicitacaoTecnica.values().length);
        assertThat(useCase.listarStatusSolicitacao()).hasSize(StatusSolicitacao.values().length);
        assertThat(useCase.listarStatusSolicitacao())
                .extracting(LookupOptionDto::value)
                .contains("PENDENTE", "APROVADO", "ENTREGUE");
    }
}
