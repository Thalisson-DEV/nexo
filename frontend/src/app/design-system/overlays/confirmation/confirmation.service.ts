import { Injectable, inject } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

export type CesConfirmationVariant = 'default' | 'destructive';

export interface CesConfirmationRequest {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  icon?: string;
  variant?: CesConfirmationVariant;
  key?: string;
}

@Injectable({ providedIn: 'root' })
export class CesConfirmationService {
  private readonly confirmations = inject(ConfirmationService);

  confirm(request: CesConfirmationRequest): Promise<boolean> {
    return new Promise((resolve) => {
      const destructive = request.variant === 'destructive';
      this.confirmations.confirm({
        key: request.key,
        header: request.title,
        message: request.message,
        icon: request.icon ?? (destructive ? 'pi pi-exclamation-triangle' : 'pi pi-question-circle'),
        acceptLabel: request.confirmLabel ?? (destructive ? 'Excluir' : 'Confirmar'),
        rejectLabel: request.cancelLabel ?? 'Cancelar',
        acceptButtonProps: {
          severity: destructive ? 'danger' : 'success',
          styleClass: destructive
            ? '!bg-red-600 !border-red-600 !text-white hover:!bg-red-700 !font-semibold !px-6 !py-2 !rounded-lg'
            : '!bg-green-600 !border-green-600 !text-white hover:!bg-green-700 !font-semibold !px-6 !py-2 !rounded-lg'
        },
        rejectButtonProps: {
          severity: 'danger',
          outlined: true,
          styleClass:
            '!bg-transparent !border-red-300 dark:!border-red-800/70 !text-red-600 dark:!text-red-400 hover:!bg-red-50 dark:hover:!bg-red-950/40 !font-semibold !px-6 !py-2 !rounded-lg'
        },
        accept: () => resolve(true),
        reject: () => resolve(false)
      });
    });
  }
}
