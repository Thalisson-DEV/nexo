package com.nexo.app.modules.notificacao.doc;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.notificacao.application.dto.AdiarComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoMuralResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notificações - Mural (usuário)", description = """
        O mural do ponto de vista de quem lê.

        `GET /mural` devolve o que este usuário deve ver agora — publicados, dentro da janela,
        que o alcançam e que ele ainda não tirou da frente — já ordenado (fixados primeiro).
        O campo `apresentacao` diz como exibir: MODAL interrompe ao entrar, BANNER fica no
        topo, CARD só aparece para quem abre o mural.

        As ações do usuário são três: **dispensar** ("não exibir novamente", some para sempre),
        **adiar** ("ver depois", some até a data e volta sozinho) e **confirmar** ("Li e
        entendi", disponível apenas quando `exigeConfirmacao` é verdadeiro — e nesse caso
        adiar é bloqueado). Nada é gravado até a primeira ação.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface MuralControllerDoc {

    @Operation(summary = "Comunicados que o usuário autenticado deve ver agora")
    ResponseEntity<List<ComunicadoMuralResponse>> mural();

    @Operation(summary = "Registrar que o usuário abriu o comunicado",
            description = "Marca como visualizado sem tirá-lo do mural.")
    ResponseEntity<Void> registrarVisualizacao(UUID id);

    @Operation(summary = "Dispensar o comunicado",
            description = "\"Não exibir novamente\": some do mural deste usuário em definitivo.")
    ResponseEntity<Void> dispensar(UUID id);

    @Operation(summary = "Adiar o comunicado",
            description = "\"Ver depois\". Sem `adiarAte`, adia por 24 horas. Indisponível "
                    + "para comunicados que exigem confirmação.")
    ResponseEntity<Void> adiar(UUID id, AdiarComunicadoRequest request);

    @Operation(summary = "Confirmar a leitura (\"Li e entendi\")",
            description = "Registra a confirmação e tira o comunicado do mural.")
    ResponseEntity<Void> confirmar(UUID id);

    @Operation(summary = "Baixar um anexo do comunicado")
    ResponseEntity<Resource> baixarAnexo(UUID id, UUID idAnexo);
}
