package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.modules.iam.application.dto.PermissoesPerfilRequest;
import com.nexo.app.modules.iam.application.mappers.PerfilAcessoMapper;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.Permissao;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.PermissaoRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilAcessoUseCasesTest {

    @Mock
    private PerfilAcessoRepository repository;
    @Mock
    private PermissaoRepository permissaoRepository;
    @Mock
    private PerfilAcessoMapper mapper;

    @InjectMocks
    private PerfilAcessoUseCases useCases;

    @Test
    void definePermissoesDeUmaVezMarcandoEDesmarcandoNaMesmaGravacao() {
        Permissao ler = permissao("estoque:saldo:read");
        Permissao escrever = permissao("estoque:saldo:write");
        Permissao aprovar = permissao("estoque:solicitacao:approve");
        PerfilAcesso perfil = new PerfilAcesso("ALMOX");
        perfil.adicionarPermissao(ler);
        perfil.adicionarPermissao(escrever);
        UUID idPerfil = UUID.randomUUID();

        when(repository.findById(idPerfil)).thenReturn(Optional.of(perfil));
        when(permissaoRepository.findAllById(Set.of(ler.getId(), aprovar.getId()))).thenReturn(List.of(ler, aprovar));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<PermissaoResponse> resultado = useCases.definirPermissoes(
                idPerfil, new PermissoesPerfilRequest(Set.of(ler.getId(), aprovar.getId())));

        assertThat(perfil.getPermissoes()).containsExactlyInAnyOrder(ler, aprovar);
        assertThat(resultado).extracting(PermissaoResponse::nome)
                .containsExactly("estoque:saldo:read", "estoque:solicitacao:approve");
        verify(repository).save(perfil);
    }

    @Test
    void listaVaziaRemoveTodasAsPermissoes() {
        PerfilAcesso perfil = new PerfilAcesso("ALMOX");
        perfil.adicionarPermissao(permissao("estoque:saldo:read"));
        UUID idPerfil = UUID.randomUUID();
        when(repository.findById(idPerfil)).thenReturn(Optional.of(perfil));
        when(permissaoRepository.findAllById(Set.of())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(useCases.definirPermissoes(idPerfil, new PermissoesPerfilRequest(Set.of()))).isEmpty();
        assertThat(perfil.getPermissoes()).isEmpty();
    }

    @Test
    void recusaPermissaoInexistenteSemAlterarOPerfil() {
        PerfilAcesso perfil = new PerfilAcesso("ALMOX");
        UUID idPerfil = UUID.randomUUID();
        UUID inexistente = UUID.randomUUID();
        when(repository.findById(idPerfil)).thenReturn(Optional.of(perfil));
        when(permissaoRepository.findAllById(Set.of(inexistente))).thenReturn(List.of());

        assertThatThrownBy(() -> useCases.definirPermissoes(idPerfil, new PermissoesPerfilRequest(Set.of(inexistente))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    private static Permissao permissao(String nome) {
        Permissao permissao = new Permissao(nome);
        ReflectionTestUtils.setField(permissao, "id", UUID.randomUUID());
        return permissao;
    }
}
