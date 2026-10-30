package com.nexo.app.modules.notificacao.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo fechado do que o sistema sabe notificar.
 *
 * <p>Este enum é a fronteira entre o que é configurável e o que não é. O
 * administrador molda tudo em volta de um gatilho — condições, limiar, cooldown,
 * destinatários, texto — mas não cria gatilhos, porque cada um exige código: ou um
 * módulo publicando o evento, ou um módulo sabendo medir a situação. Um catálogo
 * aberto daria a ilusão de que basta cadastrar para funcionar.</p>
 *
 * <p>As {@link #variaveis()} declaradas aqui são o contrato dos textos e das
 * condições: a regra é validada contra elas ao ser salva, então um {@code
 * {{placeholder}}} com erro de digitação é recusado na configuração e nunca chega
 * ao usuário final.</p>
 */
public enum GatilhoNotificacao {

    OBSERVABILITY_INCIDENTE_ABERTO(
            "observability.incidente_aberto",
            "Incidente de observabilidade aberto",
            "Dispara quando um componente monitorado entra em estado de falha ou degradação sustentada.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.SISTEMA,
            List.of("ambiente", "componente", "estadoInicial", "resumo", "linkStatus", "runbook"),
            List.of()),

    DESCONTO_PENDENTE_APROVACAO(
            "desconto.pendente_aprovacao",
            "Descontos aguardando aprovação",
            "Dispara quando a fila de descontos solicitados atinge o limiar configurado.",
            ModoGatilho.POR_LIMIAR,
            CategoriaNotificacao.APROVACAO,
            List.of("total", "limiar"),
            List.of()),

    SST_DESVIOS_PENDENTES(
            "sst.desvios_pendentes",
            "Desvios de segurança pendentes",
            "Dispara quando o número de desvios ainda não resolvidos atinge o limiar configurado.",
            ModoGatilho.POR_LIMIAR,
            CategoriaNotificacao.SEGURANCA,
            List.of("total", "limiar"),
            List.of()),

    FINANCEIRO_SOLICITACOES_PENDENTES(
            "financeiro.solicitacoes_pendentes",
            "Solicitações financeiras aguardando aprovação",
            "Dispara quando compras e pagamentos submetidos e sem decisão atingem o limiar.",
            ModoGatilho.POR_LIMIAR,
            CategoriaNotificacao.APROVACAO,
            List.of("total", "limiar"),
            List.of()),

    CHAMADOS_ABERTOS_PENDENTES(
            "chamado.pendentes_atendimento",
            "Chamados aguardando atendimento",
            "Dispara quando a fila de chamados sem resolução atinge o limiar configurado.",
            ModoGatilho.POR_LIMIAR,
            CategoriaNotificacao.CHAMADO,
            List.of("total", "limiar"),
            List.of()),

    DESCONTO_SOLICITADO(
            "desconto.solicitado",
            "Desconto solicitado",
            "Dispara a cada novo desconto registrado, antes da validação.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            // Sem o nome do colaborador de propósito: o agregado guarda só o id, e
            // resolvê-lo custaria uma consulta a cada desconto criado, mesmo quando
            // nenhuma regra estiver configurada. O deeplink leva ao registro completo.
            List.of("motivo", "parcelas", "dataOcorrencia", "idDesconto", "idColaborador"),
            List.of("solicitante")),

    DESCONTO_VALIDADO(
            "desconto.validado",
            "Desconto validado",
            "Dispara quando o termo de desconto é validado e passa a aguardar a baixa.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            List.of("motivo", "parcelas", "valorValidado", "idDesconto", "idColaborador"),
            List.of("solicitante")),

    DESCONTO_REJEITADO(
            "desconto.rejeitado",
            "Desconto rejeitado",
            "Dispara quando o termo de desconto é recusado, com o motivo da recusa.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            List.of("motivo", "motivoRejeicao", "idDesconto", "idColaborador"),
            List.of("solicitante")),

    DESCONTO_BAIXADO(
            "desconto.baixado",
            "Desconto baixado",
            "Dispara quando a baixa do desconto é registrada e o ciclo se encerra.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            List.of("motivo", "parcelas", "valorBaixado", "idDesconto", "idColaborador"),
            List.of("solicitante")),

    CHAMADO_ABERTO(
            "chamado.aberto",
            "Chamado aberto",
            "Dispara quando um chamado entra na fila, avisando a equipe que responde por ele.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.CHAMADO,
            List.of("protocolo", "assunto", "topico", "prioridade", "equipe", "idChamado"),
            // 'equipeAtendimento' é o papel que faltava: até aqui só havia como endereçar
            // pessoas nomeadas no registro, e um chamado recém-aberto não tem nenhuma
            // além de quem o abriu.
            List.of("equipeAtendimento", "solicitante")),

    CHAMADO_ATRIBUIDO(
            "chamado.atribuido",
            "Chamado atribuído a um responsável",
            "Dispara quando um chamado passa a ter dono, avisando quem assumiu o atendimento.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.CHAMADO,
            List.of("protocolo", "assunto", "prioridade", "equipe", "idChamado"),
            List.of("responsavel")),

    CHAMADO_MENSAGEM_RECEBIDA(
            "chamado.mensagem_recebida",
            "Nova mensagem em um chamado",
            "Dispara a cada mensagem pública, avisando o outro lado da conversa.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.CHAMADO,
            List.of("protocolo", "assunto", "autor", "trecho", "idChamado"),
            // O autor nunca ocupa papel no próprio evento, então uma regra pode
            // endereçar os dois lados sem o risco de a pessoa notificar a si mesma.
            List.of("solicitante", "responsavel")),

    CHAMADO_RESOLVIDO(
            "chamado.resolvido",
            "Chamado resolvido",
            "Dispara quando o atendimento registra a solução, avisando quem abriu.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.CHAMADO,
            List.of("protocolo", "assunto", "solucao", "prazoReaberturaHoras", "idChamado"),
            List.of("solicitante")),

    CHAMADO_REABERTO(
            "chamado.reaberto",
            "Chamado reaberto",
            "Dispara quando um chamado resolvido volta para atendimento.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.CHAMADO,
            List.of("protocolo", "assunto", "idChamado"),
            List.of("responsavel", "solicitante")),

    FINANCEIRO_SOLICITACAO_SUBMETIDA(
            "financeiro.solicitacao_submetida",
            "Solicitação financeira submetida",
            "Dispara quando uma compra ou pagamento sai do rascunho e entra na fila de decisão.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            // 'tipo' existe para que uma condição roteie COMPRA e PAGAMENTO a
            // aprovadores diferentes sem exigir dois gatilhos.
            List.of("tipo", "numero", "valor", "descricao", "prioridade", "idSolicitacao",
                    "base", "uf", "frota", "obraCivil"),
            List.of("solicitante")),

    FINANCEIRO_SOLICITACAO_APROVADA(
            "financeiro.solicitacao_aprovada",
            "Solicitação financeira aprovada",
            "Dispara na autorização, avisando quem pediu e quem executa o próximo passo.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            List.of("tipo", "numero", "valorAprovado", "descricao", "observacao", "idSolicitacao", "obraCivil"),
            List.of("solicitante")),

    FINANCEIRO_SOLICITACAO_REJEITADA(
            "financeiro.solicitacao_rejeitada",
            "Solicitação financeira rejeitada",
            "Dispara na recusa, devolvendo o motivo a quem pediu.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            List.of("tipo", "numero", "valor", "descricao", "motivoRejeicao", "idSolicitacao"),
            List.of("solicitante")),

    FINANCEIRO_DECISAO_DESFEITA(
            "financeiro.decisao_desfeita",
            "Decisão de solicitação financeira desfeita",
            "Dispara quando uma aprovação ou rejeição é desfeita e a solicitação volta a aguardar decisão.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.APROVACAO,
            List.of("tipo", "numero", "valor", "descricao", "decisaoAnterior", "motivo", "idSolicitacao"),
            List.of("solicitante", "decisorAnterior")),

    ESTOQUE_MATERIAL_APROVADO(
            "estoque.material_aprovado",
            "Solicitação de material aprovada",
            "Dispara na aprovação da requisição, liberando a separação no almoxarifado.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.OPERACIONAL,
            List.of("estoqueOrigem", "estoqueDestino", "totalItens", "idSolicitacao"),
            List.of("requisitante")),

    ESTOQUE_MATERIAL_REJEITADO(
            "estoque.material_rejeitado",
            "Solicitação de material rejeitada",
            "Dispara na recusa da requisição, devolvendo motivo e justificativa.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.OPERACIONAL,
            List.of("estoqueOrigem", "estoqueDestino", "motivoRejeicao", "justificativa", "idSolicitacao"),
            List.of("requisitante")),

    MENCAO_USUARIO_MARCADO(
            "mencao.usuario_marcado",
            "Usuário marcado em um texto",
            "Dispara quando alguém escreve @login em uma mensagem, avisando quem foi marcado.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.MENCAO,
            // 'link' é variável, e não caminho fixo na regra, porque a marcação nasce
            // de origens diferentes (hoje o chamado, amanhã outras) e cada uma sabe
            // para onde levar. O template da regra usa {{link}} como destino.
            List.of("autor", "trecho", "origem", "tipoOrigem", "link"),
            List.of("mencionado")),

    SST_EVENTO_REGISTRADO(
            "sst.evento.registrado",
            "Evento de segurança registrado",
            "Dispara a cada acidente, incidente, dano material ou doença "
                    + "ocupacional comunicado ao SESMT.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.SEGURANCA,
            // Sem nome de pessoa de propósito: o texto da notificação chega ao sininho e
            // ao e-mail, e quem pode ver o acidentado abre o registro pelo deeplink.
            List.of("numeroRegistro", "tipo", "tipoRotulo", "atividade", "base",
                    "dataOcorrencia", "comLesao", "comunicacaoAtrasada", "idEvento"),
            List.of()),

    SST_EVENTO_CONCLUIDO(
            "sst.evento.concluido",
            "Evento de segurança concluído",
            "Dispara quando a tratativa de uma ocorrência de segurança é encerrada.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.SEGURANCA,
            List.of("numeroRegistro", "tipo", "tipoRotulo", "atividade", "base", "tratativa", "idEvento"),
            List.of()),

    FROTA_VENCIMENTO_PROXIMO(
            "frota.vencimento_proximo",
            "Documento ou CNH perto do vencimento",
            "Dispara uma vez por faixa de aviso (ex.: 30, 15, 7 e 0 dias) quando um documento "
                    + "de veículo ou implemento, ou a CNH de um condutor, se aproxima do vencimento.",
            ModoGatilho.POR_OCORRENCIA,
            CategoriaNotificacao.OPERACIONAL,
            // 'prazo' já vem como frase ("vence em 7 dias", "venceu há 2 dias") para o texto
            // não depender de aritmética no template; 'diasRestantes' fica para condições.
            List.of("tipoAlvo", "descricao", "referencia", "dataVencimento", "diasRestantes", "prazo", "link"),
            List.of());

    private final String chave;
    private final String nome;
    private final String descricao;
    private final ModoGatilho modo;
    private final CategoriaNotificacao categoriaPadrao;
    private final List<String> variaveis;
    private final List<String> papeis;

    GatilhoNotificacao(String chave, String nome, String descricao, ModoGatilho modo,
                       CategoriaNotificacao categoriaPadrao, List<String> variaveis, List<String> papeis) {
        this.chave = chave;
        this.nome = nome;
        this.descricao = descricao;
        this.modo = modo;
        this.categoriaPadrao = categoriaPadrao;
        this.variaveis = List.copyOf(variaveis);
        this.papeis = List.copyOf(papeis);
    }

    public String getChave() {
        return chave;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public ModoGatilho getModo() {
        return modo;
    }

    public CategoriaNotificacao getCategoriaPadrao() {
        return categoriaPadrao;
    }

    /** Nomes utilizáveis como {@code {{variavel}}} nos textos e nas condições. */
    public List<String> getVariaveis() {
        return variaveis;
    }

    /** Papéis endereçáveis por {@link TipoDestinatarioRegra#PAPEL_DINAMICO}. */
    public List<String> getPapeis() {
        return papeis;
    }

    public boolean declaraVariavel(String nome) {
        return variaveis.contains(nome);
    }

    public boolean declaraPapel(String nome) {
        return papeis.contains(nome);
    }

    /**
     * Resolve pela chave persistida. Devolve vazio em vez de lançar: uma regra
     * apontando para um gatilho que saiu do catálogo precisa ser exibida e corrigida
     * na tela, não derrubar a listagem inteira.
     */
    public static Optional<GatilhoNotificacao> porChave(String chave) {
        if (chave == null || chave.isBlank()) {
            return Optional.empty();
        }
        String procurada = chave.trim();
        return java.util.Arrays.stream(values())
                .filter(gatilho -> gatilho.chave.equals(procurada))
                .findFirst();
    }

    public static Set<String> chaves() {
        return java.util.Arrays.stream(values())
                .map(GatilhoNotificacao::getChave)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
