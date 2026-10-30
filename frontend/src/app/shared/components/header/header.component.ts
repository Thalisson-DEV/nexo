import { Component, inject, input, output, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { PopoverModule, Popover } from 'primeng/popover';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../../../core/services/auth.service';
import { RouterLink, Router } from "@angular/router";
import { ChangePasswordDialogComponent } from '../change-password-dialog/change-password-dialog.component';
import { FavoritosService } from '../../../core/services/favoritos.service';
import { ThemeService } from '../../../core/services/theme.service';
import { CesShortcutsPreference } from '@ces/ui';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { NotificacoesStore } from '../../../core/services/notificacoes-store.service';
import { ConnectivityService } from '../../../core/services/connectivity.service';
import { MenuItem, MessageService } from 'primeng/api';
import { NotificacoesPanelComponent } from '../notificacoes-panel/notificacoes-panel.component';
import { BuscaTelasComponent } from '../busca-telas/busca-telas.component';
import { ViewStateStore } from '../../../core/navigation/view-state.store';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, ButtonModule, AvatarModule, PopoverModule, DialogModule, TooltipModule, RouterLink, ChangePasswordDialogComponent, NotificacoesPanelComponent, BuscaTelasComponent],
  template: `
    <header class="h-16 shrink-0 z-50 bg-[var(--ces-surface)]/95 text-[var(--ces-foreground)] backdrop-blur-md border-b border-[var(--ces-border)] flex items-center justify-between px-4 lg:px-6">
      <!-- Logo & Mobile Toggle Area -->
      <div class="flex items-center gap-2 lg:gap-0">
        @if (isMobile()) {
          <p-button
            icon="pi pi-bars"
            [text]="true"
            (onClick)="toggleMenu.emit()"
            styleClass="!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-10 !h-10 !p-0 !rounded-xl transition-all"
          ></p-button>
        }

        <div class="flex items-center h-8" [class.ml-2]="!isMobile()">
          <a [routerLink]="'/home'" class="flex items-center h-full">
            <img src="/logo_vertical.svg" alt="Nexo" class="h-full w-auto object-contain">
          </a>
        </div>
      </div>

      <!-- User Actions Area -->
      <div class="flex items-center gap-1.5 lg:gap-2">
        <!-- Busca rápida de telas (Ctrl+K) -->
        <app-busca-telas [menuItems]="menuItems()"></app-busca-telas>

        <!-- Botão dedicado de alternar tema (Claro / Escuro) com aviso sutil de Beta -->
        <div class="relative">
          <p-button
            [icon]="theme.isDarkMode() ? 'pi pi-sun text-amber-500 text-base' : 'pi pi-moon text-[var(--ces-foreground-muted)] text-sm'"
            [text]="true"
            (onClick)="toggleTheme()"
            [pTooltip]="theme.isDarkMode() ? 'Mudar para Tema Claro' : 'Mudar para Tema Escuro (Beta)'"
            tooltipPosition="bottom"
            aria-label="Alternar tema claro e escuro"
            styleClass="!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-10 !h-10 !p-0 !rounded-xl transition-all"
          ></p-button>
          <span
            class="absolute -top-1 -right-1 text-[8px] font-extrabold uppercase tracking-wider px-1 py-0.2 rounded-full bg-amber-500/15 text-amber-600 dark:bg-amber-400/20 dark:text-amber-300 border border-amber-500/30 pointer-events-none select-none"
          >
            Beta
          </span>
        </div>

        <!-- Favorites Button -->
        <p-button
          #favButton
          icon="pi pi-star"
          [text]="true"
          (onClick)="favOp.toggle($event)"
          styleClass="!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-10 !h-10 !p-0 !rounded-xl transition-all"
        ></p-button>

        <!-- Notifications Button -->
        <div class="relative">
          <p-button
            icon="pi pi-bell"
            [text]="true"
            (onClick)="notificacoes.carregarRecentes(); notifOp.toggle($event)"
            styleClass="!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-10 !h-10 !p-0 !rounded-xl transition-all"
          ></p-button>
          @if (notificacoes.temNaoLidas()) {
            <span
              class="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 flex items-center justify-center rounded-full bg-red-500 border border-white text-[9px] font-bold text-white"
            >
              {{ notificacoes.badge() }}
            </span>
          }
        </div>

        <!-- User Profile Button -->
        <p-button
          icon="pi pi-user"
          [text]="true"
          (onClick)="op.toggle($event)"
          styleClass="!text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-10 !h-10 !p-0 !rounded-xl transition-all"
        ></p-button>
      </div>
    </header>

    <app-change-password-dialog #changePasswordDialog></app-change-password-dialog>

    <!-- Favorites Popover -->
    <p-popover #favOp styleClass="!p-0 !border-0 !bg-transparent [&_.p-popover-content]:!p-0">
      <div class="flex flex-col w-72 lg:w-80 bg-[var(--ces-surface)] text-[var(--ces-foreground)] border border-[var(--ces-border)] rounded-2xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
        <div class="p-4 border-b border-[var(--ces-border)] bg-[var(--ces-surface-subtle)] flex items-center justify-between">
          <span class="text-sm font-bold text-[var(--ces-foreground)]">Meus Favoritos</span>
          <span class="text-[10px] bg-yellow-500/20 text-yellow-600 dark:text-yellow-400 px-2 py-0.5 rounded-full font-bold">
            {{ favs.favoritos().length }} Salvos
          </span>
        </div>

        <div class="max-h-[400px] overflow-y-auto custom-scrollbar">
          @if (favs.favoritos().length > 0) {
            <div class="flex flex-col p-1">
              @for (fav of favs.favoritos(); track fav.id) {
                <div
                  (click)="abrirFavorito(fav.rota); favOp.hide()"
                  class="flex items-center justify-between p-3 rounded-xl hover:bg-[var(--ces-brand-soft)] cursor-pointer transition-all group"
                >
                  <div class="flex items-center gap-3 overflow-hidden">
                    <div class="w-8 h-8 rounded-lg bg-[var(--ces-surface-subtle)] flex items-center justify-center shrink-0 group-hover:bg-[var(--ces-surface)] group-hover:shadow-sm transition-all border border-[var(--ces-border)]">
                      <i [class]="fav.icone || 'pi pi-star'" class="text-[var(--ces-foreground-subtle)] group-hover:text-[var(--ces-brand)] text-sm"></i>
                    </div>
                    <span class="text-xs font-medium text-[var(--ces-foreground-muted)] group-hover:text-[var(--ces-foreground)] truncate">{{ fav.titulo }}</span>
                  </div>
                  <p-button
                    icon="pi pi-trash"
                    [text]="true"
                    severity="danger"
                    (onClick)="$event.stopPropagation(); favs.removerFavorito(fav.id)"
                    styleClass="!w-8 !h-8 !p-0 !opacity-0 group-hover:!opacity-100 transition-opacity"
                  ></p-button>
                </div>
              }
            </div>
          } @else {
            <div class="flex flex-col items-center justify-center py-12 px-6 text-center">
              <div class="w-12 h-12 bg-yellow-500/10 rounded-full flex items-center justify-center mb-4">
                <i class="pi pi-star text-yellow-500 text-2xl"></i>
              </div>
              <span class="text-sm font-medium text-[var(--ces-foreground-muted)]">Nenhum favorito</span>
              <p class="text-[11px] text-[var(--ces-foreground-subtle)] mt-1 leading-relaxed">
                Clique na estrela das páginas para adicioná-las aqui.
              </p>
            </div>
          }

        </div>
      </div>
    </p-popover>

    <!-- Profile Popover -->
    <p-popover #op styleClass="!p-0 !border-0 !bg-transparent [&_.p-popover-content]:!p-0">
      <div class="flex flex-col w-72 bg-[var(--ces-surface)] text-[var(--ces-foreground)] border border-[var(--ces-border)] rounded-2xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
        <!-- User Info Section -->
        <div class="p-5 border-b border-[var(--ces-border)] bg-[var(--ces-surface-subtle)] flex items-center gap-4">
          <p-avatar icon="pi pi-user" shape="circle" styleClass="!bg-[var(--ces-brand-soft)] !text-[var(--ces-brand)] !w-12 !h-12 !text-lg shadow-sm border border-[var(--ces-border)]"></p-avatar>
          <div class="flex flex-col overflow-hidden">
            <span class="text-sm font-bold text-[var(--ces-foreground)] truncate">{{ auth.currentUser()?.login }}</span>
            <div class="flex items-center gap-1.5 mt-0.5">
              <span class="text-[11px] text-[var(--ces-foreground-muted)] font-medium">Acesso {{ auth.currentUser()?.perfil || 'Padrão' }}</span>
              @if (auth.currentUser()?.tipoUsuario) {
                <span class="text-[9px] font-bold px-1.5 py-0.2 rounded-full uppercase tracking-wider bg-[var(--ces-brand-soft)] text-[var(--ces-brand)]">
                  {{ auth.currentUser()?.tipoUsuario === 'CAMPO' ? 'Campo' : 'Admin' }}
                </span>
              }
            </div>
          </div>
        </div>

        <div class="flex flex-col bg-[var(--ces-surface)]">
          <!-- Theme & Personalization Section -->
          <div class="p-4 border-b border-[var(--ces-border)]">
            <span class="text-[10px] font-bold text-[var(--ces-foreground-subtle)] uppercase tracking-widest block mb-3 px-1">Personalização</span>

            <!-- Dark Mode Toggle -->
            <div class="flex items-center justify-between p-2 mb-3 bg-[var(--ces-surface-subtle)] rounded-xl border border-[var(--ces-border)]">
              <div class="flex items-center gap-2">
                <i [class]="theme.isDarkMode() ? 'pi pi-moon text-primary-400' : 'pi pi-sun text-amber-500'" class="text-sm"></i>
                <div class="flex items-center gap-1.5">
                  <span class="text-xs font-semibold text-[var(--ces-foreground-muted)]">Modo Escuro</span>
                  <span class="text-[8px] font-extrabold uppercase tracking-wider px-1.5 py-0.2 rounded-full bg-amber-500/15 text-amber-600 dark:bg-amber-400/20 dark:text-amber-300 border border-amber-500/30 select-none">
                    Beta
                  </span>
                </div>
              </div>
              <p-button
                [icon]="theme.isDarkMode() ? 'pi pi-check' : 'pi pi-circle'"
                [text]="true"
                (onClick)="toggleTheme()"
                [styleClass]="theme.isDarkMode() ? '!bg-primary-600 !text-white !w-12 !h-7 !rounded-full' : '!bg-slate-200 dark:!bg-slate-700 !text-slate-400 !w-12 !h-7 !rounded-full'"
              ></p-button>
            </div>

            <!-- Color Palette Selector -->
            <div class="flex flex-wrap gap-2 px-1">
              @for (color of availableColors; track color.name) {
                <button
                  (click)="changeColor(color.name)"
                  [class]="color.class"
                  [title]="color.label"
                  class="w-6 h-6 rounded-full border-2 transition-all transform hover:scale-110 active:scale-95 flex items-center justify-center"
                  [class.!border-[var(--ces-foreground)]]="theme.primaryColor() === color.name"
                  [class.border-transparent]="theme.primaryColor() !== color.name"
                >
                  @if (theme.primaryColor() === color.name) {
                    <i class="pi pi-check text-[10px] text-white"></i>
                  }
                </button>
              }
            </div>

            <!-- Animations Toggle -->
            <div class="flex items-center justify-between p-2 mt-3 bg-[var(--ces-surface-subtle)] rounded-xl border border-[var(--ces-border)]">
              <div class="flex items-center gap-2">
                <i class="pi pi-bolt text-[var(--ces-foreground-subtle)] text-sm"></i>
                <span class="text-xs font-semibold text-[var(--ces-foreground-muted)]">Animações</span>
              </div>
              <p-button
                [icon]="theme.rippleEnabled() ? 'pi pi-check' : 'pi pi-circle'"
                [text]="true"
                (onClick)="toggleAnimations()"
                [styleClass]="theme.rippleEnabled() ? '!bg-green-500 !text-white !w-12 !h-7 !rounded-full' : '!bg-slate-200 !text-slate-400 !w-12 !h-7 !rounded-full'"
              ></p-button>
            </div>

            <!-- Single-key shortcuts (WCAG 2.1.4) -->
            <div class="flex items-center justify-between p-2 mt-3 bg-[var(--ces-surface-subtle)] rounded-xl border border-[var(--ces-border)]">
              <div class="flex items-center gap-2">
                <i class="pi pi-key text-[var(--ces-foreground-subtle)] text-sm"></i>
                <div class="flex flex-col">
                  <span class="text-xs font-semibold text-[var(--ces-foreground-muted)]">Atalhos de uma tecla</span>
                  <span class="text-[10px] text-[var(--ces-foreground-subtle)] leading-tight">/ busca · F filtros · N novo</span>
                </div>
              </div>
              <p-button
                [icon]="atalhos.enabled() ? 'pi pi-check' : 'pi pi-circle'"
                [text]="true"
                [ariaLabel]="atalhos.enabled() ? 'Desligar atalhos de uma tecla' : 'Ligar atalhos de uma tecla'"
                (onClick)="atalhos.setEnabled(!atalhos.enabled())"
                [styleClass]="atalhos.enabled() ? '!bg-primary-600 !text-white !w-12 !h-7 !rounded-full' : '!bg-slate-200 dark:!bg-slate-700 !text-slate-400 !w-12 !h-7 !rounded-full'"
              ></p-button>
            </div>
          </div>

          <!-- Connectivity Section -->
          <div class="p-4 border-b border-[var(--ces-border)]">
            <div class="flex items-center justify-between mb-3 px-1">
              <span class="text-[10px] font-bold text-[var(--ces-foreground-subtle)] uppercase tracking-widest block">Conectividade</span>
              <span [class]="'text-[9px] font-bold px-1.5 py-0.5 rounded-md uppercase tracking-wider ' + (connectivity.isOnline() ? 'bg-green-500/10 text-green-600 dark:text-green-400' : 'bg-amber-500/10 text-amber-600 dark:text-amber-400')">
                {{ connectivity.isOnline() ? 'Online' : 'Offline' }}
              </span>
            </div>

            <!-- Mobile Data Sync Toggle -->
            <div class="flex items-center justify-between p-2 mb-2.5 bg-[var(--ces-surface-subtle)] rounded-xl border border-[var(--ces-border)]">
              <div class="flex items-center gap-2">
                <i [class]="connectivity.usarRedesMoveis() ? 'pi pi-wifi text-primary-500' : 'pi pi-mobile text-[var(--ces-foreground-subtle)]'" class="text-sm"></i>
                <div class="flex flex-col">
                  <span class="text-xs font-semibold text-[var(--ces-foreground-muted)]">Usar Redes Móveis</span>
                  <span class="text-[10px] text-[var(--ces-foreground-subtle)] leading-tight">
                    {{ connectivity.usarRedesMoveis() ? 'Sincronizar no 3G/4G' : 'Apenas em rede Wi-Fi' }}
                  </span>
                </div>
              </div>
              <p-button
                [icon]="connectivity.usarRedesMoveis() ? 'pi pi-check' : 'pi pi-circle'"
                [text]="true"
                (onClick)="toggleRedesMoveis()"
                [pTooltip]="connectivity.usarRedesMoveis() ? 'Sincronização em 3G/4G permitida' : 'Bloquear sincronização em 3G/4G'"
                tooltipPosition="left"
                [styleClass]="connectivity.usarRedesMoveis() ? '!bg-primary-600 !text-white !w-12 !h-7 !rounded-full' : '!bg-slate-200 dark:!bg-slate-700 !text-slate-400 !w-12 !h-7 !rounded-full'"
              ></p-button>
            </div>

            <!-- Manual Offline Toggle -->
            <div class="flex items-center justify-between p-2 bg-[var(--ces-surface-subtle)] rounded-xl border border-[var(--ces-border)]">
              <div class="flex items-center gap-2">
                <i [class]="connectivity.forcarOffline() ? 'pi pi-cloud-slash text-amber-500' : 'pi pi-cloud text-[var(--ces-foreground-subtle)]'" class="text-sm"></i>
                <div class="flex flex-col">
                  <span class="text-xs font-semibold text-[var(--ces-foreground-muted)]">Forçar Modo Offline</span>
                  <span class="text-[10px] text-[var(--ces-foreground-subtle)] leading-tight">
                    {{ connectivity.forcarOffline() ? 'Trabalhando offline' : 'Operação automática' }}
                  </span>
                </div>
              </div>
              <p-button
                [icon]="connectivity.forcarOffline() ? 'pi pi-check' : 'pi pi-circle'"
                [text]="true"
                (onClick)="toggleForcarOffline()"
                [pTooltip]="connectivity.forcarOffline() ? 'Desativar modo offline manual' : 'Forçar modo offline no aparelho'"
                tooltipPosition="left"
                [styleClass]="connectivity.forcarOffline() ? '!bg-amber-600 !text-white !w-12 !h-7 !rounded-full' : '!bg-slate-200 dark:!bg-slate-700 !text-slate-400 !w-12 !h-7 !rounded-full'"
              ></p-button>
            </div>
          </div>

          <!-- Quick Actions Section -->
          <div class="p-2">
            <p-button
              label="Meus Favoritos"
              icon="pi pi-star"
              [text]="true"
              (onClick)="openFavoritesFromMenu(op, favOp)"
              styleClass="!w-full !justify-start !text-[var(--ces-foreground-muted)] !text-[11px] !py-2.5 hover:!bg-[var(--ces-surface-muted)] hover:!text-[var(--ces-foreground)] !border-0 transition-all !rounded-xl"
            ></p-button>
            <p-button
              label="Central de Treinamento"
              icon="pi pi-book"
              [text]="true"
              (onClick)="showManuaisDialog.set(true); op.hide()"
              styleClass="!w-full !justify-start !text-[var(--ces-foreground-muted)] !text-[11px] !py-2.5 hover:!bg-[var(--ces-surface-muted)] hover:!text-[var(--ces-foreground)] !border-0 transition-all !rounded-xl"
            ></p-button>
            @if (auth.currentUser()?.permissoes?.includes('assistente:conexao:use')) {
              <p-button
                label="Conectar sua IA"
                icon="pi pi-link"
                [text]="true"
                (onClick)="router.navigate(['/copiloto/conectar-ia']); op.hide()"
                styleClass="!w-full !justify-start !text-[var(--ces-foreground-muted)] !text-[11px] !py-2.5 hover:!bg-[var(--ces-surface-muted)] hover:!text-[var(--ces-foreground)] !border-0 transition-all !rounded-xl"
              ></p-button>
            }
            <p-button
              label="Alterar Senha"
              icon="pi pi-lock"
              [text]="true"
              (onClick)="showChangePassword(); op.hide()"
              styleClass="!w-full !justify-start !text-[var(--ces-foreground-muted)] !text-[11px] !py-2.5 hover:!bg-[var(--ces-surface-muted)] hover:!text-[var(--ces-foreground)] !border-0 transition-all !rounded-xl"
            ></p-button>
            <div class="h-px bg-[var(--ces-border)] my-2 mx-3"></div>
            <p-button (onClick)="logout.emit()" label="Sair do Sistema" icon="pi pi-power-off" [text]="true" styleClass="!w-full !justify-start !text-red-500 !text-[11px] !py-2.5 hover:!bg-red-500/10 !border-0 font-bold transition-all !rounded-xl"></p-button>
          </div>
        </div>
      </div>
    </p-popover>

    <!-- Notifications Popover -->
    <p-popover #notifOp styleClass="!p-0 !border-0 !bg-transparent [&_.p-popover-content]:!p-0">
      <app-notificacoes-panel (fechar)="notifOp.hide()"></app-notificacoes-panel>
    </p-popover>

    <!-- Dialog Manuais -->
    <p-dialog header="Manuais de Instrução e Treinamento" [(visible)]="showManuaisDialog" [modal]="true" [style]="{ width: '90vw', maxWidth: '680px' }" [draggable]="false" [resizable]="false">
      <div class="flex flex-col gap-4 py-2">
        <p class="text-sm text-[var(--ces-foreground-muted)]">Acesse os guias oficiais e manuais interativos com vídeos tutoriais para capacitação operacional no Nexo.</p>
        
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-1">
          
          <!-- Card Central de Treinamento (destaque principal) -->
          <button
            type="button"
            class="col-span-1 md:col-span-2 text-left border border-[var(--ces-border)] rounded-xl bg-[var(--ces-surface)] p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 transition-all group cursor-pointer hover:shadow-md hover:border-primary-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ces-focus-ring)]"
            (click)="abrirManual('/manuais')"
          >
            <span class="flex items-center gap-3.5">
              <span class="w-11 h-11 rounded-xl bg-[var(--ces-brand-soft)] text-[var(--ces-brand)] flex items-center justify-center shrink-0 text-lg">
                <i class="pi pi-graduation-cap" aria-hidden="true"></i>
              </span>
              <span class="flex flex-col gap-0.5">
                <span class="text-[10px] font-bold text-[var(--ces-foreground-subtle)] uppercase tracking-wider">Hub unificado · 6 módulos com vídeo</span>
                <span class="font-bold text-sm text-[var(--ces-foreground)] group-hover:text-primary-500 transition-colors">Central de Treinamento</span>
                <span class="text-[11px] text-[var(--ces-foreground-muted)]">Passo a passo por módulo, busca instantânea, fluxos offline e impressão em A4.</span>
              </span>
            </span>
            <span class="text-[10px] font-bold text-primary-500 flex items-center gap-1.5 uppercase tracking-wider shrink-0">
              Acessar Central <i class="pi pi-arrow-right text-[8px] group-hover:translate-x-1 transition-transform" aria-hidden="true"></i>
            </span>
          </button>
          
          <!-- Card Manual Descontos -->
          <div class="border border-[var(--ces-border)] rounded-xl overflow-hidden hover:shadow-md hover:border-primary-400 transition-all group cursor-pointer bg-[var(--ces-surface)]" (click)="abrirManual('/manual-descontos')">
            <div class="h-24 bg-primary-900 flex items-center justify-center relative overflow-hidden">
              <i class="pi pi-file-edit absolute -right-2 -bottom-4 text-6xl text-white/10 group-hover:scale-110 group-hover:rotate-6 transition-all duration-300"></i>
              <div class="flex items-center gap-2 text-primary-100 font-bold tracking-widest uppercase text-xs z-10">
                <i class="pi pi-users text-sm"></i> Depto Pessoal
              </div>
            </div>
            <div class="p-4 flex flex-col gap-2">
              <h4 class="font-bold text-[var(--ces-foreground)] text-sm group-hover:text-primary-500 transition-colors">Descontos em Folha</h4>
              <p class="text-[11px] text-[var(--ces-foreground-muted)] line-clamp-2">Aprenda a lançar avarias, aprovar valores e realizar descontos no holerite.</p>
              <div class="mt-2 text-[10px] font-bold text-primary-500 flex items-center gap-1 uppercase tracking-wider">
                Acessar Guia <i class="pi pi-arrow-right text-[8px] mt-0.5 group-hover:translate-x-1 transition-transform"></i>
              </div>
            </div>
          </div>

          <!-- Card Manual SST -->
          <div class="border border-[var(--ces-border)] rounded-xl overflow-hidden hover:shadow-md hover:border-teal-400 transition-all group cursor-pointer bg-[var(--ces-surface)]" (click)="abrirManual('/manual-sst')">
            <div class="h-24 bg-teal-900 flex items-center justify-center relative overflow-hidden">
              <i class="pi pi-shield absolute -right-2 -bottom-4 text-6xl text-white/10 group-hover:scale-110 group-hover:rotate-6 transition-all duration-300"></i>
              <div class="flex items-center gap-2 text-teal-100 font-bold tracking-widest uppercase text-xs z-10">
                <i class="pi pi-shield text-sm"></i> Seg. Trabalho
              </div>
            </div>
            <div class="p-4 flex flex-col gap-2">
              <h4 class="font-bold text-[var(--ces-foreground)] text-sm group-hover:text-teal-400 transition-colors">Inspeções (SST)</h4>
              <p class="text-[11px] text-[var(--ces-foreground-muted)] line-clamp-2">Veja como analisar formulários de checklist, tratar desvios e planos de ação.</p>
              <div class="mt-2 text-[10px] font-bold text-teal-400 flex items-center gap-1 uppercase tracking-wider">
                Acessar Guia <i class="pi pi-arrow-right text-[8px] mt-0.5 group-hover:translate-x-1 transition-transform"></i>
              </div>
            </div>
          </div>

        </div>
      </div>
    </p-dialog>
  `
})
export class HeaderComponent {
  @ViewChild('changePasswordDialog') changePasswordDialog!: ChangePasswordDialogComponent;
  @ViewChild('favButton') favButton!: any;

  protected auth = inject(AuthService);
  protected connectivity = inject(ConnectivityService);
  protected favs = inject(FavoritosService);
  protected theme = inject(ThemeService);
  protected readonly atalhos = inject(CesShortcutsPreference);
  protected usuariosService = inject(UsuariosService);
  protected messageService = inject(MessageService);
  protected notificacoes = inject(NotificacoesStore);
  protected router = inject(Router);
  private readonly viewState = inject(ViewStateStore);

  /** Navegação intencional: o favorito abre a tela limpa. */
  protected abrirFavorito(rota: string): void {
    this.viewState.clearRoute(rota);
    this.router.navigate([rota]);
  }

  isMobile = input<boolean>(false);
  /** O menu já filtrado por permissão — a busca rápida se alimenta dele. */
  menuItems = input<MenuItem[]>([]);
  toggleMenu = output<void>();
  logout = output<void>();

  showManuaisDialog = signal(false);

  availableColors = [
    { name: 'blue', label: 'Azul', class: '!bg-blue-500' },
    { name: 'emerald', label: 'Esmeralda', class: '!bg-emerald-500' },
    { name: 'violet', label: 'Violeta', class: '!bg-violet-500' },
    { name: 'amber', label: 'Âmbar', class: '!bg-amber-500' },
    { name: 'rose', label: 'Rosa', class: '!bg-rose-500' },
    { name: 'slate', label: 'Grafite', class: '!bg-slate-600' }
  ];

  showChangePassword() {
    this.changePasswordDialog.show();
  }

  toggleTheme() {
    const newMode = !this.theme.isDarkMode();
    this.theme.setDarkMode(newMode);
    this.syncPreferencias();
  }

  changeColor(color: string) {
    this.theme.updatePrimaryColor(color);
    this.syncPreferencias();
  }

  toggleAnimations() {
    const newValue = !this.theme.rippleEnabled();
    this.theme.setRipple(newValue);
    this.syncPreferencias();
  }

  toggleRedesMoveis() {
    this.connectivity.setUsarRedesMoveis(!this.connectivity.usarRedesMoveis());
  }

  toggleForcarOffline() {
    this.connectivity.setForcarOffline(!this.connectivity.forcarOffline());
  }

  private syncPreferencias() {
    const themeData = {
      tema: (this.theme.isDarkMode() ? 'dark' : 'light') as 'light' | 'dark',
      corPrimaria: this.theme.primaryColor(),
      animacoesAtivadas: this.theme.rippleEnabled()
    };

    // Atualiza a sessão e localStorage localmente de forma imediata
    this.auth.updateUserSession(themeData);

    this.usuariosService.atualizarPreferencias(themeData).subscribe({
      next: () => {},
      error: () => {
        // As preferências permanecem no dispositivo mesmo se o servidor estiver offline
      }
    });
  }

  openFavoritesFromMenu(op: any, favOp: any) {
    op.hide();
    
    setTimeout(() => {
      try {
        const btnNative = this.favButton.el?.nativeElement || this.favButton.nativeElement;
        const buttonElement = btnNative?.querySelector('button') || btnNative;
        
        if (buttonElement) {
          const rect = buttonElement.getBoundingClientRect();
          const fakeClick = new MouseEvent('click', {
            bubbles: true,
            cancelable: true,
            view: window,
            clientX: rect.left + (rect.width / 2),
            clientY: rect.top + (rect.height / 2)
          });
          buttonElement.dispatchEvent(fakeClick);
        } else {
          favOp.show(new Event('click'));
        }
      } catch (e) {
        favOp.show(new Event('click'));
      }
    }, 250);
  }

  abrirManual(rota: string) {
    this.showManuaisDialog.set(false);
    const url = this.router.serializeUrl(this.router.createUrlTree([rota]));
    window.open(url, '_blank');
  }
}
