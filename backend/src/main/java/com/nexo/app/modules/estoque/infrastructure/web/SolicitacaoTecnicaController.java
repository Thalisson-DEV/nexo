package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarDevolucaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarEntregaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmacaoImportacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.CriacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.EncerrarSaldoTecnicoRequest;
import com.nexo.app.modules.estoque.application.dto.EstornarOperacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.OperacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.PlanejarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.PlanejamentoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ReservaSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.usecases.EncerramentoSaldoTecnicoUseCases;
import com.nexo.app.modules.estoque.application.usecases.ImportacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.OperacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.ReservaSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.SeparacaoSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoMaterialUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.KanbanSolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.dto.KanbanSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** API dos comandos técnicos; o endpoint comercial legado permanece inalterado. */
@RestController
@RequestMapping("/v1/solicitacoes-material")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ces.estoque.solicitacoes-tecnicas",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@Tag(
        name = "Solicitacoes tecnicas de material",
        description = "Workflow tecnico por obra e ocorrencia OC/OS. O cliente informa "
                + "221/222/921/922 por item; o backend cria solicitacoes independentes por tipo "
                + "e controla versoes e idempotencia. "
                + "Entregas transferem a responsabilidade para o estoque CAMPO da equipe; devolucoes retornam ao almoxarifado de atendimento.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Preenchimento ou referencia invalida",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autoridade ausente",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "404", description = "Recurso nao encontrado",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "409", description = "Conflito de versao, estado ou idempotencia",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class))),
        @ApiResponse(responseCode = "422", description = "Regra de negocio ou preenchimento de dominio invalido",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(
                        implementation = com.nexo.app.shared.application.dto.ProblemaHttpResponse.class)))
})
public class SolicitacaoTecnicaController {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final SolicitacaoTecnicaUseCases solicitacoes;
    private final SolicitacaoMaterialUseCases consultas;
    private final ReservaSolicitacaoTecnicaUseCases reservas;
    private final SeparacaoSolicitacaoTecnicaUseCases separacao;
    private final OperacaoSolicitacaoTecnicaUseCases operacoes;
    private final EncerramentoSaldoTecnicoUseCases encerramentos;
    private final ImportacaoSolicitacaoTecnicaUseCases importacoes;
    private final KanbanSolicitacaoTecnicaUseCases kanban;

    @GetMapping("/tecnicas/kanban")
    @Operation(
        summary = "Kanban de solicitações técnicas",
        description = "Retorna as 6 colunas operacionais com até 30 cards cada e métricas agregadas (total, atrasadas, fora de prazo, dias da mais antiga). " +
            "Os filtros aceitam os mesmos parâmetros da listagem tabular, exceto paginação. " +
            "Autoridade: estoque:solicitacao:read."
    )
    @ApiResponse(responseCode = "200", description = "Colunas do kanban com cards e métricas")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<KanbanSolicitacaoTecnicaResponse> obterKanban(
        @RequestParam(required = false) UUID idEstoqueOrigem,
        @RequestParam(required = false) UUID idEstoqueDestino,
        @RequestParam(required = false) UUID idBaseOrigem,
        @RequestParam(required = false) UUID idBaseDestino,
        @RequestParam(required = false) UUID idEquipe,
        @RequestParam(required = false) UUID idAlmoxarifado,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
        @RequestParam(required = false) TipoSolicitacaoTecnica tipoTecnico,
        @RequestParam(required = false) String ocorrencia
    ) {
        SolicitacaoFilter filtro = new SolicitacaoFilter(
            idEstoqueOrigem, idEstoqueDestino, idBaseOrigem, idBaseDestino,
            null, // idRequisitante
            null, // status
            null, // statuses
            ContextoSolicitacaoMaterial.TECNICO, // contexto fixo
            tipoTecnico,
            null, // tipoObra
            null, // idObra
            ocorrencia,
            idEquipe,
            idAlmoxarifado,
            null, // numeroReserva
            null, // idAgenteReserva
            null, // statusSeparacao
            null, // etapa
            dataInicio,
            dataFim,
            null, // referencia
            null, // numSerie
            null  // buscaGlobal
        );
        return ResponseEntity.ok(kanban.obter(filtro));
    }

    @GetMapping(value = {"/importacoes/modelo", "/tecnicas/importacoes/modelo", "/tecnicas/modelo"})
    @Operation(summary = "Baixar modelo de importacao tecnica",
            description = "Retorna CSV UTF-8 separado por ponto e virgula, limitado a 2.000 linhas na validacao. "
                    + "Autoridade: estoque:solicitacao:import.")
    @ApiResponse(responseCode = "200", description = "Modelo CSV")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:import', 'estoque:solicitacao:read', 'estoque:solicitacao:write')")
    public ResponseEntity<byte[]> baixarModeloImportacao() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"modelo-importacao-solicitacoes-tecnicas.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(importacoes.modeloCsv());
    }

    @PostMapping(value = "/importacoes/validacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validar arquivo de solicitacoes tecnicas",
            description = "Etapa sincrona: nao persiste solicitacoes; resolve referencias, normaliza OC/OS e devolve token opaco, processamento e mensagens da previa. "
                    + "Autoridade: estoque:solicitacao:import.")
    @ApiResponse(responseCode = "200", description = "Previa normalizada com token opaco")
    @PreAuthorize("hasAuthority('estoque:solicitacao:import')")
    public ResponseEntity<ImportacaoSolicitacaoTecnicaResponse> validarImportacao(
            @RequestPart("arquivo") MultipartFile arquivo
    ) {
        return ResponseEntity.ok(importacoes.validar(arquivo, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/importacoes/confirmacao")
    @Operation(summary = "Confirmar grupos validos da importacao",
            description = "Etapa sincrona: aguarda a confirmacao de cada chave em transacao propria e devolve estado final e erro por par. Requer reconhecimento dos avisos e Idempotency-Key. "
                    + "Autoridade: estoque:solicitacao:import.")
    @ApiResponse(responseCode = "200", description = "Resultado independente por chave selecionada")
    @PreAuthorize("hasAuthority('estoque:solicitacao:import')")
    public ResponseEntity<ConfirmacaoImportacaoSolicitacaoTecnicaResponse> confirmarImportacao(
            @Valid @RequestBody ConfirmarImportacaoSolicitacaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        return ResponseEntity.ok(importacoes.confirmar(
                request, SecurityUtils.getUsuarioLogadoId(), chaveIdempotencia));
    }

    @PostMapping("/tecnicas")
    @Operation(summary = "Criar solicitacoes tecnicas independentes",
            description = "O tipo 221/222/921/922 e informado por item; cada tipo recebe um ID independente. "
                    + "Idempotency-Key e recomendado para repeticao segura. Autoridade: estoque:solicitacao:create.")
    @ApiResponse(responseCode = "201", description = "Solicitacoes independentes criadas")
    @PreAuthorize("hasAuthority('estoque:solicitacao:create')")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = CriarSolicitacaoTecnicaRequest.class)))
    public ResponseEntity<CriacaoSolicitacaoTecnicaResponse> criar(
            @Valid @RequestBody CriarSolicitacaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente recomendada para repeticao segura da submissao")
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveIdempotencia
    ) {
        CriacaoSolicitacaoTecnicaResponse response = solicitacoes.criar(
                request, SecurityUtils.getUsuarioLogadoId(), OrigemComandoTecnico.UI, chaveIdempotencia);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    @PutMapping("/tecnicas/{idSolicitacao}/planejamento")
    @Operation(summary = "Substituir planejamento da solicitacao",
            description = "Snapshot completo da solicitacao; conflito de versao retorna 409. "
                    + "Autoridade: estoque:solicitacao:plan.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Detalhes atualizados, nova versao e acoes permitidas",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PlanejamentoSolicitacaoTecnicaResponse.class))))
    @PreAuthorize("hasAuthority('estoque:solicitacao:plan')")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = PlanejarSolicitacaoTecnicaRequest.class)))
    public ResponseEntity<PlanejamentoSolicitacaoTecnicaResponse> planejar(
            @PathVariable UUID idSolicitacao,
            @RequestBody String corpo
    ) {
        PlanejarSolicitacaoTecnicaRequest request = lerPlanejamento(corpo);
        solicitacoes.replanejar(
                idSolicitacao, request, SecurityUtils.getUsuarioLogadoId(), OrigemComandoTecnico.UI);
        return ResponseEntity.ok(PlanejamentoSolicitacaoTecnicaResponse.de(
                consultas.buscarPorIdCompleto(idSolicitacao)));
    }

    private PlanejarSolicitacaoTecnicaRequest lerPlanejamento(String corpo) {
        PlanejarSolicitacaoTecnicaRequest request;
        try {
            request = PlanejarSolicitacaoTecnicaRequest.fromJson(corpo);
        } catch (Exception ex) {
            throw new PreenchimentoInvalidoException(
                    Map.of("planejamento", "O corpo do planejamento possui JSON invalido."),
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        Map<String, String> erros = new LinkedHashMap<>();
        Set<ConstraintViolation<PlanejarSolicitacaoTecnicaRequest>> violacoes = VALIDATOR.validate(request);
        violacoes.forEach(violacao -> erros.putIfAbsent(
                violacao.getPropertyPath().toString(), violacao.getMessage()));
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return request;
    }

    @PutMapping("/tecnicas/{idSolicitacao}/reserva")
    @Operation(summary = "Registrar ou alterar reserva externa",
            description = "Reserva obrigatoria por filho, sem bloquear saldo. Alteracao posterior exige justificativa. "
                    + "Requer Idempotency-Key. Autoridade: estoque:solicitacao:reserve.")
    @ApiResponse(responseCode = "200", description = "Snapshot atual da reserva externa")
    @PreAuthorize("hasAuthority('estoque:solicitacao:reserve')")
    public ResponseEntity<ReservaSolicitacaoTecnicaResponse> registrarReserva(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody RegistrarReservaTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        return ResponseEntity.ok(reservas.registrarOuAlterar(
                idSolicitacao, request, SecurityUtils.getUsuarioLogadoId(),
                OrigemComandoTecnico.UI, chaveIdempotencia));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/separacao/iniciar")
    @Operation(summary = "Iniciar separacao de adicao",
            description = "Somente tipos 221/921 com data prevista; devolucoes 222/922 nunca passam por separacao. "
                    + "Autoridade: estoque:solicitacao:separate.")
    @ApiResponse(responseCode = "200", description = "Novo estado de separacao")
    @PreAuthorize("hasAuthority('estoque:solicitacao:separate')")
    public ResponseEntity<StatusSeparacao> iniciarSeparacao(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody AlterarSeparacaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        return ResponseEntity.ok(separacao.iniciar(
                idSolicitacao, request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId(), chaveIdempotencia));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/separacao/concluir")
    @Operation(summary = "Concluir separacao de adicao",
            description = "Conclui somente uma adicao em EM_SEPARACAO. Autoridade: estoque:solicitacao:separate.")
    @ApiResponse(responseCode = "200", description = "Estado SEPARADO")
    @PreAuthorize("hasAuthority('estoque:solicitacao:separate')")
    public ResponseEntity<StatusSeparacao> concluirSeparacao(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody AlterarSeparacaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        return ResponseEntity.ok(separacao.concluir(
                idSolicitacao, request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId(), chaveIdempotencia));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/separacao/pular")
    @Operation(summary = "Pular separacao de adicao",
            description = "Pula a separacao avancando direto para SEPARADO. Autoridade: estoque:solicitacao:separate.")
    @ApiResponse(responseCode = "200", description = "Estado SEPARADO")
    @PreAuthorize("hasAuthority('estoque:solicitacao:separate')")
    public ResponseEntity<StatusSeparacao> pularSeparacao(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody AlterarSeparacaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        return ResponseEntity.ok(separacao.pular(
                idSolicitacao, request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId(), chaveIdempotencia));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/entregas")
    @Operation(summary = "Confirmar entrega tecnica",
            description = "Somente 221/921 separados e com reserva. Transfere material novo do estoque de atendimento para o estoque CAMPO da equipe. "
                    + "Autoridade: estoque:solicitacao:deliver.")
    @ApiResponse(responseCode = "200", description = "Identificador da operacao de entrega")
    @PreAuthorize("hasAuthority('estoque:solicitacao:deliver')")
    public ResponseEntity<OperacaoTecnicaResponse> entregar(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody ConfirmarEntregaTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        UUID idOperacao = operacoes.entregar(idSolicitacao, request, SecurityUtils.getUsuarioLogadoId(),
                OrigemComandoTecnico.UI, chaveIdempotencia);
        return ResponseEntity.ok(new OperacaoTecnicaResponse(idOperacao));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/devolucoes")
    @Operation(summary = "Confirmar recebimento de devolucao tecnica",
            description = "Somente 222/922 com reserva, data e equipe. Retorna material novo ou sucata do estoque CAMPO da equipe ao estoque de atendimento. "
                    + "Autoridade: estoque:solicitacao:return.")
    @ApiResponse(responseCode = "200", description = "Identificador da operacao de devolucao")
    @PreAuthorize("hasAuthority('estoque:solicitacao:return')")
    public ResponseEntity<OperacaoTecnicaResponse> devolver(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody ConfirmarDevolucaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        UUID idOperacao = operacoes.devolver(idSolicitacao, request, SecurityUtils.getUsuarioLogadoId(),
                OrigemComandoTecnico.UI, chaveIdempotencia);
        return ResponseEntity.ok(new OperacaoTecnicaResponse(idOperacao));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/estornos")
    @Operation(summary = "Estornar operacao tecnica",
            description = "Referencia entrega ou devolucao original, exige motivo e reverte a rota e a condicao registradas na operacao original. "
                    + "Autoridade: estoque:solicitacao:reverse.")
    @ApiResponse(responseCode = "200", description = "Identificador da operacao compensatoria")
    @PreAuthorize("hasAuthority('estoque:solicitacao:reverse')")
    public ResponseEntity<OperacaoTecnicaResponse> estornar(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody EstornarOperacaoTecnicaRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        UUID idOperacao = operacoes.estornar(idSolicitacao, request, SecurityUtils.getUsuarioLogadoId(),
                OrigemComandoTecnico.UI, chaveIdempotencia);
        return ResponseEntity.ok(new OperacaoTecnicaResponse(idOperacao));
    }

    @PostMapping("/tecnicas/{idSolicitacao}/encerramento")
    @Operation(summary = "Encerrar saldo pendente apos operacao parcial",
            description = "Encerra explicitamente o saldo tecnico ainda pendente e requer Idempotency-Key. "
                    + "Autoridade: estoque:solicitacao:close.")
    @ApiResponse(responseCode = "204", description = "Saldo pendente encerrado")
    @PreAuthorize("hasAuthority('estoque:solicitacao:close')")
    public ResponseEntity<Void> encerrar(
            @PathVariable UUID idSolicitacao,
            @Valid @RequestBody EncerrarSaldoTecnicoRequest request,
            @Parameter(description = "Chave idempotente de ate 200 caracteres", required = true)
            @RequestHeader("Idempotency-Key") String chaveIdempotencia
    ) {
        encerramentos.encerrar(
                idSolicitacao, request, SecurityUtils.getUsuarioLogadoId(), chaveIdempotencia);
        return ResponseEntity.noContent().build();
    }

}
