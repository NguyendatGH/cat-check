# CatCheck

CatCheck is a Spring Boot 4 / React application for tracking litter color changes and estimated pH trends.

## Local development

Install PostgreSQL natively, then run the backend and frontend natively for hot reload:

```bash
# one-time PostgreSQL setup: see backend/README.md
cd backend && ./run-local.sh
cd frontend && npm ci --legacy-peer-deps && npm run dev
```

If Mailpit is installed locally, it is available at <http://localhost:8025>. Without Mailpit,
set `catcheck.notification.email-sink=file` and inspect `backend/target/dev-mail/`.

Docker is not required for the local verification path; do not build the application image for
this workflow.

The containerized frontend is available at <http://localhost:5173> and proxies same-origin API requests to the backend.

Read `CLAUDE.md`, then `context/spec/04-index.md`, before changing application behavior.
