package com.nexo.app.modules.notificacao.doc;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.nexo.app.modules.notificacao.application.dto.AnexoComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoFilter;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.CorrigirTextoComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.PublicarComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.RelatorioConfirmacaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notificações - Mural (administração)", description = """
        Escrita e publicação dos comunicados do mural: dicas, changelogs, avisos e
        manutenções programadas.

        **Ciclo de vida.** O comunicado nasce RASCUNHO, onde tudo é editável. Ao publicar,
        recebe uma janela de exibição; início no futuro significa agendado, sem estado nem
        job próprio para isso. Publicado, só o texto pode ser corrigido — mudar o público de
        algo que já foi ao ar alteraria retroativamente quem deveria tê-lo lido.

        **Público-alvo.** Vários segmentos se somam (OR). `TODOS` não convive com os demais.

        **Mídia.** Upload aceita apenas imagem (JPEG, PNG, WEBP, GIF) e PDF, até 15 MB por
        arquivo. Vídeo entra como link externo no campo `videoUrl`.

        **Confirmação de leitura.** Com `exigeConfirmacao`, o comunicado não pode ser adiado
        pelo usuário e o endpoint de relatório presta contas de quem leu e quem falta.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface ComunicadoControllerDoc {

    @Operation(summary = "Listar comunicados para administração")
    ResponseEntity<PageResponse<ComunicadoResponse>> listar(
            @ParameterObject ComunicadoFilter filtro, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar um comunicado")
    ResponseEntity<ComunicadoResponse> buscar(UUID id);

    @Operation(summary = "Criar comunicado (rascunho)")
    ResponseEntity<ComunicadoResponse> criar(ComunicadoRequest request);

    @Operation(summary = "Atualizar comunicado",
            description = "Só enquanto rascunho. A lista de segmentos informada substitui a anterior.")
    ResponseEntity<ComunicadoResponse> atualizar(UUID id, ComunicadoRequest request);

    @Operation(summary = "Corrigir o texto de um comunicado publicado",
            description = "Ajusta título, resumo e conteúdo sem alterar público nem janela de exibição.")
    ResponseEntity<ComunicadoResponse> corrigirTexto(UUID id, CorrigirTextoComunicadoRequest request);

    @Operation(summary = "Publicar comunicado",
            description = """
                    Exige ao menos um público-alvo. Sem `dataInicio`, publica imediatamente.
                    Quando `notificarNoSininho` está ligado, também entrega uma notificação na
                    caixa de entrada de cada destinatário.
                    """)
    ResponseEntity<ComunicadoResponse> publicar(UUID id, PublicarComunicadoRequest request);

    @Operation(summary = "Arquivar comunicado",
            description = "Tira do ar preservando o histórico de confirmações.")
    ResponseEntity<ComunicadoResponse> arquivar(UUID id);

    @Operation(summary = "Desativar comunicado (soft delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Relatório de confirmação de leitura",
            description = "Quem confirmou, quem apenas abriu e quem ainda não viu.")
    ResponseEntity<RelatorioConfirmacaoResponse> relatorioConfirmacao(UUID id);

    @Operation(summary = "Anexar imagem ou PDF ao comunicado")
    ResponseEntity<AnexoComunicadoResponse> anexar(UUID id, MultipartFile arquivo);

    @Operation(summary = "Baixar um anexo do comunicado")
    ResponseEntity<Resource> baixarAnexo(UUID id, UUID idAnexo);

    @Operation(summary = "Remover um anexo do comunicado")
    ResponseEntity<Void> removerAnexo(UUID id, UUID idAnexo);
}
