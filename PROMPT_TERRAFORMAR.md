# Project Core Context & Rules (Cursor Rules)
You are an expert Java and Web developer. We are building a local network Master-Slave file synchronization application.

### Strict Constraints:
1. **Tech Stack**: Java (Backend, embedded server like Spring Boot) + HTML/JS/CSS (Frontend using Tailwind CSS via CDN and Vanilla JS or Alpine.js). No complex Node.js build steps for the frontend.
2. **Environment**: OS-Agnostic (Linux and Windows compatibility required).
3. **Language Rules**: 
   - All source code (variables, classes, methods, configurations) and internal comments MUST be written in ENGLISH.
   - All User Interface text (HTML labels, buttons, table headers, alerts) MUST be written in PORTUGUESE.
4. **Formatting**: ABSOLUTELY NO emoticons or emojis are allowed in the code, comments, or UI.

---

### Phase 1: Core App & OS Integration
Execute the following task:
Write the base Java application using an embedded web server. The application must:
1. Start on a dynamic or configurable local port.
2. Contain a cross-platform Java utility (using `java.awt.Desktop` or OS-specific commands) to automatically open the default web browser pointing to `http://localhost:<port>` immediately after the server starts.
3. Create a utility class to retrieve the host machine's Name and IPv4 address reliably across both Linux and Windows environments.

---

### Phase 2: Frontend - Role Selection
Execute the following task:
Create the initial HTML/JS frontend page (`index.html`) for the application setup. 
1. Use Tailwind CSS for styling.
2. The UI must present two clear options for the user to select the machine's role: "Definir como Principal (Master)" or "Definir como Secundário (Slave)". 
3. When the user clicks an option, the frontend sends a REST call to the backend to persist this configuration state and redirects to the appropriate dashboard.

---

### Phase 3: Frontend - Master Dashboard UI
Execute the following task:
Create the Master Dashboard HTML/CSS/JS frontend.
1. **Header**: Top container with a "LOGO" placeholder.
2. **Tabs**: Two navigation tabs: "Dispositivos" (active) and "Pastas Sincronizadas".
3. **Main Device Card**: Displays "Nome da Máquina" (readonly input) and "Endereço IP" (readonly input). Values populated via backend API.
4. **Secondary Devices Card**: Contains an "Atualizar" button aligned right. Below it, a data table with columns: "Nome", "Endereço IP", "Status" (online, offline, pausado), and "Ação".
5. **Table Actions**: The action column renders buttons based on status: "Pausar" and "Desconectar" if online; "Reativar" and "Desconectar" if paused.
Ensure JS polls or listens to the backend for real-time status updates.

---

### Phase 4: Backend - Master/Slave Networking
Execute the following task:
Implement the communication protocol in the Java backend. 
1. **Master Role**: Expose REST endpoints allowing Slaves to register (sending Name and IP). Implement a heartbeat/ping mechanism to track if Slaves are online/offline. Expose endpoints for UI actions (Pause, Reactivate, Disconnect a Slave).
2. **Slave Role**: Implement a scheduled task that continuously pings the Master IP with its status.

---

### Phase 5: Frontend - Master Folders Tab
Execute the following task:
Create the "Pastas Sincronizadas" tab UI for the Master Dashboard. 
1. **New Folder Form**: Readonly "Guid" field (auto-populated by frontend/backend), "Endereço da pasta" input, "Procurar" button (directory selection), and "Salvar" button.
2. **Behavior**: Upon successful save via backend REST call, generate/fetch a new UUID for the Guid field and clear the address field.
3. **Data Table**: Columns: "Guid", "Endereço", and "Ação". 
4. **Actions**: "Excluir" button to send a delete request to the backend and remove the row.

---

### Phase 6: Frontend - Slave Dashboard UI
Execute the following task:
Create the Slave Dashboard frontend for the "Dispositivos" tab. 
1. **Header & Tabs**: Same header, tabs: "Dispositivos" (active) and "Pastas Sincronizadas".
2. **Main Section**: Simple card displaying "Nome da Máquina" (local), "Endereço IP" (local), and "Máquina Principal" (Master Name and IP). 
3. **Behavior**: All fields are strictly read-only and populated on load via API. No action buttons.

---

### Phase 7: Frontend - Slave Folders Tab
Execute the following task:
Create the "Pastas Sincronizadas" tab UI for the Slave Dashboard.
1. **Data Table**: Columns are "Guid", "Endereço Remoto", and "Endereço local".
2. **Master Data**: "Guid" and "Endereço Remoto" are read-only, fetched from Master's registry via backend.
3. **Local Mapping**: 
   - If no local folder is mapped: Display text input and a "Procurar" button to select a local directory. Save this mapping to the local backend.
   - If mapped: Display the local path and an "Excluir" button to unlink the mapping.
4. **Rule Check**: The backend must only sync folders that have a local mapping.

---

### Phase 8: Backend - WatchService & WebSockets
Execute the following task:
Implement the synchronization signaling engine.
1. **File Watcher**: Use `java.nio.file.WatchService` to monitor mapped local directories for CREATE, MODIFY, and DELETE events.
2. **WebSocket Hub**: Configure a WebSocket Server on the Master node. Slave nodes connect as clients.
3. **Payload**: JSON format with `guid`, `relativePath` (strictly relative to the root mapped folder), `action`, and `originIp`.
4. **Echo Cancellation**: When a node receives a WebSocket event, it must verify `originIp`. If it matches its own IP, discard the event to prevent loops.

---

### Phase 9: Backend - File Transfer & Recovery
Execute the following task:
Implement REST controllers for physical file transfer and offline recovery.
1. **File I/O**: Endpoints on Master and Slaves for multipart file uploads (`POST /api/files/upload`) and downloads (`GET /api/files/download`) using `guid` and `relativePath`.
2. **Delete Endpoint**: `DELETE /api/files/delete` based on `guid` and `relativePath`.
3. **State Recovery**: Master endpoint (`GET /api/sync/state?guid={guid}`) returning a flat list of relative paths and last modified hashes/timestamps.
4. **Reconnection**: When a Slave comes online, it fetches the state recovery list, compares it with its local directory, and triggers downloads/uploads for missed events. Ensure streams are used to avoid memory overflow with large files.