import { PermissaoResponse } from '../../../core/models/iam.models';

/**
 * Tradução das permissões técnicas (`modulo:recurso:acao`) para a linguagem de quem administra
 * acessos: módulo → tela/cadastro → ações ("Ver", "Editar", "Aprovar e reprovar"...).
 * Permissão nova sem entrada aqui continua aparecendo, com o nome derivado do código.
 */

export type TipoAcao = 'ver' | 'editar' | 'especial';

export interface AcaoPermissao {
  readonly permissao: PermissaoResponse;
  readonly rotulo: string;
  readonly tipo: TipoAcao;
  /** Explica o que a ação libera, quando não é óbvio. */
  readonly ajuda?: string;
}

export interface RecursoPermissao {
  readonly chave: string;
  readonly rotulo: string;
  readonly descricao?: string;
  readonly acoes: readonly AcaoPermissao[];
}

export interface ModuloPermissao {
  readonly chave: string;
  readonly rotulo: string;
  readonly icone: string;
  readonly recursos: readonly RecursoPermissao[];
  /** IDs de todas as permissões do módulo. */
  readonly ids: readonly string[];
}

const MODULOS: Record<string, { rotulo: string; icone: string; ordem: number }> = {
  estoque: { rotulo: 'Almoxarifado', icone: 'pi pi-box', ordem: 1 },
  execucao: { rotulo: 'Execução comercial', icone: 'pi pi-bolt', ordem: 2 },
  contrato: { rotulo: 'Contratos', icone: 'pi pi-file', ordem: 3 },
  obras: { rotulo: 'Obras', icone: 'pi pi-building', ordem: 4 },
  rh: { rotulo: 'Pessoas e equipes', icone: 'pi pi-users', ordem: 5 },
  frota: { rotulo: 'Frota', icone: 'pi pi-car', ordem: 6 },
  sst: { rotulo: 'Segurança do trabalho (SST)', icone: 'pi pi-shield', ordem: 7 },
  desconto: { rotulo: 'Descontos', icone: 'pi pi-percentage', ordem: 8 },
  financeiro: { rotulo: 'Financeiro', icone: 'pi pi-wallet', ordem: 9 },
  chamado: { rotulo: 'Chamados', icone: 'pi pi-ticket', ordem: 10 },
  notificacao: { rotulo: 'Comunicados e notificações', icone: 'pi pi-megaphone', ordem: 11 },
  assistente: { rotulo: 'Copiloto (assistente)', icone: 'pi pi-comments', ordem: 12 },
  sincronizacao: { rotulo: 'Sincronização do aplicativo', icone: 'pi pi-sync', ordem: 13 },
  iam: { rotulo: 'Acessos e usuários', icone: 'pi pi-lock', ordem: 14 }
};

/** Telas e cadastros, por `modulo:recurso`. `modulo:` (vazio) é o módulo como um todo. */
const RECURSOS: Record<string, { rotulo: string; descricao?: string }> = {
  'estoque:solicitacao': { rotulo: 'Solicitações de material', descricao: 'Listas, kanban, calendário, relatórios e painel de solicitações.' },
  'estoque:movimentacao': { rotulo: 'Movimentações de estoque', descricao: 'Entradas, baixas, transferências e seus comprovantes.' },
  'estoque:saldo': { rotulo: 'Saldos de estoque', descricao: 'Relatório de saldo e histórico de cada material.' },
  'estoque:estoque': { rotulo: 'Estoques' },
  'estoque:material': { rotulo: 'Materiais' },
  'estoque:tipo-estoque': { rotulo: 'Tipos de estoque' },
  'estoque:avaliacao-material': { rotulo: 'Avaliações de material' },
  'estoque:centro-investimento': { rotulo: 'Centros de investimento' },
  'estoque:motivo-rejeicao': { rotulo: 'Motivos de rejeição e pendência', descricao: 'Também é usado para escolher o motivo ao rejeitar ou registrar pendência.' },
  'execucao:comercial': { rotulo: 'Notas, turnos e painéis de execução' },
  'execucao:tipo-servico': { rotulo: 'Tipos de serviço' },
  'contrato:contrato': { rotulo: 'Contratos' },
  'contrato:acao': { rotulo: 'Ações do contrato' },
  'contrato:acao-valor': { rotulo: 'Valores das ações' },
  'obras:obra': { rotulo: 'Obras' },
  'obras:cadastros': { rotulo: 'Cadastros de obras' },
  'obras:anexos': { rotulo: 'Anexos das obras' },
  'rh:colaborador': { rotulo: 'Colaboradores' },
  'rh:equipe': { rotulo: 'Equipes' },
  'rh:base-operacional': { rotulo: 'Bases operacionais' },
  'rh:centro-faturamento': { rotulo: 'Centros de faturamento' },
  'rh:processo-negocio': { rotulo: 'Processos de negócio' },
  'rh:municipio': { rotulo: 'Municípios' },
  'rh:funcao': { rotulo: 'Funções' },
  'rh:veiculo': { rotulo: 'Veículos (cadastro de equipes)' },
  'rh:tipo-equipe': { rotulo: 'Tipos de equipe' },
  'rh:coordenador': { rotulo: 'Coordenadores' },
  'rh:supervisor': { rotulo: 'Supervisores' },
  'frota:veiculo': { rotulo: 'Veículos e implementos' },
  'frota:condutor': { rotulo: 'Condutores' },
  'frota:documento': { rotulo: 'Documentos da frota' },
  'frota:odometro': { rotulo: 'Odômetro' },
  'frota:configuracao': { rotulo: 'Configurações da frota' },
  'sst:inspecao': { rotulo: 'Inspeções' },
  'sst:formulario': { rotulo: 'Formulários de inspeção' },
  'sst:meta': { rotulo: 'Metas de inspeção' },
  'rh:meta-faturamento': { rotulo: 'Metas de faturamento das equipes' },
  'sst:evento': { rotulo: 'Eventos (acidentes e incidentes)' },
  'sst:catalogo': { rotulo: 'Catálogos de SST' },
  'sst:governanca': { rotulo: 'Governança (reuniões, atas, CIPA)' },
  'sst:dashboard': { rotulo: 'Painel de SST' },
  'sst:desvio': { rotulo: 'Desvios' },
  'sst:documento': { rotulo: 'Documentos de SST' },
  'sst:ata': { rotulo: 'Atas' },
  'sst:treinamento': { rotulo: 'Treinamentos' },
  'desconto:': { rotulo: 'Descontos' },
  'desconto:motivo': { rotulo: 'Motivos de desconto' },
  'desconto:tipo-equipamento': { rotulo: 'Tipos de equipamento' },
  'desconto:dashboard': { rotulo: 'Painel de descontos' },
  'financeiro:compra': { rotulo: 'Solicitações de compra' },
  'financeiro:pagamento': { rotulo: 'Solicitações de pagamento' },
  'financeiro:fornecedor': { rotulo: 'Fornecedores' },
  'financeiro:documento': { rotulo: 'Repositório de documentos' },
  'financeiro:formulario': { rotulo: 'Formulários financeiros' },
  'financeiro:alcada': { rotulo: 'Alçadas de aprovação' },
  'financeiro:dashboard': { rotulo: 'Painel financeiro' },
  'chamado:': { rotulo: 'Chamados' },
  'chamado:dashboard': { rotulo: 'Painel de chamados' },
  'notificacao:comunicado': { rotulo: 'Mural de comunicados' },
  'notificacao:regra': { rotulo: 'Regras de notificação' },
  'assistente:chat': { rotulo: 'Conversar com o Copiloto' },
  'sincronizacao:': { rotulo: 'Sincronização' },
  'iam:usuario': { rotulo: 'Usuários' },
  'iam:perfil': { rotulo: 'Perfis de acesso' },
  'iam:permissao': { rotulo: 'Catálogo de permissões', descricao: 'Cadastro técnico das permissões. Raramente necessário.' },
  'iam:audit': { rotulo: 'Histórico de alterações (auditoria)' }
};

/** Ação → rótulo. A ordem define a posição na linha. */
const ACOES: Record<string, { rotulo: string; tipo: TipoAcao; ordem: number }> = {
  read: { rotulo: 'Ver', tipo: 'ver', ordem: 0 },
  use: { rotulo: 'Usar', tipo: 'ver', ordem: 0 },
  write: { rotulo: 'Editar', tipo: 'editar', ordem: 1 },
  manage: { rotulo: 'Gerenciar', tipo: 'editar', ordem: 1 },
  create: { rotulo: 'Criar', tipo: 'especial', ordem: 2 },
  solicitar: { rotulo: 'Solicitar', tipo: 'especial', ordem: 2 },
  editar: { rotulo: 'Editar', tipo: 'editar', ordem: 1 },
  import: { rotulo: 'Importar planilha', tipo: 'especial', ordem: 3 },
  export: { rotulo: 'Exportar', tipo: 'especial', ordem: 3 },
  approve: { rotulo: 'Aprovar e reprovar', tipo: 'especial', ordem: 4 },
  aprovar: { rotulo: 'Aprovar', tipo: 'especial', ordem: 4 },
  validar: { rotulo: 'Validar', tipo: 'especial', ordem: 4 },
  rejeitar: { rotulo: 'Rejeitar', tipo: 'especial', ordem: 5 },
  plan: { rotulo: 'Planejar', tipo: 'especial', ordem: 5 },
  reserve: { rotulo: 'Reservar', tipo: 'especial', ordem: 6 },
  separate: { rotulo: 'Separar', tipo: 'especial', ordem: 7 },
  deliver: { rotulo: 'Entregar', tipo: 'especial', ordem: 8 },
  return: { rotulo: 'Confirmar devolução', tipo: 'especial', ordem: 9 },
  reverse: { rotulo: 'Estornar', tipo: 'especial', ordem: 10 },
  close: { rotulo: 'Encerrar saldo', tipo: 'especial', ordem: 11 },
  'audit:read': { rotulo: 'Ver histórico', tipo: 'especial', ordem: 12 },
  anexar: { rotulo: 'Anexar', tipo: 'especial', ordem: 6 },
  baixar: { rotulo: 'Dar baixa', tipo: 'especial', ordem: 7 },
  conferir: { rotulo: 'Conferir', tipo: 'especial', ordem: 6 },
  gerir: { rotulo: 'Gerir', tipo: 'especial', ordem: 6 },
  homologar: { rotulo: 'Homologar', tipo: 'especial', ordem: 6 },
  administrar: { rotulo: 'Administrar', tipo: 'especial', ordem: 7 },
  anular: { rotulo: 'Anular leitura', tipo: 'especial', ordem: 6 },
  tratar: { rotulo: 'Tratar', tipo: 'especial', ordem: 6 },
  generate: { rotulo: 'Gerar', tipo: 'especial', ordem: 6 }
};

/** Quando a ação genérica não basta para entender o efeito. */
const AJUDAS: Record<string, { rotulo?: string; ajuda: string }> = {
  'estoque:solicitacao:read': { ajuda: 'Consulta solicitações, kanban, calendário, relatórios e painel.' },
  'estoque:solicitacao:write': {
    rotulo: 'Operar',
    ajuda: 'Almoxarifado: cria, rejeita a já aprovada, registra pendências, entrega e estorna. Não aprova.'
  },
  'estoque:solicitacao:approve': { ajuda: 'Aprova a solicitação pendente (podendo editar os itens) ou a reprova.' },
  'estoque:solicitacao:plan': { ajuda: 'Solicitações técnicas: planeja data e equipe e pode rejeitar a planejada.' },
  'iam:perfil:write': { ajuda: 'Pode alterar perfis, inclusive estas permissões.' },
  'iam:usuario:write': { ajuda: 'Pode criar usuários e trocar o perfil de alguém.' }
};

function humanizar(codigo: string): string {
  const texto = codigo.replace(/[-_]/g, ' ').trim();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

interface Decomposta {
  modulo: string;
  recurso: string;
  acao: string;
}

/** `estoque:solicitacao:audit:read` → estoque / solicitacao / audit:read; `desconto:validar` → desconto / (geral) / validar. */
export function decompor(nome: string): Decomposta {
  const partes = nome.toLowerCase().split(':');
  if (partes.length >= 3) return { modulo: partes[0], recurso: partes[1], acao: partes.slice(2).join(':') };
  if (partes.length === 2) {
    return ACOES[partes[1]] ? { modulo: partes[0], recurso: '', acao: partes[1] } : { modulo: partes[0], recurso: partes[1], acao: 'use' };
  }
  return { modulo: 'outros', recurso: partes[0], acao: 'use' };
}

export function descreverAcao(permissao: PermissaoResponse): AcaoPermissao {
  const { acao } = decompor(permissao.nome);
  const base = ACOES[acao] ?? { rotulo: humanizar(acao.replace(':', ' ')), tipo: 'especial' as TipoAcao, ordem: 50 };
  const ajuda = AJUDAS[permissao.nome.toLowerCase()];
  return { permissao, rotulo: ajuda?.rotulo ?? base.rotulo, tipo: base.tipo, ajuda: ajuda?.ajuda };
}

function ordemAcao(a: AcaoPermissao): number {
  return ACOES[decompor(a.permissao.nome).acao]?.ordem ?? 50;
}

/** Monta módulos → recursos → ações, em ordem estável e com nomes legíveis. */
export function agruparPermissoes(permissoes: readonly PermissaoResponse[]): ModuloPermissao[] {
  const modulos = new Map<string, Map<string, AcaoPermissao[]>>();
  for (const p of permissoes) {
    const { modulo, recurso } = decompor(p.nome);
    const recursos = modulos.get(modulo) ?? new Map<string, AcaoPermissao[]>();
    const acoes = recursos.get(recurso) ?? [];
    acoes.push(descreverAcao(p));
    recursos.set(recurso, acoes);
    modulos.set(modulo, recursos);
  }

  return [...modulos.entries()]
    .map(([chave, recursos]) => {
      const info = MODULOS[chave] ?? { rotulo: humanizar(chave), icone: 'pi pi-th-large', ordem: 99 };
      const lista: RecursoPermissao[] = [...recursos.entries()]
        .map(([recurso, acoes]) => {
          const meta = RECURSOS[`${chave}:${recurso}`];
          return {
            chave: `${chave}:${recurso}`,
            rotulo: meta?.rotulo ?? (recurso ? humanizar(recurso) : info.rotulo),
            descricao: meta?.descricao,
            acoes: [...acoes].sort((a, b) => ordemAcao(a) - ordemAcao(b) || a.rotulo.localeCompare(b.rotulo, 'pt-BR'))
          };
        })
        .sort((a, b) => a.rotulo.localeCompare(b.rotulo, 'pt-BR'));
      return {
        chave,
        rotulo: info.rotulo,
        icone: info.icone,
        ordem: info.ordem,
        recursos: lista,
        ids: lista.flatMap((r) => r.acoes.map((a) => a.permissao.id))
      };
    })
    .sort((a, b) => a.ordem - b.ordem || a.rotulo.localeCompare(b.rotulo, 'pt-BR'))
    .map(({ ordem: _ordem, ...modulo }) => modulo);
}

/** Texto usado na busca: rótulos legíveis e o código técnico, sem acento nem caixa. */
export function textoBusca(modulo: ModuloPermissao, recurso: RecursoPermissao): string {
  return normalizar([modulo.rotulo, recurso.rotulo, recurso.descricao ?? '', ...recurso.acoes.flatMap((a) => [a.rotulo, a.permissao.nome])].join(' '));
}

export function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
