package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.ProducaoPorTipoServico;
import com.nexo.app.modules.execucao.TipoServicoApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.media;
import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.soma;

/**
 * Distribui lançamentos pelos tipos de serviço, nomeando cada tipo pelo cadastro
 * (inclusive os desativados, que ainda aparecem em lançamentos antigos).
 */
@Component
public class TiposServicoDosLancamentos {

    static final String NAO_INFORMADO = "Não informado";

    private final TipoServicoApi tipoServicoApi;

    public TiposServicoDosLancamentos(TipoServicoApi tipoServicoApi) {
        this.tipoServicoApi = tipoServicoApi;
    }

    /** Lê o cadastro uma vez para agrupar vários recortes (o total e cada equipe). */
    public Agrupador agrupador() {
        Map<UUID, TipoServicoResponse> tipos = tipoServicoApi.listarTodos().stream()
                .collect(Collectors.toMap(TipoServicoResponse::id, Function.identity(), (a, b) -> a));
        return new Agrupador(tipos);
    }

    public static final class Agrupador {

        private final Map<UUID, TipoServicoResponse> tipos;

        Agrupador(Map<UUID, TipoServicoResponse> tipos) {
            this.tipos = tipos;
        }

        /** Descrição do tipo, ou nula quando a nota não tem tipo. */
        public String descricao(UUID idTipo) {
            TipoServicoResponse tipo = idTipo == null ? null : tipos.get(idTipo);
            return tipo != null ? tipo.descricao() : null;
        }

        /** Tipos com ao menos uma nota, do que tem mais notas para o que tem menos; "não informado" por último. */
        public List<ProducaoPorTipoServico> agrupar(List<LancamentoResumido> lancamentos) {
            if (lancamentos.isEmpty()) {
                return List.of();
            }
            Map<UUID, List<LancamentoResumido>> porTipo = new LinkedHashMap<>();
            for (LancamentoResumido lancamento : lancamentos) {
                porTipo.computeIfAbsent(lancamento.idTipoServico(), k -> new ArrayList<>()).add(lancamento);
            }
            long total = lancamentos.size();
            return porTipo.entrySet().stream()
                    .map(e -> producao(e.getKey(), e.getValue(), total))
                    .sorted(Comparator.comparing((ProducaoPorTipoServico p) -> p.idTipoServico() == null)
                            .thenComparing(ProducaoPorTipoServico::notas, Comparator.reverseOrder())
                            .thenComparing(ProducaoPorTipoServico::producao, Comparator.reverseOrder())
                            .thenComparing(ProducaoPorTipoServico::descricao))
                    .toList();
        }

        private ProducaoPorTipoServico producao(UUID idTipo, List<LancamentoResumido> doTipo, long total) {
            TipoServicoResponse tipo = idTipo == null ? null : tipos.get(idTipo);
            BigDecimal producao = soma(doTipo);
            long notas = doTipo.size();
            return new ProducaoPorTipoServico(
                    idTipo,
                    tipo != null ? tipo.codigo() : null,
                    tipo != null ? tipo.descricao() : NAO_INFORMADO,
                    notas,
                    producao,
                    media(producao, notas),
                    BigDecimal.valueOf(notas * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        }
    }
}
