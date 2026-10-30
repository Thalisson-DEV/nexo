package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FornecedorRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedorBuscaGlobalSolicitacaoFinanceiraTest {

    @Mock
    private FornecedorRepository fornecedorRepository;

    @Mock
    private SolicitacaoCompraRepository solicitacaoCompraRepository;

    @Mock
    private ColaboradorApi colaboradorApi;

    @Mock
    private VeiculoApi veiculoApi;

    @Mock
    private BaseOperacionalApi baseOperacionalApi;

    @Mock
    private ProcessoNegocioApi processoNegocioApi;

    @Mock
    private ContratoApi contratoApi;

    @Mock
    private UsuarioApi usuarioApi;

    @InjectMocks
    private ResolvedorBuscaGlobalSolicitacaoFinanceira resolvedor;

    @Test
    void expandeTermoNormalizadoNosCadastrosReferenciados() {
        UUID fornecedor = UUID.randomUUID();
        UUID colaborador = UUID.randomUUID();
        UUID veiculo = UUID.randomUUID();
        UUID base = UUID.randomUUID();
        UUID processo = UUID.randomUUID();
        UUID obra = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();

        when(fornecedorRepository.buscarIdsPorTrecho("abc-1234", "1234"))
                .thenReturn(List.of(fornecedor));
        when(colaboradorApi.buscarIdsPorTrechoDoNome("abc-1234"))
                .thenReturn(List.of(colaborador));
        when(veiculoApi.buscarIdsPorTrecho("abc-1234"))
                .thenReturn(List.of(veiculo));
        when(baseOperacionalApi.buscarIdsPorTrecho("abc-1234"))
                .thenReturn(List.of(base));
        when(processoNegocioApi.buscarIdsPorTrecho("abc-1234"))
                .thenReturn(List.of(processo));
        when(contratoApi.buscarIdsPorTrecho("abc-1234"))
                .thenReturn(List.of(obra));
        when(usuarioApi.buscarIdsPorTrechoDoLogin("abc-1234"))
                .thenReturn(List.of(usuario));

        var encontrados = resolvedor.resolver("  ÁBC-1234 ");

        assertThat(encontrados.fornecedores()).containsExactly(fornecedor);
        assertThat(encontrados.colaboradores()).containsExactly(colaborador);
        assertThat(encontrados.veiculos()).containsExactly(veiculo);
        assertThat(encontrados.bases()).containsExactly(base);
        assertThat(encontrados.processos()).containsExactly(processo);
        assertThat(encontrados.obras()).containsExactly(obra);
        assertThat(encontrados.usuarios()).containsExactly(usuario);
        assertThat(encontrados.compras()).isEmpty();
        verify(fornecedorRepository).buscarIdsPorTrecho(eq("abc-1234"), eq("1234"));
    }

    @Test
    void naoConsultaCadastrosParaBuscaVazia() {
        var encontrados = resolvedor.resolver("   ");

        assertThat(encontrados).isEqualTo(com.nexo.app.modules.financeiro.application.dto
                .IdsBuscaSolicitacaoFinanceira.vazio());
        verify(fornecedorRepository, never()).buscarIdsPorTrecho(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(veiculoApi, never()).buscarIdsPorTrecho(org.mockito.ArgumentMatchers.any());
    }
}
