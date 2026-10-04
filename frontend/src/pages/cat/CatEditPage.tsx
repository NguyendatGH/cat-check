import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import {
  CatFormFields,
  catFormSchema,
  useBreeds,
  useCat,
  usePatchCat,
  useRemoveCatAvatar,
  useUploadCatAvatar,
  type CatFormSchemaValues,
  type CatMutationPayload,
} from "@/features/cat";

/** `/cats/:catId/edit` — M2 06 menu ⋮ (p9 route #30). Sửa hồ sơ mèo, merge-patch (D4). */
export function CatEditPage() {
  const { t } = useTranslation(["cat", "common"]);
  const navigate = useNavigate();
  const { catId } = useParams<{ catId: string }>();

  const { data: cat, isPending, isError, refetch } = useCat(catId);
  const { data: breeds, isLoading: breedsLoading } = useBreeds();
  const patchCat = usePatchCat(catId ?? "");
  const uploadAvatar = useUploadCatAvatar();
  const removeAvatar = useRemoveCatAvatar();

  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [avatarRemoved, setAvatarRemoved] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
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
    if (!cat) return;
    reset({
      name: cat.name,
      breedCode: cat.breedCode ?? "",
      breedOther: cat.breedOther ?? "",
      coatColor: cat.coatColor ?? "",
      sex: cat.sex,
      neutered: cat.neutered ?? false,
      ageMode: cat.approxAgeMonths !== null && cat.birthDate === null ? "approx" : "birthDate",
      birthDate: cat.birthDate ?? "",
      approxAgeMonths: cat.approxAgeMonths !== null ? String(cat.approxAgeMonths) : "",
      weightKg: cat.weightKg !== null ? String(cat.weightKg) : "",
      notes: cat.notes ?? "",
    });
  }, [cat, reset]);

  useEffect(() => {
    if (avatarFile) {
      const url = URL.createObjectURL(avatarFile);
      setAvatarPreview(url);
      return () => {
        URL.revokeObjectURL(url);
      };
    }
    setAvatarPreview(avatarRemoved ? null : (cat?.avatarUrl ?? null));
  }, [avatarFile, avatarRemoved, cat?.avatarUrl]);

  if (!catId) return null;

  if (isPending) {
    return (
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-4">
        <SkeletonLoader shape="circle" className="mx-auto size-28" />
        <SkeletonLoader shape="text" />
        <SkeletonLoader shape="text" />
        <SkeletonLoader shape="card" />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("detail.errorTitle")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  const isSaving = isSubmitting || patchCat.isPending || uploadAvatar.isPending || removeAvatar.isPending;

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
      const updated = await patchCat.mutateAsync(payload);
      if (avatarFile) {
        await uploadAvatar.mutateAsync({ catId, file: avatarFile });
      } else if (avatarRemoved && cat.avatarUrl) {
        await removeAvatar.mutateAsync({ catId });
      }
      toast.success(t("form.updateSuccess", { name: updated.name }));
      void navigate(`/cats/${catId}`);
    } catch (error) {
      if (isApiError(error) && error.code === "CAT_BREED_UNKNOWN") {
        setSubmitError(t("form.breed.required"));
      } else {
        setSubmitError(t("errors.generic"));
      }
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <h1 className="text-h2 font-bold text-text-primary">{t("pages.edit.title")}</h1>

      <CatFormFields
        register={register}
        watch={watch}
        setValue={setValue}
        errors={errors}
        breeds={breeds?.items}
        breedsLoading={breedsLoading}
        avatarPreviewUrl={avatarPreview}
        onAvatarChange={(file) => {
          setAvatarFile(file);
          if (!file) setAvatarRemoved(true);
        }}
        hasExistingAvatar={Boolean(cat.avatarUrl)}
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
