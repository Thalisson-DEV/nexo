import { Estabilizador, janelaNoQuadro, sobreALinha } from './bip';

describe('leitor no modo bip', () => {
  describe('janela da mira no quadro da câmera', () => {
    it('celular em pé com câmera deitada: corta as laterais (object-fit: cover)', () => {
      // Tela 360x480 mostrando um quadro 1920x1080: escala pela altura (480/1080), sobra largura.
      const area = janelaNoQuadro({ x: 25, y: 180, width: 310, height: 120 }, { largura: 360, altura: 480 }, { largura: 1920, altura: 1080 });
      const escala = 480 / 1080;
      const dx = (360 - 1920 * escala) / 2;
      expect(area.x).toBe(Math.round((25 - dx) / escala));
      expect(area.y).toBe(Math.round(180 / escala));
      expect(area.width).toBe(Math.round(310 / escala));
      expect(area.height).toBe(Math.round(120 / escala));
    });

    it('nunca sai do quadro', () => {
      const area = janelaNoQuadro({ x: -50, y: -50, width: 5000, height: 5000 }, { largura: 400, altura: 300 }, { largura: 1280, altura: 720 });
      expect(area.x).toBeGreaterThanOrEqual(0);
      expect(area.y).toBeGreaterThanOrEqual(0);
      expect(area.x + area.width).toBeLessThanOrEqual(1280);
      expect(area.y + area.height).toBeLessThanOrEqual(720);
    });
  });

  it('com dois códigos na mira, fica o que está sobre a linha', () => {
    const acima = { rawValue: 'A', boundingBox: { x: 0, y: 0, width: 300, height: 40 } };
    const naLinha = { rawValue: 'B', boundingBox: { x: 0, y: 80, width: 300, height: 40 } };
    expect(sobreALinha([acima, naLinha], 200)?.rawValue).toBe('B');
    expect(sobreALinha([{ rawValue: '  ' }], 200)).toBeNull();
  });

  describe('estabilizador', () => {
    const ean = (v: string) => ({ rawValue: v, format: 'ean_13' });
    const itf = (v: string) => ({ rawValue: v, format: 'itf' });

    it('bipa uma vez e não repete o código enquanto ele segue na mira', () => {
      const e = new Estabilizador();
      expect(e.considerar(ean('789'), 0)).toBe('789');
      for (let t = 100; t <= 5000; t += 100) expect(e.considerar(ean('789'), t)).toBeNull();
    });

    it('o mesmo código bipa de novo só depois de sair da mira', () => {
      const e = new Estabilizador();
      e.considerar(ean('789'), 0);
      expect(e.considerar(null, 500)).toBeNull();
      expect(e.considerar(ean('789'), 1500)).toBe('789');
    });

    it('trava logo após o bip: o código vizinho não entra junto', () => {
      const e = new Estabilizador();
      e.considerar(ean('111'), 0);
      expect(e.considerar(ean('222'), 300)).toBeNull();
      expect(e.considerar(ean('222'), 700)).toBe('222');
    });

    it('formato sem dígito verificador precisa de duas leituras iguais', () => {
      const e = new Estabilizador();
      expect(e.considerar(itf('1234'), 0)).toBeNull();
      expect(e.considerar(itf('1239'), 100)).toBeNull();
      expect(e.considerar(itf('1239'), 200)).toBe('1239');
    });

    it('no gatilho, apertar de novo no mesmo código lê de novo', () => {
      const e = new Estabilizador();
      e.considerar(ean('789'), 0, true);
      expect(e.considerar(ean('789'), 800, true)).toBe('789');
    });
  });
});
