package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses.ResumoPlanejamento;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemPlanejadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Situação do planejamento de várias obras de uma vez, para listas: quanto está planejado, programado e concluído
 * em valor de serviço. Usa o valor vigente hoje, porque serve para comparar obras, não para medir.
 */
@Component
@RequiredArgsConstructor
class ResumoPlanejamentoObras {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ItemPlanejadoRepository itemRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final KitConstrutivoUseCases kitUseCases;

    Map<UUID, ResumoPlanejamento> calcular(Collection<UUID> idsObras) {
        if (idsObras.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal[]> somas = new HashMap<>();
        Map<UUID, Integer> pendentes = new HashMap<>();
        Map<UUID, Map<UUID, BigDecimal>> valores = new HashMap<>();
        LocalDate hoje = LocalDate.now();
        for (ItemPlanejadoRepository.ServicosDaObra linha : itemRepository.somarServicos(idsObras)) {
            BigDecimal unitario = valores.computeIfAbsent(linha.idContrato(), id -> kitUseCases.valoresVigentes(id, hoje)).get(linha.idAcao());
            BigDecimal valor = unitario != null ? unitario.multiply(linha.quantidade()) : BigDecimal.ZERO;
            BigDecimal[] soma = somas.computeIfAbsent(linha.idObra(), id -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            soma[0] = soma[0].add(valor);
            if (linha.status() == StatusItemPlanejado.PROGRAMADO || linha.status() == StatusItemPlanejado.EM_ANDAMENTO) {
                soma[1] = soma[1].add(valor);
            } else if (linha.status() == StatusItemPlanejado.CONCLUIDO || linha.status() == StatusItemPlanejado.MEDIDO) {
                soma[2] = soma[2].add(valor);
            } else {
                pendentes.merge(linha.idObra(), (int) linha.itens(), Integer::sum);
            }
        }
        Map<UUID, Integer> semKit = new HashMap<>();
        for (Object[] linha : pontoRepository.contarSemKit(idsObras)) {
            semKit.put((UUID) linha[0], ((Number) linha[1]).intValue());
        }
        Map<UUID, ResumoPlanejamento> resumos = new HashMap<>();
        for (UUID id : idsObras) {
            BigDecimal[] soma = somas.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            resumos.put(id, new ResumoPlanejamento(id, somas.containsKey(id), moeda(soma[0]), moeda(soma[1]), moeda(soma[2]),
                    moeda(soma[0].subtract(soma[1]).subtract(soma[2])),
                    percentual(soma[1], soma[0]), percentual(soma[2], soma[0]),
                    pendentes.getOrDefault(id, 0), semKit.getOrDefault(id, 0)));
        }
        return resumos;
    }

    List<UUID> obrasComPendencia() {
        return itemRepository.obrasComServicoPendente();
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }
}
