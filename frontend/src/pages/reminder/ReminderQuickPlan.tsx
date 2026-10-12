import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { Button } from "@/shared/ui";
import {
  REMINDER_ERROR,
  reminderCalendarUrl,
  reminderErrorCode,
  useUpdateReminder,
  type Reminder,
  type ReminderChannel,
} from "@/features/reminder";
import {
  AddToCalendarLink,
  ChannelPicker,
  FrequencyPicker,
  GenericSaveError,
  ScheduleInvalidNotice,
  TimeWindowPicker,
  buildReminderPatch,
  firstFieldErrors,
  reminderFormSchema,
  toReminderForm,
  type ReminderFormValues,
} from "./reminderUi";

export interface ReminderQuickPlanProps {
  reminders: Reminder[];
  catNameById: Map<string, string>;
  wide?: boolean;
}

export function ReminderQuickPlan({ reminders, catNameById, wide = false }: ReminderQuickPlanProps) {
  const { t } = useTranslation(["reminder", "common"]);
  const updateReminder = useUpdateReminder();

  const [pickedId, setPickedId] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ id: string; values: ReminderFormValues } | null>(null);
  const [errors, setErrors] = useState<Partial<Record<string, string>>>({});

  const scanPlans = reminders.filter((reminder) => reminder.type === "SCAN_ROUTINE");
  const plans = scanPlans.length > 0 ? scanPlans : reminders;
  const fallback = plans.find((reminder) => reminder.active) ?? plans.at(0);
  const selected = (pickedId === null ? undefined : plans.find((reminder) => reminder.id === pickedId)) ?? fallback;

  if (selected === undefined) return null;

  const values = draft !== null && draft.id === selected.id ? draft.values : toReminderForm(selected);

  const patch = (change: Partial<ReminderFormValues>) => {
    setDraft({ id: selected.id, values: { ...values, ...change } });
  };

  const catLabel = (reminder: Reminder) =>
    reminder.catId === undefined ? t("list.accountWide") : (catNameById.get(reminder.catId) ?? t("list.unknownCat"));

  const saveErrorCode = reminderErrorCode(updateReminder.error);
  const isSaving = updateReminder.isPending;

  const handleSubmit = () => {
    const parsed = reminderFormSchema.safeParse(values);
    if (!parsed.success) {
      setErrors(firstFieldErrors(parsed.error));
      return;
    }
    setErrors({});
    const body = buildReminderPatch(values, selected);
    if (Object.keys(body).length === 0) {
      toast.success(t("form.saved"));
      return;
    }
    updateReminder.mutate(
      { reminderId: selected.id, patch: body },
      {
        onSuccess: () => {
          toast.success(t("form.saved"));
        },
      },
    );
  };

  const planTarget =
    plans.length > 1 ? (
      <select
        value={selected.id}
        aria-label={t("list.planPickLabel")}
        onChange={(event) => {
          setPickedId(event.target.value);
          setErrors({});
        }}
        className="h-9 max-w-full rounded-xl bg-background-alt px-3 text-caption font-semibold text-primary-dark focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
      >
        {plans.map((reminder) => (
          <option key={reminder.id} value={reminder.id}>
            {t("list.planFor", { name: catLabel(reminder) })}
          </option>
        ))}
      </select>
    ) : (
      <span className="text-caption font-semibold text-primary-dark">
        {t("list.planFor", { name: catLabel(selected) })}
      </span>
    );

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        handleSubmit();
      }}
      className={wide ? "grid gap-4 xl:grid-cols-2" : "flex flex-col gap-4"}
    >
      <FrequencyPicker
        scheduleMode={values.scheduleMode}
        intervalDays={values.intervalDays}
        rrule={values.rrule}
        errorKeyInterval={errors.intervalDays}
        errorKeyRrule={errors.rrule}
        headerAction={planTarget}
        onModeChange={(mode) => {
          patch({ scheduleMode: mode });
        }}
        onIntervalChange={(value) => {
          patch({ intervalDays: value });
        }}
        onRruleChange={(value) => {
          patch({ rrule: value });
        }}
      />

      <div className={wide ? "flex flex-col gap-4" : "contents"}>
        <TimeWindowPicker
          start={values.preferredTimeStart}
          end={values.preferredTimeEnd}
          timezone={selected.timezone}
          errorKey={errors.preferredTimeEnd}
          onStartChange={(value) => {
            patch({ preferredTimeStart: value });
          }}
          onEndChange={(value) => {
            patch({ preferredTimeEnd: value });
          }}
        />

        <ChannelPicker
          value={values.channels}
          errorKey={errors.channels}
          onChange={(channels: ReminderChannel[]) => {
            patch({ channels });
          }}
        />
      </div>

      <div className={wide ? "flex flex-col gap-3 xl:col-span-2 xl:flex-row xl:items-center" : "contents"}>
        {saveErrorCode === REMINDER_ERROR.SCHEDULE_INVALID ? (
          <ScheduleInvalidNotice />
        ) : updateReminder.isError ? (
          <GenericSaveError />
        ) : null}

        <Button type="submit" size="lg" loading={isSaving} className={wide ? "xl:ml-auto xl:w-60" : undefined}>
          {t("form.submitPlan")}
        </Button>

        <AddToCalendarLink
          href={reminderCalendarUrl(selected.id)}
          label={t("list.syncCalendar")}
          className={
            wide
              ? "whitespace-nowrap border-transparent bg-background-alt xl:px-6"
              : "w-full border-transparent bg-background-alt"
          }
        />
      </div>
    </form>
  );
}
