import { Injectable, inject } from '@angular/core';
import { MessageService, ToastMessageOptions } from 'primeng/api';

export type CesNotificationVariant = 'success' | 'info' | 'warning' | 'error';

export interface CesNotification {
  variant: CesNotificationVariant;
  title?: string;
  message: string;
  life?: number;
  sticky?: boolean;
  closable?: boolean;
  key?: string;
  data?: unknown;
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
      life: notification.life ?? (notification.variant === 'error' ? 7000 : 4500),
      sticky: notification.sticky,
      closable: notification.closable,
      key: notification.key,
      data: notification.data
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

  clear(key?: string): void {
    this.messages.clear(key);
  }
}
