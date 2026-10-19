import { z } from "zod";
import { CLINICAL_SIGNS } from "@/entities/cat";


export const CAT_NAME_MAX = 60;
export const WEIGHT_MIN_KG = 0.01;
export const WEIGHT_MAX_KG = 29.99;
export const NOTE_BODY_MAX = 2000;

function isValidDateString(value: string): boolean {
  return !Number.isNaN(new Date(`${value}T00:00:00`).getTime());
}

const notFuture = (value: string) => !value || new Date(`${value}T00:00:00`).getTime() <= Date.now();

export const catFormSchema = z
  .object({
    name: z.string().trim().min(1, "form.name.required").max(CAT_NAME_MAX, "form.name.tooLong"),
    breedCode: z.string(),
    breedOther: z.string(),
    coatColor: z.string(),
    sex: z.string().min(1, "form.sex.required"),
    neutered: z.boolean(),
    ageMode: z.enum(["birthDate", "approx"]),
    birthDate: z
      .string()
      .refine((value) => !value || isValidDateString(value), "form.birthDate.invalid")
      .refine(notFuture, "form.birthDate.future"),
    approxAgeMonths: z.string(),
    weightKg: z.string().refine((value) => {
      if (!value) return true;
      const n = Number(value);
      return !Number.isNaN(n) && n >= WEIGHT_MIN_KG && n <= WEIGHT_MAX_KG;
    }, "form.weight.invalid"),
    notes: z.string(),
  })
  .superRefine((values, ctx) => {
    if (values.ageMode === "birthDate" && !values.birthDate) {
      ctx.addIssue({ code: "custom", message: "form.birthDate.required", path: ["birthDate"] });
    }
    if (values.ageMode === "approx") {
      if (!values.approxAgeMonths) {
        ctx.addIssue({ code: "custom", message: "form.approxAgeMonths.required", path: ["approxAgeMonths"] });
      } else {
        const n = Number(values.approxAgeMonths);
        if (!Number.isInteger(n) || n < 0 || n > 360) {
          ctx.addIssue({ code: "custom", message: "form.approxAgeMonths.invalid", path: ["approxAgeMonths"] });
        }
      }
    }
  });

export type CatFormSchemaValues = z.infer<typeof catFormSchema>;

export const noteFormSchema = z.object({
  noteType: z.string().min(1, "notes.form.noteType.required"),
  body: z.string().trim().min(1, "notes.form.body.required").max(NOTE_BODY_MAX, "notes.form.body.tooLong"),
  occurredOn: z
    .string()
    .refine((value) => !value || isValidDateString(value), "notes.form.occurredOn.invalid")
    .refine(notFuture, "notes.form.occurredOn.future"),
});

export type NoteFormSchemaValues = z.infer<typeof noteFormSchema>;

export const surveyFormSchema = z.object({
  litterType: z.string().min(1, "survey.questions.litterType.required"),
  urinaryHistory: z.string().min(1, "survey.questions.urinaryHistory.required"),
  dietType: z.string().min(1, "survey.questions.dietType.required"),
  urinationFrequency: z.string().min(1, "survey.questions.urinationFrequency.required"),
  symptoms: z.array(z.string()).min(1, "survey.questions.symptoms.required"),
});

export type SurveyFormSchemaValues = z.infer<typeof surveyFormSchema>;

export const clinicalSignFormSchema = z.object({
  signs: z.array(z.enum(CLINICAL_SIGNS as [string, ...string[]])).min(1, "clinicalSigns.form.signs.required"),
  note: z.string().max(NOTE_BODY_MAX, "clinicalSigns.form.note.tooLong"),
});

export type ClinicalSignFormSchemaValues = z.infer<typeof clinicalSignFormSchema>;
