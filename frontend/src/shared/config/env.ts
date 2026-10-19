import { z } from "zod";

const envSchema = z.object({
  MODE: z.enum(["development", "production", "test"]),
  DEV: z.boolean(),
  PROD: z.boolean(),
  VITE_FIREBASE_VAPID_KEY: z.string().optional(),
  VITE_FIREBASE_API_KEY: z.string().optional(),
  VITE_FIREBASE_PROJECT_ID: z.string().optional(),
  VITE_FIREBASE_APP_ID: z.string().optional(),
  VITE_FIREBASE_MESSAGING_SENDER_ID: z.string().optional(),
  VITE_MAP_TILES_URL: z.string().pipe(z.url()).optional(),
});

const parsed = envSchema.safeParse(import.meta.env);

if (!parsed.success) {
  console.error("Cấu hình biến môi trường không hợp lệ:", z.treeifyError(parsed.error));
  throw new Error("Invalid environment configuration — see console for details.");
}

export const env = parsed.data;

export const isPushEnabled = Boolean(env.VITE_FIREBASE_VAPID_KEY);

export const firebaseWebConfig: {
  apiKey: string;
  projectId: string;
  appId: string;
  messagingSenderId: string;
} | null =
  env.VITE_FIREBASE_API_KEY &&
  env.VITE_FIREBASE_PROJECT_ID &&
  env.VITE_FIREBASE_APP_ID &&
  env.VITE_FIREBASE_MESSAGING_SENDER_ID
    ? {
        apiKey: env.VITE_FIREBASE_API_KEY,
        projectId: env.VITE_FIREBASE_PROJECT_ID,
        appId: env.VITE_FIREBASE_APP_ID,
        messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID,
      }
    : null;

export const firebaseVapidKey: string | null = env.VITE_FIREBASE_VAPID_KEY ?? null;

export const mapTilesUrl: string | null = env.VITE_MAP_TILES_URL ?? null;
