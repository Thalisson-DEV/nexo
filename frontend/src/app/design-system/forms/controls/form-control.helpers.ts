let controlSequence = 0;

export const createControlId = (): string => `ces-control-${++controlSequence}`;

export const getDescribedBy = (
  controlId: string,
  description: string | undefined,
  error: string | undefined,
): string | undefined => {
  if (error) return `${controlId}-error`;
  if (description) return `${controlId}-description`;
  return undefined;
};

export const getTextInputValue = (event: Event): string => (event.target as HTMLInputElement).value;

export const getTextareaValue = (event: Event): string =>
  (event.target as HTMLTextAreaElement).value;
