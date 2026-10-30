package com.nexo.app.modules.status.application.observability;

import com.nexo.app.modules.assistente.infrastructure.observability.AssistenteModuleHealthAdapter;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConversaAssistenteRepository;
import com.nexo.app.modules.chamado.infrastructure.observability.ChamadoModuleHealthAdapter;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.contrato.infrastructure.observability.ContratoModuleHealthAdapter;
import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.modules.dashboard.infrastructure.observability.DashboardModuleHealthAdapter;
import com.nexo.app.modules.desconto.infrastructure.observability.DescontoModuleHealthAdapter;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.modules.estoque.infrastructure.observability.EstoqueModuleHealthAdapter;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.execucao.infrastructure.observability.ExecucaoModuleHealthAdapter;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import com.nexo.app.modules.financeiro.infrastructure.observability.FinanceiroModuleHealthAdapter;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.modules.iam.infrastructure.observability.IamModuleHealthAdapter;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.lookup.infrastructure.observability.LookupModuleHealthAdapter;
import com.nexo.app.modules.mencao.infrastructure.observability.MencaoModuleHealthAdapter;
import com.nexo.app.modules.mencao.infrastructure.persistence.MencaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.observability.NotificacaoModuleHealthAdapter;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ExecucaoRegraRepository;
import com.nexo.app.modules.obras.infrastructure.observability.ObrasModuleHealthAdapter;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.rh.infrastructure.observability.RhModuleHealthAdapter;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.sst.infrastructure.observability.SstModuleHealthAdapter;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthAdapter;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthCheckContext;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthReason;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthStatus;
import com.nexo.app.modules.estoque.EstoqueLookupApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;

/** Static contract test documenting that every canonical module has an adapter type. */
class ModuleHealthAdaptersCoverageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void canonicalInventoryHasOneNamedAdapterPerModule() {
        List<ModuleHealthAdapter> adapters = List.of(
                new IamModuleHealthAdapter(mock(UsuarioRepository.class)),
                new RhModuleHealthAdapter(mock(ColaboradorRepository.class)),
                new EstoqueModuleHealthAdapter(mock(MaterialRepository.class), mock(SaldoRepository.class)),
                new ContratoModuleHealthAdapter(mock(ContratoRepository.class), mock(AcaoRepository.class)),
                new ExecucaoModuleHealthAdapter(mock(ExecucaoComercialRepository.class), mock(TurnoRepository.class)),
                new DashboardModuleHealthAdapter(mock(EquipeApi.class)),
                new FinanceiroModuleHealthAdapter(mock(SolicitacaoPagamentoRepository.class)),
                new DescontoModuleHealthAdapter(mock(DescontoRepository.class)),
                new ObrasModuleHealthAdapter(mock(ObraRepository.class)),
                new SstModuleHealthAdapter(mock(DesvioSegurancaRepository.class), mock(InspecaoSegurancaRepository.class)),
                new ChamadoModuleHealthAdapter(mock(ChamadoRepository.class)),
                new NotificacaoModuleHealthAdapter(mock(RegraNotificacaoRepository.class), mock(ExecucaoRegraRepository.class)),
                new MencaoModuleHealthAdapter(mock(MencaoRepository.class)),
                new LookupModuleHealthAdapter(mock(BaseOperacionalApi.class), mock(ProcessoNegocioApi.class),
                        mock(EstoqueLookupApi.class)),
                new AssistenteModuleHealthAdapter(mock(ConversaAssistenteRepository.class), false));

        assertThat(adapters).extracting(ModuleHealthAdapter::module)
                .containsExactlyElementsOf(ModuleHealthRegistry.CANONICAL_MODULES);
    }

    @Test
    void isolatedRepositoryFailureReturnsDownWithoutLeakingTheCause() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        org.mockito.Mockito.when(repository.findFirstByAtivoTrueOrderByIdAsc())
                .thenThrow(new IllegalStateException("email=private@example.test"));

        var result = new IamModuleHealthAdapter(repository).check(
                ModuleHealthCheckContext.withTimeout(CLOCK, Duration.ofSeconds(2)));

        assertThat(result.status()).isEqualTo(ModuleHealthStatus.DOWN);
        assertThat(result.reasonCode()).isEqualTo(ModuleHealthReason.CHECK_FAILED);
        assertThat(result.toString()).doesNotContain("private@example.test");
    }

    @Test
    void emptyRepositoryIsHealthyAndDoesNotNeedAFixture() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        org.mockito.Mockito.when(repository.findFirstByAtivoTrueOrderByIdAsc())
                .thenReturn(java.util.Optional.empty());

        var result = new IamModuleHealthAdapter(repository).check(
                ModuleHealthCheckContext.withTimeout(CLOCK, Duration.ofSeconds(2)));

        assertThat(result.status()).isEqualTo(ModuleHealthStatus.UP);
        assertThat(result.reasonCode()).isEqualTo(ModuleHealthReason.CHECK_SUCCEEDED);
        org.mockito.Mockito.verify(repository).findFirstByAtivoTrueOrderByIdAsc();
    }
}
