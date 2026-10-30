import { TestBed } from '@angular/core/testing';
import { ExecucoesService, EXECUCAO_ALTERAR_ENDPOINT } from './execucoes.service';
import { DbService } from '../db/db.service';
import { OutboxService } from '../sync/outbox.service';
import { TurnoService } from '../turno/turno.service';
import { TokenService } from '../auth/token.service';
import { ExecucaoCampoLocal, ExecucaoMaterialLocal, OutboxItem } from '../db/models';
import { diasParaAlterar, diferencaDaNota, podeAlterarNoCampo } from './campo-regras';

const cabo = (quantidade: number): ExecucaoMaterialLocal =>
  ({ idMaterial: 'cabo', quantidade, operacao: 'APLICADO', condicao: 'NOVO', origem: 'EQUIPE' });
const medidor = (numSerie: string): ExecucaoMaterialLocal =>
  ({ idMaterial: 'medidor', quantidade: 1, operacao: 'APLICADO', condicao: 'NOVO', numSerie, origem: 'EQUIPE' });

function nota(parcial: Partial<ExecucaoCampoLocal> = {}): ExecucaoCampoLocal {
  return {
    id: 'exec-1', referenciaNota: '9293818293', idTurno: 't-1', idEquipe: 'eq-1',
    dataLancamento: new Date().toISOString(), statusExecucao: 'CONCLUIDO',
    acoes: ['acao-1'], materiais: [cabo(10)], statusSync: 'SINCRONIZADO', valorTotalFaturado: 50,
    ...parcial,
  };
}

describe('alteração de nota sincronizada', () => {
  describe('regras', () => {
    it('diferença conta só o que mudou, com a quantidade pela diferença', () => {
      const d = diferencaDaNota(
        { acoes: ['a', 'b'], materiais: [cabo(10), medidor('S-1')] },
        { acoes: ['a', 'c'], materiais: [cabo(7), medidor('S-2')] },
      );
      expect(d.acoesAdicionadas).toEqual(['c']);
      expect(d.acoesRemovidas).toEqual(['b']);
      expect(d.materiaisRemovidos.map(m => [m.idMaterial, m.quantidade, m.numSerie ?? null]))
        .toEqual([['cabo', 3, null], ['medidor', 1, 'S-1']]);
      expect(d.materiaisAdicionados.map(m => m.numSerie)).toEqual(['S-2']);
    });

    it('campo altera nota da própria equipe até 7 dias depois do lançamento', () => {
      const agora = new Date('2026-10-22T12:00:00Z');
      expect(podeAlterarNoCampo(nota({ dataLancamento: '2026-10-16T12:00:00Z' }), 'eq-1', agora)).toBeTrue();
      expect(podeAlterarNoCampo(nota({ dataLancamento: '2026-10-14T12:00:00Z' }), 'eq-1', agora)).toBeFalse();
      expect(podeAlterarNoCampo(nota(), 'eq-2', agora)).toBeFalse();
      expect(podeAlterarNoCampo(nota({ statusSync: 'LOCAL' }), 'eq-1', agora)).toBeFalse();
      expect(diasParaAlterar('2026-10-16T12:00:00Z', agora)).toBe(1);
    });
  });

  describe('ExecucoesService', () => {
    let service: ExecucoesService;
    let db: jasmine.SpyObj<DbService>;
    let outbox: jasmine.SpyObj<OutboxService>;

    beforeEach(() => {
      db = jasmine.createSpyObj('DbService', [
        'getExecucaoById', 'getAcoes', 'saveExecucao', 'deduzirSaldoLocal', 'devolverSaldoLocal', 'getOutboxAlteracaoByExecucaoId'
      ]);
      db.getAcoes.and.resolveTo([]);
      outbox = jasmine.createSpyObj('OutboxService', ['enqueueAlteracaoExecucao', 'retirarAlteracao']);
      const token = jasmine.createSpyObj('TokenService', ['equipeId', 'estoqueId']);
      token.equipeId.and.returnValue('eq-1');
      token.estoqueId.and.returnValue('est-1');
      TestBed.configureTestingModule({
        providers: [
          ExecucoesService,
          { provide: DbService, useValue: db },
          { provide: OutboxService, useValue: outbox },
          { provide: TurnoService, useValue: jasmine.createSpyObj('TurnoService', ['turnoAtivo']) },
          { provide: TokenService, useValue: token },
        ],
      });
      service = TestBed.inject(ExecucoesService);
    });

    it('enfileira a nota inteira com motivo e guarda a versão do servidor', async () => {
      db.getExecucaoById.and.resolveTo(nota());

      await service.alterarExecucao('exec-1', { acoes: ['acao-1'], materiais: [cabo(7)], motivo: ' cabo a mais ' });

      const [item, salva] = outbox.enqueueAlteracaoExecucao.calls.mostRecent().args;
      expect(item.endpoint).toBe(EXECUCAO_ALTERAR_ENDPOINT);
      expect(item.payload.body.motivo).toBe('cabo a mais');
      expect(item.payload.body.idOperacao).toBeTruthy();
      expect(item.payload.body.materiais[0].quantidade).toBe(7);
      expect(salva.alteracaoPendente?.anterior.materiais[0].quantidade).toBe(10);
      // Saíram 3 m de cabo da nota: voltam ao saldo do caminhão.
      expect(db.devolverSaldoLocal).toHaveBeenCalledWith('est-1', 'cabo', 3, 'NOVO');
      expect(db.deduzirSaldoLocal).not.toHaveBeenCalled();
    });

    it('recusa alteração sem motivo ou sem mudança', async () => {
      db.getExecucaoById.and.resolveTo(nota());
      await expectAsync(service.alterarExecucao('exec-1', { acoes: ['acao-1'], materiais: [cabo(7)], motivo: '  ' }))
        .toBeRejectedWithError(/motivo/);
      await expectAsync(service.alterarExecucao('exec-1', { acoes: ['acao-1'], materiais: [cabo(10)], motivo: 'x' }))
        .toBeRejectedWithError(/Nada mudou/);
      expect(outbox.enqueueAlteracaoExecucao).not.toHaveBeenCalled();
    });

    it('corrige só o número da nota: vai no corpo e o desfazer volta o número do servidor', async () => {
      db.getExecucaoById.and.resolveTo(nota());

      await service.alterarExecucao('exec-1', { acoes: ['acao-1'], materiais: [cabo(10)], motivo: 'digitei errado', referenciaNota: ' 777 ' });

      const [item, salva] = outbox.enqueueAlteracaoExecucao.calls.mostRecent().args;
      expect(item.payload.body.referenciaNota).toBe('777');
      expect(salva.referenciaNota).toBe('777');
      expect(salva.alteracaoPendente?.anterior.referenciaNota).toBe(nota().referenciaNota);

      db.getExecucaoById.and.resolveTo(salva);
      db.getOutboxAlteracaoByExecucaoId.and.resolveTo({ id: 'fila-1', status: 'ERRO' } as OutboxItem);
      outbox.retirarAlteracao.and.resolveTo(true);
      await service.desfazerAlteracao('exec-1');
      expect(db.saveExecucao.calls.mostRecent().args[0].referenciaNota).toBe(nota().referenciaNota);
    });

    it('número igual ao do servidor não vai no corpo', async () => {
      db.getExecucaoById.and.resolveTo(nota());

      await service.alterarExecucao('exec-1', { acoes: ['acao-1'], materiais: [cabo(7)], motivo: 'x', referenciaNota: nota().referenciaNota });

      expect(outbox.enqueueAlteracaoExecucao.calls.mostRecent().args[0].payload.body.referenciaNota).toBeUndefined();
    });

    it('segunda alteração antes do envio mantém o retrato do servidor', async () => {
      const pendente = nota({
        materiais: [cabo(7)],
        alteracaoPendente: { idOperacao: 'op-1', motivo: 'm', alteradoEm: new Date().toISOString(), anterior: { acoes: ['acao-1'], materiais: [cabo(10)] } },
      });
      db.getExecucaoById.and.resolveTo(pendente);

      await service.alterarExecucao('exec-1', { acoes: ['acao-1'], materiais: [cabo(8)], motivo: 'eram 8' });

      const salva = outbox.enqueueAlteracaoExecucao.calls.mostRecent().args[1];
      expect(salva.alteracaoPendente?.anterior.materiais[0].quantidade).toBe(10);
      // Em relação ao que o aparelho mostrava (7), entrou 1.
      expect(db.deduzirSaldoLocal).toHaveBeenCalledWith('est-1', 'cabo', 1, 'NOVO', undefined);
    });

    it('desfazer volta a nota ao que está no servidor', async () => {
      db.getExecucaoById.and.resolveTo(nota({
        materiais: [cabo(7)],
        alteracaoPendente: { idOperacao: 'op-1', motivo: 'm', alteradoEm: new Date().toISOString(), anterior: { acoes: ['acao-1'], materiais: [cabo(10)], valorTotalFaturado: 50 } },
      }));
      db.getOutboxAlteracaoByExecucaoId.and.resolveTo({ id: 'fila-1', status: 'ERRO' } as OutboxItem);
      outbox.retirarAlteracao.and.resolveTo(true);

      await service.desfazerAlteracao('exec-1');

      const salva = db.saveExecucao.calls.mostRecent().args[0];
      expect(salva.materiais[0].quantidade).toBe(10);
      expect(salva.alteracaoPendente).toBeNull();
      expect(db.deduzirSaldoLocal).toHaveBeenCalledWith('est-1', 'cabo', 3, 'NOVO', undefined);
    });
  });
});
