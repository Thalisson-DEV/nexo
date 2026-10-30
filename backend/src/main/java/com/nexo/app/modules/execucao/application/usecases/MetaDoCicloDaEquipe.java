package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.MetaDoCicloResponse;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.JanelaMetaFaturamento;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Apura a meta da janela vigente da equipe e o faturamento já recebido nela. */
@Component
@RequiredArgsConstructor
class MetaDoCicloDaEquipe {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final MetaFaturamentoApi metaFaturamentoApi;
    private final ExecucaoComercialApi execucaoApi;

    /** Vazio quando a equipe não tem meta vigente hoje. */
    Optional<MetaDoCicloResponse> apurar(UUID idEquipe) {
        OffsetDateTime agora = OffsetDateTime.now(FUSO);
        LocalDate hoje = agora.toLocalDate();
        CronogramaMetaFaturamento cronograma = metaFaturamentoApi.cronograma(idEquipe);
        Optional<CronogramaMetaFaturamento.Vigencia> vigencia = cronograma.vigenciaEm(hoje);
        if (vigencia.isEmpty()) {
            return Optional.empty();
        }
        JanelaMetaFaturamento janela = vigencia.get().periodicidade().janelaEm(hoje);
        BigDecimal valor = vigencia.get().valor();
        int diasUteis = janela.diasUteis();
        BigDecimal metaDiaria = diasUteis == 0 ? null
                : valor.divide(BigDecimal.valueOf(diasUteis), 2, RoundingMode.HALF_UP);

        BigDecimal realizado = execucaoApi.listarLancamentosResumidos(
                        janela.inicio().atStartOfDay(FUSO).toOffsetDateTime(),
                        janela.fim().plusDays(1).atStartOfDay(FUSO).toOffsetDateTime(),
                        Set.of(idEquipe)).stream()
                .map(LancamentoResumido::valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new MetaDoCicloResponse(
                vigencia.get().periodicidade(),
                janela.competencia().toString(),
                janela.inicio(),
                janela.fim(),
                valor,
                metaDiaria,
                diasUteis,
                realizado,
                agora));
    }
}
