import { ExecucaoComercialResponse, OperacaoMaterial, OrigemMaterial } from '../../../core/models/execucao.models';

export interface LinhaAcao {
  id: string;
  codigo: string;
  nome: string;
  /** Entrou nesta alteração. */
  nova: boolean;
}

export interface LinhaMaterial {
  /** Identifica a linha na tabela do editor (não vai ao servidor). */
  chave: string;
  idMaterial: string;
  rotulo: string;
  unidade: string;
  quantidade: number;
  operacao: OperacaoMaterial;
  numSerie: string | null;
  origem: OrigemMaterial;
  nova: boolean;
  /** Série aplicada que não está no caminhão da equipe: vale, mas o saldo fica negativo. */
  foraDoSaldo?: boolean;
}

interface ItemDiferenca {
  rotulo: string;
  quantidade: number;
  unidade: string;
  operacao: OperacaoMaterial;
  numSerie: string | null;
}

export interface DiferencaNota {
  acoesAdicionadas: string[];
  acoesRemovidas: string[];
  materiaisAdicionados: ItemDiferenca[];
  materiaisRemovidos: ItemDiferenca[];
}

/** Materiais da nota como linhas do editor. */
export function materiaisDaNota(e: ExecucaoComercialResponse): LinhaMaterial[] {
  return e.materiais.map((m) => ({
    chave: m.id,
    idMaterial: m.material?.id ?? '',
    rotulo: m.material ? `${m.material.codigo} · ${m.material.descricao}` : 'Material removido do cadastro',
    unidade: m.material?.unidadeMedida ?? '',
    quantidade: Number(m.quantidade),
    operacao: m.operacao,
    numSerie: m.numSerie || null,
    origem: m.origem ?? 'EQUIPE',
    nova: false
  }));
}

/** Mesmo critério do servidor: material, operação, série e origem formam a linha. */
function chave(m: { idMaterial: string; operacao: OperacaoMaterial; numSerie: string | null; origem: OrigemMaterial }): string {
  return `${m.idMaterial}|${m.operacao}|${(m.numSerie ?? '').toUpperCase()}|${m.origem}`;
}

function agrupar(linhas: LinhaMaterial[]): Map<string, LinhaMaterial> {
  const grupos = new Map<string, LinhaMaterial>();
  for (const l of linhas) {
    const k = chave(l);
    const atual = grupos.get(k);
    grupos.set(k, atual ? { ...atual, quantidade: atual.quantidade + l.quantidade } : { ...l });
  }
  return grupos;
}

/** O que a alteração faz com a nota, comparando o editor com o que está gravado. */
export function diferencaDaNota(e: ExecucaoComercialResponse, acoes: LinhaAcao[], materiais: LinhaMaterial[]): DiferencaNota {
  const antes = new Map(e.acoes.filter((a) => a.acao).map((a) => [a.acao.id, a.acao.nome]));
  const depois = new Map(acoes.map((a) => [a.id, a.nome]));
  const acoesAdicionadas = [...depois].filter(([id]) => !antes.has(id)).map(([, nome]) => nome);
  const acoesRemovidas = [...antes].filter(([id]) => !depois.has(id)).map(([, nome]) => nome);

  const gravados = agrupar(materiaisDaNota(e));
  const editados = agrupar(materiais);
  const materiaisAdicionados: ItemDiferenca[] = [];
  const materiaisRemovidos: ItemDiferenca[] = [];
  const item = (l: LinhaMaterial, quantidade: number): ItemDiferenca => ({
    rotulo: l.rotulo, quantidade, unidade: l.unidade, operacao: l.operacao, numSerie: l.numSerie
  });
  for (const [k, g] of gravados) {
    const nova = editados.get(k)?.quantidade ?? 0;
    if (nova < g.quantidade) materiaisRemovidos.push(item(g, arredondar(g.quantidade - nova)));
  }
  for (const [k, n] of editados) {
    const anterior = gravados.get(k)?.quantidade ?? 0;
    if (n.quantidade > anterior) materiaisAdicionados.push(item(n, arredondar(n.quantidade - anterior)));
  }
  return { acoesAdicionadas, acoesRemovidas, materiaisAdicionados, materiaisRemovidos };
}

/** Frases curtas para a caixa "O que muda na nota". */
export function resumoDaDiferenca(d: DiferencaNota): string[] {
  const material = (m: ItemDiferenca) =>
    `${m.rotulo}${m.numSerie ? ` (série ${m.numSerie})` : ''}: ${quantidade(m.quantidade)}${m.unidade ? ' ' + m.unidade : ''} ${m.operacao === 'APLICADO' ? 'aplicado' : 'retirado'}`;
  return [
    ...d.acoesAdicionadas.map((nome) => `Entra o serviço ${nome}`),
    ...d.acoesRemovidas.map((nome) => `Sai o serviço ${nome}`),
    ...d.materiaisAdicionados.map((m) => `Entra ${material(m)}`),
    ...d.materiaisRemovidos.map((m) => `Sai ${material(m)}`)
  ];
}

export function quantidade(n: number): string {
  return n.toLocaleString('pt-BR', { maximumFractionDigits: 3 });
}

function arredondar(n: number): number {
  return Math.round(n * 1000) / 1000;
}
