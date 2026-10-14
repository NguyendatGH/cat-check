import { use, type ComponentType } from "react";

type Loader = () => Promise<{ default: ComponentType }>;

export interface PreloadablePage {
  (): React.JSX.Element;
  preload: () => Promise<unknown>;
}

// Unlike React.lazy, a page that was preloaded renders synchronously on first mount,
// so route changes never flash the Suspense fallback.
export function lazyPage(loader: Loader): PreloadablePage {
  let Loaded: ComponentType | null = null;
  let pending: Promise<ComponentType> | null = null;

  const preload = () => {
    pending ??= loader().then(
      (module) => {
        Loaded = module.default;
        return module.default;
      },
      (error: unknown) => {
        pending = null;
        throw error;
      },
    );
    return pending;
  };

  function LazyPage() {
    const Component = Loaded ?? use(preload());
    return <Component />;
  }

  return Object.assign(LazyPage, { preload });
}

type Idle = (cb: () => void) => void;
const whenIdle: Idle = (cb) => {
  if (typeof window.requestIdleCallback === "function") window.requestIdleCallback(cb, { timeout: 2000 });
  else window.setTimeout(cb, 200);
};

export function preloadInIdle(pages: { preload: () => Promise<unknown> }[], batch = 3) {
  let index = 0;
  const next = () => {
    const slice = pages.slice(index, index + batch);
    index += batch;
    if (slice.length === 0) return;
    void Promise.allSettled(slice.map((page) => page.preload())).then(() => {
      whenIdle(next);
    });
  };
  whenIdle(next);
}
