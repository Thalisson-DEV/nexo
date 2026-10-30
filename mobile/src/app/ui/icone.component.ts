import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { inject } from '@angular/core';

/**
 * Ícones do app de campo: traço 1,8 px em grade 24×24, embutidos no bundle
 * (abrem offline, sem fonte externa). Para um ícone novo, siga o mesmo traço.
 */
export const ICONES = {
  hoje: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M8 3v4M16 3v4M3.5 10h17"/><circle cx="12" cy="15" r="1.6"/>',
  // Chave inglesa: cabeça aberta em cima à direita, cabo até embaixo à esquerda.
  servicos: '<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.1-3.1a5.5 5.5 0 0 1-7.3 7.3l-7 7a2.1 2.1 0 0 1-3-3l7-7a5.5 5.5 0 0 1 7.3-7.3z"/>',
  material: '<path d="M12 3 4 7v10l8 4 8-4V7z"/><path d="M4 7l8 4 8-4M12 11v10"/>',
  conta: '<circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
  inspecao: '<rect x="5" y="3.5" width="14" height="17" rx="2.5"/><path d="M9 3.5h6v3H9zM8.5 12l2.2 2.2 4.3-4.4M8.5 17h7"/>',
  desvio: '<path d="M12 3.5 2.5 20h19z"/><path d="M12 10v4.5M12 17.5v.01"/>',
  mais: '<path d="M12 5v14M5 12h14"/>',
  menos: '<path d="M5 12h14"/>',
  fechar: '<path d="M6 6l12 12M18 6 6 18"/>',
  voltar: '<path d="M15 5l-7 7 7 7"/>',
  avancar: '<path d="M9 6l6 6-6 6"/>',
  seta: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  local: '<path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
  local_busca: '<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"/><circle cx="12" cy="12" r="7"/>',
  nuvem: '<path d="M7 18h10.5a4 4 0 0 0 .6-8A6 6 0 0 0 6.6 9.2 4.4 4.4 0 0 0 7 18z"/><path d="M12 11v5M9.5 13.5 12 11l2.5 2.5"/>',
  aparelho: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/>',
  sem_rede: '<path d="M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 13a10 10 0 0 1 5.5-2.8M19 13a10 10 0 0 0-3-2M2 9.3a15 15 0 0 1 5-2.7M22 9.3A15 15 0 0 0 12 5.5"/><circle cx="12" cy="20" r=".8"/>',
  retomar: '<path d="M4 12a8 8 0 1 0 2.4-5.7L4 8.5"/><path d="M4 4v4.5h4.5M12 8v4l3 2"/>',
  baixar: '<path d="M12 4v11M7.5 10.5 12 15l4.5-4.5M5 20h14"/>',
  subir: '<path d="M12 15V4M7.5 8.5 12 4l4.5 4.5M5 20h14"/>',
  atualizar: '<path d="M20 12a8 8 0 0 1-14 5.3M4 12a8 8 0 0 1 14-5.3"/><path d="M18 3v4h-4M6 21v-4h4"/>',
  alerta: '<path d="M12 3.5 2.5 20h19z"/><path d="M12 10v4.5M12 17.5v.01"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8v.01"/>',
  busca: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
  filtro: '<path d="M4 6h16M7 12h10M10 18h4"/>',
  camera: '<path d="M4 8.5A1.5 1.5 0 0 1 5.5 7h2.2l1.5-2h5.6l1.5 2h2.2A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z"/><circle cx="12" cy="13" r="3.5"/>',
  lixeira: '<path d="M5 7h14M10 7V4.5h4V7M7 7l.8 12.5h8.4L17 7"/>',
  editar: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  relogio: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  caminhao: '<path d="M3 6.5h11v9H3zM14 9.5h4l3 3v3h-7"/><circle cx="7" cy="17.5" r="1.8"/><circle cx="17" cy="17.5" r="1.8"/>',
  pessoas: '<circle cx="9" cy="9" r="3.2"/><path d="M3 19a6 6 0 0 1 12 0"/><circle cx="17" cy="10" r="2.5"/><path d="M15.5 14.2A5 5 0 0 1 21 19"/>',
  sair: '<path d="M10 4H6a1.5 1.5 0 0 0-1.5 1.5v13A1.5 1.5 0 0 0 6 20h4M15 8l4 4-4 4M19 12H9.5"/>',
  sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
  lua: '<path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"/>',
  compartilhar_ios: '<path d="M12 3.5v11M8 7.5l4-4 4 4M6 11H5v9.5h14V11h-1"/>',
  instalar: '<rect x="6" y="2.5" width="12" height="19" rx="2.5"/><path d="M12 7v7M9 11l3 3 3-3"/>',
  documento: '<path d="M6.5 3.5h7l4 4v13h-11z"/><path d="M13.5 3.5v4h4M9 12h6M9 16h6"/>',
  transferencia: '<path d="M4 8h14M14 4l4 4-4 4M20 16H6M10 12l-4 4 4 4"/>',
  estoque: '<path d="M3.5 9 12 4l8.5 5v10.5h-17z"/><path d="M8 19.5v-6h8v6"/>',
  mais_opcoes: '<circle cx="5.5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="18.5" cy="12" r="1.2"/>',
  // Almoxarifado (perfil administrativo)
  almox: '<path d="M3 20.5V9l9-5.5L21 9v11.5"/><path d="M6.5 20.5v-8h11v8M6.5 15.5h11"/>',
  entrada: '<path d="M12 3.5v9M8.5 9 12 12.5 15.5 9"/><path d="M4 13.5v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5"/>',
  saida: '<path d="M12 12.5v-9M8.5 7 12 3.5 15.5 7"/><path d="M4 13.5v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5"/>',
  desfazer: '<path d="M9 14 4.5 9.5 9 5"/><path d="M4.5 9.5H14a5.5 5.5 0 0 1 0 11h-3"/>',
  bandeira: '<path d="M5.5 21V4M5.5 4.5h11l-2 4 2 4h-11"/>',
  codigo_barras: '<path d="M3.5 7V5.5a2 2 0 0 1 2-2H7M17 3.5h1.5a2 2 0 0 1 2 2V7M20.5 17v1.5a2 2 0 0 1-2 2H17M7 20.5H5.5a2 2 0 0 1-2-2V17"/><path d="M7.5 8v8M10.5 8v8M13 8v8M16.5 8v8"/>',
  teclado: '<rect x="2.5" y="6" width="19" height="12" rx="2.5"/><path d="M6 10h.01M9.5 10h.01M13 10h.01M16.5 10h.01M6 14h.01M8.5 14h7M18 14h.01"/>',
  colar: '<rect x="5" y="4.5" width="14" height="16.5" rx="2.5"/><path d="M9 4.5V3h6v1.5M9 10h6M9 14h6M9 18h3"/>',
  sequencia: '<path d="M4 6.5h3M4 12h3M4 17.5h3M10.5 6.5H20M10.5 12H20M10.5 17.5H20"/>',
  quadro: '<rect x="3" y="4" width="5" height="16" rx="1.5"/><rect x="9.5" y="4" width="5" height="11" rx="1.5"/><rect x="16" y="4" width="5" height="7" rx="1.5"/>',
  lista: '<path d="M8.5 6.5H20M8.5 12H20M8.5 17.5H20"/><circle cx="4.5" cy="6.5" r=".9"/><circle cx="4.5" cy="12" r=".9"/><circle cx="4.5" cy="17.5" r=".9"/>',
  calendario: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M8 3v4M16 3v4M3.5 10h17M8 14h.01M12 14h.01M16 14h.01M8 17.5h.01M12 17.5h.01"/>',
  escudo: '<path d="M12 3.5 5 6v5.5c0 4.4 3 7.6 7 9 4-1.4 7-4.6 7-9V6z"/><path d="m9 12 2.2 2.2L15 10.4"/>',
  lanterna: '<path d="M8 3.5h8v3l-2 3.5v10.5h-4V10L8 6.5z"/><path d="M12 13v2.5"/>',
  expandir: '<path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"/>',
  recolher: '<path d="M9 4v5H4M20 9h-5V4M15 20v-5h5M4 15h5v5"/>',
  assinatura: '<path d="M3.5 17.5c2.5 0 3.5-9 6-9s.5 7 3 7 2-3 3.5-3 1.5 2 4.5 2"/><path d="M3.5 20.5h17"/>',
  pessoa_fora: '<circle cx="10" cy="8.5" r="3.6"/><path d="M3 20a7 7 0 0 1 11.5-5.3M16 15.5h5.5M19 13l2.5 2.5L19 18"/>',
} as const;

export type NomeIcone = keyof typeof ICONES;

@Component({
  selector: 'ces-icone',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="tamanho()" [attr.height]="tamanho()" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"
      [innerHTML]="svg()"></svg>`,
  styles: [`:host { display: inline-flex; flex: none; line-height: 0; } svg { display: block; }`],
})
export class IconeComponent {
  private readonly sanitizer = inject(DomSanitizer);
  readonly nome = input.required<NomeIcone>();
  readonly tamanho = input(22);
  // Conteúdo vem só do mapa estático acima — nunca de entrada do usuário.
  readonly svg = computed(() => this.sanitizer.bypassSecurityTrustHtml(ICONES[this.nome()] ?? ''));
}
