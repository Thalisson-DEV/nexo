package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.DashboardDesviosResponse;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstFilter;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstResponse;
import com.nexo.app.modules.sst.application.dto.DashboardInspecoesResponse;
import com.nexo.app.modules.sst.application.dto.DashboardSstFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

/**
 * Todos os recortes destes dashboards aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idBaseOperacional=a&idBaseOperacional=b}) ou separe os valores por vírgula
 * ({@code ?idBaseOperacional=a,b}). Enviar um único valor mantém o comportamento antigo.
 * Valores de uma mesma dimensão são combinados com OU; dimensões diferentes, com E.
 */
@Tag(name = "SST - Dashboards", description = "Indicadores executivos de inspeções de segurança e desvios (não-conformidades)")
@SecurityRequirement(name = "bearerAuth")
public interface DashboardSstControllerDoc {

    @Operation(
            summary = "Dashboard de inspeções e metas",
            description = """
                    Dois blocos de cartões e quatro conjuntos de séries prontas para gráfico:

                    - `resumo`: total de inspeções (com variação sobre o período anterior), inspeções com e sem
                      desvio, taxa de conformidade, médias de desvios/colaboradores por inspeção e de inspeções
                      por inspetor e por dia.
                    - `meta`: meta do período, realizado, % atingido, quantos inspetores já cumpriram a própria
                      meta e quantos colaboradores têm meta sem usuário vinculado (não conseguem lançar inspeção).
                    - `serieTemporal`: realizado x meta por DIA, MES ou ANO, conforme `granularidade` — omitida,
                      é escolhida pelo tamanho do período (até 62 dias = DIA, até 3 anos = MES, acima = ANO).
                      Todos os baldes do período são devolvidos, inclusive os zerados.
                    - `porInspetor`: realizado x meta de cada inspetor, incluindo quem tem meta e não lançou nada.
                    - `porProcesso`, `porBaseOperacional`, `porEquipe`, `porFormulario`: distribuição simples do volume.

                    A meta de um período que não coincide com a janela da meta é rateada por dia (uma meta mensal
                    de 4 inspeções contribui com 4/31 em cada dia de um mês cheio). Os filtros de inspetor, base
                    operacional e centro de custo também restringem a meta e o efetivo usado como denominador de
                    cobertura; os demais filtros afetam apenas o realizado.

                    Todos os recortes aceitam múltipla seleção (parâmetro repetido ou valores separados por vírgula).
                    O filtro de centro de custo equivale a selecionar todas as bases operacionais vinculadas a ele.

                    O período considera dataRealizacao com bordas inclusivas; denominadores iguais a zero retornam 0.
                    Período padrão: mês corrente.""")
    ResponseEntity<DashboardInspecoesResponse> dashboardInspecoes(@ParameterObject DashboardSstFilter filter);

    @Operation(
            summary = "Dashboard executivo de desvios",
            description = """
                    A leitura principal é o cenário histórico completo: um desvio resolvido continua sendo um
                    desvio da coorte em que foi apontado. Sem filtro de status, `totalDesvios`, distribuições,
                    rankings, comparativos gerais e `tendenciaDesvios` incluem ABERTO, EM_TRATAMENTO e RESOLVIDO.

                    - `tendenciaDesvios`: ocorrências pela data de realização da inspeção, por DIA, MES ou ANO.
                      Todos os baldes são devolvidos, inclusive os zerados. `granularidade` omitida é escolhida
                      pelo tamanho do período (até 62 dias = DIA, até 3 anos = MES, acima = ANO).
                    - `indicadores.totalDesvios`, `desviosCriticos` e `inspecoesComDesvio`: comparativos gerais
                      com a janela anterior equivalente; resolvidos permanecem contabilizados.
                    - `volumePorGravidade`: distribuição e comparativo de volume apontado por severidade contra a janela anterior.
                    - backlog, vencidos, prioridades e `serieDesvios`: leitura operacional das pendências.
                      Pendente significa ABERTO ou EM_TRATAMENTO; vencido é pendente com prazo anterior à referência.
                    - `desviosPorDia` e `serieDesvios` permanecem temporariamente por compatibilidade; para a
                      tendência gerencial, use `tendenciaDesvios`.

                    Um filtro explícito de status recorta a análise para os estados solicitados. Todos os recortes
                    aceitam múltipla seleção (parâmetro repetido ou valores separados por vírgula); centro de custo
                    equivale a selecionar suas bases operacionais. Período padrão: mês corrente.""")
    ResponseEntity<DashboardDesviosResponse> dashboardDesvios(@ParameterObject DashboardSstFilter filter);

    @Operation(
            summary = "Dashboard executivo de acidentalidade",
            description = """
                    Consolida os eventos de segurança — do incidente sem lesão ao óbito — **ocorridos**
                    no período (não os registrados nele). Eventos **cancelados ficam de fora de
                    todos os indicadores**.

                    - `resumo`: total com variação sobre o período anterior de mesmo tamanho,
                      quebra por tipo, eventos com lesão, dias perdidos, cobertura de CAT, média
                      de horas até a comunicação e participação das comunicações fora do prazo
                      legal (24 h para acidentes e doença ocupacional, 72 h para os demais).
                    - `serie`: total, com lesão e dias perdidos por DIA, MES ou
                      ANO, conforme `granularidade` — omitida, é escolhida pelo tamanho do período
                      (até 62 dias = DIA, até 3 anos = MES, acima = ANO). Baldes vazios vêm zerados.
                    - `piramide`: a pirâmide de Bird em cinco níveis: fatalidade, acidentes com
                      afastamento, acidentes sem afastamento, incidentes e desvios. A base não é um
                      evento: são os desvios apontados nas inspeções do período.
                    - `diasSemAcidente`: por base operacional. **Ignora o período** de propósito —
                      "há quanto tempo não acontece" se responde contra a última ocorrência de
                      fato. Base sem nenhum evento com lesão vem com valor nulo.
                    - `backlog`: eventos ainda abertos por idade **da ocorrência**, não do registro.
                    - rankings por classificação, atividade, gestor, base, equipe, processo e local
                      da lesão (este restrito aos eventos com lesão), limitados aos dez maiores.

                    Período vazio devolve indicadores zerados e listas vazias, nunca erro.
                    Período padrão: mês corrente.""")
    ResponseEntity<DashboardEventosSstResponse> dashboardEventos(@ParameterObject DashboardEventosSstFilter filter);
}
