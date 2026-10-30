import { LookupOption } from './lookup-option';

/** Catálogos pequenos compartilhados pelo bootstrap da sessão. */
export interface LookupBootstrap {
  operationalBases: LookupOption[];
  movementTypes: LookupOption[];
  requestTypes: LookupOption[];
  statuses: LookupOption[];
  processes: LookupOption[];
}
