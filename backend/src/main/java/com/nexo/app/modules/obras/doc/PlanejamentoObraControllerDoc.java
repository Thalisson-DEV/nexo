package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Obras - Planejamento", description = "Planejamento sobre o PDF do projeto: pontos, vãos, kits, anotações e leitura pelo Copiloto")
@SecurityRequirement(name = "bearerAuth")
public interface PlanejamentoObraControllerDoc {

    @Operation(summary = "Ler o planejamento completo da obra, com indicadores e avanço por kit")
    ResponseEntity<PlanejamentoObraResponse> buscar(UUID idObra);

    @Operation(summary = "Definir um anexo PDF da obra como projeto mestre")
    ResponseEntity<PlanejamentoObraResponse> definirProjetoMestre(UUID idObra, PlanejamentoObraRequests.DefinirProjetoMestre request);

    @Operation(summary = "Anexar um PDF e torná-lo o projeto mestre", description = "O projeto anterior continua nos anexos, e pontos, vãos e anotações não mudam.")
    ResponseEntity<PlanejamentoObraResponse> enviarProjetoMestre(UUID idObra, UUID idTipoAnexo, MultipartFile arquivo);

    @Operation(summary = "Salvar a rotação de uma página para todos os usuários")
    ResponseEntity<PlanejamentoObraResponse> girarPagina(UUID idObra, int pagina, PlanejamentoObraRequests.GirarPagina request);

    @Operation(summary = "Adicionar ponto na folha")
    ResponseEntity<PlanejamentoObraResponse> adicionarPonto(UUID idObra, PlanejamentoObraRequests.NovoPonto request);

    @Operation(summary = "Editar número, barramento, tipo, execução e marcadores do ponto")
    ResponseEntity<PlanejamentoObraResponse> editarPonto(UUID idObra, UUID idPonto, PlanejamentoObraRequests.EditarPonto request);

    @Operation(summary = "Mover ponto na folha")
    ResponseEntity<PlanejamentoObraResponse.Ponto> moverPonto(UUID idObra, UUID idPonto, PlanejamentoObraRequests.MoverPonto request);

    @Operation(summary = "Excluir ponto com seus kits e vãos", description = "Recusada quando algum item está programado ou executado.")
    ResponseEntity<PlanejamentoObraResponse> excluirPonto(UUID idObra, UUID idPonto);

    @Operation(summary = "Lançar vão entre dois pontos, inclusive em páginas diferentes")
    ResponseEntity<PlanejamentoObraResponse> adicionarVao(UUID idObra, PlanejamentoObraRequests.NovoVao request);

    @Operation(summary = "Alterar a metragem do vão")
    ResponseEntity<PlanejamentoObraResponse> editarVao(UUID idObra, UUID idVao, PlanejamentoObraRequests.EditarVao request);

    @Operation(summary = "Excluir vão com seus kits")
    ResponseEntity<PlanejamentoObraResponse> excluirVao(UUID idObra, UUID idVao);

    @Operation(summary = "Aplicar um kit a um ou vários pontos e vãos", description = "Locais que já têm o kit com a mesma operação ficam como estão e são informados.")
    ResponseEntity<PlanejamentoObraRequests.ResultadoAplicacaoKit> aplicarKit(UUID idObra, PlanejamentoObraRequests.AplicarKit request);

    @Operation(summary = "Alterar a quantidade de kits ou a metragem e recalcular o previsto")
    ResponseEntity<PlanejamentoObraResponse> alterarMultiplicador(UUID idObra, UUID idKitAplicado, PlanejamentoObraRequests.AlterarMultiplicador request);

    @Operation(summary = "Marcar ou desmarcar o kit como executado antes do sistema")
    ResponseEntity<PlanejamentoObraResponse> marcarExecutadoAntes(UUID idObra, UUID idKitAplicado, PlanejamentoObraRequests.MarcarExecutadoAntes request);

    @Operation(summary = "Remover kit aplicado", description = "Recusada quando algum item está programado ou executado.")
    ResponseEntity<PlanejamentoObraResponse> removerKit(UUID idObra, UUID idKitAplicado);

    @Operation(summary = "Alterar a quantidade prevista de um item pendente")
    ResponseEntity<PlanejamentoObraResponse> alterarItem(UUID idObra, UUID idKitAplicado, UUID idItem, PlanejamentoObraRequests.AlterarItem request);

    @Operation(summary = "Remover um item pendente do kit")
    ResponseEntity<PlanejamentoObraResponse> removerItem(UUID idObra, UUID idKitAplicado, UUID idItem);

    @Operation(summary = "Criar anotação na folha")
    ResponseEntity<PlanejamentoObraResponse> adicionarAnotacao(UUID idObra, PlanejamentoObraRequests.NovaAnotacao request);

    @Operation(summary = "Editar anotação")
    ResponseEntity<PlanejamentoObraResponse> editarAnotacao(UUID idObra, UUID idAnotacao, PlanejamentoObraRequests.EditarAnotacao request);

    @Operation(summary = "Excluir anotação")
    ResponseEntity<PlanejamentoObraResponse> excluirAnotacao(UUID idObra, UUID idAnotacao);

    @Operation(summary = "Informar se a leitura pelo Copiloto está disponível e a última leitura da página")
    ResponseEntity<PlanejamentoObraRequests.DisponibilidadeAnalise> disponibilidadeAnalise(UUID idObra, int pagina);

    @Operation(summary = "Pedir ao Copiloto a leitura de uma página do projeto", description = "Processa em segundo plano e não grava nada no planejamento.")
    ResponseEntity<PlanejamentoObraRequests.AnaliseResponse> iniciarAnalise(UUID idObra, PlanejamentoObraRequests.IniciarAnalise request);

    @Operation(summary = "Acompanhar a leitura e obter a proposta")
    ResponseEntity<PlanejamentoObraRequests.AnaliseResponse> buscarAnalise(UUID idObra, UUID idAnalise);

    @Operation(summary = "Gravar as sugestões aceitas pelo usuário", description = "Tudo ou nada, pelas mesmas regras do planejamento manual.")
    ResponseEntity<PlanejamentoObraResponse> aceitarAnalise(UUID idObra, UUID idAnalise, PlanejamentoObraRequests.AceitarProposta request);
}
