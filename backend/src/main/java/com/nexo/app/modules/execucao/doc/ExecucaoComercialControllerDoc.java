package com.nexo.app.modules.execucao.doc;

import com.nexo.app.modules.execucao.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Execuções Comerciais", description = "Gestão de execuções de campo e atividades de equipes")
@SecurityRequirement(name = "bearerAuth")
public interface ExecucaoComercialControllerDoc {

    @Operation(
            summary = "Lançar uma execução de campo completa",
            description = "Cria uma execução de campo em uma única requisição, incluindo metadados obrigatórios e opcionais, ações realizadas e materiais aplicados/retirados. A execução é persistida já no status CONCLUIDO e o valor faturado é calculado automaticamente. Exige um turno ABERTO para a equipe. tipoNota diz se é nota COMUM (padrão), AVULSA (número provisório gerado pelo app) ou OCORRENCIA; na ocorrência, material com origem CONCESSIONARIA fica só como registro e não movimenta o saldo da equipe."
    )
    @ApiResponse(responseCode = "201", description = "Execução lançada com sucesso")
    ResponseEntity<ExecucaoComercialResponse> registrar(RegistrarExecucaoRequest request);

    @Operation(
            summary = "Informar o número oficial de uma nota avulsa",
            description = "Substitui o número provisório gerado pelo app pelo número oficial da nota. O provisório continua guardado em referenciaProvisoria. Pode ser repetido para corrigir o número. Só vale para notas do tipo AVULSA."
    )
    @ApiResponse(responseCode = "200", description = "Número oficial registrado")
    ResponseEntity<ExecucaoComercialResponse> informarNumeroOficial(UUID id, InformarNumeroOficialRequest request);

    @Operation(
            summary = "Alterar serviços e materiais de uma nota já sincronizada",
            description = "Recebe a lista completa de ações e materiais como a nota deve ficar e o motivo (obrigatório). O servidor grava só a diferença na linha do tempo, recalcula o valor pela tabela vigente na data do lançamento e ajusta o saldo da equipe: material que saiu é estornado, material que entrou é baixado. A equipe da nota altera pelo app até 7 dias depois do lançamento; usuários sem equipe (escritório) alteram a qualquer tempo. idOperacao torna o reenvio idempotente e alteradoEm guarda quando a alteração foi feita no aparelho (offline)."
    )
    @ApiResponse(responseCode = "200", description = "Nota alterada")
    @ApiResponse(responseCode = "403", description = "Usuário de campo de outra equipe")
    @ApiResponse(responseCode = "422", description = "Prazo do campo vencido, motivo ausente ou nada mudou")
    ResponseEntity<ExecucaoComercialResponse> alterar(UUID id, AlterarExecucaoRequest request);

    @Operation(
            summary = "Linha do tempo da nota",
            description = "Lançamento, alterações e número oficial, do mais antigo ao mais novo, com autor, origem (CAMPO ou ESCRITORIO), motivo, valor antes e depois e o que entrou e saiu. Notas lançadas antes do histórico trazem um lançamento reconstruído (reconstruido = true)."
    )
    ResponseEntity<List<EventoHistoricoNotaResponse>> historico(UUID id);

    @Operation(summary = "Buscar uma execução por ID")
    ResponseEntity<ExecucaoComercialResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar execuções com filtros dinâmicos")
    ResponseEntity<PageResponse<ExecucaoComercialResponse>> listar(@ParameterObject ExecucaoFilter filter, Pageable pageable);

    @Operation(summary = "Listar execuções da equipe vinculada ao usuário logado")
    ResponseEntity<PageResponse<ExecucaoComercialResponse>> listarPorMinhaEquipe(@ParameterObject ExecucaoFilter filter, Pageable pageable);
}
