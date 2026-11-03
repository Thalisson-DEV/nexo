package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.MedicaoRequests;
import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.StatusMedicao;
import com.nexo.app.modules.obras.domain.TipoMedicao;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Medições", description = "Pré-fechamento do levantamento, medição parcial e por ciclo, estorno e fechamento de obra")
@SecurityRequirement(name = "bearerAuth")
public interface MedicaoObraControllerDoc {

    @Operation(summary = "Pré-fechamento do levantamento", description = "Obras em aberto com pontos, vãos, pendências de kit e de vão, tipo de execução e levantadores. Os indicadores contam o recorte antes do filtro \"apenas pendentes\".")
    ResponseEntity<MedicaoResponses.PreFechamento> preFechamento(String q, UUID idBase, boolean apenasPendentes);

    @Operation(summary = "Concluir o levantamento", description = "Recusado quando a obra não tem pontos ou tem ponto ou vão sem kit; a mensagem lista os pontos sem kit.")
    ResponseEntity<MedicaoResponses.LinhaPreFechamento> concluirLevantamento(UUID idObra);

    @Operation(summary = "Reabrir o levantamento")
    ResponseEntity<MedicaoResponses.LinhaPreFechamento> reabrirLevantamento(UUID idObra);

    @Operation(summary = "Obras com serviço a medir", description = "Serviços concluídos e ainda não medidos por obra, com o valor apontado.")
    ResponseEntity<List<MedicaoResponses.ObraAMedir>> obrasAMedir();

    @Operation(summary = "Medição da obra", description = "Serviços pendentes de medição, medições geradas e estornadas e o saldo sobre o valor de projeto.")
    ResponseEntity<MedicaoResponses.MedicaoDaObra> daObra(UUID idObra);

    @Operation(summary = "Medição por ciclo", description = "Valor de projeto, medido nas competências anteriores, medido na competência e saldos, com o que está a medir até o fim do ciclo.")
    ResponseEntity<MedicaoResponses.Ciclo> ciclo(UUID idObra, YearMonth competencia);

    @Operation(summary = "Gerar medição", description = "Sem itens escolhidos, entram todos os serviços pendentes (no ciclo, os concluídos até o fim da competência). Os itens passam a Medido.")
    ResponseEntity<MedicaoResponses.MedicaoDetalhe> gerar(UUID idObra, MedicaoRequests.Gerar request);

    @Operation(summary = "Listar medições", description = "Busca pelo número da medição (#12) ou pelo número e título da obra.")
    ResponseEntity<PageResponse<MedicaoResponses.MedicaoResumo>> listar(UUID idObra, List<TipoMedicao> tipos, List<StatusMedicao> status,
                                                                       YearMonth competenciaDe, YearMonth competenciaAte,
                                                                       List<UUID> idsBases, List<UUID> idsMunicipios, List<UUID> idsSupervisores,
                                                                       String usuario, LocalDate geradaDe, LocalDate geradaAte,
                                                                       String q, Pageable pageable);

    @Operation(summary = "Opções dos filtros de medições", description = "Municípios e supervisores das obras que têm medição.")
    ResponseEntity<MedicaoResponses.OpcoesMedicoes> opcoes();

    @Operation(summary = "Detalhar uma medição")
    ResponseEntity<MedicaoResponses.MedicaoDetalhe> buscar(UUID id);

    @Operation(summary = "Estornar uma medição", description = "Justificativa obrigatória. Os itens voltam a Concluído e a medição fica no histórico como estornada.")
    ResponseEntity<MedicaoResponses.MedicaoDetalhe> estornar(UUID id, MedicaoRequests.Estornar request);

    @Operation(summary = "Fechamento de obra", description = "Até 200 códigos, colados de uma coluna de planilha. Códigos sem obra voltam em naoEncontrados.")
    ResponseEntity<MedicaoResponses.Fechamento> fechamento(MedicaoRequests.Fechamento request);

    @Operation(summary = "Exportar o fechamento em XLSX", description = "Abas de resumo, serviços, materiais, kits e medições.")
    ResponseEntity<byte[]> exportarFechamento(MedicaoRequests.Fechamento request);

    @Operation(summary = "Modelo da planilha orçado × faturado", description = "Abas Materiais orçados, Mão de obra orçada e Faturado, com instruções. Aceita também planilhas já preenchidas no mesmo layout.")
    ResponseEntity<byte[]> modeloRelatorioDetalhado();

    @Operation(summary = "Analisar a planilha orçado × faturado", description = "Reconhece as obras pelo número e devolve totais, lançamentos para conferir, erros por obra e números sem obra cadastrada. Nada é gravado.")
    ResponseEntity<MedicaoResponses.AnaliseOrcadoFaturado> analisarRelatorioDetalhado(MultipartFile arquivo);

    @Operation(summary = "Gerar o relatório de medição detalhado", description = "Até 10 obras por pacote. ZIP com uma pasta por obra: Excel, PDF e as fotos do book. Orçado e faturado vêm da planilha; executado e medições, do sistema, até a data de corte (hoje quando vazia). Obra com linha errada na planilha é recusada.")
    ResponseEntity<byte[]> gerarRelatorioDetalhado(MultipartFile arquivo, List<UUID> idsObras, LocalDate dataCorte, String fiscal,
                                                   String responsavel, boolean incluirFotos);
}
