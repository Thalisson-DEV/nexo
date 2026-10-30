export type GrupoRegiaoCorpo =
  | 'cabeca'
  | 'membrosSuperiores'
  | 'tronco'
  | 'membrosInferiores'
  | 'sistemas'
  | 'geral';

export type RegiaoCorpo =
  | 'cranio'
  | 'ouvido'
  | 'olho'
  | 'nariz'
  | 'boca'
  | 'mandibula'
  | 'face'
  | 'cabeca'
  | 'pescoco'
  | 'ombro'
  | 'braco'
  | 'bracoSuperior'
  | 'cotovelo'
  | 'antebraco'
  | 'punho'
  | 'mao'
  | 'dedoMao'
  | 'membrosSuperiores'
  | 'torax'
  | 'dorsoColuna'
  | 'abdome'
  | 'quadrilPelve'
  | 'tronco'
  | 'perna'
  | 'coxa'
  | 'joelho'
  | 'pernaInferior'
  | 'tornozelo'
  | 'pe'
  | 'dedoPe'
  | 'membrosInferiores'
  | 'corpoInteiro'
  | 'sistemas'
  | 'circulatorio'
  | 'respiratorio'
  | 'nervoso'
  | 'digestivo'
  | 'geniturinario'
  | 'musculoesqueletico'
  | 'naoEspecificado';

export interface PontoRegiaoCorpo {
  x: number;
  y: number;
  tamanho?: number;
}

export interface RegiaoCorpoConfig {
  id: RegiaoCorpo;
  rotulo: string;
  grupo: GrupoRegiaoCorpo;
  pontos: readonly PontoRegiaoCorpo[];
  icone?: string;
}

const simetricos = (
  y: number,
  frenteEsquerda: number,
  frenteDireita: number,
  costasEsquerda: number,
  costasDireita: number,
  tamanho?: number
): readonly PontoRegiaoCorpo[] => [
  { x: frenteEsquerda, y, tamanho },
  { x: frenteDireita, y, tamanho },
  { x: costasEsquerda, y, tamanho },
  { x: costasDireita, y, tamanho }
];

export const REGIOES_CORPO: readonly RegiaoCorpoConfig[] = [
  { id: 'cranio', rotulo: 'Crânio', grupo: 'cabeca', pontos: [{ x: 27.5, y: 6.2, tamanho: 24 }, { x: 72.5, y: 6.2, tamanho: 24 }] },
  { id: 'ouvido', rotulo: 'Ouvido', grupo: 'cabeca', pontos: [{ x: 21.6, y: 11.8, tamanho: 20 }, { x: 78.4, y: 11.8, tamanho: 20 }] },
  { id: 'olho', rotulo: 'Olho', grupo: 'cabeca', pontos: [{ x: 25.2, y: 10.5, tamanho: 20 }] },
  { id: 'nariz', rotulo: 'Nariz', grupo: 'cabeca', pontos: [{ x: 28, y: 12.1, tamanho: 20 }] },
  { id: 'boca', rotulo: 'Boca', grupo: 'cabeca', pontos: [{ x: 30.8, y: 14, tamanho: 20 }] },
  { id: 'mandibula', rotulo: 'Mandíbula', grupo: 'cabeca', pontos: [{ x: 31.8, y: 16.5, tamanho: 20 }] },
  { id: 'face', rotulo: 'Face', grupo: 'cabeca', pontos: [{ x: 22.2, y: 15.5, tamanho: 22 }] },
  { id: 'cabeca', rotulo: 'Cabeça', grupo: 'cabeca', pontos: [{ x: 72.5, y: 10, tamanho: 28 }] },
  { id: 'pescoco', rotulo: 'Pescoço', grupo: 'cabeca', pontos: [{ x: 27.5, y: 18 }, { x: 72.5, y: 18 }] },

  { id: 'ombro', rotulo: 'Ombro', grupo: 'membrosSuperiores', pontos: simetricos(21.8, 14.5, 40.5, 59.5, 86.5) },
  { id: 'braco', rotulo: 'Braço total', grupo: 'membrosSuperiores', pontos: simetricos(31, 11.2, 43.2, 57, 89, 32) },
  { id: 'bracoSuperior', rotulo: 'Braço acima do cotovelo', grupo: 'membrosSuperiores', pontos: simetricos(27.8, 11.8, 42.7, 57.8, 88.2) },
  { id: 'cotovelo', rotulo: 'Cotovelo', grupo: 'membrosSuperiores', pontos: simetricos(35, 9.4, 45, 56.2, 90.3) },
  { id: 'antebraco', rotulo: 'Antebraço', grupo: 'membrosSuperiores', pontos: simetricos(41, 8, 46.2, 55.2, 91.6) },
  { id: 'punho', rotulo: 'Punho', grupo: 'membrosSuperiores', pontos: simetricos(46.2, 7.1, 47.2, 54.8, 92.2, 22) },
  { id: 'mao', rotulo: 'Mão', grupo: 'membrosSuperiores', pontos: simetricos(49.5, 5.8, 47.2, 55.2, 93.2) },
  { id: 'dedoMao', rotulo: 'Dedo da mão', grupo: 'membrosSuperiores', pontos: simetricos(52.3, 5.2, 47.8, 55.2, 93.5, 22) },
  { id: 'membrosSuperiores', rotulo: 'Membros superiores', grupo: 'membrosSuperiores', pontos: simetricos(34, 10.4, 44.2, 56.5, 89.5, 34) },

  { id: 'torax', rotulo: 'Tórax', grupo: 'tronco', pontos: [{ x: 27.5, y: 28, tamanho: 34 }] },
  { id: 'dorsoColuna', rotulo: 'Dorso e coluna', grupo: 'tronco', pontos: [{ x: 72.5, y: 30.5, tamanho: 34 }] },
  { id: 'abdome', rotulo: 'Abdome', grupo: 'tronco', pontos: [{ x: 27.5, y: 36.5, tamanho: 34 }] },
  { id: 'quadrilPelve', rotulo: 'Quadris e pelve', grupo: 'tronco', pontos: [{ x: 27.5, y: 44.5, tamanho: 36 }, { x: 72.5, y: 44.5, tamanho: 36 }] },
  { id: 'tronco', rotulo: 'Tronco', grupo: 'tronco', pontos: [{ x: 27.5, y: 33.5, tamanho: 40 }, { x: 72.5, y: 33.5, tamanho: 40 }] },

  { id: 'perna', rotulo: 'Perna total', grupo: 'membrosInferiores', pontos: simetricos(68, 20.3, 34.7, 65.3, 79.7, 34) },
  { id: 'coxa', rotulo: 'Coxa', grupo: 'membrosInferiores', pontos: simetricos(55.5, 20.2, 34.8, 65.2, 79.8) },
  { id: 'joelho', rotulo: 'Joelho', grupo: 'membrosInferiores', pontos: simetricos(64, 19.5, 35.5, 64.5, 80.5) },
  { id: 'pernaInferior', rotulo: 'Perna abaixo do joelho', grupo: 'membrosInferiores', pontos: simetricos(72.8, 19.1, 35.9, 64.1, 80.9) },
  { id: 'tornozelo', rotulo: 'Tornozelo', grupo: 'membrosInferiores', pontos: simetricos(82.5, 18.3, 36.7, 63.3, 81.7, 22) },
  { id: 'pe', rotulo: 'Pé', grupo: 'membrosInferiores', pontos: simetricos(87.5, 16.8, 38.2, 61.8, 83.2) },
  { id: 'dedoPe', rotulo: 'Dedo do pé', grupo: 'membrosInferiores', pontos: [{ x: 15.2, y: 89.2, tamanho: 22 }, { x: 39.8, y: 89.2, tamanho: 22 }] },
  { id: 'membrosInferiores', rotulo: 'Membros inferiores', grupo: 'membrosInferiores', pontos: simetricos(66, 19.8, 35.2, 64.8, 80.2, 38) },

  { id: 'corpoInteiro', rotulo: 'Partes múltiplas do corpo', grupo: 'geral', pontos: [], icone: 'pi pi-users' },
  { id: 'sistemas', rotulo: 'Sistemas e aparelhos', grupo: 'sistemas', pontos: [], icone: 'pi pi-heart' },
  { id: 'circulatorio', rotulo: 'Aparelho circulatório', grupo: 'sistemas', pontos: [], icone: 'pi pi-heart-fill' },
  { id: 'respiratorio', rotulo: 'Aparelho respiratório', grupo: 'sistemas', pontos: [], icone: 'pi pi-cloud' },
  { id: 'nervoso', rotulo: 'Sistema nervoso', grupo: 'sistemas', pontos: [], icone: 'pi pi-bolt' },
  { id: 'digestivo', rotulo: 'Aparelho digestivo', grupo: 'sistemas', pontos: [], icone: 'pi pi-circle' },
  { id: 'geniturinario', rotulo: 'Aparelho geniturinário', grupo: 'sistemas', pontos: [], icone: 'pi pi-shield' },
  { id: 'musculoesqueletico', rotulo: 'Sistema musculoesquelético', grupo: 'sistemas', pontos: [], icone: 'pi pi-directions' },
  { id: 'naoEspecificado', rotulo: 'Local não especificado', grupo: 'geral', pontos: [], icone: 'pi pi-question-circle' }
] as const;

export const REGIOES_CORPO_POR_ID = new Map(REGIOES_CORPO.map((regiao) => [regiao.id, regiao]));

export const LOCAIS_LESAO_MIGRATION: readonly string[] = [
  'Crânio', 'Ouvido', 'Olho', 'Nariz', 'Boca', 'Mandíbula', 'Face - partes múltiplas',
  'Cabeça - partes múltiplas', 'Cabeça - não especificado', 'Braço - total',
  'Braço - acima do cotovelo', 'Pescoço', 'Cotovelo', 'Antebraço', 'Punho', 'Mão',
  'Dedo da mão', 'Membros superiores - partes múltiplas',
  'Membros superiores - não especificado', 'Ombro', 'Tórax', 'Dorso e coluna', 'Abdome',
  'Quadris e pelve', 'Tronco - partes múltiplas', 'Tronco - não especificado',
  'Perna - total', 'Coxa', 'Joelho', 'Perna - abaixo do joelho', 'Tornozelo', 'Pé',
  'Dedo do pé', 'Membros inferiores - partes múltiplas',
  'Membros inferiores - não especificado', 'Partes múltiplas do corpo',
  'Sistemas e aparelhos', 'Aparelho circulatório', 'Aparelho respiratório',
  'Sistema nervoso', 'Aparelho digestivo', 'Aparelho geniturinário',
  'Sistema musculoesquelético', 'Sistemas e aparelhos - não especificado',
  'Local da lesão - não especificado'
] as const;

export function normalizarNomeLocal(nome: string): string {
  return nome
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

const ASSOCIACOES_OFICIAIS: readonly (readonly [string, RegiaoCorpo])[] = [
  ['Crânio', 'cranio'], ['Ouvido', 'ouvido'], ['Olho', 'olho'], ['Nariz', 'nariz'],
  ['Boca', 'boca'], ['Mandíbula', 'mandibula'], ['Face - partes múltiplas', 'face'],
  ['Cabeça - partes múltiplas', 'cabeca'], ['Cabeça - não especificado', 'cabeca'],
  ['Braço - total', 'braco'], ['Braço - acima do cotovelo', 'bracoSuperior'],
  ['Pescoço', 'pescoco'], ['Cotovelo', 'cotovelo'], ['Antebraço', 'antebraco'],
  ['Punho', 'punho'], ['Mão', 'mao'], ['Dedo da mão', 'dedoMao'],
  ['Membros superiores - partes múltiplas', 'membrosSuperiores'],
  ['Membros superiores - não especificado', 'membrosSuperiores'], ['Ombro', 'ombro'],
  ['Tórax', 'torax'], ['Dorso e coluna', 'dorsoColuna'], ['Abdome', 'abdome'],
  ['Quadris e pelve', 'quadrilPelve'], ['Tronco - partes múltiplas', 'tronco'],
  ['Tronco - não especificado', 'tronco'], ['Perna - total', 'perna'], ['Coxa', 'coxa'],
  ['Joelho', 'joelho'], ['Perna - abaixo do joelho', 'pernaInferior'],
  ['Tornozelo', 'tornozelo'], ['Pé', 'pe'], ['Dedo do pé', 'dedoPe'],
  ['Membros inferiores - partes múltiplas', 'membrosInferiores'],
  ['Membros inferiores - não especificado', 'membrosInferiores'],
  ['Partes múltiplas do corpo', 'corpoInteiro'], ['Sistemas e aparelhos', 'sistemas'],
  ['Aparelho circulatório', 'circulatorio'], ['Aparelho respiratório', 'respiratorio'],
  ['Sistema nervoso', 'nervoso'], ['Aparelho digestivo', 'digestivo'],
  ['Aparelho geniturinário', 'geniturinario'],
  ['Sistema musculoesquelético', 'musculoesqueletico'],
  ['Sistemas e aparelhos - não especificado', 'sistemas'],
  ['Local da lesão - não especificado', 'naoEspecificado']
] as const;

const REGIAO_POR_NOME = new Map(
  ASSOCIACOES_OFICIAIS.map(([nome, regiao]) => [normalizarNomeLocal(nome), regiao])
);

/** Reconhece primeiro a carga oficial e depois nomes personalizados do catálogo. */
export function regiaoDoLocal(nome: string): RegiaoCorpo | null {
  const texto = normalizarNomeLocal(nome);
  const oficial = REGIAO_POR_NOME.get(texto);
  if (oficial) return oficial;

  if (/(local.*nao especificado|nao identificado)/.test(texto)) return 'naoEspecificado';
  if (/(musculoesquelet)/.test(texto)) return 'musculoesqueletico';
  if (/(geniturin|urinari)/.test(texto)) return 'geniturinario';
  if (/(circulator|cardiac|coracao)/.test(texto)) return 'circulatorio';
  if (/(respirator|pulmao)/.test(texto)) return 'respiratorio';
  if (/(digestiv|intestin|estomago)/.test(texto)) return 'digestivo';
  if (/(sistema nervoso|neurolog)/.test(texto)) return 'nervoso';
  if (/(sistemas? e aparelhos?)/.test(texto)) return 'sistemas';
  if (/(partes multiplas do corpo|corpo inteiro)/.test(texto)) return 'corpoInteiro';
  if (/(dedo.*mao|polegar|falange)/.test(texto)) return 'dedoMao';
  if (/(mao|palma)/.test(texto)) return 'mao';
  if (/punho/.test(texto)) return 'punho';
  if (/cotovelo/.test(texto)) return 'cotovelo';
  if (/antebraco/.test(texto)) return 'antebraco';
  if (/(braco.*acima|bicep|umero)/.test(texto)) return 'bracoSuperior';
  if (/(membro.*superior)/.test(texto)) return 'membrosSuperiores';
  if (/braco/.test(texto)) return 'braco';
  if (/(ombro|clavicula|escapula)/.test(texto)) return 'ombro';
  if (/(dedo.*pe|artelho|hallux)/.test(texto)) return 'dedoPe';
  if (/(tornozelo|calcanhar)/.test(texto)) return 'tornozelo';
  if (/(^pe$|\bpe\b)/.test(texto)) return 'pe';
  if (/joelho/.test(texto)) return 'joelho';
  if (/(abaixo.*joelho|canela|tibia|panturrilha)/.test(texto)) return 'pernaInferior';
  if (/(coxa|femur)/.test(texto)) return 'coxa';
  if (/(membro.*inferior)/.test(texto)) return 'membrosInferiores';
  if (/perna/.test(texto)) return 'perna';
  if (/(quadril|pelve|bacia|virilha|gluteo)/.test(texto)) return 'quadrilPelve';
  if (/(dorso|coluna|costas|lombar|medula)/.test(texto)) return 'dorsoColuna';
  if (/(abdome|abdominal|barriga|umbig)/.test(texto)) return 'abdome';
  if (/(torax|peito|costela)/.test(texto)) return 'torax';
  if (/tronco/.test(texto)) return 'tronco';
  if (/(pescoco|cervical|garganta)/.test(texto)) return 'pescoco';
  if (/(mandibula|queixo)/.test(texto)) return 'mandibula';
  if (/(ouvido|orelha|auditiv)/.test(texto)) return 'ouvido';
  if (/(olho|ocular|visao|nervo optico)/.test(texto)) return 'olho';
  if (/nariz|olfato/.test(texto)) return 'nariz';
  if (/(boca|labio|dente|lingua|paladar)/.test(texto)) return 'boca';
  if (/(face|rosto)/.test(texto)) return 'face';
  if (/(cranio|encefalo)/.test(texto)) return 'cranio';
  if (/(cabeca|couro cabeludo|testa|nuca)/.test(texto)) return 'cabeca';
  return null;
}
