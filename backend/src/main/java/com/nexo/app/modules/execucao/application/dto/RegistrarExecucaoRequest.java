package com.nexo.app.modules.execucao.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nexo.app.modules.execucao.domain.OrigemMaterial;
import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lançamento completo de uma execução de campo em uma única requisição.
 *
 * <p>Reúne metadados obrigatórios e opcionais, ações realizadas e materiais
 * aplicados/retirados, permitindo que o cliente (PWA offline) monte a execução
 * inteira localmente e a envie de uma só vez. A execução é criada já no status
 * CONCLUIDO.</p>
 *
 * <p>O lançamento acontece de uma só vez: há uma única data (a do lançamento) e
 * uma única localização, coletada automaticamente pelo dispositivo.</p>
 *
 * <p>O tipo de serviço ({@code idTipoServico}) é obrigatório no app de campo. O
 * servidor ainda aceita lançamentos sem ele, que ficam como "não informado": são os
 * que já estavam na fila offline de aparelhos com a versão anterior do app.</p>
 *
 * <p>Compatibilidade: aparelhos com versões antigas do app ainda podem ter na fila
 * offline lançamentos com {@code dataInicio}, {@code latitudeInicio} e
 * {@code longitudeInicio}; esses nomes continuam aceitos como aliases. Campos de
 * fim ({@code dataFim}, {@code latitudeFim}, {@code longitudeFim}) são ignorados.</p>
 *
 * <p>Sem {@code tipoNota} o lançamento é uma nota COMUM e, sem {@code origem}, o
 * material é da EQUIPE: é o que chega das versões do app anteriores à nota avulsa e à
 * ocorrência comercial.</p>
 */
public record RegistrarExecucaoRequest(
        @NotNull(message = "A referência da nota é obrigatória")
        String referenciaNota,

        @NotNull(message = "O ID da equipe é obrigatório")
        UUID idEquipe,

        UUID idTipoServico,

        UUID idMunicipio,

        @NotNull(message = "A data de lançamento é obrigatória")
        @JsonAlias("dataInicio")
        OffsetDateTime dataLancamento,

        @JsonAlias("latitudeInicio")
        BigDecimal latitude,

        @JsonAlias("longitudeInicio")
        BigDecimal longitude,

        // Ações realizadas (opcionais)
        List<UUID> acoes,

        // Materiais aplicados/retirados (opcionais)
        @Valid
        List<MaterialItem> materiais,

        // Turno em que o aparelho fez o lançamento. Só decide o vínculo quando esse
        // turno foi encerrado automaticamente e o lançamento é anterior ao corte dele
        // (estava na fila offline); nos demais casos vale o turno aberto da equipe.
        UUID idTurno,

        TipoNota tipoNota
) {
    public RegistrarExecucaoRequest {
        acoes = acoes == null ? List.of() : acoes;
        materiais = materiais == null ? List.of() : materiais;
        tipoNota = tipoNota == null ? TipoNota.COMUM : tipoNota;
    }

    public record MaterialItem(
            @NotNull(message = "O ID do material é obrigatório")
            UUID idMaterial,

            @NotNull(message = "A quantidade é obrigatória")
            @Positive(message = "A quantidade deve ser positiva")
            BigDecimal quantidade,

            @NotNull(message = "A operação do material é obrigatória")
            OperacaoMaterial operacao,

            String numSerie,

            OrigemMaterial origem
    ) {
        public MaterialItem {
            origem = origem == null ? OrigemMaterial.EQUIPE : origem;
        }
    }
}
