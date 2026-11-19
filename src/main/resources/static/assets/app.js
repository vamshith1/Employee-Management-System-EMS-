/* Employee Management System – single-page frontend for the /api REST endpoints. No build step. */
(() => {
    'use strict';

    // ------------------------------------------------------------------ state & helpers
    const TOKEN_KEY = 'ems.token';
    const state = { token: null, user: null, departments: null };

    const $ = (sel, root = document) => root.querySelector(sel);
    const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

    const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    const today = () => new Date().toLocaleDateString('en-CA'); // YYYY-MM-DD in local time
    const fmtDate = (d) => d ? new Date(d + (d.length === 10 ? 'T00:00:00' : '')).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
    const fmtTime = (dt) => dt ? new Date(dt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : '—';
    const fmtMoney = (n) => n == null ? '—' : Number(n).toLocaleString(undefined, { maximumFractionDigits: 2 });
    const titleCase = (s) => s === 'HR' ? 'HR'
        : String(s ?? '').toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
    const hasRole = (...roles) => state.user && roles.includes(state.user.role);
    const isStaff = () => hasRole('ADMIN', 'HR');

    const BADGE = {
        ACTIVE: 'green', ON_LEAVE: 'amber', INACTIVE: 'red',
        PRESENT: 'green', LATE: 'amber', HALF_DAY: 'blue', ABSENT: 'red',
        PENDING: 'amber', APPROVED: 'green', REJECTED: 'red', CANCELLED: '',
        ADMIN: 'blue', HR: 'green', EMPLOYEE: '',
    };
    const badge = (v) => `<span class="badge ${BADGE[v] ?? ''}">${esc(titleCase(v))}</span>`;

    const ENUMS = {
        employeeStatus: ['ACTIVE', 'ON_LEAVE', 'INACTIVE'],
        attendanceStatus: ['PRESENT', 'LATE', 'HALF_DAY', 'ABSENT'],
        leaveType: ['ANNUAL', 'SICK', 'CASUAL', 'UNPAID'],
        leaveStatus: ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'],
        role: ['ADMIN', 'HR', 'EMPLOYEE'],
    };
    const enumOptions = (list) => list.map((v) => ({ value: v, label: titleCase(v) }));

    function toast(message, kind = 'ok') {
        const el = document.createElement('div');
        el.className = `toast ${kind === 'error' ? 'error' : ''}`;
        el.textContent = message;
        $('#toasts').appendChild(el);
        setTimeout(() => el.remove(), kind === 'error' ? 6000 : 3500);
    }

    function storage(fn) {
        try { return fn(window.sessionStorage); } catch { return null; }
    }

    // ------------------------------------------------------------------ API client
    class ApiError extends Error {
        constructor(status, body) {
            super(body?.message || `Request failed (${status})`);
            this.status = status;
            this.fieldErrors = body?.fieldErrors || [];
        }
    }

    async function api(method, path, body) {
        const headers = { Accept: 'application/json' };
        if (state.token) headers.Authorization = `Bearer ${state.token}`;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (res.status === 401 && path !== '/auth/login') {
            logout('Your session has expired. Please sign in again.');
            throw new ApiError(401, data);
        }
        if (!res.ok) throw new ApiError(res.status, data);
        return data;
    }

    const qs = (params) => {
        const p = new URLSearchParams();
        Object.entries(params).forEach(([k, v]) => { if (v !== '' && v != null) p.append(k, v); });
        const s = p.toString();
        return s ? `?${s}` : '';
    };

    async function departments(force = false) {
        if (!state.departments || force) state.departments = await api('GET', '/departments');
        return state.departments;
    }

    async function employeeOptions() {
        const page = await api('GET', '/employees?size=100&sort=lastName&sort=firstName');
        return page.content.map((e) => ({ value: e.id, label: `${e.fullName} · ${e.departmentName}` }));
    }

    // ------------------------------------------------------------------ modal & forms
    /**
     * Opens a form dialog. fields: [{ name, label, type, options, required, value, placeholder, half }].
     * onSubmit(values) may throw ApiError; field errors are shown next to inputs.
     */
    function formModal({ title, subtitle, fields, submitLabel = 'Save', danger = false, onSubmit }) {
        const root = $('#modal-root');
        const fieldHtml = (f) => {
            const id = `f-${f.name}`;
            const req = f.required ? 'required' : '';
            let input;
            if (f.type === 'select') {
                input = `<select id="${id}" name="${f.name}" ${req}>
                    ${f.allowEmpty !== false ? `<option value="">${esc(f.placeholder || '— Select —')}</option>` : ''}
                    ${f.options.map((o) => `<option value="${esc(o.value)}" ${String(o.value) === String(f.value ?? '') ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}
                </select>`;
            } else if (f.type === 'textarea') {
                input = `<textarea id="${id}" name="${f.name}" ${req} placeholder="${esc(f.placeholder || '')}">${esc(f.value ?? '')}</textarea>`;
            } else if (f.type === 'checkbox') {
                return `<label class="field" style="grid-template-columns:auto 1fr;align-items:center;gap:8px">
                    <input type="checkbox" id="${id}" name="${f.name}" ${f.value ? 'checked' : ''}> ${esc(f.label)}</label>`;
            } else {
                input = `<input id="${id}" name="${f.name}" type="${f.type || 'text'}" ${req} value="${esc(f.value ?? '')}"
                    placeholder="${esc(f.placeholder || '')}" ${f.step ? `step="${f.step}"` : ''} ${f.autocomplete ? `autocomplete="${f.autocomplete}"` : ''}>`;
            }
            return `<label class="field" data-field="${f.name}">${esc(f.label)}${f.required ? ' *' : ''}${input}<span class="err"></span></label>`;
        };

        // pair up consecutive "half" fields into two-column rows
        const parts = [];
        for (let i = 0; i < fields.length; i++) {
            if (fields[i].half && fields[i + 1]?.half) {
                parts.push(`<div class="row-2">${fieldHtml(fields[i])}${fieldHtml(fields[i + 1])}</div>`);
                i++;
            } else parts.push(fieldHtml(fields[i]));
        }

        root.innerHTML = `
            <div class="modal-backdrop">
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
                    <header><h2 id="modal-title">${esc(title)}</h2>${subtitle ? `<p class="muted">${esc(subtitle)}</p>` : ''}</header>
                    <form novalidate>
                        <div class="form-error hidden"></div>
                        ${parts.join('')}
                        <footer>
                            <button type="button" class="btn" data-cancel>Cancel</button>
                            <button type="submit" class="btn ${danger ? 'btn-danger' : 'btn-primary'}">${esc(submitLabel)}</button>
                        </footer>
                    </form>
                </div>
            </div>`;

        const close = () => { root.innerHTML = ''; document.removeEventListener('keydown', onKey); };
        const onKey = (e) => { if (e.key === 'Escape') close(); };
        document.addEventListener('keydown', onKey);
        $('[data-cancel]', root).onclick = close;
        $('.modal-backdrop', root).addEventListener('mousedown', (e) => { if (e.target.classList.contains('modal-backdrop')) close(); });

        const form = $('form', root);
        const firstInput = $('input:not([type=checkbox]), select, textarea', form);
        if (firstInput) firstInput.focus();

        form.onsubmit = async (e) => {
            e.preventDefault();
            $$('.field', form).forEach((el) => { el.classList.remove('invalid'); const er = $('.err', el); if (er) er.textContent = ''; });
            const errBox = $('.form-error', form);
            errBox.classList.add('hidden');

            const values = {};
            fields.forEach((f) => {
                const el = form.elements[f.name];
                if (f.type === 'checkbox') values[f.name] = el.checked;
                else {
                    const v = el.value.trim();
                    values[f.name] = v === '' ? null : (f.type === 'number' || f.numeric) ? Number(v) : v;
                }
            });

            const btn = $('button[type=submit]', form);
            btn.disabled = true;
            try {
                await onSubmit(values);
                close();
            } catch (err) {
                if (!(err instanceof ApiError)) { console.error(err); errBox.textContent = 'Something went wrong.'; errBox.classList.remove('hidden'); return; }
                let unplaced = [];
                err.fieldErrors.forEach((fe) => {
                    const el = $(`.field[data-field="${CSS.escape(fe.field)}"]`, form);
                    if (el) { el.classList.add('invalid'); $('.err', el).textContent = fe.message; }
                    else unplaced.push(fe.message);
                });
                const msg = err.fieldErrors.length ? unplaced.join(' ') : err.message;
                if (msg) { errBox.textContent = msg; errBox.classList.remove('hidden'); }
            } finally {
                btn.disabled = false;
            }
        };
    }

    const confirmModal = (title, subtitle, label, action) =>
        formModal({ title, subtitle, fields: [], submitLabel: label, danger: true, onSubmit: action });

    // ------------------------------------------------------------------ auth
    function renderLogin(message) {
        document.title = 'Sign in · EMS';
        $('#app').innerHTML = `
            <div class="login-wrap">
                <div class="login-card">
                    <div class="brand"><span class="brand-mark">EMS</span> Employee Management</div>
                    <p class="muted" style="margin:8px 0 0">Sign in to manage employees, attendance and leave.</p>
                    <form id="login-form" novalidate>
                        <div class="form-error ${message ? '' : 'hidden'}">${esc(message || '')}</div>
                        <label class="field">Username<input name="username" autocomplete="username" required></label>
                        <label class="field">Password<input name="password" type="password" autocomplete="current-password" required></label>
                        <button class="btn btn-primary" type="submit">Sign in</button>
                    </form>
                    <div class="demo">
                        <div class="muted" style="font-size:13px">Demo accounts (seed data)</div>
                        <div class="demo-buttons">
                            <button class="btn btn-sm" data-demo="admin">Admin</button>
                            <button class="btn btn-sm" data-demo="hr">HR</button>
                            <button class="btn btn-sm" data-demo="employee">Employee</button>
                        </div>
                    </div>
                </div>
            </div>`;

        const form = $('#login-form');
        const DEMO = { admin: 'Admin@123', hr: 'Hr@12345', employee: 'Employee@123' };
        $$('[data-demo]').forEach((b) => b.onclick = () => {
            form.username.value = b.dataset.demo;
            form.password.value = DEMO[b.dataset.demo];
            form.requestSubmit();
        });
        form.username.focus();

        form.onsubmit = async (e) => {
            e.preventDefault();
            const errBox = $('.form-error', form);
            const btn = $('button[type=submit]', form);
            btn.disabled = true;
            try {
                const res = await api('POST', '/auth/login', { username: form.username.value.trim(), password: form.password.value });
                state.token = res.accessToken;
                state.user = res.user;
                storage((s) => s.setItem(TOKEN_KEY, state.token));
                location.hash = defaultRoute();
                renderShell();
            } catch (err) {
                errBox.textContent = err.fieldErrors?.length ? 'Enter your username and password.' : err.message;
                errBox.classList.remove('hidden');
            } finally {
                btn.disabled = false;
            }
        };
    }

    function logout(message) {
        state.token = null;
        state.user = null;
        state.departments = null;
        storage((s) => s.removeItem(TOKEN_KEY));
        $('#modal-root').innerHTML = '';
        history.replaceState(null, '', location.pathname);
        renderLogin(message);
    }

    // ------------------------------------------------------------------ shell & routing
    const ROUTES = {
        dashboard:  { label: 'Dashboard', group: 'Overview', roles: ['ADMIN', 'HR'], render: pageDashboard },
        employees:  { label: 'Employees', group: 'Manage', roles: ['ADMIN', 'HR'], render: pageEmployees },
        departments:{ label: 'Departments', group: 'Manage', roles: ['ADMIN', 'HR', 'EMPLOYEE'], render: pageDepartments },
        attendance: { label: 'Attendance', group: 'Manage', roles: ['ADMIN', 'HR'], render: pageAttendanceAll },
        approvals:  { label: 'Leave requests', group: 'Manage', roles: ['ADMIN', 'HR'], render: pageApprovals },
        users:      { label: 'Users', group: 'Manage', roles: ['ADMIN'], render: pageUsers },
        me:         { label: 'My attendance', group: 'Me', roles: ['ADMIN', 'HR', 'EMPLOYEE'], needsEmployee: true, render: pageMyAttendance },
        leaves:     { label: 'My leave', group: 'Me', roles: ['ADMIN', 'HR', 'EMPLOYEE'], needsEmployee: true, render: pageMyLeaves },
        profile:    { label: 'Profile', group: 'Me', roles: ['ADMIN', 'HR', 'EMPLOYEE'], render: pageProfile },
    };

    const allowed = (key) => {
        const r = ROUTES[key];
        return r && hasRole(...r.roles) && (!r.needsEmployee || state.user.employeeId != null);
    };
    const defaultRoute = () => `#/${isStaff() ? 'dashboard' : 'me'}`;
    const currentRoute = () => (location.hash.match(/^#\/(\w+)/) || [])[1];

    function renderShell() {
        const groups = {};
        Object.entries(ROUTES).filter(([k]) => allowed(k)).forEach(([k, r]) => (groups[r.group] ||= []).push([k, r]));
        const u = state.user;

        $('#app').innerHTML = `
            <div class="shell">
                <aside class="sidebar">
                    <div class="brand"><span class="brand-mark">EMS</span> Employee Mgmt</div>
                    <nav class="nav">
                        ${Object.entries(groups).map(([g, items]) => `
                            <div class="nav-label">${esc(g)}</div>
                            ${items.map(([k, r]) => `<a href="#/${k}" data-route="${k}">${esc(r.label)}</a>`).join('')}
                        `).join('')}
                    </nav>
                    <div class="sidebar-foot">
                        <div class="who"><strong>${esc(u.employeeName || u.username)}</strong>
                            <span class="muted">@${esc(u.username)} · ${esc(titleCase(u.role))}</span></div>
                        <button class="btn btn-sm" id="logout">Sign out</button>
                    </div>
                </aside>
                <div>
                    <div class="topbar">
                        <button class="btn btn-sm" id="menu" aria-label="Open menu">☰ Menu</button>
                        <div class="brand"><span class="brand-mark">EMS</span></div>
                    </div>
                    <main class="main" id="page"></main>
                </div>
            </div>`;

        $('#logout').onclick = () => logout();
        $('#menu').onclick = () => $('.shell').classList.toggle('nav-open');
        route();
    }

    async function route() {
        if (!state.user) return;
        let key = currentRoute();
        if (!allowed(key)) {
            location.replace(defaultRoute());
            return;
        }
        $$('.nav a').forEach((a) => a.classList.toggle('active', a.dataset.route === key));
        $('.shell')?.classList.remove('nav-open');
        const page = $('#page');
        page.innerHTML = '<div class="spinner">Loading…</div>';
        document.title = `${ROUTES[key].label} · EMS`;
        try {
            await ROUTES[key].render(page);
        } catch (err) {
            if (err.status === 401) return;
            console.error(err);
            page.innerHTML = `<div class="card card-pad"><div class="form-error">${esc(err.message)}</div></div>`;
        }
    }

    window.addEventListener('hashchange', route);

    // ------------------------------------------------------------------ shared UI bits
    const pageHead = (title, subtitle, actions = '') =>
        `<div class="page-head"><div><h1>${esc(title)}</h1>${subtitle ? `<p class="muted">${esc(subtitle)}</p>` : ''}</div><div>${actions}</div></div>`;

    const optionsHtml = (opts, selected, emptyLabel) =>
        (emptyLabel !== undefined ? `<option value="">${esc(emptyLabel)}</option>` : '') +
        opts.map((o) => `<option value="${esc(o.value)}" ${String(o.value) === String(selected ?? '') ? 'selected' : ''}>${esc(o.label)}</option>`).join('');

    function pager(p, onPage) {
        const wrap = document.createElement('div');
        wrap.className = 'pager';
        const from = p.totalElements === 0 ? 0 : p.page * p.size + 1;
        const to = Math.min(p.totalElements, (p.page + 1) * p.size);
        wrap.innerHTML = `<span class="muted">${from}–${to} of ${p.totalElements}</span>
            <div style="display:flex;gap:8px">
                <button class="btn btn-sm" data-p="prev" ${p.first ? 'disabled' : ''}>← Previous</button>
                <button class="btn btn-sm" data-p="next" ${p.last ? 'disabled' : ''}>Next →</button>
            </div>`;
        $('[data-p=prev]', wrap).onclick = () => onPage(p.page - 1);
        $('[data-p=next]', wrap).onclick = () => onPage(p.page + 1);
        return wrap;
    }

    const debounce = (fn, ms = 300) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

    /** Renders a filterable, paginated table. load(params) → PageResponse. */
    function listView(container, { filtersHtml, columns, load, rowActions, onActions }) {
        container.innerHTML = `
            <div class="card">
                ${filtersHtml ? `<div class="filters">${filtersHtml}</div>` : ''}
                <div class="table-wrap"><table>
                    <thead><tr>${columns.map((c) => `<th class="${c.num ? 'num' : ''}">${esc(c.label)}</th>`).join('')}${rowActions ? '<th></th>' : ''}</tr></thead>
                    <tbody></tbody>
                </table></div>
                <div class="pager-slot"></div>
            </div>`;
        let page = 0;
        const tbody = $('tbody', container);
        const slot = $('.pager-slot', container);

        const filterValues = () => Object.fromEntries($$('.filters [name]', container).map((el) => [el.name, el.value]));

        async function refresh(p = page) {
            page = Math.max(0, p);
            tbody.innerHTML = `<tr><td colspan="${columns.length + 1}" class="empty">Loading…</td></tr>`;
            try {
                const data = await load({ ...filterValues(), page, size: 10 });
                tbody.innerHTML = data.content.length
                    ? data.content.map((row) => `<tr>${columns.map((c) => `<td class="${c.num ? 'num' : ''} ${c.wrap ? 'wrap' : ''}">${c.render(row)}</td>`).join('')}
                        ${rowActions ? `<td class="actions">${rowActions(row)}</td>` : ''}</tr>`).join('')
                    : `<tr><td colspan="${columns.length + 1}" class="empty">Nothing found.</td></tr>`;
                slot.replaceChildren(pager(data, refresh));
                if (onActions) onActions(tbody, data.content, () => refresh());
            } catch (err) {
                if (err.status === 401) return;
                tbody.innerHTML = `<tr><td colspan="${columns.length + 1}" class="empty">${esc(err.message)}</td></tr>`;
                slot.innerHTML = '';
            }
        }

        const reset = () => refresh(0);
        $$('.filters select, .filters input[type=date]', container).forEach((el) => el.addEventListener('change', reset));
        $$('.filters input[type=search]', container).forEach((el) => el.addEventListener('input', debounce(reset)));
        refresh(0);
        return { refresh };
    }

    const bindRowButtons = (tbody, rows, handlers) => {
        $$('[data-act]', tbody).forEach((b) => b.onclick = () => {
            const row = rows.find((r) => String(r.id) === b.dataset.id);
            handlers[b.dataset.act]?.(row);
        });
    };

    // ------------------------------------------------------------------ pages
    async function pageDashboard(page) {
        const d = await api('GET', '/dashboard');
        const max = Math.max(1, ...d.headcountByDepartment.map((h) => h.headcount));
        const t = d.todayAttendance;
        page.innerHTML = `
            ${pageHead('Dashboard', `Overview for ${fmtDate(d.date)}`)}
            <div class="stats">
                <div class="card stat"><div class="label">Total employees</div><div class="value">${d.totalEmployees}</div><div class="sub">${d.activeEmployees} active</div></div>
                <div class="card stat"><div class="label">Present today</div><div class="value">${t.present}</div><div class="sub">${t.late} late · ${t.checkedOut} checked out</div></div>
                <div class="card stat"><div class="label">Not checked in</div><div class="value">${t.notCheckedIn}</div><div class="sub">excluding ${d.employeesOnLeaveToday} on leave</div></div>
                <div class="card stat"><div class="label">Pending leave</div><div class="value">${d.pendingLeaveRequests}</div><div class="sub"><a href="#/approvals">Review requests →</a></div></div>
                <div class="card stat"><div class="label">Departments</div><div class="value">${d.totalDepartments}</div><div class="sub"><a href="#/departments">View all →</a></div></div>
            </div>
            <div class="grid-2">
                <div class="card card-pad">
                    <div class="card-head"><h2>Headcount by department</h2><span class="muted">excludes inactive</span></div>
                    <div class="bars">
                        ${d.headcountByDepartment.map((h) => `
                            <div class="bar-row"><span>${esc(h.departmentName)}</span>
                                <div class="bar-track"><div class="bar-fill" style="width:${(h.headcount / max) * 100}%"></div></div>
                                <span class="n">${h.headcount}</span></div>`).join('') || '<div class="muted">No departments yet.</div>'}
                    </div>
                </div>
                <div class="card card-pad">
                    <div class="card-head"><h2>Today's attendance</h2><a href="#/attendance">Details →</a></div>
                    <dl class="kv">
                        <dt>On time</dt><dd>${t.present - t.late}</dd>
                        <dt>Late</dt><dd>${t.late}</dd>
                        <dt>Checked out</dt><dd>${t.checkedOut}</dd>
                        <dt>On approved leave</dt><dd>${d.employeesOnLeaveToday}</dd>
                        <dt>Not checked in</dt><dd>${t.notCheckedIn}</dd>
                    </dl>
                </div>
            </div>`;
    }

    async function employeeForm(existing, onDone) {
        const depts = await departments();
        const e = existing || {};
        formModal({
            title: existing ? `Edit ${e.fullName}` : 'Add employee',
            fields: [
                { name: 'firstName', label: 'First name', required: true, value: e.firstName, half: true },
                { name: 'lastName', label: 'Last name', required: true, value: e.lastName, half: true },
                { name: 'email', label: 'Email', type: 'email', required: true, value: e.email, half: true },
                { name: 'phone', label: 'Phone', value: e.phone, half: true, placeholder: '+91 98765 43210' },
                { name: 'jobTitle', label: 'Job title', required: true, value: e.jobTitle },
                { name: 'departmentId', label: 'Department', type: 'select', required: true, numeric: true, value: e.departmentId,
                    options: depts.map((d) => ({ value: d.id, label: d.name })), half: true },
                { name: 'status', label: 'Status', type: 'select', value: e.status || 'ACTIVE', allowEmpty: false,
                    options: enumOptions(ENUMS.employeeStatus), half: true },
                { name: 'hireDate', label: 'Hire date', type: 'date', required: true, value: e.hireDate || today(), half: true },
                { name: 'salary', label: 'Salary', type: 'number', step: '0.01', value: e.salary, half: true },
            ],
            onSubmit: async (v) => {
                if (existing) await api('PUT', `/employees/${e.id}`, v);
                else await api('POST', '/employees', v);
                toast(existing ? 'Employee updated' : 'Employee added');
                onDone();
            },
        });
    }

    async function pageEmployees(page) {
        const depts = await departments();
        page.innerHTML = pageHead('Employees', 'Search, filter and manage employee records',
            '<button class="btn btn-primary" id="add">+ Add employee</button>') + '<div id="list"></div>';

        const view = listView($('#list', page), {
            filtersHtml: `
                <input type="search" name="q" class="grow" placeholder="Search name or email…" aria-label="Search">
                <select name="departmentId" aria-label="Department">${optionsHtml(depts.map((d) => ({ value: d.id, label: d.name })), '', 'All departments')}</select>
                <select name="status" aria-label="Status">${optionsHtml(enumOptions(ENUMS.employeeStatus), '', 'Any status')}</select>
                <select name="sort" aria-label="Sort">
                    <option value="lastName,asc">Name A–Z</option>
                    <option value="lastName,desc">Name Z–A</option>
                    <option value="hireDate,desc">Newest hires</option>
                    <option value="hireDate,asc">Oldest hires</option>
                    <option value="salary,desc">Salary high–low</option>
                    <option value="department,asc">Department</option>
                </select>`,
            columns: [
                { label: 'Name', render: (e) => `<strong>${esc(e.fullName)}</strong><div class="muted" style="font-size:12px">${esc(e.email)}</div>` },
                { label: 'Job title', render: (e) => esc(e.jobTitle) },
                { label: 'Department', render: (e) => esc(e.departmentName) },
                { label: 'Hired', render: (e) => fmtDate(e.hireDate) },
                { label: 'Salary', num: true, render: (e) => fmtMoney(e.salary) },
                { label: 'Status', render: (e) => badge(e.status) },
            ],
            load: (p) => api('GET', `/employees${qs(p)}`),
            rowActions: (e) => `<button class="btn-link" data-act="edit" data-id="${e.id}">Edit</button>
                                <button class="btn-link danger" data-act="del" data-id="${e.id}">Delete</button>`,
            onActions: (tbody, rows, refresh) => bindRowButtons(tbody, rows, {
                edit: (e) => employeeForm(e, refresh),
                del: (e) => confirmModal(`Delete ${e.fullName}?`,
                    'This also removes their attendance and leave history and disables their login. This cannot be undone.',
                    'Delete employee', async () => { await api('DELETE', `/employees/${e.id}`); toast('Employee deleted'); refresh(); }),
            }),
        });
        $('#add', page).onclick = () => employeeForm(null, () => view.refresh(0));
    }

    async function pageDepartments(page) {
        const admin = hasRole('ADMIN');
        const render = async () => {
            const list = await departments(true);
            page.innerHTML = pageHead('Departments', admin ? 'Create and organise departments' : 'Departments in the organisation',
                admin ? '<button class="btn btn-primary" id="add">+ Add department</button>' : '') + `
                <div class="card"><div class="table-wrap"><table>
                    <thead><tr><th>Name</th><th>Description</th><th>Created</th>${admin ? '<th></th>' : ''}</tr></thead>
                    <tbody>${list.map((d) => `<tr><td><strong>${esc(d.name)}</strong></td><td class="wrap">${esc(d.description || '—')}</td>
                        <td>${fmtDate(d.createdAt?.slice(0, 10))}</td>
                        ${admin ? `<td class="actions"><button class="btn-link" data-act="edit" data-id="${d.id}">Edit</button>
                            <button class="btn-link danger" data-act="del" data-id="${d.id}">Delete</button></td>` : ''}</tr>`).join('')
                        || `<tr><td colspan="4" class="empty">No departments yet.</td></tr>`}</tbody>
                </table></div></div>`;
            if (!admin) return;
            const form = (d) => formModal({
                title: d ? `Edit ${d.name}` : 'Add department',
                fields: [
                    { name: 'name', label: 'Name', required: true, value: d?.name },
                    { name: 'description', label: 'Description', type: 'textarea', value: d?.description },
                ],
                onSubmit: async (v) => {
                    if (d) await api('PUT', `/departments/${d.id}`, v); else await api('POST', '/departments', v);
                    toast(d ? 'Department updated' : 'Department created');
                    render();
                },
            });
            $('#add', page).onclick = () => form(null);
            bindRowButtons($('tbody', page), list, {
                edit: form,
                del: (d) => confirmModal(`Delete ${d.name}?`, 'Only empty departments can be deleted.', 'Delete department',
                    async () => { await api('DELETE', `/departments/${d.id}`); toast('Department deleted'); render(); }),
            });
        };
        await render();
    }

    const hours = (a) => a.hoursWorked == null ? '—' : `${a.hoursWorked} h`;

    async function pageMyAttendance(page) {
        const renderToday = async () => {
            const res = await api('GET', `/attendance/me${qs({ from: today(), to: today(), size: 1 })}`);
            const rec = res.content[0];
            const box = $('#today', page);
            let action = '<button class="btn btn-primary" id="check-in">Check in</button>';
            if (rec?.checkIn && !rec.checkOut) action = '<button class="btn btn-primary" id="check-out">Check out</button>';
            if (rec?.checkOut || (rec && !rec.checkIn)) action = '<span class="muted">You\'re done for today.</span>';
            box.innerHTML = `
                <div class="today">
                    <div><h2>Today · ${fmtDate(today())}</h2>
                        <div style="margin-top:6px">${rec ? badge(rec.status) : '<span class="muted">Not checked in yet</span>'}</div></div>
                    <div class="times">
                        <div><span>Check in</span><strong>${fmtTime(rec?.checkIn)}</strong></div>
                        <div><span>Check out</span><strong>${fmtTime(rec?.checkOut)}</strong></div>
                        <div><span>Worked</span><strong>${rec ? hours(rec) : '—'}</strong></div>
                    </div>
                    <div>${action}</div>
                </div>`;
            const act = async (path, msg) => {
                try { await api('POST', path, {}); toast(msg); await renderToday(); log.refresh(0); }
                catch (err) { toast(err.message, 'error'); }
            };
            $('#check-in', box)?.addEventListener('click', () => act('/attendance/check-in', 'Checked in'));
            $('#check-out', box)?.addEventListener('click', () => act('/attendance/check-out', 'Checked out'));
        };

        page.innerHTML = pageHead('My attendance', 'Check in when you start and check out when you finish') +
            '<div class="card card-pad" id="today"></div><div id="list" style="margin-top:16px"></div>';
        const log = listView($('#list', page), {
            filtersHtml: `<label class="field">From<input type="date" name="from"></label><label class="field">To<input type="date" name="to"></label>`,
            columns: [
                { label: 'Date', render: (a) => fmtDate(a.workDate) },
                { label: 'Status', render: (a) => badge(a.status) },
                { label: 'Check in', render: (a) => fmtTime(a.checkIn) },
                { label: 'Check out', render: (a) => fmtTime(a.checkOut) },
                { label: 'Worked', num: true, render: hours },
                { label: 'Notes', wrap: true, render: (a) => esc(a.notes || '') },
            ],
            load: (p) => api('GET', `/attendance/me${qs(p)}`),
        });
        await renderToday();
    }

    async function attendanceForm(existing, employees, onDone) {
        const a = existing || {};
        const toLocal = (dt) => dt ? dt.slice(0, 16) : '';
        formModal({
            title: existing ? `Edit attendance · ${a.employeeName}` : 'Record attendance',
            subtitle: 'Times are optional for Absent days.',
            fields: [
                { name: 'employeeId', label: 'Employee', type: 'select', required: true, numeric: true, value: a.employeeId, options: employees },
                { name: 'workDate', label: 'Date', type: 'date', required: true, value: a.workDate || today(), half: true },
                { name: 'status', label: 'Status', type: 'select', required: true, value: a.status || 'PRESENT', allowEmpty: false,
                    options: enumOptions(ENUMS.attendanceStatus), half: true },
                { name: 'checkIn', label: 'Check in', type: 'datetime-local', value: toLocal(a.checkIn), half: true },
                { name: 'checkOut', label: 'Check out', type: 'datetime-local', value: toLocal(a.checkOut), half: true },
                { name: 'notes', label: 'Notes', value: a.notes },
            ],
            onSubmit: async (v) => {
                ['checkIn', 'checkOut'].forEach((k) => { if (v[k] && v[k].length === 16) v[k] += ':00'; });
                if (existing) await api('PUT', `/attendance/${a.id}`, v); else await api('POST', '/attendance', v);
                toast('Attendance saved');
                onDone();
            },
        });
    }

    async function pageAttendanceAll(page) {
        const employees = await employeeOptions();
        page.innerHTML = pageHead('Attendance', 'Attendance records for all employees',
            '<button class="btn btn-primary" id="add">+ Record attendance</button>') + '<div id="list"></div>';
        const view = listView($('#list', page), {
            filtersHtml: `
                <select name="employeeId" class="grow" aria-label="Employee">${optionsHtml(employees, '', 'All employees')}</select>
                <label class="field">From<input type="date" name="from" value="${today()}"></label>
                <label class="field">To<input type="date" name="to" value="${today()}"></label>
                <select name="status" aria-label="Status">${optionsHtml(enumOptions(ENUMS.attendanceStatus), '', 'Any status')}</select>`,
            columns: [
                { label: 'Employee', render: (a) => `<strong>${esc(a.employeeName)}</strong>` },
                { label: 'Date', render: (a) => fmtDate(a.workDate) },
                { label: 'Status', render: (a) => badge(a.status) },
                { label: 'In', render: (a) => fmtTime(a.checkIn) },
                { label: 'Out', render: (a) => fmtTime(a.checkOut) },
                { label: 'Worked', num: true, render: hours },
                { label: 'Notes', wrap: true, render: (a) => esc(a.notes || '') },
            ],
            load: (p) => api('GET', `/attendance${qs(p)}`),
            rowActions: (a) => `<button class="btn-link" data-act="edit" data-id="${a.id}">Edit</button>
                                <button class="btn-link danger" data-act="del" data-id="${a.id}">Delete</button>`,
            onActions: (tbody, rows, refresh) => bindRowButtons(tbody, rows, {
                edit: (a) => attendanceForm(a, employees, refresh),
                del: (a) => confirmModal('Delete this attendance record?', `${a.employeeName} · ${fmtDate(a.workDate)}`, 'Delete',
                    async () => { await api('DELETE', `/attendance/${a.id}`); toast('Record deleted'); refresh(); }),
            }),
        });
        $('#add', page).onclick = () => attendanceForm(null, employees, () => view.refresh());
    }

    const leaveColumns = (withEmployee) => [
        ...(withEmployee ? [{ label: 'Employee', render: (l) => `<strong>${esc(l.employeeName)}</strong>` }] : []),
        { label: 'Type', render: (l) => esc(titleCase(l.type)) },
        { label: 'Dates', render: (l) => `${fmtDate(l.startDate)} → ${fmtDate(l.endDate)}` },
        { label: 'Days', num: true, render: (l) => l.days },
        { label: 'Reason', wrap: true, render: (l) => esc(l.reason) },
        { label: 'Status', render: (l) => badge(l.status) + (l.reviewedBy ? `<div class="muted" style="font-size:12px">by ${esc(l.reviewedBy)}</div>` : '') },
        { label: 'Comment', wrap: true, render: (l) => esc(l.reviewComment || '') },
    ];

    async function pageMyLeaves(page) {
        page.innerHTML = pageHead('My leave', 'Request time off and track approvals',
            '<button class="btn btn-primary" id="add">+ Request leave</button>') + '<div id="list"></div>';
        const view = listView($('#list', page), {
            filtersHtml: `<select name="status" aria-label="Status">${optionsHtml(enumOptions(ENUMS.leaveStatus), '', 'Any status')}</select>`,
            columns: leaveColumns(false),
            load: (p) => api('GET', `/leaves/me${qs(p)}`),
            rowActions: (l) => l.status === 'PENDING' ? `<button class="btn-link danger" data-act="cancel" data-id="${l.id}">Cancel</button>` : '',
            onActions: (tbody, rows, refresh) => bindRowButtons(tbody, rows, {
                cancel: (l) => confirmModal('Cancel this leave request?', `${fmtDate(l.startDate)} → ${fmtDate(l.endDate)}`, 'Cancel request',
                    async () => { await api('POST', `/leaves/${l.id}/cancel`); toast('Request cancelled'); refresh(); }),
            }),
        });
        $('#add', page).onclick = () => formModal({
            title: 'Request leave',
            fields: [
                { name: 'type', label: 'Leave type', type: 'select', required: true, value: 'ANNUAL', allowEmpty: false, options: enumOptions(ENUMS.leaveType) },
                { name: 'startDate', label: 'From', type: 'date', required: true, value: today(), half: true },
                { name: 'endDate', label: 'To', type: 'date', required: true, value: today(), half: true },
                { name: 'reason', label: 'Reason', type: 'textarea', required: true },
            ],
            submitLabel: 'Submit request',
            onSubmit: async (v) => {
                try { await api('POST', '/leaves', v); }
                catch (err) {
                    // the cross-field rule is reported on "dateRangeValid"; show it under the end date
                    err.fieldErrors.forEach((fe) => { if (fe.field === 'dateRangeValid') fe.field = 'endDate'; });
                    throw err;
                }
                toast('Leave request submitted');
                view.refresh(0);
            },
        });
    }

    async function pageApprovals(page) {
        page.innerHTML = pageHead('Leave requests', 'Approve or reject employee leave') + '<div id="list"></div>';
        const decide = (l, decision, refresh) => formModal({
            title: `${decision === 'approve' ? 'Approve' : 'Reject'} leave for ${l.employeeName}`,
            subtitle: `${titleCase(l.type)} · ${fmtDate(l.startDate)} → ${fmtDate(l.endDate)} (${l.days} day${l.days === 1 ? '' : 's'})`,
            fields: [{ name: 'comment', label: 'Comment (optional)', type: 'textarea' }],
            submitLabel: decision === 'approve' ? 'Approve' : 'Reject',
            danger: decision === 'reject',
            onSubmit: async (v) => { await api('PUT', `/leaves/${l.id}/${decision}`, v); toast(`Leave ${decision === 'approve' ? 'approved' : 'rejected'}`); refresh(); },
        });
        listView($('#list', page), {
            filtersHtml: `<select name="status" aria-label="Status">${optionsHtml(enumOptions(ENUMS.leaveStatus), 'PENDING', 'Any status')}</select>`,
            columns: leaveColumns(true),
            load: (p) => api('GET', `/leaves${qs(p)}`),
            rowActions: (l) => l.status === 'PENDING' && l.employeeId !== state.user.employeeId
                ? `<button class="btn btn-sm btn-primary" data-act="approve" data-id="${l.id}">Approve</button>
                   <button class="btn btn-sm btn-danger" data-act="reject" data-id="${l.id}">Reject</button>` : '',
            onActions: (tbody, rows, refresh) => bindRowButtons(tbody, rows, {
                approve: (l) => decide(l, 'approve', refresh),
                reject: (l) => decide(l, 'reject', refresh),
            }),
        });
    }

    async function pageUsers(page) {
        const render = async () => {
            const [users, employees] = await Promise.all([api('GET', '/users'), employeeOptions()]);
            page.innerHTML = pageHead('Users', 'Login accounts and roles', '<button class="btn btn-primary" id="add">+ Add user</button>') + `
                <div class="card"><div class="table-wrap"><table>
                    <thead><tr><th>Username</th><th>Role</th><th>Employee</th><th>Status</th><th>Created</th><th></th></tr></thead>
                    <tbody>${users.map((u) => `<tr>
                        <td><strong>${esc(u.username)}</strong>${u.id === state.user.id ? ' <span class="muted">(you)</span>' : ''}</td>
                        <td>${badge(u.role)}</td><td>${esc(u.employeeName || '—')}</td>
                        <td>${u.enabled ? badge('ACTIVE') : '<span class="badge red">Disabled</span>'}</td>
                        <td>${fmtDate(u.createdAt?.slice(0, 10))}</td>
                        <td class="actions"><button class="btn-link" data-act="edit" data-id="${u.id}">Edit</button>
                            <button class="btn-link" data-act="pw" data-id="${u.id}">Reset password</button>
                            ${u.id === state.user.id ? '' : `<button class="btn-link danger" data-act="del" data-id="${u.id}">Delete</button>`}</td>
                    </tr>`).join('')}</tbody>
                </table></div></div>`;

            $('#add', page).onclick = () => formModal({
                title: 'Add user',
                fields: [
                    { name: 'username', label: 'Username', required: true, autocomplete: 'off' },
                    { name: 'password', label: 'Password', type: 'password', required: true, placeholder: 'At least 8 characters', autocomplete: 'new-password' },
                    { name: 'role', label: 'Role', type: 'select', required: true, value: 'EMPLOYEE', allowEmpty: false, options: enumOptions(ENUMS.role) },
                    { name: 'employeeId', label: 'Linked employee', type: 'select', numeric: true, placeholder: '— None —', options: employees },
                ],
                onSubmit: async (v) => { await api('POST', '/users', v); toast('User created'); render(); },
            });
            bindRowButtons($('tbody', page), users, {
                edit: (u) => formModal({
                    title: `Edit ${u.username}`,
                    fields: [
                        { name: 'role', label: 'Role', type: 'select', required: true, value: u.role, allowEmpty: false, options: enumOptions(ENUMS.role) },
                        { name: 'employeeId', label: 'Linked employee', type: 'select', numeric: true, value: u.employeeId, placeholder: '— None —', options: employees },
                        { name: 'enabled', label: 'Account enabled', type: 'checkbox', value: u.enabled },
                    ],
                    onSubmit: async (v) => { await api('PUT', `/users/${u.id}`, v); toast('User updated'); render(); },
                }),
                pw: (u) => formModal({
                    title: `Reset password for ${u.username}`,
                    fields: [{ name: 'password', label: 'New password', type: 'password', required: true, autocomplete: 'new-password' }],
                    submitLabel: 'Reset password',
                    onSubmit: async (v) => { await api('PUT', `/users/${u.id}/password`, v); toast('Password reset'); },
                }),
                del: (u) => confirmModal(`Delete ${u.username}?`, 'The login is removed permanently. Disable it instead to keep history.', 'Delete user',
                    async () => { await api('DELETE', `/users/${u.id}`); toast('User deleted'); render(); }),
            });
        };
        await render();
    }

    async function pageProfile(page) {
        const u = state.user;
        const emp = u.employeeId != null ? await api('GET', '/employees/me') : null;
        page.innerHTML = pageHead('Profile', 'Your account and employee details',
            '<button class="btn" id="pw">Change password</button>') + `
            <div class="grid-2">
                <div class="card card-pad">
                    <div class="card-head"><h2>Employee record</h2>${emp ? badge(emp.status) : ''}</div>
                    ${emp ? `<dl class="kv">
                        <dt>Name</dt><dd>${esc(emp.fullName)}</dd>
                        <dt>Email</dt><dd>${esc(emp.email)}</dd>
                        <dt>Phone</dt><dd>${esc(emp.phone || '—')}</dd>
                        <dt>Job title</dt><dd>${esc(emp.jobTitle)}</dd>
                        <dt>Department</dt><dd>${esc(emp.departmentName)}</dd>
                        <dt>Hired</dt><dd>${fmtDate(emp.hireDate)}</dd>
                    </dl>` : '<p class="muted">This account is not linked to an employee record.</p>'}
                </div>
                <div class="card card-pad">
                    <div class="card-head"><h2>Account</h2></div>
                    <dl class="kv"><dt>Username</dt><dd>${esc(u.username)}</dd><dt>Role</dt><dd>${badge(u.role)}</dd></dl>
                </div>
            </div>`;
        $('#pw', page).onclick = () => formModal({
            title: 'Change password',
            fields: [
                { name: 'currentPassword', label: 'Current password', type: 'password', required: true, autocomplete: 'current-password' },
                { name: 'newPassword', label: 'New password', type: 'password', required: true, placeholder: 'At least 8 characters', autocomplete: 'new-password' },
            ],
            submitLabel: 'Change password',
            onSubmit: async (v) => { await api('POST', '/auth/change-password', v); toast('Password changed'); },
        });
    }

    // ------------------------------------------------------------------ boot
    async function boot() {
        state.token = storage((s) => s.getItem(TOKEN_KEY));
        if (!state.token) return renderLogin();
        try {
            state.user = await api('GET', '/auth/me');
            renderShell();
        } catch {
            if (state.token) logout();
        }
    }

    boot();
})();
