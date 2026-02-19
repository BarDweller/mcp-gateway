document.addEventListener('DOMContentLoaded', () => {
    const mcpApiUrl = '/mcp-servers';
    const certificateApiUrl = '/certificate';
    const gatewayApiUrl = '/mcp-gateways';
    const appAuthUrl = '/app-auth';

    let serversData = [];
    let gatewaysData = [];
    let currentEditIndex = null;
    let currentGatewayToolEdit = null;
    let gatewayDialogTools = [];
    let gatewayToolTarget = null;
    let serverHeadersDraft = {};
    let authHeader = localStorage.getItem('mcpAuthHeader') || '';
    const openGatewayDetails = new Set();
    const serverToolStatus = new Map();

    function getStatusIcon(status) {
        if (status === 'match') {
            return '✔';
        }
        if (status === 'mismatch') {
            return '✖';
        }
        if (status === 'unknown') {
            return '•';
        }
        return '';
    }

    function buildBasicAuthHeader(username, password) {
        const token = btoa(`${username}:${password}`);
        return `Basic ${token}`;
    }

    function setAuthHeader(header) {
        authHeader = header || '';
        if (authHeader) {
            localStorage.setItem('mcpAuthHeader', authHeader);
        } else {
            localStorage.removeItem('mcpAuthHeader');
        }
    }

    async function apiFetch(url, options = {}) {
        const headers = { ...(options.headers || {}) };
        if (authHeader) {
            headers.Authorization = authHeader;
        }
        const response = await fetch(url, { ...options, headers });
        if (response.status === 401) {
            showLoginDialog();
            throw new Error('Unauthorized');
        }
        return response;
    }

    function showLoginDialog() {
        document.getElementById('loginDialog').style.display = 'flex';
    }

    function hideLoginDialog() {
        document.getElementById('loginDialog').style.display = 'none';
    }

    async function confirmLogin() {
        const username = document.getElementById('loginUsername').value.trim();
        const password = document.getElementById('loginPassword').value;
        if (!username || !password) {
            showMessageDialog('Missing details', 'Please enter your username and password.');
            return;
        }
        setAuthHeader(buildBasicAuthHeader(username, password));
        try {
            await apiFetch(`${appAuthUrl}/check`, { method: 'GET' });
            hideLoginDialog();
            fetchServers();
            fetchGateways();
        } catch (error) {
            setAuthHeader('');
            showMessageDialog('Login failed', 'Invalid credentials.');
            showLoginDialog();
        }
    }

    function logout() {
        setAuthHeader('');
        showLoginDialog();
    }

    async function openAdminSettingsDialog() {
        try {
            const response = await apiFetch(appAuthUrl);
            if (response.ok) {
                const data = await response.json();
                document.getElementById('adminSettingsUsername').value = data?.username || '';
            }
        } catch (error) {
            showMessageDialog('Error', 'Unable to load admin settings.');
            return;
        }
        document.getElementById('adminSettingsPassword').value = '';
        document.getElementById('adminSettingsDialog').style.display = 'flex';
    }

    function closeAdminSettingsDialog() {
        document.getElementById('adminSettingsDialog').style.display = 'none';
    }

    async function confirmAdminSettings() {
        const username = document.getElementById('adminSettingsUsername').value.trim();
        const password = document.getElementById('adminSettingsPassword').value;
        if (!username || !password) {
            showMessageDialog('Missing details', 'Username and password are required.');
            return;
        }
        try {
            const response = await apiFetch(appAuthUrl, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            if (!response.ok) {
                throw new Error('Failed');
            }
            setAuthHeader(buildBasicAuthHeader(username, password));
            closeAdminSettingsDialog();
            showMessageDialog('Updated', 'Admin credentials updated successfully.');
        } catch (error) {
            showMessageDialog('Update failed', 'Unable to update admin credentials.');
        }
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function updateGatewayToolValidationControls(modeId, periodId, customId, periodRowId) {
        const modeEl = document.getElementById(modeId);
        const periodEl = document.getElementById(periodId);
        const customEl = document.getElementById(customId);
        const periodRow = document.getElementById(periodRowId);
        if (!modeEl || !periodEl || !customEl || !periodRow) {
            return;
        }
        const mode = modeEl.value;
        if (mode === 'PER_TIME_PERIOD') {
            periodRow.style.display = 'flex';
        } else {
            periodRow.style.display = 'none';
        }
        const isCustom = periodEl.value === 'custom';
        customEl.style.display = isCustom ? 'block' : 'none';
    }

    function updateToolValidationRow(row) {
        if (!row) {
            return;
        }
        const modeEl = row.querySelector('.tool-validation-mode');
        const periodEl = row.querySelector('.tool-validation-period');
        const customEl = row.querySelector('.tool-validation-custom');
        if (!modeEl || !periodEl || !customEl) {
            return;
        }
        const mode = modeEl.value;
        const showPeriod = mode === 'PER_TIME_PERIOD';
        periodEl.style.display = showPeriod ? 'block' : 'none';
        const isCustom = periodEl.value === 'custom';
        customEl.style.display = showPeriod && isCustom ? 'block' : 'none';
    }

    function normalizeValidationMode(mode) {
        if (!mode) {
            return 'PER_INVOCATION';
        }
        if (mode.toUpperCase() === 'PER_TIME_PERIOD') {
            return 'PER_TIME_PERIOD';
        }
        return 'PER_INVOCATION';
    }

    function formatDuration(seconds) {
        if (!Number.isFinite(seconds) || seconds <= 0) {
            return 'n/a';
        }
        if (seconds % 86400 === 0) {
            return `${seconds / 86400} day${seconds / 86400 === 1 ? '' : 's'}`;
        }
        if (seconds % 3600 === 0) {
            return `${seconds / 3600} hour${seconds / 3600 === 1 ? '' : 's'}`;
        }
        if (seconds % 60 === 0) {
            return `${seconds / 60} minute${seconds / 60 === 1 ? '' : 's'}`;
        }
        return `${seconds} seconds`;
    }

    function formatTimestamp(timestamp) {
        if (!timestamp) {
            return 'unknown time';
        }
        const date = new Date(timestamp);
        if (Number.isNaN(date.getTime())) {
            return 'unknown time';
        }
        return date.toLocaleString();
    }

    function getToolValidationInfo(tool) {
        const status = (tool?.validationStatus || 'unknown').toLowerCase();
        if (status === 'match') {
            return {
                status,
                icon: getStatusIcon('match'),
                tooltip: `Validated ${formatTimestamp(tool?.lastValidatedAt)}`
            };
        }
        if (status === 'mismatch') {
            const failedAt = tool?.firstFailedAt || tool?.lastValidatedAt;
            return {
                status,
                icon: getStatusIcon('mismatch'),
                tooltip: `Failed validation first on ${formatTimestamp(failedAt)}`
            };
        }
        return {
            status: 'unknown',
            icon: getStatusIcon('unknown'),
            tooltip: 'Tool never validated'
        };
    }

    function getValidationSummary(toolRef) {
        const mode = normalizeValidationMode(toolRef?.validationMode);
        if (mode !== 'PER_TIME_PERIOD') {
            return 'Per invocation';
        }
        const seconds = Number(toolRef?.validationPeriodSeconds || 0);
        return `Every ${formatDuration(seconds || 300)}`;
    }

    function getValidationSettingsFromControls(modeId, periodId, customId) {
        const modeEl = document.getElementById(modeId);
        const periodEl = document.getElementById(periodId);
        const customEl = document.getElementById(customId);
        if (!modeEl || !periodEl || !customEl) {
            return { mode: 'PER_INVOCATION', periodSeconds: null };
        }
        const mode = modeEl.value;
        if (mode !== 'PER_TIME_PERIOD') {
            return { mode: 'PER_INVOCATION', periodSeconds: null };
        }

        let periodSeconds = null;
        if (periodEl.value === 'custom') {
            periodSeconds = Number(customEl.value);
        } else {
            periodSeconds = Number(periodEl.value);
        }

        if (!Number.isFinite(periodSeconds) || periodSeconds <= 0) {
            showMessageDialog('Invalid period', 'Please enter a valid validation period in seconds.');
            return null;
        }

        return { mode: 'PER_TIME_PERIOD', periodSeconds };
    }

    function getValidationSettingsFromRow(row) {
        const modeEl = row.querySelector('.tool-validation-mode');
        const periodEl = row.querySelector('.tool-validation-period');
        const customEl = row.querySelector('.tool-validation-custom');
        if (!modeEl || !periodEl || !customEl) {
            return { mode: 'PER_INVOCATION', periodSeconds: null };
        }
        const mode = modeEl.value;
        if (mode !== 'PER_TIME_PERIOD') {
            return { mode: 'PER_INVOCATION', periodSeconds: null };
        }

        let periodSeconds = null;
        if (periodEl.value === 'custom') {
            periodSeconds = Number(customEl.value);
        } else {
            periodSeconds = Number(periodEl.value);
        }

        if (!Number.isFinite(periodSeconds) || periodSeconds <= 0) {
            showMessageDialog('Invalid period', 'Please enter a valid validation period in seconds.');
            return null;
        }

        return { mode: 'PER_TIME_PERIOD', periodSeconds };
    }

    function buildValidationControlsHtml(defaultMode, defaultPeriodSeconds) {
        const periodValue = defaultPeriodSeconds || 300;
        const periodSelectValue = ['30', '300', '1800', '7200', '43200', '86400'].includes(String(periodValue))
            ? String(periodValue)
            : 'custom';
        const customValue = periodSelectValue === 'custom' ? String(periodValue) : '';
        return `
            <div class="tool-validation-settings">
                <span class="tool-validation-label">Validation</span>
                <select class="tool-validation-mode">
                    <option value="PER_INVOCATION">Per invocation</option>
                    <option value="PER_TIME_PERIOD">Per time period</option>
                </select>
                <select class="tool-validation-period" style="display: none;">
                    <option value="30">30 seconds</option>
                    <option value="300">5 minutes</option>
                    <option value="1800">30 minutes</option>
                    <option value="7200">2 hours</option>
                    <option value="43200">12 hours</option>
                    <option value="86400">24 hours</option>
                    <option value="custom">Custom (seconds)</option>
                </select>
                <input type="number" class="tool-validation-custom" placeholder="Seconds" style="display: none;" value="${customValue}">
            </div>
        `;
    }

    function setValidationControlsFromToolRef(toolRef, modeId, periodId, customId, periodRowId) {
        const modeEl = document.getElementById(modeId);
        const periodEl = document.getElementById(periodId);
        const customEl = document.getElementById(customId);
        if (!modeEl || !periodEl || !customEl) {
            return;
        }
        const mode = normalizeValidationMode(toolRef?.validationMode);
        modeEl.value = mode;

        const periodSeconds = Number(toolRef?.validationPeriodSeconds || 0);
        const presetValues = ['30', '300', '1800', '7200', '43200', '86400'];
        if (mode === 'PER_TIME_PERIOD') {
            if (presetValues.includes(String(periodSeconds))) {
                periodEl.value = String(periodSeconds);
                customEl.value = '';
            } else {
                periodEl.value = 'custom';
                customEl.value = periodSeconds > 0 ? String(periodSeconds) : '';
            }
        } else {
            periodEl.value = '300';
            customEl.value = '';
        }
        updateGatewayToolValidationControls(modeId, periodId, customId, periodRowId);
    }

    let pendingConfirmResolve = null;

    function showMessageDialog(title, message) {
        const dialog = document.getElementById('messageDialog');
        const titleEl = document.getElementById('messageDialogTitle');
        const messageEl = document.getElementById('messageDialogMessage');
        if (!dialog || !titleEl || !messageEl) {
            console.error('Message dialog elements not found.');
            return;
        }
        titleEl.textContent = title || 'Notice';
        messageEl.textContent = message || '';
        dialog.style.display = 'flex';
        dialog.setAttribute('aria-hidden', 'false');
    }

    function closeMessageDialog() {
        const dialog = document.getElementById('messageDialog');
        if (!dialog) {
            return;
        }
        dialog.style.display = 'none';
        dialog.setAttribute('aria-hidden', 'true');
    }

    function showConfirmDialog({ title, message, okLabel, okClass } = {}) {
        const dialog = document.getElementById('confirmationDialog');
        const titleEl = document.getElementById('confirmDialogTitle');
        const messageEl = document.getElementById('confirmDialogMessage');
        const okButton = document.getElementById('confirmDialogOk');
        if (!dialog || !titleEl || !messageEl || !okButton) {
            console.error('Confirmation dialog elements not found.');
            return Promise.resolve(false);
        }

        titleEl.textContent = title || 'Are you sure?';
        messageEl.textContent = message || 'This action cannot be undone.';
        okButton.textContent = okLabel || 'OK';
        okButton.className = `btn ${okClass || 'btn-danger'}`;

        dialog.style.display = 'flex';
        dialog.setAttribute('aria-hidden', 'false');

        return new Promise((resolve) => {
            pendingConfirmResolve = resolve;
        });
    }

    function confirmDialogOk() {
        if (pendingConfirmResolve) {
            pendingConfirmResolve(true);
            pendingConfirmResolve = null;
        }
        closeConfirmDialog();
    }

    function confirmDialogCancel() {
        if (pendingConfirmResolve) {
            pendingConfirmResolve(false);
            pendingConfirmResolve = null;
        }
        closeConfirmDialog();
    }

    function closeConfirmDialog() {
        const dialog = document.getElementById('confirmationDialog');
        if (!dialog) {
            return;
        }
        dialog.style.display = 'none';
        dialog.setAttribute('aria-hidden', 'true');
    }

    function resolveServerId(server, index) {
        const serverId = server?.id;
        if (!serverId) {
            console.error('Server id missing:', server, index);
            showMessageDialog('Missing server id', 'Server id is missing. Please refresh the page.');
            return null;
        }
        return serverId;
    }

    function syncNameFromHost() {
        const dialogName = document.getElementById('dialogName');
        const dialogHost = document.getElementById('dialogHost');

        if (!dialogName.value.trim() && dialogHost.value.trim()) {
            dialogName.value = dialogHost.value.trim();
        }
    }

    async function fetchServers() {
        try {
            const response = await apiFetch(mcpApiUrl);
            if (!response.ok) {
                throw new Error('Failed to fetch servers');
            }

            const data = await response.json();
            console.log('Fetched server list:', data);

            serversData = Array.isArray(data) ? data : [];
            renderServerList();
        } catch (error) {
            console.error('Error fetching servers:', error);
            serversData = [];
            renderServerList();
        }
    }

    function renderServerList() {
        const serverList = document.getElementById('serverList');
        serverList.innerHTML = '';

        if (!Array.isArray(serversData) || serversData.length === 0) {
            serverList.innerHTML = '<p>No servers available.</p>';
            return;
        }

        serversData.forEach((server, index) => {
            const serverItem = document.createElement('div');
            serverItem.className = 'server-item';
            const hasStoredTools = Array.isArray(server.tools) && server.tools.length > 0;
            const serverId = server?.id || `index-${index}`;
            const status = serverToolStatus.get(serverId) || (hasStoredTools ? 'unknown' : 'none');
            const statusIcon = getStatusIcon(status);
            const buttonLabel = hasStoredTools ? 'Compare tool information to stored' : 'Read initial tool information';

            serverItem.innerHTML = `
                <div class="server-header">
                    <span class="twistie-name-span">
                        <span id="server-twistie-${index}" onclick="toggleTools(${index})" style="cursor: pointer;">▶</span>
                        <span class="server-name-span" title="${escapeHtml(serverId)}">${server.name || 'Unnamed Server'}</span>
                    </span>
                    <span class="action-buttons">
                        <button class="btn btn-secondary btn-icon" title="Clone to gateway" onclick="openCloneGatewayDialog(${index})">🧬</button>
                        <button class="edit" onclick="openEditDialog(${index})">✏️</button>
                        <button class="remove" onclick="removeServer(${index})">❌</button>
                    </span>
                </div>
                <div id="server-tools-${index}" class="server-details" style="display: none;">
                    <div class="tools-header">
                        <span class="status-icon ${status}">${statusIcon}</span>
                        <button class="btn btn-secondary" onclick="getMCPDetails(${index})">${buttonLabel}</button>
                        <button id="server-tools-approve-${index}" class="btn btn-primary" onclick="approveToolChanges(${index})" style="display: none;">Approve changes</button>
                    </div>
                    <label>Details:</label>
                    <div id="server-tools-content-${index}" class="tools-content"></div>
                </div>
            `;

            serverList.appendChild(serverItem);

            if (hasStoredTools) {
                renderToolList(index, server.tools);
            }
        });
    }

    function getMCPDetails(index) {
        console.log('Getting MCP Details for server at index:', index);
        const server = serversData[index];
        const serverId = resolveServerId(server, index);
        if (!serverId) {
            return;
        }
        const hasStoredTools = Array.isArray(server?.tools) && server.tools.length > 0;
        const endpoint = hasStoredTools ? `${mcpApiUrl}/${serverId}/tools/compare` : `${mcpApiUrl}/${serverId}/tools/read`;

        apiFetch(endpoint, { method: 'POST' })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to fetch tools for server');
                }
                return response.json();
            })
            .then(payload => {
                if (hasStoredTools) {
                    renderToolComparison(index, payload);
                } else {
                    const list = Array.isArray(payload) ? payload : [];
                    serversData[index].tools = list;
                    serverToolStatus.set(serverId, 'match');
                    renderToolList(index, list);
                }
                renderServerList();
                toggleTools(index);
            })
            .catch(error => console.error('Error fetching tools:', error));
    }

    function renderToolList(index, tools) {
        const toolsContent = document.getElementById(`server-tools-content-${index}`);
        const list = Array.isArray(tools) ? tools : [];
        const toolItems = list.map(tool => {
            const name = tool?.name || 'Unnamed tool';
            const desc = tool?.description ? ` - ${tool.description}` : '';
            const argsDisplay = buildArgsDisplay(tool);
            const validation = getToolValidationInfo(tool);
            return `<li class="tool-item"><span class="tool-status ${validation.status}" title="${escapeHtml(validation.tooltip)}">${validation.icon}</span><strong>${name}</strong>${argsDisplay}${desc}</li>`;
        }).join('');
        toolsContent.innerHTML = `<ul class="tool-list">${toolItems}</ul>`;
    }

    function renderToolComparison(index, comparison) {
        const toolsContent = document.getElementById(`server-tools-content-${index}`);
        const items = Array.isArray(comparison?.tools) ? comparison.tools : [];
        const serverId = serversData[index]?.id || `index-${index}`;
        serverToolStatus.set(serverId, comparison?.match ? 'match' : 'mismatch');
        const approveButton = document.getElementById(`server-tools-approve-${index}`);
        if (approveButton) {
            approveButton.style.display = comparison?.match ? 'none' : 'inline-flex';
        }
        serversData[index].tools = items
            .map(item => item.stored || item.current)
            .filter(tool => tool);

        const toolItems = items.map(item => {
            const match = !!item.match;
            const tool = item.current || item.stored;
            const name = tool?.name || item.name || 'Unnamed tool';
            const desc = tool?.description ? ` - ${tool.description}` : '';
            const argsDisplay = buildArgsDisplay(tool);
            const validation = getToolValidationInfo(tool);
            const statusIcon = validation.icon || (match ? '✔' : '✖');
            const statusClass = validation.status || (match ? 'match' : 'mismatch');
            const tooltip = validation.tooltip || (match ? 'Validated' : 'Failed validation');

            let diffHtml = '';
            if (!match && Array.isArray(item.diffs)) {
                diffHtml = item.diffs.map(diff => {
                    const oldValue = diff?.oldValue ?? '';
                    const newValue = diff?.newValue ?? '';
                    return `
                        <div class="diff-line">
                            <span class="diff-field">${diff?.field || 'field'}:</span>
                            <span class="diff-old">${escapeHtml(oldValue)}</span>
                            <span class="diff-new">${escapeHtml(newValue)}</span>
                        </div>
                    `;
                }).join('');
            }

            return `
                <li class="tool-item ${statusClass}">
                    <span class="tool-status ${statusClass}" title="${escapeHtml(tooltip)}">${statusIcon}</span>
                    <strong>${name}</strong>${argsDisplay}${desc}
                    ${diffHtml}
                </li>
            `;
        }).join('');

        toolsContent.innerHTML = `<ul class="tool-list">${toolItems}</ul>`;
    }

    function approveToolChanges(index) {
        const server = serversData[index];
        const serverId = resolveServerId(server, index);
        if (!serverId) {
            return;
        }
        apiFetch(`${mcpApiUrl}/${serverId}/tools/approve`, { method: 'POST' })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to approve tool changes');
                }
                return response.json();
            })
            .then(payload => {
                const list = Array.isArray(payload) ? payload : [];
                serversData[index].tools = list;
                serverToolStatus.set(serverId, 'match');
                renderToolList(index, list);
                renderServerList();
                toggleTools(index);
            })
            .catch(error => console.error('Error approving tool changes:', error));
    }

    function buildArgsDisplay(tool) {
        const properties = tool?.inputSchema?.properties || {};
        const required = new Set(tool?.inputSchema?.required || []);
        const entries = Object.entries(properties);
        if (entries.length === 0) {
            return '';
        }
        const args = entries.map(([propName, propSchema]) => {
            const type = propSchema?.type || '';
            const label = required.has(propName) ? propName : `<i>${propName}</i>`;
            return `${label}:${type}`;
        }).join(', ');
        return args ? `(${args})` : '';
    }

    function toggleTools(index) {
        const toolsSection = document.getElementById(`server-tools-${index}`);
        const twistie = document.getElementById(`server-twistie-${index}`);
        if (toolsSection.style.display === 'none') {
            toolsSection.style.display = 'block';
            twistie.innerText = '▼';
        } else {
            toolsSection.style.display = 'none';
            twistie.innerText = '▶';
        }
    }

    function openAddDialog() {
        currentEditIndex = null;
        serverHeadersDraft = {};
        document.getElementById('dialogTitle').innerText = 'Add Server';
        document.getElementById('dialogName').value = '';
        document.querySelector('input[name="serverType"][value="local"]').checked = true;
        document.querySelector('input[name="pinCert"][value="no"]').checked = true;
        document.getElementById('dialogHost').value = '';
        document.getElementById('dialogPort').value = '';
        document.getElementById('dialogProtocol').value = 'HTTP';
        document.getElementById('dialogRemotePath').value = '';
        document.getElementById('dialogAuthorizationType').value = 'None';
        document.getElementById('dialogAuthUsername').value = '';
        document.getElementById('dialogAuthPassword').value = '';
        document.getElementById('dialogAuthToken').value = '';
        renderServerHeaders(serverHeadersDraft);
        document.getElementById('dialogPath').value = '';
        document.getElementById('dialogArgument').value = '';

        document.getElementById('dialogCertCN').value = '';
        document.getElementById('dialogCertNotBefore').value = '';
        document.getElementById('dialogCertNotAfter').value = '';
        document.getElementById('dialogServerCertificate').value = '';
        document.getElementById('certDetails').style.display = 'none';
        toggleTypeFields();

        document.getElementById('addEditDialog').style.display = 'flex';
    }

    function openEditDialog(index) {
        const server = serversData[index];
        currentEditIndex = index;
        serverHeadersDraft = { ...(server.headers || {}) };
        document.getElementById('dialogTitle').innerText = 'Edit Server';
        document.getElementById('dialogName').value = server.name;
        document.querySelector(`input[name="serverType"][value="${server.type}"]`).checked = true;
        document.querySelector(`input[name="pinCert"][value="${server.certificate ? 'yes' : 'no'}"]`).checked = true;
        document.getElementById('dialogHost').value = server.host || '';
        document.getElementById('dialogPort').value = server.port || '';
        document.getElementById('dialogProtocol').value = server.protocol || 'HTTP';
        document.getElementById('dialogRemotePath').value = server.remotePath || '';
        document.getElementById('dialogAuthorizationType').value = server.authorizationType || 'None';
        document.getElementById('dialogAuthUsername').value = server.authUsername || '';
        document.getElementById('dialogAuthPassword').value = server.authPassword || '';
        document.getElementById('dialogAuthToken').value = server.authToken || '';
        renderServerHeaders(serverHeadersDraft);
        document.getElementById('dialogPath').value = server.path || '';
        document.getElementById('dialogArgument').value = server.argument || '';
        toggleTypeFields();

        if (server.certificate) {
            const certDetails = parseLeafCertificate(server.certificate);
            if (certDetails) {
                document.getElementById('dialogCertCN').value = certDetails.commonName || '';
                document.getElementById('dialogCertNotBefore').value = certDetails.notBefore || '';
                document.getElementById('dialogCertNotAfter').value = certDetails.notAfter || '';
                document.getElementById('dialogServerCertificate').value = server.certificate;
                document.getElementById('certDetails').style.display = 'block';
            } else {
                console.warn('Failed to parse certificate details.');
            }
        } else {
            document.getElementById('dialogCertCN').value = '';
            document.getElementById('dialogCertNotBefore').value = '';
            document.getElementById('dialogCertNotAfter').value = '';
            document.getElementById('dialogServerCertificate').value = '';
            document.getElementById('certDetails').style.display = 'none';
        }

        document.getElementById('addEditDialog').style.display = 'flex';
    }

    function closeDialog() {
        document.getElementById('addEditDialog').style.display = 'none';
    }

    function toggleTypeFields() {
        const serverTypeRemote = document.querySelector('input[name="serverType"][value="remote"]');
        const remoteFields = document.getElementById('remoteFields');
        const localFields = document.getElementById('localFields');
        const pinCertOption = document.getElementById('pinCertOption');
        const remoteAuthFields = document.getElementById('remoteAuthFields');
        const remoteHeaderFields = document.getElementById('remoteHeaderFields');
        const remoteOauthFields = document.getElementById('remoteOauthFields');

        if (serverTypeRemote.checked) {
            remoteFields.style.display = 'block';
            localFields.style.display = 'none';
            pinCertOption.style.display = 'block';
            remoteAuthFields.style.display = 'grid';
            remoteHeaderFields.style.display = 'block';
            remoteOauthFields.style.display = 'block';
        } else {
            remoteFields.style.display = 'none';
            localFields.style.display = 'block';
            pinCertOption.style.display = 'none';
            remoteAuthFields.style.display = 'none';
            remoteHeaderFields.style.display = 'none';
            remoteOauthFields.style.display = 'none';
        }

        toggleCertificateFields();
        toggleServerAuthFields();
    }

    function toggleServerAuthFields() {
        const authType = document.getElementById('dialogAuthorizationType').value;
        const username = document.getElementById('dialogAuthUsername');
        const password = document.getElementById('dialogAuthPassword');
        const token = document.getElementById('dialogAuthToken');
        if (!username || !password || !token) {
            return;
        }
        if (authType === 'Basic') {
            username.style.display = 'block';
            password.style.display = 'block';
            token.style.display = 'none';
        } else if (authType === 'Bearer') {
            username.style.display = 'none';
            password.style.display = 'none';
            token.style.display = 'block';
        } else {
            username.style.display = 'none';
            password.style.display = 'none';
            token.style.display = 'none';
        }
    }

    function renderServerHeaders(headers) {
        const container = document.getElementById('serverHeadersList');
        if (!container) {
            return;
        }
        const entries = Object.entries(headers || {});
        if (entries.length === 0) {
            container.innerHTML = '<p class="muted">No custom headers.</p>';
            return;
        }
        container.innerHTML = entries.map(([name, value], index) => `
            <div class="gateway-tool-item">
                <div class="gateway-tool-details">
                    <div><strong>${escapeHtml(name)}</strong></div>
                    <span class="gateway-tool-meta">${escapeHtml(value)}</span>
                </div>
                <div class="gateway-tool-actions">
                    <button class="btn btn-secondary btn-icon" onclick="editServerHeaderRow(${index})">✏️</button>
                    <button class="btn btn-secondary btn-icon" onclick="removeServerHeaderRow(${index})">✖</button>
                </div>
            </div>
        `).join('');
    }

    function readServerHeadersFromDialog() {
        const rows = document.querySelectorAll('[data-header-row="true"]');
        if (rows.length === 0) {
            return serverHeadersDraft || {};
        }
        const headers = { ...(serverHeadersDraft || {}) };
        rows.forEach(row => {
            const name = row.querySelector('[data-header-name]')?.value?.trim();
            const value = row.querySelector('[data-header-value]')?.value ?? '';
            if (name) {
                headers[name] = value;
            }
        });
        return headers;
    }

    function addServerHeaderRow() {
        const container = document.getElementById('serverHeadersList');
        if (!container) {
            return;
        }
        if (container.querySelector('[data-header-row="true"]') === null && container.querySelector('.gateway-tool-item') === null) {
            container.innerHTML = '';
        }
        const row = document.createElement('div');
        row.className = 'gateway-tool-item';
        row.setAttribute('data-header-row', 'true');
        row.innerHTML = `
            <div class="gateway-tool-details">
                <input type="text" data-header-name placeholder="Header name">
                <input type="text" data-header-value placeholder="Header value">
            </div>
            <div class="gateway-tool-actions">
                <button class="btn btn-secondary" onclick="saveServerHeaderRow()">Save</button>
                <button class="btn btn-secondary btn-icon" onclick="removeServerHeaderRow()">✖</button>
            </div>
        `;
        container.appendChild(row);
    }

    function editServerHeaderRow(index) {
        const server = serversData[currentEditIndex];
        const headers = server?.headers || {};
        const entries = Object.entries(headers);
        const entry = entries[index];
        if (!entry) {
            return;
        }
        const container = document.getElementById('serverHeadersList');
        if (!container) {
            return;
        }
        container.innerHTML = '';
        const row = document.createElement('div');
        row.className = 'gateway-tool-item';
        row.setAttribute('data-header-row', 'true');
        row.innerHTML = `
            <div class="gateway-tool-details">
                <input type="text" data-header-name placeholder="Header name" value="${escapeHtml(entry[0])}">
                <input type="text" data-header-value placeholder="Header value" value="${escapeHtml(entry[1])}">
            </div>
            <div class="gateway-tool-actions">
                <button class="btn btn-secondary" onclick="saveServerHeaderRow()">Save</button>
                <button class="btn btn-secondary btn-icon" onclick="removeServerHeaderRow()">✖</button>
            </div>
        `;
        container.appendChild(row);
    }

    function saveServerHeaderRow() {
        const headers = serverHeadersDraft || {};
        const edited = readServerHeadersFromDialog();
        const merged = { ...headers, ...edited };
        serverHeadersDraft = merged;
        if (serversData[currentEditIndex]) {
            serversData[currentEditIndex].headers = merged;
        }
        renderServerHeaders(merged);
    }

    function removeServerHeaderRow(index) {
        const headers = serverHeadersDraft || {};
        if (typeof index === 'number') {
            const entries = Object.entries(headers);
            const entry = entries[index];
            if (entry) {
                delete headers[entry[0]];
            }
            serverHeadersDraft = headers;
            if (serversData[currentEditIndex]) {
                serversData[currentEditIndex].headers = headers;
            }
            renderServerHeaders(headers);
            return;
        }
        renderServerHeaders(serverHeadersDraft || {});
    }

    function toggleCertificateFields() {
        const protocol = document.getElementById('dialogProtocol').value;
        const pinCertOption = document.getElementById('pinCertOption');

        if (protocol !== 'HTTPS') {
            pinCertOption.style.display = 'none';
        } else {
            pinCertOption.style.display = 'block';
        }

        const pinCertYes = document.querySelector('input[name="pinCert"][value="yes"]');
        const serverTypeRemote = document.querySelector('input[name="serverType"][value="remote"]');
        const pinCertFields = document.getElementById('pinCertFields');
        const retrieveTestCertButtons = document.getElementById('retrieveTestCertButtons');
        const certDetails = document.getElementById('certDetails');

        if (pinCertYes.checked && serverTypeRemote.checked && protocol === 'HTTPS') {
            pinCertFields.style.display = 'block';
            retrieveTestCertButtons.style.display = 'flex';
            certDetails.style.display = 'block';
        } else {
            pinCertFields.style.display = 'none';
            retrieveTestCertButtons.style.display = 'none';
            certDetails.style.display = 'none';
        }
    }

    function applyHostUrlParsing() {
        const hostInput = document.getElementById('dialogHost');
        const protocolInput = document.getElementById('dialogProtocol');
        const portInput = document.getElementById('dialogPort');
        const remotePathInput = document.getElementById('dialogRemotePath');
        if (!hostInput || !protocolInput || !portInput || !remotePathInput) {
            return;
        }
        const raw = hostInput.value.trim();
        if (!raw || (!raw.startsWith('http://') && !raw.startsWith('https://'))) {
            return;
        }
        try {
            const parsed = new URL(raw);
            hostInput.value = parsed.hostname;
            protocolInput.value = parsed.protocol === 'https:' ? 'HTTPS' : 'HTTP';
            portInput.value = parsed.port || (parsed.protocol === 'https:' ? '443' : '80');
            remotePathInput.value = parsed.pathname && parsed.pathname !== '/' ? parsed.pathname : '/mcp';
            toggleTypeFields();
        } catch (error) {
            // Ignore parsing errors
        }
    }

    function toggleRetrieveButton() {
        const pinCertYes = document.querySelector('input[name="pinCert"][value="yes"]');
        const serverTypeRemote = document.querySelector('input[name="serverType"][value="remote"]');
        const retrieveTestCertButtons = document.getElementById('retrieveTestCertButtons');

        if (pinCertYes.checked && serverTypeRemote.checked) {
            retrieveTestCertButtons.style.display = 'flex';
        } else {
            retrieveTestCertButtons.style.display = 'none';
        }
    }

    document.getElementById('dialogHost').addEventListener('input', toggleRetrieveButton);
    document.getElementById('dialogHost').addEventListener('input', syncNameFromHost);
    document.getElementById('dialogHost').addEventListener('blur', applyHostUrlParsing);
    document.getElementById('dialogPort').addEventListener('input', toggleRetrieveButton);

    async function removeServer(index) {
        const server = serversData[index];
        const serverId = resolveServerId(server, index);
        if (!serverId) {
            return;
        }
        const affectedGateways = gatewaysData.filter(gateway =>
            Array.isArray(gateway?.tools) && gateway.tools.some(tool => tool?.serverId === serverId)
        );
        let message = 'Are you sure you want to remove this server?';
        if (affectedGateways.length > 0) {
            const names = affectedGateways.map(gateway => gateway?.name || gateway?.id).join(', ');
            message = `Removing this server will also remove its tools from these gateways: ${names}. Continue?`;
        }
        const confirmed = await showConfirmDialog({
            title: 'Remove server?',
            message
        });
        if (!confirmed) {
            return;
        }
        console.log('Removing server:', server);
        apiFetch(`${mcpApiUrl}/${serverId}`, {
            method: 'DELETE'
        })
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Failed to remove server');
                }
                console.log('Server removed:', server);
                serverToolStatus.delete(serverId);
                serversData.splice(index, 1);
                renderServerList();
                fetchGateways();
            })
            .catch((error) => {
                console.error('Error removing server:', error);
                showMessageDialog('Remove failed', 'An error occurred while removing the server.');
            });
    }

    async function confirmDialog() {
        const dialogName = document.getElementById('dialogName');
        const dialogHost = document.getElementById('dialogHost');
        const serverTypeRemote = document.querySelector('input[name="serverType"][value="remote"]');

        if (serverTypeRemote.checked && !dialogName.value.trim() && dialogHost.value.trim()) {
            dialogName.value = dialogHost.value.trim();
        }

        const name = dialogName.value.trim();
        if (!name) {
            showMessageDialog('Missing server name', 'Server name cannot be empty.');
            return;
        }

        const type = document.querySelector('input[name="serverType"]:checked')?.value;
        const host = document.getElementById('dialogHost').value;
        const port = document.getElementById('dialogPort').value;
        const authorizationType = document.getElementById('dialogAuthorizationType').value;
        const authUsername = document.getElementById('dialogAuthUsername').value.trim();
        const authPassword = document.getElementById('dialogAuthPassword').value;
        const authToken = document.getElementById('dialogAuthToken').value.trim();
        const path = document.getElementById('dialogPath').value;
        const remotePath = document.getElementById('dialogRemotePath').value;
        const protocol = document.getElementById('dialogProtocol').value;
        const argument = document.getElementById('dialogArgument').value;
        const pinCert = document.querySelector('input[name="pinCert"]:checked').value === 'yes';
        let certificate = null;

        if (pinCert) {
            certificate = document.getElementById('dialogServerCertificate').value.trim();
            if (!certificate) {
                showMessageDialog('Missing certificate', 'Certificate details must be retrieved before pinning.');
                return;
            }
        }

        const headers = readServerHeadersFromDialog();
        const authHeaderEntry = Object.entries(headers).find(([key]) => key.toLowerCase() === 'authorization');
        if (authHeaderEntry) {
            const value = String(authHeaderEntry[1] || '').toLowerCase();
            if (value.startsWith('basic ') || value.startsWith('bearer ')) {
                showMessageDialog('Invalid header', 'Use the Basic or Bearer auth type instead of setting Authorization headers manually.');
                return;
            }
            if (authorizationType === 'Basic' || authorizationType === 'Bearer') {
                showMessageDialog('Invalid header', 'Do not set Authorization headers manually when using Basic or Bearer auth.');
                return;
            }
        }

        if (authorizationType === 'Basic' && (!authUsername || !authPassword)) {
            showMessageDialog('Missing credentials', 'Username and password are required for Basic auth.');
            return;
        }
        if (authorizationType === 'Bearer' && !authToken) {
            showMessageDialog('Missing token', 'A bearer token is required for Bearer auth.');
            return;
        }

        const server = {
            name,
            type,
            host,
            port: Number(port),
            authorizationType,
            authUsername: authorizationType === 'Basic' ? authUsername : null,
            authPassword: authorizationType === 'Basic' ? authPassword : null,
            authToken: authorizationType === 'Bearer' ? authToken : null,
            headers,
            certificate,
            path,
            protocol,
            remotePath,
            argument
        };

        if (currentEditIndex === null) {
            await apiFetch(mcpApiUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(server)
            });
        } else {
            const serverId = serversData[currentEditIndex]?.id;
            if (!serverId) {
                showMessageDialog('Missing server id', 'Server id is missing. Please refresh the page.');
                return;
            }
            server.id = serverId;
            await apiFetch(`${mcpApiUrl}/${serverId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(server)
            });
            serversData[currentEditIndex] = server;
        }

        closeDialog();
        fetchServers();
    }

    function openConfirmationDialog() {
        showConfirmDialog({ title: 'Are you sure?', message: 'This action cannot be undone.' });
    }

    async function retrieveCertificate() {
        const host = document.getElementById('dialogHost').value;
        const port = document.getElementById('dialogPort').value;

        if (!host || !port) {
            showMessageDialog('Missing details', 'Please enter both host and port.');
            return;
        }

        try {
            const response = await apiFetch(`${certificateApiUrl}/retrieve-certificate/${host}/${port}`);
            if (!response.ok) {
                throw new Error('Failed to retrieve certificate');
            }

            const pem = await response.text();
            console.log('Retrieved PEM certificate:', pem);

            const certDetails = parseLeafCertificate(pem);
            if (certDetails) {
                document.getElementById('dialogCertCN').value = certDetails.commonName;
                document.getElementById('dialogCertNotBefore').value = certDetails.notBefore;
                document.getElementById('dialogCertNotAfter').value = certDetails.notAfter;
                document.getElementById('dialogServerCertificate').value = pem;
                document.getElementById('certDetails').style.display = 'block';
            } else {
                showMessageDialog('Certificate error', 'Failed to parse the certificate.');
            }
        } catch (error) {
            console.error('Error retrieving or parsing certificates:', error);
            showMessageDialog('Certificate error', 'Failed to retrieve or parse the certificates.');
        }
    }

    async function testCertificate() {
        const host = document.getElementById('dialogHost').value;
        const port = document.getElementById('dialogPort').value;
        const storedCert = document.getElementById('dialogServerCertificate').value;

        if (!host || !port || !storedCert) {
            showMessageDialog('Missing details', 'Please ensure host, port, and certificate are provided before testing.');
            return;
        }

        try {
            const response = await apiFetch(`${certificateApiUrl}/test-certificate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ host, port, certificate: storedCert })
            });

            if (response.ok) {
                const result = await response.text();
                showMessageDialog('Certificate test', result || 'Certificate test passed successfully!');
            } else {
                const error = await response.text();
                throw new Error(error);
            }
        } catch (error) {
            console.error('Error testing certificate:', error);
            showMessageDialog('Certificate test failed', 'Failed to test the certificate.');
        }
    }

    function parseLeafCertificate(pem) {
        try {
            const cert = new X509();
            cert.readCertPEM(pem);

            const subject = cert.getSubjectString();
            const issuer = cert.getIssuerString();
            const serialNumber = cert.getSerialNumberHex();

            const parseUTCTime = (time) => {
                const year = parseInt(time.substring(0, 2), 10);
                const fullYear = year < 50 ? 2000 + year : 1900 + year;
                const month = parseInt(time.substring(2, 4), 10) - 1;
                const day = parseInt(time.substring(4, 6), 10);
                const hour = parseInt(time.substring(6, 8), 10);
                const minute = parseInt(time.substring(8, 10), 10);
                const second = parseInt(time.substring(10, 12), 10);
                return new Date(Date.UTC(fullYear, month, day, hour, minute, second)).toISOString();
            };

            const notBefore = parseUTCTime(cert.getNotBefore());
            const notAfter = parseUTCTime(cert.getNotAfter());

            console.log('Subject:', subject);
            console.log('Issuer:', issuer);
            console.log('Serial Number:', serialNumber);
            console.log('Not Before:', notBefore);
            console.log('Not After:', notAfter);

            return { commonName: subject, notBefore, notAfter };
        } catch (error) {
            console.error('Failed to parse certificate:', error);
            return null;
        }
    }

    async function fetchGateways() {
        try {
            const response = await apiFetch(gatewayApiUrl);
            if (!response.ok) {
                throw new Error('Failed to fetch gateways');
            }

            const data = await response.json();
            gatewaysData = Array.isArray(data) ? data : [];
            renderGatewayList();
        } catch (error) {
            console.error('Error fetching gateways:', error);
            gatewaysData = [];
            renderGatewayList();
        }
    }

    function renderGatewayList() {
        const gatewayList = document.getElementById('gatewayList');
        gatewayList.innerHTML = '';

        if (!Array.isArray(gatewaysData) || gatewaysData.length === 0) {
            gatewayList.innerHTML = '<p>No gateways available.</p>';
            return;
        }

        gatewaysData.forEach((gateway, index) => {
            const gatewayItem = document.createElement('div');
            gatewayItem.className = 'gateway-item';
            const isOpen = openGatewayDetails.has(gateway.id);
            const tools = Array.isArray(gateway.tools) ? gateway.tools : [];

            gatewayItem.innerHTML = `
                <div class="gateway-header">
                    <span class="twistie-name-span">
                        <span id="gateway-twistie-${index}" onclick="toggleGatewayDetails(${index})" style="cursor: pointer;">${isOpen ? '▼' : '▶'}</span>
                        <span class="gateway-name-span">${gateway.name || 'Unnamed Gateway'}</span>
                    </span>
                    <span class="action-buttons">
                        <button id="gateway-action-${index}" class="toggle" onclick="toggleGatewayStatus(${index})">
                            ${gateway.status === 'STARTED' ? '⏹️' : '▶️'}
                        </button>
                        <button class="edit" onclick="openEditGatewayDialog(${index})">✏️</button>
                        <button class="remove" onclick="removeGateway(${index})">❌</button>
                    </span>
                </div>
                <div id="gateway-details-${index}" class="gateway-details" style="display: ${isOpen ? 'block' : 'none'};">
                    <div class="gateway-tools gateway-meta-panel">
                        <div class="gateway-meta">
                            <span>Host: ${gateway.host} • Port: ${gateway.port}</span>
                            <span class="gateway-status">${gateway.status}</span>
                        </div>
                    </div>
                    <div class="gateway-tools">
                        <div class="gateway-tools-header">
                            <span>Configured tools</span>
                            <button class="btn btn-secondary" onclick="openAddGatewayToolDialog(${index})">Add tool</button>
                        </div>
                        <div id="gateway-tools-${index}" class="gateway-tools-list"></div>
                    </div>
                </div>
            `;

            gatewayList.appendChild(gatewayItem);

            renderGatewayToolList(index, tools);
        });
    }

    function renderGatewayToolList(index, tools) {
        const container = document.getElementById(`gateway-tools-${index}`);
        if (!container) {
            return;
        }
        const list = Array.isArray(tools) ? tools : [];
        if (list.length === 0) {
            container.innerHTML = '<p class="muted">No tools selected.</p>';
            return;
        }
        const items = list.map((toolRef, toolIndex) => {
            const serverId = toolRef?.serverId;
            const server = serversData.find(item => item.id === serverId);
            const serverName = server?.name || 'Unknown server';
            const toolName = toolRef?.toolName || 'Unknown tool';
            const serverTitle = serverId || '';
            const validationSummary = getValidationSummary(toolRef);
            const tool = server?.tools?.find(item => item?.name === toolName);
            const validation = getToolValidationInfo(tool);
            return `
                <div class="gateway-tool-item">
                    <div class="gateway-tool-details">
                        <div>
                            <span class="tool-status ${validation.status}" title="${escapeHtml(validation.tooltip)}">${validation.icon}</span>
                            <span title="${escapeHtml(serverTitle)}">${serverName}</span>
                            <span class="gateway-tool-separator">::</span>
                            <span>${toolName}</span>
                        </div>
                        <span class="gateway-tool-meta">Validation: ${validationSummary}</span>
                    </div>
                    <div class="gateway-tool-actions">
                        <button class="btn btn-secondary btn-icon" onclick="openGatewayToolSettingsDialog(${index}, ${toolIndex})">⚙️</button>
                        <button class="btn btn-secondary btn-icon" onclick="removeGatewayTool(${index}, ${toolIndex})">✖</button>
                    </div>
                </div>
            `;
        }).join('');
        container.innerHTML = items;
    }

    function toggleGatewayDetails(index) {
        const gateway = gatewaysData[index];
        const toolsSection = document.getElementById(`gateway-details-${index}`);
        const twistie = document.getElementById(`gateway-twistie-${index}`);
        if (toolsSection.style.display === 'none') {
            toolsSection.style.display = 'block';
            twistie.innerText = '▼';
            if (gateway?.id) {
                openGatewayDetails.add(gateway.id);
            }
        } else {
            toolsSection.style.display = 'none';
            twistie.innerText = '▶';
            if (gateway?.id) {
                openGatewayDetails.delete(gateway.id);
            }
        }
    }

    function toggleGatewayAuthFields() {
        const authType = document.getElementById('gatewayDialogAuthType')?.value || 'NONE';
        const basicFields = document.getElementById('gatewayAuthBasicFields');
        const bearerFields = document.getElementById('gatewayAuthBearerFields');
        if (basicFields) {
            basicFields.style.display = authType === 'BASIC' ? 'grid' : 'none';
        }
        if (bearerFields) {
            bearerFields.style.display = authType === 'BEARER' ? 'grid' : 'none';
        }
    }

    function toggleGatewayStatus(index) {
        const gateway = gatewaysData[index];
        const action = gateway.status === 'STARTED' ? 'stop' : 'start';

        apiFetch(`${gatewayApiUrl}/${gateway.id}/${action}`, { method: 'POST' })
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Failed to ${action} gateway`);
                }
                return response.json();
            })
            .then(updatedGateway => {
                gatewaysData[index] = updatedGateway;
                renderGatewayList();
            })
            .catch(error => console.error('Error toggling gateway status:', error));
    }

    function renderGatewayDialogToolList() {
        const container = document.getElementById('gatewayDialogTools');
        if (!container) {
            return;
        }
        const tools = Array.isArray(gatewayDialogTools) ? gatewayDialogTools : [];
        if (tools.length === 0) {
            container.innerHTML = '<p class="muted">No tools added yet.</p>';
            return;
        }
        const items = tools.map((toolRef, index) => {
            const server = serversData.find(item => item.id === toolRef.serverId);
            const serverName = escapeHtml(server?.name || 'Unknown server');
            const toolName = escapeHtml(toolRef.toolName || 'Unknown tool');
            const validationSummary = getValidationSummary(toolRef);
            return `
                <div class="gateway-tool-item">
                    <div class="gateway-tool-details">
                        <div class="gateway-tool-title">
                            <span>${serverName}</span>
                            <span class="gateway-tool-separator">::</span>
                            <span>${toolName}</span>
                        </div>
                        <span class="gateway-tool-meta">Validation: ${validationSummary}</span>
                    </div>
                    <div class="gateway-tool-actions">
                        <button class="btn btn-secondary btn-icon" onclick="removeGatewayDialogTool(${index})">✖</button>
                    </div>
                </div>
            `;
        }).join('');
        container.innerHTML = items;
    }

    function removeGatewayDialogTool(index) {
        if (!Array.isArray(gatewayDialogTools)) {
            gatewayDialogTools = [];
        }
        gatewayDialogTools.splice(index, 1);
        renderGatewayDialogToolList();
    }

    function openAddGatewayToolDialogFromDialog() {
        gatewayToolTarget = { type: 'dialog' };
        openAddGatewayToolDialog({ type: 'dialog' });
    }

    function openCloneGatewayDialog(index) {
        const server = serversData[index];
        if (!server) {
            return;
        }
        openAddGatewayDialog();
        const serverId = server?.id;
        if (!serverId) {
            return;
        }
        const tools = Array.isArray(server?.tools) ? server.tools : [];
        gatewayDialogTools = tools.map(tool => ({
            serverId,
            toolName: tool?.name,
            validationMode: 'PER_INVOCATION',
            validationPeriodSeconds: null
        })).filter(tool => tool.serverId && tool.toolName);
        renderGatewayDialogToolList();
    }

    function openAddGatewayDialog() {
        currentEditIndex = null;
        document.getElementById('gatewayDialogTitle').innerText = 'Add Gateway';
        document.getElementById('gatewayDialogName').value = '';
        document.getElementById('gatewayDialogHost').value = '';
        document.getElementById('gatewayDialogPort').value = '';
        document.getElementById('gatewayDialogAuthType').value = 'NONE';
        document.getElementById('gatewayDialogAuthUsername').value = '';
        document.getElementById('gatewayDialogAuthPassword').value = '';
        document.getElementById('gatewayDialogAuthToken').value = '';
        toggleGatewayAuthFields();

        gatewayDialogTools = [];
        renderGatewayDialogToolList();

        document.getElementById('addEditGatewayDialog').style.display = 'flex';
    }

    function openEditGatewayDialog(index) {
        const gateway = gatewaysData[index];
        currentEditIndex = index;
        document.getElementById('gatewayDialogTitle').innerText = 'Edit Gateway';
        document.getElementById('gatewayDialogName').value = gateway.name || '';
        document.getElementById('gatewayDialogHost').value = gateway.host || '';
        document.getElementById('gatewayDialogPort').value = gateway.port || '';
        document.getElementById('gatewayDialogAuthType').value = gateway.authType || 'NONE';
        document.getElementById('gatewayDialogAuthUsername').value = gateway.authUsername || '';
        document.getElementById('gatewayDialogAuthPassword').value = gateway.authPassword || '';
        document.getElementById('gatewayDialogAuthToken').value = gateway.authToken || '';
        toggleGatewayAuthFields();

        gatewayDialogTools = Array.isArray(gateway.tools)
            ? gateway.tools.map(tool => ({ ...tool }))
            : [];
        renderGatewayDialogToolList();

        document.getElementById('addEditGatewayDialog').style.display = 'flex';
    }

    function openAddGatewayToolDialog(target) {
        if (typeof target === 'number') {
            gatewayToolTarget = { type: 'gateway', index: target };
            currentEditIndex = target;
        } else if (target?.type === 'dialog') {
            gatewayToolTarget = { type: 'dialog' };
        } else if (target?.type === 'gateway' && typeof target.index === 'number') {
            gatewayToolTarget = { type: 'gateway', index: target.index };
            currentEditIndex = target.index;
        } else {
            gatewayToolTarget = { type: 'gateway', index: currentEditIndex };
        }
        const picker = document.getElementById('gatewayToolList');
        picker.innerHTML = '';

        const defaultMode = 'PER_INVOCATION';
        const defaultPeriod = 300;
        const serverSections = serversData.map((server, serverIndex) => {
            const serverId = server?.id || '';
            const serverName = server?.name || `Server ${serverIndex + 1}`;
            const serverLabel = serverId ? `${serverName} (${serverId})` : serverName;
            const tools = Array.isArray(server?.tools) ? server.tools : [];
            const toolItems = tools.map((tool, toolIndex) => {
                const toolName = tool?.name || `Tool ${toolIndex + 1}`;
                const checkboxId = `gateway-tool-${serverIndex}-${toolIndex}`;
                return `
                    <div class="gateway-tool-option">
                        <label for="${checkboxId}">
                            <input id="${checkboxId}" type="checkbox" data-server-id="${escapeHtml(serverId)}" data-server-name="${escapeHtml(serverName)}" data-tool="${escapeHtml(toolName)}">
                            <span>${toolName}</span>
                        </label>
                        ${buildValidationControlsHtml(defaultMode, defaultPeriod)}
                    </div>
                `;
            }).join('');

            return `
                <div class="gateway-tool-server">
                    <div class="gateway-tool-server-header" onclick="toggleGatewayToolServer(${serverIndex})">
                        <span id="gateway-tool-twistie-${serverIndex}">▶</span>
                        <span>${serverLabel}</span>
                    </div>
                    <div id="gateway-tool-body-${serverIndex}" class="gateway-tool-server-body" style="display: none;">
                        ${toolItems || '<p class="muted">No tools stored for this server.</p>'}
                    </div>
                </div>
            `;
        }).join('');

        picker.innerHTML = serverSections || '<p class="muted">No tools available. Read tools from servers first.</p>';
        document.querySelectorAll('.gateway-tool-option').forEach(option => {
            const modeEl = option.querySelector('.tool-validation-mode');
            const periodEl = option.querySelector('.tool-validation-period');
            if (modeEl && defaultMode) {
                modeEl.value = defaultMode;
            }
            if (periodEl) {
                periodEl.value = ['30', '300', '1800', '7200', '43200', '86400'].includes(String(defaultPeriod))
                    ? String(defaultPeriod)
                    : 'custom';
            }
            updateToolValidationRow(option);
            modeEl?.addEventListener('change', () => updateToolValidationRow(option));
            periodEl?.addEventListener('change', () => updateToolValidationRow(option));
        });
        document.getElementById('addGatewayToolDialog').style.display = 'flex';
    }

    function closeGatewayToolDialog() {
        document.getElementById('addGatewayToolDialog').style.display = 'none';
        gatewayToolTarget = null;
    }

    function confirmGatewayToolDialog() {
        const target = gatewayToolTarget || { type: 'gateway', index: currentEditIndex };
        const gateway = target.type === 'gateway' ? gatewaysData[target.index] : null;
        if (target.type === 'gateway' && !gateway) {
            closeGatewayToolDialog();
            return;
        }

        const checked = Array.from(document.querySelectorAll('#gatewayToolList input[type="checkbox"]:checked'));
        if (checked.length === 0) {
            closeGatewayToolDialog();
            return;
        }


        const tools = target.type === 'dialog'
            ? (Array.isArray(gatewayDialogTools) ? gatewayDialogTools : [])
            : (Array.isArray(gateway.tools) ? gateway.tools : []);
        checked.forEach(input => {
            const serverId = input.getAttribute('data-server-id');
            const toolName = input.getAttribute('data-tool');
            if (!serverId || !toolName) {
                return;
            }
            const option = input.closest('.gateway-tool-option');
            const validationSettings = option ? getValidationSettingsFromRow(option) : null;
            if (!validationSettings) {
                return;
            }
            const exists = tools.some(t => t.serverId === serverId && t.toolName === toolName);
            if (!exists) {
                tools.push({
                    serverId,
                    toolName,
                    validationMode: validationSettings.mode,
                    validationPeriodSeconds: validationSettings.periodSeconds
                });
            }
        });
        if (target.type === 'dialog') {
            gatewayDialogTools = tools;
            renderGatewayDialogToolList();
            closeGatewayToolDialog();
            return;
        }

        gateway.tools = tools;
        saveGatewayTools(gateway)
            .then(() => {
                renderGatewayToolList(target.index, tools);
                closeGatewayToolDialog();
            })
            .catch(() => closeGatewayToolDialog());
    }

    function openGatewayToolSettingsDialog(gatewayIndex, toolIndex) {
        const gateway = gatewaysData[gatewayIndex];
        const toolRef = gateway?.tools?.[toolIndex];
        if (!gateway || !toolRef) {
            return;
        }
        currentGatewayToolEdit = { gatewayIndex, toolIndex };
        const server = serversData.find(item => item.id === toolRef.serverId);
        const serverName = server?.name || 'Unknown server';
        const toolName = toolRef.toolName || 'Unknown tool';
        const titleEl = document.getElementById('gatewayToolSettingsTitle');
        if (titleEl) {
            titleEl.textContent = `${serverName} :: ${toolName}`;
        }
        setValidationControlsFromToolRef(toolRef,
            'gatewayToolSettingsMode',
            'gatewayToolSettingsPeriod',
            'gatewayToolSettingsCustomSeconds',
            'gatewayToolSettingsPeriodRow');
        document.getElementById('gatewayToolSettingsDialog').style.display = 'flex';
    }

    function closeGatewayToolSettingsDialog() {
        document.getElementById('gatewayToolSettingsDialog').style.display = 'none';
        currentGatewayToolEdit = null;
    }

    function confirmGatewayToolSettingsDialog() {
        if (!currentGatewayToolEdit) {
            closeGatewayToolSettingsDialog();
            return;
        }
        const { gatewayIndex, toolIndex } = currentGatewayToolEdit;
        const gateway = gatewaysData[gatewayIndex];
        const toolRef = gateway?.tools?.[toolIndex];
        if (!gateway || !toolRef) {
            closeGatewayToolSettingsDialog();
            return;
        }

        const validationSettings = getValidationSettingsFromControls(
            'gatewayToolSettingsMode',
            'gatewayToolSettingsPeriod',
            'gatewayToolSettingsCustomSeconds'
        );
        if (!validationSettings) {
            return;
        }

        toolRef.validationMode = validationSettings.mode;
        toolRef.validationPeriodSeconds = validationSettings.periodSeconds;

        saveGatewayTools(gateway)
            .then(() => {
                renderGatewayToolList(gatewayIndex, gateway.tools);
                closeGatewayToolSettingsDialog();
            })
            .catch(() => closeGatewayToolSettingsDialog());
    }

    function toggleGatewayToolServer(serverIndex) {
        const body = document.getElementById(`gateway-tool-body-${serverIndex}`);
        const twistie = document.getElementById(`gateway-tool-twistie-${serverIndex}`);
        if (!body || !twistie) {
            return;
        }
        if (body.style.display === 'none') {
            body.style.display = 'block';
            twistie.textContent = '▼';
        } else {
            body.style.display = 'none';
            twistie.textContent = '▶';
        }
    }

    function removeGatewayTool(gatewayIndex, toolIndex) {
        const gateway = gatewaysData[gatewayIndex];
        if (!gateway) {
            return;
        }
        const tools = Array.isArray(gateway.tools) ? gateway.tools : [];
        tools.splice(toolIndex, 1);
        gateway.tools = tools;
        saveGatewayTools(gateway)
            .then(() => renderGatewayToolList(gatewayIndex, tools))
            .catch(() => renderGatewayToolList(gatewayIndex, tools));
    }

    function saveGatewayTools(gateway) {
        return apiFetch(`${gatewayApiUrl}/${gateway.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: gateway.id,
                name: gateway.name,
                host: gateway.host,
                port: gateway.port,
                status: gateway.status,
                authType: gateway.authType || 'NONE',
                authUsername: gateway.authUsername || null,
                authPassword: gateway.authPassword || null,
                authToken: gateway.authToken || null,
                tools: gateway.tools
            })
        }).then(response => {
            if (!response.ok) {
                throw new Error('Failed to update gateway tools');
            }
            return response.json();
        }).then(updated => {
            const idx = gatewaysData.findIndex(item => item.id === updated.id);
            if (idx >= 0) {
                gatewaysData[idx] = updated;
            }
            return updated;
        });
    }

    async function confirmGatewayDialog() {
        const name = document.getElementById('gatewayDialogName').value.trim();
        const host = document.getElementById('gatewayDialogHost').value.trim();
        const port = parseInt(document.getElementById('gatewayDialogPort').value, 10);
        const authType = document.getElementById('gatewayDialogAuthType').value;
        const authUsername = document.getElementById('gatewayDialogAuthUsername').value.trim();
        const authPassword = document.getElementById('gatewayDialogAuthPassword').value;
        const authToken = document.getElementById('gatewayDialogAuthToken').value.trim();

        if (!name || !host || isNaN(port)) {
            showMessageDialog('Missing details', 'Please fill in all fields correctly.');
            return;
        }

        if (authType === 'BASIC' && (!authUsername || !authPassword)) {
            showMessageDialog('Missing details', 'Username and password are required for Basic authentication.');
            return;
        }
        if (authType === 'BEARER' && !authToken) {
            showMessageDialog('Missing details', 'A bearer token is required for Bearer authentication.');
            return;
        }

        const existing = currentEditIndex !== null ? gatewaysData[currentEditIndex] : null;
        const status = existing?.status || 'STOPPED';
        const tools = Array.isArray(gatewayDialogTools) ? gatewayDialogTools : [];
        const gateway = {
            name,
            host,
            port,
            status,
            tools,
            authType,
            authUsername: authType === 'BASIC' ? authUsername : null,
            authPassword: authType === 'BASIC' ? authPassword : null,
            authToken: authType === 'BEARER' ? authToken : null
        };

        if (currentEditIndex === null) {
            await apiFetch(gatewayApiUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(gateway)
            });
        } else {
            const gatewayId = gatewaysData[currentEditIndex]?.id;
            await apiFetch(`${gatewayApiUrl}/${gatewayId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(gateway)
            });
            gatewaysData[currentEditIndex] = { ...gatewaysData[currentEditIndex], ...gateway };
        }

        closeGatewayDialog();
        fetchGateways();
    }

    function closeGatewayDialog() {
        document.getElementById('addEditGatewayDialog').style.display = 'none';
        gatewayToolTarget = null;
    }

    async function removeGateway(index) {
        const confirmed = await showConfirmDialog({
            title: 'Remove gateway?',
            message: 'Are you sure you want to remove this gateway?'
        });
        if (!confirmed) {
            return;
        }

        const gw = gatewaysData[index];
        console.log('Removing gateway:', gw);

        apiFetch(`${gatewayApiUrl}/${gw.id}`, {
            method: 'DELETE'
        })
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Failed to remove gateway');
                }
                console.log('Gateway removed:', gw);
                gatewaysData.splice(index, 1);
                renderGatewayList();
            })
            .catch((error) => {
                console.error('Error removing gateway:', error);
                showMessageDialog('Remove failed', 'An error occurred while removing the gateway.');
            });
    }

    window.removeServer = removeServer;
    window.fetchServers = fetchServers;
    window.openEditDialog = openEditDialog;
    window.openAddDialog = openAddDialog;
    window.confirmDialog = confirmDialog;
    window.closeDialog = closeDialog;
    window.toggleTypeFields = toggleTypeFields;
    window.toggleServerAuthFields = toggleServerAuthFields;
    window.toggleCertificateFields = toggleCertificateFields;
    window.retrieveCertificate = retrieveCertificate;
    window.testCertificate = testCertificate;
    window.addServerHeaderRow = addServerHeaderRow;
    window.editServerHeaderRow = editServerHeaderRow;
    window.saveServerHeaderRow = saveServerHeaderRow;
    window.removeServerHeaderRow = removeServerHeaderRow;
    window.confirmDialogOk = confirmDialogOk;
    window.confirmDialogCancel = confirmDialogCancel;
    window.closeMessageDialog = closeMessageDialog;
    window.toggleTools = toggleTools;

    window.toggleGatewayDetails = toggleGatewayDetails;
    window.toggleGatewayStatus = toggleGatewayStatus;
    window.openAddGatewayToolDialog = openAddGatewayToolDialog;
    window.openAddGatewayToolDialogFromDialog = openAddGatewayToolDialogFromDialog;
    window.openCloneGatewayDialog = openCloneGatewayDialog;
    window.confirmGatewayToolDialog = confirmGatewayToolDialog;
    window.closeGatewayToolDialog = closeGatewayToolDialog;
    window.openGatewayToolSettingsDialog = openGatewayToolSettingsDialog;
    window.confirmGatewayToolSettingsDialog = confirmGatewayToolSettingsDialog;
    window.closeGatewayToolSettingsDialog = closeGatewayToolSettingsDialog;
    window.updateGatewayToolValidationControls = updateGatewayToolValidationControls;
    window.removeGatewayTool = removeGatewayTool;
    window.removeGatewayDialogTool = removeGatewayDialogTool;
    window.toggleGatewayToolServer = toggleGatewayToolServer;

    window.openEditGatewayDialog = openEditGatewayDialog;
    window.openAddGatewayDialog = openAddGatewayDialog;
    window.confirmGatewayDialog = confirmGatewayDialog;
    window.closeGatewayDialog = closeGatewayDialog;
    window.toggleGatewayAuthFields = toggleGatewayAuthFields;
    window.removeGateway = removeGateway;
    window.getMCPDetails = getMCPDetails;
    window.confirmLogin = confirmLogin;
    window.logout = logout;
    window.openAdminSettingsDialog = openAdminSettingsDialog;
    window.closeAdminSettingsDialog = closeAdminSettingsDialog;
    window.confirmAdminSettings = confirmAdminSettings;

    toggleTypeFields();
    if (authHeader) {
        apiFetch(`${appAuthUrl}/check`)
            .then(() => {
                hideLoginDialog();
                fetchServers();
                fetchGateways();
            })
            .catch(() => showLoginDialog());
    } else {
        showLoginDialog();
    }
});