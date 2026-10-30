package com.nexo.app.modules.execucao.doc;

import com.nexo.app.modules.execucao.application.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Turnos", description = "Gestão de turnos de trabalho das equipes de campo")
public interface TurnoControllerDoc {

    @Operation(
            summary = "Abrir um novo turno para a equipe",
            description = "Opcionalmente, o responsável da equipe confirma a composição no mesmo passo: "
                    + "idVeiculo troca o veículo e idsColaboradores define a lista completa de parceiros. "
                    + "Campos nulos mantêm a composição cadastrada."
    )
    ResponseEntity<TurnoResponse> abrir(AbrirTurnoRequest request);

    @Operation(
            summary = "Composição da equipe do usuário logado",
            description = "Veículo, membros e meta de faturamento mensal da equipe, com as opções de "
                    + "colaboradores (base operacional da equipe) e veículos ativos para a conferência na abertura do turno."
    )
    ResponseEntity<ComposicaoTurnoResponse> buscarComposicaoMinhaEquipe();

    @Operation(summary = "Fechar o turno atual")
    ResponseEntity<Void> fechar(UUID id);

    @Operation(
            summary = "Buscar um turno",
            description = "Inclui o informativo de encerramento automático, quando a equipe esqueceu o turno aberto."
    )
    ResponseEntity<TurnoResponse> buscarPorId(UUID id);

    @Operation(summary = "Buscar o turno aberto de uma equipe")
    ResponseEntity<TurnoResponse> buscarAtivoPorEquipe(UUID idEquipe);
}
