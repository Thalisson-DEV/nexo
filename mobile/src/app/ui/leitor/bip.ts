/**
 * Regras do leitor no "modo bip": a câmera só decodifica a faixa da mira, aceita um código por
 * vez (o que está sobre a linha) e só "bipa" quando a leitura é confiável. Puras, para os testes.
 */

export interface Retangulo { x: number; y: number; width: number; height: number }
export interface CodigoLido { rawValue: string; format?: string; boundingBox?: Retangulo }

/**
 * Formatos com dígito verificador obrigatório (ou correção de erro): uma leitura basta.
 * Os demais (Code 39/93, Codabar, ITF) só valem confirmados em duas leituras iguais seguidas.
 */
export const FORMATOS_COM_VERIFICADOR = new Set(['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'qr_code', 'data_matrix', 'pdf417', 'aztec']);

/**
 * Onde a janela da mira (coordenadas da tela, relativas ao vídeo) cai no quadro da câmera, com o
 * vídeo em `object-fit: cover`. É o pedaço da imagem que vai para o decodificador.
 */
export function janelaNoQuadro(
  janela: Retangulo,
  tela: { largura: number; altura: number },
  quadro: { largura: number; altura: number },
): Retangulo {
  const escala = Math.max(tela.largura / quadro.largura, tela.altura / quadro.altura);
  const dx = (tela.largura - quadro.largura * escala) / 2;
  const dy = (tela.altura - quadro.altura * escala) / 2;
  const x = Math.max(0, (janela.x - dx) / escala);
  const y = Math.max(0, (janela.y - dy) / escala);
  const width = Math.min(quadro.largura - x, janela.width / escala);
  const height = Math.min(quadro.altura - y, janela.height / escala);
  return { x: Math.round(x), y: Math.round(y), width: Math.round(Math.max(0, width)), height: Math.round(Math.max(0, height)) };
}

/** Entre os códigos do recorte, o que está mais perto da linha (centro vertical) da mira. */
export function sobreALinha(codigos: readonly CodigoLido[], alturaRecorte: number): CodigoLido | null {
  let melhor: CodigoLido | null = null;
  let menor = Number.POSITIVE_INFINITY;
  for (const c of codigos) {
    if (!(c.rawValue ?? '').trim()) continue;
    const b = c.boundingBox;
    const d = b ? Math.abs(b.y + b.height / 2 - alturaRecorte / 2) : Number.MAX_SAFE_INTEGER;
    if (d < menor) { menor = d; melhor = c; }
  }
  return melhor;
}

export interface OpcoesEstabilizador {
  /** Depois de um bip, nada é aceito por este tempo (a pessoa vê o retorno e move a câmera). */
  travaMs: number;
  /** O mesmo código só bipa de novo depois de ficar este tempo fora da mira. */
  ausenciaMs: number;
  /** Janela para a segunda leitura igual nos formatos sem verificador. */
  confirmacaoMs: number;
}

export const OPCOES_PADRAO: OpcoesEstabilizador = { travaMs: 650, ausenciaMs: 1200, confirmacaoMs: 900 };

/**
 * Decide quando uma leitura vira bip: confirma formatos fracos, trava logo após cada bip e não
 * repete o mesmo código enquanto ele continua na mira.
 */
export class Estabilizador {
  private ultimo = { codigo: '', visto: Number.NEGATIVE_INFINITY };
  private travadoAte = Number.NEGATIVE_INFINITY;
  private candidato = { codigo: '', em: Number.NEGATIVE_INFINITY };

  constructor(private readonly opcoes: OpcoesEstabilizador = OPCOES_PADRAO) {}

  /** Código lido neste quadro (ou null). Devolve o código quando é hora de bipar. */
  considerar(lido: CodigoLido | null, agora: number, permitirRepetido = false): string | null {
    if (!lido) return null;
    const codigo = lido.rawValue.trim();
    if (!codigo) return null;
    const mesmo = codigo === this.ultimo.codigo;
    if (mesmo && !permitirRepetido && agora - this.ultimo.visto < this.opcoes.ausenciaMs) {
      this.ultimo.visto = agora; // continua na mira: espera sair
      return null;
    }
    if (agora < this.travadoAte) return null;
    if (!lido.format || !FORMATOS_COM_VERIFICADOR.has(lido.format)) {
      const confirmado = this.candidato.codigo === codigo && agora - this.candidato.em <= this.opcoes.confirmacaoMs;
      this.candidato = { codigo, em: agora };
      if (!confirmado) return null;
    }
    this.candidato = { codigo: '', em: Number.NEGATIVE_INFINITY };
    this.ultimo = { codigo, visto: agora };
    this.travadoAte = agora + this.opcoes.travaMs;
    return codigo;
  }

  reiniciar(): void {
    this.ultimo = { codigo: '', visto: Number.NEGATIVE_INFINITY };
    this.travadoAte = Number.NEGATIVE_INFINITY;
    this.candidato = { codigo: '', em: Number.NEGATIVE_INFINITY };
  }
}
