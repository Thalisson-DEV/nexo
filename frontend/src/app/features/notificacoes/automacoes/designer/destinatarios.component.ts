import { ChangeDetectionStrategy, Component, ElementRef, HostListener, Injector, afterNextRender, computed, effect, inject, input, output, signal } from '@angular/core';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CesButtonComponent } from '@ces/ui';

import { JsonValue, Opcao } from '../../../../core/models/automacao.models';
import { AutomacoesService } from '../../../../core/services/automacoes.service';
import { objetos, texto } from '../lib/arvore';
import { descreverExpressao, mapaEspecs } from '../lib/dinamico';
import { ContextoDinamico, SeletorDinamicoComponent } from './seletor-dinamico.component';

export interface Destinatario {
  tipo: 'usuario' | 'colaborador' | 'email' | 'permissao' | 'perfil' | 'papel' | 'expressao';
  valor: string;
  rotulo?: string;
  /** Permissão e perfil: só quem é desta base (texto fixo ou expressão, ex.: a base do colaborador do evento). */
  base?: string;
}

/** A base do colaborador que o gatilho trouxe (eventos de desconto, RH…). */
export const BASE_DO_COLABORADOR = '{{ trigger.outputs.colaborador.base }}';

const TIPOS: { tipo: Destinatario['tipo']; rotulo: string; icone: string; ajuda: string }[] = [
  { tipo: 'usuario', rotulo: 'Pessoa (usuário)', icone: 'pi pi-user', ajuda: 'Busque pelo nome ou login' },
  { tipo: 'colaborador', rotulo: 'Colaborador', icone: 'pi pi-id-card', ajuda: 'Recebe pelo usuário vinculado ao colaborador' },
  { tipo: 'email', rotulo: 'E-mail', icone: 'pi pi-envelope', ajuda: 'Qualquer endereço, de dentro ou de fora' },
  { tipo: 'permissao', rotulo: 'Quem tem a permissão', icone: 'pi pi-key', ajuda: 'Acompanha sozinho quem ganha ou perde a atribuição' },
  { tipo: 'perfil', rotulo: 'Perfil de acesso', icone: 'pi pi-users', ajuda: 'Todos os usuários ativos do perfil' },
  { tipo: 'papel', rotulo: 'Pessoa do evento', icone: 'pi pi-user-edit', ajuda: 'Quem pediu, o responsável, o colaborador…' },
  { tipo: 'expressao', rotulo: 'Dado de um passo', icone: 'pi pi-bolt', ajuda: 'Ex.: a saída de "Escolher destinatários"' }
];

function serializar(x: Destinatario): JsonValue {
  return { tipo: x.tipo, valor: x.valor, ...(x.rotulo ? { rotulo: x.rotulo } : {}), ...(x.base ? { base: x.base } : {}) };
}

/** Sufixo do chip quando o destinatário é filtrado por base. */
export function sufixoBase(base: string | undefined): string {
  if (!base) return '';
  return base === BASE_DO_COLABORADOR ? ' · base do colaborador' : ` · ${base}`;
}

export function iconeDestinatario(tipo: string): string {
  return TIPOS.find((t) => t.tipo === tipo)?.icone ?? 'pi pi-user';
}

/** "Quem recebe": chips de destinatários de vários tipos, somados. */
@Component({
  selector: 'aut-destinatarios',
  standalone: true,
  imports: [CesButtonComponent, SeletorDinamicoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-chips">
      @for (d of itens(); track $index) {
        <span class="aut-chip" [class.aut-chip-dyn]="d.tipo === 'expressao' || d.tipo === 'papel'" [title]="dica(d)">
          <i [class]="icone(d.tipo)" aria-hidden="true"></i>
          {{ nome(d) }}{{ sufixo(d.base) }}
          <button type="button" [attr.aria-label]="'Remover ' + nome(d)" (click)="remover($index)"><i class="pi pi-times" aria-hidden="true"></i></button>
        </span>
      }
      @if (!itens().length) { <span class="aut-muted">{{ vazio() }}</span> }
      <span style="flex:1"></span>
      <ces-button icon="pi pi-plus" label="Adicionar" variant="ghost" size="sm" data-dyn-trigger (pressed)="abrirMenu()" aria-haspopup="true"></ces-button>

      @if (menu() === 'tipos') {
        <div class="aut-dyn" role="menu">
          @for (t of tipos; track t.tipo) {
            @if (t.tipo !== 'papel' || papeis().length) {
              <button type="button" class="aut-dyn-item" style="padding-left:0.5rem" role="menuitem" (click)="escolherTipo(t.tipo)">
                <span style="display:flex;gap:0.6rem;align-items:center">
                  <i [class]="t.icone" aria-hidden="true"></i>
                  <span style="display:flex;flex-direction:column"><strong style="font-weight:600">{{ t.rotulo }}</strong><small class="aut-muted">{{ t.ajuda }}</small></span>
                </span>
              </button>
            }
          }
        </div>
      }
      @if (menu() === 'busca') {
        <div class="aut-dyn" role="dialog" [attr.aria-label]="tipoAtual()?.rotulo">
          <div class="aut-dyn-top"><span>{{ tipoAtual()?.rotulo }}</span><button type="button" class="aut-link" (click)="menu.set('tipos')">Voltar</button></div>
          @if (tipoAtual()?.tipo === 'email') {
            <form (submit)="$event.preventDefault(); adicionarEmail(email.value); email.value=''" style="display:flex;gap:0.5rem">
              <input #email class="p-inputtext ces-prime-control" type="email" placeholder="rh.juazeiro@exemplo.com.br" aria-label="E-mail" />
              <ces-button label="Adicionar" variant="primary" size="sm" type="submit" ></ces-button>
            </form>
          } @else if (tipoAtual()?.tipo === 'papel') {
            <div class="aut-dyn-list">
              @for (p of papeis(); track p.valor) {
                <button type="button" class="aut-dyn-item" style="padding-left:0.5rem" (click)="adicionar(p.destinatario)">
                  <span class="aut-dyn-label">{{ p.rotulo }}</span>
                </button>
              }
            </div>
          } @else {
            @if (tipoAtual()?.tipo === 'permissao' || tipoAtual()?.tipo === 'perfil') {
              <label class="aut-field">
                <span class="ces-field__label">Só da base</span>
                <select class="p-inputtext ces-prime-control aut-select" [value]="base()" (change)="base.set($any($event.target).value)">
                  <option value="">Todas as bases</option>
                  @if (temColaborador()) { <option [value]="baseDoColaborador">Base do colaborador do evento</option> }
                  @for (b of bases(); track b.valor) { <option [value]="b.valor">{{ b.rotulo }}</option> }
                </select>
              </label>
            }
            <input #q class="p-inputtext ces-prime-control" [placeholder]="tipoAtual()?.ajuda ?? 'Buscar'" (input)="buscar(q.value)" aria-label="Buscar" />
            <div class="aut-dyn-list">
              @if (carregando()) { <p class="aut-muted">Buscando…</p> }
              @for (o of opcoes(); track o.valor) {
                <button type="button" class="aut-dyn-item" style="padding-left:0.5rem" (click)="adicionarOpcao(o)">
                  <span style="display:flex;flex-direction:column;min-width:0;text-align:start">
                    <span class="aut-dyn-label">{{ o.rotulo }}</span>
                    @if (o.detalhe) { <small class="aut-muted">{{ o.detalhe }}</small> }
                  </span>
                </button>
              }
              @if (!carregando() && termo().length >= minimo() && !opcoes().length) { <p class="aut-muted">Nada encontrado.</p> }
              @if (termo().length < minimo()) { <p class="aut-muted">Digite ao menos {{ minimo() }} letras.</p> }
            </div>
          }
        </div>
      }
      @if (menu() === 'dinamico') {
        <aut-seletor-dinamico [contexto]="contexto()" (escolher)="adicionarExpressao($event)" (fechar)="menu.set(null)" />
      }
    </div>
  `
})
export class DestinatariosComponent {
  readonly valor = input<JsonValue | undefined>(undefined);
  readonly contexto = input.required<ContextoDinamico>();
  readonly vazio = input('Ninguém ainda');
  readonly mudou = output<JsonValue>();

  private readonly service = inject(AutomacoesService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly tipos = TIPOS;
  protected readonly menu = signal<'tipos' | 'busca' | 'dinamico' | null>(null);
  protected readonly tipoAtual = signal<(typeof TIPOS)[number] | null>(null);
  protected readonly opcoes = signal<Opcao[]>([]);
  protected readonly carregando = signal(false);
  protected readonly termo = signal('');
  protected readonly base = signal('');
  protected readonly bases = signal<Opcao[]>([]);
  protected readonly baseDoColaborador = BASE_DO_COLABORADOR;
  protected readonly temColaborador = computed(() => {
    const c = this.contexto();
    return !!mapaEspecs(c.catalogo).get(c.definicao.trigger.type)?.saidas.some((s) => s.chave === 'colaborador');
  });
  private readonly nomes = signal<Record<string, string>>({});
  private readonly busca$ = new Subject<string>();

  protected readonly itens = computed<Destinatario[]>(() =>
    objetos(this.valor()).map((o) => ({
      tipo: texto(o['tipo']) as Destinatario['tipo'], valor: texto(o['valor']), rotulo: texto(o['rotulo']) || undefined, base: texto(o['base']) || undefined
    }))
  );

  protected sufixo(base: string | undefined): string {
    return sufixoBase(base);
  }

  protected readonly minimo = computed(() => (this.tipoAtual()?.tipo === 'perfil' || this.tipoAtual()?.tipo === 'permissao' ? 0 : 2));

  /** Pessoas que o gatilho traz: papéis do evento e o colaborador. */
  protected readonly papeis = computed(() => {
    const c = this.contexto();
    const espec = mapaEspecs(c.catalogo).get(c.definicao.trigger.type);
    const lista: { valor: string; rotulo: string; destinatario: Destinatario }[] = [];
    if (!espec) return lista;
    const papeis = espec.saidas.find((s) => s.chave === 'papeis');
    for (const p of papeis?.campos ?? []) {
      lista.push({ valor: p.chave, rotulo: p.rotulo.replace(' (todos)', ''), destinatario: { tipo: 'papel', valor: p.chave, rotulo: p.rotulo.replace(' (todos)', '') } });
    }
    if (espec.saidas.some((s) => s.chave === 'colaborador')) {
      lista.push({ valor: 'colaborador', rotulo: 'O colaborador do registro', destinatario: { tipo: 'expressao', valor: '{{ trigger.outputs.colaborador }}', rotulo: 'O colaborador do registro' } });
    }
    if (espec.saidas.some((s) => s.chave === 'executado_por')) {
      lista.push({ valor: 'executor', rotulo: 'Quem executou', destinatario: { tipo: 'expressao', valor: '{{ trigger.outputs.executado_por }}', rotulo: 'Quem executou' } });
    }
    return lista;
  });

  constructor() {
    this.busca$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((t) => {
        const tipo = this.tipoAtual()?.tipo;
        if (!tipo || t.length < this.minimo()) return of([] as Opcao[]);
        this.carregando.set(true);
        return this.service.opcoes(tipo, t).pipe(catchError(() => of([] as Opcao[])));
      }),
      takeUntilDestroyed()
    ).subscribe((o) => {
      this.opcoes.set(o);
      this.carregando.set(false);
    });

    // Nomes de ids salvos sem rótulo (ex.: regras convertidas).
    effect(() => {
      const faltando: Record<string, string[]> = {};
      for (const d of this.itens()) {
        if (!d.rotulo && ['usuario', 'colaborador', 'perfil'].includes(d.tipo) && !this.nomes()[d.valor]) {
          (faltando[d.tipo] ??= []).push(d.valor);
        }
      }
      for (const [tipo, valores] of Object.entries(faltando)) {
        this.service.rotulos(tipo, valores).subscribe({
          next: (lista) => this.nomes.update((n) => ({ ...n, ...Object.fromEntries(lista.map((o) => [o.valor, o.rotulo])) })),
          error: () => undefined
        });
      }
    });
  }

  protected icone(tipo: string): string {
    return iconeDestinatario(tipo);
  }

  protected nome(d: Destinatario): string {
    if (d.rotulo) return d.rotulo;
    if (d.tipo === 'expressao') {
      const m = /^\{\{\s*([\s\S]+?)\s*\}\}$/.exec(d.valor);
      const c = this.contexto();
      return m ? descreverExpressao(m[1]!, c.definicao, c.catalogo) : d.valor;
    }
    return this.nomes()[d.valor] ?? d.valor;
  }

  protected dica(d: Destinatario): string {
    return `${TIPOS.find((t) => t.tipo === d.tipo)?.rotulo ?? d.tipo}: ${d.valor}`;
  }

  protected abrirMenu(): void {
    this.menu.set(this.menu() ? null : 'tipos');
  }

  protected escolherTipo(tipo: Destinatario['tipo']): void {
    if (tipo === 'expressao') {
      this.menu.set('dinamico');
      return;
    }
    this.tipoAtual.set(TIPOS.find((t) => t.tipo === tipo) ?? null);
    this.opcoes.set([]);
    this.termo.set('');
    this.menu.set('busca');
    this.base.set('');
    if (tipo === 'perfil' || tipo === 'permissao') {
      this.buscar('');
      if (!this.bases().length) this.service.opcoes('base', '').subscribe({ next: (o) => this.bases.set(o), error: () => undefined });
    }
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLInputElement>('.aut-dyn input')?.focus(), { injector: this.injector });
  }

  protected buscar(t: string): void {
    this.termo.set(t);
    if (this.minimo() === 0 && t === '') {
      const tipo = this.tipoAtual()?.tipo;
      if (tipo) this.service.opcoes(tipo, '').subscribe({ next: (o) => this.opcoes.set(o), error: () => undefined });
      return;
    }
    this.busca$.next(t);
  }

  protected adicionarOpcao(o: Opcao): void {
    const tipo = this.tipoAtual()?.tipo;
    if (!tipo) return;
    const base = (tipo === 'permissao' || tipo === 'perfil') && this.base() ? this.base() : undefined;
    this.adicionar({ tipo, valor: o.valor, rotulo: tipo === 'permissao' ? o.valor : o.rotulo, base });
  }

  protected adicionarEmail(email: string): void {
    for (const parte of email.split(/[,;\s]+/)) {
      const e = parte.trim().toLowerCase();
      if (e) this.adicionar({ tipo: 'email', valor: e, rotulo: e }, false);
    }
  }

  protected adicionarExpressao(expr: string): void {
    const c = this.contexto();
    this.adicionar({ tipo: 'expressao', valor: `{{ ${expr} }}`, rotulo: descreverExpressao(expr, c.definicao, c.catalogo) });
  }

  protected adicionar(d: Destinatario, fechar = true): void {
    const atuais = this.itens();
    if (!atuais.some((x) => x.tipo === d.tipo && x.valor === d.valor && (x.base ?? '') === (d.base ?? ''))) {
      this.mudou.emit([...atuais, d].map(serializar));
    }
    if (fechar) this.menu.set(null);
  }

  protected remover(i: number): void {
    this.mudou.emit(this.itens().filter((_, j) => j !== i).map(serializar));
  }

  @HostListener('document:mousedown', ['$event'])
  protected fora(e: MouseEvent): void {
    if (this.menu() && this.menu() !== 'dinamico' && !this.host.nativeElement.contains(e.target as Node)) this.menu.set(null);
  }
}
