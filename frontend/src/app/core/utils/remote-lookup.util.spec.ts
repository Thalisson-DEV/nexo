import { Observable, Subject, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PagedResponse } from '../models/shared.models';
import { RemoteLookup } from './remote-lookup.util';

interface Option {
  id: string;
  label: string;
}

function page(
  content: Option[],
  number = 0,
  totalPages = 1,
  totalElements = content.length,
): PagedResponse<Option> {
  return {
    content,
    page: { number, size: 30, totalElements, totalPages },
  };
}

describe('RemoteLookup', () => {
  afterEach(() => vi.useRealTimers());

  it('espera a digitação estabilizar e consulta somente o texto mais recente', async () => {
    vi.useFakeTimers();
    const load = vi.fn(() => of(page([])));
    const lookup = new RemoteLookup<Option>({ load, identify: (item) => item.id });

    lookup.search('ma');
    await vi.advanceTimersByTimeAsync(150);
    lookup.search('material');
    await vi.advanceTimersByTimeAsync(299);

    expect(load).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(load).toHaveBeenCalledTimes(1);
    expect(load).toHaveBeenCalledWith({ query: 'material', page: 0, size: 30 });
    lookup.destroy();
  });

  it('cancela a resposta anterior quando uma nova busca começa', async () => {
    vi.useFakeTimers();
    const requests = new Map<string, Subject<PagedResponse<Option>>>();
    const canceled: string[] = [];
    const load = vi.fn(
      ({ query }: { query: string }): Observable<PagedResponse<Option>> =>
        new Observable((subscriber) => {
          const request = new Subject<PagedResponse<Option>>();
          requests.set(query, request);
          const subscription = request.subscribe(subscriber);
          return () => {
            canceled.push(query);
            subscription.unsubscribe();
          };
        }),
    );
    const lookup = new RemoteLookup<Option>({ load, identify: (item) => item.id });

    lookup.search('antiga', true);
    await vi.advanceTimersByTimeAsync(0);
    lookup.search('nova', true);
    await vi.advanceTimersByTimeAsync(0);
    requests.get('antiga')?.next(page([{ id: 'old', label: 'Antiga' }]));
    requests.get('nova')?.next(page([{ id: 'new', label: 'Nova' }]));

    expect(canceled).toContain('antiga');
    expect(lookup.items()).toEqual([{ id: 'new', label: 'Nova' }]);
    lookup.destroy();
  });

  it('preserva a opção selecionada ao substituir a página de resultados', async () => {
    vi.useFakeTimers();
    let selected = ['selected'];
    const lookup = new RemoteLookup<Option>({
      identify: (item) => item.id,
      selectedKeys: () => selected,
      initialItems: [{ id: 'selected', label: 'Já selecionado' }],
      load: () => of(page([{ id: 'other', label: 'Outro' }], 0, 1, 2)),
    });

    lookup.search('outro', true);
    await vi.advanceTimersByTimeAsync(0);

    expect(lookup.items()).toEqual([
      { id: 'selected', label: 'Já selecionado' },
      { id: 'other', label: 'Outro' },
    ]);
    selected = [];
    lookup.destroy();
  });

  it('anexa a próxima página sem duplicar registros', async () => {
    vi.useFakeTimers();
    const load = vi.fn(({ page: requestedPage }: { page: number }) =>
      of(
        requestedPage === 0
          ? page([{ id: '1', label: 'Um' }], 0, 2, 2)
          : page(
              [
                { id: '1', label: 'Um' },
                { id: '2', label: 'Dois' },
              ],
              1,
              2,
              2,
            ),
      ),
    );
    const lookup = new RemoteLookup<Option>({ load, identify: (item) => item.id });

    lookup.open();
    await vi.advanceTimersByTimeAsync(0);
    expect(lookup.hasMore()).toBe(true);
    lookup.loadNext();
    await vi.advanceTimersByTimeAsync(0);

    expect(load).toHaveBeenLastCalledWith({ query: '', page: 1, size: 30 });
    expect(lookup.items()).toEqual([
      { id: '1', label: 'Um' },
      { id: '2', label: 'Dois' },
    ]);
    expect(lookup.hasMore()).toBe(false);
    lookup.destroy();
  });
});
