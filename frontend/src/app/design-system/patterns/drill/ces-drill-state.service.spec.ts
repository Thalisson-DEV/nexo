import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { CesDrillStateService, parseCesDrill, serializeCesDrill } from './ces-drill-state.service';

function configurar(drillInicial: string | null) {
  const params = new BehaviorSubject(convertToParamMap(drillInicial ? { drill: drillInicial } : {}));
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };
  const location = { back: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      CesDrillStateService,
      { provide: Router, useValue: router },
      { provide: Location, useValue: location },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: params.value }, queryParamMap: params.asObservable() },
      },
    ],
  });
  return { service: TestBed.inject(CesDrillStateService), router, location, params };
}

describe('CesDrillStateService', () => {
  it('serializa e valida o parâmetro', () => {
    expect(parseCesDrill('dia:2026-09-14')).toEqual({ dimension: 'dia', value: '2026-09-14' });
    expect(parseCesDrill('inspetor:abc:def')).toEqual({ dimension: 'inspetor', value: 'abc:def' });
    expect(parseCesDrill(':x')).toBeNull();
    expect(parseCesDrill('dia:')).toBeNull();
    expect(parseCesDrill('1x:y')).toBeNull();
    expect(serializeCesDrill({ dimension: 'mes', value: '2026-09' })).toBe('mes:2026-09');
  });

  it('restaura o alvo da URL (link compartilhado) e fecha sem voltar no histórico', () => {
    const { service, router, location } = configurar('dia:2026-09-14');
    expect(service.target()).toEqual({ dimension: 'dia', value: '2026-09-14' });
    service.close();
    expect(service.target()).toBeNull();
    expect(location.back).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { drill: null }, replaceUrl: true }));
  });

  it('empilha ao abrir, substitui ao trocar de alvo e volta no histórico ao fechar', () => {
    const { service, router, location } = configurar(null);
    service.open({ dimension: 'dia', value: '2026-09-14' });
    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { drill: 'dia:2026-09-14' }, replaceUrl: false }));
    service.open({ dimension: 'dia', value: '2026-09-15' });
    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ replaceUrl: true }));
    service.close();
    expect(location.back).toHaveBeenCalledOnce();
  });

  it('acompanha o Voltar do navegador', () => {
    const { service, params } = configurar('dia:2026-09-14');
    params.next(convertToParamMap({}));
    expect(service.target()).toBeNull();
  });
});
