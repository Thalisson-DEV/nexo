import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { MenuItem } from 'primeng/api';
import { SidebarComponent } from './sidebar.component';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let component: SidebarComponent;
  let mockUrl = '/almoxarifado/solicitacoes-tecnicas';

  const mockRouter = {
    get url() {
      return mockUrl;
    },
    parseUrl: (url: string) => {
      const [path, query] = url.split('?');
      const queryParams: Record<string, string> = {};
      if (query) {
        query.split('&').forEach((param) => {
          const [k, v] = param.split('=');
          if (k) queryParams[k] = v ?? '';
        });
      }
      return {
        root: { children: { primary: { segments: path.split('/').filter(Boolean).map((p) => ({ path: p })) } } },
        queryParams,
        toString: () => url,
      };
    },
  };

  const gerenciarItem: MenuItem = {
    label: 'Gerenciar Solicitações',
    icon: 'pi pi-list',
    routerLink: '/almoxarifado/solicitacoes-tecnicas',
  };

  const filaSeparacaoItem: MenuItem = {
    label: 'Fila de Separação',
    icon: 'pi pi-box',
    routerLink: '/almoxarifado/solicitacoes-tecnicas',
    queryParams: { view: 'separacao' },
  };

  const novaDevolucaoItem: MenuItem = {
    label: 'Nova Devolução',
    icon: 'pi pi-undo',
    routerLink: '/almoxarifado/solicitacoes-tecnicas/devolucao',
  };

  const parentGroup: MenuItem = {
    label: 'Solicitações Técnicas',
    icon: 'pi pi-wrench',
    items: [gerenciarItem, filaSeparacaoItem, novaDevolucaoItem],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [{ provide: Router, useValue: mockRouter }],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    component.menuItems.set([parentGroup]);
  });

  it('ativa somente "Gerenciar Solicitações" quando a rota não tem queryParams', () => {
    mockUrl = '/almoxarifado/solicitacoes-tecnicas';

    expect(component.isItemActive(gerenciarItem)).toBe(true);
    expect(component.isItemActive(filaSeparacaoItem)).toBe(false);
    expect(component.isItemActive(novaDevolucaoItem)).toBe(false);
    expect(component.isItemActive(parentGroup)).toBe(true);
  });

  it('ativa somente "Fila de Separação" quando a rota possui ?view=separacao', () => {
    mockUrl = '/almoxarifado/solicitacoes-tecnicas?view=separacao';

    expect(component.isItemActive(gerenciarItem)).toBe(false);
    expect(component.isItemActive(filaSeparacaoItem)).toBe(true);
    expect(component.isItemActive(novaDevolucaoItem)).toBe(false);
    expect(component.isItemActive(parentGroup)).toBe(true);
  });

  it('ativa somente "Nova Devolução" e não marca "Gerenciar Solicitações" quando em rota filha', () => {
    mockUrl = '/almoxarifado/solicitacoes-tecnicas/devolucao';

    expect(component.isItemActive(novaDevolucaoItem)).toBe(true);
    expect(component.isItemActive(gerenciarItem)).toBe(false);
    expect(component.isItemActive(filaSeparacaoItem)).toBe(false);
    expect(component.isItemActive(parentGroup)).toBe(true);
  });

  it('respeita routerLinkActiveOptions com exact: true', () => {
    mockUrl = '/almoxarifado/solicitacoes-tecnicas/nova';
    const itemComExact: MenuItem = {
      label: 'Gerenciar Solicitações',
      icon: 'pi pi-list',
      routerLink: '/almoxarifado/solicitacoes-tecnicas',
      routerLinkActiveOptions: { exact: true },
    };

    expect(component.isItemActive(itemComExact)).toBe(false);
  });

  it('não ativa itens quando em rota totalmente não relacionada', () => {
    mockUrl = '/rh/colaboradores';

    expect(component.isItemActive(gerenciarItem)).toBe(false);
    expect(component.isItemActive(filaSeparacaoItem)).toBe(false);
    expect(component.isItemActive(novaDevolucaoItem)).toBe(false);
    expect(component.isItemActive(parentGroup)).toBe(false);
  });
});
