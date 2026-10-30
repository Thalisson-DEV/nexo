import { describe, expect, it } from 'vitest';
import {
  EVIDENCIA_TAMANHO_MAX_BYTES,
  evidenciaRenderizavelNoBrowser,
  validarEvidenciaInspecao
} from './sst.models';

/** Cria um `File` do tamanho pedido sem alocar o conteúdo de verdade. */
function arquivo(nome: string, type: string, size = 1024): File {
  const file = new File(['x'], nome, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('validarEvidenciaInspecao', () => {
  it('aceita os formatos de imagem suportados pelo backend', () => {
    expect(validarEvidenciaInspecao(arquivo('campo.jpg', 'image/jpeg'))).toBeNull();
    expect(validarEvidenciaInspecao(arquivo('campo.png', 'image/png'))).toBeNull();
    expect(validarEvidenciaInspecao(arquivo('campo.webp', 'image/webp'))).toBeNull();
  });

  it('aceita HEIC do iPhone mesmo quando o navegador não informa o MIME', () => {
    expect(validarEvidenciaInspecao(arquivo('IMG_0001.HEIC', ''))).toBeNull();
  });

  it('recusa arquivo que não é imagem', () => {
    expect(validarEvidenciaInspecao(arquivo('laudo.pdf', 'application/pdf'))).toMatch(/fotografia/i);
    expect(validarEvidenciaInspecao(arquivo('ata.docx', 'application/msword'))).toMatch(/fotografia/i);
  });

  it('recusa arquivo vazio e acima de 15 MB', () => {
    expect(validarEvidenciaInspecao(arquivo('campo.jpg', 'image/jpeg', 0))).toMatch(/não vazio/i);
    expect(
      validarEvidenciaInspecao(arquivo('campo.jpg', 'image/jpeg', EVIDENCIA_TAMANHO_MAX_BYTES + 1))
    ).toMatch(/15 MB/);
  });
});

describe('evidenciaRenderizavelNoBrowser', () => {
  it('renderiza formatos que o <img> do desktop decodifica', () => {
    expect(evidenciaRenderizavelNoBrowser('image/jpeg', 'campo.jpg')).toBe(true);
    expect(evidenciaRenderizavelNoBrowser('image/png', 'campo.png')).toBe(true);
  });

  it('não renderiza HEIC/HEIF — a tela mostra um cartão em vez de miniatura quebrada', () => {
    expect(evidenciaRenderizavelNoBrowser('image/heic', 'IMG_0001.HEIC')).toBe(false);
    expect(evidenciaRenderizavelNoBrowser('', 'IMG_0001.heif')).toBe(false);
  });

  it('cai na extensão quando o contentType não vem', () => {
    expect(evidenciaRenderizavelNoBrowser(null, 'campo.jpeg')).toBe(true);
    expect(evidenciaRenderizavelNoBrowser(null, 'sem-extensao')).toBe(false);
  });
});
