import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Apple, Check, Download, Info, Monitor, Smartphone } from "lucide-react";
import { Button } from "@/shared/ui";
import { isStandalonePwa } from "@/shared/lib/platform";


interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  readonly userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
}

type PromptOutcome = "accepted" | "dismissed";

function StepList({ steps }: { steps: string[] }) {
  return (
    <ol className="flex list-decimal flex-col gap-1.5 pl-5 text-caption leading-relaxed text-text-secondary">
      {steps.map((step) => (
        <li key={step}>{step}</li>
      ))}
    </ol>
  );
}

export function InstallPage() {
  const { t } = useTranslation(["settings", "common"]);
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [outcome, setOutcome] = useState<PromptOutcome | null>(null);
  const [standalone] = useState<boolean>(() => isStandalonePwa());

  useEffect(() => {
    const onBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      setDeferredPrompt(event as BeforeInstallPromptEvent);
    };
    const onAppInstalled = () => {
      setDeferredPrompt(null);
      setOutcome("accepted");
    };
    window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt);
    window.addEventListener("appinstalled", onAppInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt);
      window.removeEventListener("appinstalled", onAppInstalled);
    };
  }, []);

  const handleInstall = () => {
    const prompt = deferredPrompt;
    if (!prompt) return;
    void (async () => {
      await prompt.prompt();
      const choice = await prompt.userChoice;
      setOutcome(choice.outcome);
      setDeferredPrompt(null);
    })();
  };

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("common:pages.install.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("settings:install.lead")}</p>
      </header>

      <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <section className="flex flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
              <Download size={18} className="text-primary-dark" aria-hidden="true" />
              {deferredPrompt ? t("settings:install.promptTitle") : t("settings:install.promptUnavailableTitle")}
            </h2>
            <p className="text-caption leading-relaxed text-text-secondary">
              {deferredPrompt ? t("settings:install.promptBody") : t("settings:install.promptUnavailableBody")}
            </p>
            {deferredPrompt ? (
              <Button type="button" size="md" className="self-start" onClick={handleInstall}>
                {t("settings:install.promptCta")}
              </Button>
            ) : null}
            {outcome !== null ? (
              <p
                aria-live="polite"
                className="flex items-start gap-2 rounded-xl bg-background-alt px-3 py-2 text-caption text-text-secondary"
              >
                <Check size={15} className="mt-0.5 shrink-0 text-success-text" aria-hidden="true" />
                {outcome === "accepted" ? t("settings:install.promptAccepted") : t("settings:install.promptDismissed")}
              </p>
            ) : null}
          </section>

          <section className="flex flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="text-h3 font-bold text-text-primary">{t("settings:install.manualTitle")}</h2>

            <div className="flex flex-col gap-2">
              <h3 className="flex items-center gap-2 text-body font-semibold text-text-primary">
                <Apple size={16} className="text-primary-dark" aria-hidden="true" />
                {t("settings:install.iosTitle")}
              </h3>
              <StepList
                steps={[
                  t("settings:install.iosStep1"),
                  t("settings:install.iosStep2"),
                  t("settings:install.iosStep3"),
                  t("settings:install.iosStep4"),
                ]}
              />
            </div>

            <div className="flex flex-col gap-2">
              <h3 className="flex items-center gap-2 text-body font-semibold text-text-primary">
                <Smartphone size={16} className="text-primary-dark" aria-hidden="true" />
                {t("settings:install.androidTitle")}
              </h3>
              <StepList
                steps={[
                  t("settings:install.androidStep1"),
                  t("settings:install.androidStep2"),
                  t("settings:install.androidStep3"),
                ]}
              />
            </div>

            <div className="flex flex-col gap-2">
              <h3 className="flex items-center gap-2 text-body font-semibold text-text-primary">
                <Monitor size={16} className="text-primary-dark" aria-hidden="true" />
                {t("settings:install.desktopTitle")}
              </h3>
              <StepList steps={[t("settings:install.desktopStep1")]} />
            </div>
          </section>
        </div>

        <aside className="flex w-full flex-col gap-5 xl:w-[360px] xl:shrink-0">
          {standalone ? (
            <section className="flex flex-col gap-2 rounded-2xl bg-success-bg p-5">
              <h2 className="flex items-center gap-2 text-body font-bold text-success-text">
                <Check size={17} aria-hidden="true" />
                {t("settings:install.standaloneTitle")}
              </h2>
              <p className="text-caption leading-relaxed text-success-text">{t("settings:install.standaloneBody")}</p>
            </section>
          ) : null}

          <section className="flex flex-col gap-2 rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="flex items-center gap-2 text-body font-bold text-text-primary">
              <Info size={17} className="text-primary-dark" aria-hidden="true" />
              {t("settings:install.notesTitle")}
            </h2>
            <ul className="flex list-disc flex-col gap-1.5 pl-5 text-caption leading-relaxed text-text-secondary">
              <li>{t("settings:install.note1")}</li>
              <li>{t("settings:install.note2")}</li>
              <li>{t("settings:install.note3")}</li>
            </ul>
          </section>
        </aside>
      </div>
    </div>
  );
}
