import { TestBed } from '@angular/core/testing';
import { SwUpdate } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { AppUpdateService } from './app-update.service';
import { RascunhoService } from '../sessao/rascunho.service';
import { SessaoService } from '../sessao/sessao.service';
import { TelemetriaService } from '../sessao/telemetria.service';

describe('AppUpdateService (atualização sem derrubar o trabalho)', () => {
  let versoes: Subject<any>;
  let sw: { isEnabled: boolean; versionUpdates: Subject<any>; unrecoverable: Subject<any>; activateUpdate: jasmine.Spy; checkForUpdate: jasmine.Spy };
  let servico: AppUpdateService;
  let aplicar: jasmine.Spy;
  let visibilidade: DocumentVisibilityState;

  beforeEach(() => {
    versoes = new Subject();
    sw = {
      isEnabled: true, versionUpdates: versoes, unrecoverable: new Subject(),
      activateUpdate: jasmine.createSpy().and.resolveTo(true), checkForUpdate: jasmine.createSpy().and.resolveTo(true),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: SwUpdate, useValue: sw },
        { provide: RascunhoService, useValue: { gravarTodos: () => Promise.resolve() } },
        { provide: SessaoService, useValue: { gravarAgora: () => Promise.resolve(true) } },
        { provide: TelemetriaService, useValue: { registrar: () => Promise.resolve() } },
      ],
    });
    servico = TestBed.inject(AppUpdateService);
    aplicar = spyOn(servico, 'aplicar').and.resolveTo();
    visibilidade = 'visible';
    spyOnProperty(document, 'visibilityState', 'get').and.callFake(() => visibilidade);
    servico.init();
  });

  function ficarOculto() {
    visibilidade = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
  }

  function versaoPronta() {
    versoes.next({ type: 'VERSION_READY', latestVersion: { hash: 'abc12345' }, currentVersion: { hash: 'x' } });
  }

  it('versão pronta não recarrega a tela enquanto o app está em uso', () => {
    versaoPronta();
    expect(servico.atualizacaoPronta()).toBe(true);
    expect(aplicar).not.toHaveBeenCalled();
  });

  it('aplica quando o app vai para segundo plano', () => {
    versaoPronta();
    ficarOculto();
    expect(aplicar).toHaveBeenCalledTimes(1);
  });

  it('sem versão nova, ir para segundo plano não faz nada', () => {
    ficarOculto();
    expect(aplicar).not.toHaveBeenCalled();
  });

  it('com a câmera ou o seletor de arquivo aberto, espera (a foto que volta não se perde)', () => {
    const input = document.createElement('input');
    input.type = 'file';
    document.body.appendChild(input);
    input.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    versaoPronta();
    ficarOculto();
    expect(servico.capturando()).toBe(true);
    expect(aplicar).not.toHaveBeenCalled();

    // A foto voltou (change): na próxima ida ao segundo plano a versão entra.
    input.dispatchEvent(new Event('change', { bubbles: true }));
    visibilidade = 'visible';
    ficarOculto();
    expect(aplicar).toHaveBeenCalledTimes(1);
    input.remove();
  });

  it('busca manual com versão nova só marca como pronta (quem chama decide quando aplicar)', async () => {
    expect(await servico.checkForUpdateManual()).toBe('updated');
    expect(servico.atualizacaoPronta()).toBe(true);
    expect(sw.activateUpdate).not.toHaveBeenCalled();
  });
});
