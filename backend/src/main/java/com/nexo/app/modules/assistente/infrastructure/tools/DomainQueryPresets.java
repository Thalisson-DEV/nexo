package com.nexo.app.modules.assistente.infrastructure.tools;

import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.AgentToolProvider;
import com.nexo.app.modules.assistente.application.semantic.ConsultaDadosService;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.modules.assistente.infrastructure.tools.DomainQueryTool.Campo;
import com.nexo.app.modules.assistente.infrastructure.tools.DomainQueryTool.MetricaPreset;
import com.nexo.app.modules.assistente.infrastructure.tools.DomainQueryTool.Preset;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ferramentas de dominio do Nexo. Cada uma cobre uma pergunta operacional recorrente com parametros de
 * negocio; perguntas fora destes moldes usam consultar_fonte (qualquer view) ou consultar_sql.
 */
@Component
public class DomainQueryPresets implements AgentToolProvider {

    private static final String[] STATUS_SOLICITACAO = {"PENDENTE", "APROVADO", "REJEITADO", "ENTREGUE_PARCIAL",
            "ENTREGUE", "DEVOLVIDO_PARCIAL", "DEVOLVIDO", "ESTORNADO", "CANCELADO"};
    private static final String[] TIPO_TECNICO = {"ADICAO_COMUM", "ADICAO_CONCRETO", "DEVOLUCAO_COMUM", "DEVOLUCAO_CONCRETO"};
    private static final String[] STATUS_FINANCEIRO = {"RASCUNHO", "SOLICITADO", "APROVADO", "REJEITADO", "CANCELADO"};
    private static final String[] PRIORIDADE_FINANCEIRO = {"BAIXA", "NORMAL", "ALTA", "URGENTE"};
    private static final String[] TIPO_DESPESA = {"MATERIAL", "SERVICO", "COMBUSTIVEL", "LOCACAO", "UTILIDADES",
            "TRIBUTOS", "MANUTENCAO", "OUTRA"};
    private static final String[] FORMA_PAGAMENTO = {"A_VISTA_ESPECIE", "PIX_TRANSFERENCIA", "BOLETO", "CARTAO_CREDITO",
            "CARTAO_DEBITO", "OUTRA"};

    private final ConsultaDadosService consultas;
    private final SemanticCatalog catalog;

    public DomainQueryPresets(ConsultaDadosService consultas, SemanticCatalog catalog) {
        this.consultas = consultas;
        this.catalog = catalog;
    }

    @Override
    public List<AgentTool> tools() {
        return presets().stream().map(preset -> (AgentTool) new DomainQueryTool(preset, consultas, catalog)).toList();
    }

    static List<Preset> presets() {
        return List.of(
                new Preset("consultar_estoque", "Consultar estoque", "Consultando o estoque",
                        "Saldo atual de materiais nos estoques (almoxarifados, equipes e obras): quanto existe de cada "
                                + "material, onde e em que condicao. Use para 'como esta o estoque', 'quanto temos de X', "
                                + "rankings de saldo por material/base/estoque. Retorna totais, agrupamentos ou lista.",
                        "saldos", false,
                        List.of(Campo.texto("material", "Descricao do material (contem)"),
                                Campo.exato("material_codigo", "Codigo exato do material"),
                                Campo.texto("estoque", "Nome do estoque (contem)"),
                                Campo.texto("tipo_estoque", "Tipo do estoque (contem)"),
                                Campo.texto("base_operacional", "Base operacional (contem)"),
                                Campo.texto("equipe", "Codigo da equipe (contem)"),
                                Campo.enumerado("condicao", "Condicao do material", "NOVO", "SUCATA"),
                                Campo.numero("quantidade", "Faixa de quantidade em saldo")),
                        List.of("material", "material_codigo", "unidade_medida", "estoque", "tipo_estoque",
                                "base_operacional", "equipe", "condicao"),
                        List.of(new MetricaPreset("quantidade_total", "soma", "quantidade", "soma das quantidades em saldo"),
                                new MetricaPreset("registros_saldo", "contagem", null, "linhas de saldo (material x estoque x condicao)"),
                                new MetricaPreset("materiais_distintos", "contagem_distinta", "material_id", "materiais diferentes"),
                                new MetricaPreset("estoques_distintos", "contagem_distinta", "estoque_id", "estoques diferentes")),
                        List.of("estoque", "tipo_estoque", "base_operacional", "equipe", "material_codigo", "material",
                                "unidade_medida", "condicao", "quantidade", "atualizado_em"),
                        "-quantidade"),

                new Preset("consultar_movimentacoes", "Consultar movimentações", "Consultando movimentações de estoque",
                        "Historico de movimentacoes de material (entradas, saidas, transferencias, entregas, devolucoes, "
                                + "estornos) com material, quantidade, origem, destino, obra e equipe. Use para volume "
                                + "movimentado, materiais mais movimentados, comparacao entre meses e materiais sem giro.",
                        "movimentacoes", true,
                        List.of(Campo.enumerado("tipo_movimentacao", "Tipo da movimentacao", "ENTRADA_AVULSA",
                                        "BAIXA_AVULSA", "TRANSFERENCIA", "ENTREGA", "ESTORNO", "APLICACAO", "RETIRADA", "DEVOLUCAO"),
                                Campo.texto("material", "Descricao do material (contem)"),
                                Campo.exato("material_codigo", "Codigo exato do material"),
                                Campo.texto("estoque_origem", "Estoque de origem (contem)"),
                                Campo.texto("estoque_destino", "Estoque de destino (contem)"),
                                Campo.texto("base_origem", "Base de origem (contem)"),
                                Campo.texto("base_destino", "Base de destino (contem)"),
                                Campo.texto("obra_numero", "Numero da obra (contem)"),
                                Campo.texto("equipe", "Codigo da equipe (contem)"),
                                Campo.enumerado("condicao", "Condicao do material", "NOVO", "SUCATA"),
                                Campo.enumerado("tipo_obra", "Tipo da obra", "OBRA", "CORRETIVA", "PREVENTIVA", "LPT"),
                                Campo.texto("operador", "Nome de quem registrou (contem)")),
                        List.of("material", "material_codigo", "unidade_medida", "tipo_movimentacao", "mes", "data",
                                "base_origem", "base_destino", "estoque_origem", "estoque_destino", "obra_numero",
                                "equipe", "condicao", "tipo_obra", "operador"),
                        List.of(new MetricaPreset("movimentacoes", "contagem", null, "linhas de movimentacao"),
                                new MetricaPreset("quantidade_total", "soma", "quantidade", "quantidade movimentada"),
                                new MetricaPreset("materiais_distintos", "contagem_distinta", "material_id", "materiais diferentes"),
                                new MetricaPreset("operacoes", "contagem_distinta", "operacao_id", "operacoes (documentos) distintas"),
                                new MetricaPreset("obras_distintas", "contagem_distinta", "obra_id", "obras diferentes")),
                        List.of("data_hora", "tipo_movimentacao", "material_codigo", "material", "quantidade",
                                "unidade_medida", "condicao", "estoque_origem", "estoque_destino", "obra_numero", "equipe",
                                "operador"),
                        "-data_hora"),

                new Preset("consultar_solicitacoes_material", "Consultar solicitações de material",
                        "Consultando solicitações de material",
                        "Solicitacoes de material ao almoxarifado (comerciais e tecnicas de obra): status, separacao, "
                                + "retirada prevista, atrasos, quantidades pedidas/entregues e tempos de aprovacao e entrega.",
                        "solicitacoes", true,
                        List.of(Campo.enumerado("contexto", "Contexto", "COMERCIAL", "TECNICO"),
                                Campo.enumerado("status", "Status", STATUS_SOLICITACAO),
                                Campo.enumerado("status_separacao", "Etapa de separacao", "AGUARDANDO_AGENDAMENTO",
                                        "PRONTO_PARA_SEPARACAO", "EM_SEPARACAO", "SEPARADO"),
                                Campo.enumerado("tipo_tecnico", "Tipo tecnico", TIPO_TECNICO),
                                Campo.texto("obra_numero", "Numero da obra (contem)"),
                                Campo.texto("base_origem", "Base de origem (contem)"),
                                Campo.texto("base_destino", "Base de destino (contem)"),
                                Campo.texto("estoque_destino", "Estoque de destino (contem)"),
                                Campo.texto("equipe_saque", "Equipe que retira (contem)"),
                                Campo.texto("requisitante", "Quem solicitou (contem)"),
                                Campo.booleano("retirada_atrasada", "Retirada prevista vencida sem entrega"),
                                Campo.booleano("fora_prazo_saque", "Pedido com menos de 48h de antecedencia")),
                        List.of("status", "contexto", "tipo_tecnico", "status_separacao", "mes", "data", "base_origem",
                                "base_destino", "estoque_destino", "obra_numero", "equipe_saque", "requisitante",
                                "motivo_rejeicao", "almoxarifado_atendimento", "retirada_atrasada"),
                        List.of(new MetricaPreset("solicitacoes", "contagem", null, "quantidade de solicitacoes"),
                                new MetricaPreset("itens", "soma", "total_itens", "itens pedidos"),
                                new MetricaPreset("quantidade_solicitada", "soma", "quantidade_solicitada", "quantidade pedida"),
                                new MetricaPreset("quantidade_entregue", "soma", "quantidade_entregue", "quantidade entregue"),
                                new MetricaPreset("horas_media_ate_decisao", "media", "horas_ate_decisao", "tempo medio ate aprovar/rejeitar"),
                                new MetricaPreset("horas_media_ate_entrega", "media", "horas_ate_entrega", "tempo medio ate entregar"),
                                new MetricaPreset("dias_medios_em_aberto", "media", "dias_desde_solicitacao", "idade media")),
                        List.of("data_solicitacao", "contexto", "tipo_tecnico", "status", "status_separacao", "obra_numero",
                                "base_origem", "estoque_destino", "equipe_saque", "requisitante", "data_prevista_retirada",
                                "retirada_atrasada", "total_itens", "quantidade_solicitada", "quantidade_entregue",
                                "reserva_numero", "solicitacao_id"),
                        "-data_solicitacao"),

                new Preset("consultar_reservas", "Consultar reservas", "Consultando reservas e prazos",
                        "Reservas das solicitacoes tecnicas de obra com prazo de retirada e situacao do SLA "
                                + "(ATRASADA, VENCE_EM_ATE_2_DIAS, NO_PRAZO...). Use para 'reservas perto de estourar o SLA', "
                                + "atrasos por obra/agente e fila de retiradas. No modo detalhado ordena pelo prazo mais proximo.",
                        "reservas", true,
                        List.of(Campo.enumerado("situacao_prazo", "Situacao do prazo", "ATRASADA", "VENCE_EM_ATE_2_DIAS",
                                        "NO_PRAZO", "SEM_DATA_PREVISTA", "ENTREGA_PARCIAL", "CONCLUIDA"),
                                Campo.enumerado("status_solicitacao", "Status da solicitacao", STATUS_SOLICITACAO),
                                Campo.enumerado("tipo_tecnico", "Tipo tecnico", TIPO_TECNICO),
                                Campo.texto("obra_numero", "Numero da obra (contem)"),
                                Campo.texto("agente_responsavel", "Agente responsavel (contem)"),
                                Campo.texto("equipe_saque", "Equipe que retira (contem)"),
                                Campo.texto("base_origem", "Base de origem (contem)"),
                                Campo.numero("dias_para_retirada", "Dias ate a retirada (negativo = atrasada)")),
                        List.of("situacao_prazo", "status_solicitacao", "obra_numero", "agente_responsavel",
                                "empresa_agente", "equipe_saque", "base_origem", "tipo_tecnico", "tipo_obra"),
                        List.of(new MetricaPreset("reservas", "contagem", null, "quantidade de reservas"),
                                new MetricaPreset("dias_medios_para_retirada", "media", "dias_para_retirada", "folga media"),
                                new MetricaPreset("dias_medios_desde_reserva", "media", "dias_desde_reserva", "idade media")),
                        List.of("reserva_numero", "situacao_prazo", "dias_para_retirada", "data_prevista_retirada",
                                "status_solicitacao", "status_separacao", "obra_numero", "equipe_saque",
                                "agente_responsavel", "base_origem", "data_reserva", "expiracao_entrega_parcial_em",
                                "solicitacao_id"),
                        "dias_para_retirada"),

                new Preset("consultar_obras", "Consultar obras", "Consultando obras e projetos",
                        "Obras e projetos: status, tipo, valor do projeto, prazos (inicio, previsao, fim), atraso, base, "
                                + "municipio, supervisor, coordenador e agente responsavel.",
                        "obras", true,
                        List.of(Campo.texto("obra_numero", "Numero da obra (contem)"),
                                Campo.texto("titulo", "Titulo (contem)"),
                                Campo.enumerado("tipo_obra", "Tipo", "OBRA", "CORRETIVA", "PREVENTIVA", "LPT"),
                                Campo.texto("status", "Status (contem)"),
                                Campo.booleano("atrasada", "Previsao de fim vencida sem conclusao"),
                                Campo.booleano("status_concluido", "Status concluido"),
                                Campo.texto("base_operacional", "Base (contem)"),
                                Campo.texto("municipio", "Municipio (contem)"),
                                Campo.texto("supervisor", "Supervisor (contem)"),
                                Campo.texto("coordenador", "Coordenador (contem)"),
                                Campo.texto("agente_responsavel", "Agente responsavel (contem)")),
                        List.of("status", "tipo_obra", "base_operacional", "municipio", "processo", "supervisor",
                                "coordenador", "agente_responsavel", "atrasada"),
                        List.of(new MetricaPreset("obras", "contagem", null, "quantidade de obras"),
                                new MetricaPreset("valor_total_projetos", "soma", "valor_projeto", "soma do valor dos projetos"),
                                new MetricaPreset("valor_medio_projeto", "media", "valor_projeto", "valor medio")),
                        List.of("obra_numero", "titulo", "tipo_obra", "status", "valor_projeto", "data_inicio",
                                "data_previsao_fim", "data_fim_efetiva", "atrasada", "base_operacional", "municipio",
                                "supervisor", "coordenador", "agente_responsavel", "obra_id"),
                        "data_previsao_fim"),

                new Preset("consultar_colaboradores", "Consultar colaboradores", "Consultando colaboradores e técnicos",
                        "Colaboradores e tecnicos: funcao, base, processo, equipe atual e data de contratacao. Nunca "
                                + "retorna CPF ou contatos.",
                        "colaboradores", false,
                        List.of(Campo.texto("nome", "Nome (contem)"), Campo.texto("funcao", "Funcao (contem)"),
                                Campo.texto("base_operacional", "Base (contem)"), Campo.texto("processo", "Processo (contem)"),
                                Campo.texto("equipe_atual", "Equipe atual (contem)"), Campo.booleano("ativo", "Somente ativos")),
                        List.of("funcao", "base_operacional", "processo", "equipe_atual", "ativo"),
                        List.of(new MetricaPreset("colaboradores", "contagem", null, "quantidade de colaboradores")),
                        List.of("nome", "funcao", "base_operacional", "processo", "equipe_atual", "data_contratacao", "ativo"),
                        "nome"),

                new Preset("consultar_descontos", "Consultar descontos", "Consultando descontos de colaboradores",
                        "Descontos em folha (perda, mau uso, dano de equipamentos): status do ciclo, motivo, colaborador, "
                                + "base, valores sugerido/validado/baixado e tempo de validacao.",
                        "descontos", true,
                        List.of(Campo.enumerado("status", "Status", "SOLICITADO", "VALIDADO", "BAIXADO", "REJEITADO"),
                                Campo.texto("motivo", "Motivo (contem)"), Campo.texto("colaborador", "Colaborador (contem)"),
                                Campo.texto("base_operacional", "Base (contem)"),
                                Campo.texto("funcao_colaborador", "Funcao do colaborador (contem)"),
                                Campo.texto("solicitante", "Quem solicitou (contem)")),
                        List.of("status", "motivo", "colaborador", "base_operacional", "funcao_colaborador", "mes", "solicitante"),
                        List.of(new MetricaPreset("descontos", "contagem", null, "quantidade de descontos"),
                                new MetricaPreset("valor_total", "soma", "valor_referencia", "valor mais atual do ciclo"),
                                new MetricaPreset("valor_baixado", "soma", "valor_baixado", "valor efetivamente descontado"),
                                new MetricaPreset("valor_validado", "soma", "valor_validado", "valor validado"),
                                new MetricaPreset("valor_sugerido", "soma", "valor_sugerido", "valor proposto"),
                                new MetricaPreset("dias_medios_ate_validacao", "media", "dias_ate_validacao", "tempo medio de validacao"),
                                new MetricaPreset("colaboradores_distintos", "contagem_distinta", "colaborador_id", "colaboradores afetados")),
                        List.of("data_ocorrencia", "colaborador", "funcao_colaborador", "base_operacional", "motivo",
                                "status", "quantidade_parcelas", "valor_sugerido", "valor_validado", "valor_baixado",
                                "solicitante", "total_itens", "desconto_id"),
                        "-data_ocorrencia"),

                financeiro("consultar_compras", "Consultar solicitações de compra", "Consultando solicitações de compra",
                        "Solicitacoes de compra: status de aprovacao, prioridade, tipo de despesa, fornecedor, base, "
                                + "processo, obra, valores solicitado/aprovado, vencimento e tempo ate a decisao.",
                        "solicitacoes_compra", List.of()),

                financeiro("consultar_pagamentos", "Consultar solicitações de pagamento",
                        "Consultando solicitações de pagamento",
                        "Solicitacoes de pagamento: status, prioridade, tipo de despesa, fornecedor, vencimento (dias para "
                                + "vencer), vinculo com compra previa, valores e tempo ate a decisao.",
                        "solicitacoes_pagamento",
                        List.of(Campo.enumerado("motivo_sem_compra", "Motivo de pagamento sem compra previa",
                                        "COMBUSTIVEL_FORNECEDOR_CADASTRADO", "DESPESA_MENSAL_RECORRENTE", "OUTRA"),
                                Campo.numero("dias_para_vencimento", "Dias ate o vencimento (negativo = vencido)"))),

                new Preset("consultar_chamados", "Consultar chamados", "Consultando chamados e SLA",
                        "Chamados de suporte: status, prioridade, topico, equipe de atendimento e SLA de primeira resposta "
                                + "e resolucao (vencido, horas para vencer). Quem nao gerencia chamados ve apenas os proprios.",
                        "chamados", true,
                        List.of(Campo.enumerado("status", "Status", "ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_SOLICITANTE",
                                        "RESOLVIDO", "FECHADO", "CANCELADO"),
                                Campo.enumerado("prioridade", "Prioridade", "BAIXA", "NORMAL", "ALTA", "CRITICA"),
                                Campo.texto("topico", "Topico (contem)"),
                                Campo.texto("equipe_atendimento", "Equipe de atendimento (contem)"),
                                Campo.booleano("sla_resolucao_vencido", "SLA de resolucao vencido"),
                                Campo.booleano("sla_primeira_resposta_vencido", "SLA de primeira resposta vencido"),
                                Campo.exato("protocolo", "Protocolo exato")),
                        List.of("status", "prioridade", "topico", "equipe_atendimento", "mes_abertura",
                                "sla_resolucao_vencido", "sla_primeira_resposta_vencido"),
                        List.of(new MetricaPreset("chamados", "contagem", null, "quantidade de chamados"),
                                new MetricaPreset("horas_media_resolucao", "media", "horas_ate_resolucao", "tempo medio de resolucao")),
                        List.of("protocolo", "assunto", "topico", "prioridade", "status", "data_abertura",
                                "data_limite_resolucao", "horas_para_vencer_resolucao", "sla_resolucao_vencido",
                                "sla_primeira_resposta_vencido", "equipe_atendimento"),
                        "-data_abertura"),

                new Preset("consultar_desvios_sst", "Consultar desvios de segurança", "Consultando desvios de segurança",
                        "Desvios (nao conformidades) encontrados nas inspecoes de seguranca: gravidade, status de "
                                + "tratamento, prazo vencido, item verificado, descricao, base, equipe, processo da inspecao, "
                                + "processo do inspetor e inspetor.",
                        "desvios", true,
                        List.of(Campo.enumerado("gravidade", "Gravidade", "BAIXA", "MEDIA", "ALTA", "CRITICA"),
                                Campo.enumerado("status", "Status", "ABERTO", "EM_TRATAMENTO", "RESOLVIDO"),
                                Campo.booleano("prazo_vencido", "Prazo de tratamento vencido"),
                                Campo.texto("base_operacional", "Base (contem)"), Campo.texto("equipe", "Equipe (contem)"),
                                Campo.texto("tipo_inspecao", "Tipo de inspecao (contem)"),
                                Campo.texto("processo", "Processo da inspecao (contem)"),
                                Campo.texto("processo_inspetor", "Processo do inspetor (contem)"),
                                Campo.texto("inspetor", "Inspetor (contem)"),
                                Campo.texto("item_verificado", "Item do checklist (contem)")),
                        List.of("gravidade", "status", "base_operacional", "equipe", "tipo_inspecao", "processo",
                                "processo_inspetor", "inspetor", "item_verificado", "mes", "prazo_vencido"),
                        List.of(new MetricaPreset("desvios", "contagem", null, "quantidade de desvios"),
                                new MetricaPreset("inspecoes", "contagem_distinta", "inspecao_id", "inspecoes com desvio")),
                        List.of("data_inspecao", "gravidade", "status", "prazo", "prazo_vencido", "item_verificado",
                                "descricao", "acao_corretiva", "base_operacional", "equipe", "processo", "processo_inspetor",
                                "inspetor"),
                        "-data_inspecao"),

                new Preset("consultar_eventos_sst", "Consultar eventos de segurança", "Consultando acidentes e incidentes",
                        "Eventos de seguranca (acidentes tipicos, de trajeto, incidentes, danos materiais, doencas "
                                + "ocupacionais): tipo, status, base, equipe, atividade, classificacao, lesao, dias perdidos "
                                + "e CAT. Eventos CANCELADOS nao entram nos indicadores oficiais.",
                        "eventos_sst", true,
                        List.of(Campo.enumerado("tipo", "Tipo", "ACIDENTE_TIPICO", "ACIDENTE_TRAJETO",
                                        "INCIDENTE_SEM_LESAO", "DANO_MATERIAL", "DOENCA_OCUPACIONAL"),
                                Campo.enumerado("status", "Status", "REGISTRADO", "EM_TRATATIVA", "CONCLUIDO", "CANCELADO"),
                                Campo.texto("base_operacional", "Base (contem)"), Campo.texto("equipe", "Equipe (contem)"),
                                Campo.texto("atividade", "Atividade (contem)"),
                                Campo.texto("classificacao", "Classificacao (contem)"),
                                Campo.booleano("possui_cat", "Houve CAT")),
                        List.of("tipo", "status", "base_operacional", "equipe", "atividade", "classificacao",
                                "natureza_lesao", "local_lesao", "agente_causador", "mes"),
                        List.of(new MetricaPreset("eventos", "contagem", null, "quantidade de eventos"),
                                new MetricaPreset("dias_perdidos", "soma", "dias_perdidos", "dias perdidos"),
                                new MetricaPreset("acidentados", "soma", "total_acidentados", "pessoas acidentadas")),
                        List.of("numero_registro", "data_ocorrencia", "tipo", "status", "atividade", "base_operacional",
                                "equipe", "classificacao", "natureza_lesao", "dias_perdidos", "possui_cat"),
                        "-data_ocorrencia"));
    }

    private static Preset financeiro(String nome, String rotulo, String progresso, String descricao, String fonte,
            List<Campo> extras) {
        List<Campo> filtros = new java.util.ArrayList<>(List.of(
                Campo.enumerado("status", "Status", STATUS_FINANCEIRO),
                Campo.enumerado("prioridade", "Prioridade", PRIORIDADE_FINANCEIRO),
                Campo.enumerado("tipo_despesa", "Tipo de despesa", TIPO_DESPESA),
                Campo.enumerado("forma_pagamento", "Forma de pagamento", FORMA_PAGAMENTO),
                Campo.texto("base_operacional", "Base (contem)"), Campo.texto("processo", "Processo (contem)"),
                Campo.texto("obra_numero", "Obra (contem)"), Campo.texto("fornecedor", "Fornecedor (contem)"),
                Campo.texto("solicitante", "Solicitante (contem)"), Campo.texto("descricao", "Descricao (contem)"),
                Campo.numero("valor", "Faixa de valor"), Campo.exato("numero", "Numero da solicitacao")));
        filtros.addAll(extras);
        return new Preset(nome, rotulo, progresso, descricao, fonte, true, filtros,
                List.of("status", "prioridade", "tipo_despesa", "forma_pagamento", "local_aquisicao", "base_operacional",
                        "processo", "obra_numero", "fornecedor", "solicitante", "mes", "natureza_servico",
                        "veiculo_placa", "formulario"),
                List.of(new MetricaPreset("solicitacoes", "contagem", null, "quantidade de solicitacoes"),
                        new MetricaPreset("valor_total", "soma", "valor", "valor solicitado"),
                        new MetricaPreset("valor_aprovado", "soma", "valor_aprovado", "valor aprovado"),
                        new MetricaPreset("valor_medio", "media", "valor", "valor medio"),
                        new MetricaPreset("horas_media_ate_decisao", "media", "horas_ate_decisao", "tempo medio ate a decisao")),
                List.of("numero", "data_submissao", "status", "prioridade", "tipo_despesa", "descricao", "fornecedor",
                        "base_operacional", "processo", "obra_numero", "valor", "valor_aprovado", "vencimento",
                        "solicitante", "horas_ate_decisao"),
                "-data_submissao");
    }
}
