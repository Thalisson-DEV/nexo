package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.ReferenciaGovernancaSstDto;
import com.nexo.app.modules.sst.application.usecases.ReferenciasGovernancaSstUseCases;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca")
@RequiredArgsConstructor
public class ReferenciasGovernancaSstController {
    private final ReferenciasGovernancaSstUseCases useCases;

    @GetMapping("/processos-treinamento") @PreAuthorize("hasAuthority('sst:governanca:read')")
    public List<ReferenciaGovernancaSstDto.ProcessoResponse> processos(){return useCases.listarProcessos();}
    @PostMapping("/processos-treinamento") @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReferenciaGovernancaSstDto.ProcessoResponse> processo(
            @Valid @RequestBody ReferenciaGovernancaSstDto.ProcessoRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criarProcesso(r));}
    @PostMapping("/processos-treinamento/{id}/confirmacao") @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ReferenciaGovernancaSstDto.ProcessoResponse confirmarProcesso(@PathVariable UUID id,@RequestParam LocalDate vigenteDesde){return useCases.confirmarProcesso(id,vigenteDesde,SecurityUtils.getUsuarioLogadoId());}

    @GetMapping("/instituicoes-treinamento") @PreAuthorize("hasAuthority('sst:governanca:read')")
    public List<ReferenciaGovernancaSstDto.InstituicaoResponse> instituicoes(){return useCases.listarInstituicoes();}
    @PostMapping("/instituicoes-treinamento") @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReferenciaGovernancaSstDto.InstituicaoResponse> instituicao(
            @Valid @RequestBody ReferenciaGovernancaSstDto.InstituicaoRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criarInstituicao(r));}
    @PostMapping("/instituicoes-treinamento/{idInstituicao}/homologacoes/{id}/confirmacao")
    @PreAuthorize("hasAuthority('sst:treinamento:homologar')")
    public ReferenciaGovernancaSstDto.HomologacaoResponse homologar(@PathVariable UUID idInstituicao,@PathVariable UUID id,
            @Valid @RequestBody ReferenciaGovernancaSstDto.ConfirmarHomologacaoRequest r){return useCases.confirmarHomologacao(idInstituicao,id,r,SecurityUtils.getUsuarioLogadoId());}

    @GetMapping("/ativos-inspecionaveis") @PreAuthorize("hasAuthority('sst:governanca:read')")
    public List<ReferenciaGovernancaSstDto.AtivoResponse> ativos(@RequestParam UUID idBase){return useCases.listarAtivos(idBase);}
    @PostMapping("/ativos-inspecionaveis") @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ReferenciaGovernancaSstDto.AtivoResponse> ativo(
            @Valid @RequestBody ReferenciaGovernancaSstDto.AtivoRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criarAtivo(r));}
    @PostMapping("/ativos-inspecionaveis/{id}/confirmacao") @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ReferenciaGovernancaSstDto.AtivoResponse confirmarAtivo(@PathVariable UUID id){return useCases.confirmarAtivo(id);}

    @PostMapping("/importacoes/matriz-treinamentos") @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ReferenciaGovernancaSstDto.ImportacaoResponse importarMatriz(){return useCases.importarMatriz();}
    @PostMapping("/importacoes/escolas") @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ReferenciaGovernancaSstDto.ImportacaoResponse importarEscolas(){return useCases.importarEscolas();}
    @PostMapping("/importacoes/ativos-inspecionaveis") @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ReferenciaGovernancaSstDto.ImportacaoResponse importarAtivos(@RequestParam UUID idBase){return useCases.importarAtivos(idBase);}
}
