package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.application.dto.UsuarioFilter;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UsuarioSpecification {

    private UsuarioSpecification() {
        // Utility class
    }

    /**
     * <p>Só existe esta forma, com os colaboradores já resolvidos: uma sobrecarga sem
     * eles seria um jeito silencioso de perder o filtro por nome da pessoa. Quem não
     * filtra por gente passa {@link ColaboradoresResolvidos#nenhum()}.</p>
     *
     * @param colaboradores ids que o RH resolveu para os filtros que dependem do nome da
     *                      pessoa — ver {@link ColaboradoresResolvidos}
     */
    public static Specification<Usuario> doFiltro(UsuarioFilter filter, ColaboradoresResolvidos colaboradores) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.login() != null && !filter.login().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("login")), "%" + filter.login().toLowerCase() + "%"));
            }

            if (filter.email() != null && !filter.email().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + filter.email().toLowerCase() + "%"));
            }

            if (filter.idPerfil() != null) {
                predicates.add(cb.equal(root.get("perfil").get("id"), filter.idPerfil()));
            }

            if (filter.tipo() != null) {
                predicates.add(cb.equal(root.get("tipo"), filter.tipo()));
            }

            if (filter.ativo() != null) {
                predicates.add(cb.equal(root.get("ativo"), filter.ativo()));
            }

            if (filter.idColaborador() != null) {
                predicates.add(cb.equal(root.get("idColaborador"), filter.idColaborador()));
            }

            if (filter.vinculadoAColaborador() != null) {
                predicates.add(Boolean.TRUE.equals(filter.vinculadoAColaborador())
                        ? cb.isNotNull(root.get("idColaborador"))
                        : cb.isNull(root.get("idColaborador")));
            }

            // Filtrou por nome e o RH não devolveu ninguém: a listagem tem de vir vazia.
            // Ignorar o filtro aqui seria mostrar a base inteira como se fosse resultado.
            if (colaboradores.filtrouPorNome()) {
                predicates.add(colaboradores.porNome().isEmpty()
                        ? cb.disjunction()
                        : root.get("idColaborador").in(colaboradores.porNome()));
            }

            if (filter.dataCriacaoInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), filter.dataCriacaoInicio()));
            }

            if (filter.dataCriacaoFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), filter.dataCriacaoFim()));
            }

            adicionarBuscaRapida(filter, colaboradores, predicates, cb, root);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * A busca rápida é um OR só: o mesmo texto pode ser um login, um e-mail ou o nome da
     * pessoa. Os candidatos por nome entram como {@code IN} sobre o vínculo, ao lado dos
     * {@code LIKE} de login e e-mail, para que quem digita "maria" encontre o acesso sem
     * precisar saber que ele se chama {@code MSOUZA1}.
     */
    private static void adicionarBuscaRapida(UsuarioFilter filter,
                                             ColaboradoresResolvidos colaboradores,
                                             List<Predicate> predicates,
                                             CriteriaBuilder cb,
                                             Root<Usuario> root) {
        List<Predicate> alternativas = new ArrayList<>();
        SpecificationUtils.buscaRapida(filter.buscaGlobal(), alternativas, cb, root, "login", "email");

        if (!colaboradores.porBuscaGlobal().isEmpty()) {
            alternativas.add(root.get("idColaborador").in(colaboradores.porBuscaGlobal()));
        }

        if (!alternativas.isEmpty()) {
            predicates.add(cb.or(alternativas.toArray(new Predicate[0])));
        }
    }
}
