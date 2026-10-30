/**
 * Compressão das evidências fotográficas antes do upload.
 *
 * O backend aceita até 15 MB por arquivo, mas foto de celular moderno passa
 * fácil disso e o envio em 3G de campo fica insuportável. O alvo aqui é ~1–2 MB
 * mantendo a orientação EXIF do original.
 */

const DIMENSAO_MAXIMA = 1600;
const TAMANHO_ALVO_BYTES = 2 * 1024 * 1024;
const QUALIDADES = [0.82, 0.7, 0.6];

export interface EvidenciaPreparada {
  arquivo: Blob;
  nomeArquivo: string;
  contentType: string;
}

/**
 * Recomprime a foto para JPEG quando isso reduz o envio. Devolve o arquivo
 * original quando o navegador não consegue decodificar (HEIC do iPhone, por
 * exemplo — que o backend aceita sem conversão) ou quando comprimir não ajuda.
 */
export async function prepararEvidencia(arquivo: File): Promise<EvidenciaPreparada> {
  const original: EvidenciaPreparada = {
    arquivo,
    nomeArquivo: arquivo.name || `evidencia-${Date.now()}.jpg`,
    contentType: arquivo.type || 'image/jpeg',
  };

  const bitmap = await decodificar(arquivo);
  if (!bitmap) return original;

  try {
    const { largura, altura } = redimensionar(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = largura;
    canvas.height = altura;

    const ctx = canvas.getContext('2d');
    if (!ctx) return original;
    ctx.drawImage(bitmap as CanvasImageSource, 0, 0, largura, altura);

    let comprimido: Blob | null = null;
    for (const qualidade of QUALIDADES) {
      comprimido = await paraBlob(canvas, qualidade);
      if (comprimido && comprimido.size <= TAMANHO_ALVO_BYTES) break;
    }

    // Recomprimir nem sempre compensa (PNG pequeno, foto já otimizada).
    if (!comprimido || comprimido.size >= arquivo.size) return original;

    return {
      arquivo: comprimido,
      nomeArquivo: trocarExtensaoParaJpg(original.nomeArquivo),
      contentType: 'image/jpeg',
    };
  } finally {
    if (typeof ImageBitmap !== 'undefined' && bitmap instanceof ImageBitmap) bitmap.close();
  }
}

/**
 * `createImageBitmap` com `imageOrientation: 'from-image'` já aplica o EXIF.
 * O fallback por `<img>` também é orientado pelos navegadores atuais, que usam
 * `image-orientation: from-image` como padrão.
 */
async function decodificar(arquivo: File): Promise<ImageBitmap | HTMLImageElement | null> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(arquivo, { imageOrientation: 'from-image' });
    } catch {
      // Formato não decodificável pelo navegador (HEIC costuma cair aqui).
    }
  }

  return new Promise(resolve => {
    const url = URL.createObjectURL(arquivo);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    img.src = url;
  });
}

function redimensionar(largura: number, altura: number): { largura: number; altura: number } {
  const maior = Math.max(largura, altura);
  if (maior <= DIMENSAO_MAXIMA) return { largura, altura };
  const fator = DIMENSAO_MAXIMA / maior;
  return {
    largura: Math.max(1, Math.round(largura * fator)),
    altura: Math.max(1, Math.round(altura * fator)),
  };
}

function paraBlob(canvas: HTMLCanvasElement, qualidade: number): Promise<Blob | null> {
  return new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', qualidade));
}

function trocarExtensaoParaJpg(nome: string): string {
  return /\.[a-z0-9]+$/i.test(nome) ? nome.replace(/\.[a-z0-9]+$/i, '.jpg') : `${nome}.jpg`;
}

export function formatarTamanho(bytes: number | undefined): string {
  if (!bytes) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
