package com.nexo.app.modules.frota.doc;

import com.nexo.app.modules.frota.application.dto.CatalogoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.OpcoesFrotaResponse;
import com.nexo.app.modules.frota.application.dto.ParametrosFrotaRequest;
import com.nexo.app.modules.frota.application.dto.ParametrosFrotaResponse;
import com.nexo.app.modules.frota.application.dto.TipoImplementoRequest;
import com.nexo.app.modules.frota.application.dto.TipoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoRequest;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Frota — Configuração", description = "Tipos de veículo e implemento, parâmetros e listas fechadas")
public interface ConfiguracaoFrotaControllerDoc {

    @Operation(summary = "Listas fechadas (situações, regimes, combustíveis...) com rótulos")
    ResponseEntity<OpcoesFrotaResponse> opcoes();

    @Operation(summary = "Consultar parâmetros da frota")
    ResponseEntity<ParametrosFrotaResponse> parametros();

    @Operation(summary = "Atualizar parâmetros", description = "Faixas de aviso de vencimento e limite de km por dia.")
    ResponseEntity<ParametrosFrotaResponse> atualizarParametros(ParametrosFrotaRequest request);

    @Operation(summary = "Listar tipos de veículo")
    ResponseEntity<PageResponse<TipoVeiculoResponse>> listarTiposVeiculo(CatalogoFrotaFilter filtro, Pageable pageable);

    @Operation(summary = "Criar tipo de veículo")
    ResponseEntity<TipoVeiculoResponse> criarTipoVeiculo(TipoVeiculoRequest request);

    @Operation(summary = "Atualizar tipo de veículo")
    ResponseEntity<TipoVeiculoResponse> atualizarTipoVeiculo(UUID id, TipoVeiculoRequest request);

    @Operation(summary = "Desativar tipo de veículo")
    ResponseEntity<Void> desativarTipoVeiculo(UUID id);

    @Operation(summary = "Reativar tipo de veículo")
    ResponseEntity<Void> ativarTipoVeiculo(UUID id);

    @Operation(summary = "Listar tipos de implemento")
    ResponseEntity<PageResponse<TipoImplementoResponse>> listarTiposImplemento(CatalogoFrotaFilter filtro, Pageable pageable);

    @Operation(summary = "Criar tipo de implemento")
    ResponseEntity<TipoImplementoResponse> criarTipoImplemento(TipoImplementoRequest request);

    @Operation(summary = "Atualizar tipo de implemento")
    ResponseEntity<TipoImplementoResponse> atualizarTipoImplemento(UUID id, TipoImplementoRequest request);

    @Operation(summary = "Desativar tipo de implemento")
    ResponseEntity<Void> desativarTipoImplemento(UUID id);

    @Operation(summary = "Reativar tipo de implemento")
    ResponseEntity<Void> ativarTipoImplemento(UUID id);
}
