package com.nexo.app.modules.frota.doc;

import com.nexo.app.modules.frota.application.dto.CondutorFilter;
import com.nexo.app.modules.frota.application.dto.CondutorRequest;
import com.nexo.app.modules.frota.application.dto.CondutorResponse;
import com.nexo.app.modules.frota.application.dto.EncerrarPeriodoRequest;
import com.nexo.app.modules.frota.application.dto.VinculoCondutorResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Frota — Condutores", description = "CNH dos colaboradores que dirigem e histórico de veículos por período")
public interface CondutorFrotaControllerDoc {

    @Operation(summary = "Listar condutores", description = "Busca por nome ou pelo número completo da CNH; filtra por situação da CNH.")
    ResponseEntity<PageResponse<CondutorResponse>> listar(CondutorFilter filtro, Pageable pageable);

    @Operation(summary = "Buscar condutor pelo colaborador")
    ResponseEntity<CondutorResponse> buscar(UUID idColaborador);

    @Operation(summary = "Registrar ou atualizar a CNH", description = "Cria a ficha de condutor se ainda não existir.")
    ResponseEntity<CondutorResponse> registrarCnh(UUID idColaborador, CondutorRequest request);

    @Operation(summary = "Desativar condutor", description = "Exige que não haja vínculo aberto com veículo.")
    ResponseEntity<Void> desativar(UUID idColaborador);

    @Operation(summary = "Histórico de veículos do condutor")
    ResponseEntity<PageResponse<VinculoCondutorResponse>> historico(UUID idColaborador, Pageable pageable);

    @Operation(summary = "Encerrar vínculo condutor–veículo")
    ResponseEntity<VinculoCondutorResponse> encerrarVinculo(UUID idVinculo, EncerrarPeriodoRequest request);
}
