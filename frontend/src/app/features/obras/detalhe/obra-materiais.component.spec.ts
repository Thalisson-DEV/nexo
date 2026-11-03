import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { ObraMateriaisComponent, agruparMateriais } from './obra-materiais.component';

const solicitacao = (id: string) => ({
  id,
  tipo: 'ADICAO_COMUM',
  status: 'APROVADO',
  statusSeparacao: null,
  idEquipe: null,
  dataPrevista: null,
  quantidadeSolicitada: 5,
  quantidadePendente: 3,
  dataSolicitacao: '2026-10-31T10:00:00-03:00',
  almoxarifadoNome: 'Central',
  itens: [{ idMaterial: 'm1', codigo: 'MAT-1', descricao: 'Cabo', unidade: 'M', quantidade: 5, quantidadeAtendida: 2, quantidadePendente: 3 }],
});

const movimentacao = {
  idReferencia: 'ref-12345678',
  tipo: 'DEVOLUCAO',
  tipoTecnico: 'DEVOLUCAO_CONCRETO',
  dataHora: '2026-10-31T11:00:00-03:00',
  origemNome: 'Obra',
  destinoNome: 'Central',
  itens: [
    { codigo: 'MAT-9', descricao: 'Trafo', unidade: 'UN', quantidade: 1, condicao: 'SUCATA', numSerie: 'S-1' },
    { codigo: 'MAT-9', descricao: 'Trafo', unidade: 'UN', quantidade: 1, condicao: 'SUCATA', numSerie: 'S-2' },
  ],
};

function abrir(solicitacoes: unknown[] = [solicitacao('sol-1'), solicitacao('sol-2')], movimentacoes: unknown[] = [movimentacao]) {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture = TestBed.createComponent(ObraMateriaisComponent);
  fixture.componentRef.setInput('solicitacoes', solicitacoes);
  fixture.componentRef.setInput('movimentacoes', movimentacoes);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const abertos = (el: HTMLElement) => el.querySelectorAll('.mat-detalhe[data-aberto="true"]').length;

describe('ObraMateriaisComponent', () => {
  it('mostra o resumo de cada operação com os detalhes recolhidos', () => {
    const { el } = abrir();

    expect(abertos(el)).toBe(0);
    const linhas = el.querySelectorAll('.mat-linha');
    expect(linhas[0].textContent).toContain('Central');
    expect(linhas[0].textContent).toContain('1 com pendência');
    expect(linhas[0].textContent).toContain('1 material');
    expect(linhas[2].textContent).toContain('Devolução');
    expect(linhas[2].textContent).toContain('922');
    expect(linhas[2].textContent).toContain('Obra → Central');
    expect(linhas[0].getAttribute('aria-expanded')).toBe('false');
  });

  it('abre e fecha uma operação de cada vez, sem mexer nas outras', () => {
    const { fixture, el } = abrir();
    const linha = el.querySelector('.mat-linha') as HTMLButtonElement;

    linha.click();
    fixture.detectChanges();
    expect(abertos(el)).toBe(1);
    expect(linha.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('.mat-detalhe[data-aberto="true"]')?.textContent).toContain('MAT-1');

    linha.click();
    fixture.detectChanges();
    expect(abertos(el)).toBe(0);
  });

  it('expandir e recolher todas valem só para a seção', () => {
    const { fixture, el } = abrir([solicitacao('sol-1'), solicitacao('sol-2')], [movimentacao, { ...movimentacao, idReferencia: 'ref-2' }]);
    const acoes = el.querySelectorAll<HTMLButtonElement>('.mat-secao__acoes button');

    acoes[0].click();
    fixture.detectChanges();
    expect(abertos(el)).toBe(2);

    acoes[2].click();
    fixture.detectChanges();
    expect(abertos(el)).toBe(4);

    acoes[1].click();
    fixture.detectChanges();
    expect(abertos(el)).toBe(2);
  });

  it('só chama de fora do saldo a entrada gerada por devolução', () => {
    const entrada = { ...movimentacao, idReferencia: 'ref-3', tipo: 'ENTRADA_AVULSA' };
    const { el } = abrir([], [entrada, { ...entrada, idReferencia: 'ref-4', tipoTecnico: null }]);
    const linhas = el.querySelectorAll('.mat-linha');

    expect(linhas[0].textContent).toContain('Material fora do saldo da obra');
    expect(linhas[1].textContent).toContain('Entrada avulsa');
    expect(linhas[1].textContent).not.toContain('Material fora do saldo da obra');
  });

  it('junta as séries do mesmo material numa linha', () => {
    expect(agruparMateriais(movimentacao.itens)).toEqual([
      expect.objectContaining({ codigo: 'MAT-9', condicao: 'Sucata', quantidade: 2, series: ['S-1', 'S-2'] }),
    ]);
  });

  it('mostra o saldo sem precisar abrir nada, destacando o negativo', () => {
    const { fixture, el } = abrir([], []);
    expect(el.textContent).not.toContain('Saldo na obra');

    fixture.componentRef.setInput('saldo', {
      estado: 'ready',
      dados: {
        idEstoque: 'est-1',
        itens: [
          { codigo: 'MAT-1', descricao: 'Cabo', unidade: 'M', condicao: 'NOVO', quantidade: 12.5, series: [] },
          { codigo: 'MAT-9', descricao: 'Trafo', unidade: 'UN', condicao: 'SUCATA', quantidade: -1, series: ['S-1', 'S-2'] },
        ],
      },
    });
    fixture.detectChanges();

    const linhas = el.querySelectorAll('.mat-rolagem--saldo tbody tr');
    expect(el.textContent).toContain('Saldo na obra');
    expect(linhas[0].textContent).toContain('Novo');
    expect(linhas[0].querySelector('.mat-pendente')).toBeNull();
    expect(linhas[1].textContent).toContain('Sucata');
    expect(linhas[1].textContent).toContain('S-1, S-2');
    expect(linhas[1].querySelector('.mat-pendente')?.textContent).toContain('-1');
    expect(linhas[0].querySelector('ces-status')?.textContent).toContain('Novo');
    expect(linhas[1].querySelector('ces-status')?.textContent).toContain('Sucata');

    const relatorio = el.querySelector('.mat-secao__acoes a') as HTMLAnchorElement;
    expect(relatorio.getAttribute('href')).toContain('/almoxarifado/saldos/relatorio?idEstoque=est-1');
    expect(relatorio.getAttribute('target')).toBe('_blank');
  });

  it('recolhe e reabre o saldo, que começa aberto', () => {
    const { fixture, el } = abrir([], []);
    fixture.componentRef.setInput('saldo', { estado: 'ready', dados: { idEstoque: 'est-1', itens: [] } });
    fixture.detectChanges();
    const alternar = el.querySelector('.mat-secao__alternar') as HTMLButtonElement;
    const corpo = el.querySelector('#mat-saldo') as HTMLElement;
    expect(alternar.getAttribute('aria-expanded')).toBe('true');

    alternar.click();
    fixture.detectChanges();
    expect(alternar.getAttribute('aria-expanded')).toBe('false');
    expect(corpo.getAttribute('data-aberto')).toBe('false');

    alternar.click();
    fixture.detectChanges();
    expect(corpo.getAttribute('data-aberto')).toBe('true');
  });

  it('não oferece o relatório enquanto a obra não tem estoque', () => {
    const { fixture, el } = abrir([], []);
    fixture.componentRef.setInput('saldo', { estado: 'ready', dados: { idEstoque: null, itens: [] } });
    fixture.detectChanges();
    expect(el.querySelector('.mat-secao__acoes a')).toBeNull();
  });

  it('avisa quando a obra não tem saldo e permite tentar de novo se a carga falhar', () => {
    const { fixture, el } = abrir([], []);
    fixture.componentRef.setInput('saldo', { estado: 'ready', dados: { idEstoque: 'est-1', itens: [] } });
    fixture.detectChanges();
    expect(el.textContent).toContain('Nenhum material em saldo');

    let pedidos = 0;
    fixture.componentInstance.recarregarSaldo.subscribe(() => pedidos++);
    fixture.componentRef.setInput('saldo', { estado: 'error', mensagem: 'Falhou' });
    fixture.detectChanges();
    expect(el.textContent).toContain('Não foi possível carregar o saldo');
    (el.querySelector('ces-async-state button') as HTMLButtonElement).click();
    expect(pedidos).toBe(1);
  });

  it('avisa quando não há nada e quando o histórico foi cortado', () => {
    const { fixture, el } = abrir([], []);
    expect(el.textContent).toContain('Nenhuma solicitação vinculada');
    expect(el.textContent).toContain('Nenhuma movimentação de estoque');

    fixture.componentRef.setInput('movimentacoes', [movimentacao]);
    fixture.componentRef.setInput('truncadas', true);
    fixture.detectChanges();
    expect(el.textContent).toContain('movimentações mais recentes');
  });
});
