import { LookupOption } from './lookup-option';

/** Página retornada pelos endpoints de lookup baseados em cursor. */
export interface LookupPage<T = LookupOption> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
