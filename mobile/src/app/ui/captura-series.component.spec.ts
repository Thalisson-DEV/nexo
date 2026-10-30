import { TestBed } from '@angular/core/testing';
import { CapturaSeriesComponent } from './captura-series.component';

describe('CapturaSeriesComponent', () => {
  function criar(): CapturaSeriesComponent {
    TestBed.configureTestingModule({ imports: [CapturaSeriesComponent] });
    const fixture = TestBed.createComponent(CapturaSeriesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('lê a série da etiqueta GS1', () => {
    const c = criar();
    c.adicionar(['(01)07891234567895(21)tr0001'], 'leitura');
    expect(c.series()).toEqual(['TR0001']);
  });

  it('segura para conferir a leitura que destoa do lote e deixa aceitar ou ignorar', () => {
    const c = criar();
    c.adicionar(['ABC0001', 'ABC0002', 'ABC0003'], 'lote');
    c.adicionar(['7891234567895'], 'leitura');
    expect(c.series().length).toBe(3);
    expect(c.conferir()[0]).toEqual(jasmine.objectContaining({ serie: '7891234567895', tipo: 'suspeita' }));

    c.ignorar('7891234567895');
    c.adicionar(['7891234567895'], 'leitura');
    expect(c.ultimo()?.texto).toBe('7891234567895 ignorada');
    expect(c.series().length).toBe(3);

    c.adicionar(['ABC9'], 'leitura');
    c.aceitarSuspeita('ABC9');
    expect(c.series()).toContain('ABC9');
  });

  it('desfaz a última adição de uma vez', () => {
    const c = criar();
    c.adicionar(['A1']);
    c.adicionar(['B1', 'B2']);
    c.desfazer();
    expect(c.series()).toEqual(['A1']);
    expect(c.ultimaAdicao()).toEqual([]);
  });

  it('leitor por teclado (USB/Bluetooth) funciona fora do campo de texto', () => {
    const c = criar();
    c.aberta.set(true);
    for (const tecla of ['S', 'N', '9', '9', 'Enter']) {
      c.teclaGlobal(new KeyboardEvent('keydown', { key: tecla }));
    }
    expect(c.series()).toEqual(['SN99']);
  });

  it('tira da lista as séries que já estão no sistema', async () => {
    TestBed.configureTestingModule({ imports: [CapturaSeriesComponent] });
    const fixture = TestBed.createComponent(CapturaSeriesComponent);
    fixture.componentRef.setInput('verificar', async (series: string[]) =>
      new Map(series.filter(s => s === 'X2').map(s => [s, 'Já está cadastrada em OBRA 1.'] as [string, string])));
    const c = fixture.componentInstance;
    fixture.detectChanges();
    c.adicionar(['X1', 'X2']);
    expect(c.verificando()).toBe(2);
    await new Promise(r => setTimeout(r, 600));
    expect(c.series()).toEqual(['X1']);
    expect(c.conferir()[0]).toEqual(jasmine.objectContaining({ serie: 'X2', tipo: 'recusada' }));
  });
});
