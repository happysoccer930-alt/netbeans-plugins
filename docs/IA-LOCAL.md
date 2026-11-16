# IA local offline (DeepSetk)

Todo el entorno de IA de este repo funciona **sin internet**, contra un servidor
local compatible con OpenAI.

## Servidor

| Dato | Valor |
|---|---|
| Motor | Bionic (LM Studio) |
| URL | `http://127.0.0.1:1235/v1` |
| Modelo | `qwen2.5-coder-1.5b-instruct` (GGUF **Q4_K_M**, 0.92 GB) |
| Embeddings | `text-embedding-nomic-embed-text-v1.5` (768 dim) |
| Contexto | 4096 tokens, 1 slot, CPU puro |
| Rendimiento medido | ~15 tok/s (CPU, sin GPU) |

## Clientes

| IDE | Extension | Uso |
|---|---|---|
| VS Code | `continue.continue` | Tab acepta, `Ctrl+L` chat, `Ctrl+I` editar |
| VS Code | `deepsetk.ia-local-deepsetk` | botón `⚡ IA local` en la barra de estado |
| NetBeans 31 | `modules/ai-errors` | sugerencias fantasma, “Explicar error con IA” |
| Visual Studio | LM Local 3.2.8 | chat y sugerencias inline |

## Configuraciones incluidas

- **VS Code / Continue**: `vscode/continue-config.yaml` → copiar a `%USERPROFILE%\.continue\config.yaml`
- **VS Code / extensión del botón**: `vscode/ia-local/` (código + `.vsix` listo para instalar)
- **NetBeans**: el módulo `modules/ai-errors` detecta solo el puerto 1235
- **Visual Studio**: LM Local → Base URL `http://127.0.0.1:1235/v1`

## Diagrama

`docs/ia-local-arquitectura.html` (abrir en el navegador; tiene exportación a PNG/PDF).

## Notas de rendimiento (equipo de 12 GB, sin GPU dedicada)

- Un solo modelo cargado a la vez: cambiar de modelo cuesta ~20 s de recarga.
- Bajar el contexto de 32768 a 4096 liberó ~1.5 GB de RAM.
- GPU Offload a 0 (la iGPU Intel UHD es más lenta que la CPU).
- Modelo Q4_K_M en vez de Q8_0: ×3 más rápido (5.3 → 15.8 tok/s).
