package com.nexo.app.modules.iam.doc;

import com.nexo.app.modules.iam.application.dto.*;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Usuários", description = "Gestão de acessos e credenciais do sistema")
@SecurityRequirement(name = "bearerAuth")
public interface UsuarioControllerDoc {

    @Operation(summary = "Registrar novo usuário", description = "Cria um acesso com senha protegida e vinculada a um perfil")
    ResponseEntity<UsuarioResponse> criar(UsuarioRequest request);

    @Operation(
            summary = "Validar importação de usuários em lote",
            description = "Valida CSV UTF-8 separado por ';'. O colaborador é opcional e identificado por nome completo; nomes homônimos são rejeitados. Limite: 2000 registros."
    )
    ResponseEntity<ImportacaoUsuarioResponse> importar(
            @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            MultipartFile arquivo);

    @Operation(
            summary = "Confirmar importação de usuários",
            description = "Revalida o lote e inicia a persistência assíncrona quando todos os registros estiverem válidos."
    )
    ResponseEntity<ImportacaoUsuarioResponse> confirmarImportacao(ConfirmarImportacaoUsuarioRequest request);

    @Operation(
            summary = "Baixar modelo CSV para importação de usuários",
            description = "Disponibiliza o modelo com exemplos de usuário vinculado e não vinculado a colaborador."
    )
    ResponseEntity<byte[]> baixarModeloCsv();

    @Operation(summary = "Atualizar dados do usuário")
    ResponseEntity<UsuarioResponse> atualizar(UUID id, UsuarioUpdateRequest request);

    @Operation(summary = "Desativar usuário (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar usuário")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar usuário por ID", description = "Inclui os dados do colaborador vinculado, quando houver")
    ResponseEntity<UsuarioResponse> buscarPorId(UUID id);

    @Operation(
            summary = "Listar usuários paginados com filtros dinâmicos",
            description = """
                    Cada usuário vem com o colaborador dono do acesso (nome, função e base), \
                    para que a lista possa ser lida sem conhecer a convenção de logins. \
                    É possível procurar pelo nome da pessoa (nomeColaborador ou buscaGlobal) \
                    e isolar os acessos sem vínculo com o RH (vinculadoAColaborador=false)."""
    )
    ResponseEntity<PageResponse<UsuarioResponse>> listar(@ParameterObject UsuarioFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Obter dados do usuário logado", description = "Retorna os detalhes do usuário autenticado no contexto de segurança")
    ResponseEntity<UsuarioResponse> me(@Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Listar favoritos do usuário logado", description = "Retorna a lista de rotas favoritas do usuário autenticado")
    ResponseEntity<List<FavoritoResponse>> listarFavoritos(@Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Adicionar novo favorito", description = "Salva uma rota nos favoritos do usuário logado")
    ResponseEntity<FavoritoResponse> adicionarFavorito(@Parameter(hidden = true) Usuario usuario, FavoritoRequest request);

    @Operation(summary = "Remover favorito", description = "Remove (soft delete) um favorito do usuário logado por ID")
    ResponseEntity<Void> removerFavorito(@Parameter(hidden = true) Usuario usuario, UUID id);

    @Operation(summary = "Atualizar preferências de interface", description = "Altera o tema (claro/escuro) e a cor primária do usuário logado")
    ResponseEntity<Void> atualizarPreferencias(@Parameter(hidden = true) Usuario usuario, PreferenciasRequest request);
}
