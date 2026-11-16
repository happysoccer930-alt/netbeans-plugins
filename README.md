# DeepSetk · Plugins para NetBeans

Suite de plugins para **Apache NetBeans 31**: temas oscuros, IA local sin internet,
reproductor de música, generador de CRUD y gestor de plugins.

Todo funciona en Windows con el JDK que ya trae NetBeans.

---

## Qué incluye

| Plugin | Qué hace |
|---|---|
| **DeepSetk Dark** | Tema negro puro, sin azules. Selección gris. |
| **DeepSetk Hacker Green** | Tema negro con verde neón y números de línea verdes. |
| **Cyberpunk** | Estilo negro y azul, con splash propio. |
| **Soft Dark** | Tema suave de bajo contraste para leer cómodo. |
| **IA local** | Sugerencias mientras escribes y explicación de errores, sin internet. |
| **Tutor** | Chat para aprender con tu propio código. |
| **Generador CRUD** | Crea Entity, DAO, Service y SQL desde un `CREATE TABLE`. |
| **Música** | Reproductor dentro del IDE (MP3, FLAC, OGG, WAV). |
| **YouTube** | Reproductor de YouTube en una ventana del IDE. |
| **Gestor de plugins** | Activa y desactiva módulos de NetBeans con búsqueda. |

---

## Empezar en 3 pasos

1. **Instalar los plugins**
   NetBeans → **Herramientas → Plugins → Descargados → Agregar plugins…**
   Elige los `.nbm` de la carpeta `dist/` → **Instalar** → reinicia.

2. **Elegir el tema**
   **Herramientas → Aplicar tema DeepSetk Dark** (o *Hacker Green*).
   Acepta el reinicio.

3. **Encender la IA local**
   Doble clic en el acceso directo **“Iniciar IA + NetBeans”**, o abre Bionic
   y pulsa *Start Server*. Después, en NetBeans: **Herramientas → Sugerencias fantasma (Tab)**.

Guía completa para otro PC: **[INSTALL.md](INSTALL.md)**

---

## Capturas

| | |
|---|---|
| Menú con todas las opciones | [01](docs/img/01-menu-herramientas.png) |
| Tema oscuro aplicado | [02](docs/img/02-tema-dark.png) |
| Editor y árbol de proyectos | [03](docs/img/03-editor.png) |
| Diálogos de NetBeans | [04](docs/img/04-dialogo.png) |
| Cómo funciona la IA | [05](docs/img/05-arquitectura-ia.png) |

Galería con explicaciones: **[docs/CAPTURAS.md](docs/CAPTURAS.md)**

---

## La IA local

Funciona con cualquier servidor compatible con OpenAI, **sin internet**.
El plugin busca solo en los puertos **1235, 1234, 11434 y 8080**.

Configuración recomendada (probada en un equipo de 12 GB sin tarjeta gráfica):

| Ajuste | Valor |
|---|---|
| Modelo | `qwen2.5-coder-1.5b-instruct` (Q4_K_M, 0.92 GB) |
| Contexto | 4096 |
| Sesiones a la vez | 1 |
| GPU Offload | 0 (solo procesador) |
| Reasoning Budget | desactivado |

Con eso va a **~16 palabras por segundo** y usa **1,2 GB de memoria**.

### Atajos

| Atajo | Qué hace |
|---|---|
| `Ctrl+Alt+Espacio` | Pedir una sugerencia ahora |
| `Tab` | Aceptar la sugerencia en gris |
| `Herramientas → Explicar error con IA` | Explica el error de la línea actual |

Si el servidor no está encendido, el plugin avisa. Si tampoco hay clave de Gemini,
puedes configurarla en **Herramientas → Configurar Gemini**.

Detalles técnicos y ajustes: **[docs/IA-LOCAL.md](docs/IA-LOCAL.md)**

---

## Compilar desde el código

```powershell
cd D:\DeepSetk\netbeans-deepsetk
& "C:\Program Files\netbeans\java\maven\bin\mvn.cmd" clean package
```

Los `.nbm` quedan en `modules\<modulo>\target\nbm\`.
En `dist/` hay copias ya compiladas para instalar.

---

## Estructura

```
modules/
  ui-themes/        Temas DeepSetk Dark y Hacker Green
  cyberpunk-theme/  Tema Cyberpunk
  softdark-theme/   Tema Soft Dark
  ai-errors/        IA local, tutor y sugerencias
  crud-generator/   Generador de CRUD
  music/            Reproductor
  youtube/          YouTube
  plugin-manager/   Gestor de plugins
docs/               Capturas, guía de IA y diagramas
tools/              Lanzador "Iniciar IA + NetBeans"
dist/               .nbm listos para instalar
```

---

## Otros repos

- **Solo temas de diseño:** https://github.com/happysoccer930-alt/netbeans-deepsetk-themes
- **Licencia:** Apache-2.0
