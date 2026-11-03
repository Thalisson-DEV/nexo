package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.application.dto.TurnoObraFilter;
import com.nexo.app.modules.obras.application.dto.TurnoObraRequests;
import com.nexo.app.modules.obras.application.dto.TurnoObraResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Obras - Turnos", description = "Turnos das equipes de Obras, boletim, registros do turno e apontamento da execução")
@SecurityRequirement(name = "bearerAuth")
public interface TurnoObraControllerDoc {

    @Operation(summary = "Listar turnos de Obras", description = "Traz os indicadores do recorte junto da página. Status, meta, relatório e ocorrência não mudam os indicadores.")
    ResponseEntity<TurnoObraResponses.Lista> listar(TurnoObraFilter filtro, Pageable pageable);

    @Operation(summary = "Boletim das equipes", description = "Situação de cada equipe no dia, com programado e executado no ciclo, no dia e no dia útil anterior.")
    ResponseEntity<TurnoObraResponses.Boletim> boletim(LocalDate data, UUID idBase);

    @Operation(summary = "Detalhar um turno de Obras")
    ResponseEntity<TurnoObraResponses.Detalhe> detalhar(UUID id);

    @Operation(summary = "Registrar atividade em obra não programada")
    ResponseEntity<TurnoObraResponses.Detalhe> registrarAtividade(UUID id, TurnoObraRequests.Atividade request);

    @Operation(summary = "Retirar atividade em obra não programada")
    ResponseEntity<TurnoObraResponses.Detalhe> removerAtividade(UUID id, UUID idAtividade);

    @Operation(summary = "Salvar o relatório diário")
    ResponseEntity<TurnoObraResponses.Detalhe> salvarRelatorio(UUID id, TurnoObraRequests.Relatorio request);

    @Operation(summary = "Registrar ocorrência")
    ResponseEntity<TurnoObraResponses.Detalhe> registrarOcorrencia(UUID id, TurnoObraRequests.Ocorrencia request);

    @Operation(summary = "Alterar ocorrência")
    ResponseEntity<TurnoObraResponses.Detalhe> alterarOcorrencia(UUID id, UUID idOcorrencia, TurnoObraRequests.Ocorrencia request);

    @Operation(summary = "Excluir ocorrência")
    ResponseEntity<TurnoObraResponses.Detalhe> removerOcorrencia(UUID id, UUID idOcorrencia);

    @Operation(summary = "Registrar APR", description = "Começa em rascunho.")
    ResponseEntity<TurnoObraResponses.Detalhe> registrarApr(UUID id, TurnoObraRequests.Apr request);

    @Operation(summary = "Alterar APR em rascunho")
    ResponseEntity<TurnoObraResponses.Detalhe> alterarApr(UUID id, UUID idApr, TurnoObraRequests.Apr request);

    @Operation(summary = "Concluir APR", description = "Exige responsável. Concluída, a APR não muda mais.")
    ResponseEntity<TurnoObraResponses.Detalhe> concluirApr(UUID id, UUID idApr);

    @Operation(summary = "Excluir APR em rascunho")
    ResponseEntity<TurnoObraResponses.Detalhe> removerApr(UUID id, UUID idApr);

    @Operation(summary = "Anexar foto do relatório ou documento da APR", description = "Com idApr, aceita PDF ou imagem. Sem idApr, é foto do relatório e deve ser imagem.")
    ResponseEntity<TurnoObraResponses.Detalhe> anexar(UUID id, UUID idApr, MultipartFile arquivo);

    @Operation(summary = "Baixar arquivo do turno")
    ResponseEntity<StreamingResponseBody> baixarAnexo(UUID id, UUID idAnexo);

    @Operation(summary = "Remover arquivo do turno")
    ResponseEntity<TurnoObraResponses.Detalhe> removerAnexo(UUID id, UUID idAnexo);

    @Operation(summary = "Apontar a execução de itens de uma programação", description = "Soma a quantidade executada de cada item. Passar do previsto exige justificativa. Sem turno informado, entra no turno da equipe na data.")
    ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> apontar(UUID idProgramacao, TurnoObraRequests.Apontar request);

    @Operation(summary = "Estornar um apontamento", description = "Devolve a quantidade ao item. Item já medido não pode ser estornado.")
    ResponseEntity<Void> estornar(UUID idApontamento);
}
