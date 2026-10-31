import {
  KitAplicado,
  PlanejamentoObra,
  PontoPlanejamento,
  StatusItemPlanejado,
  VaoPlanejamento
} from '../../../core/models/obras-planejamento.models';

export type EstadoLocal = 'sem-kit' | 'pendente' | 'programado' | 'andamento' | 'concluido';

export const ROTULO_ESTADO: Record<EstadoLocal, string> = {
  'sem-kit': 'Sem kit',
  pendente: 'Pendente',
  programado: 'Programado',
  andamento: 'Em andamento',
  concluido: 'Concluído'
};

export const ROTULO_STATUS_ITEM: Record<StatusItemPlanejado, string> = {
  PENDENTE: 'Pendente',
  PROGRAMADO: 'Programado',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDO: 'Concluído',
  MEDIDO: 'Medido'
};

const COLECAO = new Intl.Collator('pt-BR', { numeric: true, sensitivity: 'base' });

export function compararNumero(a: string, b: string): number {
  return COLECAO.compare(a, b);
}

/** Próximo número sugerido: quem levanta os pontos em sequência só confirma com Enter. */
export function proximoNumero(pontos: readonly PontoPlanejamento[]): string {
  const maior = pontos.reduce((maximo, p) => {
    const numero = /^\d+$/.test(p.numero) ? Number(p.numero) : 0;
    return Math.max(maximo, numero);
  }, 0);
  return String(maior + 1);
}

export function kitsDoLocal(plano: PlanejamentoObra, idLocal: string): KitAplicado[] {
  return plano.kits.filter((k) => k.idPonto === idLocal || k.idVao === idLocal);
}

/** O estado mais avançado que ainda exige atenção resume o ponto ou vão na folha. */
export function estadoDoLocal(kits: readonly KitAplicado[]): EstadoLocal {
  const itens = kits.flatMap((k) => k.itens);
  if (!itens.length) return 'sem-kit';
  if (itens.some((i) => i.status === 'EM_ANDAMENTO')) return 'andamento';
  if (itens.some((i) => i.status === 'PROGRAMADO')) return 'programado';
  if (itens.some((i) => i.status === 'PENDENTE')) return 'pendente';
  return 'concluido';
}

export function kitBloqueado(kit: KitAplicado): boolean {
  return !kit.executadoAntes && kit.itens.some((i) => i.status !== 'PENDENTE');
}

export function rotuloVao(vao: VaoPlanejamento, pontos: ReadonlyMap<string, PontoPlanejamento>): string {
  return `P${pontos.get(vao.idPontoA)?.numero ?? '?'} — P${pontos.get(vao.idPontoB)?.numero ?? '?'}`;
}

export interface LinhaArvore {
  chave: string;
  tipo: 'pagina' | 'ponto' | 'kit' | 'item' | 'secao-vaos' | 'vao';
  titulo: string;
  detalhe?: string;
  nivel: number;
  idLocal?: string;
  idItem?: string;
  /** Quantidade em linhas de item; contagem nas demais. */
  etiqueta?: string;
  estado?: EstadoLocal;
  statusItem?: StatusItemPlanejado;
  ehMaterial?: boolean;
  recolhido?: boolean;
  selecionavel?: boolean;
  semKit?: boolean;
  pagina?: number;
}

export interface OpcoesArvore {
  busca: string;
  soServicos: boolean;
  /** Mostra só o que ainda falta planejar. */
  soSemKit?: boolean;
  recolhidos: ReadonlySet<string>;
}

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

export function formatarQuantidade(valor: number): string {
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
}

function linhasDoKit(
  kit: KitAplicado,
  idLocal: string,
  busca: string,
  opcoes: OpcoesArvore,
  localCombina: boolean
): LinhaArvore[] {
  const kitCombina = localCombina || normalizar(`${kit.codigo} ${kit.descricao}`).includes(busca);
  const itens = kit.itens
    .filter((i) => !opcoes.soServicos || i.tipo === 'SERVICO')
    .filter((i) => kitCombina || normalizar(`${i.codigo ?? ''} ${i.descricao ?? ''}`).includes(busca))
    .sort((a, b) => Number(a.tipo === 'MATERIAL') - Number(b.tipo === 'MATERIAL'));
  if (!kitCombina && !itens.length) return [];

  const chave = `kit:${kit.id}`;
  const recolhido = opcoes.recolhidos.has(chave);
  const linhas: LinhaArvore[] = [{
    chave,
    tipo: 'kit',
    titulo: kit.codigo,
    detalhe: kit.descricao,
    nivel: 1,
    idLocal,
    etiqueta: kit.executadoAntes ? 'Já executado' : kit.operacao === 'RETIRAR' ? 'Retirar' : undefined,
    recolhido
  }];
  if (recolhido && !busca) return linhas;
  for (const item of itens) {
    linhas.push({
      chave: `item:${item.id}`,
      tipo: 'item',
      titulo: item.descricao ?? item.codigo ?? 'Item',
      detalhe: item.codigo ?? undefined,
      nivel: 2,
      idLocal,
      idItem: item.id,
      etiqueta: `${formatarQuantidade(item.quantidadePrevista)} ${item.unidade ?? ''}`.trim(),
      statusItem: item.status,
      ehMaterial: item.tipo === 'MATERIAL',
      selecionavel: item.status === 'PENDENTE' && !kit.executadoAntes
    });
  }
  return linhas;
}

/**
 * Achata Página → Ponto → Kit → itens e a seção de vãos em linhas de altura fixa, para que a árvore
 * desenhe só o que está visível mesmo em obras grandes. A busca abre tudo o que combina.
 */
export function montarLinhas(plano: PlanejamentoObra, opcoes: OpcoesArvore): LinhaArvore[] {
  const busca = normalizar(opcoes.busca.trim());
  const pontosPorId = new Map(plano.pontos.map((p) => [p.id, p]));
  const kitsPorLocal = new Map<string, KitAplicado[]>();
  for (const kit of plano.kits) {
    const local = kit.idPonto ?? kit.idVao;
    if (local) kitsPorLocal.set(local, [...(kitsPorLocal.get(local) ?? []), kit]);
  }

  const linhas: LinhaArvore[] = [];
  const paginas = [...new Set(plano.pontos.map((p) => p.pagina))].sort((a, b) => a - b);
  for (const pagina of paginas) {
    const doLocal: LinhaArvore[] = [];
    const pontos = plano.pontos
      .filter((p) => p.pagina === pagina)
      .sort((a, b) => compararNumero(a.numero, b.numero));
    for (const ponto of pontos) {
      const kits = kitsPorLocal.get(ponto.id) ?? [];
      if (opcoes.soSemKit && kits.length) continue;
      const combina = !busca || normalizar(`p${ponto.numero} ${ponto.numero} ${ponto.barramento ?? ''}`).includes(busca);
      const filhos = kits.flatMap((k) => linhasDoKit(k, ponto.id, busca, opcoes, combina));
      if (!combina && !filhos.length) continue;
      const chave = `ponto:${ponto.id}`;
      const recolhido = opcoes.recolhidos.has(chave);
      doLocal.push({
        chave,
        tipo: 'ponto',
        titulo: `P${ponto.numero}`,
        detalhe: ponto.barramento ?? undefined,
        nivel: 0,
        idLocal: ponto.id,
        estado: estadoDoLocal(kits),
        etiqueta: kits.length ? `${kits.length} ${kits.length === 1 ? 'kit' : 'kits'}` : undefined,
        semKit: !kits.length,
        recolhido,
        selecionavel: true,
        pagina
      });
      if (!recolhido || busca) doLocal.push(...filhos);
    }
    if (doLocal.length) {
      linhas.push({
        chave: `pagina:${pagina}`,
        tipo: 'pagina',
        titulo: `Página ${pagina}`,
        etiqueta: String(pontos.length),
        nivel: 0,
        pagina
      });
      linhas.push(...doLocal);
    }
  }

  const doVao: LinhaArvore[] = [];
  for (const vao of plano.vaos) {
    const kits = kitsPorLocal.get(vao.id) ?? [];
    if (opcoes.soSemKit && kits.length) continue;
    const titulo = rotuloVao(vao, pontosPorId);
    const combina = !busca || normalizar(titulo).includes(busca);
    const filhos = kits.flatMap((k) => linhasDoKit(k, vao.id, busca, opcoes, combina));
    if (!combina && !filhos.length) continue;
    const chave = `vao:${vao.id}`;
    const recolhido = opcoes.recolhidos.has(chave);
    doVao.push({
      chave,
      tipo: 'vao',
      titulo,
      detalhe: vao.metragem ? `${formatarQuantidade(vao.metragem)} m` : 'sem metragem',
      nivel: 0,
      idLocal: vao.id,
      estado: estadoDoLocal(kits),
      etiqueta: kits.length ? `${kits.length} ${kits.length === 1 ? 'kit' : 'kits'}` : undefined,
      semKit: !kits.length,
      recolhido,
      selecionavel: true
    });
    if (!recolhido || busca) doVao.push(...filhos);
  }
  if (doVao.length) {
    linhas.push({ chave: 'secao-vaos', tipo: 'secao-vaos', titulo: 'Vãos', etiqueta: String(plano.vaos.length), nivel: 0 });
    linhas.push(...doVao);
  }
  return linhas;
}

/** Janela de linhas a desenhar para a rolagem atual, com folga para não piscar. */
export function janelaVisivel(total: number, alturaLinha: number, topo: number, alturaArea: number, folga = 8): { inicio: number; fim: number } {
  const inicio = Math.max(0, Math.floor(topo / alturaLinha) - folga);
  const fim = Math.min(total, Math.ceil((topo + alturaArea) / alturaLinha) + folga);
  return { inicio, fim };
}
