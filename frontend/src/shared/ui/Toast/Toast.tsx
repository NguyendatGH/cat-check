import { Button, ConfigProvider, notification } from "antd";
import type { NotificationArgsProps } from "antd";
import type { ReactNode } from "react";
import { toastTheme } from "./toastTheme";

type NotificationApi = ReturnType<typeof notification.useNotification>[0];
type ToastKind = "success" | "error" | "info" | "warning";

export interface ToastOptions {
  description?: ReactNode;
  duration?: number;
  action?: { label: ReactNode; onClick: () => void };
}

export interface ToastProps {
  placement?: NotificationArgsProps["placement"];
}

let api: NotificationApi | null = null;
let seq = 0;

function show(kind: ToastKind, message: ReactNode, options: ToastOptions = {}) {
  const key = `toast-${String(++seq)}`;
  const target = api ?? notification;
  const { action } = options;
  target[kind]({
    key,
    title: message,
    description: options.description,
    duration: options.duration ?? 3,
    showProgress: true,
    pauseOnHover: true,
    actions: action ? (
      <Button
        type="primary"
        size="small"
        onClick={() => {
          action.onClick();
          target.destroy(key);
        }}
      >
        {action.label}
      </Button>
    ) : undefined,
  });
  return key;
}

export const toast = {
  success: (message: ReactNode, options?: ToastOptions) => show("success", message, options),
  error: (message: ReactNode, options?: ToastOptions) => show("error", message, options),
  info: (message: ReactNode, options?: ToastOptions) => show("info", message, options),
  warning: (message: ReactNode, options?: ToastOptions) => show("warning", message, options),
  dismiss: (key?: string) => {
    (api ?? notification).destroy(key);
  },
};

/** Mounts the antd notification holder; `toast.*` calls render here. */
export function Toast({ placement = "top" }: ToastProps) {
  const [notificationApi, holder] = notification.useNotification({
    placement,
    maxCount: 3,
    stack: { threshold: 2 },
    top: 72,
  });
  api = notificationApi;
  return (
    <ConfigProvider theme={toastTheme}>
      {holder}
    </ConfigProvider>
  );
}
