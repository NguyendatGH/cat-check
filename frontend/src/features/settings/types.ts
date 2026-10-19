
export type AttentionAlertChannel = "PUSH_AND_INAPP" | "INAPP_ONLY";

export interface NotificationPreferences {
  attentionAlertChannel: AttentionAlertChannel;
  creditAlertsEnabled: boolean;
  reportReadyEnabled: boolean;
  imageRetentionWarningEnabled: boolean;
  normalResultEnabled: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string;
  quietHoursEnd: string;
}

export type NotificationToggleKey =
  "creditAlertsEnabled" | "reportReadyEnabled" | "imageRetentionWarningEnabled" | "normalResultEnabled";
