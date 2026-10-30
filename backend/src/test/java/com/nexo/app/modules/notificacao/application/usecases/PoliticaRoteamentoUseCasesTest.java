package com.nexo.app.modules.notificacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexo.app.modules.iam.ContextoAudienciaUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoRequest;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PoliticaRoteamentoNotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class PoliticaRoteamentoUseCasesTest {

    @Mock
    private PoliticaRoteamentoNotificacaoRepository repository;
    @Mock
    private UsuarioApi usuarioApi;

    private PoliticaRoteamentoUseCases useCases;

    @BeforeEach
    void preparar() {
        useCases = new PoliticaRoteamentoUseCases(repository, usuarioApi);
    }

    @Test
    void criaEAtualizaAListaCompletaDeRotasComUsuariosAtivos() {
        UUID claudio = UUID.randomUUID();
        UUID felipe = UUID.randomUUID();
        ativo(claudio);
        ativo(felipe);
        when(repository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var criada = useCases.criar(request("Diretoria", claudio, felipe));

        assertThat(criada.rotas()).hasSize(2);
        assertThat(criada.rotas()).extracting("usuarioId").containsExactly(claudio, felipe);

        PoliticaRoteamentoNotificacao entidade = new PoliticaRoteamentoNotificacao(
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA, "Antiga", null);
        var antiga = entidade.adicionarRota(10, false, claudio);
        antiga.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        entidade.adicionarRota(100, true, felipe);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entidade));

        var atualizada = useCases.atualizar(id, request("Nova", felipe, claudio));

        assertThat(atualizada.nome()).isEqualTo("Nova");
        assertThat(atualizada.rotas()).extracting("usuarioId").containsExactly(felipe, claudio);
    }

    @Test
    void listaBuscaAtivaEDesativa() {
        UUID usuarioA = UUID.randomUUID();
        UUID usuarioB = UUID.randomUUID();
        ativo(usuarioA);
        ativo(usuarioB);
        var politica = politicaValida(usuarioA, usuarioB);
        UUID id = UUID.randomUUID();
        when(repository.findAllByOrderByNomeAsc()).thenReturn(List.of(politica));
        when(repository.findById(id)).thenReturn(Optional.of(politica));
        when(repository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        assertThat(useCases.listar()).hasSize(1);
        assertThat(useCases.buscar(id).nome()).isEqualTo("Diretoria");
        assertThat(useCases.desativar(id).ativo()).isFalse();
        assertThat(useCases.ativar(id).ativo()).isTrue();
    }

    private PoliticaRoteamentoRequest request(String nome, UUID claudio, UUID felipe) {
        return new PoliticaRoteamentoRequest(
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA.getChave(),
                nome, null,
                List.of(
                        new PoliticaRoteamentoRequest.RotaRequest(10, false, claudio,
                                List.of(
                                        new PoliticaRoteamentoRequest.CondicaoRequest(
                                                "frota", OperadorCondicao.IGUAL, "true"),
                                        new PoliticaRoteamentoRequest.CondicaoRequest(
                                                "base", OperadorCondicao.EM,
                                                "JUAZEIRO,REMANSO,JACOBINA,BONFIM"))),
                        new PoliticaRoteamentoRequest.RotaRequest(100, true, felipe, List.of())));
    }

    private PoliticaRoteamentoNotificacao politicaValida(UUID especifico, UUID fallback) {
        var politica = new PoliticaRoteamentoNotificacao(
                GatilhoNotificacao.FINANCEIRO_SOLICITACAO_SUBMETIDA, "Diretoria", null);
        var rota = politica.adicionarRota(10, false, especifico);
        rota.adicionarCondicao("frota", OperadorCondicao.IGUAL, "true");
        politica.adicionarRota(100, true, fallback);
        return politica;
    }

    private void ativo(UUID id) {
        when(usuarioApi.buscarContextoAudiencia(id))
                .thenReturn(Optional.of(new ContextoAudienciaUsuario(
                        id, UUID.randomUUID(), "ADMINISTRATIVO")));
    }
}
