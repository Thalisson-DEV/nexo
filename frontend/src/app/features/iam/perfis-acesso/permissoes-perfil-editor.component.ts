import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { CesBooleanControlComponent, CesButtonComponent, CesCalloutComponent, CesStatusComponent, CesTextInputComponent } from '@ces/ui';

import { PermissaoResponse } from '../../../core/models/iam.models';
import { ModuloPermissao, RecursoPermissao, agruparPermissoes, normalizar, textoBusca } from './permissoes-catalogo';

/**
 * Editor das permissões de um perfil para quem administra sem conhecer os códigos técnicos:
 * módulos recolhíveis com contagem, busca por nome em português, atalhos por módulo
 * ("Só consultar", "Tudo", "Nenhuma"), "Ver" marcado junto de qualquer outra ação do item e
 * um resumo do que vai mudar antes de salvar.
 */
@Component({
  selector: 'app-permissoes-perfil-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesBooleanControlComponent, CesButtonComponent, CesCalloutComponent, CesStatusComponent, CesTextInputComponent],
  styleUrl: './permissoes-perfil-editor.component.css',
  template: `
    <div class="editor" [attr.aria-busy]="carregando()">
      <ces-callout tone="info" title="Como funciona">
        <strong>Ver</strong> deixa consultar; <strong>Editar</strong> deixa cadastrar e alterar; as demais são ações
        específicas (aprovar, entregar...). Marcar qualquer ação marca também o <strong>Ver</strong> do mesmo item, e
        desmarcar o <strong>Ver</strong> tira as demais.
      </ces-callout>

      <div class="editor__barra">
        <div class="editor__busca">
          <ces-text-input label="Buscar permissão" [labelVisible]="false" placeholder="Buscar por módulo, tela ou ação (ex.: solicitações, aprovar)" [(value)]="busca" />
        </div>
        <ces-boolean-control label="Mostrar só as marcadas" kind="toggle" [inline]="true" [(value)]="soMarcadas" />
      </div>

      <div class="editor__resumo" aria-live="polite">
        <span><strong>{{ selecionadas().size }}</strong> de {{ total() }} permissões marcadas</span>
        @if (adicionadas() || removidas()) {
          <span class="editor__mudancas">
            @if (adicionadas()) { <ces-status tone="success" icon="pi pi-plus">{{ adicionadas() }} a conceder</ces-status> }
            @if (removidas()) { <ces-status tone="danger" icon="pi pi-minus">{{ removidas() }} a remover</ces-status> }
            <ces-button label="Desfazer alterações" icon="pi pi-undo" size="sm" variant="ghost" (pressed)="desfazer()" />
          </span>
        }
      </div>

      @if (carregando()) {
        <div class="editor__esqueleto" aria-hidden="true">
          @for (i of [1, 2, 3, 4]; track i) { <div></div> }
        </div>
        <span class="sr-only">Carregando permissões do perfil...</span>
      } @else if (!visiveis().length) {
        <p class="editor__vazio">
          @if (busca().trim()) { Nenhuma permissão encontrada para "{{ busca().trim() }}". }
          @else if (soMarcadas()) { Este perfil ainda não tem permissões marcadas. }
          @else { Nenhuma permissão cadastrada no sistema. }
        </p>
      } @else {
        @for (m of visiveis(); track m.modulo.chave) {
          @let aberto = estaAberto(m.modulo.chave);
          @let marcadas = contar(m.modulo);
          <section class="modulo" [class.modulo--ativo]="marcadas > 0">
            <header class="modulo__topo">
              <button
                type="button"
                class="modulo__alternar"
                [attr.aria-expanded]="aberto"
                [attr.aria-controls]="'mod-' + m.modulo.chave"
                (click)="alternar(m.modulo.chave)"
              >
                <i class="pi" [class.pi-chevron-right]="!aberto" [class.pi-chevron-down]="aberto" aria-hidden="true"></i>
                <i [class]="m.modulo.icone + ' modulo__icone'" aria-hidden="true"></i>
                <span class="modulo__nome">{{ m.modulo.rotulo }}</span>
                <span class="modulo__contagem">{{ marcadas }} de {{ m.modulo.ids.length }}</span>
              </button>
              <span class="modulo__atalhos">
                <ces-button label="Só consultar" size="sm" variant="ghost" [ariaLabel]="'Marcar só consulta em ' + m.modulo.rotulo" (pressed)="soConsultar(m.modulo)" />
                <ces-button label="Tudo" size="sm" variant="ghost" [ariaLabel]="'Marcar tudo em ' + m.modulo.rotulo" (pressed)="marcarModulo(m.modulo, true)" />
                <ces-button label="Nenhuma" size="sm" variant="ghost" [ariaLabel]="'Desmarcar tudo em ' + m.modulo.rotulo" [disabled]="!marcadas" (pressed)="marcarModulo(m.modulo, false)" />
              </span>
            </header>

            @if (aberto) {
              <ul class="modulo__recursos" [id]="'mod-' + m.modulo.chave">
                @for (r of m.recursos; track r.chave) {
                  <li class="recurso">
                    <div class="recurso__info">
                      <span class="recurso__nome">{{ r.rotulo }}</span>
                      @if (r.descricao) { <span class="recurso__descricao">{{ r.descricao }}</span> }
                    </div>
                    <div class="recurso__acoes" role="group" [attr.aria-label]="'Permissões de ' + r.rotulo">
                      @for (a of r.acoes; track a.permissao.id) {
                        @let marcada = selecionadas().has(a.permissao.id);
                        <div class="acao" [class.acao--nova]="marcada && !iniciais().has(a.permissao.id)" [class.acao--removida]="!marcada && iniciais().has(a.permissao.id)" [title]="a.ajuda ?? a.permissao.nome">
                          <ces-boolean-control [label]="a.rotulo" [inline]="true" [value]="marcada" (valueChange)="marcar(r, a.permissao, $event)" />
                          @if (a.ajuda) { <span class="acao__ajuda">{{ a.ajuda }}</span> }
                        </div>
                      }
                    </div>
                  </li>
                }
              </ul>
            }
          </section>
        }
      }
    </div>
  `
})
export class PermissoesPerfilEditorComponent {
  readonly permissoes = input.required<readonly PermissaoResponse[]>();
  readonly selecionadas = model.required<ReadonlySet<string>>();
  /** O que o perfil já tinha, para mostrar o que vai mudar. */
  readonly iniciais = input<ReadonlySet<string>>(new Set());
  readonly carregando = input(false);

  protected readonly busca = signal('');
  protected readonly soMarcadas = signal(false);
  private readonly abertos = signal<ReadonlySet<string>>(new Set());

  protected readonly modulos = computed(() => agruparPermissoes(this.permissoes()));
  protected readonly total = computed(() => this.permissoes().length);
  protected readonly adicionadas = computed(() => [...this.selecionadas()].filter((id) => !this.iniciais().has(id)).length);
  protected readonly removidas = computed(() => [...this.iniciais()].filter((id) => !this.selecionadas().has(id)).length);

  /** Módulos e recursos que passam pela busca e pelo filtro "só marcadas". */
  protected readonly visiveis = computed(() => {
    const termo = normalizar(this.busca().trim());
    const marcadas = this.selecionadas();
    return this.modulos()
      .map((modulo) => ({
        modulo,
        recursos: modulo.recursos.filter(
          (r) =>
            (!termo || textoBusca(modulo, r).includes(termo)) &&
            (!this.soMarcadas() || r.acoes.some((a) => marcadas.has(a.permissao.id)))
        )
      }))
      .filter((m) => m.recursos.length);
  });

  /** Buscando ou filtrando, tudo que sobrou aparece aberto; senão vale o que a pessoa abriu. */
  protected estaAberto(chave: string): boolean {
    return !!this.busca().trim() || this.soMarcadas() || this.abertos().has(chave);
  }

  protected alternar(chave: string): void {
    this.abertos.update((atual) => {
      const novo = new Set(atual);
      if (novo.has(chave)) novo.delete(chave);
      else novo.add(chave);
      return novo;
    });
  }

  protected contar(modulo: ModuloPermissao): number {
    const marcadas = this.selecionadas();
    return modulo.ids.filter((id) => marcadas.has(id)).length;
  }

  protected marcar(recurso: RecursoPermissao, permissao: PermissaoResponse, marcado: boolean): void {
    const ver = recurso.acoes.find((a) => a.tipo === 'ver' && a.permissao.id !== permissao.id);
    const ehVer = recurso.acoes.find((a) => a.permissao.id === permissao.id)?.tipo === 'ver';
    this.selecionadas.update((atual) => {
      const novo = new Set(atual);
      if (marcado) {
        novo.add(permissao.id);
        // Qualquer ação precisa enxergar a tela.
        if (ver) novo.add(ver.permissao.id);
      } else {
        novo.delete(permissao.id);
        // Sem "Ver", as demais ações do item não têm onde ser usadas.
        if (ehVer) recurso.acoes.forEach((a) => novo.delete(a.permissao.id));
      }
      return novo;
    });
  }

  protected soConsultar(modulo: ModuloPermissao): void {
    const ids = modulo.recursos.flatMap((r) => r.acoes.filter((a) => a.tipo === 'ver').map((a) => a.permissao.id));
    this.selecionadas.update((atual) => {
      const novo = new Set(atual);
      modulo.ids.forEach((id) => novo.delete(id));
      ids.forEach((id) => novo.add(id));
      return novo;
    });
    this.abrir(modulo.chave);
  }

  protected marcarModulo(modulo: ModuloPermissao, marcar: boolean): void {
    this.selecionadas.update((atual) => {
      const novo = new Set(atual);
      modulo.ids.forEach((id) => (marcar ? novo.add(id) : novo.delete(id)));
      return novo;
    });
    if (marcar) this.abrir(modulo.chave);
  }

  protected desfazer(): void {
    this.selecionadas.set(new Set(this.iniciais()));
  }

  private abrir(chave: string): void {
    this.abertos.update((atual) => new Set(atual).add(chave));
  }
}
