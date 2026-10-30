package com.nexo.app.modules.estoque.doc;

import com.nexo.app.modules.estoque.application.dto.DashboardSolicitacaoFilter;
import com.nexo.app.modules.estoque.application.dto.DashboardSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesFilter;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Estoque - Dashboard de Solicitações", description = "Fornece métricas e indicadores de solicitações de materiais.")
@SecurityRequirement(name = "bearerAuth")
public interface DashboardSolicitacaoControllerDoc {

    @Operation(summary = "Obter métricas do dashboard",
               description = "Retorna os totais, quantidades por status e médias baseados em filtros como Estoque, Base Operacional ou Processo.")
    ResponseEntity<DashboardSolicitacaoResponse> obterMetricas(@ParameterObject DashboardSolicitacaoFilter filter);

    @Operation(summary = "Painel analítico de solicitações",
               description = "Solicitações do período (comercial, técnico ou ambos) com etapa operacional, marcos de tempo e "
                       + "quantidades atendidas, mais as ainda abertas criadas antes do período (backlog), os materiais "
                       + "agregados e o resumo do período anterior de mesma duração. Limitado a 5.000 solicitações.")
    ResponseEntity<PainelSolicitacoesResponse> obterPainel(@ParameterObject PainelSolicitacoesFilter filter);

    @Operation(summary = "Exportar o painel de solicitações (CSV ou XLSX)",
               description = "Mesmo recorte do painel. Conteúdo: solicitações (resumo = uma por linha; detalhado = um item por "
                       + "linha), materiais (resumo = por código; detalhado = por processo e sentido), equipes ou almoxarifados. "
                       + "Filtra por situação atual e inclui ou não as abertas de antes do período. XLSX traz a aba \"Sobre\".")
    ResponseEntity<StreamingResponseBody> exportarPainel(@ParameterObject PainelSolicitacoesFilter filter,
                                                         @ParameterObject ExportarPainelSolicitacoesRequest pedido);
}
