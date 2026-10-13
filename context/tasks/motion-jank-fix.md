# Task: Sửa giật ("khựng") khi chuyển trang / thao tác + nâng cấp GSAP splash

Repo frontend: `frontend/` (React 19, TS, Tailwind v4, GSAP 3 + `@gsap/react`, zustand, react-router 8).
Làm theo thứ tự các bước. Mỗi bước có tiêu chí xong. KHÔNG tự sáng tạo thêm ngoài spec.

## 0. Ràng buộc bắt buộc

- KHÔNG chạy dev server, KHÔNG docker, KHÔNG commit.
- KHÔNG thêm dependency, KHÔNG thêm text hiển thị mới (eslint `i18next/no-literal-string` chặn chữ trong JSX).
- Không dùng màu hex mới; dùng token (`bg-surface`, `text-primary`, `var(--color-primary)`...).
- Ranh giới import (eslint boundaries): `pages` → `features|entities|shared`; `app` → mọi thứ; `entities` → `shared`; `shared` → chỉ `shared`.
- Lỗi tsc có sẵn KHÔNG thuộc task này: `src/pages/seller/SellerColorChartPage.tsx` (dòng 753, 926). Bỏ qua, không sửa.
- Kiểm tra cuối (chạy trong `frontend/`):
  ```
  npx tsc -b
  npx eslint . --max-warnings=0
  npx vitest run
  ```

## 1. Nguyên nhân giật (đã chẩn đoán)

1. Splash sau login chạy xong → `window.location.assign()` = **reload cả trang** → màn trắng, tải lại bundle + font → hiện lại logo. Cú reload chính là chỗ khựng.
2. `useListReveal(ref, trigger)` animate lại **toàn bộ** item mỗi khi `trigger` đổi (đếm số item, filter, id...) → thêm/xoá/lọc là cả list nháy từ opacity 0.
3. `usePageReveal` có MutationObserver 3s: node nào React remount trong 3s (do thao tác) bị reveal lại → nháy.
4. Mỗi route có `<Suspense>` riêng → mỗi lần chuyển trang hiện skeleton một khung hình rồi mới ra trang → nháy.
5. CSS `.motion-card { transform: translateZ(0) }` tạo layer GPU cho từng card → list dài nặng, cuộn giật. `.animate-pop-spring` / `.tab-content-enter` giữ `transform` sau khi chạy (`forwards`) → tranh chấp với transform của GSAP.

CSRF đọc từ cookie mỗi request (`src/features/auth/api.ts`), nên login KHÔNG cần reload.

## 2. Trạng thái hiện tại (đã làm dở, giữ nguyên)

Đã xong:
- `src/entities/user/session.ts`: `loadSession(): Promise<SessionUser | null>` (fetch `/api/v1/auth/session`), export ở `src/entities/user/index.ts`.
- `src/app/providers/SessionProvider.tsx`: dùng `loadSession`, gọi `useSplashStore.getState().boot()` khi bắt đầu, `.release()` khi xong, render `null` lúc loading.
- `src/shared/lib/motion/splash.ts`: zustand store `useSplashStore` (phase `hidden|boot|intro|exit`, `runId`, `pending`, actions `boot/release/play/exit/hide`) + helper `playSplash(task, reveal)`.

Chưa xong (spec dưới đây):
- `BrandSplash.tsx` vẫn là bản cũ (props `mode="intro"|"hold"`, dùng sessionStorage handoff + portal). Phải thay bằng `SplashHost`.
- Các trang vẫn dùng `<BrandSplash mode="intro">` + `window.location.assign`.

## 3. Bước 1: Thay `BrandSplash` bằng `SplashHost` (overlay toàn cục)

File: `src/shared/ui/BrandSplash/BrandSplash.tsx`, ghi đè TOÀN BỘ bằng nội dung sau:

```tsx
import { useRef } from "react";
import { useTranslation } from "react-i18next";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { useSplashStore, type SplashPhase } from "@/shared/lib/motion/splash";

gsap.registerPlugin(useGSAP);

const WORDMARK = Array.from("CATCHECK");
const CAT_LETTERS = 3;
const HANDOFF_TIMEOUT_MS = 8000;

function canAnimate() {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

export function SplashHost() {
  const phase = useSplashStore((s) => s.phase);
  const runId = useSplashStore((s) => s.runId);
  if (phase === "hidden") return null;
  return <SplashOverlay phase={phase} runId={runId} />;
}

function SplashOverlay({ phase, runId }: { phase: Exclude<SplashPhase, "hidden">; runId: number }) {
  const { t } = useTranslation("common");
  const rootRef = useRef<HTMLDivElement>(null);
  const breatheRef = useRef<gsap.core.Tween | null>(null);

  useGSAP(
    () => {
      const root = rootRef.current;
      if (!root) return;
      const q = gsap.utils.selector(root);
      const content = q("[data-splash='content']");
      const mark = q("[data-splash='mark']");
      const ring = q("[data-splash='ring']");
      const check = q("[data-splash='check']");
      const ears = q("[data-splash='ear']");
      const letters = q("[data-splash='letter']");
      const motion = canAnimate();
      const store = useSplashStore.getState();

      const stopBreathing = () => {
        breatheRef.current?.kill();
        breatheRef.current = null;
      };
      const showStatic = () => {
        gsap.set(content, { autoAlpha: 1, y: 0, scale: 1 });
        gsap.set(mark, { autoAlpha: 1, scale: 1, scaleX: 1, scaleY: 1, rotation: 0 });
        gsap.set([ring, check], { strokeDashoffset: 0 });
        gsap.set(ears, { scale: 1, transformOrigin: "50% 100%" });
        gsap.set(letters, { autoAlpha: 1, y: 0, rotation: 0 });
      };
      const squash = {
        keyframes: [
          { scaleX: 1.08, scaleY: 0.93, duration: 0.12, ease: "power1.out" },
          { scaleX: 1, scaleY: 1, duration: 0.5, ease: "elastic.out(1, 0.45)" },
        ],
      };

      if (phase === "boot") {
        showStatic();
        if (!motion) {
          gsap.set(root, { autoAlpha: 1 });
          return;
        }
        // Delay so a fast session check never flashes the logo.
        gsap.fromTo(root, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.2, delay: 0.15, ease: "power1.out" });
        breatheRef.current = gsap.to(mark, {
          scale: 1.035,
          duration: 1,
          delay: 0.35,
          ease: "sine.inOut",
          yoyo: true,
          repeat: -1,
        });
        return stopBreathing;
      }

      if (phase === "exit") {
        stopBreathing();
        if (!motion || Number(gsap.getProperty(root, "opacity")) < 0.05) {
          store.hide();
          return;
        }
        const out = gsap
          .timeline({
            onComplete: () => {
              useSplashStore.getState().hide();
            },
          })
          .to(content, { y: -12, scale: 0.96, autoAlpha: 0, duration: 0.24, ease: "power2.in" })
          .to(root, { autoAlpha: 0, duration: 0.32, ease: "power2.out" }, "-=0.1");
        return () => {
          out.kill();
        };
      }

      stopBreathing();
      let cancelled = false;
      const handOff = () => {
        const pending = useSplashStore.getState().pending ?? Promise.resolve(() => undefined);
        const timeout = new Promise<() => void>((resolve) => {
          window.setTimeout(() => {
            resolve(() => undefined);
          }, HANDOFF_TIMEOUT_MS);
        });
        void Promise.race([pending, timeout]).then((reveal) => {
          if (cancelled || useSplashStore.getState().runId !== runId) return;
          reveal();
          // Let the next route commit under the overlay before fading out.
          requestAnimationFrame(() => {
            requestAnimationFrame(() => {
              if (!cancelled) useSplashStore.getState().exit();
            });
          });
        });
      };

      if (!motion) {
        showStatic();
        gsap.set(root, { autoAlpha: 1 });
        handOff();
        return () => {
          cancelled = true;
        };
      }

      const fromVisible = Number(gsap.getProperty(root, "opacity")) > 0.5;
      const tl = gsap.timeline({ defaults: { ease: "power3.out" }, onComplete: handOff });

      if (fromVisible) {
        showStatic();
        tl.fromTo(check, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.3, ease: "power2.out" })
          .to(mark, squash, ">-0.05")
          .to(
            letters,
            {
              keyframes: [
                { y: -8, duration: 0.16, ease: "power2.out" },
                { y: 0, duration: 0.34, ease: "bounce.out" },
              ],
              stagger: 0.035,
            },
            "<",
          );
      } else {
        gsap.set(content, { autoAlpha: 1, y: 0, scale: 1 });
        tl.fromTo(root, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.28, ease: "power1.out" })
          .fromTo(
            mark,
            { autoAlpha: 0, scale: 0.6, rotation: -12 },
            { autoAlpha: 1, scale: 1, rotation: 0, duration: 0.6, ease: "back.out(1.7)" },
            0.1,
          )
          .fromTo(ring, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.55, ease: "power2.inOut" }, 0.1)
          .fromTo(
            ears,
            { scale: 0, transformOrigin: "50% 100%" },
            { scale: 1, duration: 0.4, ease: "back.out(2.6)", stagger: 0.06 },
            0.38,
          )
          .fromTo(check, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.3, ease: "power2.out" }, 0.55)
          .fromTo(
            letters,
            { autoAlpha: 0, y: 20, rotation: 8 },
            { autoAlpha: 1, y: 0, rotation: 0, duration: 0.45, ease: "back.out(2.2)", stagger: 0.04 },
            0.62,
          )
          .to(mark, squash, 0.85)
          .to({}, { duration: 0.08 });
      }

      return () => {
        cancelled = true;
        tl.kill();
      };
    },
    { scope: rootRef, dependencies: [phase, runId] },
  );

  return (
    <div
      ref={rootRef}
      role="status"
      aria-live="polite"
      className="invisible fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-surface opacity-0"
    >
      <span className="sr-only">{t("actions.loading")}</span>
      <div data-splash="content" className="flex flex-col items-center gap-5" aria-hidden="true">
        <svg data-splash="mark" viewBox="0 0 120 120" className="size-24 will-change-transform md:size-28" fill="none">
          <path data-splash="ear" d="M28 46 L32 12 L58 30 Z" fill="var(--color-primary)" stroke="var(--color-primary)" strokeWidth="6" strokeLinejoin="round" />
          <path data-splash="ear" d="M92 46 L88 12 L62 30 Z" fill="var(--color-primary)" stroke="var(--color-primary)" strokeWidth="6" strokeLinejoin="round" />
          <path data-splash="ear" d="M35 36 L37 21 L49 30 Z" fill="var(--color-secondary)" />
          <path data-splash="ear" d="M85 36 L83 21 L71 30 Z" fill="var(--color-secondary)" />
          <path data-splash="ring" d="M41 98.9 A38 38 0 1 1 79 98.9" pathLength={1} strokeDasharray="1" stroke="var(--color-primary)" strokeWidth="10" strokeLinecap="round" />
          <path data-splash="check" d="M44 66 L56 78 L78 54" pathLength={1} strokeDasharray="1" stroke="var(--color-secondary)" strokeWidth="11" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <div className="flex text-[1.75rem] font-extrabold leading-none tracking-[0.04em] md:text-[2rem]">
          {WORDMARK.map((char, index) => (
            <span
              key={String(index)}
              data-splash="letter"
              className={`inline-block will-change-transform ${index < CAT_LETTERS ? "text-primary" : "text-secondary"}`}
            >
              {char}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
```

Sau đó:
- `src/shared/ui/BrandSplash/index.ts` → chỉ còn `export { SplashHost } from "./BrandSplash";`
- `src/shared/ui/index.ts` → thay 2 dòng export `BrandSplash`/`BrandSplashProps` bằng `export { SplashHost } from "./BrandSplash";`
- Chạy `npx prettier --write src/shared/ui/BrandSplash/BrandSplash.tsx` (các thẻ `<path>` dài sẽ được xuống dòng).

## 4. Bước 2: Mount `SplashHost` trong AppProviders

File `src/app/providers/AppProviders.tsx`: đặt `<SplashHost />` **bên trong `I18nProvider`, ngang hàng và trước `QueryProvider`**, để overlay không bị unmount khi đổi route hay đổi session:

```tsx
import { SplashHost } from "@/shared/ui";
...
<I18nProvider>
  <SplashHost />
  <QueryProvider>
    <SessionProvider>
      <ToastProvider>{children}</ToastProvider>
    </SessionProvider>
  </QueryProvider>
</I18nProvider>
```

## 5. Bước 3: Chuyển `resolveRedirectTarget` sang shared + guard tôn trọng `?next=`

Lý do: sau login, ta chỉ `setSession(user)`; guard `RedirectIfAuthenticated` sẽ tự điều hướng. Guard phải ra đúng đích như LoginPage.

1. Chuyển hàm `resolveRedirectTarget` từ `src/pages/auth/loginRedirect.ts` vào cuối `src/shared/auth/landing.ts` (giữ nguyên thân hàm; bỏ dòng import vì đã cùng file).
2. Xoá `src/pages/auth/loginRedirect.ts`. Chuyển `src/pages/auth/loginRedirect.test.ts` thành `src/shared/auth/landing.test.ts`, đổi import thành `import { resolveRedirectTarget } from "./landing";`.
3. Cập nhật mọi import cũ (`grep -rn "loginRedirect" src`) sang `@/shared/auth/landing`.
4. `src/app/router/guards/RedirectIfAuthenticated.tsx`:

```tsx
import { Navigate, Outlet, useSearchParams } from "react-router";
import { useSessionStore } from "@/entities/user";
import { resolveRedirectTarget } from "@/shared/auth/landing";

export function RedirectIfAuthenticated() {
  const status = useSessionStore((state) => state.status);
  const roles = useSessionStore((state) => state.user?.roles);
  const [searchParams] = useSearchParams();

  if (status === "authenticated") {
    return <Navigate to={resolveRedirectTarget(searchParams.get("next"), roles ?? [])} replace />;
  }
  return <Outlet />;
}
```
Lưu ý: `resolveRedirectTarget(next, [])` trả `"/"` khi roles rỗng. User đã đăng nhập luôn có role nên OK.

## 6. Bước 4: Các điểm gọi splash (bỏ hết `window.location.assign` sau auth)

Mẫu chung: `playSplash(task, reveal)`. Overlay chạy intro; khi intro xong VÀ `task` resolve thì gọi `reveal(value)` (đổi UI bên dưới), sau đó overlay fade-out lộ trang mới.

### 4a. `src/pages/auth/LoginPage.tsx`
- Xoá state `splashTarget`, biến `splash`, cả 2 chỗ render `{splash}`, và import `BrandSplash`.
- Thêm import: `import { loadSession, useSessionStore } from "@/entities/user";`, `import { useQueryClient } from "@tanstack/react-query";`, `import { playSplash } from "@/shared/lib/motion/splash";`.
- Trong component:
```tsx
const queryClient = useQueryClient();
const setSession = useSessionStore((s) => s.setSession);

const redirectAfterLogin = () => {
  playSplash(loadSession(), (user) => {
    queryClient.clear();
    setSession(user);
  });
};
```
- Đổi 2 chỗ gọi: `redirectAfterLogin(result.roles)` → `redirectAfterLogin()`. Ở nhánh MFA: xoá dòng `const session = await apiFetch<SessionBootstrapResponse>(...)` và gọi `redirectAfterLogin()`. Xoá import thừa (`apiFetch`, `SessionBootstrapResponse`, `resolveRedirectTarget`) nếu không còn dùng.
- Nếu `user` trả về `null` (hiếm): `setSession(null)` → user ở lại trang login. Chấp nhận được.

### 4b. `src/pages/auth/OAuthCompletePage.tsx`
Trang này được load lại toàn trang từ backend; SessionProvider đã nạp session nên đọc store, không fetch lần nữa. Ghi đè:

```tsx
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router";
import { useSessionStore } from "@/entities/user";
import { resolveRedirectTarget } from "@/shared/auth/landing";
import { playSplash } from "@/shared/lib/motion/splash";
import { Button, EmptyState } from "@/shared/ui";

export function OAuthCompletePage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const user = useSessionStore((s) => s.user);
  const failed = searchParams.has("error") || !user;
  const started = useRef(false);

  useEffect(() => {
    if (failed || started.current) return;
    started.current = true;
    playSplash(Promise.resolve(user.roles), (roles) => {
      void navigate(resolveRedirectTarget(null, roles), { replace: true });
    });
  }, [failed, user, navigate]);

  if (!failed) return null;
  return (
    <EmptyState
      title={t("oauthComplete.failed.title")}
      description={t("oauthComplete.failed.description")}
      action={
        <Button type="button" onClick={() => { void navigate("/auth/login"); }}>
          {t("oauthComplete.failed.cta")}
        </Button>
      }
    />
  );
}
```
(TS có thể báo `user` có thể null bên trong effect; nếu vậy thêm `if (failed || !user || started.current) return;`.)

Route `/auth/oauth/complete` đã nằm NGOÀI `RedirectIfAuthenticated` (đúng rồi, giữ nguyên).

### 4c. `src/pages/auth/VerifyOtpPage.tsx` (đăng ký thành công)
- Xoá state `registered`, khối `if (registered) { return <BrandSplash .../> }`, import `BrandSplash`.
- Thêm import `loadSession`, `useSessionStore` từ `@/entities/user`, `playSplash` từ `@/shared/lib/motion/splash`.
- `const setSession = useSessionStore((s) => s.setSession);` (đặt cùng nhóm hook ở đầu, TRƯỚC mọi `return`).
- Sau khi `activateRegistration.mutateAsync(...)` thành công, thay `setRegistered(true);` bằng:
```tsx
playSplash(loadSession(), (user) => {
  reset();
  setSession(user);
});
```
Guard sẽ đưa user mới (onboarding `ACCOUNT_ONLY`) về landing, rồi `RequireOnboarding` chuyển sang `/onboarding/cat`.

### 4d. `src/pages/onboarding/OnboardingSuccessPage.tsx`
- Xoá `useState` import (nếu không còn dùng), state `leaving`, biến `splash`, 2 chỗ `{splash}` (cả fragment `<>...</>` bọc footer: trả về `<Button>` như cũ), import `BrandSplash`.
- Import `playSplash`. Hai nút "Go to dashboard": `onClick={() => { playSplash(Promise.resolve(), () => { void navigate("/dashboard"); }); }}`.

Xong bước 4 khi: `grep -rn "BrandSplash\|location.assign" src/pages` chỉ còn `oauth2/authorization/google` (trong features) và không còn `BrandSplash`.

## 7. Bước 5: Sửa reveal gây nháy (`src/shared/lib/motion/usePageReveal.ts`)

### 5a. Default toàn cục
Ngay sau `gsap.registerPlugin(useGSAP);` thêm:
```ts
gsap.defaults({ overwrite: "auto" });
```
(tránh 2 tween cùng target giành nhau → giật.)

### 5b. `usePageReveal`: nhanh hơn + chỉ reveal trễ MỘT lần
Trong nhánh `(prefers-reduced-motion: no-preference)`:
- Hàm `reveal` đổi tham số tween thành: `{ autoAlpha: 0, y: 10 }` → `{ autoAlpha: 1, y: 0, duration: 0.34, ease: "power3.out", stagger: { each: 0.04, amount: 0.2 }, clearProps: "transform,opacity,visibility" }`. Nếu GSAP báo lỗi khi có cả `each` và `amount` thì chỉ dùng `stagger: 0.035`.
- MutationObserver: sau lần reveal trễ ĐẦU TIÊN (khi `late.length > 0`) thì `observer.disconnect()` luôn. Timeout giữ 3000ms làm chốt chặn. Nghĩa là chỉ bắt đúng lần "skeleton → nội dung thật"; remount do thao tác về sau không bị reveal lại.

### 5c. `useListReveal`: chỉ animate item MỚI
Thay thân callback bằng:
```ts
const root = scope.current;
if (!root || prefersReducedMotion()) return;
const fresh = gsap.utils
  .toArray<HTMLElement>("[data-reveal-item]:not([data-revealed])", root)
  .slice(0, 16);
if (fresh.length === 0) return;
for (const el of fresh) el.setAttribute("data-revealed", "");
gsap.fromTo(
  fresh,
  { autoAlpha: 0, y: 8 },
  { autoAlpha: 1, y: 0, duration: 0.32, ease: "power3.out", stagger: 0.03, clearProps: "transform,opacity,visibility" },
);
```
- KHÔNG còn cleanup `gsap.set(items, clearProps)` và KHÔNG `tween.kill()` khi trigger đổi (để tween đang chạy chạy hết, khỏi nháy giữa chừng). Hàm trả về `undefined`.
- Hệ quả: item giữ nguyên node thì không nháy lại; item mới (thêm vào, hoặc remount do đổi filter có `key` mới) thì được stagger.
- Giữ nguyên chữ ký `useListReveal(scope, trigger)`, không cần sửa ~60 chỗ gọi.

## 8. Bước 6: Không nháy skeleton khi chuyển trang (`src/app/router/router.tsx`)

Thêm component fallback có trễ, dùng cho `PageSuspense`:
```tsx
function DelayedFallback() {
  const [show, setShow] = useState(false);
  useEffect(() => {
    const id = window.setTimeout(() => { setShow(true); }, 220);
    return () => { window.clearTimeout(id); };
  }, []);
  return show ? <SkeletonLoader shape="card" className="m-4" /> : null;
}
```
- Import `useEffect, useState` từ react.
- Trong `PageSuspense`: `fallback={<DelayedFallback />}`.
- Chunk tải dưới 220ms thì không thấy skeleton nữa.

## 9. Bước 7: CSS (`src/app/styles/index.css`)

1. `.motion-card`: XOÁ dòng `transform: translateZ(0);` (giữ transition box-shadow).
2. `@keyframes popSpring`: frame `100%` đổi `transform: scale(1);` → `transform: none;`.
3. `@keyframes tabContentEnter`: `to { transform: translateY(0); }` → `to { opacity: 1; transform: none; }`.
4. `@keyframes sidebarCardEnter`, `navItemSlideIn`: tương tự, frame cuối đổi `transform: ...0` → `transform: none`.
Không đổi gì khác.

## 10. Test

- Chạy `npx vitest run`. Test có thể gãy:
  - Test nào mock `window.location.assign` sau login/verify OTP: sửa để assert `useSplashStore.getState().phase === "intro"` (import từ `@/shared/lib/motion/splash`), hoặc assert `setSession` được gọi sau khi `await` xong pending. Trong jsdom `matchMedia` không có → `canAnimate()` = false → luồng chạy ngay: `handOff` → `reveal` → `exit` → `hide`. Muốn test cũng có thể gọi trực tiếp `await useSplashStore.getState().pending` rồi gọi hàm trả về.
  - Test SessionProvider/router nếu kỳ vọng `SkeletonLoader` lúc loading: giờ loading render `null`.
- Thêm 1 test nhỏ `src/shared/lib/motion/splash.test.ts`:
  - `boot()` từ hidden → `"boot"`; `release()` → `"exit"`; `hide()` → `"hidden"`.
  - `play(Promise.resolve(5), spy)` → phase `"intro"`, `runId` tăng; `await (await store.pending!)()` thì `spy` được gọi với `5`.
  - `play(Promise.reject(new Error()), spy)` → gọi callback trả về không throw, `spy` không được gọi.
  - Reset store giữa các test: `useSplashStore.setState({ phase: "hidden", runId: 0, pending: null })`.

## 11. Tiêu chí hoàn thành (kiểm bằng mắt, do owner tự chạy app)

- Login (email/MFA/Google), đăng ký xong, onboarding xong: overlay trắng hiện dần, logo nảy, chữ CATCHECK pop từng chữ, rồi logo nhấc lên và mờ đi lộ dashboard. KHÔNG có màn trắng reload, KHÔNG thấy logo hiện 2 lần.
- F5 trang bất kỳ khi đã login: session load nhanh thì không thấy logo; chậm (>150ms) thì logo hiện tĩnh và "thở" nhẹ, rồi mờ dần.
- Thêm/xoá/lọc trong list (reminders, cats, notifications, admin tables): chỉ item mới animate, item cũ đứng yên.
- Chuyển trang nhanh: không nháy skeleton.
- Bật "reduce motion" trong OS: không animation, luồng login vẫn vào dashboard.
- `tsc -b` (trừ lỗi có sẵn ở SellerColorChartPage), `eslint . --max-warnings=0`, `vitest run` đều sạch.
