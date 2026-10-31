package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.KitImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoFilter;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoRequest;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Obras - Kits construtivos", description = "Catálogo de kits por contrato, itens de serviço e material, valor em R$ e importação")
@SecurityRequirement(name = "bearerAuth")
public interface KitConstrutivoControllerDoc {

    @Operation(summary = "Cadastrar kit construtivo com seus itens")
    ResponseEntity<KitConstrutivoResponse> criar(KitConstrutivoRequest request);

    @Operation(summary = "Atualizar descrição, grupo, classes de tensão e itens do kit",
            description = "Itens com id são alterados, sem id são criados e os ausentes são desativados. Contrato e código não mudam.")
    ResponseEntity<KitConstrutivoResponse> atualizar(UUID id, KitConstrutivoRequest request);

    @Operation(summary = "Buscar kit com itens e valor vigente na data de referência (hoje por padrão)")
    ResponseEntity<KitConstrutivoResponse> buscarPorId(UUID id,
            @Parameter(description = "Data de vigência do valor das ações (ISO, padrão hoje)") LocalDate dataReferencia);

    @Operation(summary = "Listar kits com filtros por contrato, grupo, classe de tensão e busca por código/descrição")
    ResponseEntity<PageResponse<KitConstrutivoResponse>> listar(@ParameterObject KitConstrutivoFilter filter,
            @Parameter(description = "Data de vigência do valor das ações (ISO, padrão hoje)") LocalDate dataReferencia,
            @ParameterObject Pageable pageable);

    @Operation(summary = "Listar os grupos de kits em uso, opcionalmente de um contrato")
    ResponseEntity<List<String>> listarGrupos(UUID idContrato);

    @Operation(summary = "Desativar kit (soft delete)",
            description = "O kit continua visível nas obras onde já foi aplicado, mas não pode ser aplicado em novos pontos.")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Validar a planilha de kits (XLSX ou CSV) e devolver a pré-visualização por linha",
            description = "Uma linha por item, agrupada por CODIGO_KIT. Nada é gravado; corrija as linhas com erro e confirme.")
    ResponseEntity<PreviaImportacaoObraResponse<KitImportacaoLinha>> validarImportacao(UUID idContrato, MultipartFile arquivo);

    @Operation(summary = "Revalidar as linhas da pré-visualização de kits depois de uma correção (nada é gravado)")
    ResponseEntity<PreviaImportacaoObraResponse<KitImportacaoLinha>> revalidarImportacao(
            ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha> request);

    @Operation(summary = "Confirmar a importação de kits",
            description = "Revalida as linhas; as válidas são processadas em segundo plano (cria ou atualiza por código) e a importação vai para o histórico.")
    ResponseEntity<PreviaImportacaoObraResponse<KitImportacaoLinha>> confirmarImportacao(
            ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha> request);

    @Operation(summary = "Baixar o modelo XLSX de importação de kits")
    ResponseEntity<byte[]> baixarModelo();
}
