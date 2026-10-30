export interface CesSelectOption<T> {
  label: string;
  value: T;
  disabled?: boolean;
}

export type CesDateValue = Date | null;
export type CesBooleanControlKind = 'checkbox' | 'toggle';
export type CesOverlayAppendTarget = 'body' | 'self';
