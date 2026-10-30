import {
  FormaPagamento,
  LocalAquisicao,
  MotivoSemCompra,
  MotivoServico,
  NaturezaServico,
  PrioridadeSolicitacao,
  RegimeVeiculo,
  StatusSolicitacao,
  TipoDespesaSolicitacao
} from '../../../core/models/financeiro.models';

export interface OpcaoFiltro<T> {
  label: string;
  value: T;
}

export const STATUS_OPTIONS: OpcaoFiltro<StatusSolicitacao>[] = [
  { label: 'Rascunho', value: 'RASCUNHO' },
  { label: 'Solicitado', value: 'SOLICITADO' },
  { label: 'Aprovado', value: 'APROVADO' },
  { label: 'Rejeitado', value: 'REJEITADO' },
  { label: 'Cancelado', value: 'CANCELADO' }
];

export const PRIORIDADE_OPTIONS: OpcaoFiltro<PrioridadeSolicitacao>[] = [
  { label: 'Baixa', value: 'BAIXA' },
  { label: 'Normal', value: 'NORMAL' },
  { label: 'Alta', value: 'ALTA' },
  { label: 'Urgente', value: 'URGENTE' }
];

export const LOCAL_AQUISICAO_OPTIONS: OpcaoFiltro<LocalAquisicao>[] = [
  { label: 'Local — própria base/obra', value: 'LOCAL' },
  { label: 'Central — matriz', value: 'CENTRAL' }
];

export const FORMA_PAGAMENTO_OPTIONS: OpcaoFiltro<FormaPagamento>[] = [
  { label: 'À vista em espécie', value: 'A_VISTA_ESPECIE' },
  { label: 'PIX / transferência', value: 'PIX_TRANSFERENCIA' },
  { label: 'Boleto', value: 'BOLETO' },
  { label: 'Cartão de crédito', value: 'CARTAO_CREDITO' },
  { label: 'Cartão de débito', value: 'CARTAO_DEBITO' },
  { label: 'Outra', value: 'OUTRA' }
];

export const REGIME_VEICULO_OPTIONS: OpcaoFiltro<RegimeVeiculo>[] = [
  { label: 'Próprio', value: 'PROPRIO' },
  { label: 'Locado', value: 'LOCADO' }
];

export const NATUREZA_SERVICO_OPTIONS: OpcaoFiltro<NaturezaServico>[] = [
  ['Compra e reforma de pneus', 'COMPRA_E_REFORMA_DE_PNEUS'],
  ['Suspensão e freios', 'SUSPENSAO_FREIOS'],
  ['Arrefecimento', 'ARREFECIMENTO'],
  ['Transmissão', 'TRANSMISSAO'],
  ['Elétrica', 'ELETRICA'],
  ['Chaparia e pintura', 'CHAPARIA_E_PINTURA'],
  ['Guincho', 'GUINCHO'],
  ['Hidráulica', 'HIDRAULICA'],
  ['Tapeçaria', 'TAPECARIA'],
  ['Lavagem e lubrificação', 'LAVAGEM_E_LUBRIFICACAO'],
  ['Acessórios', 'ACESSORIOS'],
  ['Bateria', 'BATERIA'],
  ['Borracharia', 'BORRACHARIA'],
  ['Mecânica', 'MECANICA'],
  ['Pneumática', 'PNEUMATICA'],
  ['Posto de molas', 'POSTO_DE_MOLAS'],
  ['Refrigeração e climatização', 'REFRIGERACAO_E_CLIMATIZACAO'],
  ['Tacógrafo', 'TACOGRAFO'],
  ['Torneadora', 'TORNEADORA']
].map(([label, value]) => ({ label, value: value as NaturezaServico }));

export const MOTIVO_SERVICO_OPTIONS: OpcaoFiltro<MotivoServico>[] = [
  { label: 'Desgaste natural', value: 'DESGASTE_NATURAL' },
  { label: 'Adequação de melhoria', value: 'ADEQUACAO_DE_MELHORIA' },
  { label: 'Problema mecânico', value: 'PROBLEMA_MECANICO' },
  { label: 'Mau uso / desconto', value: 'MAU_USO_DESCONTO' },
  { label: 'Outra', value: 'OUTRA' }
];

export const TIPO_DESPESA_OPTIONS: OpcaoFiltro<TipoDespesaSolicitacao>[] = [
  { label: 'Material', value: 'MATERIAL' },
  { label: 'Serviço', value: 'SERVICO' },
  { label: 'Combustível', value: 'COMBUSTIVEL' },
  { label: 'Locação', value: 'LOCACAO' },
  { label: 'Utilidades', value: 'UTILIDADES' },
  { label: 'Tributos', value: 'TRIBUTOS' },
  { label: 'Manutenção', value: 'MANUTENCAO' },
  { label: 'Outra', value: 'OUTRA' }
];

export const MOTIVO_SEM_COMPRA_OPTIONS: OpcaoFiltro<MotivoSemCompra>[] = [
  {
    label: 'Combustível — fornecedor cadastrado',
    value: 'COMBUSTIVEL_FORNECEDOR_CADASTRADO'
  },
  { label: 'Despesa mensal recorrente', value: 'DESPESA_MENSAL_RECORRENTE' },
  { label: 'Outra', value: 'OUTRA' }
];

export const BOOLEANO_OPTIONS: OpcaoFiltro<boolean>[] = [
  { label: 'Sim', value: true },
  { label: 'Não', value: false }
];

export const ATIVO_OPTIONS: OpcaoFiltro<boolean>[] = [
  { label: 'Ativos', value: true },
  { label: 'Inativos', value: false }
];
