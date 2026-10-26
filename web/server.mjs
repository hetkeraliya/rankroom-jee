import { createServer } from 'node:http';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, readFile, rename, rm, stat } from 'node:fs/promises';
import { createHash, createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';
import { basename, extname, join, resolve, sep } from 'node:path';
import { Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const publicDir = resolve(here, 'public');
const dataDir = resolve(process.env.DATA_DIR || join(here, 'data'));
const pdfDir = join(dataDir, 'pdfs');
const dbPath = join(dataDir, 'papers.sqlite');
const port = Number(process.env.PORT || 4173);
const adminPassword = process.env.ADMIN_PASSWORD || '';
const sessionSecret = process.env.SESSION_SECRET || '';
const maxPdfBytes = 64 * 1024 * 1024;
const maxJsonBytes = 16 * 1024;
const isProduction = process.env.NODE_ENV === 'production';

if (adminPassword.length < 16) throw new Error('Set ADMIN_PASSWORD to at least 16 characters.');
if (sessionSecret.length < 32) throw new Error('Set SESSION_SECRET to a random value of at least 32 characters.');

await mkdir(pdfDir, { recursive: true });
const db = new DatabaseSync(dbPath);
db.exec(`
  PRAGMA journal_mode = WAL;
  CREATE TABLE IF NOT EXISTS papers (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    exam TEXT NOT NULL,
    year INTEGER,
    original_name TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    sha256 TEXT NOT NULL UNIQUE,
    stored_name TEXT NOT NULL,
    uploaded_at TEXT NOT NULL
  );
  CREATE INDEX IF NOT EXISTS papers_year_exam ON papers(year DESC, exam ASC);
`);

const selectPapers = db.prepare(`
  SELECT id, title, exam, year, original_name AS originalName,
         size_bytes AS sizeBytes, sha256, uploaded_at AS uploadedAt
  FROM papers ORDER BY year DESC, exam ASC, title ASC
`);
const selectPaper = db.prepare('SELECT * FROM papers WHERE id = ?');
const findHash = db.prepare('SELECT id FROM papers WHERE sha256 = ?');
const addPaper = db.prepare(`
  INSERT INTO papers (id, title, exam, year, original_name, size_bytes, sha256, stored_name, uploaded_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
const removePaper = db.prepare('DELETE FROM papers WHERE id = ?');

const loginAttempts = new Map();
const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

function json(res, status, value, headers = {}) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    ...securityHeaders,
    ...headers
  });
  res.end(body);
}

const securityHeaders = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'X-Frame-Options': 'SAMEORIGIN',
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; frame-src 'self'; object-src 'self'; base-uri 'none'; form-action 'self'"
};

async function readJson(req, limit = maxJsonBytes) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw Object.assign(new Error('Request body is too large.'), { status: 413 });
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw Object.assign(new Error('Send valid JSON.'), { status: 400 });
  }
}

function equalSecret(a, b) {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && timingSafeEqual(left, right);
}

function sign(payload) {
  return createHmac('sha256', sessionSecret).update(payload).digest('base64url');
}

function makeSession() {
  const payload = `${Math.floor(Date.now() / 1000) + 60 * 60 * 12}`;
  return `${payload}.${sign(payload)}`;
}

function isAdmin(req) {
  const cookie = req.headers.cookie || '';
  const token = cookie.split(';').map(part => part.trim()).find(part => part.startsWith('rr_admin='))?.slice('rr_admin='.length);
  if (!token) return false;
  const [expires, signature] = token.split('.');
  if (!expires || !signature || Number(expires) < Date.now() / 1000) return false;
  return equalSecret(signature, sign(expires));
}

function cookieHeader(value, maxAge) {
  return `rr_admin=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${isProduction ? '; Secure' : ''}`;
}

function loginLimited(req) {
  const ip = req.socket.remoteAddress || 'unknown';
  const now = Date.now();
  const entry = loginAttempts.get(ip) || { count: 0, until: now + 15 * 60 * 1000 };
  if (entry.until <= now) {
    entry.count = 0;
    entry.until = now + 15 * 60 * 1000;
  }
  entry.count += 1;
  loginAttempts.set(ip, entry);
  return entry.count > 8;
}

function deriveMetadata(filename) {
  const base = basename(filename, extname(filename)).replace(/[\\/\u0000-\u001f]/g, ' ').replace(/\s+/g, ' ').trim();
  const yearMatch = base.match(/\b(19\d{2}|20\d{2})\b/);
  const lowered = base.toLowerCase();
  let exam = 'Other';
  if (lowered.includes('advanced') || lowered.includes('iit-jee') || lowered.includes('iit jee')) exam = 'JEE Advanced';
  else if (lowered.includes('main') || lowered.includes('aIEEE'.toLowerCase())) exam = 'JEE Main';
  return { title: base || 'Untitled paper', exam, year: yearMatch ? Number(yearMatch[1]) : null };
}

async function importPdf(req, res, filename) {
  if (!filename || extname(filename).toLowerCase() !== '.pdf') return json(res, 415, { error: 'Choose a PDF paper.' });
  const declaredLength = Number(req.headers['content-length'] || 0);
  if (declaredLength > maxPdfBytes) return json(res, 413, { error: 'Each PDF must be 64 MB or smaller.' });
  if (!String(req.headers['content-type'] || '').toLowerCase().startsWith('application/pdf')) {
    return json(res, 415, { error: 'The upload must use application/pdf.' });
  }

  const id = randomUUID();
  const tempPath = join(pdfDir, `${id}.upload`);
  const storedName = `${id}.pdf`;
  const finalPath = join(pdfDir, storedName);
  const hash = createHash('sha256');
  let size = 0;
  let signature = Buffer.alloc(0);
  const meter = new Transform({
    transform(chunk, encoding, callback) {
      size += chunk.length;
      if (size > maxPdfBytes) return callback(Object.assign(new Error('Each PDF must be 64 MB or smaller.'), { status: 413 }));
      if (signature.length < 5) signature = Buffer.concat([signature, chunk]).subarray(0, 5);
      hash.update(chunk);
      callback(null, chunk);
    }
  });

  try {
    await pipeline(req, meter, createWriteStream(tempPath, { flags: 'wx' }));
    if (signature.toString('ascii') !== '%PDF-') throw Object.assign(new Error('This file does not begin with a PDF signature.'), { status: 415 });
    const sha256 = hash.digest('hex');
    if (findHash.get(sha256)) throw Object.assign(new Error('That PDF is already in the library.'), { status: 409 });
    const metadata = deriveMetadata(filename);
    const uploadedAt = new Date().toISOString();
    await rename(tempPath, finalPath);
    try {
      addPaper.run(id, metadata.title, metadata.exam, metadata.year, filename, size, sha256, storedName, uploadedAt);
    } catch (error) {
      await rm(finalPath, { force: true });
      throw error;
    }
    json(res, 201, { id, ...metadata, originalName: filename, sizeBytes: size, sha256, uploadedAt });
  } catch (error) {
    await rm(tempPath, { force: true });
    if (!res.headersSent && !res.destroyed) json(res, error.status || 400, { error: error.message || 'Upload failed.' });
    else res.destroy();
  }
}

async function serveStatic(pathname, res) {
  const route = pathname === '/' ? '/index.html' : decodeURIComponent(pathname);
  const filePath = resolve(publicDir, `.${route}`);
  if (!filePath.startsWith(publicDir + sep) && filePath !== publicDir) return json(res, 404, { error: 'Not found.' });
  let info;
  try {
    info = await stat(filePath);
  } catch {
    return json(res, 404, { error: 'Not found.' });
  }
  if (!info.isFile()) return json(res, 404, { error: 'Not found.' });
  res.writeHead(200, {
    'Content-Type': mimeTypes[extname(filePath)] || 'application/octet-stream',
    'Content-Length': info.size,
    'Cache-Control': filePath.endsWith('.html') ? 'no-cache' : 'public, max-age=3600',
    ...securityHeaders
  });
  createReadStream(filePath).pipe(res);
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;
  try {
    if (req.method === 'GET' && pathname === '/api/papers') {
      return json(res, 200, { papers: selectPapers.all() });
    }
    if (req.method === 'GET' && pathname === '/api/admin/session') {
      return json(res, 200, { authenticated: isAdmin(req) });
    }
    if (req.method === 'POST' && pathname === '/api/admin/login') {
      if (loginLimited(req)) return json(res, 429, { error: 'Too many login attempts. Try again in 15 minutes.' });
      const body = await readJson(req);
      if (!equalSecret(body.password || '', adminPassword)) return json(res, 401, { error: 'Password did not match.' });
      loginAttempts.delete(req.socket.remoteAddress || 'unknown');
      return json(res, 200, { authenticated: true }, { 'Set-Cookie': cookieHeader(makeSession(), 60 * 60 * 12) });
    }
    if (req.method === 'POST' && pathname === '/api/admin/logout') {
      return json(res, 200, { authenticated: false }, { 'Set-Cookie': cookieHeader('', 0) });
    }
    if (req.method === 'POST' && pathname === '/api/admin/papers') {
      if (!isAdmin(req)) return json(res, 401, { error: 'Sign in as an admin to import papers.' });
      let filename = '';
      try { filename = decodeURIComponent(req.headers['x-paper-name'] || ''); } catch { return json(res, 400, { error: 'Paper filename is invalid.' }); }
      return await importPdf(req, res, filename);
    }
    const fileMatch = pathname.match(/^\/api\/papers\/([0-9a-f-]{36})\/file$/i);
    if (req.method === 'GET' && fileMatch) {
      const paper = selectPaper.get(fileMatch[1]);
      if (!paper) return json(res, 404, { error: 'Paper not found.' });
      const path = join(pdfDir, paper.stored_name);
      res.writeHead(200, {
        'Content-Type': 'application/pdf',
        'Content-Length': paper.size_bytes,
        'Content-Disposition': `inline; filename="${paper.id}.pdf"`,
        'Cache-Control': 'public, max-age=3600',
        'Accept-Ranges': 'bytes',
        ...securityHeaders
      });
      return createReadStream(path).pipe(res);
    }
    const deleteMatch = pathname.match(/^\/api\/admin\/papers\/([0-9a-f-]{36})$/i);
    if (req.method === 'DELETE' && deleteMatch) {
      if (!isAdmin(req)) return json(res, 401, { error: 'Sign in as an admin to remove papers.' });
      const paper = selectPaper.get(deleteMatch[1]);
      if (!paper) return json(res, 404, { error: 'Paper not found.' });
      removePaper.run(paper.id);
      await rm(join(pdfDir, paper.stored_name), { force: true });
      return json(res, 200, { removed: true });
    }
    if (req.method === 'GET' && !pathname.startsWith('/api/')) return await serveStatic(pathname, res);
    return json(res, 404, { error: 'Not found.' });
  } catch (error) {
    console.error(error);
    if (!res.headersSent) json(res, error.status || 500, { error: error.status ? error.message : 'The request could not be completed.' });
    else res.destroy();
  }
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Rankroom Paper Library listening on port ${port}`);
  if (isProduction) console.warn('Use HTTPS at the reverse proxy. Admin cookies require HTTPS in production.');
});

function shutdown() {
  server.close(() => {
    db.close();
    process.exit(0);
  });
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
