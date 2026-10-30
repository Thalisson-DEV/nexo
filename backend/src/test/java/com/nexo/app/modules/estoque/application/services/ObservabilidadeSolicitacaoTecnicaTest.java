package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilidadeSolicitacaoTecnicaTest {

    @Test
    void registraCategoriasComTagsFechadasSemIdentificadores() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilidadeSolicitacaoTecnica observabilidade =
                new ObservabilidadeSolicitacaoTecnica(registry);

        observabilidade.registrarConflito(
                ObservabilidadeSolicitacaoTecnica.Operacao.PLANEJAMENTO,
                ObservabilidadeSolicitacaoTecnica.TipoConflito.VERSAO);
        observabilidade.registrarFilhosCriados(OrigemComandoTecnico.IMPORT, 4);
        observabilidade.registrarReservaInvalida(
                ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA,
                ObservabilidadeSolicitacaoTecnica.MotivoReservaInvalida.AUSENTE);
        observabilidade.registrarCustodiaInsuficiente(
                ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO,
                ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SALDO);
        observabilidade.registrarImportacao(
                ObservabilidadeSolicitacaoTecnica.EtapaImportacao.CONFIRMACAO,
                ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.FALHA, 2);
        observabilidade.registrarIntegridade(
                StatusIntegridadeHistorico.VIOLADO, List.of(evento()));

        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_CONFLITOS)
                .tags("operacao", "PLANEJAMENTO", "tipo", "VERSAO")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_FILHOS)
                .tags("acao", "CRIADO", "origem", "IMPORT")
                .counter().count()).isEqualTo(4);
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_RESERVAS_INVALIDAS)
                .tags("operacao", "ENTREGA", "motivo", "AUSENTE")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_CUSTODIA_INSUFICIENTE)
                .tags("operacao", "DEVOLUCAO", "recurso", "SALDO")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_IMPORTACAO)
                .tags("etapa", "CONFIRMACAO", "resultado", "FALHA")
                .counter().count()).isEqualTo(2);
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_INTEGRIDADE)
                .tag("status", "VIOLADO").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).noneMatch(tag ->
                        tag.getKey().toLowerCase().contains("id")
                                || tag.getKey().toLowerCase().contains("chave")
                                || tag.getKey().toLowerCase().contains("payload")));
    }

    @Test
    void distingueDeadlockPostgresqlDeTimeoutDeLock() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilidadeSolicitacaoTecnica observabilidade =
                new ObservabilidadeSolicitacaoTecnica(registry);

        boolean deadlock = observabilidade.registrarFalhaConcorrencia(
                ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO,
                new CannotAcquireLockException(
                        "falha", new SQLException("deadlock", "40P01")));
        boolean timeout = observabilidade.registrarFalhaConcorrencia(
                ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO,
                new QueryTimeoutException("timeout"));

        assertThat(deadlock).isTrue();
        assertThat(timeout).isTrue();
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_CONCORRENCIA)
                .tags("operacao", "DEVOLUCAO", "tipo", "DEADLOCK")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get(ObservabilidadeSolicitacaoTecnica.METRICA_CONCORRENCIA)
                .tags("operacao", "ESTORNO", "tipo", "LOCK_TIMEOUT")
                .counter().count()).isEqualTo(1);
    }

    private static EventoSolicitacaoMaterial evento() {
        return new EventoSolicitacaoMaterial(
                UUID.randomUUID(), 1, TipoEventoSolicitacao.PLANEJAMENTO_ALTERADO, null,
                TipoObraSnapshot.OBRA, null, null, UUID.randomUUID(), "Operador",
                OrigemComandoTecnico.UI, null, 1, "{}", OffsetDateTime.now());
    }
}
