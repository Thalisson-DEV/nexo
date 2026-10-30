package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.application.dto.UsuarioFilter;
import com.nexo.app.modules.iam.domain.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * O que se protege aqui é a diferença entre "não filtrei por nome" e "filtrei e não
 * achei ninguém": confundir as duas faz a tela devolver a base inteira como se fosse
 * resultado da busca, que é o pior tipo de erro de filtro — o que parece ter funcionado.
 */
class UsuarioSpecificationTest {

    private Root<Usuario> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> path;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        path = mock(Path.class);

        when(root.get(anyString())).thenReturn(path);
        lenient().when(path.in(anyCollection())).thenReturn(mock(Predicate.class));
        lenient().when(cb.disjunction()).thenReturn(mock(Predicate.class));
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
    }

    @Test
    @DisplayName("Filtro por nome sem nenhum colaborador correspondente deve zerar o resultado")
    void nomeSemCorrespondencia_DeveTornarAConsultaImpossivel() {
        UsuarioSpecification
                .doFiltro(filtroPorNome(), new ColaboradoresResolvidos(List.of(), List.of()))
                .toPredicate(root, query, cb);

        verify(cb).disjunction();
        verify(path, never()).in(anyCollection());
    }

    @Test
    @DisplayName("Filtro por nome com correspondentes deve restringir pelo vínculo com o colaborador")
    void nomeComCorrespondencia_DeveRestringirPeloVinculo() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        UsuarioSpecification
                .doFiltro(filtroPorNome(), new ColaboradoresResolvidos(ids, List.of()))
                .toPredicate(root, query, cb);

        verify(root).get("idColaborador");
        verify(path).in(ids);
        verify(cb, never()).disjunction();
    }

    @Test
    @DisplayName("Sem filtro por nome, colaborador nenhum deve influenciar a consulta")
    void semFiltroPorNome_NaoDeveTocarNoVinculo() {
        UsuarioFilter filtro = new UsuarioFilter("msouza", null, null, null, null, null, null,
                null, null, null, null);

        UsuarioSpecification.doFiltro(filtro, ColaboradoresResolvidos.nenhum()).toPredicate(root, query, cb);

        verify(cb, never()).disjunction();
        verify(path, never()).in(anyCollection());
    }

    @Test
    @DisplayName("Busca rápida deve somar o vínculo com o colaborador às alternativas de login e e-mail")
    void buscaGlobal_DeveIncluirColaboradorNoOr() {
        List<UUID> ids = List.of(UUID.randomUUID());
        UsuarioFilter filtro = new UsuarioFilter(null, null, null, null, null, null, null,
                null, null, null, "souza");
        when(cb.like(any(), anyString())).thenReturn(mock(Predicate.class));
        when(cb.function(anyString(), eq(String.class), any())).thenReturn(mock(jakarta.persistence.criteria.Expression.class));

        UsuarioSpecification.doFiltro(filtro, new ColaboradoresResolvidos(null, ids)).toPredicate(root, query, cb);

        verify(path).in(ids);
        // login, e-mail e colaborador entram como alternativas de um mesmo OR.
        verify(cb, atLeastOnce()).or(any(Predicate[].class));
    }

    @Test
    @DisplayName("Filtro de acessos sem pessoa vinculada deve procurar vínculo nulo")
    void vinculadoFalse_DeveExigirVinculoNulo() {
        UsuarioFilter filtro = new UsuarioFilter(null, null, null, null, null, null, null,
                null, null, false, null);

        UsuarioSpecification.doFiltro(filtro, ColaboradoresResolvidos.nenhum()).toPredicate(root, query, cb);

        verify(cb).isNull(path);
        verify(cb, never()).isNotNull(path);
    }

    private UsuarioFilter filtroPorNome() {
        return new UsuarioFilter(null, null, null, null, null, null, null,
                "maria", null, null, null);
    }
}
