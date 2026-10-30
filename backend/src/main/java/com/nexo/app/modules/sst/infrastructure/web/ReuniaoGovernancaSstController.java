package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AtaGovernancaDto;
import com.nexo.app.modules.sst.application.dto.ReuniaoGovernancaDto;
import com.nexo.app.modules.sst.application.usecases.AtaGovernancaSstUseCases;
import com.nexo.app.modules.sst.application.usecases.ReuniaoGovernancaSstUseCases;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/reunioes")
@RequiredArgsConstructor
public class ReuniaoGovernancaSstController {
    private final ReuniaoGovernancaSstUseCases reunioes;
    private final AtaGovernancaSstUseCases atas;

    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.Response> agendar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReuniaoGovernancaDto.AgendarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reunioes.agendar(
                request, SecurityUtils.getUsuarioLogadoId(), false));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<ReuniaoGovernancaDto.Response>> listar(
            @ParameterObject ReuniaoGovernancaDto.Filter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(reunioes.listar(filtro, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<ReuniaoGovernancaDto.Response> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(reunioes.buscar(id));
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<ReuniaoGovernancaDto.CapabilitiesResponse> capabilities() {
        return ResponseEntity.ok(atas.obterCapabilities());
    }

    @PostMapping("/{id}/inicio")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.Response> iniciar(@PathVariable UUID id) {
        return ResponseEntity.ok(reunioes.iniciar(id));
    }

    @GetMapping("/{id}/participacoes")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<ReuniaoGovernancaDto.ParticipacaoResponse>> participacoes(@PathVariable UUID id) {
        return ResponseEntity.ok(reunioes.listarParticipacoes(id));
    }

    @PutMapping("/{id}/participacoes/{idParticipacao}/presenca")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.ParticipacaoResponse> presenca(
            @PathVariable UUID id, @PathVariable UUID idParticipacao,
            @Valid @RequestBody ReuniaoGovernancaDto.PresencaRequest request) {
        return ResponseEntity.ok(reunioes.registrarPresenca(
                id, idParticipacao, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/{id}/convidados")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.ConvidadoResponse> convidado(
            @PathVariable UUID id, @Valid @RequestBody ReuniaoGovernancaDto.ConvidadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reunioes.adicionarConvidado(id, request));
    }

    @GetMapping("/{id}/convidados")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<ReuniaoGovernancaDto.ConvidadoResponse>> convidados(@PathVariable UUID id) {
        return ResponseEntity.ok(reunioes.listarConvidados(id));
    }

    @PutMapping("/{id}/secretario")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.Response> secretario(
            @PathVariable UUID id, @Valid @RequestBody ReuniaoGovernancaDto.SecretarioRequest request) {
        return ResponseEntity.ok(reunioes.designarSecretario(id, request));
    }

    @PostMapping("/{id}/presencas/conclusao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.Response> concluirPresencas(@PathVariable UUID id) {
        return ResponseEntity.ok(reunioes.concluirPresencas(id));
    }

    @PostMapping("/{id}/transcricao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.TranscricaoResponse> transcricao(
            @PathVariable UUID id, @Valid @RequestBody ReuniaoGovernancaDto.TranscricaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reunioes.salvarTranscricao(id, request));
    }

    @GetMapping("/{id}/transcricoes")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<ReuniaoGovernancaDto.TranscricaoResponse>> transcricoes(@PathVariable UUID id) {
        return ResponseEntity.ok(reunioes.listarTranscricoes(id));
    }

    @PostMapping("/{id}/transcricoes/{idRevisao}/confirmacao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReuniaoGovernancaDto.TranscricaoResponse> confirmarTranscricao(
            @PathVariable UUID id, @PathVariable UUID idRevisao) {
        return ResponseEntity.ok(reunioes.confirmarTranscricao(
                id, idRevisao, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/{id}/atas/manual")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<AtaGovernancaDto.VersaoResponse> ataManual(
            @PathVariable UUID id, @Valid @RequestBody AtaGovernancaDto.VersaoManualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                atas.criarManual(id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/{id}/atas/geracao")
    @PreAuthorize("hasAuthority('sst:ata:generate')")
    public ResponseEntity<AtaGovernancaDto.TentativaResponse> gerarAta(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @PathVariable UUID id,
            @Valid @RequestBody AtaGovernancaDto.GerarRequest request) {
        return ResponseEntity.accepted().body(atas.gerar(id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}/atas")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<AtaGovernancaDto.VersaoResponse>> atas(@PathVariable UUID id) {
        return ResponseEntity.ok(atas.listarVersoes(id));
    }

    @PostMapping("/{id}/atas/{idVersao}/aprovacao")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<AtaGovernancaDto.VersaoResponse> aprovarAta(
            @PathVariable UUID id, @PathVariable UUID idVersao) {
        return ResponseEntity.ok(atas.aprovar(id, idVersao, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping(value = "/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<AtaGovernancaDto.EvidenciaFotoResponse> foto(
            @PathVariable UUID id, @RequestPart MultipartFile arquivo,
            @RequestParam(required = false) String legenda) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                atas.anexarFoto(id, arquivo, legenda, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}/evidencias")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<AtaGovernancaDto.EvidenciaFotoResponse>> fotos(@PathVariable UUID id) {
        return ResponseEntity.ok(atas.listarFotos(id));
    }

    @GetMapping("/{id}/evidencias/{idFoto}/arquivo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<InputStreamResource> baixarFoto(
            @PathVariable UUID id, @PathVariable UUID idFoto) {
        return download(atas.baixarFoto(id, idFoto));
    }

    @DeleteMapping("/{id}/evidencias/{idFoto}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<Void> removerFoto(@PathVariable UUID id, @PathVariable UUID idFoto) {
        atas.removerFoto(id, idFoto, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/atas/{idVersao}/copia-assinada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<AtaGovernancaDto.ArquivoResponse> copiaAssinada(
            @PathVariable UUID id, @PathVariable UUID idVersao, @RequestPart MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                atas.anexarCopiaAssinada(id, idVersao, arquivo, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}/atas/{idVersao}/copia-assinada/arquivo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<InputStreamResource> baixarCopia(
            @PathVariable UUID id, @PathVariable UUID idVersao) {
        return download(atas.baixarCopiaAssinada(id, idVersao));
    }

    @PostMapping("/{id}/atas/{idVersao}/docx")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<AtaGovernancaDto.ArquivoResponse> gerarDocx(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID id, @PathVariable UUID idVersao) {
        return ResponseEntity.ok(atas.gerarDocx(id, idVersao, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}/atas/docx/{idArquivo}/arquivo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<InputStreamResource> baixarDocx(
            @PathVariable UUID id, @PathVariable UUID idArquivo) {
        return download(atas.baixarDocx(id, idArquivo));
    }

    @PostMapping("/{id}/finalizacao")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<Void> finalizar(@PathVariable UUID id) {
        atas.finalizar(id, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reabertura")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<Void> reabrir(
            @PathVariable UUID id, @Valid @RequestBody ReuniaoGovernancaDto.MotivoRequest request) {
        atas.reabrir(id, request.motivo(), SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<InputStreamResource> download(AtaGovernancaDto.Download arquivo) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(arquivo.contentType()))
                .contentLength(arquivo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(arquivo.conteudo()));
    }
}
