package com.nexo.app.modules.desconto.doc;

import com.nexo.app.modules.desconto.application.dto.DashboardDescontoFilter;
import com.nexo.app.modules.desconto.application.dto.DashboardDescontosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

/**
 * Todos os recortes deste dashboard aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idMotivo=a&idMotivo=b}) ou separe os valores por vírgula
 * ({@code ?idMotivo=a,b}). Valores de uma mesma dimensão são combinados com OU;
 * dimensões diferentes, com E.
 */
@Tag(name = "Descontos - Dashboard",
        description = "Indicadores executivos de descontos em folha: recuperação de valor, "
                + "gargalos do ciclo e onde as perdas se concentram")
@SecurityRequirement(name = "bearerAuth")
public interface DashboardDescontoDoc {

    @Operation(
            summary = "Dashboard executivo de descontos",
            description = """
                    Consolida, em uma única chamada, quatro leituras do domínio de descontos:

                    **Quanto**
                    - `resumo`: volume por status, pendentes, taxa de aprovação e rejeição, cobertura do
                      efetivo, reincidentes e médias de itens e parcelas.
                    - `financeiro`: valor em cada etapa do ciclo (solicitado, validado, baixado) e o que se
                      perde entre elas — a **glosa** é o corte feito na validação; o **ajuste**, o feito na
                      baixa. A `taxaRecuperacao` é o baixado sobre o solicitado.

                    **Como flui**
                    - `funil`: conversão da coorte do período — solicitados → validados → baixados, com os
                      rejeitados como vazamento. Um termo baixado conta também como validado, então o funil
                      só estreita da esquerda para a direita.
                    - `serieTemporal`: evolução por DIA, MES ou ANO conforme `granularidade` — omitida, é
                      escolhida pelo tamanho do período (até 62 dias = DIA, até 3 anos = MES, acima = ANO).
                      Todos os baldes são devolvidos, inclusive os zerados.
                    - `ciclo`: dias de cada etapa com média, mediana e P90. O P90 é o número que serve para
                      prometer prazo; a média sozinha esconde o termo esquecido.
                    - `envelhecimento`: backlog por faixa de idade, contada desde a entrada na **etapa
                      atual**. Inclui pendências abertas antes do período — backlog não respeita recorte.

                    **Onde dói**
                    - `porMotivo`, `porBaseOperacional`, `porCentroCusto`, `porFuncao`, `porSolicitante`:
                      volume e dinheiro lado a lado, ordenados por valor.
                    - `porTipoEquipamento`: contado por **item**, não por termo — é o item que diz qual
                      material está sumindo.
                    - `porColaborador`: com a data da última ocorrência, para separar histórico antigo de
                      problema corrente.

                    **O que fazer**
                    - `prioridades`: pendências acionáveis com evidência, ação sugerida e o `drilldown` que
                      reproduz o conjunto na listagem.
                    - `qualidade`: pendências sem anexo, sem itens ou sem observação, além dos termos cujo
                      colaborador não foi encontrado no cadastro de RH — estes últimos ficam de fora dos
                      agrupamentos por base, centro de custo e função, e o número existe para explicar a
                      diferença.

                    O período recorta pela **data de ocorrência** (o fato gerador), com bordas inclusivas;
                    período padrão é o mês corrente. Todas as variações comparam com a janela anterior
                    equivalente — para um mês de calendário fechado, o mês anterior inteiro.
                    Denominadores iguais a zero retornam 0.

                    Os filtros de base operacional, centro de custo e função recortam pelo **colaborador
                    descontado**; `idTipoEquipamento` inclui o termo que tenha ao menos um item daquele
                    tipo, sem recortar os demais itens dele.""")
    ResponseEntity<DashboardDescontosResponse> dashboard(@ParameterObject DashboardDescontoFilter filter);
}
