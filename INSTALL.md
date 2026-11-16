# Instalar todo en un PC nuevo

Guía de puesta a punto: **plugins de NetBeans + IA local offline + VS Code + Visual Studio**.
Probado con perfiles vacíos (userdir de NetBeans limpio y VS Code sin extensiones).

## Requisitos

| Cosa | Versión probada |
|---|---|
| Windows | 10 / 11 (x64) |
| Apache NetBeans | 31 (JDK 17+ incluido) |
| JDK para compilar | 17 o superior |
| Maven | el que trae NetBeans (`netbeans/java/maven/bin/mvn.cmd`) |
| VS Code | 1.146+ |
| Visual Studio | 2022 17.8+ o 2026 (opcional) |
| Servidor LLM local | Bionic / LM Studio / Ollama / llama.cpp |

---

## 1. Plugins de NetBeans

### Opción A — instalar los `.nbm` (recomendada)

1. Descargar los `.nbm` de `dist/` (o compilarlos, ver abajo).
2. NetBeans → **Tools → Plugins → Downloaded → Add Plugins…**
3. Seleccionar todos los `.nbm` → **Install** → reiniciar.

> ⚠️ Copiar solo los `.jar` a `%APPDATA%\NetBeans\31\modules` **no funciona** en un
> perfil nuevo: NetBeans no los activa. Hace falta el `.nbm` o el XML de
> `config\Modules\` (ver `netbeans-themes/README.md`).

### Opción B — compilar

```bash
cd netbeans-plugins
mvn -B package
```

Los `.nbm` quedan en `modules/<modulo>/target/nbm/`.

### Activar el tema

**Tools → Aplicar tema Dark** (o *Hacker Green*), aceptar el reinicio.

Comprobación: fondo negro (`#000000`), menús negros, selección gris `#353535`.

---

## 2. Servidor de IA local

Instalar **Bionic** (LM Studio) o cualquier servidor compatible con OpenAI y
cargar un modelo de código.

### Modelo recomendado

`qwen2.5-coder-1.5b-instruct` en **GGUF Q4_K_M** (0.92 GB).

### Ajustes de carga (equipo de 12 GB, sin GPU dedicada)

| Ajuste | Valor | Por qué |
|---|---|---|
| Context Length | **4096** | libera ~1.5 GB de RAM |
| Max Concurrent Predictions | **1** | multiplica la caché KV si > 1 |
| GPU Offload | **0** | la iGPU Intel es más lenta que la CPU |
| Offload KV Cache to GPU | **off** | idem |
| Reasoning Budget | **off** | el modelo no razona; gasta tiempo |
| Flash Attention | on | por defecto |

### Activar el servidor

- LM Studio / Bionic: pestaña **Developer** → *Start Server* (puerto **1234** o **1235**).
- Ollama: `ollama serve` (puerto **11434**).
- llama.cpp: `llama-server -m modelo.gguf --port 8080`.

Comprobación:

```bash
curl http://127.0.0.1:1235/v1/models
```

Debe devolver el id del modelo. El plugin de NetBeans autodetecta
**1235, 1234, 11434 y 8080**, así que cualquier puerto de esos sirve.

### Si tu programa solo acepta el puerto 1234

Algunos clientes (por ejemplo la extensión de Visual Studio) usan el 1234 fijo.
Puedes redirigir el 1234 al 1235 con una regla de Windows (una vez, como
administrador):

```bat
netsh interface portproxy add v4tov4 listenport=1234 listenaddress=127.0.0.1 connectport=1235 connectaddress=127.0.0.1
```

Comprobar que funciona:

```bash
curl http://127.0.0.1:1234/v1/models
```

Para quitarla:

```bat
netsh interface portproxy delete v4tov4 listenport=1234 listenaddress=127.0.0.1
```

---

## 3. VS Code

1. Instalar la extensión **Continue**:
 ```bash
 code --install-extension continue.continue
 ```
2. Copiar la configuración (ajustar puerto/modelo si hace falta):
 ```bash
 copy vscode\continue-config.yaml %USERPROFILE%\.continue\config.yaml
 ```
3. Instalar el botón de la barra de estado:
 ```bash
 code --install-extension vscode\ia-local\ia-local-deepsetk-1.0.0.vsix
 ```
4. Añadir a `settings.json` (si no están):
 ```json
 "continue.enableTabAutocomplete": true,
 "continue.telemetryEnabled": false,
 "editor.inlineSuggest.enabled": true
 ```

Comprobación: abajo a la derecha aparece `⚡ IA local ON`; al escribir código
sale una sugerencia en gris y **Tab** la acepta.

> ⚠️ Si al abrir la carpeta VS Code muestra **“Restricted Mode”**, las extensiones
> no se activan (ni Continue ni el botón). Pulsa **Trust** en el aviso o
> `Ctrl+Shift+P → Workspaces: Manage Workspace Trust → Trust`.

---

## 4. Visual Studio (opcional)

1. Instalar **LM Local – AI assistant** desde el Marketplace (VSIX).
2. **Extensions → LM Local → ⋯ → Settings…**
3. Provider: *LM Studio* · Base URL: `http://127.0.0.1:1235/v1` · Modelo:
 `qwen2.5-coder-1.5b-instruct` → **Test**.

---

## 5. Lista de comprobación

- [ ] `curl http://127.0.0.1:1235/v1/models` devuelve el modelo
- [ ] NetBeans: **Tools** muestra *Aplicar tema*, *Sugerencias fantasma*,
 *Explicar error con IA*, *Configurar LLM local*
- [ ] NetBeans: el tema se ve negro tras reiniciar
- [ ] NetBeans: `Ctrl+Alt+Espacio` genera una sugerencia en el editor
- [ ] VS Code: `⚡ IA local ON` en la barra de estado
- [ ] VS Code: al escribir, sugerencia gris + **Tab**
- [ ] Visual Studio: *Test* del proveedor responde OK
- [ ] RAM: el proceso del modelo (`llama-server`) ronda **1.2 GB**

---

## 6. Problemas típicos

| Síntoma | Causa / solución |
|---|---|
| Los plugins no aparecen en NetBeans | Se copiaron `.jar` sin `.nbm` ni XML en `config\Modules` |
| “No hay IA local disponible” | El servidor no está abierto o el modelo no está cargado |
| El puerto 1235 no responde | Bionic/LM Studio cerrado, o el servidor en otro puerto (probar 1234/11434/8080) |
| Respuestas lentísimas (3 tok/s) | GPU Offload activo en iGPU, contexto 32768 o modelo Q8 → aplicar los ajustes del punto 2 |
| El tema se ve a medias | Reiniciar NetBeans (los colores de pestañas se cachean al abrir ventanas) |
| Texto con bordes azules | Añadir `-J-Dawt.useSystemAAFontSettings=gasp` en `netbeans.conf` |
