package com.nexo.app.modules.financeiro.application.usecases;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Produz somente os fatos financeiros usados por políticas cadastradas. */
@Component
@RequiredArgsConstructor
@Slf4j
class MontadorContextoRoteamentoFinanceiro {

    private static final String CODIGO_FROTA = "FROTA";
    private static final String CODIGO_OBRA_CIVIL = "OBRA_CIVIL";

    private final BaseOperacionalApi baseOperacionalApi;
    private final SolicitacaoCompraRepository compraRepository;

    Contexto montar(SolicitacaoBase solicitacao) {
        SolicitacaoCompra compraOrigem = buscarCompraOrigem(solicitacao).orElse(null);
        UUID baseId = baseEfetiva(solicitacao, compraOrigem);
        BaseOperacionalResponse base = buscarBase(baseId).orElse(null);
        return new Contexto(
                base == null ? "" : normalizar(base.nome()),
                base == null ? "" : normalizar(base.uf()),
                ehFrota(solicitacao, compraOrigem),
                ehObraCivil(solicitacao, compraOrigem));
    }

    private Optional<SolicitacaoCompra> buscarCompraOrigem(SolicitacaoBase solicitacao) {
        if (!(solicitacao instanceof SolicitacaoPagamento pagamento)
                || pagamento.getSolicitacaoCompraId() == null) {
            return Optional.empty();
        }
        try {
            return compraRepository.findById(pagamento.getSolicitacaoCompraId());
        } catch (RuntimeException e) {
            log.warn("Não foi possível consultar a compra {} para montar o contexto do pagamento {}: {}",
                    pagamento.getSolicitacaoCompraId(), solicitacao.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BaseOperacionalResponse> buscarBase(UUID baseId) {
        if (baseId == null) {
            return Optional.empty();
        }
        try {
            return baseOperacionalApi.buscarPorIdResponse(baseId);
        } catch (RuntimeException e) {
            log.warn("Não foi possível consultar a base {} para montar o contexto financeiro: {}",
                    baseId, e.getMessage());
            return Optional.empty();
        }
    }

    private static UUID baseEfetiva(SolicitacaoBase solicitacao, SolicitacaoCompra compraOrigem) {
        UUID basePropria = solicitacao.getNucleo().getBaseId();
        return basePropria != null
                ? basePropria
                : compraOrigem == null ? null : compraOrigem.getNucleo().getBaseId();
    }

    private static boolean ehFrota(SolicitacaoBase solicitacao, SolicitacaoCompra compraOrigem) {
        return CODIGO_FROTA.equalsIgnoreCase(solicitacao.getFormulario().getCodigo())
                || compraOrigem != null
                && CODIGO_FROTA.equalsIgnoreCase(compraOrigem.getFormulario().getCodigo());
    }

    private static boolean ehObraCivil(SolicitacaoBase solicitacao, SolicitacaoCompra compraOrigem) {
        if (solicitacao.getFormulario() != null
                && CODIGO_OBRA_CIVIL.equalsIgnoreCase(solicitacao.getFormulario().getCodigo())) {
            return true;
        }
        if (solicitacao.getNucleo() != null && solicitacao.getNucleo().getObraId() != null) {
            return true;
        }
        if (compraOrigem != null) {
            if (compraOrigem.getFormulario() != null
                    && CODIGO_OBRA_CIVIL.equalsIgnoreCase(compraOrigem.getFormulario().getCodigo())) {
                return true;
            }
            return compraOrigem.getNucleo() != null && compraOrigem.getNucleo().getObraId() != null;
        }
        return false;
    }

    private static String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "";
        }
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    record Contexto(String base, String uf, boolean frota, boolean obraCivil) {
    }
}
