package com.nexo.app.modules.automacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.iam.ContatoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;

class ResolvedorDestinatariosTest {

    private static final UUID RH_JUAZEIRO = UUID.randomUUID();
    private static final UUID RH_REMANSO = UUID.randomUUID();

    private UsuarioApi usuarios;
    private EnriquecedorEntidades entidades;
    private ResolvedorDestinatarios resolvedor;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioApi.class);
        entidades = mock(EnriquecedorEntidades.class);
        resolvedor = new ResolvedorDestinatarios(usuarios, mock(ColaboradorApi.class), entidades);
        when(usuarios.buscarIdsAtivosComPermissao("desconto:baixar")).thenReturn(List.of(RH_JUAZEIRO, RH_REMANSO));
        when(usuarios.buscarContatos(any())).thenAnswer(inv -> {
            Map<UUID, ContatoUsuario> contatos = new HashMap<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                contatos.put((UUID) id, new ContatoUsuario((UUID) id, "rh", "rh@exemplo.com.br", null, true));
            }
            return contatos;
        });
        when(entidades.pessoas(any())).thenReturn(List.of(
                Map.of("usuario_id", RH_JUAZEIRO.toString(), "base", "JUAZEIRO"),
                Map.of("usuario_id", RH_REMANSO.toString(), "base", "Remanso")));
    }

    @Test
    void permissaoSemBaseIncluiTodos() {
        var r = resolvedor.resolverTudo(List.of(Map.of("tipo", "permissao", "valor", "desconto:baixar")), contexto());
        assertThat(r.usuarios()).containsExactlyInAnyOrder(RH_JUAZEIRO, RH_REMANSO);
    }

    @Test
    void soDaBaseFicaComQuemEDaBaseSemDiferenciarMaiusculas() {
        var r = resolvedor.resolverTudo(List.of(Map.of("tipo", "permissao", "valor", "desconto:baixar", "base", "remanso")), contexto());
        assertThat(r.usuarios()).containsExactly(RH_REMANSO);
        assertThat(r.avisos()).isEmpty();
    }

    @Test
    void baseSemNinguemNaoPerdeOAvisoEExplica() {
        var r = resolvedor.resolverTudo(List.of(Map.of("tipo", "permissao", "valor", "desconto:baixar", "base", "BONFIM")), contexto());
        assertThat(r.usuarios()).containsExactlyInAnyOrder(RH_JUAZEIRO, RH_REMANSO);
        assertThat(r.avisos()).anyMatch(a -> a.contains("BONFIM") && a.contains("enviado a todos"));
    }

    private static ContextoAcao contexto() {
        return new ContextoAcao(UUID.randomUUID(), "Teste", UUID.randomUUID(), "TESTE", "avisar", "", 1, OffsetDateTime.now(),
                Map.of(), (v, extra) -> v, null, UUID.randomUUID(), Set.of(), null, false);
    }
}
