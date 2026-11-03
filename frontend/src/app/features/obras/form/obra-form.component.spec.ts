import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NotificationService } from '@ces/ui';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObraResponse, TipoVinculoSubprojeto } from '../../../core/models/obras.models';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { ObraFormComponent } from './obra-form.component';

describe('ObraFormComponent', () => {
  let component: ObraFormComponent;
  let fixture: ComponentFixture<ObraFormComponent>;
  let obrasService: any;
  let rhService: any;

  const mockObra: ObraResponse = {
    id: 'obra-1',
    numero: '1228694',
    titulo: 'SUBESTAÇÃO CENTRAL - REFORMA ALIMENTADOR 02',
    dataInicio: '01/03/2026',
    dataPrevisaoFim: '30/09/2026',
    statusId: 'st-1',
    statusNome: 'EM EXECUÇÃO',
    statusConcluida: false,
    supervisorId: 'sup-1',
    supervisorNome: 'CARLOS SILVA',
    coordenadorId: 'coord-1',
    coordenadorNome: 'MARCOS OLIVEIRA',
    equipes: [],
    subprojetos: [],
    anexos: [],
    ativo: true,
    dataCriacao: '2026-03-01T09:00:00-03:00'
  };

  beforeEach(async () => {
    obrasService = {
      buscarPorId: vi.fn().mockReturnValue(of(mockObra)),
      buscarObraPorId: vi.fn().mockReturnValue(of(mockObra)),
      criarObra: vi.fn().mockReturnValue(of(mockObra)),
      atualizarObra: vi.fn().mockReturnValue(of(mockObra)),
      listarStatusAtivos: vi.fn().mockReturnValue(of([{ id: 'st-1', nome: 'EM EXECUÇÃO', concluida: false, ativo: true }])),
      listarAgentesAtivos: vi.fn().mockReturnValue(of([])),
      listarObras: vi.fn().mockReturnValue(of({ content: [] }))
    };

    rhService = {
      listarBasesOperacionaisAtivas: vi.fn().mockReturnValue(of({ content: [] })),
      listarProcessosAtivos: vi.fn().mockReturnValue(of({ content: [] })),
      listarMunicipios: vi.fn().mockReturnValue(of({ content: [] })),
      listarCentrosFaturamentoAtivos: vi.fn().mockReturnValue(of({ content: [] })),
      listarSupervisoresAtivosMin: vi.fn().mockReturnValue(of([
        { id: 'sup-1', idColaborador: 'colab-1', matricula: 'SUP001', nomeColaborador: 'CARLOS SILVA' }
      ])),
      listarCoordenadoresAtivosMin: vi.fn().mockReturnValue(of([
        { id: 'coord-1', idColaborador: 'colab-2', matricula: 'CRD001', nomeColaborador: 'MARCOS OLIVEIRA' }
      ])),
      listarEquipesAtivas: vi.fn().mockReturnValue(of({ content: [] })),
      listarEquipes: vi.fn().mockReturnValue(of({ content: [] }))
    };

    TestBed.overrideComponent(ObraFormComponent, { set: { imports: [], template: '' } });
    await TestBed.configureTestingModule({
      imports: [ObraFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ObrasService, useValue: obrasService },
        { provide: RhService, useValue: rhService },
        { provide: NotificationService, useValue: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ObraFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function preencherObrigatorios(): void {
    const c = component as any;
    c.numero.set('1228694');
    c.titulo.set('SUBESTAÇÃO CENTRAL');
    c.idProcesso.set('proc-1');
    c.idBaseOperacional.set('base-1');
    c.idMunicipio.set('mun-1');
    c.idCentroCusto.set('cc-1');
    c.idSupervisor.set('sup-1');
    c.idCoordenador.set('coord-1');
    c.dataInicio.set(new Date(2026, 2, 1));
    c.dataPrevisaoFim.set(new Date(2026, 8, 30));
  }

  it('abre para cadastrar, sugerindo o status sem contar como alteração', () => {
    expect(component['editando']()).toBe(false);
    expect(component['idStatusObra']()).toBe('st-1');
    expect(component['alterado']()).toBe(false);

    component['numero'].set('1');
    expect(component['alterado']()).toBe(true);
  });

  it('não salva sem os obrigatórios e aponta cada campo', () => {
    component['salvar']();

    expect(obrasService.criarObra).not.toHaveBeenCalled();
    expect(component['erro']('numero')).toBeTruthy();
    expect(component['erro']('idSupervisor')).toBeTruthy();
    expect(component['erro']('idCoordenador')).toBeTruthy();
    expect(component['erro']('dataInicio')).toBeTruthy();
  });

  it('só mostra erro depois da primeira tentativa de salvar', () => {
    expect(component['erro']('numero')).toBeUndefined();
  });

  it('envia o cadastro com número e título limpos, avisa quem abriu e fecha', () => {
    const salva = vi.fn();
    component.salva.subscribe(salva);
    preencherObrigatorios();
    component['numero'].set('b-1228694');
    component['titulo'].set('  reforma   geral  ');

    component['salvar']();

    expect(obrasService.criarObra).toHaveBeenCalledWith(
      expect.objectContaining({
        numero: '1228694',
        titulo: 'REFORMA GERAL',
        idSupervisor: 'sup-1',
        idCoordenador: 'coord-1',
        idStatusObra: 'st-1',
        idObraPai: null,
        tipoVinculoPai: null
      })
    );
    expect(salva).toHaveBeenCalledWith(mockObra);
    expect(component['aberto']()).toBe(false);
  });

  it('obra nova avisa com a opção de abrir; não sai da tela sozinha', () => {
    const notificacoes = TestBed.inject(NotificationService) as unknown as { success: ReturnType<typeof vi.fn> };
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    preencherObrigatorios();

    component['salvar']();

    expect(navegar).not.toHaveBeenCalled();
    const { action } = notificacoes.success.mock.calls[0][1];
    expect(action.label).toBe('Abrir obra');
    action.run();
    expect(navegar).toHaveBeenCalledWith(['/obras', 'obra-1']);
  });

  it('subprojeto exige a obra principal e o tipo de vínculo', () => {
    preencherObrigatorios();
    component['ehSubprojeto'].set(true);
    component['tipoVinculoPai'].set(null);

    component['salvar']();
    expect(obrasService.criarObra).not.toHaveBeenCalled();
    expect(component['erro']('idObraPai')).toBeTruthy();
    expect(component['erro']('tipoVinculoPai')).toBeTruthy();

    component['idObraPai'].set('obra-pai-1');
    component['tipoVinculoPai'].set(TipoVinculoSubprojeto.KIT);
    component['salvar']();
    expect(obrasService.criarObra).toHaveBeenCalledWith(expect.objectContaining({ idObraPai: 'obra-pai-1', tipoVinculoPai: 'KIT' }));
  });

  it('em edição, carrega a obra, não conta como alteração e atualiza', () => {
    const edicao = TestBed.createComponent(ObraFormComponent);
    edicao.componentRef.setInput('idObra', 'obra-1');
    edicao.detectChanges();
    const tela = edicao.componentInstance;

    expect(obrasService.buscarObraPorId).toHaveBeenCalledWith('obra-1');
    expect(tela['numero']()).toBe('1228694');
    expect(tela['idSupervisor']()).toBe('sup-1');
    expect(tela['alterado']()).toBe(false);

    tela['idProcesso'].set('proc-1');
    tela['idBaseOperacional'].set('base-1');
    tela['idMunicipio'].set('mun-1');
    tela['idCentroCusto'].set('cc-1');
    tela['salvar']();
    expect(obrasService.atualizarObra).toHaveBeenCalledWith('obra-1', expect.objectContaining({ numero: '1228694' }));
  });

  it('a obra principal não oferece a própria obra', () => {
    obrasService.listarObras.mockReturnValue(of({ content: [
      { id: 'obra-1', numero: '1228694', titulo: 'ELA MESMA' },
      { id: 'obra-pai-1', numero: '1228600', titulo: 'SUBESTAÇÃO PRINCIPAL' }
    ] }));
    const edicao = TestBed.createComponent(ObraFormComponent);
    edicao.componentRef.setInput('idObra', 'obra-1');
    edicao.detectChanges();

    expect(edicao.componentInstance['obrasPaiOpcoes']()).toEqual([{ label: '1228600 — SUBESTAÇÃO PRINCIPAL', value: 'obra-pai-1' }]);
  });
});
