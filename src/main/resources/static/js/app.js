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
        stopServer: () => request('POST', '/api/websocket/stop', null, { allowedStatuses: [200] })
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
            const message = (payload && payload.message) || `HTTP ${response.status}`;
            throw new Error(message);
        }
        return payload;
    }

    const state = {
        localAddress: null,
        mode: ConnectionMode.LOCAL,
        connected: false,
        serverAddress: null,
        remoteAddressDraft: ''
    };

    const elements = {};

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
    }

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
                <div class="toast-body">${message}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Fechar"></button>
            </div>`;
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

    async function init() {
        cacheElements();
        elements.connectButton.addEventListener('click', handleConnectClick);
        elements.modeLocal.addEventListener('change', handleModeChange);
        elements.modeRemote.addEventListener('change', handleModeChange);
        elements.serverAddressInput.addEventListener('input', handleRemoteAddressInput);
        elements.navLinks.forEach(link => link.addEventListener('click', handleNavClick));

        await Promise.all([loadLocalAddress(), loadConsumerStatus()]);
        render();
    }

    document.addEventListener('DOMContentLoaded', init);
})();
