import {
  SNAPSHOT_SCHEMA, SessaoSnapshot, selar, lerSnapshot, decidirRetomada, fnv1a, jsonCanonico,
  JANELA_RETOMADA_MS, rotaInternaSegura, ResultadoLeitura,
} from './snapshot';

const AGORA = Date.UTC(2026, 9, 8, 15, 0, 0);

function base(over: Partial<SessaoSnapshot> = {}): SessaoSnapshot {
  return {
    schema: SNAPSHOT_SCHEMA,
    salvoEm: AGORA - 60_000,
    idUsuario: 'u-1',
    rota: '/campo/nova',
    pilha: ['/hoje'],
    telas: { '/campo': { rolagem: 420, filtro: 'hoje' } },
    idAba: 'aba-1',
    seq: 7,
    ...over,
  };
}

describe('snapshot da sessão', () => {
  describe('serialização e soma de verificação', () => {
    it('lê de volta exatamente o que foi selado', () => {
      const lido = lerSnapshot(selar(base()));
      expect(lido).toEqual({ ok: true, snapshot: base() });
    });

    it('o checksum não depende da ordem das chaves', () => {
      expect(fnv1a(jsonCanonico({ a: 1, b: { c: 2, d: 3 } })))
        .toBe(fnv1a(jsonCanonico({ b: { d: 3, c: 2 }, a: 1 })));
    });

    it('sobrevive a um ciclo JSON (como o IndexedDB guarda)', () => {
      const gravado = JSON.parse(JSON.stringify(selar(base())));
      expect(lerSnapshot(gravado).ok).toBeTrue();
    });

    it('detecta qualquer campo alterado depois de selado', () => {
      const adulterados: Array<(s: any) => void> = [
        s => (s.rota = '/perfil'),
        s => (s.salvoEm += 1),
        s => (s.telas['/campo'].rolagem = 0),
        s => s.pilha.push('/x'),
        s => (s.idUsuario = 'u-2'),
      ];
      for (const adulterar of adulterados) {
        const s: any = JSON.parse(JSON.stringify(selar(base())));
        adulterar(s);
        expect(lerSnapshot(s)).toEqual({ ok: false, motivo: 'checksum' });
      }
    });

    it('rejeita sem lançar: nulo, texto, número, objeto vazio, sem checksum', () => {
      expect(lerSnapshot(null)).toEqual({ ok: false, motivo: 'vazio' });
      expect(lerSnapshot(undefined)).toEqual({ ok: false, motivo: 'vazio' });
      expect(lerSnapshot('{"schema":2}')).toEqual({ ok: false, motivo: 'formato' });
      expect(lerSnapshot(42)).toEqual({ ok: false, motivo: 'formato' });
      expect(lerSnapshot({})).toEqual({ ok: false, motivo: 'formato' });
      const { checksum: _c, ...semChecksum } = selar(base());
      expect(lerSnapshot(semChecksum)).toEqual({ ok: false, motivo: 'checksum' });
    });

    it('rejeita registro parcial: falta qualquer um dos campos', () => {
      const completo = selar(base());
      for (const campo of Object.keys(completo)) {
        const parcial: Record<string, unknown> = { ...completo };
        delete parcial[campo];
        expect(lerSnapshot(parcial).ok).withContext(`sem ${campo}`).toBeFalse();
      }
    });

    it('recusa schema de uma versão futura (downgrade do app) sem quebrar', () => {
      expect(lerSnapshot({ ...selar(base()), schema: SNAPSHOT_SCHEMA + 1 }))
        .toEqual({ ok: false, motivo: 'schema-futuro' });
    });

    it('recusa campos com tipo errado mesmo com checksum válido', () => {
      const invalidos: Partial<SessaoSnapshot>[] = [
        { rota: 'campo/nova' }, { idUsuario: '' }, { salvoEm: NaN }, { pilha: [1 as any] }, { telas: [] as any },
      ];
      for (const inv of invalidos) {
        expect(lerSnapshot(selar(base(inv))).ok).withContext(JSON.stringify(inv)).toBeFalse();
      }
    });
  });

  describe('migração de schema', () => {
    it('migra o formato v1 para o atual preservando rota, dono e estado de tela', () => {
      const v1 = { versao: 1, ts: AGORA - 1000, usuario: 'u-1', url: '/solicitacoes', ui: { '/solicitacoes': { aba: 'pendentes' } } };
      const lido = lerSnapshot(v1);
      expect(lido.ok).toBeTrue();
      if (!lido.ok) return;
      expect(lido.migradoDe).toBe(1);
      expect(lido.snapshot.rota).toBe('/solicitacoes');
      expect(lido.snapshot.idUsuario).toBe('u-1');
      expect(lido.snapshot.telas).toEqual({ '/solicitacoes': { aba: 'pendentes' } });
      expect(lido.snapshot.schema).toBe(SNAPSHOT_SCHEMA);
    });

    it('v1 inválido após migrar é rejeitado, não restaurado pela metade', () => {
      expect(lerSnapshot({ versao: 1, ts: 'ontem', usuario: 'u-1', url: '/x' }).ok).toBeFalse();
      expect(lerSnapshot({ versao: 1 }).ok).toBeFalse();
    });

    it('versão sem migração conhecida é rejeitada', () => {
      expect(lerSnapshot({ versao: 0 })).toEqual({ ok: false, motivo: 'formato' });
    });
  });

  describe('decisão de retomada', () => {
    const ok = (s: SessaoSnapshot): ResultadoLeitura => ({ ok: true, snapshot: s });
    const ctx = { agora: AGORA, idUsuario: 'u-1', autenticado: true };

    const casos: Array<[string, ResultadoLeitura, Partial<typeof ctx>, unknown]> = [
      ['recente, mesma conta → retoma', ok(base()), {}, { acao: 'retomar', rota: '/campo/nova' }],
      ['1 s atrás → retoma', ok(base({ salvoEm: AGORA - 1000 })), {}, { acao: 'retomar', rota: '/campo/nova' }],
      ['11 h 59 → retoma', ok(base({ salvoEm: AGORA - JANELA_RETOMADA_MS + 60_000 })), {}, { acao: 'retomar', rota: '/campo/nova' }],
      ['12 h 01 → início', ok(base({ salvoEm: AGORA - JANELA_RETOMADA_MS - 60_000 })), {}, { acao: 'inicio', motivo: 'expirado' }],
      ['outra conta → início', ok(base({ idUsuario: 'u-2' })), {}, { acao: 'inicio', motivo: 'outra-conta' }],
      ['token vencido → login e volta', ok(base()), { autenticado: false }, { acao: 'login', rotaDepois: '/campo/nova' }],
      ['sem dono no aparelho → login', ok(base()), { idUsuario: null as any }, { acao: 'login', rotaDepois: null }],
      ['corrompido → início', { ok: false, motivo: 'checksum' }, {}, { acao: 'inicio', motivo: 'checksum' }],
      ['vazio → início', { ok: false, motivo: 'vazio' }, {}, { acao: 'inicio', motivo: 'sem-snapshot' }],
      ['rota /login → início', ok(base({ rota: '/login' })), {}, { acao: 'inicio', motivo: 'rota-invalida' }],
      ['rota /sync → início', ok(base({ rota: '/sync' })), {}, { acao: 'inicio', motivo: 'rota-invalida' }],
      ['rota raiz → início', ok(base({ rota: '/' })), {}, { acao: 'inicio', motivo: 'rota-invalida' }],
      ['relógio voltou 1 h → início', ok(base({ salvoEm: AGORA + 3_600_000 })), {}, { acao: 'inicio', motivo: 'relogio' }],
      ['relógio adiantado 1 min (tolerado) → retoma', ok(base({ salvoEm: AGORA + 60_000 })), {}, { acao: 'retomar', rota: '/campo/nova' }],
      ['janela configurada de 1 h', ok(base({ salvoEm: AGORA - 2 * 3_600_000 })), { janelaMs: 3_600_000 } as any, { acao: 'inicio', motivo: 'expirado' }],
      ['mantém query string', ok(base({ rota: '/campo/nova?editId=abc' })), {}, { acao: 'retomar', rota: '/campo/nova?editId=abc' }],
    ];

    for (const [nome, leitura, extra, esperado] of casos) {
      it(nome, () => expect(decidirRetomada(leitura, { ...ctx, ...extra })).toEqual(esperado as any));
    }
  });

  describe('rota de volta segura', () => {
    it('aceita só caminhos internos', () => {
      expect(rotaInternaSegura('/campo/nova?editId=1')).toBe('/campo/nova?editId=1');
      expect(rotaInternaSegura('https://evil.example')).toBeNull();
      expect(rotaInternaSegura('//evil.example')).toBeNull();
      expect(rotaInternaSegura('/\\evil')).toBeNull();
      expect(rotaInternaSegura('/login')).toBeNull();
      expect(rotaInternaSegura('')).toBeNull();
      expect(rotaInternaSegura(null)).toBeNull();
    });
  });
});
