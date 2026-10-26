# Rankroom Paper Library

A small self-hosted web app for a shared JEE paper library. An admin imports complete PDFs; everyone can browse and view the original pages. Keeping the original PDFs preserves diagrams, graphs, notation, and answer-key pages without OCR conversion.

## Requirements

- Node.js 22.13 or newer (uses Node's built-in SQLite module)
- A persistent writable directory for `web/data`
- HTTPS in production

## Run locally

```sh
cd web
export ADMIN_PASSWORD='replace-with-a-long-unique-password'
export SESSION_SECRET="$(openssl rand -hex 32)"
npm start
```

Open `http://localhost:4173`. The first startup creates `web/data/papers.sqlite` and `web/data/pdfs/`. Set `PORT` to change the listening port, or `DATA_DIR` to store records and PDFs elsewhere. Do not put the data directory in a public/static folder.

## Import papers

1. Sign in with `ADMIN_PASSWORD`.
2. Choose one or more PDF papers (up to 64 MB per file).
3. The server stores the original PDF and indexes its filename, inferred exam/year, size, and SHA-256 checksum.
4. Library visitors can search and view the original PDF. Duplicate PDFs are rejected by checksum.

The app does not rewrite papers into extracted text or claim that answer keys have been independently verified. Metadata inferred from filenames can be corrected by renaming a file before import.

## Deploy

Run the Node process behind an HTTPS reverse proxy. Set `NODE_ENV=production`, `ADMIN_PASSWORD`, and `SESSION_SECRET` in the deployment environment. Persist `DATA_DIR` across restarts and backups. Keep the admin password private; visitors do not need an account to view the public library.

The app uses no external services or npm packages. SQLite and file storage are local to the server, so all visitors to that deployment see the same imported papers. This is not the Android APK and does not upload the PDFs to GitHub.
