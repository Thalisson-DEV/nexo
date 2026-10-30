package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.ExecucaoRegra;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ExecucaoRegraRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a aplicação de uma regra: condições, cooldown, destinatários e o texto que
 * chega ao usuário. O motor apenas delega para cá, então é aqui que a decisão de
 * notificar (ou calar) é verificada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AplicadorRegraTest {

    @Mock
    private RegraNotificacaoRepository regraRepository;
    @Mock
    private ExecucaoRegraRepository execucaoRepository;
    @Mock
    private ResolvedorDestinatariosRegra resolvedorDestinatarios;
    @Mock
    private PublicadorNotificacoes publicador;

    private final UUID gestor = UUID.randomUUID();
    private AplicadorRegra aplicador;

    private AplicadorRegra aplicador() {
        if (aplicador == null) {
            // O renderizador é puro e sem estado; usar o real dá cobertura de verdade
            // sobre o texto que chega ao usuário.
            aplicador = new AplicadorRegra(regraRepository, execucaoRepository,
                    resolvedorDestinatarios, new RenderizadorTemplate(), publicador);
        }
        return aplicador;
    }

    @Test
    void limiarNaoAtingidoNaoDeveNotificar() {
        UUID id = registrar(regraPorLimiar(10));

        int entregues = aplicarLimiar(id, 9);

        assertThat(entregues).isZero();
        verify(publicador, never()).publicar(any());
    }

    @Test
    void limiarAtingidoDeveNotificarComTotalELimiarNoTexto() {
        UUID id = registrar(regraPorLimiar(10));
        when(resolvedorDestinatarios.resolver(any(), any(), any())).thenReturn(Set.of(gestor));
        when(publicador.publicar(any())).thenReturn(1);

        int entregues = aplicarLimiar(id, 12);

        assertThat(entregues).isEqualTo(1);
        NovaNotificacao comando = capturarComando();
        assertThat(comando.corpo()).isEqualTo("Há 12 desvios sem resolução, acima de 10.");
        assertThat(comando.origem()).isEqualTo(OrigemNotificacao.REGRA);
        assertThat(comando.categoria()).isEqualTo(CategoriaNotificacao.SEGURANCA);
        assertThat(comando.destinatarios()).containsExactly(gestor);
        assertThat(comando.contexto()).containsEntry("total", 12L).containsEntry("limiar", 10);
    }

    @Test
    void regraDesativadaNoIntervaloEntreSelecaoEAplicacaoNaoDeveNotificar() {
        RegraNotificacao regra = regraPorLimiar(10);
        regra.desativar();
        UUID id = registrar(regra);

        assertThat(aplicarLimiar(id, 50)).isZero();
        verify(publicador, never()).publicar(any());
    }

    @Test
    void regraEmCooldownNaoDeveNotificarDeNovo() {
        UUID id = registrar(regraPorLimiar(10));
        ExecucaoRegra execucao = new ExecucaoRegra(id, ExecucaoRegra.ESCOPO_GLOBAL,
                OffsetDateTime.now().minusMinutes(30));
        when(execucaoRepository.findByIdRegraAndChaveEscopo(any(), any())).thenReturn(Optional.of(execucao));

        // A regra tem cooldown de 720 minutos; 30 minutos depois ainda está em silêncio.
        int entregues = aplicarLimiar(id, 50);

        assertThat(entregues).isZero();
        verify(publicador, never()).publicar(any());
    }

    @Test
    void condicaoNaoSatisfeitaNaoDeveNotificar() {
        RegraNotificacao regra = regraPorLimiar(1);
        regra.adicionarCondicao("total", OperadorCondicao.MAIOR_QUE, "100");
        UUID id = registrar(regra);
        when(resolvedorDestinatarios.resolver(any(), any(), any())).thenReturn(Set.of(gestor));

        assertThat(aplicarLimiar(id, 5)).isZero();
        verify(publicador, never()).publicar(any());
    }

    @Test
    void semDestinatarioNaoDeveGravarExecucao() {
        UUID id = registrar(regraPorLimiar(1));
        when(resolvedorDestinatarios.resolver(any(), any(), any())).thenReturn(Set.of());

        assertThat(aplicarLimiar(id, 5)).isZero();
        verify(execucaoRepository, never()).save(any());
    }

    @Test
    void eventoDeveNotificarComOEscopoDoRegistro() {
        UUID idDesconto = UUID.randomUUID();
        UUID solicitante = UUID.randomUUID();
        UUID id = registrar(regraPorOcorrencia());
        when(resolvedorDestinatarios.resolver(any(), any(), any())).thenReturn(Set.of(solicitante));
        when(publicador.publicar(any())).thenReturn(1);

        int entregues = aplicador().aplicar(id,
                Map.of("motivo", "Dano em EPI", "parcelas", 3, "idDesconto", idDesconto),
                Map.of("solicitante", Set.of(solicitante)),
                idDesconto, idDesconto.toString(), null);

        assertThat(entregues).isEqualTo(1);
        NovaNotificacao comando = capturarComando();
        assertThat(comando.corpo()).isEqualTo("Seu desconto por Dano em EPI em 3x foi registrado.");
        assertThat(comando.idReferencia()).isEqualTo(idDesconto);
        assertThat(comando.chaveIdempotencia())
                .as("o escopo por registro é o que evita avisar duas vezes sobre o mesmo desconto")
                .endsWith(idDesconto.toString());
    }

    @Test
    void falhaAoAplicarNaoDevePropagarParaOMotor() {
        UUID id = registrar(regraPorOcorrencia());
        when(resolvedorDestinatarios.resolver(any(), any(), any()))
                .thenThrow(new IllegalStateException("permissão inexistente"));

        // Propagar aqui derrubaria as demais regras do mesmo gatilho.
        assertThat(aplicador().aplicar(id, Map.of(), Map.of(), null,
                ExecucaoRegra.ESCOPO_GLOBAL, null)).isZero();
    }

    @Test
    void regraApagadaEntreASelecaoEAAplicacaoNaoDeveQuebrar() {
        when(regraRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(aplicador().aplicar(UUID.randomUUID(), Map.of(), Map.of(), null,
                ExecucaoRegra.ESCOPO_GLOBAL, null)).isZero();
    }

    private int aplicarLimiar(UUID idRegra, long valorAtual) {
        return aplicador().aplicar(idRegra, Map.of("total", valorAtual), Map.of(), null,
                ExecucaoRegra.ESCOPO_GLOBAL, valorAtual);
    }

    private NovaNotificacao capturarComando() {
        ArgumentCaptor<NovaNotificacao> captor = ArgumentCaptor.forClass(NovaNotificacao.class);
        verify(publicador).publicar(captor.capture());
        return captor.getValue();
    }

    private RegraNotificacao regraPorLimiar(int limiar) {
        RegraNotificacao regra = new RegraNotificacao(
                GatilhoNotificacao.SST_DESVIOS_PENDENTES, "Desvios acumulados", null,
                CategoriaNotificacao.SEGURANCA, PrioridadeNotificacao.ALTA,
                "Desvios exigem atenção",
                "Há {{total}} desvios sem resolução, acima de {{limiar}}.", null, limiar, 720, false, true);
        regra.adicionarDestinatario(TipoDestinatarioRegra.PERMISSAO, null, "sst:desvio:tratar");
        return regra;
    }

    private RegraNotificacao regraPorOcorrencia() {
        RegraNotificacao regra = new RegraNotificacao(
                GatilhoNotificacao.DESCONTO_SOLICITADO, "Confirmação de desconto", null, null,
                PrioridadeNotificacao.BAIXA, "Desconto registrado",
                "Seu desconto por {{motivo}} em {{parcelas}}x foi registrado.",
                "/descontos/{{idDesconto}}", null, 0, false, true);
        regra.adicionarDestinatario(TipoDestinatarioRegra.PAPEL_DINAMICO, null, "solicitante");
        return regra;
    }

    /**
     * Fora do JPA o id da entidade é nulo, e o aplicador busca a regra pelo id. Sem
     * isto todo teste cairia no caminho de "regra não encontrada".
     */
    private UUID registrar(RegraNotificacao regra) {
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(regra, "id", id);
        when(regraRepository.findById(id)).thenReturn(Optional.of(regra));
        return id;
    }
}
