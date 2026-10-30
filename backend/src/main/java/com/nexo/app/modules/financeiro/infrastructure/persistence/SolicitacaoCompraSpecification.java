package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.IdsBuscaSolicitacaoFinanceira;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraFilter;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Filtros estruturados e busca global das solicitações de compra. */
public final class SolicitacaoCompraSpecification {

    private SolicitacaoCompraSpecification() {
    }

    public static Specification<SolicitacaoCompra> comFiltros(SolicitacaoCompraFilter filtro,
                                                              UUID usuarioAutenticado) {
        return comFiltros(filtro, usuarioAutenticado, IdsBuscaSolicitacaoFinanceira.vazio());
    }

    public static Specification<SolicitacaoCompra> comFiltros(SolicitacaoCompraFilter filtro,
                                                              UUID usuarioAutenticado,
                                                              IdsBuscaSolicitacaoFinanceira idsEncontrados) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro == null) {
                predicates.add(cb.isTrue(root.get("ativo")));
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            Path<?> nucleo = root.get("nucleo");
            Path<?> formulario = root.get("formulario");
            List<Expression<String>> camposDeBusca = List.of(
                    nucleo.<String>get("descricao"),
                    nucleo.<String>get("observacoes"),
                    nucleo.<String>get("fornecedorNome"),
                    nucleo.<String>get("fornecedorRazaoSocial"),
                    nucleo.<String>get("fornecedorOrdenacao"),
                    nucleo.<String>get("justificativaSemNf"),
                    nucleo.<String>get("numeroNf"),
                    formulario.<String>get("codigo"),
                    formulario.<String>get("titulo"),
                    formulario.<String>get("descricao"),
                    root.<String>get("observacaoDecisao"),
                    root.<String>get("motivoRejeicao"),
                    root.<String>get("motivoCancelamento"),
                    root.<String>get("usuarioCriacao"),
                    root.<String>get("usuarioUltimaAlteracao"));
            Predicate buscaRapida = BuscaRapidaSolicitacaoFinanceira.montar(
                    filtro.buscaGlobal(), root, nucleo, query, cb, camposDeBusca,
                    idsEncontrados, false);
            if (buscaRapida != null) {
                predicates.add(buscaRapida);
            }

            if (filtro.numero() != null) {
                predicates.add(cb.equal(root.get("numero"), filtro.numero()));
            }
            if (filtro.status() != null) {
                predicates.add(cb.equal(root.get("status"), filtro.status()));
            }
            if (filtro.formularioId() != null) {
                predicates.add(cb.equal(formulario.get("id"), filtro.formularioId()));
            }
            if (filtro.baseId() != null) {
                predicates.add(cb.equal(nucleo.get("baseId"), filtro.baseId()));
            }
            if (filtro.processoId() != null) {
                predicates.add(cb.equal(nucleo.get("processoId"), filtro.processoId()));
            }
            if (filtro.obraId() != null) {
                predicates.add(cb.equal(nucleo.get("obraId"), filtro.obraId()));
            }
            if (filtro.fornecedorId() != null) {
                predicates.add(cb.equal(nucleo.get("fornecedorId"), filtro.fornecedorId()));
            }
            if (filtro.veiculoId() != null) {
                predicates.add(cb.equal(nucleo.get("veiculoId"), filtro.veiculoId()));
            }
            if (filtro.solicitanteColaboradorId() != null) {
                predicates.add(cb.equal(nucleo.get("solicitanteColaboradorId"), filtro.solicitanteColaboradorId()));
            }
            if (filtro.prioridade() != null) {
                predicates.add(cb.equal(nucleo.get("prioridade"), filtro.prioridade()));
            }
            if (filtro.localAquisicao() != null) {
                predicates.add(cb.equal(nucleo.get("localAquisicao"), filtro.localAquisicao()));
            }
            if (filtro.formaPagamento() != null) {
                predicates.add(cb.equal(nucleo.get("formaPagamento"), filtro.formaPagamento()));
            }
            if (filtro.regimeVeiculo() != null) {
                predicates.add(cb.equal(nucleo.get("regimeVeiculo"), filtro.regimeVeiculo()));
            }
            if (filtro.naturezaServico() != null) {
                predicates.add(cb.equal(nucleo.get("naturezaServico"), filtro.naturezaServico()));
            }
            if (filtro.motivoServico() != null) {
                predicates.add(cb.equal(nucleo.get("motivoServico"), filtro.motivoServico()));
            }
            if (filtro.tipoDespesa() != null) {
                predicates.add(cb.equal(nucleo.get("tipoDespesa"), filtro.tipoDespesa()));
            }
            if (filtro.usuarioSolicitanteId() != null) {
                predicates.add(cb.equal(root.get("usuarioSolicitanteId"), filtro.usuarioSolicitanteId()));
            }
            if (filtro.usuarioDecisaoId() != null) {
                predicates.add(cb.equal(root.get("usuarioDecisaoId"), filtro.usuarioDecisaoId()));
            }
            if (filtro.usuarioCancelamentoId() != null) {
                predicates.add(cb.equal(root.get("usuarioCancelamentoId"), filtro.usuarioCancelamentoId()));
            }
            if (Boolean.TRUE.equals(filtro.apenasMinhas()) && usuarioAutenticado != null) {
                predicates.add(cb.equal(root.get("usuarioSolicitanteId"), usuarioAutenticado));
            }

            adicionarFaixa(predicates, cb, nucleo.get("valor"), filtro.valorMinimo(), filtro.valorMaximo());
            adicionarFaixa(predicates, cb, root.get("valorAprovado"),
                    filtro.valorAprovadoMinimo(), filtro.valorAprovadoMaximo());
            adicionarFaixa(predicates, cb, nucleo.get("quantidade"),
                    filtro.quantidadeMinima(), filtro.quantidadeMaxima());
            adicionarFaixa(predicates, cb, nucleo.get("quantidadeParcelas"),
                    filtro.parcelasMinimas(), filtro.parcelasMaximas());
            adicionarFaixa(predicates, cb, nucleo.get("vencimento"),
                    filtro.vencimentoInicio(), filtro.vencimentoFim());
            adicionarFaixa(predicates, cb, root.get("dataCriacao"),
                    filtro.dataCriacaoInicio(), filtro.dataCriacaoFim());
            adicionarFaixa(predicates, cb, root.get("dataSubmissao"),
                    filtro.dataSubmissaoInicio(), filtro.dataSubmissaoFim());
            adicionarFaixa(predicates, cb, root.get("dataDecisao"),
                    filtro.dataDecisaoInicio(), filtro.dataDecisaoFim());
            adicionarFaixa(predicates, cb, root.get("dataCancelamento"),
                    filtro.dataCancelamentoInicio(), filtro.dataCancelamentoFim());
            adicionarFaixa(predicates, cb, root.get("dataUltimaAlteracao"),
                    filtro.dataUltimaAlteracaoInicio(), filtro.dataUltimaAlteracaoFim());
            adicionarFaixa(predicates, cb, root.get("dataDesativacao"),
                    filtro.dataDesativacaoInicio(), filtro.dataDesativacaoFim());

            if (filtro.temNf() != null) {
                predicates.add(cb.equal(nucleo.get("temNf"), filtro.temNf()));
            }
            SpecificationUtils.buscaRapida(filtro.numeroNf(), predicates, cb,
                    List.of(nucleo.<String>get("numeroNf")));
            adicionarDocumentos(predicates, query, cb, root, filtro.possuiDocumentos());
            predicates.add(cb.equal(root.get("ativo"), filtro.ativo() == null || filtro.ativo()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <N extends Comparable<? super N>> void adicionarFaixa(List<Predicate> predicates,
                                                                           jakarta.persistence.criteria.CriteriaBuilder cb,
                                                                           Expression<N> expressao,
                                                                           N inicio, N fim) {
        if (inicio != null) {
            predicates.add(cb.greaterThanOrEqualTo(expressao, inicio));
        }
        if (fim != null) {
            predicates.add(cb.lessThanOrEqualTo(expressao, fim));
        }
    }

    private static void adicionarDocumentos(List<Predicate> predicates,
                                            jakarta.persistence.criteria.CriteriaQuery<?> query,
                                            jakarta.persistence.criteria.CriteriaBuilder cb,
                                            Root<?> solicitacao, Boolean possuiDocumentos) {
        if (possuiDocumentos == null) {
            return;
        }
        Subquery<Integer> documentos = query.subquery(Integer.class);
        Root<DocumentoFinanceiro> documento = documentos.from(DocumentoFinanceiro.class);
        documentos.select(cb.literal(1)).where(
                cb.equal(documento.get("origem").get("solicitacaoId"), solicitacao.get("id")),
                cb.isTrue(documento.get("ativo")));
        Predicate existe = cb.exists(documentos);
        predicates.add(Boolean.TRUE.equals(possuiDocumentos) ? existe : cb.not(existe));
    }
}
