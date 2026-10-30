package com.nexo.app.modules.automacao.doc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.automacao.application.dto.AprovacaoResponse;
import com.nexo.app.modules.automacao.application.dto.AutomacaoDetalheResponse;
import com.nexo.app.modules.automacao.application.dto.AutomacaoFilter;
import com.nexo.app.modules.automacao.application.dto.AutomacaoRequest;
import com.nexo.app.modules.automacao.application.dto.AutomacaoResumoResponse;
import com.nexo.app.modules.automacao.application.dto.ExecucaoDetalheResponse;
import com.nexo.app.modules.automacao.application.dto.ExecucaoResumoResponse;
import com.nexo.app.modules.automacao.application.dto.ExecutarRequest;
import com.nexo.app.modules.automacao.application.dto.OpcaoResponse;
import com.nexo.app.modules.automacao.application.dto.PreviaEmailRequest;
import com.nexo.app.modules.automacao.application.dto.PreviaEmailResponse;
import com.nexo.app.modules.automacao.application.dto.RascunhoRequest;
import com.nexo.app.modules.automacao.application.dto.RascunhoResponse;
import com.nexo.app.modules.automacao.application.dto.RespostaAprovacaoRequest;
import com.nexo.app.modules.automacao.application.dto.StatusAutomacaoRequest;
import com.nexo.app.modules.automacao.application.dto.TransferirRequest;
import com.nexo.app.modules.automacao.application.dto.ValidacaoRequest;
import com.nexo.app.modules.automacao.application.dto.ValidacaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Automações", description = """
        Motor de automações do Nexo (substitui as regras de notificação).

        Uma automação é **um gatilho** (manual, agenda, evento de domínio, limiar ou falha de outra
        automação), **variáveis** e **uma árvore de passos**: condições com e/ou, escolher caso, para cada,
        repetir até, escopo (tentar/tratar), ramos paralelos, esperar, aprovação, dados, IA (incluindo o
        Copiloto com relatório PDF), e-mail rico e aviso no sininho. Os passos trocam dados por
        expressões `{{ steps.<id>.outputs.<campo> }}`; os gatilhos de evento trazem as entidades por trás dos
        ids (ex.: `trigger.outputs.colaborador.base`).

        Cada edição salva cria uma **versão**; cada execução grava **uma etapa por passo** (entradas, saídas,
        tentativas, erro) e pode ser retomada, cancelada e reenviada. Nada roda antes de alguém ativar.
        Execuções leem dados com as permissões do **responsável** pela automação.
        """)
public interface AutomacaoControllerDoc {

    @Operation(summary = "Catálogo de nós, grupos, tipos, operadores e funções para o designer")
    ResponseEntity<Map<String, Object>> catalogo();

    @Operation(summary = "Modelos prontos para começar")
    ResponseEntity<List<com.nexo.app.modules.automacao.application.usecases.ModelosAutomacao.Modelo>> modelos();

    @Operation(summary = "Lista automações")
    ResponseEntity<PageResponse<AutomacaoResumoResponse>> listar(AutomacaoFilter filtro, Pageable pageable);

    @Operation(summary = "Detalhe com a definição atual, versões e o que impede a ativação")
    ResponseEntity<AutomacaoDetalheResponse> buscar(UUID id);

    @Operation(summary = "Cria uma automação (rascunho)")
    ResponseEntity<AutomacaoDetalheResponse> criar(AutomacaoRequest request);

    @Operation(summary = "Salva; se a definição mudou, grava uma versão nova")
    ResponseEntity<AutomacaoDetalheResponse> salvar(UUID id, AutomacaoRequest request);

    @Operation(summary = "Ativa ou pausa")
    ResponseEntity<AutomacaoDetalheResponse> alterarStatus(UUID id, StatusAutomacaoRequest request);

    @Operation(summary = "Arquiva (some da lista e para de rodar)")
    ResponseEntity<Void> arquivar(UUID id);

    @Operation(summary = "Duplica como rascunho")
    ResponseEntity<AutomacaoDetalheResponse> duplicar(UUID id);

    @Operation(summary = "Troca o responsável (em nome de quem a automação lê dados)")
    ResponseEntity<AutomacaoDetalheResponse> transferir(UUID id, TransferirRequest request);

    @Operation(summary = "Definição de uma versão antiga")
    ResponseEntity<Map<String, Object>> versao(UUID id, int numero);

    @Operation(summary = "Verifica uma definição: erros e avisos por passo")
    ResponseEntity<ValidacaoResponse> validar(ValidacaoRequest request);

    @Operation(summary = "Prévia do e-mail: o layout institucional real, com os dados do fluxo marcados")
    ResponseEntity<PreviaEmailResponse> previaEmail(PreviaEmailRequest request);

    @Operation(summary = "Executa agora (ou testa: e-mails só para quem testa, esperas puladas, aprovações automáticas)")
    ResponseEntity<ExecucaoResumoResponse> executar(UUID id, ExecutarRequest request);

    @Operation(summary = "Histórico de execuções")
    ResponseEntity<PageResponse<ExecucaoResumoResponse>> execucoes(UUID id, Pageable pageable);

    @Operation(summary = "Uma execução com todas as etapas")
    ResponseEntity<ExecucaoDetalheResponse> execucao(UUID id, UUID execucaoId);

    @Operation(summary = "Cancela uma execução em andamento")
    ResponseEntity<Void> cancelar(UUID id, UUID execucaoId);

    @Operation(summary = "Reenvia com a mesma saída do gatilho, na versão atual")
    ResponseEntity<ExecucaoResumoResponse> reenviar(UUID id, UUID execucaoId);

    @Operation(summary = "Baixa um arquivo gerado numa execução")
    ResponseEntity<byte[]> arquivo(UUID arquivoId);

    @Operation(summary = "O Copiloto monta (ou ajusta) uma automação a partir de um pedido")
    ResponseEntity<RascunhoResponse> rascunho(RascunhoRequest request);

    @Operation(summary = "Opções para os seletores (usuario, colaborador, perfil, permissao, base, fonte)")
    ResponseEntity<List<OpcaoResponse>> opcoes(String tipo, String busca);

    @Operation(summary = "Nomes de valores já escolhidos")
    ResponseEntity<List<OpcaoResponse>> rotulos(String tipo, List<String> valores);

    @Operation(summary = "Aprovações pendentes para o usuário logado")
    ResponseEntity<List<AprovacaoResponse>> minhasAprovacoes();

    @Operation(summary = "Uma aprovação")
    ResponseEntity<AprovacaoResponse> aprovacao(UUID id);

    @Operation(summary = "Responde a uma aprovação")
    ResponseEntity<AprovacaoResponse> responder(UUID id, RespostaAprovacaoRequest request);
}
