package com.nexo.app.modules.frota.infrastructure.web;

import com.nexo.app.modules.frota.application.dto.AcoplamentoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.FichaVeiculoFilter;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroFilter;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroResponse;
import com.nexo.app.modules.frota.application.dto.RegistrarLeituraRequest;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaDetalheResponse;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaRequest;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaResumoResponse;
import com.nexo.app.modules.frota.application.dto.VincularCondutorRequest;
import com.nexo.app.modules.frota.application.dto.VinculoCondutorResponse;
import com.nexo.app.modules.frota.application.usecases.ImplementoUseCases;
import com.nexo.app.modules.frota.application.usecases.OdometroUseCases;
import com.nexo.app.modules.frota.application.usecases.VeiculoFrotaUseCases;
import com.nexo.app.modules.frota.application.usecases.VinculoCondutorUseCases;
import com.nexo.app.modules.frota.doc.VeiculoFrotaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/frota/veiculos")
@RequiredArgsConstructor
public class VeiculoFrotaController implements VeiculoFrotaControllerDoc {

    private final VeiculoFrotaUseCases veiculos;
    private final VinculoCondutorUseCases vinculos;
    private final ImplementoUseCases implementos;
    private final OdometroUseCases odometro;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<PageResponse<VeiculoFrotaResumoResponse>> listar(@ParameterObject FichaVeiculoFilter filtro,
                                                                           @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(veiculos.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{idVeiculo}")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<VeiculoFrotaDetalheResponse> detalhar(@PathVariable UUID idVeiculo) {
        return ResponseEntity.ok(veiculos.detalhar(idVeiculo));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<VeiculoFrotaDetalheResponse> criar(@Valid @RequestBody VeiculoFrotaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(veiculos.criar(request));
    }

    @Override
    @PutMapping("/{idVeiculo}")
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<VeiculoFrotaDetalheResponse> atualizar(@PathVariable UUID idVeiculo,
                                                                 @Valid @RequestBody VeiculoFrotaRequest request) {
        return ResponseEntity.ok(veiculos.atualizar(idVeiculo, request));
    }

    @Override
    @GetMapping("/{idVeiculo}/condutores")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<PageResponse<VinculoCondutorResponse>> historicoCondutores(@PathVariable UUID idVeiculo,
                                                                                    @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(vinculos.historicoDoVeiculo(idVeiculo, pageable));
    }

    @Override
    @PostMapping("/{idVeiculo}/condutores")
    @PreAuthorize("hasAuthority('frota:condutor:write')")
    public ResponseEntity<VinculoCondutorResponse> vincularCondutor(@PathVariable UUID idVeiculo,
                                                                    @Valid @RequestBody VincularCondutorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vinculos.vincular(idVeiculo, request));
    }

    @Override
    @GetMapping("/{idVeiculo}/condutor-em")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<VinculoCondutorResponse> condutorEm(@PathVariable UUID idVeiculo,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime instante) {
        return ResponseEntity.of(vinculos.vinculoEm(idVeiculo, instante));
    }

    @Override
    @GetMapping("/{idVeiculo}/implementos")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<List<AcoplamentoImplementoResponse>> historicoImplementos(@PathVariable UUID idVeiculo) {
        return ResponseEntity.ok(implementos.historicoDoVeiculo(idVeiculo));
    }

    @Override
    @GetMapping("/{idVeiculo}/odometro")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<PageResponse<LeituraOdometroResponse>> leituras(@PathVariable UUID idVeiculo,
                                                                         @ParameterObject Pageable pageable) {
        var filtro = new LeituraOdometroFilter(idVeiculo, null, null, null, null, null);
        return ResponseEntity.ok(odometro.listar(filtro, pageable));
    }

    @Override
    @PostMapping("/{idVeiculo}/odometro")
    @PreAuthorize("hasAuthority('frota:odometro:write')")
    public ResponseEntity<LeituraOdometroResponse> registrarLeitura(@PathVariable UUID idVeiculo,
                                                                    @Valid @RequestBody RegistrarLeituraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(odometro.registrarManual(idVeiculo, request));
    }
}
