import { CodigoLido } from './bip';

/** Um decodificador de códigos sobre uma imagem (o recorte da mira). */
export interface Motor {
  nome: 'nativo' | 'zxing';
  detect(fonte: CanvasImageSource): Promise<CodigoLido[]>;
}

interface ConstrutorDetector {
  new (opts?: { formats?: string[] }): { detect(fonte: CanvasImageSource): Promise<CodigoLido[]> };
  getSupportedFormats?(): Promise<string[]>;
}

export const FORMATOS = ['code_128', 'code_39', 'code_93', 'codabar', 'ean_13', 'ean_8', 'itf', 'upc_a', 'upc_e', 'qr_code', 'data_matrix', 'pdf417', 'aztec'];

/** Onde o app publica o WebAssembly do ZXing (copiado de node_modules no build). */
const CAMINHO_WASM = 'zxing/';

let zxing: Promise<Motor | null> | null = null;

/** ZXing-C++ em WebAssembly: roda em qualquer navegador (inclusive iPhone) e é forte em 1D. */
function criarZxing(): Promise<Motor | null> {
  zxing ??= (async () => {
    try {
      const { BarcodeDetector, prepareZXingModule } = await import('barcode-detector/ponyfill');
      // Carrega o WebAssembly já: se falhar (rede, 404), cai no catch e o leitor segue com o nativo.
      await prepareZXingModule({
        overrides: {
          locateFile: (caminho: string, prefixo: string) =>
            caminho.endsWith('.wasm') ? new URL(CAMINHO_WASM + caminho, document.baseURI).href : prefixo + caminho,
        },
        fireImmediately: true,
      });
      const suportados = await BarcodeDetector.getSupportedFormats();
      const detector = new BarcodeDetector({ formats: FORMATOS.filter(f => suportados.includes(f as never)) as never[] });
      return { nome: 'zxing', detect: fonte => detector.detect(fonte as ImageBitmapSource) as Promise<CodigoLido[]> } satisfies Motor;
    } catch (e) {
      console.warn('[leitor] ZXing indisponível:', e);
      zxing = null; // tenta de novo na próxima abertura
      return null;
    }
  })();
  return zxing;
}

/** Leitor do próprio sistema (ML Kit no Android), quando existe e reconhece algum formato. */
async function criarNativo(): Promise<Motor | null> {
  const Detector = (globalThis as unknown as { BarcodeDetector?: ConstrutorDetector }).BarcodeDetector;
  if (!Detector) return null;
  try {
    const suportados = (await Detector.getSupportedFormats?.()) ?? [];
    const formatos = FORMATOS.filter(f => suportados.includes(f));
    if (!formatos.length) return null;
    const detector = new Detector({ formats: formatos });
    return { nome: 'nativo', detect: fonte => detector.detect(fonte) };
  } catch {
    return null;
  }
}

/** Os motores disponíveis neste aparelho; o leitor alterna entre eles a cada quadro. */
export async function criarMotores(): Promise<Motor[]> {
  const [nativo, wasm] = await Promise.all([criarNativo(), criarZxing()]);
  return [nativo, wasm].filter((m): m is Motor => !!m);
}

export function cameraDisponivel(): boolean {
  return typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;
}
