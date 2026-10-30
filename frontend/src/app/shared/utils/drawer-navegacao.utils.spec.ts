import { afterEach, describe, expect, it, vi } from 'vitest';

import { limparMascarasOrfas, navegarSaindoDoDrawer } from './drawer-navegacao.utils';

function mascara(classes: string): HTMLElement {
  const el = document.createElement('div');
  el.className = classes;
  document.body.appendChild(el);
  return el;
}

describe('drawer-navegacao.utils', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    document.body.className = '';
    vi.restoreAllMocks();
  });

  it('remove a máscara que ficou saindo e devolve a rolagem', () => {
    const orfa = mascara('p-drawer-mask p-overlay-mask p-overlay-mask-leave-active');
    document.body.classList.add('p-overflow-hidden');

    limparMascarasOrfas();

    expect(orfa.isConnected).toBe(false);
    expect(document.body.classList).not.toContain('p-overflow-hidden');
  });

  it('não mexe num overlay que continua aberto', () => {
    const aberta = mascara('p-dialog-mask p-overlay-mask');
    document.body.classList.add('p-overflow-hidden');

    limparMascarasOrfas();

    expect(aberta.isConnected).toBe(true);
    expect(document.body.classList).toContain('p-overflow-hidden');
  });

  it('limpa depois que a navegação termina', async () => {
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      cb(0);
      return 0;
    });
    const orfa = mascara('p-drawer-mask p-overlay-mask p-overlay-mask-leave-active');

    await expect(navegarSaindoDoDrawer(() => Promise.resolve(true))).resolves.toBe(true);

    expect(orfa.isConnected).toBe(false);
  });
});
