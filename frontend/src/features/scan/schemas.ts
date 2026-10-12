import { z } from "zod";


export const DISPUTE_NOTE_MAX = 500;

export const disputeNoteSchema = z.object({
  note: z.string().trim().max(DISPUTE_NOTE_MAX, "dispute.note.tooLong").optional(),
});

export type DisputeNoteFormValues = z.infer<typeof disputeNoteSchema>;
