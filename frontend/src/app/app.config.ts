import {
  ApplicationConfig,
  ErrorHandler,
  provideBrowserGlobalErrorListeners,
  LOCALE_ID,
  inject,
  provideAppInitializer
} from '@angular/core';
import { provideRouter, withInMemoryScrolling, withNavigationErrorHandler } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';

import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';

import { routes } from './app.routes';
import { comModulosOcultos } from './core/white-label/modulos-ocultos';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { idempotencyInterceptor } from './core/interceptors/idempotency.interceptor';
import {
  tratarErroDeNavegacao,
  VersaoObsoletaErrorHandler
} from './core/versao-obsoleta';
import { ConfirmationService, MessageService } from 'primeng/api';
import { cesPreset } from './design-system/core';
import { CES_PAGE_FAVORITES } from './design-system/patterns/page-header';
import { FavoritosService } from './core/services/favoritos.service';
import { NavigationHistoryService } from './core/navigation/navigation-history.service';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Rota lazy cujo chunk sumiu em um deploy: a navegação falha calada e a tela
    // simplesmente não muda. Foi assim que um desconto salvo com sucesso pareceu
    // não ter salvo, e o usuário lançou o mesmo desconto três vezes (07/08/2026).
    provideRouter(
      comModulosOcultos(routes),
      withNavigationErrorHandler(tratarErroDeNavegacao),
      // A janela não rola (o shell rola dentro de <main>, ver appScrollMemory em app.html);
      // isto cobre telas que rolam a janela e as âncoras.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' })
    ),
    // Precisa existir antes da navegação inicial para reconhecer F5 e deep links.
    provideAppInitializer(() => inject(NavigationHistoryService).start()),
    { provide: ErrorHandler, useClass: VersaoObsoletaErrorHandler },
    provideAnimationsAsync(),
    // A ordem importa: o de idempotência roda DEPOIS do de autenticação, sendo o último a
    // tocar nos headers antes do envio — é assim que a chave sobrevive até o backend.
    provideHttpClient(withInterceptors([authInterceptor, idempotencyInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    MessageService,
    ConfirmationService,
    // O cabeçalho de página do DS (ces-page-header) favorita sem depender de core/.
    {
      provide: CES_PAGE_FAVORITES,
      useFactory: () => {
        const favoritos = inject(FavoritosService);
        return {
          isCurrentPageFavorite: favoritos.isCurrentPageFavorite,
          toggle: (titulo: string, icone?: string) => favoritos.toggleFavorite(titulo, icone)
        };
      }
    },
    providePrimeNG({
      ripple: true,
      pt: {
        confirmDialog: {
          host: {
            'aria-label': 'Confirmação da ação'
          }
        }
      },
      theme: {
        preset: cesPreset,
        options: {
          darkModeSelector: '.app-dark',
          cssLayer: {
            name: 'primeng',
            order: 'theme, base, primeng, ces.tokens, ces.base, ces.components, utilities, ces.legacy'
          }
        }
      },
      translation: {
        dayNames: ["Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"],
        dayNamesShort: ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"],
        dayNamesMin: ["D", "S", "T", "Q", "Q", "S", "S"],
        monthNames: ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"],
        monthNamesShort: ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"],
        today: 'Hoje',
        clear: 'Limpar',
        dateFormat: 'dd/mm/yy',
        firstDayOfWeek: 0,
      }
    }),
  ],
};
