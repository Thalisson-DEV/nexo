import { Injectable, inject } from '@angular/core';
import { MessageService, ToastMessageOptions } from 'primeng/api';

export type CesNotificationVariant = 'success' | 'info' | 'warning' | 'error';

/** Botão dentro da notificação: um atalho para o próximo passo, nunca a única forma de fazê-lo. */
export interface CesNotificationAction {
  label: string;
  run: () => void;
}

export interface CesNotification {
  variant: CesNotificationVariant;
  title?: string;
  message: string;
  life?: number;
  sticky?: boolean;
  closable?: boolean;
  key?: string;
  data?: unknown;
  action?: CesNotificationAction;
}

const DEFAULT_TITLES: Record<CesNotificationVariant, string> = {
  success: 'Concluído',
  info: 'Informação',
  warning: 'Atenção',
  error: 'Não foi possível concluir'
};

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly messages = inject(MessageService);

  show(notification: CesNotification): void {
    const message: ToastMessageOptions = {
      severity: notification.variant === 'warning' ? 'warn' : notification.variant,
      summary: notification.title ?? DEFAULT_TITLES[notification.variant],
      detail: notification.message,
      life: notification.life ?? (notification.action ? 9000 : notification.variant === 'error' ? 7000 : 4500),
      sticky: notification.sticky,
      closable: notification.closable,
      key: notification.key,
      data: notification.action ? { ...(notification.data as object | undefined), cesAction: notification.action } : notification.data
    };

    this.messages.add(message);
  }

  success(message: string, options: Omit<CesNotification, 'variant' | 'message'> = {}): void {
    this.show({ ...options, variant: 'success', message });
  }

  info(message: string, options: Omit<CesNotification, 'variant' | 'message'> = {}): void {
    this.show({ ...options, variant: 'info', message });
  }

  warning(message: string, options: Omit<CesNotification, 'variant' | 'message'> = {}): void {
    this.show({ ...options, variant: 'warning', message });
  }

  error(message: string, options: Omit<CesNotification, 'variant' | 'message'> = {}): void {
    this.show({ ...options, variant: 'error', message });
  }

  /** Usado pelo host global ao clicar no botão da notificação. */
  runAction(action: CesNotificationAction): void {
    this.messages.clear();
    action.run();
  }

  clear(key?: string): void {
    this.messages.clear(key);
  }
}
