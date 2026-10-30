package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.Veiculo;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaFilter;
import com.nexo.app.modules.sst.domain.ColaboradorInspecionado;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Specification para filtros dinâmicos de Inspeção de Segurança.
 * Suporta filtros por cabeçalho, colaborador inspecionado, desvios e busca global
 * nas entidades relacionadas (Veículo, Equipe, Inspetor) via Subqueries JPA.
 */
public class InspecaoSegurancaSpecification {

    private InspecaoSegurancaSpecification() {}

    public static Specification<InspecaoSeguranca> filterBy(InspecaoSegurancaFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            SpecificationUtils.filtrarPor(filter.idFormulario(), predicates, root.get("idFormulario"));
            SpecificationUtils.filtrarPor(filter.idTipoInspecao(), predicates, root.get("idTipoInspecao"));
            // Processo da inspeção (atividade/equipe operacional inspecionada)
            SpecificationUtils.filtrarPor(filter.idProcesso(), predicates, root.get("idProcesso"));

            // Processo do inspetor (quem realizou a inspeção)
            if (!SelecaoMultipla.vazia(filter.idProcessoInspetor())) {
                Subquery<UUID> subColabUser = query.subquery(UUID.class);
                Root<Colaborador> colabUser = subColabUser.from(Colaborador.class);
                subColabUser.select(colabUser.get("idUsuario"))
                        .where(
                                cb.isNotNull(colabUser.get("idUsuario")),
                                colabUser.get("processo").get("id").in(filter.idProcessoInspetor())
                        );

                Subquery<UUID> subColabId = query.subquery(UUID.class);
                Root<Colaborador> colabId = subColabId.from(Colaborador.class);
                subColabId.select(colabId.get("id"))
                        .where(
                                colabId.get("processo").get("id").in(filter.idProcessoInspetor())
                        );

                predicates.add(cb.or(
                        root.get("idInspetor").in(subColabUser),
                        root.get("idInspetor").in(subColabId)
                ));
            }
            SpecificationUtils.filtrarPor(filter.idInspetor(), predicates, root.get("idInspetor"));
            SpecificationUtils.filtrarPor(filter.idVeiculo(), predicates, root.get("idVeiculo"));
            SpecificationUtils.filtrarPor(filter.idEquipe(), predicates, root.get("idEquipe"));
            SpecificationUtils.filtrarPor(filter.idBaseOperacional(), predicates, root.get("idBaseOperacional"));
            SpecificationUtils.filtrarPor(filter.status(), predicates, root.get("status"));

            // A inspeção guarda apenas a base; o centro de custo é resolvido nas bases dele.
            if (!SelecaoMultipla.vazia(filter.idCentroCusto())) {
                predicates.add(root.get("idBaseOperacional")
                        .in(EscopoOrganizacional.basesDosCentrosCusto(query, cb, filter.idCentroCusto())));
            }

            if (filter.dataRealizacaoDe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataRealizacao"), filter.dataRealizacaoDe()));
            }
            if (filter.dataRealizacaoAte() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataRealizacao"), filter.dataRealizacaoAte()));
            }

            // Filtro por colaborador inspecionado (join)
            if (!SelecaoMultipla.vazia(filter.idColaborador())) {
                Join<InspecaoSeguranca, ColaboradorInspecionado> joinColab = root.join("colaboradoresInspecionados");
                predicates.add(joinColab.get("idColaborador").in(filter.idColaborador()));
            }

            // Filtros por desvios (join)
            if (!SelecaoMultipla.vazia(filter.gravidadeDesvio()) || Boolean.TRUE.equals(filter.apenasComDesvios())) {
                Join<InspecaoSeguranca, DesvioSeguranca> joinDesvios = root.join("desvios");
                SpecificationUtils.filtrarPor(filter.gravidadeDesvio(), predicates, joinDesvios.get("gravidade"));
            }

            // --- Busca Global em Observações, Veículo, Equipe e Inspetor ---
            if (StringUtils.hasText(filter.buscaGlobal())) {
                String pattern = "%" + SpecificationUtils.normalizarTermo(filter.buscaGlobal()) + "%";
                List<Predicate> ors = new ArrayList<>();

                // 1. Busca nas observações da inspeção
                ors.add(cb.like(cb.function("immutable_unaccent", String.class, cb.lower(root.get("observacoes"))), pattern));

                // 2. Subquery em Veículo (busca por placa ou modelo)
                Subquery<UUID> subVeiculo = query.subquery(UUID.class);
                Root<Veiculo> rootVeiculo = subVeiculo.from(Veiculo.class);
                subVeiculo.select(rootVeiculo.get("id"))
                        .where(
                                cb.or(
                                        cb.like(cb.function("immutable_unaccent", String.class, cb.lower(rootVeiculo.get("placa"))), pattern),
                                        cb.like(cb.function("immutable_unaccent", String.class, cb.lower(rootVeiculo.get("modelo"))), pattern)
                                )
                        );
                ors.add(root.get("idVeiculo").in(subVeiculo));

                // 3. Subquery em Equipe (busca pelo código/nome da equipe)
                Subquery<UUID> subEquipe = query.subquery(UUID.class);
                Root<Equipe> rootEquipe = subEquipe.from(Equipe.class);
                subEquipe.select(rootEquipe.get("id"))
                        .where(cb.like(cb.function("immutable_unaccent", String.class, cb.lower(rootEquipe.get("codigo"))), pattern));
                ors.add(root.get("idEquipe").in(subEquipe));

                // 4. Subquery em Colaborador via idUsuario do Inspetor (busca pelo nomeBusca normalizado)
                Subquery<UUID> subColaboradorPorUsuario = query.subquery(UUID.class);
                Root<Colaborador> rootColabUser = subColaboradorPorUsuario.from(Colaborador.class);
                subColaboradorPorUsuario.select(rootColabUser.get("idUsuario"))
                        .where(
                                cb.isNotNull(rootColabUser.get("idUsuario")),
                                cb.like(cb.function("immutable_unaccent", String.class, cb.lower(rootColabUser.get("nomeBusca"))), pattern)
                        );
                ors.add(root.get("idInspetor").in(subColaboradorPorUsuario));

                // 5. Subquery em Colaborador via id do Inspetor (caso o idInspetor referencie diretamente o Colaborador)
                Subquery<UUID> subColaboradorPorId = query.subquery(UUID.class);
                Root<Colaborador> rootColabId = subColaboradorPorId.from(Colaborador.class);
                subColaboradorPorId.select(rootColabId.get("id"))
                        .where(
                                cb.like(cb.function("immutable_unaccent", String.class, cb.lower(rootColabId.get("nomeBusca"))), pattern)
                        );
                ors.add(root.get("idInspetor").in(subColaboradorPorId));

                // 6. Subquery em Usuários (busca pelo login do Inspetor)
                Subquery<UUID> subUsuario = query.subquery(UUID.class);
                Root<Usuario> rootUser = subUsuario.from(Usuario.class);
                subUsuario.select(rootUser.get("id"))
                        .where(cb.like(cb.function("immutable_unaccent", String.class, cb.lower(rootUser.get("login"))), pattern));
                ors.add(root.get("idInspetor").in(subUsuario));

                predicates.add(cb.or(ors.toArray(new Predicate[0])));
            }

            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
