import { Type, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

import { LookupStore } from '../../../core/lookup';
import { AuthService } from '../../../core/services/auth.service';
import { FrotaService } from '../../../core/services/frota.service';

/** Página de resposta vazia/simples dos endpoints da frota, para os testes. */
export function paginaDe<T>(content: T[], totalElements = content.length) {
  return of({ content, page: { size: 25, number: 0, totalElements, totalPages: 1 } });
}

export const OPCOES_VAZIAS = {
  situacoes: [],
  regimes: [],
  combustiveis: [],
  provedoresTelemetria: [],
  categoriasCnh: [],
  tiposDocumento: [],
  origensOdometro: []
};

/** Abre uma tela da frota roteada em `caminho`, com serviço, lookups e permissões simulados. */
export async function abrirTelaFrota<T>(
  componente: Type<T>,
  caminho: string,
  url: string,
  frota: object,
  permissoes: string[]
): Promise<{ harness: RouterTestingHarness; componente: T; el: HTMLElement }> {
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      provideRouter([{ path: caminho, component: componente }]),
      MessageService,
      ConfirmationService,
      { provide: FrotaService, useValue: frota },
      {
        provide: LookupStore,
        useValue: {
          operationalBases: signal([{ value: 'b1', label: 'BASE SALVADOR' }]),
          loadBootstrap: () => of({}),
          searchCollaborators: () => of({ items: [] })
        }
      },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]
  });
  const harness = await RouterTestingHarness.create();
  const instancia = await harness.navigateByUrl(url, componente);
  harness.detectChanges();
  await harness.fixture.whenStable();
  harness.detectChanges();
  return { harness, componente: instancia, el: harness.routeNativeElement as HTMLElement };
}

export function chipsDoResumo(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.ces-filter-panel__chip')).map((c) => c.textContent!.trim());
}
