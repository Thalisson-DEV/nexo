import { CommonModule } from '@angular/common';
import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem, MessageService, ConfirmationService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';

import { SstService } from '../../../core/services/sst.service';
import {
  CriarFormularioRequest,
  FormularioInspecaoResponse,
  GravidadeDesvio,
  GRAVIDADE_LABELS,
  TipoResposta,
  TIPO_RESPOSTA_LABELS,
  isTipoObjetivo
} from '../../../core/models/sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';

interface BuilderOpcao {
  id?: string;
  uid: string;
  texto: string;
  geraDesvio: boolean;
  gravidade?: GravidadeDesvio | null;
  padrao: boolean;
}
interface BuilderPergunta {
  id?: string;
  uid: string;
  enunciado: string;
  tipoResposta: TipoResposta;
  obrigatoria: boolean;
  opcoes: BuilderOpcao[];
}
interface BuilderGrupo {
  id?: string;
  uid: string;
  titulo: string;
  perguntas: BuilderPergunta[];
}

function uid(): string {
  return (crypto as any)?.randomUUID?.() ?? Math.random().toString(36).slice(2);
}

@Component({
  selector: 'app-sst-formulario-builder',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    CheckboxModule,
    ConfirmDialogModule,
    InputTextModule,
    SelectModule,
    TextareaModule,
    ToastModule,
    TooltipModule,
    SkeletonModule
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast></p-toast>
    <p-confirmDialog></p-confirmDialog>

    <div class="flex flex-col gap-4 pb-28">
      <!-- Barra superior -->
      <div class="flex justify-between items-center">
        <p-breadcrumb [model]="breadcrumbItems" styleClass="!bg-transparent !border-0 !p-0"></p-breadcrumb>
        <p-button icon="pi pi-arrow-left" label="Voltar" [text]="true" severity="secondary" (onClick)="voltar()" styleClass="!text-slate-400 !text-[10px] !font-medium"></p-button>
      </div>

      @if (loading()) {
        <p-skeleton height="22rem" styleClass="!rounded-2xl"></p-skeleton>
      } @else {
        <!-- DOCUMENTO -->
        <div class="bg-white rounded-2xl border border-slate-100 overflow-hidden">
          <!-- CABEÇALHO -->
          <div class="px-5 md:px-8 py-6 border-b border-slate-100">
            <div class="flex items-center gap-2 mb-5">
              <i class="pi pi-file-edit text-primary-700 text-xs"></i>
              <span class="text-[10px] font-medium text-gray-500 uppercase tracking-widest">
                {{ formId() ? 'Editar Formulário de Inspeção' : 'Novo Formulário de Inspeção' }}
              </span>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div class="flex flex-col gap-1.5 md:col-span-1">
                <label class="filter-label !text-slate-600 !text-[11px]">Título</label>
                <input pInputText placeholder="Ex: Inspeção de Campo" class="doc-input" [(ngModel)]="titulo" />
              </div>
              <div class="flex flex-col gap-1.5 md:col-span-2">
                <label class="filter-label !text-slate-600 !text-[11px]">Descrição</label>
                <input pInputText placeholder="Ex: Checklist diário" class="doc-input" [(ngModel)]="descricao" />
              </div>
            </div>
            @if (emUso()) {
              <div class="mt-5 flex items-start gap-3 bg-amber-50/50 border border-amber-100 rounded-xl px-4 py-3">
                <i class="pi pi-lock text-amber-500 text-xs mt-0.5"></i>
                <div class="flex-grow">
                  <span class="text-[10px] font-medium text-amber-700 uppercase tracking-widest">Formulário em uso</span>
                  <p class="text-[11px] text-amber-700/80 leading-relaxed mt-1">
                    A estrutura está bloqueada porque já existem inspeções lançadas. É possível reordenar os itens e
                    editar o título/descrição. Para mudar a estrutura, gere uma cópia editável.
                  </p>
                </div>
                <div class="flex flex-col gap-2 shrink-0">
                  <p-button label="Salvar dados" icon="pi pi-check" [loading]="saving()" (onClick)="salvarDados()" styleClass="!rounded-lg !bg-white !border-amber-200 !text-amber-700 !font-medium !text-[10px] !px-4 !py-2"></p-button>
                  <p-button label="Duplicar" icon="pi pi-copy" (onClick)="duplicar()" styleClass="!rounded-lg !bg-amber-500 !border-amber-500 !text-white !font-medium !text-[10px] !px-4 !py-2"></p-button>
                </div>
              </div>
            }
          </div>

          <!-- CORPO -->
          <div class="px-5 md:px-8 py-6 flex flex-col gap-8">
            @for (grupo of grupos(); track grupo.uid; let gi = $index) {
              <div class="flex flex-col gap-4">
                <!-- Marcador do grupo -->
                <div class="flex items-center gap-3 border-l-2 border-primary-700 pl-3">
                  <span class="text-[11px] font-medium text-gray-400">{{ formatarNumero(gi + 1) }}</span>
                  <input pInputText placeholder="Título do grupo (ex: Kit SMC)" class="doc-input flex-grow" [(ngModel)]="grupo.titulo" [disabled]="emUso()" />
                  <div class="flex items-center gap-0.5 shrink-0">
                    <p-button icon="pi pi-chevron-up" [text]="true" [rounded]="true" pTooltip="Subir" [disabled]="gi === 0" (onClick)="moverGrupo(gi, -1)" styleClass="!text-slate-400 !w-7 !h-7"></p-button>
                    <p-button icon="pi pi-chevron-down" [text]="true" [rounded]="true" pTooltip="Descer" [disabled]="gi === grupos().length - 1" (onClick)="moverGrupo(gi, 1)" styleClass="!text-slate-400 !w-7 !h-7"></p-button>
                    @if (!emUso()) {
                      <p-button icon="pi pi-trash" [text]="true" [rounded]="true" pTooltip="Remover grupo" (onClick)="removerGrupo(gi)" styleClass="!text-red-400 !w-7 !h-7"></p-button>
                    }
                  </div>
                </div>

                <!-- Perguntas -->
                <div class="flex flex-col gap-3 md:pl-5">
                  @for (pergunta of grupo.perguntas; track pergunta.uid; let pi = $index) {
                    <div class="rounded-xl border border-slate-100 bg-slate-50/30 px-4 py-4 flex flex-col gap-3">
                      <div class="flex items-start gap-3">
                        <span class="text-[10px] font-medium text-gray-400 mt-2.5 shrink-0">{{ gi + 1 }}.{{ pi + 1 }}</span>
                        <input pInputText placeholder="Enunciado da pergunta" class="doc-input flex-grow" [(ngModel)]="pergunta.enunciado" [disabled]="emUso()" />
                        <div class="flex items-center gap-0.5 shrink-0">
                          <p-button icon="pi pi-chevron-up" [text]="true" [rounded]="true" pTooltip="Subir" [disabled]="pi === 0" (onClick)="moverPergunta(grupo, pi, -1)" styleClass="!text-slate-400 !w-7 !h-7"></p-button>
                          <p-button icon="pi pi-chevron-down" [text]="true" [rounded]="true" pTooltip="Descer" [disabled]="pi === grupo.perguntas.length - 1" (onClick)="moverPergunta(grupo, pi, 1)" styleClass="!text-slate-400 !w-7 !h-7"></p-button>
                          @if (!emUso()) {
                            <p-button icon="pi pi-trash" [text]="true" [rounded]="true" pTooltip="Remover pergunta" (onClick)="removerPergunta(grupo, pi)" styleClass="!text-red-400 !w-7 !h-7"></p-button>
                          }
                        </div>
                      </div>

                      <div class="flex flex-wrap items-end gap-3 md:pl-7">
                        <div class="flex flex-col gap-1.5">
                          <label class="filter-label !text-slate-600 !text-[11px]">Tipo de resposta</label>
                          <p-select
                            [options]="tipoOptions"
                            [(ngModel)]="pergunta.tipoResposta"
                            optionLabel="label"
                            optionValue="value"
                            class="doc-select !w-56"
                            size="small"
                            appendTo="body"
                            [disabled]="emUso()"
                            (onChange)="onTipoChange(pergunta)"
                          ></p-select>
                        </div>
                        <label class="flex items-center gap-2 h-[34px] cursor-pointer">
                          <p-checkbox [binary]="true" size="small" [(ngModel)]="pergunta.obrigatoria" [disabled]="emUso()"></p-checkbox>
                          <span class="text-[10px] font-medium text-gray-500 uppercase tracking-widest">Obrigatória</span>
                        </label>
                      </div>

                      @if (isObjetivo(pergunta.tipoResposta)) {
                        <div class="md:pl-7 flex flex-col gap-2 mt-1">
                          <span class="text-[10px] font-medium text-gray-400 uppercase tracking-widest">Opções de resposta</span>
                          @for (opcao of pergunta.opcoes; track opcao.uid; let oi = $index) {
                            <div class="flex items-center gap-2">
                              <i class="pi pi-circle text-[7px] text-slate-300"></i>
                              <input pInputText placeholder="Texto da opção" class="doc-input flex-grow" [(ngModel)]="opcao.texto" [disabled]="emUso()" />
                              <label class="flex items-center gap-1.5 h-[34px] px-2.5 rounded-md border shrink-0 cursor-pointer transition-colors"
                                [ngClass]="opcao.padrao ? 'border-blue-200 bg-blue-50/40' : 'border-slate-200 bg-white'">
                                <p-checkbox [binary]="true" size="small" [(ngModel)]="opcao.padrao" [disabled]="emUso() || opcao.geraDesvio" (onChange)="onPadraoChange(pergunta, opcao)"></p-checkbox>
                                <span class="text-[9px] font-medium uppercase tracking-widest" [ngClass]="opcao.padrao ? 'text-blue-600' : 'text-gray-400'">Padrão</span>
                              </label>
                              <label class="flex items-center gap-1.5 h-[34px] px-2.5 rounded-md border shrink-0 cursor-pointer transition-colors"
                                [ngClass]="opcao.geraDesvio ? 'border-red-200 bg-red-50/40' : 'border-slate-200 bg-white'">
                                <p-checkbox [binary]="true" size="small" [(ngModel)]="opcao.geraDesvio" [disabled]="emUso()" (onChange)="onGeraDesvioChange(opcao)"></p-checkbox>
                                <span class="text-[9px] font-medium uppercase tracking-widest" [ngClass]="opcao.geraDesvio ? 'text-red-500' : 'text-gray-400'">Gera desvio</span>
                              </label>
                              @if (opcao.geraDesvio) {
                                <p-select
                                  [options]="gravidadeOptions"
                                  [(ngModel)]="opcao.gravidade"
                                  optionLabel="label"
                                  optionValue="value"
                                  placeholder="Gravidade *"
                                  class="doc-select !w-32 border-red-200"
                                  size="small"
                                  appendTo="body"
                                  [disabled]="emUso()"
                                ></p-select>
                              }
                              <div class="flex items-center gap-0.5 shrink-0">
                                <p-button icon="pi pi-chevron-up" [text]="true" [rounded]="true" [disabled]="oi === 0" (onClick)="moverOpcao(grupo, pergunta, oi, -1)" styleClass="!text-slate-300 !w-6 !h-6"></p-button>
                                <p-button icon="pi pi-chevron-down" [text]="true" [rounded]="true" [disabled]="oi === pergunta.opcoes.length - 1" (onClick)="moverOpcao(grupo, pergunta, oi, 1)" styleClass="!text-slate-300 !w-6 !h-6"></p-button>
                                @if (!emUso()) {
                                  <p-button icon="pi pi-times" [text]="true" [rounded]="true" (onClick)="removerOpcao(pergunta, oi)" styleClass="!text-red-300 !w-6 !h-6"></p-button>
                                }
                              </div>
                            </div>
                          }
                          @if (!emUso()) {
                            <button type="button" (click)="adicionarOpcao(pergunta)" class="flex items-center gap-1.5 text-[10px] font-medium text-primary-700 uppercase tracking-widest mt-1 w-fit">
                              <i class="pi pi-plus text-[9px]"></i> Adicionar opção
                            </button>
                          }
                        </div>
                      } @else {
                        <div class="md:pl-7 text-[10px] text-gray-400 tracking-wide">
                          <i class="pi pi-info-circle text-[9px]"></i> Resposta livre — preenchida em campo, sem opções.
                        </div>
                      }
                    </div>
                  }

                  @if (!emUso()) {
                    <button type="button" (click)="adicionarPergunta(grupo)" class="flex items-center gap-1.5 text-[10px] font-medium text-primary-700 uppercase tracking-widest w-fit">
                      <i class="pi pi-plus text-[9px]"></i> Adicionar pergunta
                    </button>
                  }
                </div>
              </div>
            }

            @if (!emUso()) {
              <button type="button" (click)="adicionarGrupo()" class="flex items-center justify-center gap-2 text-[10px] font-medium text-gray-500 uppercase tracking-widest border border-dashed border-slate-200 rounded-xl py-3 hover:bg-slate-50/50 transition-colors">
                <i class="pi pi-plus text-[10px]"></i> Adicionar grupo
              </button>
            }
          </div>
        </div>
      }
    </div>

    @if (!loading() && !emUso()) {
      <div class="fixed bottom-6 right-6 md:right-12 z-50">
        <p-button [label]="formId() ? 'Salvar estrutura' : 'Criar formulário'" icon="pi pi-check" [loading]="saving()" (onClick)="salvar()" styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !text-white !font-medium !text-[10px] !tracking-widest !uppercase !px-7 !py-3 shadow-lg"></p-button>
      </div>
    }
  `,
  styles: [`
    :host ::ng-deep {
      .doc-input.p-inputtext, .doc-select .p-select-label, .doc-select .p-select-placeholder {
        font-size: 11px !important;
        font-weight: 500 !important;
        color: #374151;
        height: 34px !important;
        display: flex;
        align-items: center;
        border-radius: 6px !important;
      }
      .doc-input.p-inputtext { border-color: #e5e7eb !important; }
      .doc-select .p-select { width: 100%; height: 34px; border-color: #e5e7eb !important; border-radius: 6px !important; }
      .p-breadcrumb .p-menuitem-text { font-size: 0.7rem; font-weight: 500; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.08em; }
    }
  `]
})
export class FormularioBuilderComponent implements OnInit {
  private sst = inject(SstService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);

  protected loading = signal(false);
  protected saving = signal(false);
  protected formId = signal<string | null>(null);
  protected emUso = signal(false);
  protected ativo = signal(true);

  protected titulo = '';
  protected descricao = '';
  protected grupos = signal<BuilderGrupo[]>([]);

  protected breadcrumbItems: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'SST' },
    { label: 'Formulários', routerLink: '/sst/formularios' },
    { label: 'Construtor' }
  ];

  protected tipoOptions = (Object.keys(TIPO_RESPOSTA_LABELS) as TipoResposta[]).map((value) => ({
    label: TIPO_RESPOSTA_LABELS[value],
    value
  }));

  protected gravidadeOptions = (Object.keys(GRAVIDADE_LABELS) as GravidadeDesvio[]).map((value) => ({
    label: GRAVIDADE_LABELS[value],
    value
  }));

  protected isObjetivo = isTipoObjetivo;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'novo') {
      this.carregar(id);
    } else {
      this.grupos.set([this.novoGrupo()]);
    }
  }

  protected formatarNumero(n: number): string {
    return n < 10 ? `0${n}` : String(n);
  }

  private carregar(id: string) {
    this.loading.set(true);
    this.sst.buscarFormularioPorId(id).subscribe({
      next: (form) => {
        this.mapearParaBuilder(form);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erro ao carregar', detail: getErrorMessage(err) });
        this.router.navigate(['/sst/formularios']);
      }
    });
  }

  private mapearParaBuilder(form: FormularioInspecaoResponse) {
    this.formId.set(form.id);
    this.emUso.set(form.emUso);
    this.ativo.set(form.ativo);
    this.titulo = form.titulo;
    this.descricao = form.descricao ?? '';
    this.grupos.set(
      (form.grupos ?? [])
        .slice()
        .sort((a, b) => a.ordem - b.ordem)
        .map((g) => ({
          id: g.id,
          uid: uid(),
          titulo: g.titulo,
          perguntas: (g.perguntas ?? [])
            .slice()
            .sort((a, b) => a.ordem - b.ordem)
            .map((p) => ({
              id: p.id,
              uid: uid(),
              enunciado: p.enunciado,
              tipoResposta: p.tipoResposta,
              obrigatoria: p.obrigatoria,
              opcoes: (p.opcoes ?? [])
                .slice()
                .sort((a, b) => a.ordem - b.ordem)
                .map((o) => ({ id: o.id, uid: uid(), texto: o.texto, geraDesvio: o.geraDesvio, gravidade: o.gravidade ?? null, padrao: o.padrao ?? false }))
            }))
        }))
    );
  }

  // ---------- factories ----------
  private novoGrupo(): BuilderGrupo {
    return { uid: uid(), titulo: '', perguntas: [this.novaPergunta()] };
  }
  private novaPergunta(): BuilderPergunta {
    return {
      uid: uid(),
      enunciado: '',
      tipoResposta: 'MULTIPLA_ESCOLHA',
      obrigatoria: true,
      opcoes: [this.novaOpcao('Sim', false, null, true), this.novaOpcao('Não', true)]
    };
  }
  private novaOpcao(texto = '', geraDesvio = false, gravidade: GravidadeDesvio | null = null, padrao = false): BuilderOpcao {
    return { uid: uid(), texto, geraDesvio, gravidade: geraDesvio ? (gravidade || 'MEDIA') : null, padrao: geraDesvio ? false : padrao };
  }

  // ---------- estrutura (somente quando editável) ----------
  protected adicionarGrupo() {
    this.grupos.update((gs) => [...gs, this.novoGrupo()]);
  }
  protected removerGrupo(index: number) {
    this.grupos.update((gs) => gs.filter((_, i) => i !== index));
  }
  protected adicionarPergunta(grupo: BuilderGrupo) {
    grupo.perguntas.push(this.novaPergunta());
    this.grupos.update((gs) => [...gs]);
  }
  protected removerPergunta(grupo: BuilderGrupo, index: number) {
    grupo.perguntas.splice(index, 1);
    this.grupos.update((gs) => [...gs]);
  }
  protected adicionarOpcao(pergunta: BuilderPergunta) {
    pergunta.opcoes.push(this.novaOpcao());
    this.grupos.update((gs) => [...gs]);
  }
  protected removerOpcao(pergunta: BuilderPergunta, index: number) {
    pergunta.opcoes.splice(index, 1);
    this.grupos.update((gs) => [...gs]);
  }

  protected onTipoChange(pergunta: BuilderPergunta) {
    if (isTipoObjetivo(pergunta.tipoResposta)) {
      if (pergunta.opcoes.length === 0) {
        pergunta.opcoes = [this.novaOpcao('Sim', false), this.novaOpcao('Não', true)];
      }
    } else {
      pergunta.opcoes = [];
    }
    this.grupos.update((gs) => [...gs]);
  }

  protected onPadraoChange(pergunta: BuilderPergunta, opcao: BuilderOpcao) {
    if (opcao.padrao) {
      opcao.geraDesvio = false;
      opcao.gravidade = null;
      if (pergunta.tipoResposta === 'MULTIPLA_ESCOLHA') {
        pergunta.opcoes.forEach((o) => {
          if (o !== opcao) o.padrao = false;
        });
      }
    }
  }

  protected onGeraDesvioChange(opcao: BuilderOpcao) {
    if (opcao.geraDesvio) {
      opcao.padrao = false;
      if (!opcao.gravidade) opcao.gravidade = 'MEDIA';
    } else {
      opcao.gravidade = null;
    }
  }

  // ---------- reordenação (local + endpoint quando em uso) ----------
  protected moverGrupo(index: number, dir: -1 | 1) {
    const gs = this.grupos();
    const target = index + dir;
    if (target < 0 || target >= gs.length) return;
    const novo = [...gs];
    [novo[index], novo[target]] = [novo[target], novo[index]];
    this.grupos.set(novo);
    if (this.emUso() && this.formId()) {
      const ids = novo.map((g) => g.id!).filter(Boolean);
      this.persistirOrdem(this.sst.reordenarGrupos(this.formId()!, { idsNaOrdem: ids }), gs);
    }
  }

  protected moverPergunta(grupo: BuilderGrupo, index: number, dir: -1 | 1) {
    const snapshot = this.grupos();
    const target = index + dir;
    if (target < 0 || target >= grupo.perguntas.length) return;
    [grupo.perguntas[index], grupo.perguntas[target]] = [grupo.perguntas[target], grupo.perguntas[index]];
    this.grupos.update((gs) => [...gs]);
    if (this.emUso() && this.formId() && grupo.id) {
      const ids = grupo.perguntas.map((p) => p.id!).filter(Boolean);
      this.persistirOrdem(this.sst.reordenarPerguntas(this.formId()!, grupo.id, { idsNaOrdem: ids }), snapshot);
    }
  }

  protected moverOpcao(grupo: BuilderGrupo, pergunta: BuilderPergunta, index: number, dir: -1 | 1) {
    const snapshot = this.grupos();
    const target = index + dir;
    if (target < 0 || target >= pergunta.opcoes.length) return;
    [pergunta.opcoes[index], pergunta.opcoes[target]] = [pergunta.opcoes[target], pergunta.opcoes[index]];
    this.grupos.update((gs) => [...gs]);
    if (this.emUso() && this.formId() && grupo.id && pergunta.id) {
      const ids = pergunta.opcoes.map((o) => o.id!).filter(Boolean);
      this.persistirOrdem(
        this.sst.reordenarOpcoes(this.formId()!, grupo.id, pergunta.id, { idsNaOrdem: ids }),
        snapshot
      );
    }
  }

  private persistirOrdem(obs: ReturnType<SstService['reordenarGrupos']>, rollback: BuilderGrupo[]) {
    obs.subscribe({
      next: (form) => {
        if (form) this.mapearParaBuilder(form);
      },
      error: (err) => {
        this.grupos.set(rollback);
        this.messageService.add({ severity: 'error', summary: 'Falha ao reordenar', detail: getErrorMessage(err) });
      }
    });
  }

  // ---------- validação + save ----------
  private validar(): string | null {
    if (!this.titulo.trim()) return 'Informe o título do formulário.';
    const grupos = this.grupos();
    if (grupos.length === 0) return 'Adicione ao menos um grupo.';
    for (const [gi, g] of grupos.entries()) {
      if (!g.titulo.trim()) return `Informe o título do grupo ${gi + 1}.`;
      if (g.perguntas.length === 0) return `O grupo "${g.titulo}" precisa de ao menos uma pergunta.`;
      for (const [pi, p] of g.perguntas.entries()) {
        if (!p.enunciado.trim()) return `Informe o enunciado da pergunta ${gi + 1}.${pi + 1}.`;
        if (isTipoObjetivo(p.tipoResposta)) {
          if (p.opcoes.length === 0) return `A pergunta "${p.enunciado}" precisa de ao menos uma opção.`;
          if (p.opcoes.some((o) => !o.texto.trim())) return `Preencha o texto de todas as opções da pergunta "${p.enunciado}".`;
          if (p.opcoes.some((o) => o.geraDesvio && !o.gravidade)) return `Selecione a gravidade para as opções configuradas para gerar desvio na pergunta "${p.enunciado}".`;
          const padroes = p.opcoes.filter((o) => o.padrao);
          if (p.tipoResposta === 'MULTIPLA_ESCOLHA' && padroes.length > 1) {
            return `A pergunta "${p.enunciado}" não pode ter mais de uma opção padrão.`;
          }
          if (p.opcoes.some((o) => o.padrao && o.geraDesvio)) {
            return `A opção padrão não pode gerar desvio na pergunta "${p.enunciado}".`;
          }
        }
      }
    }
    return null;
  }

  private montarRequest(): CriarFormularioRequest {
    return {
      titulo: this.titulo.trim(),
      descricao: this.descricao.trim() || undefined,
      grupos: this.grupos().map((g) => ({
        titulo: g.titulo.trim(),
        perguntas: g.perguntas.map((p) => ({
          enunciado: p.enunciado.trim(),
          tipoResposta: p.tipoResposta,
          obrigatoria: p.obrigatoria,
          opcoes: isTipoObjetivo(p.tipoResposta)
            ? p.opcoes.map((o) => ({
                texto: o.texto.trim(),
                geraDesvio: o.geraDesvio,
                gravidade: o.geraDesvio ? (o.gravidade || 'MEDIA') : null,
                padrao: o.padrao
              }))
            : []
        }))
      }))
    };
  }

  protected salvar() {
    const erro = this.validar();
    if (erro) {
      this.messageService.add({ severity: 'warn', summary: 'Revise o formulário', detail: erro });
      return;
    }
    this.saving.set(true);
    const request = this.montarRequest();
    const id = this.formId();
    const obs = id ? this.sst.atualizarEstrutura(id, request) : this.sst.criarFormulario(request);
    obs.subscribe({
      next: () => {
        this.saving.set(false);
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Formulário salvo.' });
        this.router.navigate(['/sst/formularios']);
      },
      error: (err) => {
        this.saving.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erro ao salvar', detail: getErrorMessage(err) });
      }
    });
  }

  protected salvarDados() {
    if (!this.titulo.trim()) {
      this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'Informe o título.' });
      return;
    }
    const id = this.formId();
    if (!id) return;
    this.saving.set(true);
    this.sst.atualizarDados(id, { titulo: this.titulo.trim(), descricao: this.descricao.trim() || undefined }).subscribe({
      next: () => {
        this.saving.set(false);
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Dados atualizados.' });
      },
      error: (err) => {
        this.saving.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
      }
    });
  }

  protected duplicar() {
    const id = this.formId();
    if (!id) return;
    this.sst.duplicarFormulario(id).subscribe({
      next: (res) => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Cópia criada.' });
        if (res) {
          this.formId.set(null);
          this.router.navigate(['/sst/formularios', res.id]).then(() => window.location.reload());
        }
      },
      error: (err) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) })
    });
  }

  protected voltar() {
    this.router.navigate(['/sst/formularios']);
  }
}
