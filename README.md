# DeepSetk NetBeans Suite

Suite de plugins para **Apache NetBeans 31** (Windows, JDK 17+).

| Módulo | Estado | Qué hace |
|---|---|---|
| `softdark-theme` | ✅ listo | Tema oscuro cálido de bajo contraste para el editor |
| `ai-errors` | ✅ listo | Tutor de Java, errores con Gemini y sugerencias fantasma al escribir |
| `music` | ✅ listo | Reproductor de música local dentro del IDE (MP3, FLAC, OGG, WAV) |
| `plugin-manager` | ✅ listo | Activa/desactiva módulos de NetBeans con búsqueda y protección de dependencias |
| `crud-generator` | ✅ listo | Genera Entity, DAO, Service y SQL desde un CREATE TABLE de MySQL |
| `cyberpunk-theme` | ✅ listo | Estilo negro/azul para la interfaz y perfil Cyberpunk para el editor |

## Requisitos

- Apache NetBeans 31 instalado en `C:\Program Files\netbeans`
- JDK 17 o superior (aquí se usa el JDK 26)
- Maven: el que trae NetBeans en `C:\Program Files\netbeans\java\maven\bin\mvn.cmd`

No hace falta instalar Maven aparte.

## Compilar

```powershell
cd D:\DeepSetk\netbeans-deepsetk
& "C:\Program Files\netbeans\java\maven\bin\mvn.cmd" clean package
```

Los `.nbm` quedan en `modules\<modulo>\target\nbm\`. En `dist\` hay copias
listas para instalar:

- `dist\SoftDark-1.0.0.nbm`
- `dist\Gemini-1.0.0.nbm`
- `dist\Music-1.0.0.nbm`
- `dist\PluginManager-1.0.0.nbm`
- `dist\CrudGenerator-1.0.0.nbm`
- `dist\Cyberpunk-1.0.0.nbm`

## Instalar un plugin

1. NetBeans: **Herramientas → Plugins → Descargados → Agregar plugins…**
2. Selecciona el `.nbm` (por ejemplo `softdark-theme-1.0.0.nbm`).
3. **Instalar** y reiniciar si lo pide.

## Módulo Soft Dark

- Perfil de colores nuevo llamado **Soft Dark** en
  *Herramientas → Opciones → Fuentes y colores*.
- Se activa solo la primera vez que se instala.
- Menú **Herramientas → Aplicar tema Soft Dark** para volver a activarlo.
- Cubre Java, XML, HTML, CSS, JavaScript, JSON, YAML, SQL, Markdown,
  Properties, Diff, JSP, PHP, TLD, XHTML, EL, INI y Apache Conf.
- Los ficheros de lenguaje se generan desde la paleta FlatLaf Dark de
  Apache NetBeans con `tools/generate-theme.ps1` (reduce saturación y limita
  el brillo). Los ficheros base, Java y de resaltados están escritos a mano.

### Verificar sin abrir la GUI

```powershell
# monta layer.xml con el XMLFileSystem real y valida los DTD
powershell -ExecutionPolicy Bypass -File tools\verify-theme.ps1
```

## Módulo Gemini AI Errors

Explica un error del editor con la API de Google Gemini.

### Configurar (una vez)

1. Consigue una API key gratuita en <https://aistudio.google.com/apikey>.
2. En NetBeans: **Herramientas → Configurar Gemini (API key y modelo)**.
3. Pega la key y pulsa **Probar**: descarga la lista oficial de modelos y
   rellena el desplegable. Elige uno (por defecto se sugiere un `flash`) y
   guarda.

La key se guarda en las preferencias de NetBeans cifrada con AES-GCM. Eso evita
que aparezca en claro en los ficheros de configuración, pero **no es seguridad
fuerte**: cualquiera con acceso a tu sesión de usuario podría descifrarla.

### Usar

- Abre un archivo Java con un error y coloca el cursor en la línea marcada.
- Clic derecho en el editor → **Explicar error con Gemini**, o
  **Herramientas → Explicar error con Gemini**.
- Se abre una ventana (no modal) con la causa, la corrección y el código
  sugerido. El botón **Copiar** lleva el texto al portapapeles.
- Se envían al modelo la línea del cursor, unas líneas de contexto y los
  diagnósticos que ya calculó NetBeans. No se envía el archivo completo.

Si no hay modelo elegido, la primera consulta toma el primer `flash` de tu
cuenta y lo guarda.

## Tutor Gemini (aprender con tu código)

La misma API key y el mismo modelo se usan en un tutor con conversación:

- **Herramientas → Tutor Gemini: aprender con mi código**
- **Ventana → Tutor Gemini**
- Clic derecho en el editor → **Tutor Gemini: aprender con mi código**
- Botón de burbuja de diálogo en la barra de herramientas (grupo *Archivo*)
- Se abre como pestaña en la zona inferior (modo *Output*).

### Qué puedes hacer

- **Explicar archivo**: qué hace, cómo está organizado y qué conceptos repasar.
- **Explicar selección**: analiza el texto seleccionado (o la línea del cursor).
- **Revisar código**: errores reales, riesgos, malas prácticas y mejoras, con el
  porqué de cada cambio.
- **Generar preguntas**: 6 preguntas de práctica con las respuestas al final.
- **Dame un ejercicio**: propone un ejercicio guiado, con pista y criterio de logro.
- **Explicar error**: usa los diagnósticos reales de NetBeans.
- **Pregunta libre**: escribe abajo y pulsa **Enviar** o `Ctrl+Enter`.

Elige el **nivel** (Principiante, Intermedio, Avanzado). El tutor adapta la
explicación y propone ejercicios. La casilla **Incluir archivo actual** adjunta
el archivo abierto y la selección; la conversación se mantiene en memoria hasta
que pulses **Nueva conversación**. **Copiar última** copia la respuesta.

El historial no se guarda en disco (privacidad) y solo se envían los últimos
turnos, con el archivo recortado si es muy grande.

### Sugerencias fantasma al escribir (estilo VS Code)

Incluido en el mismo plugin **Gemini**. Funciona en archivos **Java**:

- Mientras escribes, después de una pausa aparece una **palabra en gris** junto al cursor.
- `Tab` acepta la sugerencia.
- `Escape` la descarta.
- La predicción local es inmediata y no necesita Internet.
- Si tienes **LM Studio** con el servidor local activo, al pausar se consulta el modelo local. Por defecto:
  - URL: `http://127.0.0.1:1235/v1`
  - Modelo: `qwen2.5-coder-1.5b-instruct`
- Menú **Herramientas → Configurar LLM local (LM Studio)** para cambiar URL o modelo.
- Como respaldo, si tienes API key de **Gemini** o de **OpenCode Zen free**, el plugin las usa en ese orden.
- Menú **Herramientas → Configurar OpenCode Zen free** para la URL, el modelo y la API key.
- Menú **Herramientas → Sugerencias fantasma (Tab)** para activarlas o desactivarlas.

El router de proveedores es:

```text
1. Prediccion local de palabras (siempre)
2. LM Studio (si esta disponible)
3. Gemini Flash (si hay API key)
4. OpenCode Zen free (si hay API key)
```

La primera consulta a LM Studio puede tardar mientras carga el modelo; después suele responder en unos segundos. La predicción de la siguiente palabra sigue siendo instantánea. Si LM Studio no responde a tiempo, se usa Gemini/OpenCode como respaldo.

#### Control y velocidad

- **Circuito de seguridad**: si un proveedor falla dos veces, se evita durante 30 segundos.
- **Timeouts**: 15 segundos para LM Studio y 20 segundos para Gemini/OpenCode.
- **Sugerencia manual**: `Ctrl+Alt+Espacio` pide una sugerencia de IA inmediata, sin esperar al temporizador.
- También está en **Herramientas → Pedir sugerencia IA ahora**.
- **Latencia registrada** por proveedor para no repetir uno que está fallando.
- **Caché** de contexto y de disponibilidad para no hacer peticiones duplicadas.

#### Capas de eficiencia

- **Índice local por versión del documento**: los bigramas se calculan una vez y se reutilizan hasta que cambia el texto.
- **Capa de contexto Java**: prioriza tipos, `return`, `new`, `System.out` y nombres de clase.
- **Caché LRU de sugerencias**: los contextos repetidos no vuelven a consultar al modelo.
- **Solo se llama al LLM si no hay una sugerencia local**: evita esperas innecesarias.
- **Debounce, cancelación y cooldown**: se descarta la petición anterior si sigues escribiendo.
- **Contexto recortado**: se envían como máximo unos 3.000 caracteres antes del cursor y 800 después.
- **Respuesta corta**: el modelo local está limitado a 12 tokens.

La sugerencia local usa las palabras y estructuras del propio archivo. La de Gemini envía solo el fragmento cercano al cursor, no el proyecto completo. Es una ayuda, no una verdad absoluta: revisa siempre lo que aceptas.

### Verificar sin abrir la GUI

```powershell
# layer, parser JSON, cifrado de la key, Markdown y cuerpo multi-turno del tutor
powershell -ExecutionPolicy Bypass -File tools\verify-ai.ps1
```

## Módulo Música

Reproductor local, sin Internet ni cuentas externas.

- Formatos: **MP3, FLAC, OGG/OGA (Vorbis) y WAV**.
- La lista se guarda entre sesiones junto con la pista actual y el volumen.
- Controles en la barra de herramientas (grupo **Archivo**): anterior,
  reproducir/pausar, detener, siguiente y abrir la lista.
- Menú **Ventana → Lista de música** o **Herramientas → Lista de música**.
- En la ventana: **Carpeta…** agrega una carpeta y sus subcarpetas,
  **Archivos…** agrega archivos sueltos, doble clic reproduce, **Quitar** y
  **Vaciar** editan la lista, y el deslizador regula el volumen.
- No se copia música al IDE: la lista guarda rutas absolutas. Si mueves o
  borras un archivo, se descarta al arrancar la próxima vez.
- Al terminar una canción pasa a la siguiente; al terminar la última se detiene.
- Los decodificadores (JLayer, MP3SPI, Tritonus, JOrbis, VorbisSPI y jFLAC,
  todos LGPL) van empaquetados en `netbeans/modules/ext`; ver
  `modules/music/src/main/resources/org/deepsetk/music/notice.txt`.

Limitación conocida: VorbisSPI puede devolver 0 bytes en OGG extremadamente
cortos (alrededor de 1 segundo). Las canciones normales no se ven afectadas.

### Verificar sin abrir la GUI

```powershell
# layer, titulos, iconos y decodificacion real de los 4 formatos
powershell -ExecutionPolicy Bypass -File tools\verify-music.ps1
```

El verificador genera audios de prueba con **ffmpeg** (tiene que estar en el
`PATH`) y decodifica WAV, MP3, FLAC de 16/24 bits y OGG.

## Gestor de plugins

NetBeans ya permite activar/desactivar módulos en *Herramientas → Plugins →
Instalados*. Este módulo añade una ventana propia más cómoda:

- **Herramientas → Gestionar plugins (activar/desactivar)** o el botón de
  deslizadores en la barra de herramientas (grupo *Archivo*).
- Lista todos los módulos instalados con **búsqueda**, **categoría** y
  **estado** (todos, activados, desactivados).
- **Bloques** a la izquierda: *Todos*, *NetBeans*, *Tema Soft Dark*, *Gemini*,
  *Música*, *Gestor de plugins*, *Generador CRUD*, *Cyberpunk*, *JRebel* y
  *Otros*. Al elegir un bloque, la tabla muestra solo esos módulos.
- Casilla **Activo** para cada módulo. Los cambios se guardan en memoria hasta
  pulsar **Aplicar cambios**.
- **Protección de dependencias** activada: no deja desactivar un módulo si otro
  módulo activo depende de él; muestra la lista de afectados.
- **Detalle** del módulo seleccionado y contador de cambios pendientes.
- **Aplicar cambios** usa la API oficial de actualizaciones de NetBeans.
  Después pregunta si quieres **reiniciar ahora** o dejar el reinicio para
  después.

No desinstala nada: solo activa o desactiva. Desactivar módulos base puede
dejar el IDE sin funciones; la protección y las advertencias están para evitar
errores, pero la decisión final es tuya.

### Verificar sin abrir la GUI

```powershell
# layer, acción, menús, botón e icono del gestor
powershell -ExecutionPolicy Bypass -File tools\verify-plugin-manager.ps1
```

## Generador CRUD (Java + MySQL)

- **Herramientas → Generar CRUD (Java + MySQL)** o el botón de tabla con “+”
  en la barra de herramientas.
- Pega un `CREATE TABLE` de MySQL y pulsa **Analizar**.
- Revisa las pestañas **Entity**, **DAO**, **Service**, **Conexión** y **SQL**.
- Las pestañas de código son **editables**: puedes cambiar el DAO, añadir
  métodos o ajustar el Entity antes de guardar. Se escribe tal como lo dejes.
  **Analizar** vuelve a generar el código y pide confirmación si editaste algo.
- Ajusta el **paquete base**, el nombre de la **clase entidad** y la
  **carpeta de salida** (por defecto intenta usar el `src` del archivo Java
  que tengas abierto).
- Marca qué quieres generar y pulsa **Generar archivos**.

Genera:

- `model/Entidad.java`: campos con tipos Java, constructor, getters y setters.
- `dao/EntidadDAO.java`: `insertar`, `actualizar`, `eliminar`, `buscarPorId`
  y `listar` con `PreparedStatement` y mapeo de `ResultSet`.
- `service/EntidadService.java`: capa de servicio que usa el DAO.
- `conexion/Conexion.java`: clase de conexión JDBC con URL, usuario y clave.
  Puedes cambiar el paquete y el nombre de la clase en la pestaña **Conexión**.
- La pestaña **SQL** muestra las sentencias con `?`.

La pestaña **Conexión** permite escribir la URL JDBC, el usuario y la clave.
La clave queda en el código generado; para producción es mejor leerla de una
variable de entorno. Ejemplo de uso:

```java
UsuarioService service = new UsuarioService(Conexion.conectar());
```

Soporta claves simples, compuestas y tablas sin `PRIMARY KEY` (en ese caso solo
genera insertar/listar y avisa). Reconoce `INT`, `BIGINT`, `DECIMAL`, `VARCHAR`,
`TEXT`, `DATE`, `DATETIME`, `TIME`, `TINYINT(1)`, `ENUM`, `BLOB`, etc.

El código generado usa JDBC estándar; tu proyecto necesita el driver de MySQL
(`mysql-connector-j`). No modifica tu base de datos ni tus clases existentes:
solo escribe archivos nuevos (o pregunta antes de sobrescribir).

### Verificar sin abrir la GUI

```powershell
# parser, tipos, PK simple/compuesta/sin PK y compilacion real del codigo generado
powershell -ExecutionPolicy Bypass -File tools\verify-crud.ps1
```

## Estilo Cyberpunk

Un tema visual negro/azul para el IDE, un **splash animado** y un perfil de
colores Cyberpunk para el editor.

- **Herramientas → Aplicar estilo Cyberpunk (negro y azul)**.
- Para volver: **Herramientas → Quitar estilo Cyberpunk (volver a Soft Dark)**.

La interfaz cambia al instante: menús y barras en azul marino, barra superior
(título, buscador y botones de ventana) del mismo tono, buscador con borde azul,
pestañas con línea azul eléctrica, selección azul, árbol/listas/tablas negras y
botones azul oscuro. El estilo se mantiene entre reinicios hasta que lo quites.

### Splash y barra de carga

Al arrancar, el plugin muestra un **splash Cyberpunk** con el cubo mejorado y
una **barra de carga animada** sobre una pista oscura: relleno azul degradado,
segmentos, borde cian, cabeza luminosa con pulso, porcentaje y líneas de
escaneo. Dura unos segundos y se cierra cuando NetBeans termina de cargar.

Para que no se solape con el splash nativo, usa el acceso directo
**“NetBeans Cyberpunk”** del Escritorio: apunta a `netbeans64.exe --nosplash`
y ya lleva el icono del cubo. Si abres NetBeans desde el acceso normal, puede
aparecer primero el splash original y luego el Cyberpunk.

### Icono de la ventana y del .exe

- El icono del **cubo Cyberpunk mejorado** se aplica a la ventana y a la barra
  de tareas mientras el plugin está activo.
- El acceso directo del Escritorio también usa ese icono.
- Para cambiar el icono **real** de `netbeans64.exe` hay que modificar un
  archivo de `Program Files` con permisos de administrador. El proyecto incluye
  `tools\patch-netbeans-exe-icon.ps1`: ejecútalo en PowerShell **como
  administrador**. Hace copia de seguridad antes de tocar el `.exe` y descarga
  `rcedit` automáticamente. Una actualización de NetBeans puede revertirlo.

El icono del editor usa el perfil **Cyberpunk** (fondo negro azulado, palabras
clave azul eléctrico, cadenas cian, números azul claro). Como NetBeans guarda
el perfil al arrancar, el cambio de colores del editor se ve al **reiniciar**;
la interfaz y el splash se actualizan en el momento.

Si quieres elegir el perfil a mano: **Herramientas → Opciones → Fuentes y
colores → Cyberpunk**.

### Verificar sin abrir la GUI

```powershell
# valida los XML del perfil Cyberpunk contra el DTD y monta su layer
powershell -ExecutionPolicy Bypass -File tools\verify-theme.ps1 -Profile Cyberpunk
```

## Estructura

```
netbeans-deepsetk/
  pom.xml                     reactor Maven
  modules/
    softdark-theme/           modulo NBM del tema
      src/main/java/          aplicador del perfil + accion de menu
      src/main/nbm/           manifest del modulo
      src/main/resources/     layer.xml + paletas XML
    ai-errors/                modulo NBM de Gemini
      src/main/java/          acciones, dialogo, cliente HTTP, JSON, cifrado
      src/main/nbm/           manifest del modulo
      src/main/resources/     layer.xml
    music/                    modulo NBM del reproductor
      src/main/java/          reproductor, decodificadores, acciones, ventana
      src/main/nbm/           manifest del modulo
      src/main/resources/     layer.xml + aviso de licencias
    plugin-manager/           gestor de modulos activos/inactivos
      src/main/java/          dialogo, tabla, proteccion de dependencias
      src/main/nbm/           manifest del modulo
      src/main/resources/     layer.xml
    crud-generator/           generador Entity/DAO/Service desde SQL
      src/main/java/          parser SQL, generador, dialogo
      src/main/nbm/           manifest del modulo
      src/main/resources/     layer.xml
    cyberpunk-theme/          estilo negro/azul + perfil Cyberpunk
      src/main/java/          UI defaults, splash, instalador, acciones
      src/main/nbm/           manifest del modulo
      src/main/resources/     layer.xml + paletas + splash/iconos
  assets/                     imagen original, PNGs e ICO del cubo
  tools/
    generate-theme.ps1        genera las paletas de lenguaje
    generate-cyberpunk.ps1    genera el perfil Cyberpunk desde Soft Dark
    create-cyberpunk-icon.ps1 crea el .ico, PNGs y acceso directo
    patch-netbeans-exe-icon.ps1 cambia el icono del .exe (admin)
    verify/VerifyTheme.java   verificacion offline del tema
    verify-theme.ps1          lanzador de la verificacion del tema
    verify/VerifyAiErrors.java verificacion offline de Gemini
    verify-ai.ps1             lanzador de la verificacion de Gemini
    verify/VerifyMusic.java   verificacion offline del reproductor
    verify-music.ps1          lanzador de la verificacion del reproductor
    verify/VerifyPluginManager.java verificacion del gestor
    verify-plugin-manager.ps1 lanzador de la verificacion del gestor
    verify/VerifyCrudGenerator.java verificacion del generador CRUD
    verify-crud.ps1           lanzador del generador CRUD
    verify/music-cp.txt       classpath resuelto para el reproductor
    verify/plugin-manager-cp.txt classpath resuelto para el gestor
    verify/crud-cp.txt        classpath resuelto para el generador
  dist/                       .nbm listos para instalar
  docs/
    soft-dark-preview.html    vista previa de la paleta
```
