import { describe, it, expect } from 'vitest';
import {
  getDescontoAnexoCategoria,
  inferMimeType,
  getAnexoIcon,
  getAnexoLabel,
  getAnexoContainerClass,
  formatAnexoSize,
  MAX_DESCONTO_ANEXO_SIZE_BYTES,
  MAX_DESCONTO_ANEXO_SIZE_MB,
  DESCONTO_ANEXO_ACCEPT
} from './desconto-anexo.util';

describe('desconto-anexo.util', () => {
  describe('Constantes', () => {
    it('deve ter limite de 15 MB configurado', () => {
      expect(MAX_DESCONTO_ANEXO_SIZE_MB).toBe(15);
      expect(MAX_DESCONTO_ANEXO_SIZE_BYTES).toBe(15 * 1024 * 1024);
    });

    it('deve incluir formatos de áudio e vídeo no accept string', () => {
      expect(DESCONTO_ANEXO_ACCEPT).toContain('audio/*');
      expect(DESCONTO_ANEXO_ACCEPT).toContain('.ogg');
      expect(DESCONTO_ANEXO_ACCEPT).toContain('video/*');
      expect(DESCONTO_ANEXO_ACCEPT).toContain('.mp4');
      expect(DESCONTO_ANEXO_ACCEPT).toContain('application/pdf');
    });
  });

  describe('getDescontoAnexoCategoria', () => {
    it('deve identificar áudios por contentType', () => {
      expect(getDescontoAnexoCategoria('gravacao', 'audio/ogg')).toBe('audio');
      expect(getDescontoAnexoCategoria('voz', 'application/ogg')).toBe('audio');
      expect(getDescontoAnexoCategoria('audio1', 'audio/mpeg')).toBe('audio');
      expect(getDescontoAnexoCategoria('audio2', 'audio/wav')).toBe('audio');
    });

    it('deve identificar áudios por extensão quando contentType é octet-stream ou ausente', () => {
      const audioExts = ['.ogg', '.oga', '.opus', '.mp3', '.wav', '.aac', '.m4a', '.weba', '.flac', '.wma'];
      for (const ext of audioExts) {
        expect(getDescontoAnexoCategoria(`arquivo${ext}`, 'application/octet-stream')).toBe('audio');
        expect(getDescontoAnexoCategoria(`arquivo${ext}`)).toBe('audio');
        expect(getDescontoAnexoCategoria(`ARQUIVO${ext.toUpperCase()}`)).toBe('audio');
      }
    });

    it('deve identificar vídeos por contentType', () => {
      expect(getDescontoAnexoCategoria('camera', 'video/mp4')).toBe('video');
      expect(getDescontoAnexoCategoria('registro', 'video/webm')).toBe('video');
      expect(getDescontoAnexoCategoria('filme', 'video/quicktime')).toBe('video');
    });

    it('deve identificar vídeos por extensão', () => {
      const videoExts = ['.mp4', '.webm', '.mov', '.avi', '.mkv', '.mpeg', '.mpg', '.3gp', '.wmv', '.ogv'];
      for (const ext of videoExts) {
        expect(getDescontoAnexoCategoria(`evidencia${ext}`)).toBe('video');
      }
    });

    it('deve identificar imagens por contentType e extensão', () => {
      expect(getDescontoAnexoCategoria('foto', 'image/png')).toBe('image');
      expect(getDescontoAnexoCategoria('foto.jpg')).toBe('image');
      expect(getDescontoAnexoCategoria('foto.jpeg')).toBe('image');
      expect(getDescontoAnexoCategoria('foto.webp')).toBe('image');
      expect(getDescontoAnexoCategoria('foto.heic')).toBe('image');
    });

    it('deve identificar PDFs', () => {
      expect(getDescontoAnexoCategoria('doc', 'application/pdf')).toBe('pdf');
      expect(getDescontoAnexoCategoria('boletim.pdf')).toBe('pdf');
    });

    it('deve identificar documentos Word', () => {
      expect(getDescontoAnexoCategoria('termo.docx')).toBe('word');
      expect(getDescontoAnexoCategoria('termo.doc')).toBe('word');
      expect(getDescontoAnexoCategoria('termo', 'application/msword')).toBe('word');
    });

    it('deve retornar other para formatos não mapeados', () => {
      expect(getDescontoAnexoCategoria('planilha.xlsx')).toBe('other');
      expect(getDescontoAnexoCategoria('dados.bin')).toBe('other');
    });
  });

  describe('inferMimeType', () => {
    it('deve priorizar contentType válido e normalizar application/ogg', () => {
      expect(inferMimeType('audio.ogg', 'application/ogg')).toBe('audio/ogg');
      expect(inferMimeType('video.mp4', 'video/mp4')).toBe('video/mp4');
    });

    it('deve inferir a partir da extensão quando contentType for octet-stream ou indefinido', () => {
      expect(inferMimeType('depoimento.ogg', 'application/octet-stream')).toBe('audio/ogg');
      expect(inferMimeType('depoimento.opus')).toBe('audio/opus');
      expect(inferMimeType('musica.mp3')).toBe('audio/mpeg');
      expect(inferMimeType('video.mp4', 'application/octet-stream')).toBe('video/mp4');
      expect(inferMimeType('video.webm')).toBe('video/webm');
      expect(inferMimeType('foto.jpg')).toBe('image/jpeg');
      expect(inferMimeType('foto.png')).toBe('image/png');
      expect(inferMimeType('documento.pdf')).toBe('application/pdf');
    });
  });

  describe('getAnexoIcon e getAnexoLabel', () => {
    it('deve fornecer ícones coerentes para cada tipo', () => {
      expect(getAnexoIcon('audio')).toContain('pi-volume-up');
      expect(getAnexoIcon('video')).toContain('pi-video');
      expect(getAnexoIcon('image')).toContain('pi-image');
      expect(getAnexoIcon('pdf')).toContain('pi-file-pdf');
      expect(getAnexoIcon('word')).toContain('pi-file-word');
      expect(getAnexoIcon('other')).toContain('pi-file');
    });

    it('deve fornecer rótulos em português', () => {
      expect(getAnexoLabel('audio')).toBe('Áudio');
      expect(getAnexoLabel('video')).toBe('Vídeo');
      expect(getAnexoLabel('image')).toBe('Imagem');
      expect(getAnexoLabel('pdf')).toBe('Documento PDF');
      expect(getAnexoLabel('word')).toBe('Documento Word');
      expect(getAnexoLabel('other')).toBe('Arquivo');
    });

    it('deve fornecer classes de container de destaque', () => {
      expect(getAnexoContainerClass('audio')).toContain('amber');
      expect(getAnexoContainerClass('video')).toContain('purple');
      expect(getAnexoContainerClass('image')).toContain('emerald');
      expect(getAnexoContainerClass('pdf')).toContain('red');
      expect(getAnexoContainerClass('word')).toContain('blue');
    });
  });

  describe('formatAnexoSize', () => {
    it('deve formatar bytes corretamente', () => {
      expect(formatAnexoSize(0)).toBe('0 B');
      expect(formatAnexoSize(500)).toBe('500 B');
      expect(formatAnexoSize(1024)).toBe('1 KB');
      expect(formatAnexoSize(1.5 * 1024 * 1024)).toBe('1.5 MB');
      expect(formatAnexoSize(15 * 1024 * 1024)).toBe('15 MB');
    });
  });
});
