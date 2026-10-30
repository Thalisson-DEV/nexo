package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoCat;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Um evento de SST por inteiro.
 *
 * <p>Os nomes de atividade, gestor, base, equipe, veículo e pessoas são o <b>retrato do
 * registro</b>: não são relidos dos cadastros e por isso não mudam quando alguém troca
 * de função, de equipe ou de empresa. Já classificação, lesão, agente e processo são
 * resolvidos na leitura — corrigir o nome no catálogo corrige aqui também.</p>
 *
 * <p>O atraso de comunicação também é calculado na leitura, contra o prazo legal do
 * tipo do evento, e nunca gravado.</p>
 */
public record EventoSstResponse(
        UUID id,

        @Schema(description = "Identificador legível EVT-AAAA-NNNNNN, único e nunca reaproveitado")
        String numeroRegistro,

        TipoEventoSst tipo,
        StatusEventoSst status,

        OffsetDateTime dataOcorrencia,
        OffsetDateTime dataComunicacao,

        @Schema(description = "Horas entre a ocorrência e a comunicação (derivado)")
        long horasAteComunicacao,

        @Schema(description = "Comunicação fora do prazo legal do tipo (derivado, nunca gravado)")
        boolean comunicacaoAtrasada,

        // Snapshot — atividade e gestor
        UUID idAtividade,
        String atividadeNome,
        UUID idGestor,
        String gestorNome,
        String gestorFuncao,

        @Schema(description = "Gestor informado no evento por divergir do cadastrado na atividade")
        boolean gestorSubstituto,

        // Snapshot — onde
        UUID idBaseOperacional,
        String baseNome,
        UUID idEquipe,
        String equipeCodigo,
        UUID idVeiculo,
        String veiculoPlaca,

        // Referências vivas
        UUID idClassificacao,
        String classificacaoNome,
        UUID idProcesso,
        String processoNome,

        // Circunstância
        RegistroEvidencia telemetria,
        RegistroEvidencia filmagem,
        String localOcorrencia,
        BigDecimal latitude,
        BigDecimal longitude,
        String descricao,

        // Lesão
        @Schema(description = "Derivado do tipo do evento")
        boolean comLesao,
        UUID idLocalLesao,
        String localLesaoNome,
        UUID idNaturezaLesao,
        String naturezaLesaoNome,
        UUID idAgenteCausador,
        String agenteCausadorNome,
        Integer diasPerdidos,

        // CAT
        boolean possuiCat,
        TipoCat tipoCat,
        String numeroCat,
        LocalDate dataEmissaoCat,

        // Tratativa e desfecho
        String tratativa,
        OffsetDateTime dataConclusao,
        OffsetDateTime dataCancelamento,
        String motivoCancelamento,
        String motivoReabertura,

        List<TecnicoEventoResponse> tecnicos,
        List<EnvolvidoEventoResponse> envolvidos,
        List<AnexoEventoResponse> anexos,

        boolean ativo,
        OffsetDateTime dataCriacao,
        String usuarioCriacao
) {
}
