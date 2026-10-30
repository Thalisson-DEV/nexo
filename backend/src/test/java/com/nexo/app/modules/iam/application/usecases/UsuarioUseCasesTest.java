package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.UsuarioFilter;
import com.nexo.app.modules.iam.application.dto.UsuarioResponse;
import com.nexo.app.modules.iam.application.dto.UsuarioUpdateRequest;
import com.nexo.app.modules.iam.application.mappers.UsuarioMapper;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.TipoUsuario;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioUseCasesTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PerfilAcessoRepository perfilRepository;

    @Mock
    private UsuarioMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ColaboradorApi colaboradorApi;

    @Mock
    private SessaoAutenticacaoUseCases sessoes;

    @InjectMocks
    private UsuarioUseCases useCases;

    @Test
    @DisplayName("Deve atualizar o tipo de usuário com sucesso")
    void atualizar_DeveAtualizarTipoComSucesso() {
        // Arrange
        UUID id = UUID.randomUUID();
        PerfilAcesso perfil = mock(PerfilAcesso.class);
        Usuario usuario = new Usuario("login", "email@teste.com", "11999999999", "senha", perfil, null, TipoUsuario.ADMINISTRATIVO);
        
        UsuarioUpdateRequest request = new UsuarioUpdateRequest(
                "login",
                null,
                "email@teste.com",
                "11999999999",
                TipoUsuario.CAMPO,
                null
        );

        when(repository.findById(id)).thenReturn(Optional.of(usuario));
        when(repository.existsByLoginIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        when(repository.existsByEmailIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        when(repository.save(any())).thenReturn(usuario);
        when(mapper.toResponse(any())).thenReturn(mock(UsuarioResponse.class));

        // Act
        useCases.atualizar(id, request);

        // Assert
        assertEquals(TipoUsuario.CAMPO, usuario.getTipo());
        verify(repository).save(usuario);
    }

    @Test
    @DisplayName("Deve manter o tipo de usuário se não for enviado no request")
    void atualizar_DeveManterTipoSeNaoEnviado() {
        // Arrange
        UUID id = UUID.randomUUID();
        PerfilAcesso perfil = mock(PerfilAcesso.class);
        Usuario usuario = new Usuario("login", "email@teste.com", "11999999999", "senha", perfil, null, TipoUsuario.ADMINISTRATIVO);
        
        UsuarioUpdateRequest request = new UsuarioUpdateRequest(
                "login",
                null,
                "email@teste.com",
                "11999999999",
                null, // tipo nulo
                null
        );

        when(repository.findById(id)).thenReturn(Optional.of(usuario));
        when(repository.existsByLoginIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        when(repository.existsByEmailIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        when(repository.save(any())).thenReturn(usuario);
        when(mapper.toResponse(any())).thenReturn(mock(UsuarioResponse.class));

        // Act
        useCases.atualizar(id, request);

        // Assert
        assertEquals(TipoUsuario.ADMINISTRATIVO, usuario.getTipo());
        verify(repository).save(usuario);
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar usuário não encontrado")
    void atualizar_DeveLancarExcecaoSeNaoEncontrado() {
        UUID id = UUID.randomUUID();
        UsuarioUpdateRequest request = new UsuarioUpdateRequest(null, null, null, null, null, null);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.atualizar(id, request));
    }

    @Test
    @DisplayName("Listagem deve apresentar o colaborador dono de cada acesso em uma única consulta ao RH")
    void listar_DeveEnriquecerComOColaboradorVinculado() {
        UUID idColaboradorA = UUID.randomUUID();
        UUID idColaboradorB = UUID.randomUUID();
        UsuarioResponse semVinculo = respostaDe(null, "INTEGRA1");

        pagina(respostaDe(idColaboradorA, "MSOUZA1"), respostaDe(idColaboradorB, "JLIMA2"), semVinculo);
        when(colaboradorApi.buscarResumosPorIds(any())).thenReturn(Map.of(
                idColaboradorA, new ResumoColaborador(idColaboradorA, "MARIA SOUZA", "ELETRICISTA", "BASE NORTE", true),
                idColaboradorB, new ResumoColaborador(idColaboradorB, "JOAO LIMA", "SUPERVISOR", "BASE SUL", false)));

        PageResponse<UsuarioResponse> resultado = useCases.listar(filtroVazio(), PageRequest.of(0, 20));

        assertEquals("MARIA SOUZA", resultado.getContent().get(0).colaborador().nomeCompleto());
        assertEquals("ELETRICISTA", resultado.getContent().get(0).colaborador().funcao());
        assertEquals("BASE NORTE", resultado.getContent().get(0).colaborador().baseOperacional());
        assertFalse(resultado.getContent().get(1).colaborador().ativo(), "colaborador desligado deve aparecer como inativo");
        assertNull(resultado.getContent().get(2).colaborador(), "acesso sem pessoa vinculada não inventa colaborador");

        // Uma chamada para a página inteira: o vínculo é resolvido em lote, nunca por linha.
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.captor();
        verify(colaboradorApi, times(1)).buscarResumosPorIds(ids.capture());
        assertEquals(2, ids.getValue().size());
    }

    @Test
    @DisplayName("Vínculo apontando para colaborador inexistente não deve quebrar a listagem")
    void listar_DeveTolerarVinculoOrfao() {
        UUID idColaborador = UUID.randomUUID();
        pagina(respostaDe(idColaborador, "MSOUZA1"));
        when(colaboradorApi.buscarResumosPorIds(any())).thenReturn(Map.of());

        PageResponse<UsuarioResponse> resultado = useCases.listar(filtroVazio(), PageRequest.of(0, 20));

        assertNull(resultado.getContent().get(0).colaborador());
        assertEquals("MSOUZA1", resultado.getContent().get(0).login());
    }

    @Test
    @DisplayName("Filtrar por nome de colaborador deve traduzir o texto em ids junto ao RH")
    void listar_DeveResolverNomeDoColaboradorNoRh() {
        UUID idColaborador = UUID.randomUUID();
        when(colaboradorApi.buscarIdsPorTrechoDoNome("maria")).thenReturn(List.of(idColaborador));
        pagina(respostaDe(idColaborador, "MSOUZA1"));
        when(colaboradorApi.buscarResumosPorIds(any())).thenReturn(Map.of(
                idColaborador, new ResumoColaborador(idColaborador, "MARIA SOUZA", "ELETRICISTA", "BASE NORTE", true)));

        UsuarioFilter filtro = new UsuarioFilter(null, null, null, null, null, null, null,
                "maria", null, null, null);
        useCases.listar(filtro, PageRequest.of(0, 20));

        verify(colaboradorApi).buscarIdsPorTrechoDoNome("maria");
    }

    @Test
    @DisplayName("Busca rápida deve procurar também pelo nome da pessoa, não só por login e e-mail")
    void listar_BuscaGlobalDeveAlcancarNomeDoColaborador() {
        pagina();
        when(colaboradorApi.buscarIdsPorTrechoDoNome("souza")).thenReturn(List.of(UUID.randomUUID()));

        UsuarioFilter filtro = new UsuarioFilter(null, null, null, null, null, null, null,
                null, null, null, "souza");
        useCases.listar(filtro, PageRequest.of(0, 20));

        verify(colaboradorApi).buscarIdsPorTrechoDoNome("souza");
    }

    @Test
    @DisplayName("Sem filtro que fale de pessoas, a listagem não deve consultar o RH para montar a query")
    void listar_SemFiltroDeGente_NaoDeveResolverNomes() {
        pagina();

        useCases.listar(filtroVazio(), PageRequest.of(0, 20));

        verify(colaboradorApi, never()).buscarIdsPorTrechoDoNome(anyString());
    }

    @Test
    @DisplayName("Busca por ID deve trazer o colaborador vinculado")
    void buscarPorId_DeveIncluirColaborador() {
        UUID id = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(mock(Usuario.class)));
        when(mapper.toResponse(any())).thenReturn(respostaDe(idColaborador, "MSOUZA1"));
        when(colaboradorApi.buscarResumosPorIds(any())).thenReturn(Map.of(
                idColaborador, new ResumoColaborador(idColaborador, "MARIA SOUZA", "ELETRICISTA", "BASE NORTE", true)));

        UsuarioResponse resultado = useCases.buscarPorId(id);

        assertEquals("MARIA SOUZA", resultado.colaborador().nomeCompleto());
    }

    private UsuarioFilter filtroVazio() {
        return new UsuarioFilter(null, null, null, null, null, null, null, null, null, null, null);
    }

    private UsuarioResponse respostaDe(UUID idColaborador, String login) {
        return new UsuarioResponse(UUID.randomUUID(), idColaborador, login, login.toLowerCase() + "@exemplo.com.br",
                null, TipoUsuario.ADMINISTRATIVO, null, "light", "blue", true, true, null);
    }

    @SuppressWarnings("unchecked")
    private void pagina(UsuarioResponse... respostas) {
        List<Usuario> entidades = List.of(respostas).stream().map(r -> mock(Usuario.class)).toList();
        Page<Usuario> page = new PageImpl<>(entidades, PageRequest.of(0, 20), entidades.size());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        if (respostas.length > 0) {
            when(mapper.toResponse(any())).thenReturn(respostas[0],
                    java.util.Arrays.copyOfRange(respostas, 1, respostas.length));
        }
    }
}
