import { CommonModule } from '@angular/common';
import { Component, input, model, output, effect, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MenuItem } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';
import { SplitterModule } from 'primeng/splitter';
import { FavoritosService } from '../../../core/services/favoritos.service';
import { CesButtonComponent, CesIconButtonComponent, CesStatusComponent } from '@ces/ui';

@Component({
  selector: 'app-movement-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    DividerModule,
    CheckboxModule,
    TooltipModule,
    CardModule,
    SplitterModule,
    CesButtonComponent,
    CesIconButtonComponent,
    CesStatusComponent
  ],
  template: `
    <div class="flex flex-col gap-3 pb-32 relative min-h-[calc(100vh-180px)]">
      <!-- Top Layout: Breadcrumb and Icons -->
      <div class="flex justify-between items-center mb-1">
        <p-breadcrumb [model]="breadcrumb()" styleClass="!bg-transparent !border-0 !p-0"></p-breadcrumb>

        <div class="flex items-center gap-1.5">
          <ces-icon-button
            [icon]="favs.isCurrentPageFavorite() ? 'pi pi-star-fill' : 'pi pi-star'"
            [label]="favs.isCurrentPageFavorite() ? 'Remover dos favoritos' : 'Adicionar aos favoritos'"
            size="icon-sm"
            [className]="favs.isCurrentPageFavorite() ? '!text-yellow-500' : ''"
            (pressed)="favs.toggleFavorite(title())"
          ></ces-icon-button>
          <ces-icon-button
            icon="pi pi-question-circle"
            label="Abrir ajuda"
            size="icon-sm"
            (pressed)="onHelp.emit()"
          ></ces-icon-button>
        </div>
      </div>

      <!-- Header Title -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h2 class="text-sm font-bold text-[var(--ces-foreground)] uppercase tracking-tight">{{ title() }}</h2>
        @if (step() > 1) {
          <p-button
            label="Editar Cabeçalho"
            icon="pi pi-pencil"
            [text]="true"
            (onClick)="onEditHeader()"
            styleClass="!text-[10px] !font-medium !tracking-wider"
          ></p-button>
        }
      </div>

      <p-divider class="!my-1 !border-[var(--ces-border)]"></p-divider>

      <!-- Stage 1: Header Configuration -->
      <div class="bg-[var(--ces-surface)] text-[var(--ces-foreground)] rounded-2xl border border-[var(--ces-border)] shadow-sm p-4 animate-in fade-in slide-in-from-bottom-2 duration-500">
        <div [class.pointer-events-none]="step() > 1" [class.opacity-70]="step() > 1">
          <ng-content select="[header]"></ng-content>

          @if (showTotal()) {
            <div class="mt-4 flex items-center gap-3">
              <div class="flex items-center gap-2 bg-[var(--ces-surface-subtle)] px-3 py-2 rounded-lg border border-[var(--ces-border)] w-fit">
                <p-checkbox
                  [(ngModel)]="totalTransfer"
                  [binary]="true"
                  class="custom-checkbox"
                  [disabled]="step() > 1 || isTotalDisabled()"
                  inputId="totalTransfer"
                  size="small"
                ></p-checkbox>
                <label for="totalTransfer" class="filter-label !text-[var(--ces-foreground-muted)] !text-[11px] cursor-pointer">{{ totalLabel() }}</label>
              </div>
              <i class="pi pi-info-circle text-[var(--ces-foreground-subtle)] text-xs" pTooltip="Ao marcar, todos os itens com saldo no estoque de origem serão adicionados automaticamente."></i>
            </div>
          }
        </div>

        @if (step() === 1) {
          <div class="mt-6 flex justify-start">
            <ces-button
              label="Salvar"
              icon="pi pi-check"
              [disabled]="!isHeaderValid()"
              (pressed)="handleSaveHeader()"
            ></ces-button>
          </div>
        }
      </div>

      <!-- Stage 2 & 3: Item Addition and Review -->
      @if (step() >= 2) {
        <div class="animate-in fade-in slide-in-from-bottom-4 duration-500 flex-1 flex flex-col">
          <p-divider align="left" class="!my-6">
            <span class="text-[10px] font-medium text-[var(--ces-foreground-subtle)] uppercase tracking-[0.2em]">Itens da Movimentação</span>
          </p-divider>

          <p-splitter
            [panelSizes]="step() === 3 ? [35, 65] : [100, 0]"
            [minSizes]="step() === 3 ? [20, 30] : [100, 0]"
            class="!border-0 !bg-transparent gap-4 custom-movement-splitter"
            [class.hide-gutter]="step() === 2"
          >
            <ng-template pTemplate>
              <!-- Item Form Panel (Always projected here) -->
              <div class="bg-[var(--ces-surface)] text-[var(--ces-foreground)] rounded-2xl border border-[var(--ces-border)] shadow-sm p-4 h-fit w-full">
                <h3 class="text-[11px] font-medium text-[var(--ces-foreground-muted)] uppercase tracking-wider mb-4">Adicionar Item</h3>
                <div class="mt-4">
                  <ng-content select="[itemForm]"></ng-content>
                </div>

                @if (showAddButton()) {
                  <div class="mt-6 flex justify-start">
                    <ces-button
                      label="Adicionar"
                      icon="pi pi-plus"
                      variant="success"
                      (pressed)="onAddItem.emit()"
                    ></ces-button>
                  </div>
                }
              </div>
            </ng-template>

            <ng-template pTemplate>
               <!-- Review Table Panel (Always projected here, hidden via CSS if step 2) -->
               <div [class.hidden]="step() === 2" class="bg-[var(--ces-surface)] text-[var(--ces-foreground)] rounded-2xl border border-[var(--ces-border)] shadow-sm overflow-hidden w-full h-fit animate-in fade-in slide-in-from-right-4 duration-500">
                  <div class="p-4 border-b border-[var(--ces-border)] flex justify-between items-center bg-[var(--ces-surface-subtle)]">
                    <h3 class="text-[11px] font-medium text-[var(--ces-foreground-muted)] uppercase tracking-wider">Itens Adicionados</h3>
                    <ces-status tone="brand">{{ itemsCount() }} {{ itemsCount() === 1 ? 'item' : 'itens' }}</ces-status>
                  </div>
                  <div class="overflow-x-auto">
                    <ng-content select="[itemsTable]"></ng-content>
                  </div>
               </div>
            </ng-template>
          </p-splitter>
        </div>
      }

      <!-- Final Action Buttons -->
      <div class="flex mt-8 gap-3 items-center">
        @if (step() > 1) {
          <ces-button
            label="Cancelar"
            icon="pi pi-times"
            variant="secondary"
            (pressed)="onCancel.emit()"
          ></ces-button>

          @if (hasItems()) {
            <ces-button
              [label]="entityLabel()"
              icon="pi pi-check-circle"
              variant="success"
              (pressed)="onConfirm.emit()"
            ></ces-button>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    :host ::ng-deep {
      .custom-input, .p-inputtext, .p-select-label, .p-datepicker-input, .p-select-placeholder {
        font-size: 10px !important;
        height: 30px !important;
        display: flex;
        align-items: center;
        border-radius: 8px !important;
      }

      .p-select, .p-datepicker, .p-inputtext {
        width: 100%;
        border-color: var(--ces-border) !important;
      }

      .custom-checkbox .p-checkbox-box {
        width: 18px !important;
        height: 18px !important;
        border-radius: 4px !important;
      }

      .p-checkbox-label {
        font-size: 11px !important;
        font-weight: 600 !important;
        color: var(--ces-foreground-muted) !important;
        margin-left: 8px !important;
      }

      .p-breadcrumb { background: transparent; border: 0; padding: 0; }
      .p-breadcrumb .p-menuitem-text { font-size: 9px; font-weight: 700; color: var(--ces-foreground-muted); text-transform: uppercase; }
      .p-breadcrumb-list { display: flex; align-items: center; gap: 4px; }

      .p-datatable .p-datatable-tbody > tr > td {
        font-size: 10px !important;
        padding: 0.75rem 1rem !important;
      }

      .custom-movement-splitter.hide-gutter > .p-splitter-gutter {
        display: none !important;
      }
    }
  `]
})
export class MovementPageComponent {
  favs = inject(FavoritosService);

  title = input.required<string>();
  breadcrumb = input.required<MenuItem[]>();
  entityLabel = input<string>('Finalizar');
  totalLabel = input<string>('Transferência Total');
  hasItems = input<boolean>(false);
  itemsCount = input<number>(0);
  isHeaderValid = input<boolean>(true);
  showAddButton = input<boolean>(true);
  showTotal = input<boolean>(true);
  isTotalDisabled = input<boolean>(false);

  step = model<number>(1);
  totalTransfer = model<boolean>(false);

  onSaveHeader = output<void>();
  onAddItem = output<void>();
  onConfirm = output<void>();
  onCancel = output<void>();
  onHelp = output<void>();

  constructor() {
    effect(() => {
      const items = this.hasItems();
      const currentStep = this.step();

      if (currentStep >= 2) {
        if (items && currentStep === 2) {
          this.step.set(3);
        } else if (!items && currentStep === 3) {
          this.step.set(2);
        }
      }
    });
  }

  handleSaveHeader() {
    this.onSaveHeader.emit();
  }

  onEditHeader() {
    this.step.set(1);
  }

  onBack() {
    if (this.step() >= 2) {
      this.step.set(1);
    } else {
      this.onCancel.emit();
    }
  }
}
