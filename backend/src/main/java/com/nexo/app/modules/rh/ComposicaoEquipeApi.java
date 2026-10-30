package com.nexo.app.modules.rh;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública do RH para a composição operacional de uma equipe (veículo e membros),
 * consumida pela abertura de turno do aplicativo de campo.
 *
 * <p>Permite que o próprio responsável da equipe mantenha a composição atualizada
 * sem depender do administrativo. Os contratos não expõem CPF nem entidades JPA.</p>
 */
public interface ComposicaoEquipeApi {

    record VeiculoComposicao(UUID id, String placa, String modelo) {
    }

    record MembroComposicao(UUID idColaborador, String nome, String funcao, boolean responsavel) {
    }

    record ComposicaoEquipe(
            UUID idEquipe,
            String codigo,
            UUID idUsuarioResponsavel,
            VeiculoComposicao veiculo,
            List<MembroComposicao> membros,
            BigDecimal metaFaturamentoMensal,
            BigDecimal metaFaturamentoDiaria
    ) {
        public ComposicaoEquipe(UUID idEquipe, String codigo, UUID idUsuarioResponsavel,
                                VeiculoComposicao veiculo, List<MembroComposicao> membros,
                                BigDecimal metaFaturamentoMensal) {
            this(idEquipe, codigo, idUsuarioResponsavel, veiculo, membros, metaFaturamentoMensal, null);
        }
    }

    /** Colaborador que pode ser incluído na equipe; {@code equipeAtual} indica onde ele está hoje. */
    record OpcaoColaborador(UUID id, String nome, String funcao, String equipeAtual, boolean responsavelEmOutraEquipe) {
    }

    record OpcoesComposicao(List<OpcaoColaborador> colaboradores, List<VeiculoComposicao> veiculos) {
    }

    Optional<ComposicaoEquipe> buscarComposicao(UUID idEquipe);

    /**
     * Colaboradores ativos da base operacional da equipe (incluindo os membros atuais)
     * e veículos ativos, para a conferência da equipe na abertura do turno.
     */
    OpcoesComposicao listarOpcoes(UUID idEquipe);

    /**
     * Atualiza veículo e parceiros da equipe a pedido do seu responsável.
     *
     * @param idVeiculo     novo veículo; {@code null} mantém o atual
     * @param idsParceiros  lista completa desejada de parceiros (sem o responsável);
     *                      {@code null} mantém os membros atuais
     */
    ComposicaoEquipe atualizarPeloResponsavel(UUID idEquipe, UUID idUsuarioResponsavel,
                                              UUID idVeiculo, List<UUID> idsParceiros);
}
