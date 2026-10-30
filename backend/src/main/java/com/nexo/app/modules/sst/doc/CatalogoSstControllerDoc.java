package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.CatalogoSstFilter;
import com.nexo.app.modules.sst.application.dto.CatalogoSstRequest;
import com.nexo.app.modules.sst.application.dto.CatalogoSstResponse;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
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

@Tag(name = "SST - Catálogos de Eventos",
        description = "Vocabulários mantidos pelo usuário para classificar eventos de SST")
@SecurityRequirement(name = "bearerAuth")
public interface CatalogoSstControllerDoc {

    @Operation(
            summary = "Cadastrar uma entrada de catálogo",
            description = """
                    Cria uma entrada em um dos vocabulários de SST: CLASSIFICACAO_EVENTO (como a
                    empresa classifica o evento), LOCAL_LESAO (parte do corpo atingida),
                    NATUREZA_LESAO (tipo de lesão) ou AGENTE_CAUSADOR (o que causou).

                    O nome é único dentro do tipo, comparado sem acento e sem diferenciar
                    maiúsculas — "Choque Elétrico" e "choque eletrico" são a mesma entrada. A
                    comparação considera apenas as entradas ativas: um nome liberado por uma
                    desativação pode ser reutilizado.""")
    @ApiResponse(responseCode = "201", description = "Entrada cadastrada")
    @ApiResponse(responseCode = "422", description = "Nome já usado por outra entrada ativa do mesmo tipo")
    ResponseEntity<CatalogoSstResponse> criar(CatalogoSstRequest request);

    @Operation(summary = "Buscar uma entrada por ID")
    ResponseEntity<CatalogoSstResponse> buscarPorId(UUID id);

    @Operation(
            summary = "Listar entradas com filtros dinâmicos",
            description = "Listagem paginada de todos os vocabulários. Use `tipo` para restringir a um deles.")
    ResponseEntity<PageResponse<CatalogoSstResponse>> listar(
            @ParameterObject CatalogoSstFilter filter, Pageable pageable);

    @Operation(
            summary = "Listar as opções ativas de um vocabulário",
            description = """
                    Devolve, sem paginação e na ordem de exibição, as entradas ativas do tipo —
                    é o que abastece um campo do formulário de registro de evento. Entradas
                    desativadas não aparecem aqui, mas continuam visíveis nos eventos que já as
                    referenciam.""")
    ResponseEntity<List<CatalogoSstResponse>> listarOpcoes(TipoCatalogoSst tipo);

    @Operation(
            summary = "Atualizar uma entrada",
            description = """
                    Altera nome, descrição, código e ordem. O tipo é imutável: mudá-lo
                    reescreveria o significado do dado nos eventos que já apontam para a
                    entrada.""")
    @ApiResponse(responseCode = "422", description = "Tipo divergente do atual ou nome duplicado")
    ResponseEntity<CatalogoSstResponse> atualizar(UUID id, CatalogoSstRequest request);

    @Operation(
            summary = "Desativar (soft delete) uma entrada",
            description = "A entrada deixa de ser oferecida em novos registros e continua exibida nos eventos antigos.")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(
            summary = "Reativar uma entrada",
            description = "Recusa a reativação se, nesse meio-tempo, o nome tiver sido tomado por outra entrada ativa.")
    ResponseEntity<Void> ativar(UUID id);
}
