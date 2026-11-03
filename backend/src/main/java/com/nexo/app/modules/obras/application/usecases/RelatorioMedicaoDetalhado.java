package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.StatusMedicao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Conteúdo do relatório de medição detalhado de uma obra, comum ao Excel e ao PDF. Orçado e faturado vêm da planilha;
 * planejado e executado (até a data de corte) vêm do sistema.
 */
record RelatorioMedicaoDetalhado(Obra obra, String municipio, LocalDate dataCorte, String fiscal, String responsavel,
                                 List<Linha> maoDeObra, List<Linha> materiais,
                                 List<PlanilhaOrcadoFaturado.LinhaFaturada> faturado,
                                 List<MedicaoResponses.MedicaoResumo> medicoes) {

    /**
     * Um item por código e operação. Valores nulos quando a fonte não traz o dado (material não tem faturado nem valor
     * executado).
     */
    record Linha(String codigo, OperacaoItemKit operacao, String descricao, String unidade,
                 BigDecimal orcadoQuantidade, BigDecimal orcadoValor, BigDecimal planejadoQuantidade,
                 BigDecimal executadoQuantidade, BigDecimal executadoValor,
                 BigDecimal faturadoQuantidade, BigDecimal faturadoValor) {

        /** Executado ainda não faturado. */
        BigDecimal aFaturar() {
            return zero(executadoValor).subtract(zero(faturadoValor));
        }

        /** Orçado que ainda não foi faturado. */
        BigDecimal saldoOrcado() {
            return zero(orcadoValor).subtract(zero(faturadoValor));
        }

        /** Executado acima do orçado ou fora dele. */
        boolean acimaDoOrcado() {
            return executadoQuantidade != null && executadoQuantidade.signum() > 0
                    && (orcadoQuantidade == null || executadoQuantidade.compareTo(orcadoQuantidade) > 0);
        }
    }

    BigDecimal orcadoMaoDeObra() {
        return somar(maoDeObra, Linha::orcadoValor);
    }

    BigDecimal orcadoMateriais() {
        return somar(materiais, Linha::orcadoValor);
    }

    BigDecimal executado() {
        return somar(maoDeObra, Linha::executadoValor);
    }

    BigDecimal faturadoTotal() {
        return somar(maoDeObra, Linha::faturadoValor);
    }

    BigDecimal aFaturar() {
        return executado().subtract(faturadoTotal());
    }

    BigDecimal saldoOrcado() {
        return orcadoMaoDeObra().subtract(faturadoTotal());
    }

    BigDecimal medidoNoSistema() {
        return medicoes.stream().filter(m -> m.status() == StatusMedicao.GERADA)
                .map(MedicaoResponses.MedicaoResumo::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<PlanilhaOrcadoFaturado.LinhaFaturada> paraConferir() {
        return faturado.stream().filter(PlanilhaOrcadoFaturado.LinhaFaturada::paraConferir).toList();
    }

    private static BigDecimal somar(List<Linha> linhas, Function<Linha, BigDecimal> valor) {
        return linhas.stream().map(valor).map(RelatorioMedicaoDetalhado::zero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal zero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
