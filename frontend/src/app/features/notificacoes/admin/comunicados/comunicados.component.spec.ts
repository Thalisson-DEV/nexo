import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ComunicadosService } from '../../../../core/services/comunicados.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { ComunicadosComponent } from './comunicados.component';

const rascunho = {
  id: 'c1',
  titulo: 'Nova tela de frota',
  resumo: 'Conheça a ficha do veículo',
  tipo: 'CHANGELOG',
  tipoRotulo: 'Novidade',
  apresentacaoRotulo: 'Cartão',
  status: 'RASCUNHO',
  statusRotulo: 'Rascunho',
  dataInicio: null,
  dataFim: null,
  fixado: false,
  exigeConfirmacao: true,
  notificarNoSininho: true,
  agendado: false,
  vigente: false,
  segmentos: [
    { id: 's1', tipoAlvo: 'PERFIL', tipoAlvoRotulo: 'Perfil', idAlvo: 'p1', valorAlvo: null },
    { id: 's2', tipoAlvo: 'USUARIO', tipoAlvoRotulo: 'Usuário', idAlvo: 'u1', valorAlvo: null }
  ],
  anexos: [],
  dataCriacao: '2026-10-10T10:00:00Z'
};

describe('ComunicadosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(ComunicadosComponent, 'lista', url, [
      { provide: ComunicadosService, useValue: servico },
      {
        provide: UsuariosService,
        useValue: { listarPerfisAtivos: vi.fn(() => of([{ id: 'p1', nome: 'GESTOR FROTA', ativo: true }])), buscarUsuarioPorId: vi.fn(() => of({ id: 'u1', login: 'USR000001' })) }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listar: vi.fn(() => paginaTeste([rascunho])),
      buscar: vi.fn(() => of(rascunho)),
      publicar: vi.fn(() => of(rascunho)),
      arquivar: vi.fn(() => of(rascunho)),
      remover: vi.fn(() => of(undefined)),
      relatorioConfirmacao: vi.fn(() =>
        of({
          idComunicado: 'c1',
          titulo: 'Nova tela de frota',
          exigeConfirmacao: true,
          totalDestinatarios: 4,
          totalConfirmados: 1,
          totalVisualizados: 2,
          confirmados: [{ idUsuario: 'u1', login: 'USR000001', visualizadoEm: null, confirmadoEm: '2026-10-10T11:00:00Z' }],
          pendentes: [
            { idUsuario: 'u2', login: 'USR000002', visualizadoEm: '2026-10-10T11:00:00Z' },
            { idUsuario: 'u3', login: 'USR000003', visualizadoEm: null }
          ]
        })
      )
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos ativos (chip do padrão), mais recentes primeiro, com o público-alvo legível', async () => {
    const { el, harness } = await abrir();
    harness.detectChanges();
    expect(servico['listar']).toHaveBeenCalledWith(expect.objectContaining({ ativo: true, page: 0, size: 25, sort: 'dataCriacao,desc' }));
    expect(chipsDoPainel(el)).toEqual(['Situação: Ativos']);
    expect(el.textContent).toContain('GESTOR FROTA');
    expect(el.textContent).toContain('USR000001');
  });

  it('publicar exige fim depois do início e envia o período', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      abrirPublicacao(c: unknown): void;
      inicio: { set(d: Date | null): void };
      fim: { set(d: Date | null): void };
      publicar(): void;
    };
    tela.abrirPublicacao(rascunho);
    tela.inicio.set(new Date(2030, 0, 10, 8, 0));
    tela.fim.set(new Date(2030, 0, 9, 8, 0));
    tela.publicar();
    expect(servico['publicar']).not.toHaveBeenCalled();
    tela.fim.set(null);
    tela.publicar();
    expect(servico['publicar']).toHaveBeenCalledWith('c1', { dataInicio: new Date(2030, 0, 10, 8, 0).toISOString(), dataFim: null });
  });

  it('relatório calcula a cobertura e quem abriu sem confirmar', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { abrirRelatorio(c: unknown): void; cobertura(): number; abriramSemConfirmar(): number };
    tela.abrirRelatorio(rascunho);
    expect(tela.cobertura()).toBe(25);
    expect(tela.abriramSemConfirmar()).toBe(1);
  });

  it('?publicar=<id> (volta do formulário) abre a publicação', async () => {
    const { componente } = await abrir('/lista?publicar=c1');
    expect(servico['buscar']).toHaveBeenCalledWith('c1');
    expect((componente as unknown as { publicacaoAberta(): boolean }).publicacaoAberta()).toBe(true);
  });

  it('"n" vai para o formulário de novo comunicado', async () => {
    await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tecla('n');
    expect(navegar).toHaveBeenCalledWith(['/notificacoes/admin/comunicados/novo']);
  });
});
