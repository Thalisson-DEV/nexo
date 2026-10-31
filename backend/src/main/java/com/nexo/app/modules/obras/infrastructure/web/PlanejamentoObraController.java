package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraResponse;
import com.nexo.app.modules.obras.application.usecases.AnalisePlanejamentoUseCases;
import com.nexo.app.modules.obras.application.usecases.PlanejamentoObraUseCases;
import com.nexo.app.modules.obras.doc.PlanejamentoObraControllerDoc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/obras/{idObra}/planejamento")
@RequiredArgsConstructor
public class PlanejamentoObraController implements PlanejamentoObraControllerDoc {

    private static final String LER = "hasAuthority('obras:planejamento:read')";
    private static final String GRAVAR = "hasAuthority('obras:planejamento:write')";

    private final PlanejamentoObraUseCases useCases;
    private final AnalisePlanejamentoUseCases analises;

    @Override
    @GetMapping
    @PreAuthorize(LER)
    public ResponseEntity<PlanejamentoObraResponse> buscar(@PathVariable UUID idObra) {
        return ResponseEntity.ok(useCases.buscar(idObra));
    }

    @Override
    @PutMapping("/projeto-mestre")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> definirProjetoMestre(
            @PathVariable UUID idObra, @Valid @RequestBody PlanejamentoObraRequests.DefinirProjetoMestre request) {
        return ResponseEntity.ok(useCases.definirProjetoMestre(idObra, request.idAnexo()));
    }

    @Override
    @PostMapping(value = "/projeto-mestre", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> enviarProjetoMestre(
            @PathVariable UUID idObra, @RequestParam UUID idTipoAnexo, @RequestPart MultipartFile arquivo) {
        return ResponseEntity.ok(useCases.enviarProjetoMestre(idObra, idTipoAnexo, arquivo));
    }

    @Override
    @PutMapping("/paginas/{pagina}/rotacao")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> girarPagina(
            @PathVariable UUID idObra, @PathVariable int pagina, @Valid @RequestBody PlanejamentoObraRequests.GirarPagina request) {
        return ResponseEntity.ok(useCases.girarPagina(idObra, pagina, request.graus()));
    }

    @Override
    @PostMapping("/pontos")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> adicionarPonto(
            @PathVariable UUID idObra, @Valid @RequestBody PlanejamentoObraRequests.NovoPonto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.adicionarPonto(idObra, request));
    }

    @Override
    @PutMapping("/pontos/{idPonto}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> editarPonto(
            @PathVariable UUID idObra, @PathVariable UUID idPonto, @Valid @RequestBody PlanejamentoObraRequests.EditarPonto request) {
        return ResponseEntity.ok(useCases.editarPonto(idObra, idPonto, request));
    }

    @Override
    @PutMapping("/pontos/{idPonto}/posicao")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse.Ponto> moverPonto(
            @PathVariable UUID idObra, @PathVariable UUID idPonto, @Valid @RequestBody PlanejamentoObraRequests.MoverPonto request) {
        return ResponseEntity.ok(useCases.moverPonto(idObra, idPonto, request));
    }

    @Override
    @DeleteMapping("/pontos/{idPonto}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> excluirPonto(@PathVariable UUID idObra, @PathVariable UUID idPonto) {
        return ResponseEntity.ok(useCases.excluirPonto(idObra, idPonto));
    }

    @Override
    @PostMapping("/vaos")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> adicionarVao(
            @PathVariable UUID idObra, @Valid @RequestBody PlanejamentoObraRequests.NovoVao request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.adicionarVao(idObra, request));
    }

    @Override
    @PutMapping("/vaos/{idVao}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> editarVao(
            @PathVariable UUID idObra, @PathVariable UUID idVao, @Valid @RequestBody PlanejamentoObraRequests.EditarVao request) {
        return ResponseEntity.ok(useCases.editarVao(idObra, idVao, request.metragem(), request.versao()));
    }

    @Override
    @DeleteMapping("/vaos/{idVao}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> excluirVao(@PathVariable UUID idObra, @PathVariable UUID idVao) {
        return ResponseEntity.ok(useCases.excluirVao(idObra, idVao));
    }

    @Override
    @PostMapping("/kits")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraRequests.ResultadoAplicacaoKit> aplicarKit(
            @PathVariable UUID idObra, @Valid @RequestBody PlanejamentoObraRequests.AplicarKit request) {
        return ResponseEntity.ok(useCases.aplicarKit(idObra, request));
    }

    @Override
    @PutMapping("/kits/{idKitAplicado}/quantidade")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> alterarMultiplicador(
            @PathVariable UUID idObra, @PathVariable UUID idKitAplicado, @Valid @RequestBody PlanejamentoObraRequests.AlterarMultiplicador request) {
        return ResponseEntity.ok(useCases.alterarMultiplicador(idObra, idKitAplicado, request.multiplicador(), request.versao()));
    }

    @Override
    @PutMapping("/kits/{idKitAplicado}/executado-antes")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> marcarExecutadoAntes(
            @PathVariable UUID idObra, @PathVariable UUID idKitAplicado, @RequestBody PlanejamentoObraRequests.MarcarExecutadoAntes request) {
        return ResponseEntity.ok(useCases.marcarExecutadoAntes(idObra, idKitAplicado, request.executado(), request.versao()));
    }

    @Override
    @DeleteMapping("/kits/{idKitAplicado}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> removerKit(@PathVariable UUID idObra, @PathVariable UUID idKitAplicado) {
        return ResponseEntity.ok(useCases.removerKit(idObra, idKitAplicado));
    }

    @Override
    @PutMapping("/kits/{idKitAplicado}/itens/{idItem}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> alterarItem(
            @PathVariable UUID idObra, @PathVariable UUID idKitAplicado, @PathVariable UUID idItem,
            @Valid @RequestBody PlanejamentoObraRequests.AlterarItem request) {
        return ResponseEntity.ok(useCases.alterarItem(idObra, idKitAplicado, idItem, request.quantidade(), request.versao()));
    }

    @Override
    @DeleteMapping("/kits/{idKitAplicado}/itens/{idItem}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> removerItem(
            @PathVariable UUID idObra, @PathVariable UUID idKitAplicado, @PathVariable UUID idItem) {
        return ResponseEntity.ok(useCases.removerItem(idObra, idKitAplicado, idItem));
    }

    @Override
    @PostMapping("/anotacoes")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> adicionarAnotacao(
            @PathVariable UUID idObra, @Valid @RequestBody PlanejamentoObraRequests.NovaAnotacao request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.adicionarAnotacao(idObra, request));
    }

    @Override
    @PutMapping("/anotacoes/{idAnotacao}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> editarAnotacao(
            @PathVariable UUID idObra, @PathVariable UUID idAnotacao, @Valid @RequestBody PlanejamentoObraRequests.EditarAnotacao request) {
        return ResponseEntity.ok(useCases.editarAnotacao(idObra, idAnotacao, request.texto(), request.versao()));
    }

    @Override
    @DeleteMapping("/anotacoes/{idAnotacao}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> excluirAnotacao(@PathVariable UUID idObra, @PathVariable UUID idAnotacao) {
        return ResponseEntity.ok(useCases.excluirAnotacao(idObra, idAnotacao));
    }

    @Override
    @GetMapping("/analises/disponibilidade")
    @PreAuthorize(LER)
    public ResponseEntity<PlanejamentoObraRequests.DisponibilidadeAnalise> disponibilidadeAnalise(
            @PathVariable UUID idObra, @RequestParam int pagina) {
        return ResponseEntity.ok(analises.disponibilidade(idObra, pagina));
    }

    @Override
    @PostMapping("/analises")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraRequests.AnaliseResponse> iniciarAnalise(
            @PathVariable UUID idObra, @Valid @RequestBody PlanejamentoObraRequests.IniciarAnalise request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(analises.iniciar(idObra, request.pagina()));
    }

    @Override
    @GetMapping("/analises/{idAnalise}")
    @PreAuthorize(LER)
    public ResponseEntity<PlanejamentoObraRequests.AnaliseResponse> buscarAnalise(
            @PathVariable UUID idObra, @PathVariable UUID idAnalise) {
        return ResponseEntity.ok(analises.buscar(idObra, idAnalise));
    }

    @Override
    @PostMapping("/analises/{idAnalise}/aceite")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<PlanejamentoObraResponse> aceitarAnalise(
            @PathVariable UUID idObra, @PathVariable UUID idAnalise, @Valid @RequestBody PlanejamentoObraRequests.AceitarProposta request) {
        return ResponseEntity.ok(analises.aceitar(idObra, idAnalise, request));
    }
}
