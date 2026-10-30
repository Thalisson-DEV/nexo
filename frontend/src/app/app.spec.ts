import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { ConfirmationService, MessageService } from 'primeng/api';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        provideNoopAnimations(),
        providePrimeNG(),
        MessageService,
        ConfirmationService
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render main content', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('main')).toBeTruthy();
  });

  it('deve conter atalhos para Nova Compra e Novo Pagamento no menu Financeiro', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const financeiro = app['menuItems']().find((m) => m.label === 'Financeiro');
    expect(financeiro).toBeTruthy();

    const minhasSolicitacoes = financeiro?.items?.find((i) => i.label === 'Minhas Solicitações');
    expect(minhasSolicitacoes).toBeTruthy();

    const novaCompra = minhasSolicitacoes?.items?.find((i) => i.label === 'Nova Compra');
    expect(novaCompra).toBeTruthy();
    expect(novaCompra?.routerLink).toBe('/financeiro/compras/nova');
    expect(novaCompra?.icon).toBe('pi pi-plus-circle');

    const novoPagamento = minhasSolicitacoes?.items?.find((i) => i.label === 'Novo Pagamento');
    expect(novoPagamento).toBeTruthy();
    expect(novoPagamento?.routerLink).toBe('/financeiro/pagamentos/nova');
    expect(novoPagamento?.icon).toBe('pi pi-plus-circle');
  });

  it('deve mapear rotas financeiras em permissaoPorRota', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const permissoes = app['permissaoPorRota'];

    expect(permissoes['/financeiro/compras']).toBe('financeiro:compra:read');
    expect(permissoes['/financeiro/compras/nova']).toBe('financeiro:compra:write');
    expect(permissoes['/financeiro/pagamentos']).toBe('financeiro:pagamento:read');
    expect(permissoes['/financeiro/pagamentos/nova']).toBe('financeiro:pagamento:write');
  });

  it('deve conter gestão, nova solicitação, devolução e kanban no menu Almoxarifado > Solicitações Comerciais', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const almoxarifado = app['menuItems']().find((m) => m.label === 'Almoxarifado');
    expect(almoxarifado).toBeTruthy();

    const solicitacoes = almoxarifado?.items?.find((i) => i.label === 'Solicitações Comerciais');
    expect(solicitacoes).toBeTruthy();

    // As filas por situação (pendente aprovação/entrega) viraram colunas do Kanban.
    const itens = Object.fromEntries((solicitacoes?.items ?? []).map((item) => [item.label, item.routerLink]));
    expect(itens).toEqual({
      'Gerenciar Solicitações': '/almoxarifado/solicitacoes',
      'Nova Solicitação': '/almoxarifado/solicitacoes/nova',
      'Nova Devolução': '/almoxarifado/devolucao-comercial',
      Quadro: '/almoxarifado/solicitacoes/kanban',
      'Calendário de saques': '/almoxarifado/solicitacoes/calendario'
    });
  });

  it('deve mapear rotas de solicitacoes comerciais em permissaoPorRota', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const permissoes = app['permissaoPorRota'];

    expect(permissoes['/almoxarifado/solicitacoes']).toBe('estoque:solicitacao:read');
    expect(permissoes['/almoxarifado/solicitacoes/nova']).toBe('estoque:solicitacao:create');
    expect(permissoes['/almoxarifado/solicitacoes/calendario']).toBe('estoque:solicitacao:read');
  });
});
