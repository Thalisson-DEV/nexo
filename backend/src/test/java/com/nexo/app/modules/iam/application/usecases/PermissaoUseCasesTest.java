package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.PermissaoRequest;
import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.modules.iam.application.mappers.PermissaoMapper;
import com.nexo.app.modules.iam.domain.Permissao;
import com.nexo.app.modules.iam.infrastructure.persistence.PermissaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissaoUseCasesTest {

    @Mock
    private PermissaoRepository repository;

    @Mock
    private PermissaoMapper mapper;

    @InjectMocks
    private PermissaoUseCases useCases;

    @Test
    @DisplayName("Deve criar uma permissão com sucesso")
    void criar_DeveCriarComSucesso() {
        PermissaoRequest request = new PermissaoRequest("estoque:ler");
        when(repository.existsByNomeIgnoreCase(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new PermissaoResponse(UUID.randomUUID(), "estoque:ler"));

        PermissaoResponse response = useCases.criar(request);

        assertNotNull(response);
        assertEquals("estoque:ler", response.nome());
        verify(repository).save(any(Permissao.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar permissão com nome duplicado")
    void criar_DeveLancarExcecaoSeNomeDuplicado() {
        PermissaoRequest request = new PermissaoRequest("estoque:ler");
        when(repository.existsByNomeIgnoreCase("estoque:ler")).thenReturn(true);

        assertThrows(InvarianteVioladaException.class, () -> useCases.criar(request));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atualizar uma permissão com sucesso")
    void atualizar_DeveAtualizarComSucesso() {
        UUID id = UUID.randomUUID();
        PermissaoRequest request = new PermissaoRequest("estoque:editar");
        Permissao permissao = new Permissao("estoque:ler");

        when(repository.findById(id)).thenReturn(Optional.of(permissao));
        when(repository.existsByNomeIgnoreCase("estoque:editar")).thenReturn(false);
        when(repository.save(any())).thenReturn(permissao);
        when(mapper.toResponse(any())).thenReturn(new PermissaoResponse(id, "estoque:editar"));

        PermissaoResponse response = useCases.atualizar(id, request);

        assertNotNull(response);
        assertEquals("estoque:editar", response.nome());
        verify(repository).save(permissao);
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar permissão não encontrada")
    void atualizar_DeveLancarExcecaoSeNaoEncontrada() {
        UUID id = UUID.randomUUID();
        PermissaoRequest request = new PermissaoRequest("novo:nome");
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.atualizar(id, request));
    }

    @Test
    @DisplayName("Deve ativar uma permissão")
    void alterarStatus_DeveAtivarPermissao() {
        UUID id = UUID.randomUUID();
        Permissao permissao = new Permissao("estoque:ler");
        permissao.desativar();

        when(repository.findById(id)).thenReturn(Optional.of(permissao));

        useCases.alterarStatus(id, true);

        assertTrue(permissao.isAtivo());
        verify(repository).save(permissao);
    }
}
