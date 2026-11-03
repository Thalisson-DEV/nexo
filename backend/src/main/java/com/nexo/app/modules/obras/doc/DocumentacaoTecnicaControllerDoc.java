package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.DocumentacaoTecnicaRequests;
import com.nexo.app.modules.obras.application.dto.DocumentacaoTecnicaResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@Tag(name = "Obras - Documentação técnica", description = "Visita prévia, As Built, fotos do book e equipamentos da obra, lançados no web e no app")
@SecurityRequirement(name = "bearerAuth")
public interface DocumentacaoTecnicaControllerDoc {

    @Operation(summary = "Consultar a documentação técnica da obra")
    ResponseEntity<DocumentacaoTecnicaResponses.Documentacao> consultar(UUID id);

    @Operation(summary = "Registrar visita prévia")
    ResponseEntity<DocumentacaoTecnicaResponses.Visita> registrarVisita(UUID id, DocumentacaoTecnicaRequests.Visita request);

    @Operation(summary = "Alterar visita prévia")
    ResponseEntity<DocumentacaoTecnicaResponses.Visita> alterarVisita(UUID id, UUID idVisita, DocumentacaoTecnicaRequests.Visita request);

    @Operation(summary = "Remover visita prévia")
    ResponseEntity<Void> removerVisita(UUID id, UUID idVisita);

    @Operation(summary = "Baixar o PDF da pré-vistoria")
    ResponseEntity<byte[]> pdfDaVisita(UUID id, UUID idVisita);

    @Operation(summary = "Preencher a ficha As Built de um ponto com execução")
    ResponseEntity<Void> preencherFicha(UUID id, UUID idPonto, DocumentacaoTecnicaRequests.Ficha request);

    @Operation(summary = "Lançar uma foto do book em um ponto", description = "JPG ou PNG. Com idItem, responde a um item do modelo; sem ele, é uma foto avulsa do ponto. Vale desde antes da execução.")
    ResponseEntity<UUID> registrarEvidencia(UUID id, UUID idPonto, UUID idModelo, String idItem, String legenda, MultipartFile arquivo);

    @Operation(summary = "Alterar a legenda de uma foto do book")
    ResponseEntity<Void> alterarLegendaDaEvidencia(UUID id, UUID idEvidencia, DocumentacaoTecnicaRequests.LegendaEvidencia request);

    @Operation(summary = "Remover uma foto do book")
    ResponseEntity<Void> removerEvidencia(UUID id, UUID idEvidencia);

    @Operation(summary = "Baixar o arquivo original de uma foto do book")
    ResponseEntity<StreamingResponseBody> baixarEvidencia(UUID id, UUID idEvidencia);

    @Operation(summary = "Baixar a miniatura de uma foto do book")
    ResponseEntity<StreamingResponseBody> baixarMiniaturaDaEvidencia(UUID id, UUID idEvidencia);

    @Operation(summary = "Gerar nova versão do As Built", description = "Recusa enquanto houver ficha incompleta; falta de foto não impede. Leva as plantas, as fichas e as fotos dos pontos executados. O arquivo entra nos anexos da obra.")
    ResponseEntity<DocumentacaoTecnicaResponses.Documento> gerarAsBuilt(UUID id);

    @Operation(summary = "Marcar religador ou regulador em um ponto")
    ResponseEntity<Void> definirEquipamento(UUID id, UUID idPonto, DocumentacaoTecnicaRequests.Equipamento request);

    @Operation(summary = "Retirar o ponto da lista de equipamentos")
    ResponseEntity<Void> removerEquipamento(UUID id, UUID idPonto);
}
