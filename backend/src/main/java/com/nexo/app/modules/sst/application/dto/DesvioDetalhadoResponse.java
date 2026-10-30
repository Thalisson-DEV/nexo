package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.modules.sst.domain.StatusInspecao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Desvio com todo o contexto que a inspeção de origem carrega — quem inspecionou, onde,
 * em que item do formulário e sobre quem recaiu.
 *
 * <p>É autocontido de propósito: a listagem geral é a tela em que o SESMT cobra
 * tratamento, e ter de abrir a inspeção para descobrir a base ou o enunciado da pergunta
 * transformaria cada linha da fila em duas telas.</p>
 *
 * <p>Os colaboradores vêm como {@link ResumoColaborador} — nome, função e base, sem CPF
 * nem datas: uma fila de tratamento precisa reconhecer a pessoa, não consultar o cadastro
 * dela, e o que não trafega não vaza.</p>
 */
public record DesvioDetalhadoResponse(
        UUID id,

        UUID idInspecao,
        OffsetDateTime dataInspecao,
        StatusInspecao statusInspecao,

        UUID idFormulario,
        String formulario,

        UUID idInspetor,
        String inspetor,

        ProcessoNegocioResponse processo,
        ProcessoNegocioResponse processoInspetor,
        BaseOperacionalResponse baseOperacional,
        EquipeResponse equipe,
        VeiculoResponse veiculo,

        UUID idPergunta,
        String grupo,
        String pergunta,
        UUID idOpcaoResposta,
        String opcaoResposta,

        GravidadeDesvio gravidade,
        String descricao,
        String acaoCorretiva,
        LocalDate prazo,
        StatusDesvio status,
        OffsetDateTime dataResolucao,

        /** Prazo estourado e desvio ainda não resolvido. */
        boolean vencido,

        /** Dias corridos além do prazo; {@code null} quando o desvio não está vencido. */
        Integer diasEmAtraso,

        List<ResumoColaborador> colaboradores,
        List<AnexoDesvioResponse> anexos,

        OffsetDateTime dataRegistro,
        OffsetDateTime dataUltimaAlteracao
) {
}
