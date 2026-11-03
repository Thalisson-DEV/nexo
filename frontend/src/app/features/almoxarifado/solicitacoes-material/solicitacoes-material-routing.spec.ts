import '@angular/compiler';
import { describe, expect, it } from 'vitest';
import { routes } from '../../../app.routes';

describe('rotas de solicitações de material', () => {
  it('mantém áreas independentes para o fluxo comercial e o técnico', () => {
    const almoxarifado = routes.find((route) => route.path === 'almoxarifado');
    const paths = almoxarifado?.children?.map((route) => route.path);

    expect(paths).toContain('solicitacoes');
    expect(paths).toContain('solicitacoes/nova');
    expect(paths).toContain('solicitacoes/:id');
    expect(paths).toContain('devolucao-comercial');
    expect(paths).toContain('solicitacoes-tecnicas');
    expect(paths).toContain('solicitacoes-tecnicas/nova');
    expect(paths).toContain('solicitacoes-tecnicas/devolucao');
    expect(paths).toContain('solicitacoes-tecnicas/kanban');
    expect(paths).toContain('solicitacoes-tecnicas/calendario');
    expect(paths).toContain('solicitacoes-tecnicas/:id');
  });

  it('resolve o cadastro técnico e comercial antes da rota parametrizada de detalhe', () => {
    const almoxarifado = routes.find((route) => route.path === 'almoxarifado');
    const paths = almoxarifado?.children?.map((route) => route.path) ?? [];

    expect(paths.indexOf('solicitacoes-tecnicas/nova')).toBeLessThan(
      paths.indexOf('solicitacoes-tecnicas/:id'),
    );
    expect(paths.indexOf('solicitacoes-tecnicas/calendario')).toBeLessThan(
      paths.indexOf('solicitacoes-tecnicas/:id'),
    );
    expect(paths.indexOf('solicitacoes-tecnicas/devolucao')).toBeLessThan(
      paths.indexOf('solicitacoes-tecnicas/:id'),
    );
    expect(paths.indexOf('solicitacoes/nova')).toBeLessThan(
      paths.indexOf('solicitacoes/:id'),
    );
  });

  it('preserva a URL técnica anterior como redirecionamento compatível', () => {
    const almoxarifado = routes.find((route) => route.path === 'almoxarifado');
    const legacy = almoxarifado?.children?.find(
      (route) => route.path === 'solicitacoes/tecnicas/:id',
    );

    expect(legacy?.redirectTo).toBe('solicitacoes-tecnicas/:id');
  });
});
