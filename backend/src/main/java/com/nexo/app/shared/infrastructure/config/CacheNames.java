package com.nexo.app.shared.infrastructure.config;

/**
 * Centraliza os nomes dos caches para evitar erros de digitação (Magic Strings).
 */
public final class CacheNames {
    public static final String LOOKUP_BOOTSTRAP = "lookup-bootstrap-v1";
    // Dados de Referência (Longa Duração - 24h+)
    public static final String BASES_OPERACIONAIS = "bases-operacionais";
    public static final String CENTROS_FATURAMENTO = "centros-faturamento";
    public static final String FUNCOES = "funcoes";
    public static final String MUNICIPIOS = "municipios";
    public static final String PROCESSOS_NEGOCIO = "processos-negocio";
    public static final String TIPOS_EQUIPE = "tipos-equipe";
    public static final String PERFIS_ACESSO = "perfis-acesso";
    public static final String ACOES = "acoes";
    public static final String ACOES_VALORES = "acoes-valores";
    public static final String CBF = "contratos-base-faturamento";
    public static final String PERMISSOES = "permissoes";
    // A versao impede que uma lista de authorities anterior a uma migration seja reutilizada no login.
    public static final String PERMISSOES_PERFIL = "permissoes-perfil-v5";

    // Estoque e Materiais
    public static final String TIPOS_ESTOQUE = "tipos-estoque";
    public static final String AVALIACOES_MATERIAL = "avaliacoes-material";
    public static final String CENTROS_INVESTIMENTO = "centros-investimento";
    public static final String MATERIAIS = "materiais";
    public static final String ESTOQUES = "estoques";
    // A versão evita reaproveitar saldos serializados antes das datas de movimentação.
    public static final String SALDOS = "saldos-v3";
    public static final String MOTIVOS_REJEICAO = "motivos-rejeicao";

    // Dados Transacionais (Curta Duração - 30min a 1h)
    public static final String USUARIOS = "usuarios";
    public static final String COLABORADORES = "colaboradores";
    public static final String COORDENADORES = "coordenadores";
    public static final String SUPERVISORES = "supervisores";
    public static final String EQUIPES = "equipes";
    public static final String EQUIPES_MEMBROS = "equipes-membros";
    public static final String METAS_FATURAMENTO = "metas-faturamento";
    public static final String VEICULOS = "veiculos";
    // A versão evita reaproveitar execuções serializadas antes do total de alterações (V191).
    public static final String EXECUCOES = "execucoes-v3";
    public static final String TIPOS_SERVICO = "tipos-servico";

    // Saúde e Segurança do Trabalho (SST)
    // A versao evita reutilizar formularios serializados antes do backfill de gravidades da V137.
    public static final String FORMULARIOS_INSPECAO = "formularios-inspecao-v2";
    public static final String INSPECOES_SEGURANCA = "inspecoes-seguranca";
    public static final String METAS_INSPECAO = "metas-inspecao";
    // Vocabulários e atividades da governança de eventos: mudam pouco e são lidos
    // a cada abertura do formulário de registro.
    public static final String SST_CATALOGOS = "sst-catalogos";
    public static final String SST_ATIVIDADES = "sst-atividades";
    public static final String SST_GOVERNANCA_CORPOS = "sst-governanca-corpos";
    public static final String SST_GOVERNANCA_PERFIS = "sst-governanca-perfis";

    // Financeiro — configuração (muda pouco, é lida em toda abertura de formulário)
    public static final String FINANCEIRO_FORNECEDORES = "financeiro-fornecedores";
    public static final String FINANCEIRO_FORMULARIOS = "financeiro-formularios";

    // Central de chamados — catálogo estável exibido no formulário de abertura
    // Versao no nome evita que contratos JSON antigos sejam reutilizados apos evolucoes dos DTOs.
    public static final String CHAMADO_TOPICOS = "chamado-topicos-v2";
    public static final String CHAMADO_EQUIPES = "chamado-equipes-v2";

    // Notificações — preferências do destinatário, lidas uma vez por usuário a cada
    // publicação. A caixa de entrada em si não é cacheada (muda o tempo todo).
    public static final String NOTIFICACAO_PREFERENCIAS = "notificacao-preferencias";

    // Gestão de Obras e Projetos
    public static final String OBRAS = "obras";
    public static final String OBRAS_STATUS = "obras-status";
    public static final String OBRAS_AGENTES = "obras-agentes";
    public static final String OBRAS_TIPOS_ANEXOS = "obras-tipos-anexos";
    public static final String OBRAS_KITS = "obras-kits";
    public static final String OBRAS_ROTAS = "obras-rotas";

    private CacheNames() {}
}
