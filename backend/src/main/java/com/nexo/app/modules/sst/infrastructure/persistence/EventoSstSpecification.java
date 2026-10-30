package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.EventoSstFilter;
import com.nexo.app.modules.sst.domain.EnvolvidoEvento;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.TecnicoSegurancaEvento;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Filtros dinâmicos da listagem de eventos de SST.
 *
 * <p>Duas particularidades valem o comentário. A primeira é que quase tudo o que a tela
 * mostra e filtra está <b>no próprio evento</b> — atividade, gestor, base, equipe e
 * veículo são snapshots —, então a maior parte dos recortes é um {@code IN} direto, sem
 * join e sem consulta a outro módulo.</p>
 *
 * <p>A segunda é a busca por nome de pessoa, que chega aqui já resolvida quando é de
 * colaborador: o trecho vira ids dentro do RH, único módulo com a coluna normalizada
 * de busca. O nome do terceiro, que só existe aqui e está cifrado, é comparado pelo
 * blind index — igualdade exata, por construção.</p>
 */
public final class EventoSstSpecification {

    private EventoSstSpecification() {
    }

    /**
     * Listagem geral de eventos.
     *
     * @param idsColaboradoresPorNome ids resolvidos no RH a partir de
     *                                {@code filter.nomeColaborador()}. {@code null}
     *                                significa "não foi pedido filtro por nome";
     *                                lista <b>vazia</b> significa "pedido, e ninguém
     *                                casou" — que precisa devolver nada, e não tudo.
     */
    public static Specification<EventoSst> geral(EventoSstFilter filter, List<UUID> idsColaboradoresPorNome) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));

            if (filter != null) {
                SpecificationUtils.filtrarPor(filter.tipo(), predicates, root.get("tipo"));
                SpecificationUtils.filtrarPor(filter.status(), predicates, root.get("status"));
                SpecificationUtils.filtrarPor(filter.idClassificacao(), predicates, root.get("idClassificacao"));
                SpecificationUtils.filtrarPor(filter.idAtividade(), predicates, root.get("idAtividade"));
                SpecificationUtils.filtrarPor(filter.idGestor(), predicates, root.get("idGestor"));
                SpecificationUtils.filtrarPor(filter.idProcesso(), predicates, root.get("idProcesso"));
                SpecificationUtils.filtrarPor(filter.idBaseOperacional(), predicates, root.get("idBaseOperacional"));
                SpecificationUtils.filtrarPor(filter.idEquipe(), predicates, root.get("idEquipe"));
                SpecificationUtils.filtrarPor(filter.idVeiculo(), predicates, root.get("idVeiculo"));
                SpecificationUtils.filtrarPor(filter.idLocalLesao(), predicates, root.get("idLocalLesao"));
                SpecificationUtils.filtrarPor(filter.telemetria(), predicates, root.get("telemetria"));
                SpecificationUtils.filtrarPor(filter.filmagem(), predicates, root.get("filmagem"));

                // O evento guarda a base; o centro de custo é resolvido nas bases dele.
                if (!SelecaoMultipla.vazia(filter.idCentroCusto())) {
                    predicates.add(root.get("idBaseOperacional")
                            .in(EscopoOrganizacional.basesDosCentrosCusto(query, cb, filter.idCentroCusto())));
                }

                if (filter.possuiCat() != null) {
                    predicates.add(cb.equal(root.get("possuiCat"), filter.possuiCat()));
                }
                if (filter.comLesao() != null) {
                    Predicate tipoComLesao = root.get("tipo").in(tiposComLesao());
                    predicates.add(filter.comLesao() ? tipoComLesao : cb.not(tipoComLesao));
                }

                filtrarPeriodos(predicates, cb, root, filter);
                filtrarPessoas(predicates, cb, root, filter, idsColaboradoresPorNome);
                filtrarBuscaRapida(predicates, cb, root, filter.buscaGlobal());
            }

            query.distinct(true);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Um evento específico, ainda ativo. */
    public static Specification<EventoSst> porId(UUID idEvento) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("id"), idEvento),
                cb.isTrue(root.get("ativo")));
    }

    /**
     * Eventos ocorridos no período, para os indicadores. Reaproveita os mesmos recortes
     * da listagem para que o dashboard e a tabela nunca discordem sobre o que entra.
     */
    public static Specification<EventoSst> doPeriodo(EventoSstFilter filter, List<UUID> idsColaboradoresPorNome,
                                                     java.time.OffsetDateTime inicio,
                                                     java.time.OffsetDateTime fim) {
        return geral(filter, idsColaboradoresPorNome).and((root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("dataOcorrencia"), inicio),
                cb.lessThanOrEqualTo(root.get("dataOcorrencia"), fim)));
    }

    // --------------------------------------------------------------------- //
    //  Recortes
    // --------------------------------------------------------------------- //

    private static void filtrarPeriodos(List<Predicate> predicates, CriteriaBuilder cb,
                                        Root<EventoSst> root, EventoSstFilter filter) {
        if (filter.dataOcorrenciaDe() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("dataOcorrencia"), filter.dataOcorrenciaDe()));
        }
        if (filter.dataOcorrenciaAte() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("dataOcorrencia"), filter.dataOcorrenciaAte()));
        }
        if (filter.dataComunicacaoDe() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("dataComunicacao"), filter.dataComunicacaoDe()));
        }
        if (filter.dataComunicacaoAte() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("dataComunicacao"), filter.dataComunicacaoAte()));
        }
    }

    /**
     * Recortes por gente. Cada critério ganha o <b>seu</b> join: reaproveitar um único
     * join faria "envolvido X" e "envolvido chamado Y" exigirem a mesma linha, e a
     * pergunta que o usuário fez foi por eventos, não por pessoas coincidentes.
     */
    private static void filtrarPessoas(List<Predicate> predicates, CriteriaBuilder cb, Root<EventoSst> root,
                                       EventoSstFilter filter, List<UUID> idsColaboradoresPorNome) {
        if (!SelecaoMultipla.vazia(filter.idTecnico())) {
            Join<EventoSst, TecnicoSegurancaEvento> tecnicos = root.join("tecnicos");
            predicates.add(tecnicos.get("idColaborador").in(filter.idTecnico()));
        }

        if (!SelecaoMultipla.vazia(filter.idColaboradorEnvolvido())) {
            Join<EventoSst, EnvolvidoEvento> envolvidos = root.join("envolvidos");
            predicates.add(envolvidos.get("idColaborador").in(filter.idColaboradorEnvolvido()));
        }

        if (idsColaboradoresPorNome != null) {
            if (idsColaboradoresPorNome.isEmpty()) {
                // Pediu-se um nome que ninguém tem: o resultado correto é vazio.
                predicates.add(cb.disjunction());
            } else {
                Join<EventoSst, EnvolvidoEvento> porNome = root.join("envolvidos");
                predicates.add(porNome.get("idColaborador").in(idsColaboradoresPorNome));
            }
        }

        if (filter.nomeTerceiro() != null && !filter.nomeTerceiro().isBlank()) {
            Join<EventoSst, EnvolvidoEvento> terceiros = root.join("envolvidos");
            predicates.add(cb.and(
                    cb.equal(terceiros.get("tipo"), TipoEnvolvido.TERCEIRO),
                    cb.equal(terceiros.get("nomeHash"),
                            HashUtils.gerarBlindIndex(filter.nomeTerceiro().trim().toUpperCase()))));
        }
    }

    /** Busca rápida sobre as colunas em texto claro — nome cifrado não aceita {@code LIKE}. */
    private static void filtrarBuscaRapida(List<Predicate> predicates, CriteriaBuilder cb,
                                           Root<EventoSst> root, String termo) {
        SpecificationUtils.buscaRapida(termo, predicates, cb, List.<Expression<String>>of(
                root.get("numeroRegistro"),
                root.get("descricao"),
                root.get("localOcorrencia"),
                root.get("atividadeNome"),
                root.get("baseNome")));
    }

    private static Collection<TipoEventoSst> tiposComLesao() {
        return Arrays.stream(TipoEventoSst.values()).filter(TipoEventoSst::implicaLesao).toList();
    }
}
