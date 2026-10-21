# CatCheck — product and motion graphic reference

Source snapshot: `feat/i18n-vi-en` at `de418d1` (18 October 2026). This is a production brief derived from the current code and assets, not a claim that a finished film or design file exists. The repository guide refers to `context/HANDOFF.md` and `context/spec/04-index.md`, but those files are absent on this branch; the linked source files below are the evidence for this brief.

## The product in one paragraph

CatCheck is a web and installable mobile web app for cat owners using color indicator litter. A person photographs the color changed granules, selects the cat (or a shared tray), and the app estimates a urine pH reference band from the photo. It stores scans so owners can review changes over time, set reminders, keep cat profiles, and find veterinary care. The app also includes a litter shop, package activation codes that grant scan credits, a community, and a cited care assistant. The core story is **observe → scan → understand a reference reading → follow the trend → seek a vet when concerned**. It is an at home observation tool, not a diagnosis or replacement for a veterinarian. [Project overview](../README.md), [product tour copy](../frontend/src/shared/i18n/locales/en/tour.json), [scan copy](../frontend/src/shared/i18n/locales/en/scan.json).

## Brand identity

### Logo and marks

| Asset | What it contains | Use in a film |
| --- | --- | --- |
| [Active app logo](../frontend/src/shared/assets/images/web-auth/logo.png) | Transparent 612 × 408 PNG: blue cat head and ears, yellow check, blue `CAT` / yellow `CHECK`, small line reading “HEALTHIER CATS [shield] HAPPIER LIVES.” Used by the welcome, login, desktop, and mobile headers. | Main end card and accurate brand reference. Keep its proportions and transparent edge. |
| [Separated splash layers](../frontend/src/shared/assets/images/splash/head.png) | Head, ears, check, eight wordmark letters, and decorative layers in the same directory. | Best source for animating the logo parts independently. [Splash composition and timing](../frontend/src/shared/ui/BrandSplash/BrandSplash.tsx). |
| [Dashboard logo lockup](../frontend/src/shared/assets/images/web-dashboard/logo-lockup.png) | Wider 866 × 288 PNG with a different line: “CHECK IN - CLEAN UP - LUXURY CARE.” | Treat as an alternate/older artwork: no current source import uses it. Do not combine its tagline with the active logo. |
| [PWA icons](../frontend/public/icons/pwa-512x512.png) | App icon artwork. The [SVG favicon](../frontend/public/icons/favicon.svg) is only a blue square with a white circle. | Use the active logo for a brand reveal; use the app icon only when depicting installation. |

The visual mark communicates **cat + check/confirmation**. The wordmark is custom artwork, so keep it as an image or its supplied layers rather than recreating it in a font. The welcome page also displays the phrase “check with care, always there”; it is UI copy and differs from the text baked into the active logo. [Welcome page](../frontend/src/pages/public/HomePage.tsx), [English copy](../frontend/src/shared/i18n/locales/en/common.json).

### Color specification

Values below are the actual UI tokens in [index.css](../frontend/src/app/styles/index.css). Use the first five for most frames; reserve status colors for results. The pH rainbow is a separate chemistry scale, not a severity palette.

| Role | Hex | Suggested film use |
| --- | --- | --- |
| Primary blue | `#2F4FB2` | Brand fields, scan CTA, cat outline. |
| Dark primary blue | `#0D369A` | Active navigation, charts, strong text on light cards. |
| Yellow | `#FDCF52` | Check mark, key highlight, scan action, celebratory accent. |
| Light yellow | `#FFDF93` | Soft highlight behind yellow objects. |
| Near white background | `#FAF8FF` | Main scene background. |
| White surface | `#FFFFFF` | UI cards and phone panels. |
| Lavender surface | `#F3F2FF` | Secondary panels and depth layers. |
| Lavender chip | `#E4E7FF` | Pills and supporting callouts. |
| Primary text | `#161B2C` | Headlines and important numbers. |
| Secondary text | `#444652` | Supporting copy. |
| Normal state | `#22C55E` | Normal status, only with a label. |
| Attention state | `#F59E0B` | Slightly outside reference range, only with a label. |
| Watch state | `#BA1A1A` | Clearly outside reference range, only with a label. |

The app's pH dial uses universal indicator hues from red at pH 3 (`#E4352B`) through green at pH 7 (`#3FAE4A`) to violet at pH 11 (`#7A3FA8`). Those hues explain the *scale*; the blue/yellow brand still frames the UI. The measured granule swatch can vary per scan. See [pH color variables](../frontend/src/app/styles/index.css) and [result scale](../frontend/src/features/scan/components.tsx). The seeded reference range is 6.3–6.6, but bands are served by the backend and may be configured, so a film should show a plausible example rather than promise a universal threshold. [Seeded bands](../backend/src/main/resources/db/migration/V9__colorchart.sql), [band token mapping](../frontend/src/entities/ph-bands/colorToken.ts).

### Type, shape, and texture

- **Type:** Nunito Regular 400, Semibold 600, Bold 700. Local Latin and Vietnamese WOFF2 files live in [public/fonts](../frontend/public/fonts); [font faces](../frontend/src/app/styles/fonts.css). Buyer UI falls back to Be Vietnam Pro/system sans; admin UI uses a denser system font.
- **Hierarchy:** page title 28/36 desktop and 24/32 mobile; section title 20/28; body 16/24; caption 14/20; small 12/16. Use bold, round headlines and restrained body text. [Type tokens](../frontend/src/app/styles/index.css).
- **Geometry:** cards and buttons are predominantly rounded; canonical token radii are 6 px input/table, 10 px panel, 12 px focal card, while many live consumer cards use 16–24 px. Shadows are subtle blue tinted. Spacing is built from 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 px steps. [Style tokens](../frontend/src/app/styles/index.css).
- **Visual language:** clean white cards on a pale lavender background; blue controls; yellow punctuations; cat portraits, paw icons, scan frame corners, colored litter granules, pH gauges, trend lines, and small badges. Avoid presenting medical equipment or laboratory certainty as the app's promise.

## Layout and screen grammar

| Scene | Real app layout and information | Source |
| --- | --- | --- |
| Welcome | Mobile is a narrow stacked layout: small trust pill, logo and slogans, cat photo in tilted soft lavender/yellow frames, three feature tiles, primary/secondary CTAs. Desktop becomes a two column white card with copy left and cat image right. | [HomePage](../frontend/src/pages/public/HomePage.tsx) |
| Main app shell | Desktop at `lg` (1024 px) has a 288 px white left sidebar, logo, cat switcher, navigation, and yellow credits card; page content has a top header and roomy cards. Mobile has a 64 px sticky logo header, collapsible shortcut chips, and a 72 px five tab bottom bar with the raised blue Scan action in the middle. | [AppLayout](../frontend/src/app/layouts/AppLayout.tsx), [DesktopAside](../frontend/src/app/layouts/shell/DesktopAside.tsx), [MobileChrome](../frontend/src/app/layouts/shell/MobileChrome.tsx) |
| Dashboard | Greets the user, lets them pick a cat, exposes a strong scan CTA, cat cards, latest scan/pH gauge, trend chart, recent scans, reminders and care tips. Desktop content uses a 12 column grid: main information left, supporting cards right. Mobile stacks and horizontally scrolls cat cards. | [DashboardPage](../frontend/src/pages/dashboard/DashboardPage.tsx), [DashboardPhCard](../frontend/src/pages/dashboard/DashboardPhCard.tsx) |
| Scan | Choose the cat or shared tray, photograph or upload a close view of changed granules, then analyze. Camera framing includes a moving yellow scan line. Good light, sharp focus, and changed litter area are central visual cues. | [ScanPage](../frontend/src/pages/scan/ScanPage.tsx), [scan components](../frontend/src/features/scan/components.tsx) |
| Result | pH estimate and reference band appear with the captured image, circular gauge and horizontal spectrum. Quality can be inconclusive; the result may suggest retaking the photo. | [ScanResultPage](../frontend/src/pages/scan/ScanResultPage.tsx), [result components](../frontend/src/features/scan/components.tsx) |
| History and trends | Per cat scan timeline and pH chart show change across days. Recent trend is more important than a single colored number. | [History](../frontend/src/pages/history/CatHistoryPage.tsx), [Trends](../frontend/src/pages/trends/CatTrendsPage.tsx) |
| Wider ecosystem | Profiles, reminders, vet map, shop, community, credits, assistant, and notifications appear in navigation and the guided product tour. | [Routes](../frontend/src/app/router/routes.ts), [tour copy](../frontend/src/shared/i18n/locales/en/tour.json) |

Responsive breakpoints in the theme are 375, 768, 1024 and 1280 px. For a portrait video, use the mobile shell; for landscape, use the desktop dashboard or place a portrait phone mockup against the lavender field. These are film composition recommendations based on the live layouts.

## Motion already in the app

- The existing brand splash draws the cat head outline, grows the ears, wipes in the check, bounces the letters, then reveals a tagline. The logo parts and GSAP sequence are directly reusable as reference. [BrandSplash](../frontend/src/shared/ui/BrandSplash/BrandSplash.tsx).
- The welcome scene eases in background glows and copy, then pops decorative badges. General page reveals fade and rise roughly 8–10 px over 0.24–0.44 s with small stagger. [Page motion](../frontend/src/shared/lib/motion/usePageReveal.ts).
- The camera scan line sweeps from top to bottom over 2.2 s; scan CTA pulse, card hover, tab movement, and logo shine are present. Use these as a timing and energy reference, not as a required film storyboard. [CSS motion](../frontend/src/app/styles/index.css).
- The application respects `prefers-reduced-motion`; make any product demo version understandable with static frames and short dissolves too. [CSS motion](../frontend/src/app/styles/index.css), [page motion](../frontend/src/shared/lib/motion/usePageReveal.ts).

## Suggested 30 second motion graphic

This sequence is a proposed treatment. It uses real product behavior but can be compressed for clarity.

| Time | Image / motion | On screen idea |
| --- | --- | --- |
| 0–3 s | A warm cat portrait enters on the pale background. A few indicator granules change color; keep the scene clearly illustrative. | “Small changes are easy to miss.” |
| 3–6 s | The blue cat outline traces on; ears and yellow check lock into place. Reveal the active CatCheck wordmark. | “CatCheck” |
| 6–10 s | A phone UI selects one cat; the scan frame appears around a close photo of the litter area. Yellow line sweeps once. | “Photo the color changed litter.” |
| 10–15 s | The image becomes a measured color swatch, then a pH estimate lands on a labeled reference scale. Keep number and category legible; show it as an example. | “See an estimated pH band.” |
| 15–20 s | One result moves into the cat's history; two or three more points build a trend line over dates. | “Understand change over time.” |
| 20–25 s | Brief cards for reminder, cat profile and vet map slide in; prioritize one or two if the frame is crowded. | “Keep a routine. Know when to ask for help.” |
| 25–30 s | Return to the logo on `#FAF8FF`, with blue CTA and small veterinary disclaimer. | “Check with care, always there.” / “Not a veterinary diagnosis.” |

For an explainer rather than an ad, spend longer on the cat selection and inconclusive result path. Do not imply the app identifies which cat used a shared tray: users must confirm the cat, or save to the shared log. Do not portray every photo as yielding a valid result. [Cat selection copy](../frontend/src/shared/i18n/locales/en/scan.json), [result copy](../frontend/src/shared/i18n/locales/en/scan.json).

## Asset pull list

| Need | File or directory |
| --- | --- |
| Main logo / end card | [web-auth/logo.png](../frontend/src/shared/assets/images/web-auth/logo.png) |
| Animatable brand pieces | [splash layers](../frontend/src/shared/assets/images/splash/) |
| Warm hero cat photo | [welcome-hero-cat.png](../frontend/src/shared/assets/images/welcome-hero-cat.png) |
| Litter close up | [viewport-litter.png](../frontend/src/shared/assets/images/web-scan/viewport-litter.png), [scan-litter-closeup.jpg](../frontend/src/shared/assets/images/web-dashboard/scan-litter-closeup.jpg) |
| Individual colored beads | [bead-01.png](../frontend/src/shared/assets/images/web-scan/bead-01.png) through `bead-04.png` in the same directory |
| Example cat portraits | [web-cat images](../frontend/src/shared/assets/images/web-cat/) |
| UI icon families | [dashboard SVGs](../frontend/src/shared/assets/icons/web-dashboard/), [welcome SVGs](../frontend/src/shared/assets/icons/welcome/) |
| Fonts | [Nunito WOFF2](../frontend/public/fonts/) |
| UI copy in both languages | [English](../frontend/src/shared/i18n/locales/en/), [Vietnamese](../frontend/src/shared/i18n/locales/vi/) |

The photography and raster logo are source assets, not high resolution vector masters. Check the final export at target resolution, especially on a large end card. Screens should be recreated from the component code or captured from the running app with consented demo data; the repository has no committed finished screen screenshots in `frontend/public/screenshots`.

## Accuracy guardrails for narration and graphics

1. Say **estimates pH from indicator litter color**, **reference band**, and **trend over time**. Do not say it diagnoses urinary disease or replaces a urine test or veterinarian. [Scan UI language](../frontend/src/shared/i18n/locales/en/scan.json), [settings disclaimer](../frontend/src/shared/i18n/locales/en/settings.json).
2. Show owner confirmation of the cat. A shared tray result is stored separately from an individual cat's trend. [Selection copy](../frontend/src/shared/i18n/locales/en/scan.json).
3. Label red/amber/green statuses with text or icons, never color alone. The pH rainbow and clinical status colors have different meanings. [Band tokens](../frontend/src/entities/ph-bands/colorToken.ts).
4. Avoid a fixed pH threshold in generic marketing. The current seed has 6.3–6.6 as the reference range, but the application loads the band configuration. [Seeded bands](../backend/src/main/resources/db/migration/V9__colorchart.sql).
5. If the film is public facing, use fictional cat names and example readings; avoid real account data and photos.

## Production checklist

- Choose one language for captions and voiceover; the branch contains both English and Vietnamese UI copy.
- Use the **active** logo and keep the check shape legible at small sizes.
- Keep the phone UI readable: one main action or data point per shot, with comfortable contrast against the pale background.
- Leave safe margins for 9:16 and 16:9 crops; keep text separate from the bottom navigation and scan button.
- Render a static or reduced motion version with the same message.
- Verify the final disclaimer and any example pH reading against the current product copy before publishing.
