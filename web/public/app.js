const state = { papers: [], exam: 'All', search: '', authenticated: false, toastTimer: null };
const $ = (selector) => document.querySelector(selector);
const paperList = $('#paper-list');
const emptyState = $('#empty-state');
const countLabel = $('#paper-count');
const adminPanel = $('#admin-panel');

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
}

function formatSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function showToast(message) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => toast.classList.remove('show'), 3200);
}

async function request(url, options = {}) {
  const response = await fetch(url, { credentials: 'same-origin', ...options });
  const type = response.headers.get('content-type') || '';
  const payload = type.includes('application/json') ? await response.json() : null;
  if (!response.ok) throw new Error(payload?.error || `Request failed (${response.status}).`);
  return payload;
}

function visiblePapers() {
  const query = state.search.trim().toLocaleLowerCase();
  return state.papers.filter(paper => {
    const examMatches = state.exam === 'All' || paper.exam === state.exam;
    const text = `${paper.title} ${paper.exam} ${paper.year ?? ''} ${paper.originalName}`.toLocaleLowerCase();
    return examMatches && (!query || text.includes(query));
  });
}

function render() {
  const papers = visiblePapers();
  countLabel.textContent = `${papers.length} ${papers.length === 1 ? 'paper' : 'papers'}`;
  paperList.replaceChildren();
  emptyState.hidden = papers.length > 0;
  for (const paper of papers) {
    const row = document.createElement('article');
    row.className = 'paper-row';
    const title = escapeHtml(paper.title);
    const exam = escapeHtml(paper.exam);
    const year = paper.year ? String(paper.year) : 'Year untagged';
    const original = escapeHtml(paper.originalName);
    row.innerHTML = `<div class="paper-main"><span class="paper-title">${title}</span><div class="paper-meta">${exam} · ${year} · ${formatSize(paper.sizeBytes)}<br>${original}</div></div><div class="paper-actions"><button class="open-button" type="button" data-open="${paper.id}">View</button>${state.authenticated ? `<button class="delete-button" type="button" data-delete="${paper.id}" aria-label="Remove ${title}">Remove</button>` : ''}</div>`;
    paperList.append(row);
  }
  paperList.querySelectorAll('[data-open]').forEach(button => button.addEventListener('click', () => openPaper(button.dataset.open)));
  paperList.querySelectorAll('[data-delete]').forEach(button => button.addEventListener('click', () => deletePaper(button.dataset.delete)));
}

async function loadPapers() {
  try {
    const result = await request('/api/papers');
    state.papers = result.papers;
    render();
  } catch (error) {
    countLabel.textContent = 'Could not load papers';
    emptyState.hidden = false;
    emptyState.querySelector('h3').textContent = 'Library unavailable';
    emptyState.querySelector('p').textContent = `${error.message} Check that the web app server is running, then refresh.`;
  }
}

function openPaper(id) {
  const paper = state.papers.find(item => item.id === id);
  if (!paper) return;
  const url = `/api/papers/${encodeURIComponent(id)}/file`;
  $('#viewer-title').textContent = paper.title;
  $('#pdf-frame').src = url;
  $('#open-pdf').href = url;
  $('#viewer').hidden = false;
  $('#viewer').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function closeViewer() {
  $('#pdf-frame').src = 'about:blank';
  $('#viewer').hidden = true;
}

function setAdminView(authenticated) {
  state.authenticated = authenticated;
  $('#login-form').hidden = authenticated;
  $('#upload-area').hidden = !authenticated;
  $('#admin-toggle').textContent = authenticated ? 'Admin controls' : 'Admin sign in';
  render();
}

async function checkSession() {
  try {
    const session = await request('/api/admin/session');
    setAdminView(session.authenticated);
  } catch {
    setAdminView(false);
  }
}

async function deletePaper(id) {
  if (!window.confirm?.('Remove this paper from the library?')) return;
  try {
    await request(`/api/admin/papers/${encodeURIComponent(id)}`, { method: 'DELETE' });
    state.papers = state.papers.filter(paper => paper.id !== id);
    render();
    showToast('Paper removed from the library.');
  } catch (error) {
    showToast(error.message);
  }
}

$('#admin-toggle').addEventListener('click', () => {
  adminPanel.hidden = !adminPanel.hidden;
  if (!adminPanel.hidden) adminPanel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
});
$('#admin-close').addEventListener('click', () => { adminPanel.hidden = true; });
document.querySelectorAll('[data-open-admin]').forEach(button => button.addEventListener('click', () => {
  adminPanel.hidden = false;
  adminPanel.scrollIntoView({ behavior: 'smooth', block: 'center' });
}));
$('#login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    await request('/api/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: $('#admin-password').value })
    });
    $('#admin-password').value = '';
    setAdminView(true);
    showToast('Admin access enabled for this session.');
  } catch (error) {
    showToast(error.message);
  } finally {
    button.disabled = false;
  }
});
$('#logout-button').addEventListener('click', async () => {
  try { await request('/api/admin/logout', { method: 'POST' }); } finally {
    setAdminView(false);
    showToast('Signed out.');
  }
});
$('#paper-files').addEventListener('change', async event => {
  const files = [...event.currentTarget.files];
  const status = $('#upload-status');
  status.className = 'status-line';
  if (!files.length) return;
  for (let index = 0; index < files.length; index += 1) {
    const file = files[index];
    status.textContent = `Importing ${index + 1} of ${files.length}: ${file.name}`;
    try {
      await request('/api/admin/papers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/pdf', 'X-Paper-Name': encodeURIComponent(file.name) },
        body: file
      });
    } catch (error) {
      status.classList.add('error');
      status.textContent = `${file.name}: ${error.message}`;
      continue;
    }
  }
  event.currentTarget.value = '';
  await loadPapers();
  if (!status.classList.contains('error')) {
    status.classList.add('success');
    status.textContent = `${files.length} ${files.length === 1 ? 'paper' : 'papers'} imported.`;
  }
});
$('#search').addEventListener('input', event => { state.search = event.currentTarget.value; render(); });
$('#exam-filters').addEventListener('click', event => {
  const button = event.target.closest('[data-exam]');
  if (!button) return;
  state.exam = button.dataset.exam;
  document.querySelectorAll('.filter').forEach(filter => filter.classList.toggle('active', filter === button));
  render();
});
$('#viewer-close').addEventListener('click', closeViewer);

await Promise.all([loadPapers(), checkSession()]);
