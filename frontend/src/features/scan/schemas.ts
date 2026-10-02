import { z } from "zod";

/**
 * Zod schema cho `features/scan`. Message là KEY i18n (namespace `scan`) — dịch lúc render,
 * cùng quy ước `features/onboarding/schemas.ts`.
 */

export const DISPUTE_NOTE_MAX = 500;

export const disputeNoteSchema = z.object({
  note: z.string().trim().max(DISPUTE_NOTE_MAX, "dispute.note.tooLong").optional(),
});

export type DisputeNoteFormValues = z.infer<typeof disputeNoteSchema>;
