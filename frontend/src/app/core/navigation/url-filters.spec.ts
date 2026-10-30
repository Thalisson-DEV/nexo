import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { qp } from './query-state';
import { createUrlFilters } from './url-filters';

interface Filtros {
  inicio: Date | null;
  base: string | undefined;
}

@Component({ template: '', standalone: true })
class PainelHost {
  readonly url = createUrlFilters<Filtros>({ inicio: qp.date('inicio'), base: qp.text('base') });
}

describe('createUrlFilters', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('lê e grava com replace, preservando outros parâmetros', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'painel', component: PainelHost }]), provideLocationMocks()],
    });
    const harness = await RouterTestingHarness.create();
    const painel = await harness.navigateByUrl('/painel?base=b1&outro=x', PainelHost);
    expect(painel.url.read()).toEqual({ base: 'b1' });

    painel.url.write({ inicio: new Date(2026, 9, 8, 12), base: undefined });
    await new Promise((resolve) => setTimeout(resolve, 0));
    const location = TestBed.inject(Location);
    expect(location.path()).toBe('/painel?outro=x&inicio=2026-10-08');
  });
});
