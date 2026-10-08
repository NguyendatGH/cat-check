import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { Button } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import {
  CatFormFields,
  catFormSchema,
  useBreeds,
  useCreateCat,
  useUploadCatAvatar,
  type CatFormSchemaValues,
  type CatMutationPayload,
} from "@/features/cat";

/** `/cats/new` — W1 9 + M2 06 (p9 route #28). Tạo hồ sơ mèo (D2, p8 §8.4.4). */
export function CatNewPage() {
  const { t } = useTranslation(["cat", "common"]);
  const navigate = useNavigate();
  const { data: breeds, isLoading: breedsLoading } = useBreeds();
  const createCat = useCreateCat();
  const uploadAvatar = useUploadCatAvatar();

  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CatFormSchemaValues>({
    resolver: zodResolver(catFormSchema),
    defaultValues: {
      name: "",
      breedCode: "",
      breedOther: "",
      coatColor: "",
      sex: "",
      neutered: false,
      ageMode: "birthDate",
      birthDate: "",
      approxAgeMonths: "",
      weightKg: "",
      notes: "",
    },
  });

  useEffect(() => {
    if (avatarFile) {
      const url = URL.createObjectURL(avatarFile);
      setAvatarPreview(url);
      return () => {
        URL.revokeObjectURL(url);
      };
    }
    setAvatarPreview(null);
  }, [avatarFile]);

  const isSaving = isSubmitting || createCat.isPending || uploadAvatar.isPending;

  const onSubmit = async (values: CatFormSchemaValues) => {
    setSubmitError(null);
    const payload: CatMutationPayload = {
      name: values.name,
      breedCode: values.breedCode || null,
      breedOther: values.breedOther || null,
      coatColor: values.coatColor || null,
      sex: values.sex as CatMutationPayload["sex"],
      neutered: values.neutered,
      birthDate: values.ageMode === "birthDate" ? values.birthDate || null : null,
      approxAgeMonths: values.ageMode === "approx" && values.approxAgeMonths ? Number(values.approxAgeMonths) : null,
      weightKg: values.weightKg ? Number(values.weightKg) : null,
      notes: values.notes || null,
    };

    try {
      const cat = await createCat.mutateAsync(payload);
      if (avatarFile) {
        try {
          await uploadAvatar.mutateAsync({ catId: cat.id, file: avatarFile });
        } catch {
          // Hồ sơ đã tạo thành công — lỗi upload ảnh không nên chặn điều hướng, chỉ báo toast.
          toast.error(t("errors.generic"));
        }
      }
      toast.success(t("form.createSuccess", { name: cat.name }));
      void navigate(`/cats/${cat.id}`);
    } catch (error) {
      if (isApiError(error) && error.code === "CAT_PROFILE_LIMIT_REACHED") {
        setSubmitError(t("errors.limitReached", { max: 8 }));
      } else {
        setSubmitError(t("errors.generic"));
      }
    }
  };

  return (
    // Desktop: form nằm trong một thẻ trắng (như các màn TaskLayout khác) thay vì trôi trên nền.
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 lg:rounded-3xl lg:bg-surface lg:p-8 lg:shadow-xs">
      <h1 className="text-h2 font-bold text-text-primary">{t("pages.new.title")}</h1>

      <CatFormFields
        register={register}
        watch={watch}
        setValue={setValue}
        errors={errors}
        breeds={breeds?.items}
        breedsLoading={breedsLoading}
        avatarPreviewUrl={avatarPreview}
        onAvatarChange={setAvatarFile}
      />

      {submitError ? (
        <div role="alert" className="rounded-lg border border-danger bg-danger-bg p-3">
          <p className="text-small font-medium text-danger-text">{submitError}</p>
        </div>
      ) : null}

      <div className="flex flex-col gap-3 sm:flex-row-reverse">
        <Button
          type="button"
          size="lg"
          loading={isSaving}
          disabled={isSaving}
          className="sm:min-w-40"
          onClick={() => {
            void handleSubmit(onSubmit)();
          }}
        >
          {isSaving ? t("form.saving") : t("form.submit")}
        </Button>
        <Button
          type="button"
          variant="tertiary"
          size="lg"
          disabled={isSaving}
          onClick={() => {
            void navigate(-1);
          }}
        >
          {t("actions.cancel", { ns: "common" })}
        </Button>
      </div>
    </div>
  );
}
