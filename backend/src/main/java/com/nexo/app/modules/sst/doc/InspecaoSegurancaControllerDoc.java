package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.application.dto.EvidenciaInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaFilter;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaResponse;
import com.nexo.app.modules.sst.application.dto.RegistrarInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TratarDesvioRequest;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "SST - Inspeções de Segurança", description = "Lançamento e acompanhamento de inspeções de segurança e seus desvios")
@SecurityRequirement(name = "bearerAuth")
public interface InspecaoSegurancaControllerDoc {

    @Operation(
            summary = "Lançar uma inspeção de segurança",
            description = "Registra uma inspeção completa em uma única requisição: vínculos (processo, veículo, equipe), "
                    + "colaboradores inspecionados, respostas e desvios. O inspetor é o usuário logado e a inspeção é "
                    + "persistida já CONCLUÍDA. O campo `evidencias` é obrigatório e deve conter ao menos um ID devolvido "
                    + "por `POST /v1/sst/inspecoes/anexos`: sem fotografia a inspeção não é aceita.")
    @ApiResponse(responseCode = "201", description = "Inspeção lançada com sucesso")
    @ApiResponse(responseCode = "400", description = "`evidencias` vazio ou ausente")
    @ApiResponse(responseCode = "404", description = "Evidência informada não existe (inclusive por já ter sido expurgada)")
    @ApiResponse(responseCode = "422", description = "Evidência enviada por outro usuário ou já usada em outra inspeção")
    ResponseEntity<InspecaoSegurancaResponse> registrar(RegistrarInspecaoRequest request);

    @Operation(
            summary = "Enviar uma evidência fotográfica (antes do lançamento)",
            description = "Sobe uma foto avulsa e devolve o ID a ser informado em `evidencias` no lançamento da inspeção. "
                    + "Uma foto por requisição, máx. 15 MB, apenas imagens (JPEG, PNG, WebP, HEIC). A foto fica pendente "
                    + "até ser usada em um lançamento e é descartada automaticamente se não for.")
    @ApiResponse(responseCode = "201", description = "Evidência recebida")
    @ApiResponse(responseCode = "422", description = "Arquivo vazio, acima de 15 MB ou que não é uma fotografia")
    ResponseEntity<EvidenciaInspecaoResponse> enviarEvidencia(MultipartFile arquivo);

    @Operation(
            summary = "Descartar uma evidência ainda não utilizada",
            description = "Remove uma foto pendente enviada pelo próprio usuário. Fotos já vinculadas a uma inspeção "
                    + "devem ser removidas pelo endpoint da inspeção.")
    @ApiResponse(responseCode = "204", description = "Evidência descartada")
    ResponseEntity<Void> descartarEvidenciaPendente(UUID idAnexo);

    @Operation(
            summary = "Anexar outra evidência a uma inspeção já lançada",
            description = "Acrescenta uma fotografia à comprovação de uma inspeção existente. Apenas imagens, máx. 15 MB.")
    @ApiResponse(responseCode = "201", description = "Evidência anexada")
    ResponseEntity<EvidenciaInspecaoResponse> anexarEvidenciaInspecao(UUID idInspecao, MultipartFile arquivo);

    @Operation(summary = "Baixar uma evidência da inspeção", description = "Transmite o conteúdo binário da fotografia.")
    ResponseEntity<InputStreamResource> baixarEvidenciaInspecao(UUID idInspecao, UUID idAnexo);

    @Operation(
            summary = "Remover uma evidência da inspeção",
            description = "A última evidência não pode ser removida — a inspeção ficaria sem comprovação. Anexe outra antes.")
    @ApiResponse(responseCode = "204", description = "Evidência removida")
    @ApiResponse(responseCode = "422", description = "Tentativa de remover a única evidência da inspeção")
    ResponseEntity<Void> removerEvidenciaInspecao(UUID idInspecao, UUID idAnexo);

    @Operation(summary = "Buscar uma inspeção por ID")
    ResponseEntity<InspecaoSegurancaResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar inspeções com filtros dinâmicos",
            description = "Formulário, processo, inspetor, veículo, equipe, base operacional, centro de custo, colaborador, "
                    + "status e gravidade aceitam múltipla seleção: repita o parâmetro (`?idProcesso=a&idProcesso=b`) ou "
                    + "separe por vírgula (`?idProcesso=a,b`). Enviar um único valor mantém o comportamento antigo. "
                    + "O centro de custo equivale a selecionar todas as bases operacionais vinculadas a ele.")
    ResponseEntity<PageResponse<InspecaoSegurancaResponse>> listar(@ParameterObject InspecaoSegurancaFilter filter, Pageable pageable);

    @Operation(summary = "Listar as inspeções realizadas pelo inspetor logado")
    ResponseEntity<PageResponse<InspecaoSegurancaResponse>> listarMinhas(@ParameterObject InspecaoSegurancaFilter filter, Pageable pageable);

    @Operation(summary = "Atualizar o tratamento de um desvio (status, ação corretiva e prazo)")
    ResponseEntity<InspecaoSegurancaResponse> tratarDesvio(UUID idInspecao, UUID idDesvio, TratarDesvioRequest request);

    @Operation(summary = "Atualizar completamente a inspeção de segurança",
            description = "Permite alterar cabeçalho, respostas do formulário, desvios e evidências fotográficas da inspeção. Restrito UNICAMENTE ao criador original da inspeção e gera evento auditável.")
    @ApiResponse(responseCode = "200", description = "Inspeção atualizada com sucesso")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é o criador da inspeção")
    @ApiResponse(responseCode = "422", description = "Violação de invariante ou formulário inválido")
    ResponseEntity<InspecaoSegurancaResponse> atualizar(UUID id, com.nexo.app.modules.sst.application.dto.AtualizarInspecaoRequest request);

    @Operation(summary = "Atualizar cabeçalho da inspeção de segurança",
            description = "Permite alterar apenas dados do cabeçalho (tipo, processo, base, veículo, equipe, data/hora, observações, localização e colaboradores). Restrito UNICAMENTE ao criador original da inspeção.")
    @ApiResponse(responseCode = "200", description = "Cabeçalho atualizado com sucesso")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é o criador da inspeção")
    @ApiResponse(responseCode = "422", description = "Violação de invariante ou tentativa de remover colaborador com desvio")
    ResponseEntity<InspecaoSegurancaResponse> atualizarCabecalho(UUID id, com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest request);

    @Operation(summary = "Obter histórico de alterações e eventos da inspeção de segurança",
            description = "Retorna linha do tempo com eventos auditáveis, detalhes de alterações campo a campo e integridade da cadeia.")
    ResponseEntity<com.nexo.app.modules.sst.application.dto.HistoricoInspecaoResponse> obterHistorico(UUID id, Pageable pageable);

    @Operation(
            summary = "Anexar uma evidência a um desvio",
            description = "Faz upload de uma foto ou documento (PDF/Word) como evidência do desvio (contexto=DESVIO) ou da ação corretiva (contexto=ACAO_CORRETIVA). Máx. 15 MB. Anexos são opcionais.")
    @ApiResponse(responseCode = "201", description = "Evidência anexada com sucesso")
    ResponseEntity<AnexoDesvioResponse> anexarEvidencia(UUID idInspecao, UUID idDesvio, ContextoAnexo contexto, MultipartFile arquivo);

    @Operation(summary = "Baixar uma evidência de um desvio", description = "Transmite o conteúdo binário do anexo.")
    ResponseEntity<InputStreamResource> baixarEvidencia(UUID idInspecao, UUID idDesvio, UUID idAnexo);

    @Operation(summary = "Remover uma evidência de um desvio")
    @ApiResponse(responseCode = "204", description = "Evidência removida")
    ResponseEntity<Void> removerEvidencia(UUID idInspecao, UUID idDesvio, UUID idAnexo);

    @Operation(summary = "Exportar inspeções em CSV ou XLSX (Resumido)", description = "Gera um arquivo CSV contendo os dados gerais das inspeções, limitado a 10.000 registros.")
    ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportarResumo(@ParameterObject InspecaoSegurancaFilter filter, Boolean apenasMinhas,
            @io.swagger.v3.oas.annotations.Parameter(description = "csv (padrão, Excel pt-BR) ou xlsx (tipos nativos e aba Sobre)") String formato);

    @Operation(summary = "Exportar inspeções em CSV ou XLSX (Detalhado)", description = "Gera um arquivo CSV contendo os dados detalhados das inspeções, incluindo resumo dos desvios, limitado a 10.000 registros.")
    ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportarDetalhado(@ParameterObject InspecaoSegurancaFilter filter, Boolean apenasMinhas,
            @io.swagger.v3.oas.annotations.Parameter(description = "csv (padrão, Excel pt-BR) ou xlsx (tipos nativos e aba Sobre)") String formato);

    @Operation(summary = "Exportar desvios em CSV ou XLSX (Resumido)", description = "Gera um arquivo CSV contendo os dados gerais dos desvios, limitado a 10.000 registros.")
    ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportarDesviosResumo(@ParameterObject InspecaoSegurancaFilter filter, Boolean apenasMinhas,
            @io.swagger.v3.oas.annotations.Parameter(description = "csv (padrão, Excel pt-BR) ou xlsx (tipos nativos e aba Sobre)") String formato);

    @Operation(summary = "Exportar desvios em CSV ou XLSX (Detalhado)", description = "Gera um arquivo CSV contendo os dados detalhados dos desvios, limitado a 10.000 registros.")
    ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportarDesviosDetalhado(@ParameterObject InspecaoSegurancaFilter filter, Boolean apenasMinhas,
            @io.swagger.v3.oas.annotations.Parameter(description = "csv (padrão, Excel pt-BR) ou xlsx (tipos nativos e aba Sobre)") String formato);
}
