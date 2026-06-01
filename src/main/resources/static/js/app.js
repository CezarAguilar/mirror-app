(() => {
    'use strict';

    const ConnectionMode = Object.freeze({
        LOCAL: 'LOCAL',
        REMOTE: 'REMOTE'
    });

    const api = {
        getLocalAddress: () => request('GET', '/api/network/local-address'),
        getConsumerStatus: () => request('GET', '/api/websocket-consumer/status'),
        connectConsumer: (mode, serverAddress) => request('POST', '/api/websocket-consumer/connect', { mode, serverAddress }),
        disconnectConsumer: () => request('POST', '/api/websocket-consumer/disconnect'),
        startServer: () => request('POST', '/api/websocket/start', null, { allowedStatuses: [200, 400] }),
        stopServer: () => request('POST', '/api/websocket/stop', null, { allowedStatuses: [200] }),
        listFolders: () => request('GET', '/sync-folders'),
        createFolder: (payload) => request('POST', '/sync-folders', payload),
        deleteFolder: (guid) => request('DELETE', `/sync-folders/${encodeURIComponent(guid)}`),
        browsePath: (path) => {
            const qs = path ? `?path=${encodeURIComponent(path)}` : '';
            return request('GET', `/api/system/browse${qs}`);
        }
    };

    async function request(method, url, body = null, { allowedStatuses } = {}) {
        const options = {
            method,
            headers: body ? { 'Content-Type': 'application/json' } : {}
        };
        if (body) {
            options.body = JSON.stringify(body);
        }
        const response = await fetch(url, options);
        const ok = allowedStatuses ? allowedStatuses.includes(response.status) : response.ok;
        const contentType = response.headers.get('content-type') || '';
        const payload = contentType.includes('application/json')
            ? await response.json().catch(() => null)
            : await response.text().catch(() => null);
        if (!ok) {
            const message = (payload && (payload.detail || payload.message)) || `HTTP ${response.status}`;
            throw new Error(message);
        }
        return payload;
    }

    const state = {
        localAddress: null,
        mode: ConnectionMode.LOCAL,
        connected: false,
        serverAddress: null,
        remoteAddressDraft: '',
        folders: [],
        newFolder: { guid: '', basePath: '' },
        folderPicker: {
            currentPath: null,
            parentPath: null,
            entries: [],
            loading: false
        }
    };

    const elements = {};
    let folderPickerModal = null;

    function cacheElements() {
        elements.serverIcon = document.getElementById('server-icon');
        elements.localAddress = document.getElementById('local-address');
        elements.modeLocal = document.getElementById('mode-local');
        elements.modeRemote = document.getElementById('mode-remote');
        elements.serverAddressInput = document.getElementById('server-address-input');
        elements.connectButton = document.getElementById('connect-button');
        elements.connectionStatus = document.getElementById('connection-status');
        elements.toastArea = document.getElementById('toast-area');
        elements.navLinks = document.querySelectorAll('.nav-link[data-view]');
        elements.views = document.querySelectorAll('.view');

        elements.folderForm = document.getElementById('folder-form');
        elements.folderGuid = document.getElementById('folder-guid');
        elements.folderPath = document.getElementById('folder-path');
        elements.folderBrowse = document.getElementById('folder-browse');
        elements.folderClear = document.getElementById('folder-clear');
        elements.folderSave = document.getElementById('folder-save');
        elements.foldersTableBody = document.getElementById('folders-table-body');
        elements.foldersEmpty = document.getElementById('folders-empty');

        elements.folderPickerModal = document.getElementById('folder-picker-modal');
        elements.folderPickerCurrent = document.getElementById('folder-picker-current');
        elements.folderPickerUp = document.getElementById('folder-picker-up');
        elements.folderPickerRoots = document.getElementById('folder-picker-roots');
        elements.folderPickerList = document.getElementById('folder-picker-list');
        elements.folderPickerEmpty = document.getElementById('folder-picker-empty');
        elements.folderPickerError = document.getElementById('folder-picker-error');
        elements.folderPickerConfirm = document.getElementById('folder-picker-confirm');
    }

    // ---------- Connection / server menu ----------

    function render() {
        elements.localAddress.textContent = state.localAddress || '--';

        elements.modeLocal.checked = state.mode === ConnectionMode.LOCAL;
        elements.modeRemote.checked = state.mode === ConnectionMode.REMOTE;
        elements.modeLocal.disabled = state.connected;
        elements.modeRemote.disabled = state.connected;

        if (state.mode === ConnectionMode.LOCAL) {
            elements.serverAddressInput.value = state.localAddress || '';
            elements.serverAddressInput.disabled = true;
        } else {
            elements.serverAddressInput.value = state.connected
                ? (state.serverAddress || '')
                : state.remoteAddressDraft;
            elements.serverAddressInput.disabled = state.connected;
        }

        elements.connectButton.textContent = state.connected ? 'Desconectar' : 'Conectar';
        elements.connectButton.classList.toggle('btn-primary', !state.connected);
        elements.connectButton.classList.toggle('btn-danger', state.connected);

        elements.connectionStatus.textContent = state.connected ? 'Online' : 'Offline';
        elements.connectionStatus.classList.toggle('text-success', state.connected);
        elements.connectionStatus.classList.toggle('text-muted', !state.connected);

        elements.serverIcon.classList.toggle('server-icon-connected', state.connected);
        elements.serverIcon.classList.toggle('server-icon-disconnected', !state.connected);
    }

    function showToast(message, variant = 'danger') {
        const toast = document.createElement('div');
        toast.className = `toast align-items-center text-bg-${variant} border-0`;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');
        toast.innerHTML = `
            <div class="d-flex">
                <div class="toast-body"></div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Fechar"></button>
            </div>`;
        toast.querySelector('.toast-body').textContent = message;
        elements.toastArea.appendChild(toast);
        const bsToast = new bootstrap.Toast(toast, { delay: 5000 });
        toast.addEventListener('hidden.bs.toast', () => toast.remove());
        bsToast.show();
    }

    async function loadLocalAddress() {
        try {
            const { address } = await api.getLocalAddress();
            state.localAddress = address;
        } catch (error) {
            console.warn('Could not resolve local address', error);
            state.localAddress = '127.0.0.1';
        }
    }

    async function loadConsumerStatus() {
        try {
            const status = await api.getConsumerStatus();
            state.connected = !!status.connected;
            if (status.mode) {
                state.mode = status.mode;
            }
            state.serverAddress = status.serverAddress || null;
        } catch (error) {
            console.error('Failed to load consumer status', error);
        }
    }

    async function handleConnectClick() {
        elements.connectButton.disabled = true;
        try {
            if (state.connected) {
                await disconnectFlow();
            } else {
                await connectFlow();
            }
            await loadConsumerStatus();
            render();
        } catch (error) {
            showToast(error.message || 'Falha na operação', 'danger');
            console.error(error);
        } finally {
            elements.connectButton.disabled = false;
        }
    }

    async function connectFlow() {
        const address = state.mode === ConnectionMode.LOCAL
            ? state.localAddress
            : elements.serverAddressInput.value.trim();

        if (!address) {
            throw new Error('Informe o endereço do servidor');
        }
        if (state.mode === ConnectionMode.REMOTE) {
            state.remoteAddressDraft = address;
        }

        if (state.mode === ConnectionMode.LOCAL) {
            await api.startServer();
        }
        await api.connectConsumer(state.mode, address);
    }

    async function disconnectFlow() {
        await api.disconnectConsumer();
        if (state.mode === ConnectionMode.LOCAL) {
            await api.stopServer();
        }
    }

    function handleModeChange(event) {
        state.mode = event.target.value;
        render();
    }

    function handleRemoteAddressInput(event) {
        if (state.mode === ConnectionMode.REMOTE && !state.connected) {
            state.remoteAddressDraft = event.target.value;
        }
    }

    function handleNavClick(event) {
        event.preventDefault();
        const targetView = event.currentTarget.dataset.view;
        elements.navLinks.forEach(link => link.classList.toggle('active', link.dataset.view === targetView));
        elements.views.forEach(view => view.classList.toggle('d-none', view.id !== `view-${targetView}`));
    }

    // ---------- Folders CRUD ----------

    function generateUuid() {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
            return crypto.randomUUID();
        }
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    function resetFolderForm() {
        state.newFolder = { guid: generateUuid(), basePath: '' };
        elements.folderGuid.value = state.newFolder.guid;
        elements.folderPath.value = '';
    }

    function renderFolders() {
        const body = elements.foldersTableBody;
        body.innerHTML = '';
        if (!state.folders.length) {
            elements.foldersEmpty.classList.remove('d-none');
            return;
        }
        elements.foldersEmpty.classList.add('d-none');

        state.folders.forEach(folder => {
            const tr = document.createElement('tr');

            const tdGuid = document.createElement('td');
            tdGuid.className = 'font-monospace small text-break';
            tdGuid.textContent = folder.guid;

            const tdPath = document.createElement('td');
            tdPath.className = 'text-break';
            tdPath.textContent = folder.basePath;

            const tdAction = document.createElement('td');
            tdAction.className = 'text-end';
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm btn-outline-danger';
            btn.textContent = 'Excluir';
            btn.dataset.action = 'delete';
            btn.dataset.guid = folder.guid;
            tdAction.appendChild(btn);

            tr.append(tdGuid, tdPath, tdAction);
            body.appendChild(tr);
        });
    }

    async function loadFolders() {
        try {
            state.folders = await api.listFolders() || [];
        } catch (error) {
            console.error('Failed to load folders', error);
            state.folders = [];
        }
    }

    async function handleFolderSubmit(event) {
        event.preventDefault();
        const guid = state.newFolder.guid;
        const basePath = elements.folderPath.value.trim();
        if (!basePath) {
            showToast('Informe o endereço da pasta', 'warning');
            elements.folderPath.focus();
            return;
        }
        elements.folderSave.disabled = true;
        try {
            await api.createFolder({ guid, basePath });
            showToast('Pasta cadastrada com sucesso', 'success');
            resetFolderForm();
            await loadFolders();
            renderFolders();
        } catch (error) {
            showToast(error.message || 'Falha ao salvar pasta', 'danger');
        } finally {
            elements.folderSave.disabled = false;
        }
    }

    function handleFolderClear() {
        resetFolderForm();
    }

    async function handleFoldersTableClick(event) {
        const button = event.target.closest('[data-action="delete"]');
        if (!button) return;
        const guid = button.dataset.guid;
        if (!confirm(`Excluir a pasta ${guid}?`)) return;
        button.disabled = true;
        try {
            await api.deleteFolder(guid);
            showToast('Pasta excluída', 'success');
            await loadFolders();
            renderFolders();
        } catch (error) {
            showToast(error.message || 'Falha ao excluir pasta', 'danger');
            button.disabled = false;
        }
    }

    // ---------- Folder picker modal ----------

    function openFolderPicker() {
        if (!folderPickerModal) {
            folderPickerModal = new bootstrap.Modal(elements.folderPickerModal);
        }
        const initialPath = elements.folderPath.value.trim() || null;
        folderPickerModal.show();
        loadFolderPicker(initialPath);
    }

    async function loadFolderPicker(path) {
        state.folderPicker.loading = true;
        renderFolderPicker();
        try {
            const data = await api.browsePath(path);
            state.folderPicker.currentPath = data.currentPath;
            state.folderPicker.parentPath = data.parentPath;
            state.folderPicker.entries = data.entries || [];
            elements.folderPickerError.classList.add('d-none');
        } catch (error) {
            console.error('Folder picker error', error);
            elements.folderPickerError.textContent = error.message || 'Não foi possível listar essa pasta';
            elements.folderPickerError.classList.remove('d-none');
            state.folderPicker.entries = [];
            if (path) {
                // Try to recover by listing the parent (or roots if at top)
                if (state.folderPicker.currentPath === null) {
                    state.folderPicker.entries = [];
                }
            }
        } finally {
            state.folderPicker.loading = false;
            renderFolderPicker();
        }
    }

    function renderFolderPicker() {
        elements.folderPickerCurrent.textContent = state.folderPicker.currentPath || '/ (raízes do sistema)';
        elements.folderPickerUp.disabled = !state.folderPicker.currentPath;
        elements.folderPickerConfirm.disabled = !state.folderPicker.currentPath;

        const list = elements.folderPickerList;
        list.innerHTML = '';

        if (state.folderPicker.loading) {
            const li = document.createElement('li');
            li.className = 'list-group-item text-muted small';
            li.textContent = 'Carregando...';
            list.appendChild(li);
            elements.folderPickerEmpty.classList.add('d-none');
            return;
        }

        if (!state.folderPicker.entries.length) {
            elements.folderPickerEmpty.classList.remove('d-none');
            return;
        }
        elements.folderPickerEmpty.classList.add('d-none');

        state.folderPicker.entries.forEach(entry => {
            const li = document.createElement('li');
            li.className = 'list-group-item list-group-item-action d-flex align-items-center gap-2';
            li.dataset.path = entry.path;

            const icon = document.createElement('i');
            icon.className = 'bi bi-folder-fill text-warning';

            const name = document.createElement('span');
            name.textContent = entry.name;

            const arrow = document.createElement('i');
            arrow.className = 'bi bi-chevron-right text-muted ms-auto small';

            li.append(icon, name, arrow);
            list.appendChild(li);
        });
    }

    function handleFolderPickerListClick(event) {
        const item = event.target.closest('[data-path]');
        if (!item) return;
        loadFolderPicker(item.dataset.path);
    }

    function handleFolderPickerUp() {
        loadFolderPicker(state.folderPicker.parentPath);
    }

    function handleFolderPickerRoots() {
        loadFolderPicker(null);
    }

    function handleFolderPickerConfirm() {
        if (!state.folderPicker.currentPath) return;
        elements.folderPath.value = state.folderPicker.currentPath;
        state.newFolder.basePath = state.folderPicker.currentPath;
        folderPickerModal.hide();
    }

    // ---------- Init ----------

    async function init() {
        cacheElements();

        elements.connectButton.addEventListener('click', handleConnectClick);
        elements.modeLocal.addEventListener('change', handleModeChange);
        elements.modeRemote.addEventListener('change', handleModeChange);
        elements.serverAddressInput.addEventListener('input', handleRemoteAddressInput);
        elements.navLinks.forEach(link => link.addEventListener('click', handleNavClick));

        elements.folderForm.addEventListener('submit', handleFolderSubmit);
        elements.folderClear.addEventListener('click', handleFolderClear);
        elements.folderBrowse.addEventListener('click', openFolderPicker);
        elements.foldersTableBody.addEventListener('click', handleFoldersTableClick);

        elements.folderPickerList.addEventListener('click', handleFolderPickerListClick);
        elements.folderPickerUp.addEventListener('click', handleFolderPickerUp);
        elements.folderPickerRoots.addEventListener('click', handleFolderPickerRoots);
        elements.folderPickerConfirm.addEventListener('click', handleFolderPickerConfirm);

        resetFolderForm();
        await Promise.all([loadLocalAddress(), loadConsumerStatus(), loadFolders()]);
        render();
        renderFolders();
    }

    document.addEventListener('DOMContentLoaded', init);
})();
