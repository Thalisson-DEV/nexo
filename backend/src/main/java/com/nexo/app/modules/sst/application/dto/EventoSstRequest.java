package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.TipoCat;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Registro (e correção) de um evento de SST.
 *
 * <p>O mesmo contrato serve ao cadastro e à edição porque corrigir um evento é
 * reafirmar o que aconteceu, não um subconjunto disso: um acidente registrado como
 * dano material precisa poder virar acidente típico, com todas as consequências de
 * validação. Editar exige que o evento esteja aberto.</p>
 *
 * <p>O que este payload manda são <b>identificadores</b>. Os textos que ficam gravados
 * no evento (nome do gestor, da base, código da equipe, placa) são copiados dos
 * cadastros de origem no instante do registro — quem preenche o formulário não escolhe
 * o retrato.</p>
 */
public record EventoSstRequest(

        @NotNull(message = "O tipo do evento é obrigatório")
        TipoEventoSst tipo,

        @NotNull(message = "A data e hora da ocorrência são obrigatórias")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataOcorrencia,

        @NotNull(message = "A data e hora da comunicação são obrigatórias")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataComunicacao,

        @NotNull(message = "A atividade em execução é obrigatória")
        UUID idAtividade,

        @Schema(description = """
                Gestor responsável. Omitido, herda o gestor cadastrado na atividade.
                Informado e diferente dele, vale para este evento e fica marcado como
                substituto — o cadastro da atividade não é alterado. Obrigatório
                enquanto a atividade não tiver gestor definido.""")
        UUID idGestor,

        @NotNull(message = "A base operacional do evento é obrigatória")
        UUID idBaseOperacional,

        UUID idEquipe,

        UUID idVeiculo,

        @NotNull(message = "A classificação do evento é obrigatória")
        UUID idClassificacao,

        UUID idProcesso,

        RegistroEvidencia telemetria,

        RegistroEvidencia filmagem,

        @Size(max = 255)
        String localOcorrencia,

        BigDecimal latitude,

        BigDecimal longitude,

        @NotBlank(message = "A descrição do que aconteceu é obrigatória")
        @Size(max = 4000)
        String descricao,

        @Schema(description = "Obrigatório nos tipos que implicam lesão; recusado nos demais")
        UUID idLocalLesao,

        UUID idNaturezaLesao,

        @Schema(description = "O que causou a lesão ou o dano. Vale para qualquer tipo.")
        UUID idAgenteCausador,

        @PositiveOrZero(message = "Os dias perdidos não podem ser negativos")
        Integer diasPerdidos,

        boolean possuiCat,

        @Schema(description = "Obrigatório quando possuiCat = true; recusado quando false")
        TipoCat tipoCat,

        @Size(max = 40)
        String numeroCat,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataEmissaoCat,

        @NotEmpty(message = "Informe ao menos um técnico de segurança responsável")
        List<UUID> idsTecnicos,

        @Valid
        List<EnvolvidoEventoRequest> envolvidos
) {
}
