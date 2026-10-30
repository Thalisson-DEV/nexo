package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroFilter;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Filtros da busca no repositório documental.
 *
 * <p>A busca rápida cobre o nome do arquivo, número e chave do documento, código
 * pagável, emissor e descrição. Ficam de fora os dados do fornecedor cadastrado, que vivem em outra
 * tabela: para procurar por ele existe o filtro dedicado, que é exato e não
 * depende de o nome ter sido digitado igual.</p>
 */
public final class DocumentoFinanceiroSpecification {

    private DocumentoFinanceiroSpecification() {
    }

    public static Specification<DocumentoFinanceiro> comFiltros(DocumentoFinanceiroFilter filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro == null) {
                predicates.add(cb.isTrue(root.get("ativo")));
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            List<Expression<String>> camposDeBusca = List.of(
                    root.get("nomeArquivo"),
                    root.get("numeroDocumento"),
                    root.get("chaveAcesso"),
                    root.get("codigoPagamento"),
                    root.get("emissorNome"),
                    root.get("descricao"));
            SpecificationUtils.buscaRapida(filtro.buscaGlobal(), predicates, cb, camposDeBusca);

            SpecificationUtils.filtrarPor(filtro.tipo(), predicates, root.get("tipo"));
            SpecificationUtils.filtrarPor(filtro.situacao(), predicates, root.get("situacao"));
            SpecificationUtils.filtrarPor(filtro.fornecedorId(), predicates, root.get("fornecedorId"));

            igual(predicates, cb, root, "baseId", filtro.baseId());
            igual(predicates, cb, root, "processoId", filtro.processoId());
            igual(predicates, cb, root, "obraId", filtro.obraId());
            igual(predicates, cb, root, "numeroDocumento", filtro.numeroDocumento());

            if (filtro.chaveAcesso() != null && !filtro.chaveAcesso().isBlank()) {
                // A chave é guardada só com dígitos; comparar o texto colado sem
                // normalizar faria uma chave com pontuação nunca encontrar nada.
                predicates.add(cb.equal(root.get("chaveAcesso"), filtro.chaveAcesso().replaceAll("\\D", "")));
            }

            Path<?> origem = root.get("origem");
            igual(predicates, cb, origem, "tipo", filtro.origemTipo());
            igual(predicates, cb, origem, "solicitacaoId", filtro.origemSolicitacaoId());
            igual(predicates, cb, origem, "numero", filtro.origemNumero());
            if (Boolean.TRUE.equals(filtro.apenasAvulsos())) {
                predicates.add(cb.isNull(origem.get("solicitacaoId")));
            }

            // A competência é gravada no dia 1; recortar pelo mês da data recebida
            // evita que "de 15/08" deixe agosto inteiro de fora.
            intervaloDeData(predicates, cb, root, "competencia",
                    primeiroDiaDoMes(filtro.competenciaInicio()), primeiroDiaDoMes(filtro.competenciaFim()));
            intervaloDeData(predicates, cb, root, "dataEmissao", filtro.emissaoInicio(), filtro.emissaoFim());
            intervaloDeData(predicates, cb, root, "dataVencimento",
                    filtro.vencimentoInicio(), filtro.vencimentoFim());

            if (Boolean.TRUE.equals(filtro.apenasVencidos())) {
                predicates.add(cb.lessThan(root.get("dataVencimento"), LocalDate.now()));
                predicates.add(root.get("situacao").in(situacoesEmAberto()));
            }
            if (Boolean.TRUE.equals(filtro.apenasSemIdentificacao())) {
                predicates.add(root.get("tipo").in(tiposQueExigemIdentificacao()));
                predicates.add(cb.or(
                        cb.isNull(root.get("numeroDocumento")),
                        cb.equal(cb.trim(root.get("numeroDocumento")), "")));
            }

            if (filtro.valorMinimo() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("valor"), filtro.valorMinimo()));
            }
            if (filtro.valorMaximo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("valor"), filtro.valorMaximo()));
            }

            OffsetDateTime criacaoInicio = filtro.dataCriacaoInicio();
            if (criacaoInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), criacaoInicio));
            }
            OffsetDateTime criacaoFim = filtro.dataCriacaoFim();
            if (criacaoFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), criacaoFim));
            }

            predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Documentos que o dashboard considera do período, pela data de entrada no repositório. */
    public static Specification<DocumentoFinanceiro> recebidosEntre(OffsetDateTime inicio, OffsetDateTime fim) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), fim));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Documentos ainda em aberto na conferência, sem recorte de período. Backlog
     * não respeita janela: um documento recebido em janeiro e não conferido
     * continua cobrando alguém em março.
     */
    public static Specification<DocumentoFinanceiro> emAbertoAte(OffsetDateTime fim) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("ativo")));
            predicates.add(root.get("situacao").in(situacoesEmAberto()));
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataCriacao"), fim));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static List<SituacaoDocumento> situacoesEmAberto() {
        return Arrays.stream(SituacaoDocumento.values()).filter(SituacaoDocumento::isEmAberto).toList();
    }

    private static List<TipoDocumentoFinanceiro> tiposQueExigemIdentificacao() {
        return Arrays.stream(TipoDocumentoFinanceiro.values())
                .filter(TipoDocumentoFinanceiro::exigeIdentificacao).toList();
    }

    private static LocalDate primeiroDiaDoMes(LocalDate data) {
        return data == null ? null : data.withDayOfMonth(1);
    }

    private static void igual(List<Predicate> predicates, CriteriaBuilder cb, Path<?> caminho, String atributo, Object valor) {
        if (valor != null && !(valor instanceof String texto && texto.isBlank())) {
            predicates.add(cb.equal(caminho.get(atributo), valor));
        }
    }

    private static void intervaloDeData(List<Predicate> predicates,
                                        CriteriaBuilder cb,
                                        Root<DocumentoFinanceiro> root, String atributo,
                                        LocalDate inicio, LocalDate fim) {
        if (inicio != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(atributo), inicio));
        }
        if (fim != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(atributo), fim));
        }
    }
}
