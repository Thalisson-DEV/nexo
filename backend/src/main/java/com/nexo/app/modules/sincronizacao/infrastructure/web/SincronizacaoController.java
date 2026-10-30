package com.nexo.app.modules.sincronizacao.infrastructure.web;

import com.nexo.app.modules.sincronizacao.application.dto.SincronizacaoDtos.*;
import com.nexo.app.modules.sincronizacao.application.usecases.SincronizacaoUseCases;
import com.nexo.app.modules.sincronizacao.domain.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@RestController @RequiredArgsConstructor
public class SincronizacaoController {
 private final SincronizacaoUseCases useCases;
 @PostMapping("/v1/sincronizacao/operacoes")
 @PreAuthorize("isAuthenticated()")
 public ResponseEntity<OperacaoResponse> receber(@Valid @RequestBody OperacaoRequest request){
  var r=useCases.receber(request,SecurityUtils.getUsuarioLogadoId());
  return ResponseEntity.status(r.backofficeInformado()?HttpStatus.CREATED:HttpStatus.ACCEPTED).body(r);
 }
 @GetMapping("/v1/sincronizacao/operacoes/{idOperacaoLocal}") @PreAuthorize("isAuthenticated()")
 public OperacaoResponse consultar(@PathVariable UUID idOperacaoLocal){return useCases.consultar(idOperacaoLocal,SecurityUtils.getUsuarioLogadoId());}
 @GetMapping("/v1/sincronizacao/casos") @PreAuthorize("hasAuthority('sincronizacao:read')")
 public PageResponse<CasoResponse> listar(@ParameterObject Pageable pageable,@RequestParam(required=false) StatusCasoSincronizacao status,@RequestParam(required=false) TipoOperacaoSincronizacao tipo,@RequestParam(required=false) String busca,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate receivedFrom,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate receivedTo){return useCases.listar(pageable,status,tipo,busca,receivedFrom,receivedTo);}
 @GetMapping("/v1/sincronizacao/casos/{id}") @PreAuthorize("hasAuthority('sincronizacao:read')")
 public CasoResponse detalhar(@PathVariable UUID id){return useCases.detalhar(id);}
 @GetMapping("/v1/sincronizacao/casos/{id}/anexos/{idAnexo}") @PreAuthorize("hasAuthority('sincronizacao:read')")
 public ResponseEntity<byte[]> baixarAnexo(@PathVariable UUID id,@PathVariable UUID idAnexo){var a=useCases.baixarAnexo(id,idAnexo);return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.getContentType())).contentLength(a.getTamanhoBytes()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+a.getNome().replace("\"","")+"\"").body(a.getConteudo());}
 @PostMapping("/v1/sincronizacao/casos/{id}/atribuicao") @PreAuthorize("hasAuthority('sincronizacao:manage')")
 public CasoResponse atribuir(@PathVariable UUID id,@Valid @RequestBody AtribuicaoRequest r){return useCases.atribuir(id,r.idResponsavel());}
 @PostMapping("/v1/sincronizacao/casos/{id}/correcoes") @PreAuthorize("hasAuthority('sincronizacao:manage')")
 public CasoResponse corrigir(@PathVariable UUID id,@Valid @RequestBody CorrecaoRequest r){return useCases.corrigir(id,r,SecurityUtils.getUsuarioLogadoId());}
 @PostMapping("/v1/sincronizacao/casos/{id}/reprocessamento") @PreAuthorize("hasAuthority('sincronizacao:manage')")
 public CasoResponse reprocessar(@PathVariable UUID id,@Valid @RequestBody ReprocessamentoRequest r){return useCases.reprocessar(id,r);}
 @PostMapping("/v1/sincronizacao/casos/{id}/descarte") @PreAuthorize("hasAuthority('sincronizacao:manage')")
 public CasoResponse descartar(@PathVariable UUID id,@Valid @RequestBody DescarteRequest r){return useCases.descartar(id,r);}
}
