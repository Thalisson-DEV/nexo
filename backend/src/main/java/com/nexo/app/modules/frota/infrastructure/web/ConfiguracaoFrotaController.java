package com.nexo.app.modules.frota.infrastructure.web;

import com.nexo.app.modules.frota.application.dto.CatalogoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.OpcoesFrotaResponse;
import com.nexo.app.modules.frota.application.dto.ParametrosFrotaRequest;
import com.nexo.app.modules.frota.application.dto.ParametrosFrotaResponse;
import com.nexo.app.modules.frota.application.dto.TipoImplementoRequest;
import com.nexo.app.modules.frota.application.dto.TipoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoRequest;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoResponse;
import com.nexo.app.modules.frota.application.usecases.OpcoesFrotaUseCases;
import com.nexo.app.modules.frota.application.usecases.ParametrosFrotaUseCases;
import com.nexo.app.modules.frota.application.usecases.TipoImplementoUseCases;
import com.nexo.app.modules.frota.application.usecases.TipoVeiculoUseCases;
import com.nexo.app.modules.frota.doc.ConfiguracaoFrotaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/frota")
@RequiredArgsConstructor
public class ConfiguracaoFrotaController implements ConfiguracaoFrotaControllerDoc {

    private static final String LEITURA = "hasAnyAuthority('frota:veiculo:read', 'frota:condutor:read', 'frota:documento:read')";

    private final OpcoesFrotaUseCases opcoes;
    private final ParametrosFrotaUseCases parametros;
    private final TipoVeiculoUseCases tiposVeiculo;
    private final TipoImplementoUseCases tiposImplemento;

    @Override
    @GetMapping("/opcoes")
    @PreAuthorize(LEITURA)
    public ResponseEntity<OpcoesFrotaResponse> opcoes() {
        return ResponseEntity.ok(opcoes.listar());
    }

    @Override
    @GetMapping("/parametros")
    @PreAuthorize(LEITURA)
    public ResponseEntity<ParametrosFrotaResponse> parametros() {
        return ResponseEntity.ok(parametros.consultar());
    }

    @Override
    @PutMapping("/parametros")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<ParametrosFrotaResponse> atualizarParametros(@Valid @RequestBody ParametrosFrotaRequest request) {
        return ResponseEntity.ok(parametros.atualizar(request));
    }

    @Override
    @GetMapping("/tipos-veiculo")
    @PreAuthorize(LEITURA)
    public ResponseEntity<PageResponse<TipoVeiculoResponse>> listarTiposVeiculo(@ParameterObject CatalogoFrotaFilter filtro,
                                                                               @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(tiposVeiculo.listar(filtro, pageable));
    }

    @Override
    @PostMapping("/tipos-veiculo")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<TipoVeiculoResponse> criarTipoVeiculo(@Valid @RequestBody TipoVeiculoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tiposVeiculo.criar(request));
    }

    @Override
    @PutMapping("/tipos-veiculo/{id}")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<TipoVeiculoResponse> atualizarTipoVeiculo(@PathVariable UUID id,
                                                                   @Valid @RequestBody TipoVeiculoRequest request) {
        return ResponseEntity.ok(tiposVeiculo.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/tipos-veiculo/{id}")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<Void> desativarTipoVeiculo(@PathVariable UUID id) {
        tiposVeiculo.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/tipos-veiculo/{id}/ativar")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<Void> ativarTipoVeiculo(@PathVariable UUID id) {
        tiposVeiculo.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/tipos-implemento")
    @PreAuthorize(LEITURA)
    public ResponseEntity<PageResponse<TipoImplementoResponse>> listarTiposImplemento(@ParameterObject CatalogoFrotaFilter filtro,
                                                                                     @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(tiposImplemento.listar(filtro, pageable));
    }

    @Override
    @PostMapping("/tipos-implemento")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<TipoImplementoResponse> criarTipoImplemento(@Valid @RequestBody TipoImplementoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tiposImplemento.criar(request));
    }

    @Override
    @PutMapping("/tipos-implemento/{id}")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<TipoImplementoResponse> atualizarTipoImplemento(@PathVariable UUID id,
                                                                         @Valid @RequestBody TipoImplementoRequest request) {
        return ResponseEntity.ok(tiposImplemento.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/tipos-implemento/{id}")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<Void> desativarTipoImplemento(@PathVariable UUID id) {
        tiposImplemento.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/tipos-implemento/{id}/ativar")
    @PreAuthorize("hasAuthority('frota:configuracao:write')")
    public ResponseEntity<Void> ativarTipoImplemento(@PathVariable UUID id) {
        tiposImplemento.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
