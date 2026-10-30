package com.nexo.app.modules.notificacao.doc;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.notificacao.application.dto.GatilhoResponse;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoFilter;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoRequest;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoResponse;
import com.nexo.app.shared.application.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notificações - Regras", description = """
        Configuração de quando o sistema avisa, quem ele avisa e com que texto.

        **O gatilho vem de um catálogo fechado** (`GET /gatilhos`), não é cadastrável: cada
        um exige código — ou um módulo publicando o evento, ou um módulo sabendo medir a
        situação. Tudo em volta dele é moldável.

        **Dois modos.** `POR_OCORRENCIA` reage a um evento de domínio, na hora, uma vez por
        registro. `POR_LIMIAR` é avaliado por varredura periódica contra uma contagem do
        módulo dono ("há N pendentes") e exige o campo `limiar` — situações não emitem
        eventos, então só uma varredura as detecta.

        **Variáveis e papéis.** Cada gatilho declara quais variáveis existem; use-as como
        `{{variavel}}` nos textos e como campo das condições. Um placeholder que o gatilho
        não fornece é **recusado ao salvar**, e não descoberto depois pelo usuário final
        lendo um texto quebrado. Papéis (`PAPEL_DINAMICO`) só existem em gatilhos por
        ocorrência, pois é o evento que carrega quem os ocupa.

        **Destinatários somam (OR).** O tipo `PERMISSAO` é o mais durável: a regra guarda o
        nome da permissão, então quem entra ou sai da atribuição passa a receber ou deixa de
        receber sozinho. `POLITICA` referencia uma matriz cadastrável de rotas e escolhe
        exatamente um usuário pelo contexto; a mesma política pode ser reutilizada por
        várias regras do mesmo gatilho.

        **Cooldown** é o silêncio mínimo entre dois avisos do mesmo escopo — o registro, nas
        regras por ocorrência; a própria regra, nas por limiar. É o que impede a fila
        pendente de gerar aviso a cada varredura.

        **Regras de fábrica** (`sistema: true`) podem ser editadas e desativadas, mas não
        removidas.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface RegraNotificacaoControllerDoc {

    @Operation(summary = "Listar os gatilhos disponíveis",
            description = "Catálogo fechado, com as variáveis e papéis de cada gatilho. "
                    + "É a fonte que a tela de criação de regras consome para se montar.")
    ResponseEntity<List<GatilhoResponse>> listarGatilhos();

    @Operation(summary = "Listar regras")
    ResponseEntity<PageResponse<RegraNotificacaoResponse>> listar(
            @ParameterObject RegraNotificacaoFilter filtro, @ParameterObject Pageable pageable);

    @Operation(summary = "Buscar uma regra")
    ResponseEntity<RegraNotificacaoResponse> buscar(UUID id);

    @Operation(summary = "Criar regra")
    ResponseEntity<RegraNotificacaoResponse> criar(RegraNotificacaoRequest request);

    @Operation(summary = "Atualizar regra",
            description = "As listas de condições e destinatários substituem as anteriores. "
                    + "O gatilho não pode ser trocado.")
    ResponseEntity<RegraNotificacaoResponse> atualizar(UUID id, RegraNotificacaoRequest request);

    @Operation(summary = "Ativar regra")
    ResponseEntity<RegraNotificacaoResponse> ativar(UUID id);

    @Operation(summary = "Desativar regra",
            description = "Vale para qualquer regra, inclusive as de fábrica.")
    ResponseEntity<RegraNotificacaoResponse> desativar(UUID id);

    @Operation(summary = "Remover regra",
            description = "Indisponível para regras de fábrica — desative-as em vez de removê-las.")
    ResponseEntity<Void> remover(UUID id);
}
