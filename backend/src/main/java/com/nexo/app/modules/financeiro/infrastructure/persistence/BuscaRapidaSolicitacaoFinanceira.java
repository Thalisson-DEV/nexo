package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.application.dto.IdsBuscaSolicitacaoFinanceira;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.FormaPagamento;
import com.nexo.app.modules.financeiro.domain.GrauPrioridade;
import com.nexo.app.modules.financeiro.domain.LocalAquisicao;
import com.nexo.app.modules.financeiro.domain.MotivoSemCompra;
import com.nexo.app.modules.financeiro.domain.MotivoServicoFrota;
import com.nexo.app.modules.financeiro.domain.NaturezaServicoFrota;
import com.nexo.app.modules.financeiro.domain.RegimeVeiculo;
import com.nexo.app.modules.financeiro.domain.Rotulavel;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.TipoDespesa;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Busca global de solicitações, incluindo referências e documentos anexados. */
final class BuscaRapidaSolicitacaoFinanceira {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private BuscaRapidaSolicitacaoFinanceira() {
    }

    static Predicate montar(String termo, Root<?> solicitacao, Path<?> nucleo,
                            CriteriaQuery<?> query, CriteriaBuilder cb,
                            List<Expression<String>> camposSolicitacao,
                            IdsBuscaSolicitacaoFinanceira idsEncontrados,
                            boolean pesquisarCompraVinculada) {
        if (termo == null || termo.isBlank()) {
            return null;
        }

        String normalizado = SpecificationUtils.normalizarTermo(termo);
        List<Predicate> alternativas = new ArrayList<>();

        Predicate textos = SpecificationUtils.predicadoBuscaRapida(normalizado, cb, camposSolicitacao);
        if (textos != null) {
            alternativas.add(textos);
        }

        adicionarIdsDeReferencias(alternativas, solicitacao, nucleo, idsEncontrados);
        adicionarValorNumerico(alternativas, solicitacao, nucleo, cb, normalizado);
        adicionarData(alternativas, solicitacao, nucleo, cb, normalizado);
        adicionarEnums(alternativas, solicitacao, nucleo, cb, normalizado);
        adicionarBooleanos(alternativas, solicitacao, nucleo, cb, normalizado);
        adicionarUuid(alternativas, solicitacao, nucleo, cb, normalizado);

        String hash = HashUtils.gerarBlindIndex(normalizado);
        alternativas.add(cb.like(nucleo.get("dadosPagamentoBusca"), "%|" + hash + "|%"));
        String digitos = termo.replaceAll("\\D", "");
        String hashDigitos = null;
        if (digitos.length() >= 4 && !digitos.equals(termo.trim())) {
            hashDigitos = HashUtils.gerarBlindIndex(digitos);
            alternativas.add(cb.like(nucleo.get("dadosPagamentoBusca"), "%|" + hashDigitos + "|%"));
        }

        alternativas.add(documentoCorrespondente(termo, solicitacao, query, cb, idsEncontrados));
        alternativas.add(fornecedorCorrespondente(hash, hashDigitos, nucleo, query, cb));

        if (pesquisarCompraVinculada) {
            alternativas.add(compraVinculadaCorrespondente(normalizado, nucleo, query, cb, idsEncontrados));
        }

        return cb.or(alternativas.toArray(new Predicate[0]));
    }

    private static void adicionarIdsDeReferencias(List<Predicate> alternativas,
                                                   Root<?> solicitacao, Path<?> nucleo,
                                                   IdsBuscaSolicitacaoFinanceira ids) {
        if (ids == null) {
            return;
        }
        if (!ids.fornecedores().isEmpty()) {
            alternativas.add(nucleo.get("fornecedorId").in(ids.fornecedores()));
        }
        if (!ids.colaboradores().isEmpty()) {
            alternativas.add(nucleo.get("solicitanteColaboradorId").in(ids.colaboradores()));
        }
        if (!ids.veiculos().isEmpty()) {
            alternativas.add(nucleo.get("veiculoId").in(ids.veiculos()));
        }
        if (!ids.bases().isEmpty()) {
            alternativas.add(nucleo.get("baseId").in(ids.bases()));
        }
        if (!ids.processos().isEmpty()) {
            alternativas.add(nucleo.get("processoId").in(ids.processos()));
        }
        if (!ids.obras().isEmpty()) {
            alternativas.add(nucleo.get("obraId").in(ids.obras()));
        }
        if (!ids.usuarios().isEmpty()) {
            alternativas.add(solicitacao.get("usuarioSolicitanteId").in(ids.usuarios()));
            alternativas.add(solicitacao.get("usuarioDecisaoId").in(ids.usuarios()));
            alternativas.add(solicitacao.get("usuarioCancelamentoId").in(ids.usuarios()));
        }
        if (!ids.compras().isEmpty()) {
            alternativas.add(nucleo.get("solicitacaoCompraId").in(ids.compras()));
        }
    }

    private static void adicionarValorNumerico(List<Predicate> alternativas, Root<?> solicitacao,
                                                Path<?> nucleo, CriteriaBuilder cb, String termo) {
        BigDecimal valor = converterNumero(termo);
        if (valor == null) {
            return;
        }
        alternativas.add(cb.equal(nucleo.get("valor"), valor));
        alternativas.add(cb.equal(solicitacao.get("valorAprovado"), valor));
        alternativas.add(cb.equal(nucleo.get("quantidade"), valor));
        if (valor.scale() == 0) {
            try {
                Long inteiro = valor.longValueExact();
                alternativas.add(cb.equal(solicitacao.get("numero"), inteiro));
                if (inteiro <= Integer.MAX_VALUE) {
                    alternativas.add(cb.equal(nucleo.get("quantidadeParcelas"), inteiro.intValue()));
                }
            } catch (ArithmeticException ignored) {
                // O texto ainda pode casar com os campos textuais da busca.
            }
        }
    }

    private static void adicionarData(List<Predicate> alternativas, Root<?> solicitacao,
                                      Path<?> nucleo, CriteriaBuilder cb, String termo) {
        LocalDate data = converterData(termo);
        if (data == null) {
            return;
        }
        alternativas.add(cb.equal(nucleo.get("vencimento"), data));
        ZoneId zona = ZoneId.systemDefault();
        OffsetDateTime inicio = data.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fim = data.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        adicionarDia(alternativas, solicitacao, cb, "dataCriacao", inicio, fim);
        adicionarDia(alternativas, solicitacao, cb, "dataSubmissao", inicio, fim);
        adicionarDia(alternativas, solicitacao, cb, "dataDecisao", inicio, fim);
        adicionarDia(alternativas, solicitacao, cb, "dataCancelamento", inicio, fim);
        adicionarDia(alternativas, solicitacao, cb, "dataUltimaAlteracao", inicio, fim);
        adicionarDia(alternativas, solicitacao, cb, "dataDesativacao", inicio, fim);
    }

    private static void adicionarDia(List<Predicate> alternativas, Root<?> solicitacao,
                                     CriteriaBuilder cb, String campo,
                                     OffsetDateTime inicio, OffsetDateTime fim) {
        alternativas.add(cb.and(
                cb.greaterThanOrEqualTo(solicitacao.get(campo), inicio),
                cb.lessThan(solicitacao.get(campo), fim)));
    }

    private static void adicionarEnums(List<Predicate> alternativas, Root<?> solicitacao,
                                       Path<?> nucleo, CriteriaBuilder cb, String termo) {
        adicionarEnum(alternativas, solicitacao.get("status"), termo, StatusSolicitacao.class, cb);
        adicionarEnum(alternativas, nucleo.get("prioridade"), termo, GrauPrioridade.class, cb);
        adicionarEnum(alternativas, nucleo.get("localAquisicao"), termo, LocalAquisicao.class, cb);
        adicionarEnum(alternativas, nucleo.get("formaPagamento"), termo, FormaPagamento.class, cb);
        adicionarEnum(alternativas, nucleo.get("regimeVeiculo"), termo, RegimeVeiculo.class, cb);
        adicionarEnum(alternativas, nucleo.get("naturezaServico"), termo, NaturezaServicoFrota.class, cb);
        adicionarEnum(alternativas, nucleo.get("motivoServico"), termo, MotivoServicoFrota.class, cb);
        adicionarEnum(alternativas, nucleo.get("tipoDespesa"), termo, TipoDespesa.class, cb);
        adicionarEnum(alternativas, nucleo.get("motivoSemCompra"), termo, MotivoSemCompra.class, cb);
    }

    private static <E extends Enum<E>> void adicionarEnum(List<Predicate> alternativas,
                                                            Expression<?> expressao,
                                                            String termo, Class<E> tipo,
                                                            CriteriaBuilder cb) {
        for (E valor : tipo.getEnumConstants()) {
            String nome = SpecificationUtils.normalizarTermo(valor.name().replace('_', ' '));
            String rotulo = valor instanceof Rotulavel rotulavel
                    ? SpecificationUtils.normalizarTermo(rotulavel.getRotulo()) : nome;
            if (termo.equals(nome) || termo.equals(rotulo)
                    || nome.contains(termo) || rotulo.contains(termo)) {
                alternativas.add(cb.equal(expressao, valor));
            }
        }
    }

    private static void adicionarBooleanos(List<Predicate> alternativas, Root<?> solicitacao,
                                           Path<?> nucleo, CriteriaBuilder cb, String termo) {
        Boolean valor = switch (termo) {
            case "true", "sim", "yes", "ativo", "com", "possui" -> true;
            case "false", "nao", "não", "inativo", "sem" -> false;
            default -> null;
        };
        if (valor == null) {
            return;
        }
        alternativas.add(cb.or(
                cb.equal(nucleo.get("temNf"), valor),
                cb.equal(nucleo.get("possuiCompraPrevia"), valor),
                cb.equal(solicitacao.get("ativo"), valor)));
    }

    private static void adicionarUuid(List<Predicate> alternativas, Root<?> solicitacao,
                                      Path<?> nucleo, CriteriaBuilder cb, String termo) {
        try {
            UUID id = UUID.fromString(termo);
            alternativas.add(cb.equal(solicitacao.get("id"), id));
            alternativas.add(cb.equal(solicitacao.get("formulario").get("id"), id));
            alternativas.add(cb.equal(nucleo.get("solicitanteColaboradorId"), id));
            alternativas.add(cb.equal(nucleo.get("baseId"), id));
            alternativas.add(cb.equal(nucleo.get("processoId"), id));
            alternativas.add(cb.equal(nucleo.get("obraId"), id));
            alternativas.add(cb.equal(nucleo.get("fornecedorId"), id));
            alternativas.add(cb.equal(nucleo.get("veiculoId"), id));
            alternativas.add(cb.equal(nucleo.get("solicitacaoCompraId"), id));
            alternativas.add(cb.equal(solicitacao.get("usuarioSolicitanteId"), id));
            alternativas.add(cb.equal(solicitacao.get("usuarioDecisaoId"), id));
            alternativas.add(cb.equal(solicitacao.get("usuarioCancelamentoId"), id));
        } catch (IllegalArgumentException ignored) {
            // A maioria dos termos não será UUID; os demais campos continuam válidos.
        }
    }

    private static Predicate documentoCorrespondente(String termo, Root<?> solicitacao,
                                                     CriteriaQuery<?> query, CriteriaBuilder cb,
                                                     IdsBuscaSolicitacaoFinanceira ids) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<DocumentoFinanceiro> documento = subquery.from(DocumentoFinanceiro.class);
        Predicate texto = SpecificationUtils.predicadoBuscaRapida(termo, cb, List.of(
                documento.get("nomeArquivo"),
                documento.get("contentType"),
                documento.get("hashConteudo"),
                documento.get("objectKey"),
                documento.get("numeroDocumento"),
                documento.get("chaveAcesso"),
                documento.get("codigoPagamento"),
                documento.get("emissorNome"),
                documento.get("descricao"),
                documento.get("observacoes"),
                documento.get("observacaoConferencia"),
                documento.get("motivoPendencia"),
                documento.get("usuarioCriacao"),
                documento.get("usuarioUltimaAlteracao")));
        List<Predicate> alternativas = new ArrayList<>();
        alternativas.add(texto);
        adicionarEnumDocumento(alternativas, documento.get("tipo"), termo, TipoDocumentoFinanceiro.class, cb);
        adicionarEnumDocumento(alternativas, documento.get("situacao"), termo, SituacaoDocumento.class, cb);

        BigDecimal valor = converterNumero(termo);
        if (valor != null) {
            alternativas.add(cb.equal(documento.get("valor"), valor));
            if (valor.scale() == 0) {
                try {
                    alternativas.add(cb.equal(documento.get("tamanhoBytes"), valor.longValueExact()));
                } catch (ArithmeticException ignored) {
                    // O valor continua disponivel para os metadados financeiros.
                }
            }
        }
        LocalDate data = converterData(termo);
        if (data != null) {
            alternativas.add(cb.equal(documento.get("dataEmissao"), data));
            alternativas.add(cb.equal(documento.get("dataVencimento"), data));
            alternativas.add(cb.equal(documento.get("competencia"), data.withDayOfMonth(1)));
            ZoneId zona = ZoneId.systemDefault();
            OffsetDateTime inicio = data.atStartOfDay(zona).toOffsetDateTime();
            OffsetDateTime fim = data.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
            adicionarDia(alternativas, documento, cb, "dataConferencia", inicio, fim);
        }
        try {
            UUID id = UUID.fromString(SpecificationUtils.normalizarTermo(termo));
            alternativas.add(cb.equal(documento.get("id"), id));
            alternativas.add(cb.equal(documento.get("fornecedorId"), id));
            alternativas.add(cb.equal(documento.get("baseId"), id));
            alternativas.add(cb.equal(documento.get("processoId"), id));
            alternativas.add(cb.equal(documento.get("obraId"), id));
            alternativas.add(cb.equal(documento.get("usuarioEnvioId"), id));
            alternativas.add(cb.equal(documento.get("usuarioConferenciaId"), id));
        } catch (IllegalArgumentException ignored) {
            // Os outros campos continuam disponiveis para o termo textual.
        }
        if (ids != null && !ids.fornecedores().isEmpty()) {
            alternativas.add(documento.get("fornecedorId").in(ids.fornecedores()));
        }
        if (ids != null && !ids.bases().isEmpty()) {
            alternativas.add(documento.get("baseId").in(ids.bases()));
        }
        if (ids != null && !ids.processos().isEmpty()) {
            alternativas.add(documento.get("processoId").in(ids.processos()));
        }
        if (ids != null && !ids.obras().isEmpty()) {
            alternativas.add(documento.get("obraId").in(ids.obras()));
        }
        if (ids != null && !ids.usuarios().isEmpty()) {
            alternativas.add(documento.get("usuarioEnvioId").in(ids.usuarios()));
            alternativas.add(documento.get("usuarioConferenciaId").in(ids.usuarios()));
        }
        subquery.select(cb.literal(1)).where(
                cb.equal(documento.get("origem").get("solicitacaoId"), solicitacao.get("id")),
                cb.isTrue(documento.get("ativo")),
                cb.or(alternativas.toArray(new Predicate[0])));
        return cb.exists(subquery);
    }

    private static <E extends Enum<E>> void adicionarEnumDocumento(List<Predicate> alternativas,
                                                                     Expression<?> expressao,
                                                                     String termo, Class<E> tipo,
                                                                     CriteriaBuilder cb) {
        for (E valor : tipo.getEnumConstants()) {
            String nome = SpecificationUtils.normalizarTermo(valor.name().replace('_', ' '));
            String rotulo = valor instanceof Rotulavel rotulavel
                    ? SpecificationUtils.normalizarTermo(rotulavel.getRotulo()) : nome;
            if (termo.equals(nome) || termo.equals(rotulo)
                    || nome.contains(termo) || rotulo.contains(termo)) {
                alternativas.add(cb.equal(expressao, valor));
            }
        }
    }

    private static Predicate fornecedorCorrespondente(String hash, String hashDigitos,
                                                       Path<?> nucleo, CriteriaQuery<?> query,
                                                       CriteriaBuilder cb) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<com.nexo.app.modules.financeiro.domain.Fornecedor> fornecedor = subquery.from(
                com.nexo.app.modules.financeiro.domain.Fornecedor.class);
        Predicate dados = cb.like(fornecedor.get("dadosBancariosBusca"), "%|" + hash + "|%");
        if (hashDigitos != null) {
            dados = cb.or(dados, cb.like(
                    fornecedor.get("dadosBancariosBusca"), "%|" + hashDigitos + "|%"));
        }
        subquery.select(cb.literal(1)).where(
                cb.equal(fornecedor.get("id"), nucleo.get("fornecedorId")),
                dados);
        return cb.exists(subquery);
    }

    private static Predicate compraVinculadaCorrespondente(String termo, Path<?> nucleo,
                                                            CriteriaQuery<?> query, CriteriaBuilder cb,
                                                            IdsBuscaSolicitacaoFinanceira ids) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<SolicitacaoCompra> compra = subquery.from(SolicitacaoCompra.class);
        Path<?> compraNucleo = compra.get("nucleo");
        Predicate texto = SpecificationUtils.predicadoBuscaRapida(termo, cb, List.of(
                compraNucleo.get("descricao"),
                compraNucleo.get("observacoes"),
                compraNucleo.get("fornecedorNome"),
                compraNucleo.get("fornecedorRazaoSocial"),
                compraNucleo.get("fornecedorOrdenacao"),
                compraNucleo.get("justificativaSemNf"),
                compraNucleo.get("numeroNf"),
                compra.get("observacaoDecisao"),
                compra.get("motivoRejeicao"),
                compra.get("motivoCancelamento"),
                compra.get("usuarioCriacao"),
                compra.get("usuarioUltimaAlteracao"),
                compra.get("formulario").get("codigo"),
                compra.get("formulario").get("titulo"),
                compra.get("formulario").get("descricao")));
        List<Predicate> alternativas = new ArrayList<>();
        alternativas.add(texto);
        adicionarEnum(alternativas, compra.get("status"), termo, StatusSolicitacao.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("prioridade"), termo, GrauPrioridade.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("localAquisicao"), termo, LocalAquisicao.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("formaPagamento"), termo, FormaPagamento.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("regimeVeiculo"), termo, RegimeVeiculo.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("naturezaServico"), termo, NaturezaServicoFrota.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("motivoServico"), termo, MotivoServicoFrota.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("tipoDespesa"), termo, TipoDespesa.class, cb);
        adicionarEnum(alternativas, compraNucleo.get("motivoSemCompra"), termo, MotivoSemCompra.class, cb);
        adicionarIdsDaCompra(alternativas, compra, compraNucleo, ids);
        adicionarBooleanos(alternativas, compra, compraNucleo, cb, termo);
        alternativas.add(documentoCorrespondente(termo, compra, query, cb, ids));

        String hashCompra = HashUtils.gerarBlindIndex(termo);
        alternativas.add(cb.like(compraNucleo.get("dadosPagamentoBusca"), "%|" + hashCompra + "|%"));
        String digitosCompra = termo.replaceAll("\\D", "");
        if (digitosCompra.length() >= 4 && !digitosCompra.equals(termo.trim())) {
            alternativas.add(cb.like(compraNucleo.get("dadosPagamentoBusca"),
                    "%|" + HashUtils.gerarBlindIndex(digitosCompra) + "|%"));
        }

        BigDecimal numero = converterNumero(termo);
        if (numero != null) {
            alternativas.add(cb.equal(compraNucleo.get("valor"), numero));
            alternativas.add(cb.equal(compra.get("valorAprovado"), numero));
            alternativas.add(cb.equal(compraNucleo.get("quantidade"), numero));
            try {
                alternativas.add(cb.equal(compra.get("numero"), numero.longValueExact()));
                alternativas.add(cb.equal(compraNucleo.get("quantidadeParcelas"), numero.intValueExact()));
            } catch (ArithmeticException ignored) {
                // O texto já foi considerado acima.
            }
        }
        LocalDate data = converterData(termo);
        if (data != null) {
            alternativas.add(cb.equal(compraNucleo.get("vencimento"), data));
            ZoneId zona = ZoneId.systemDefault();
            OffsetDateTime inicio = data.atStartOfDay(zona).toOffsetDateTime();
            OffsetDateTime fim = data.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
            adicionarDia(alternativas, compra, cb, "dataCriacao", inicio, fim);
            adicionarDia(alternativas, compra, cb, "dataSubmissao", inicio, fim);
            adicionarDia(alternativas, compra, cb, "dataDecisao", inicio, fim);
            adicionarDia(alternativas, compra, cb, "dataCancelamento", inicio, fim);
            adicionarDia(alternativas, compra, cb, "dataUltimaAlteracao", inicio, fim);
            adicionarDia(alternativas, compra, cb, "dataDesativacao", inicio, fim);
        }
        subquery.select(cb.literal(1)).where(
                cb.equal(compra.get("id"), nucleo.get("solicitacaoCompraId")),
                cb.or(alternativas.toArray(new Predicate[0])));
        return cb.exists(subquery);
    }

    private static void adicionarIdsDaCompra(List<Predicate> alternativas, Root<?> compra,
                                              Path<?> nucleo,
                                              IdsBuscaSolicitacaoFinanceira ids) {
        if (ids == null) {
            return;
        }
        if (!ids.fornecedores().isEmpty()) {
            alternativas.add(nucleo.get("fornecedorId").in(ids.fornecedores()));
        }
        if (!ids.colaboradores().isEmpty()) {
            alternativas.add(nucleo.get("solicitanteColaboradorId").in(ids.colaboradores()));
        }
        if (!ids.veiculos().isEmpty()) {
            alternativas.add(nucleo.get("veiculoId").in(ids.veiculos()));
        }
        if (!ids.bases().isEmpty()) {
            alternativas.add(nucleo.get("baseId").in(ids.bases()));
        }
        if (!ids.processos().isEmpty()) {
            alternativas.add(nucleo.get("processoId").in(ids.processos()));
        }
        if (!ids.obras().isEmpty()) {
            alternativas.add(nucleo.get("obraId").in(ids.obras()));
        }
        if (!ids.usuarios().isEmpty()) {
            alternativas.add(compra.get("usuarioSolicitanteId").in(ids.usuarios()));
            alternativas.add(compra.get("usuarioDecisaoId").in(ids.usuarios()));
            alternativas.add(compra.get("usuarioCancelamentoId").in(ids.usuarios()));
        }
    }

    private static BigDecimal converterNumero(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        String valor = termo.replace("r$", "").replace(" ", "");
        try {
            if (valor.contains(",") && valor.contains(".")) {
                valor = valor.replace(".", "").replace(',', '.');
            } else if (valor.contains(",")) {
                valor = valor.replace(',', '.');
            }
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate converterData(String termo) {
        try {
            return LocalDate.parse(termo);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(termo, DATA_BR);
            } catch (DateTimeParseException ignoredBr) {
                return null;
            }
        }
    }
}
