import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { atualizarPeriodicamente } from './atualizacao-automatica';

const acao = vi.fn();
let pode = true;

@Component({ selector: 'app-teste-atualizacao', template: '' })
class TelaComAtualizacao {
  private readonly atualizacao = atualizarPeriodicamente(acao, { intervaloMs: 60_000, pode: () => pode });
}

describe('atualizarPeriodicamente', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    acao.mockClear();
    pode = true;
  });

  afterEach(() => vi.useRealTimers());

  it('renova a cada intervalo, pula quando não pode e para ao destruir a tela', () => {
    const fixture = TestBed.createComponent(TelaComAtualizacao);

    vi.advanceTimersByTime(60_000);
    expect(acao).toHaveBeenCalledTimes(1);

    pode = false;
    vi.advanceTimersByTime(60_000);
    expect(acao).toHaveBeenCalledTimes(1);

    fixture.destroy();
    pode = true;
    vi.advanceTimersByTime(120_000);
    expect(acao).toHaveBeenCalledTimes(1);
  });

  it('ao voltar para a aba só renova se o intervalo já passou', () => {
    TestBed.createComponent(TelaComAtualizacao);

    document.dispatchEvent(new Event('visibilitychange'));
    expect(acao).not.toHaveBeenCalled();

    vi.setSystemTime(Date.now() + 61_000);
    document.dispatchEvent(new Event('visibilitychange'));
    expect(acao).toHaveBeenCalledTimes(1);
  });
});
