import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LookupOption } from '../models/lookup-option';
import { LookupPage } from '../models/lookup-page';
import {
  LOOKUP_RATE_LIMIT_MESSAGE,
  LookupRateLimitError,
  RemoteLookup,
  RemoteLookupRequest,
} from './remote-lookup';

type Option = LookupOption<string>;

const page = (
  items: Option[],
  nextCursor: string | null = null,
  hasMore = false,
): LookupPage<Option> => ({ items, nextCursor, hasMore });

describe('RemoteLookup', () => {
  afterEach(() => vi.useRealTimers());

  it('permanece idle e nao consulta antes do minLength', () => {
    vi.useFakeTimers();
    const loader = vi.fn(() => of(page([{ value: '1', label: 'Maria' }])));
    const lookup = new RemoteLookup(loader);

    lookup.search('m');
    vi.advanceTimersByTime(500);

    expect(loader).not.toHaveBeenCalled();
    expect(lookup.status()).toBe('idle');
    lookup.destroy();
  });

  it('normaliza, aplica debounce e ignora consultas distintas dentro da janela', () => {
    vi.useFakeTimers();
    const loader = vi.fn((request: RemoteLookupRequest) =>
      of(page([{ value: request.query, label: request.query }])),
    );
    const lookup = new RemoteLookup(loader);

    lookup.search('  MAR ');
    lookup.search('mar');
    vi.advanceTimersByTime(299);
    expect(loader).not.toHaveBeenCalled();
    expect(lookup.status()).toBe('idle');

    vi.advanceTimersByTime(1);
    expect(loader).toHaveBeenCalledOnce();
    expect(loader).toHaveBeenCalledWith({ query: 'mar', cursor: null });
    expect(lookup.status()).toBe('success');
    lookup.destroy();
  });

  it('nao repete uma query normalizada por distinctUntilChanged', () => {
    vi.useFakeTimers();
    const loader = vi.fn(() => of(page([{ value: '1', label: 'Maria' }])));
    const lookup = new RemoteLookup(loader);

    lookup.search('Maria');
    vi.advanceTimersByTime(300);
    lookup.search(' maria ');
    vi.advanceTimersByTime(300);

    expect(loader).toHaveBeenCalledOnce();
    lookup.destroy();
  });

  it('cancela a resposta da query anterior com switchMap', () => {
    vi.useFakeTimers();
    const first = new Subject<LookupPage<Option>>();
    const second = new Subject<LookupPage<Option>>();
    const loader = vi.fn((request: RemoteLookupRequest) =>
      request.query === 'ma' ? first : second,
    );
    const lookup = new RemoteLookup(loader);

    lookup.search('ma');
    vi.advanceTimersByTime(300);
    lookup.search('maria');
    vi.advanceTimersByTime(300);

    first.next(page([{ value: 'old', label: 'Resultado antigo' }]));
    expect(lookup.items()).toEqual([]);
    second.next(page([{ value: 'new', label: 'Resultado novo' }]));
    expect(lookup.items()).toEqual([{ value: 'new', label: 'Resultado novo' }]);
    expect(lookup.status()).toBe('success');
    lookup.destroy();
  });

  it('publica loading somente depois do debounce', () => {
    vi.useFakeTimers();
    const response = new Subject<LookupPage<Option>>();
    const lookup = new RemoteLookup(() => response);

    lookup.search('ma');
    expect(lookup.status()).toBe('idle');
    vi.advanceTimersByTime(299);
    expect(lookup.status()).toBe('idle');
    vi.advanceTimersByTime(1);
    expect(lookup.status()).toBe('loading');
    response.next(page([{ value: '1', label: 'Maria' }]));
    expect(lookup.status()).toBe('success');
    lookup.destroy();
  });

  it('carrega paginas seguintes por cursor e acumula sem duplicar opcoes', () => {
    vi.useFakeTimers();
    const loader = vi.fn((request: RemoteLookupRequest) =>
      request.cursor === null
        ? of(page([{ value: '1', label: 'Um' }], 'next', true))
        : of(
            page([
              { value: '1', label: 'Um atualizado' },
              { value: '2', label: 'Dois' },
            ]),
          ),
    );
    const lookup = new RemoteLookup(loader);

    lookup.search('um');
    vi.advanceTimersByTime(300);
    expect(lookup.hasMore()).toBe(true);
    expect(lookup.nextCursor()).toBe('next');
    lookup.loadNext();

    expect(loader).toHaveBeenCalledTimes(2);
    expect(loader).toHaveBeenLastCalledWith({ query: 'um', cursor: 'next' });
    expect(lookup.items()).toEqual([
      { value: '1', label: 'Um' },
      { value: '2', label: 'Dois' },
    ]);
    expect(lookup.hasMore()).toBe(false);
    lookup.destroy();
  });

  it('mantem status success quando append retorna pagina vazia com itens existentes', () => {
    vi.useFakeTimers();
    const loader = vi.fn((request: RemoteLookupRequest) =>
      request.cursor === null
        ? of(page([{ value: '1', label: 'Um' }], 'next', true))
        : of(page([])),
    );
    const lookup = new RemoteLookup(loader);

    lookup.search('um');
    vi.advanceTimersByTime(300);
    lookup.loadNext();

    expect(lookup.status()).toBe('success');
    expect(lookup.items()).toEqual([{ value: '1', label: 'Um' }]);
    lookup.destroy();
  });

  it('preserva selected seed quando resultados chegam', () => {
    vi.useFakeTimers();
    const selected = { value: 'selected', label: 'Selecionado' };
    const lookup = new RemoteLookup(() => of(page([{ value: 'result', label: 'Resultado' }])), {
      initialSelected: [selected],
    });

    expect(lookup.items()).toEqual([selected]);
    lookup.search('re');
    vi.advanceTimersByTime(300);
    expect(lookup.items()).toEqual([selected, { value: 'result', label: 'Resultado' }]);
    lookup.seedSelected({ value: 'another', label: 'Outro' });
    expect(lookup.items()).toEqual([
      selected,
      { value: 'another', label: 'Outro' },
      { value: 'result', label: 'Resultado' },
    ]);
    lookup.destroy();
  });

  it('preserva o label de valores selecionados ao substituir a página', () => {
    vi.useFakeTimers();
    let selectedValues: readonly string[] = ['keep'];
    const loader = vi.fn((request: RemoteLookupRequest) =>
      request.query === 'pri'
        ? of(
            page([
              { value: 'keep', label: 'Valor preservado' },
              { value: 'old', label: 'Antigo' },
            ]),
          )
        : of(page([{ value: 'new', label: 'Novo' }])),
    );
    const lookup = new RemoteLookup(loader, { selectedValues: () => selectedValues });

    lookup.search('pri');
    vi.advanceTimersByTime(300);
    selectedValues = ['keep'];
    lookup.search('nov');
    vi.advanceTimersByTime(300);

    expect(lookup.items()).toEqual([
      { value: 'keep', label: 'Valor preservado' },
      { value: 'new', label: 'Novo' },
    ]);
    lookup.destroy();
  });

  it('mantem opcoes validas e permite retry manual apos erro', () => {
    vi.useFakeTimers();
    const response = new Subject<LookupPage<Option>>();
    let attempts = 0;
    const loader = vi.fn(() => {
      attempts += 1;
      return attempts === 1
        ? of(page([{ value: 'valid', label: 'Válido' }]))
        : attempts === 2
          ? throwError(() => new Error('offline'))
          : response;
    });
    const lookup = new RemoteLookup(loader);

    lookup.search('va');
    vi.advanceTimersByTime(300);
    lookup.search('valid');
    vi.advanceTimersByTime(300);

    expect(lookup.status()).toBe('error');
    expect(lookup.items()).toEqual([{ value: 'valid', label: 'Válido' }]);
    lookup.retry();
    expect(lookup.status()).toBe('loading');
    response.next(page([{ value: 'valid-2', label: 'Válido novamente' }]));
    expect(lookup.status()).toBe('success');
    expect(loader).toHaveBeenCalledTimes(3);
    lookup.destroy();
  });

  it('converte 429 para erro amigavel', () => {
    vi.useFakeTimers();
    const lookup = new RemoteLookup(() =>
      throwError(() => ({ status: 429, error: { retryAfter: 2 } })),
    );

    lookup.search('ma');
    vi.advanceTimersByTime(300);

    expect(lookup.status()).toBe('error');
    expect(lookup.error()).toBeInstanceOf(LookupRateLimitError);
    expect((lookup.error() as LookupRateLimitError).message).toBe(LOOKUP_RATE_LIMIT_MESSAGE);
    expect((lookup.error() as LookupRateLimitError).retryAfterMs).toBe(2_000);
    lookup.destroy();
  });

  it('encerra pela DestroyRef e pelo destroy explicito', () => {
    const onDestroy = vi.fn<(callback: () => void) => () => void>();
    const loader = vi.fn(() => of(page([])));
    const lookup = new RemoteLookup(loader, { destroyRef: { onDestroy, destroyed: false } });

    expect(onDestroy).toHaveBeenCalledOnce();
    onDestroy.mock.calls[0][0]();
    lookup.search('ma');
    vi.useFakeTimers();
    vi.advanceTimersByTime(500);
    expect(loader).not.toHaveBeenCalled();
    lookup.destroy();
  });
});
