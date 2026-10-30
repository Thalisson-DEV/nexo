export const MAX_DESCONTO_ANEXO_SIZE_BYTES = 15 * 1024 * 1024; // 15 MB
export const MAX_DESCONTO_ANEXO_SIZE_MB = 15;

export const DESCONTO_ANEXO_ACCEPT =
  'image/*,application/pdf,.doc,.docx,audio/*,.ogg,video/*,.mp4,.webm,.mov,.avi,.mkv';

export type DescontoAnexoCategoria = 'audio' | 'video' | 'image' | 'pdf' | 'word' | 'other';

const EXTENSOES_AUDIO = new Set([
  '.ogg',
  '.oga',
  '.opus',
  '.mp3',
  '.wav',
  '.aac',
  '.m4a',
  '.weba',
  '.flac',
  '.wma'
]);

const EXTENSOES_VIDEO = new Set([
  '.mp4',
  '.webm',
  '.mov',
  '.avi',
  '.mkv',
  '.mpeg',
  '.mpg',
  '.3gp',
  '.3g2',
  '.wmv',
  '.ogv'
]);

const EXTENSOES_IMAGEM = new Set([
  '.jpg',
  '.jpeg',
  '.png',
  '.webp',
  '.heic',
  '.heif',
  '.svg',
  '.gif',
  '.bmp'
]);

const MIME_MAP: Record<string, string> = {
  // Áudio
  '.ogg': 'audio/ogg',
  '.oga': 'audio/ogg',
  '.opus': 'audio/opus',
  '.mp3': 'audio/mpeg',
  '.wav': 'audio/wav',
  '.aac': 'audio/aac',
  '.m4a': 'audio/mp4',
  '.weba': 'audio/webm',
  '.flac': 'audio/flac',
  '.wma': 'audio/x-ms-wma',

  // Vídeo
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.mov': 'video/quicktime',
  '.avi': 'video/x-msvideo',
  '.mkv': 'video/x-matroska',
  '.mpeg': 'video/mpeg',
  '.mpg': 'video/mpeg',
  '.3gp': 'video/3gpp',
  '.3g2': 'video/3gpp2',
  '.wmv': 'video/x-ms-wmv',
  '.ogv': 'video/ogg',

  // Imagens
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.heic': 'image/heic',
  '.heif': 'image/heif',
  '.svg': 'image/svg+xml',
  '.gif': 'image/gif',
  '.bmp': 'image/bmp',

  // Documentos
  '.pdf': 'application/pdf',
  '.doc': 'application/msword',
  '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
};

function extrairExtensao(nomeArquivo?: string): string {
  if (!nomeArquivo) return '';
  const limpo = nomeArquivo.trim().toLowerCase();
  const ponto = limpo.lastIndexOf('.');
  return ponto >= 0 ? limpo.substring(ponto) : '';
}

/**
 * Identifica a categoria funcional de um anexo com base no seu Content-Type ou extensão.
 */
export function getDescontoAnexoCategoria(
  nomeArquivo?: string,
  contentType?: string
): DescontoAnexoCategoria {
  const ct = contentType?.trim().toLowerCase();
  const ext = extrairExtensao(nomeArquivo);

  // 1. Áudio
  if (
    ct?.startsWith('audio/') ||
    ct === 'application/ogg' ||
    ct === 'audio/opus' ||
    EXTENSOES_AUDIO.has(ext)
  ) {
    return 'audio';
  }

  // 2. Vídeo
  if (ct?.startsWith('video/') || EXTENSOES_VIDEO.has(ext)) {
    return 'video';
  }

  // 3. Imagem
  if (ct?.startsWith('image/') || EXTENSOES_IMAGEM.has(ext)) {
    return 'image';
  }

  // 4. PDF
  if (ct === 'application/pdf' || ext === '.pdf') {
    return 'pdf';
  }

  // 5. Word
  if (
    ct === 'application/msword' ||
    ct === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
    ext === '.doc' ||
    ext === '.docx'
  ) {
    return 'word';
  }

  return 'other';
}

/**
 * Infere o MIME Type adequado para criação de Blob e visualização/reprodução.
 */
export function inferMimeType(nomeArquivo?: string, contentType?: string): string {
  const ct = contentType?.trim().toLowerCase();
  if (ct && ct !== 'application/octet-stream') {
    // Normalização para application/ogg se enviado como áudio
    if (ct === 'application/ogg') {
      return 'audio/ogg';
    }
    return ct;
  }

  const ext = extrairExtensao(nomeArquivo);
  return MIME_MAP[ext] || contentType || 'application/octet-stream';
}

/**
 * Retorna classes de ícone do PrimeIcons e cor de destaque por categoria.
 */
export function getAnexoIcon(categoria: DescontoAnexoCategoria): string {
  switch (categoria) {
    case 'audio':
      return 'pi pi-volume-up text-amber-500 dark:text-amber-400';
    case 'video':
      return 'pi pi-video text-purple-500 dark:text-purple-400';
    case 'image':
      return 'pi pi-image text-emerald-500 dark:text-emerald-400';
    case 'pdf':
      return 'pi pi-file-pdf text-red-500 dark:text-red-400';
    case 'word':
      return 'pi pi-file-word text-blue-600 dark:text-blue-400';
    default:
      return 'pi pi-file text-slate-500 dark:text-slate-400';
  }
}

/**
 * Retorna rótulo legível em português para o tipo de anexo.
 */
export function getAnexoLabel(categoria: DescontoAnexoCategoria): string {
  switch (categoria) {
    case 'audio':
      return 'Áudio';
    case 'video':
      return 'Vídeo';
    case 'image':
      return 'Imagem';
    case 'pdf':
      return 'Documento PDF';
    case 'word':
      return 'Documento Word';
    default:
      return 'Arquivo';
  }
}

/**
 * Retorna classes de background e borda para o quadrado que envolve o ícone.
 */
export function getAnexoContainerClass(categoria: DescontoAnexoCategoria): string {
  switch (categoria) {
    case 'audio':
      return 'bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800/80';
    case 'video':
      return 'bg-purple-50 dark:bg-purple-950/40 border border-purple-200 dark:border-purple-800/80';
    case 'image':
      return 'bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800/80';
    case 'pdf':
      return 'bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800/80';
    case 'word':
      return 'bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800/80';
    default:
      return 'bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700';
  }
}

/**
 * Formata tamanho em bytes para representação legível (B, KB, MB, GB).
 */
export function formatAnexoSize(bytes?: number): string {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}
