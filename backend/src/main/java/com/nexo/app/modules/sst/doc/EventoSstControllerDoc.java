package com.nexo.app.modules.sst.doc;

import com.nexo.app.modules.sst.application.dto.AnexoEventoResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstFilter;
import com.nexo.app.modules.sst.application.dto.ConfirmarImportacaoEventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstResumoResponse;
import com.nexo.app.modules.sst.application.dto.ImportacaoEventoSstResponse;
import com.nexo.app.modules.sst.application.dto.MotivoEventoRequest;
import com.nexo.app.modules.sst.application.dto.TratativaEventoRequest;
import com.nexo.app.modules.sst.domain.ContextoAnexoEvento;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.UUID;

@Tag(name = "SST - Eventos de Segurança",
        description = "Acidentes, incidentes, danos materiais e doenças ocupacionais")
@SecurityRequirement(name = "bearerAuth")
public interface EventoSstControllerDoc {

    @Operation(
            summary = "Registrar um evento de segurança",
            description = """
                    Registra uma ocorrência e **congela o contexto**: atividade, gestor, base,
                    equipe, veículo, técnicos e envolvidos são copiados dos cadastros de origem
                    no instante do registro. Trocar o gestor de uma atividade amanhã não altera
                    nenhum evento já registrado.

                    O que é exigido depende do **tipo**:

                    * `ACIDENTE_TIPICO`, `ACIDENTE_TRAJETO` e `DOENCA_OCUPACIONAL` implicam lesão:
                      exigem ao menos um envolvido no papel `ACIDENTADO` e o local da lesão;
                    * `INCIDENTE_SEM_LESAO` e `DANO_MATERIAL` não admitem local
                      da lesão, natureza da lesão nem dias perdidos.

                    O gestor é herdado da atividade; informá-lo aqui vale só para este evento e
                    o marca como substituto. Enquanto a atividade não tiver gestor cadastrado,
                    informá-lo é obrigatório.

                    A resposta traz o identificador legível `EVT-AAAA-NNNNNN`, além do id técnico.

                    Envie o header `Idempotency-Key` para que uma retentativa devolva o mesmo
                    evento em vez de registrar o acidente duas vezes.""")
    @ApiResponse(responseCode = "201", description = "Evento registrado")
    @ApiResponse(responseCode = "400", description = "Lista de técnicos vazia ou payload inválido")
    @ApiResponse(responseCode = "422", description = """
            Datas incoerentes, dado de lesão em tipo que não admite, CAT sem tipo (ou dados de
            CAT sem CAT declarada), envolvido duplicado, terceiro sem empresa, gestor ausente
            ou entrada de catálogo de outro vocabulário""")
    ResponseEntity<EventoSstResponse> registrar(EventoSstRequest request);

    @Operation(
            summary = "Validar importação de eventos de SST em lote",
            description = """
                    Valida um CSV UTF-8 separado por ponto e vírgula sem persistir. Cada linha
                    passa pelas mesmas referências, snapshots e invariantes do cadastro manual.
                    Nomes de atividade, base, classificação e pessoas são resolvidos de forma
                    exata; UUIDs também são aceitos. Limite de 2.000 eventos por arquivo.""")
    ResponseEntity<ImportacaoEventoSstResponse> importar(MultipartFile arquivo);

    @Operation(
            summary = "Confirmar importação de eventos de SST",
            description = "Revalida o lote e inicia a persistência assíncrona somente das linhas válidas.")
    ResponseEntity<ImportacaoEventoSstResponse> confirmarImportacao(
            ConfirmarImportacaoEventoSstRequest request);

    @Operation(
            summary = "Baixar modelo CSV de eventos de SST",
            description = "Disponibiliza cabeçalho, instruções e exemplos no formato exato aceito pela importação.")
    ResponseEntity<byte[]> baixarModeloImportacao();

    @Operation(
            summary = "Corrigir um evento",
            description = """
                    Reafirma o registro inteiro, com as mesmas validações do cadastro — inclusive
                    a troca de tipo, que muda o que passa a ser exigido. Os snapshots são
                    recopiados dos cadastros no momento da correção.

                    Um evento concluído ou cancelado é imutável: reabra-o informando o motivo
                    antes de editar.""")
    @ApiResponse(responseCode = "422", description = "Evento concluído ou cancelado, ou invariante violada")
    ResponseEntity<EventoSstResponse> atualizar(UUID id, EventoSstRequest request);

    @Operation(
            summary = "Buscar um evento por ID",
            description = """
                    Nomes de atividade, gestor, base, equipe, veículo e pessoas vêm do snapshot —
                    são o retrato do registro e não mudam com o cadastro. Classificação, lesão,
                    agente causador e processo são resolvidos na leitura.

                    `horasAteComunicacao` e `comunicacaoAtrasada` são calculados contra o prazo
                    legal do tipo (24 h para acidentes e doença ocupacional, 72 h para os demais)
                    e nunca ficam gravados.""")
    ResponseEntity<EventoSstResponse> buscarPorId(UUID id);

    @Operation(
            summary = "Listar eventos com filtros dinâmicos",
            description = """
                    Listagem paginada, mais recentes primeiro pela data da ocorrência.

                    A busca por nome de pessoa é **assimétrica**, e isso decorre de como cada nome
                    está protegido: `nomeColaborador` casa **parcialmente** (o trecho é resolvido
                    em ids dentro do RH, que é quem tem a coluna normalizada de busca), enquanto
                    `nomeTerceiro` casa de forma **exata** (blind index sobre o nome cifrado — um
                    hash não tem "começa com").

                    `buscaGlobal` combina com os demais filtros e compara, sem acento e sem
                    diferenciar maiúsculas, o número de registro, a descrição, o local da
                    ocorrência, a atividade e a base.""")
    ResponseEntity<PageResponse<EventoSstResumoResponse>> listar(
            @ParameterObject EventoSstFilter filter, Pageable pageable);

    @Operation(
            summary = "Exportar a listagem em CSV",
            description = """
                    Exporta exatamente as linhas que a listagem devolveria com os mesmos filtros —
                    a consulta é a mesma, sem uma segunda implementação que possa divergir.

                    O arquivo traz identificador legível, tipo, status, datas de ocorrência e
                    comunicação (com o atraso já calculado), atividade, gestor, base, equipe,
                    veículo, classificação, dados de lesão, acidentados, técnicos responsáveis e
                    dados da CAT. Limite de 10.000 linhas por exportação.""")
    ResponseEntity<StreamingResponseBody> exportar(@ParameterObject EventoSstFilter filter);

    @Operation(
            summary = "Assumir a tratativa",
            description = "Move o evento para `EM_TRATATIVA`, opcionalmente já registrando o que está sendo feito.")
    ResponseEntity<EventoSstResponse> iniciarTratativa(UUID id, TratativaEventoRequest request);

    @Operation(
            summary = "Concluir a tratativa",
            description = """
                    Encerra o evento. Exige tratativa preenchida — aqui ou em uma chamada
                    anterior: concluir sem relato é arquivar, e arquivar não é tratar.""")
    @ApiResponse(responseCode = "422", description = "Evento sem tratativa, já concluído ou cancelado")
    ResponseEntity<EventoSstResponse> concluir(UUID id, TratativaEventoRequest request);

    @Operation(
            summary = "Cancelar um registro indevido",
            description = """
                    Exige motivo. O evento continua legível e sai de todos os indicadores de
                    acidentalidade. Não é o caminho para corrigir um evento concluído — para
                    isso, reabra.""")
    @ApiResponse(responseCode = "422", description = "Evento já cancelado ou concluído")
    ResponseEntity<EventoSstResponse> cancelar(UUID id, MotivoEventoRequest request);

    @Operation(
            summary = "Reabrir um evento concluído ou cancelado",
            description = "Exige motivo e devolve o evento a `EM_TRATATIVA`, liberando a edição.")
    @ApiResponse(responseCode = "422", description = "O evento já está aberto")
    ResponseEntity<EventoSstResponse> reabrir(UUID id, MotivoEventoRequest request);

    @Operation(
            summary = "Desativar (soft delete) um evento",
            description = "Nenhuma linha é apagada fisicamente: o registro sai das listagens e permanece auditável.")
    ResponseEntity<Void> desativar(UUID id);

    // ------------------------------------------------------------------ //
    //  Evidências
    // ------------------------------------------------------------------ //

    @Operation(
            summary = "Anexar uma evidência ao evento",
            description = """
                    Envio **após** o registro, no contexto `EVENTO` (fotos do local, boletim),
                    `CAT` (a comunicação emitida) ou `TRATATIVA` (comprovação do que foi feito).

                    Aceita imagens (JPEG, PNG, WebP, HEIC), PDF e Word, até 15 MB por arquivo.
                    Se o armazenamento falhar, nada é gravado. Um evento concluído não aceita
                    novos anexos — reabra antes.""")
    @ApiResponse(responseCode = "201", description = "Evidência anexada")
    @ApiResponse(responseCode = "422", description = "Formato fora da whitelist, tamanho excedido ou evento fechado")
    ResponseEntity<AnexoEventoResponse> anexar(UUID id, ContextoAnexoEvento contexto, MultipartFile arquivo);

    @Operation(summary = "Listar as evidências do evento")
    ResponseEntity<List<AnexoEventoResponse>> listarAnexos(UUID id);

    @Operation(summary = "Baixar uma evidência")
    ResponseEntity<InputStreamResource> baixarAnexo(UUID id, UUID idAnexo);

    @Operation(
            summary = "Remover uma evidência",
            description = "Recusada em evento concluído ou cancelado, pela mesma razão que a edição.")
    ResponseEntity<Void> removerAnexo(UUID id, UUID idAnexo);
}
