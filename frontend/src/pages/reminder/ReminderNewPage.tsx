import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { Button } from "@/shared/ui";
import { useCatList } from "@/features/cat";
import {
  REMINDER_ERROR,
  inputTimeToApi,
  isFeatureLocked,
  reminderErrorCode,
  useCreateReminder,
  useReminders,
  type CreateReminderPayload,
} from "@/features/reminder";
import {
  CatSelect,
  ChannelPicker,
  DEFAULT_REMINDER_FORM,
  EducationCard,
  FeatureLockedCard,
  FrequencyPicker,
  GenericSaveError,
  LimitReachedNotice,
  PageHeader,
  ScheduleInvalidNotice,
  TimeWindowPicker,
  reminderFormSchema,
  type ReminderFormValues,
} from "./reminderUi";

/**
 * `/reminders/new` — tạo lịch nhắc (I2 `POST /reminders`).
 *
 * Dựng từ phần giữa frame `09` ("Tần suất Theo dõi" + "Khung giờ Ưu tiên" + "Kênh nhận thông
 * báo" + nút "Lưu Lịch Nhắc nhở"). Trang nằm trong `TaskLayout` nên không tự thêm padding ngang.
 *
 * CHỈ tạo được `type: "SCAN_ROUTINE"`: `CREDIT_EXPIRY` và `SURVEY_FOLLOWUP` là lịch hệ thống
 * tự sinh, không phải thứ người dùng tự đặt — và chúng cũng không phải nội dung của frame này.
 */
const REMINDER_TYPE = "SCAN_ROUTINE";

/** Múi giờ của trình duyệt — server lưu để tính `nextRunAt` đúng khung giờ ưu tiên. */
function browserTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function ReminderNewPage() {
  const { t } = useTranslation(["reminder", "common"]);
  const navigate = useNavigate();

  const catsQuery = useCatList("ACTIVE");
  const remindersQuery = useReminders();
  const createReminder = useCreateReminder();

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ReminderFormValues>({
    resolver: zodResolver(reminderFormSchema),
    defaultValues: DEFAULT_REMINDER_FORM,
  });

  const values = watch();
  const catOptions = (catsQuery.data?.items ?? []).map((cat) => ({ id: cat.id, name: cat.name }));

  const errorCode = reminderErrorCode(createReminder.error);
  const locked = isFeatureLocked(createReminder.error) || isFeatureLocked(remindersQuery.error);

  /**
   * 409 — lịch cùng loại đang bật của chính con mèo vừa chọn. Tìm trong danh sách I1 đã tải
   * để dẫn thẳng sang trang sửa; không tìm được thì rơi về link danh sách.
   */
  const existingReminderId = (remindersQuery.data?.items ?? []).find(
    (reminder) =>
      reminder.catId === values.catId && reminder.type === REMINDER_TYPE && reminder.active,
  )?.id;

  const onSubmit = (formValues: ReminderFormValues) => {
    const payload: CreateReminderPayload = {
      catId: formValues.catId,
      type: REMINDER_TYPE,
      scheduleKind: formValues.scheduleMode,
      intervalDays:
        formValues.scheduleMode === "INTERVAL" ? Number(formValues.intervalDays) : undefined,
      rrule: formValues.scheduleMode === "RRULE" ? formValues.rrule.trim() : undefined,
      preferredTimeStart: inputTimeToApi(formValues.preferredTimeStart),
      preferredTimeEnd: inputTimeToApi(formValues.preferredTimeEnd),
      timezone: browserTimezone(),
      channels: formValues.channels,
      source: "USER",
    };
    createReminder.mutate(payload, {
      onSuccess: (reminder) => {
        toast.success(t("form.saved"));
        void navigate(`/reminders/${reminder.id}`, { replace: true });
      },
    });
  };

  if (locked) {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader title={t("pages.new.title")} />
        <FeatureLockedCard />
      </div>
    );
  }

  const isSaving = isSubmitting || createReminder.isPending;

  return (
    <form
      onSubmit={(event) => {
        void handleSubmit(onSubmit)(event);
      }}
      className="flex flex-col gap-5"
    >
      <PageHeader title={t("pages.new.title")} subtitle={t("list.subtitle")} />

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <CatSelect
            value={values.catId}
            options={catOptions}
            loading={catsQuery.isPending}
            errorKey={errors.catId?.message}
            onChange={(catId) => {
              setValue("catId", catId, { shouldValidate: true });
            }}
          />

          <FrequencyPicker
            scheduleMode={values.scheduleMode}
            intervalDays={values.intervalDays}
            rrule={values.rrule}
            errorKeyInterval={errors.intervalDays?.message}
            errorKeyRrule={errors.rrule?.message}
            onModeChange={(mode) => {
              setValue("scheduleMode", mode, { shouldValidate: true });
            }}
            onIntervalChange={(value) => {
              setValue("intervalDays", value, { shouldValidate: true });
            }}
            onRruleChange={(value) => {
              setValue("rrule", value, { shouldValidate: true });
            }}
          />

          <TimeWindowPicker
            start={values.preferredTimeStart}
            end={values.preferredTimeEnd}
            timezone={browserTimezone()}
            errorKey={errors.preferredTimeEnd?.message}
            onStartChange={(value) => {
              setValue("preferredTimeStart", value, { shouldValidate: true });
            }}
            onEndChange={(value) => {
              setValue("preferredTimeEnd", value, { shouldValidate: true });
            }}
          />

          <ChannelPicker
            value={values.channels}
            errorKey={errors.channels?.message}
            onChange={(channels) => {
              setValue("channels", channels, { shouldValidate: true });
            }}
          />

          {errorCode === REMINDER_ERROR.LIMIT_REACHED ? (
            <LimitReachedNotice existingReminderId={existingReminderId} />
          ) : errorCode === REMINDER_ERROR.SCHEDULE_INVALID ? (
            <ScheduleInvalidNotice />
          ) : errorCode === REMINDER_ERROR.CAT_NOT_FOUND ? (
            <GenericSaveError message={t("errors.catNotFound")} />
          ) : createReminder.isError ? (
            <GenericSaveError />
          ) : null}

          <Button type="submit" size="lg" loading={isSaving} disabled={catOptions.length === 0}>
            {t("form.submitCreate")}
          </Button>
        </div>

        {/* Cột phụ 360px ở desktop — cùng khuôn `SettingsSecurityPage`. */}
        <aside className="w-full lg:w-[360px] lg:shrink-0">
          <EducationCard withPhoto />
        </aside>
      </div>
    </form>
  );
}
