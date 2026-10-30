package com.nexo.app.modules.iam.doc;

import com.nexo.app.modules.iam.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Perfis de Acesso", description = "Gestão de papéis e permissões do sistema")
@SecurityRequirement(name = "bearerAuth")
public interface PerfilAcessoControllerDoc {

    @Operation(summary = "Criar novo perfil de acesso")
    ResponseEntity<PerfilAcessoResponse> criar(PerfilAcessoRequest request);

    @Operation(summary = "Atualizar perfil de acesso")
    ResponseEntity<PerfilAcessoResponse> atualizar(UUID id, PerfilAcessoRequest request);

    @Operation(summary = "Desativar perfil de acesso (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Ativar perfil de acesso")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Buscar perfil por ID")
    ResponseEntity<PerfilAcessoResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar todos os perfis de acesso")
    ResponseEntity<PageResponse<PerfilAcessoResponse>> listar(@ParameterObject PerfilAcessoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Vincular uma permissão granular a um perfil")
    ResponseEntity<Void> vincularPermissao(UUID id, UUID idPermissao);

    @Operation(summary = "Remover uma permissão granular de um perfil")
    ResponseEntity<Void> removerPermissao(UUID id, UUID idPermissao);

    @Operation(summary = "Definir todas as permissões do perfil de uma vez",
            description = "Substitui o conjunto inteiro numa única transação: marca as enviadas e remove as ausentes.")
    ResponseEntity<List<PermissaoResponse>> definirPermissoes(UUID id, PermissoesPerfilRequest request);

    @Operation(summary = "Listar permissões vinculadas a um perfil")
    ResponseEntity<List<PermissaoResponse>> listarPermissoesPorPerfil(UUID id);

    @Operation(summary = "Validar arquivo CSV para importação de perfis de acesso")
    ResponseEntity<ImportacaoPerfilAcessoResponse> importar(MultipartFile arquivo);

    @Operation(summary = "Confirmar a importação dos perfis de acesso validados")
    ResponseEntity<ImportacaoPerfilAcessoResponse> confirmarImportacao(ConfirmarImportacaoPerfilAcessoRequest request);

    @Operation(summary = "Baixar modelo CSV para importação de perfis de acesso", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
