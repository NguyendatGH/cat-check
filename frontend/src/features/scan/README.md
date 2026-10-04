# Scan

- The production UI calls the native API through `apiFetch` / `apiSubmitScan`; `mocks.ts` is a
  legacy fixture and is not started by the application.
- Current flow: choose an owned cat or shared tray, capture/select an image, submit multipart
  metadata + image, and display the persisted pH result. History, analysis, dispute, reassignment,
  image access and deletion use the corresponding `/api/v1/scans` endpoints.
- The current result remains the existing pH/color-chart contract. No new scan label taxonomy or
  model output is implemented before the owner completes the label research and approves a
  versioned taxonomy. See `docs/plans/ai-rag-and-scan-label-plan.md`.
- An inconclusive image is a persisted result but does not consume a scan credit. A conclusive
  scan consumes one trial/paid credit; native E2E tests must avoid spending demo credits.
- `/shared-tray-log` is not implemented yet.
