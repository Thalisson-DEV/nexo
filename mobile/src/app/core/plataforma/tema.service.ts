import { Injectable, signal } from '@angular/core';

export type Tema = 'claro' | 'escuro';
export type Contraste = 'normal' | 'alto';

const CHAVE_TEMA = 'ces_tema';
const CHAVE_CONTRASTE = 'ces_contraste';
const COR_BARRA: Record<Tema, string> = { claro: '#F4F6F9', escuro: '#0B1017' };

/**
 * Tema do app. CLARO por padrão, mesmo com o celular em modo escuro (decisão do dono
 * do produto): o escuro e o alto contraste (sol forte) são escolhas do usuário em Conta.
 * O index.html aplica a escolha antes do Angular carregar, para não piscar.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  readonly tema = signal<Tema>(ler(CHAVE_TEMA) === 'escuro' ? 'escuro' : 'claro');
  readonly contraste = signal<Contraste>(ler(CHAVE_CONTRASTE) === 'alto' ? 'alto' : 'normal');

  definirTema(tema: Tema): void {
    this.tema.set(tema);
    gravar(CHAVE_TEMA, tema);
    this.aplicar();
  }

  definirContraste(contraste: Contraste): void {
    this.contraste.set(contraste);
    gravar(CHAVE_CONTRASTE, contraste);
    this.aplicar();
  }

  aplicar(): void {
    if (typeof document === 'undefined') return;
    const raiz = document.documentElement;
    if (this.tema() === 'escuro') raiz.setAttribute('data-tema', 'escuro');
    else raiz.removeAttribute('data-tema');
    if (this.contraste() === 'alto') raiz.setAttribute('data-contraste', 'alto');
    else raiz.removeAttribute('data-contraste');
    const meta = document.querySelector<HTMLMetaElement>('meta[name=theme-color]');
    if (meta) meta.content = COR_BARRA[this.tema()];
  }
}

function ler(chave: string): string | null {
  try { return localStorage.getItem(chave); } catch { return null; }
}

function gravar(chave: string, valor: string): void {
  try { localStorage.setItem(chave, valor); } catch { /* modo privado: vale só nesta sessão */ }
}
