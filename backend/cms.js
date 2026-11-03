/* Same-origin CMS. Credentials remain in the browser's HTTP authentication context. */
const sections = [
  ['Hotel Information', ['hotel']],
  ['Welcome', ['guest_welcome', 'approved_content.welcome']],
  ['About the Resort', ['approved_content.about', 'resort']],
  ['Accommodation', ['approved_content.accommodation']],
  ['Dining', ['approved_content.dining']],
  ['Spa & Pool', ['approved_content.facilities']],
  ['Guest Information', ['approved_content.guest', 'guest']],
  ['Contact', ['approved_content.contact']],
  ['Explore Krabi', ['explore']]
];
const byId = id => document.getElementById(id);
let draft = JSON.parse(byId('seed').value);
let selected = 0;
const role = document.body.dataset.role;
const result = value => { byId('result').textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2); };
function getPath(object, path) { return path.split('.').reduce((value, key) => value?.[key], object); }
function setPath(object, path, value) {
  const keys = path.split('.'); let node = object;
  for (const key of keys.slice(0, -1)) node = node[key];
  node[keys.at(-1)] = value;
}
function collect() {
  const updated = structuredClone(draft);
  for (const input of byId('fields').querySelectorAll('[data-path]')) {
    const original = getPath(draft, input.dataset.path);
    let value = input.value;
    if (Array.isArray(original) || (original && typeof original === 'object')) value = JSON.parse(value);
    else if (typeof original === 'boolean') value = value === 'true';
    else if (typeof original === 'number') value = Number(value);
    setPath(updated, input.dataset.path, value);
  }
  draft = updated;
  return updated;
}
function addField(path, value) {
  const wrap = document.createElement('div'); wrap.className = 'field';
  const label = document.createElement('label'); label.textContent = path.split('.').slice(1).join(' / ') || path;
  const id = 'field-' + path.replaceAll('.', '-'); label.htmlFor = id;
  const input = document.createElement('textarea'); input.id = id; input.dataset.path = path;
  input.rows = Array.isArray(value) || (value && typeof value === 'object') ? 9 :
    String(value).length > 100 ? 5 : 2;
  input.value = typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
  wrap.append(label, input); byId('fields').append(wrap);
}
function showSection(index) {
  try { collect(); } catch (error) { result('Fix JSON before changing section: ' + error); return; }
  selected = index;
  byId('section-title').textContent = sections[index][0];
  byId('section-hint').textContent = 'Edit approved content fields. Empty source sections remain empty until the resort supplies data.';
  byId('fields').replaceChildren();
  for (const path of sections[index][1]) {
    const value = getPath(draft, path);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      for (const [key, leaf] of Object.entries(value)) addField(path + '.' + key, leaf);
    } else if (value !== undefined) addField(path, value);
  }
  for (const [i, button] of [...byId('sections').children].entries()) button.classList.toggle('active', i === index);
}
async function api(path, method = 'GET', body) {
  const response = await fetch('/api/v1/admin/' + path, {
    method, headers: body === undefined ? {} : {'Content-Type':'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body), cache:'no-store'
  });
  const value = await response.json();
  if (!response.ok) throw new Error(JSON.stringify(value));
  return value;
}
async function run(operation) { try { result(await operation()); } catch (error) { result('Error: ' + error.message); } }
function renderTable(host, title, rows, columns) {
  const heading = document.createElement('h3'); heading.textContent = title;
  const table = document.createElement('table');
  const top = document.createElement('tr');
  for (const column of columns) { const cell = document.createElement('th'); cell.textContent = column; top.append(cell); }
  table.append(top);
  for (const row of rows) {
    const tr = document.createElement('tr');
    for (const column of columns) { const cell = document.createElement('td'); cell.textContent = String(row[column] ?? ''); tr.append(cell); }
    table.append(tr);
  }
  host.append(heading, table);
}
async function refresh() {
  const status = await api('status');
  const host = byId('history'); host.replaceChildren();
  renderTable(host, 'Published version: ' + (status.published || 'None'), status.revisions,
    ['id','created_at','actor']);
  const devices = byId('devices'); devices.replaceChildren();
  renderTable(devices, 'Registered TVs', status.devices,
    ['id','label','room','revoked','last_seen','last_sync','content_version']);
  return status;
}
sections.forEach(([title], index) => {
  const button = document.createElement('button'); button.textContent = title;
  button.addEventListener('click', () => showSection(index)); byId('sections').append(button);
});
showSection(0);
byId('validate').onclick = () => run(() => api('validate', 'POST', collect()));
byId('save').onclick = () => run(() => api('draft', 'POST', collect()));
byId('preview').onclick = () => run(() => api('preview'));
byId('publish').onclick = () => run(async () => {
  if (!confirm('Publish the saved draft to enrolled TVs?')) return 'Cancelled';
  const value = await api('publish', 'POST', {}); await refresh(); return value;
});
byId('refresh').onclick = () => run(refresh);
byId('rollback').onclick = () => run(async () => {
  const revision = Number(byId('revision').value);
  if (!Number.isInteger(revision) || revision < 1 || !confirm('Activate revision ' + revision + '?')) return 'Cancelled';
  const value = await api('rollback', 'POST', {revision}); await refresh(); return value;
});
byId('enroll').onclick = () => run(() => api('enrollment', 'POST', {label:byId('label').value}));
byId('assign').onclick = () => run(async () => {
  const value = await api('device', 'POST', {device_id:byId('device-id').value,
    room:byId('room').value, action:'assign'}); await refresh(); return value;
});
byId('revoke').onclick = () => run(async () => {
  const device_id = byId('device-id').value;
  if (!confirm('Revoke token for device ' + device_id + '?')) return 'Cancelled';
  const value = await api('device', 'POST', {device_id, action:'revoke'}); await refresh(); return value;
});
byId('save-media').onclick = () => run(() => api('media', 'POST', {media:JSON.parse(byId('playlist').value)}));
byId('upload').onclick = () => run(async () => {
  const file = byId('media-file').files[0]; if (!file) throw new Error('Select a file');
  if (file.size > 50 * 1024 * 1024) throw new Error('File exceeds 50 MiB');
  const bytes = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const hash = [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2,'0')).join('');
  const extension = file.name.split('.').at(-1).toLowerCase();
  const response = await fetch('/api/v1/admin/media-file', {method:'PUT',
    headers:{'X-Content-SHA256':hash,'X-Media-Extension':extension},body:bytes});
  const value = await response.json(); if (!response.ok) throw new Error(JSON.stringify(value));
  return value;
});
run(async () => { const preview = await api('preview'); byId('playlist').value = JSON.stringify(preview.media, null, 2); await refresh(); return 'Draft loaded'; });
