import { SafeResourceUrl } from '@angular/platform-browser';

export type CategoriaManual = 'TODOS' | 'GERAL' | 'SST_WEB' | 'MOBILE';

export type ContextoTag = 'Web' | 'Mobile' | 'Mobile / Offline' | 'Geral / Web';

export type TipoAlertaManual = 'atencao' | 'dica' | 'seguranca' | 'regra-ouro';

export interface AlertaManual {
  tipo: TipoAlertaManual;
  titulo: string;
  mensagem: string;
  icone?: string;
}

export interface ItemPasso {
  rotulo: string;
  descricao: string;
  icone?: string;
  badge?: string;
  destaque?: string;
  atalho?: string;
  comando?: string;
  subitens?: string[];
}

export interface SecaoManual {
  numero: string;
  titulo: string;
  icone?: string;
  subtitulo?: string;
  itens?: ItemPasso[];
  paragrafos?: string[];
  alerta?: AlertaManual;
}

export interface ManualModulo {
  id: string;
  numeroOficial: string;
  /** Título oficial e completo do módulo, usado no documento impresso. */
  titulo: string;
  /** Rótulo curto para o índice de navegação e para o título da página na tela. */
  tituloCurto: string;
  subtitulo: string;
  categoria: CategoriaManual;
  categoriaRotulo: string;
  contextoTag: ContextoTag;
  icone: string;
  objetivo: string;
  videoId: string;
  videoUrlOriginal: string;
  safeEmbedUrl?: SafeResourceUrl;
  secoes: SecaoManual[];
  alertasPrincipais?: AlertaManual[];
  palavrasChave: string[];
}

export interface FiltroCategoria {
  id: CategoriaManual;
  label: string;
  icone: string;
}
