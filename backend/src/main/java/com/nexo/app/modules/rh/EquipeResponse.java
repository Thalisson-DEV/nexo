package com.nexo.app.modules.rh;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Record para resposta detalhada de uma equipe.
 */
public record EquipeResponse(
    UUID id,
    String codigo,
    TipoEquipeResponse tipoEquipe,
    ProcessoNegocioResponse processo,
    VeiculoResponse veiculo,
    CoordenadorResponse coordenador,
    SupervisorResponse supervisor,
    BaseOperacionalResponse baseOperacional,
    UUID idContrato,
    boolean ativo,
    List<EquipeMembroResponse> membros,
    BigDecimal metaFaturamentoMensal,
    BigDecimal metaFaturamentoDiaria
) {
    public EquipeResponse(UUID id, String codigo, TipoEquipeResponse tipoEquipe, ProcessoNegocioResponse processo,
                          VeiculoResponse veiculo, CoordenadorResponse coordenador, SupervisorResponse supervisor,
                          BaseOperacionalResponse baseOperacional, UUID idContrato, boolean ativo,
                          List<EquipeMembroResponse> membros, BigDecimal metaFaturamentoMensal) {
        this(id, codigo, tipoEquipe, processo, veiculo, coordenador, supervisor, baseOperacional, idContrato, ativo, membros, metaFaturamentoMensal, null);
    }

    /**
     * Cópia com a meta de faturamento da janela vigente (ciclo comercial ou mês) e a
     * meta diária correspondente, ambas vindas do cadastro de metas.
     */
    public EquipeResponse comMeta(BigDecimal metaDaJanela, BigDecimal metaDiaria) {
        return new EquipeResponse(id, codigo, tipoEquipe, processo, veiculo, coordenador, supervisor, baseOperacional,
                idContrato, ativo, membros, metaDaJanela, metaDiaria);
    }
}
