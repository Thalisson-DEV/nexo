package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.ProgramacaoRequests;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.domain.TipoEventoProgramacao;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Programação", description = "Programação dos itens planejados por equipe e período, Gantt e histórico")
@SecurityRequirement(name = "bearerAuth")
public interface ProgramacaoObraControllerDoc {

    @Operation(summary = "Prever a programação", description = "Informa o que será programado, o que fica de fora e a capacidade e a meta de cada equipe no período.")
    ResponseEntity<ProgramacaoResponses.Previa> previa(UUID idObra, ProgramacaoRequests.Previa request);

    @Operation(summary = "Programar itens", description = "Itens já programados ou concluídos são ignorados e informados. O valor dos serviços fica congelado pelo vigente na data de início.")
    ResponseEntity<ProgramacaoResponses.Resultado> programar(UUID idObra, ProgramacaoRequests.Programar request);

    @Operation(summary = "Listar as programações da obra")
    ResponseEntity<List<ProgramacaoResponses.ProgramacaoDetalhe>> listarDaObra(UUID idObra);

    @Operation(summary = "Linha do tempo por equipe", description = "Período de até 186 dias, com filtros opcionais por base, tipo de equipe e equipe.")
    ResponseEntity<ProgramacaoResponses.Gantt> gantt(LocalDate inicio, LocalDate fim, UUID idBase, UUID idTipoEquipe, UUID idEquipe);

    @Operation(summary = "Histórico de edições, reprogramações, cancelamentos e exclusões")
    ResponseEntity<PageResponse<ProgramacaoResponses.Evento>> historico(List<TipoEventoProgramacao> tipos, UUID idObra, UUID idProgramacao, String obra, String equipe, String usuario, String mudou, LocalDate de, LocalDate ate, String q, Pageable pageable);

    @Operation(summary = "Obras abertas com serviço por programar", description = "Até 50 obras, as de prazo mais curto primeiro. O prazo regulado manda sobre o previsto.")
    ResponseEntity<List<ProgramacaoResponses.ObraAProgramar>> obrasAProgramar(UUID idBase);

    @Operation(summary = "Situação do planejamento de várias obras", description = "Valor planejado, programado e concluído de cada obra, para listas.")
    ResponseEntity<List<ProgramacaoResponses.ResumoPlanejamento>> resumos(List<UUID> ids);

    @Operation(summary = "Detalhar uma programação")
    ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> buscar(UUID id);

    @Operation(summary = "Editar as condições da programação")
    ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> editar(UUID id, ProgramacaoRequests.Editar request);

    @Operation(summary = "Trocar equipe ou período", description = "Observação obrigatória. O valor congelado na confirmação não muda.")
    ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> reprogramar(UUID id, ProgramacaoRequests.Reprogramar request);

    @Operation(summary = "Cancelar a programação", description = "Observação obrigatória. Os itens sem execução voltam a pendentes.")
    ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> cancelar(UUID id, ProgramacaoRequests.Encerrar request);

    @Operation(summary = "Excluir a programação", description = "Os itens voltam a pendentes. Programação com execução só pode ser cancelada.")
    ResponseEntity<Void> excluir(UUID id, ProgramacaoRequests.Encerrar request);

    @Operation(summary = "Retirar um item da programação", description = "O item volta a pendente. Responde 204 quando era o último e a programação foi excluída.")
    ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> retirarItem(UUID id, UUID idItem);
}
