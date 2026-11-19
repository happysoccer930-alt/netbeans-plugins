# Crystal Reports para Visual Studio en español — error 2229

Solución al error que aparece al instalar **SAP Crystal Reports, version for Microsoft Visual Studio (64-bit)** en un Windows con interfaz en español:

```
Error 2229. Base de datos: . No es posible cargar la tabla "Control" en la consulta SQL:
SELECT `Control`, `Type`, `X`, `Y`, `Width`, `Height`, `Attributes`, `Property`, `Text`,
`Control_Next`, `Help` FROM `Control` WHERE `Dialog_`=?
```

Afecta al paquete `CRforVS6413SP40_0-80007712.EXE` (Service Pack 40, 64 bits) en **cualquier** idioma que no sea inglés.

---

## Causa raíz

El instalador es un autoextraíble que deja el MSI en `%TEMP%\RarSFX1\` y aplica la transformación de idioma según el idioma de Windows (`setup.ini` → `Transform=package\` + `<LCID>.mst`).

En el SP40 esas transformaciones están mal generadas. El log del instalador (`%TEMP%\CR4VS64_0.log`) lo dice sin ambigüedad:

```
18:25:55  Doing action: Setup_Dialog
18:25:55  Transforming table Control.
          Note: 1: 2229 ... SELECT Control, Type ... FROM Control WHERE Dialog_=?
18:26:15  **Database string pool is corrupted.**   (x4)
18:26:15  La acción terminó: Setup_Dialog. Valor devuelto: 3
```

Al fusionar el `.mst` se corrompe el *string pool* de la base MSI. La primera ventana (`Setup_Dialog`) intenta leer la tabla `Control`, falla y el instalador aborta.

Comprobado aplicando cada transformación a una copia del MSI con la API de Windows Installer:

| Transform | Idioma | Resultado |
|---|---|---|
| `1033.mst` | inglés | aplica correctamente |
| `3082.mst` | español | **corrompe la base** |
| `1031.mst` | alemán | **corrompe la base** |
| `1034.mst` | francés | **corrompe la base** |
| `1040.mst` | italiano | **corrompe la base** |

Es un fallo de SAP en el paquete, no del equipo: el ejecutable tiene firma digital válida y el archivo está completo.

---

## Solución rápida (sin traducir nada)

Instalar el MSI forzando la transformación inglesa, que es la única sana:

```powershell
# 1) extraer el EXE con 7-Zip
& "C:\Program Files\7-Zip\7z.exe" x "CRforVS6413SP40_0-80007712.EXE" -o"C:\Temp\CR" -y

# 2) instalar con el transform inglés (PowerShell como administrador)
msiexec /i "C:\Temp\CR\package\CrystalReportsForVisualStudio64.msi" `
        TRANSFORMS="C:\Temp\CR\package\1033.mst" /qb
```

El asistente sale en inglés, pero el producto funciona exactamente igual.

---

## Solución completa: instalador en español

`scripts/Fix-CrystalReports2229.ps1` traduce la interfaz del MSI y genera un instalador en español sin usar las transformaciones de SAP:

```powershell
.\scripts\Fix-CrystalReports2229.ps1 -Package "$env:USERPROFILE\Downloads\CRforVS6413SP40_0-80007712.EXE"
```

Qué hace:

1. Valida la firma digital del paquete y avisa si el archivo está bloqueado.
2. Extrae el MSI y localiza las transformaciones.
3. **Diagnostica** qué `.mst` están rotas aplicándolas a una copia y leyendo la tabla `Control`.
4. Traduce la base MSI:
   - **140 textos** de la tabla `Control` (botones, títulos, avisos, casillas).
   - **71 entradas** de `ActionText` (descripción y plantilla de progreso: `Copiando archivos nuevos — Archivo: … Directorio: … Tamaño: …`).
   - **19 mensajes** de la tabla `Error`.
   - `Property/_WiseDialogSuffix = Instalación` (título de las 27 ventanas).
5. Verifica que la base resultante es consistente.
6. Con `-MakeTransform` genera además un `.mst` equivalente.

Salida: `%TEMP%\CR-es\CrystalReportsForVisualStudio64_es.msi`, listo para:

```powershell
msiexec /i "$env:TEMP\CR-es\CrystalReportsForVisualStudio64_es.msi"
```

Ejemplo de ejecución:

```
== Diagnóstico de las transformaciones de idioma
   !!  1028.mst  -> CORRUPTA (provoca el error 2229)
   !!  1031.mst  -> CORRUPTA (provoca el error 2229)
   OK  1033.mst  -> aplica correctamente
   ...
   !!  3082.mst  -> CORRUPTA (provoca el error 2229)

== Generando el MSI en español
   OK  140 textos de control, 71 acciones de progreso, 19 mensajes de error

== Verificando el MSI generado
   OK  La tabla Control se lee correctamente (182 controles) -> el error 2229 no puede reproducirse
   Boton de bienvenida : &Siguiente >
   Boton del error     : &Aceptar
   Progreso            : Copiando archivos nuevos | Archivo: [1],  Directorio: [9],  Tamaño: [6]
   Mensaje 2229        : Base de datos: [2]. No es posible cargar la tabla [3] en la consulta SQL: [4]
   Titulo de ventanas  : Instalación
```

### Requisitos

- Windows 10/11 con Windows PowerShell 5.1
- [7-Zip](https://www.7-zip.org/) (solo si la entrada es el `.EXE`)
- El paquete original descargado de SAP (no se incluye aquí)

---

## Verificación realizada

Instalado y probado en **Windows 11 + Visual Studio Insiders (18)**:

| Comprobación | Resultado |
|---|---|
| Producto instalado | `SAP Crystal Reports ... (64-bit) 13.0.40.5789` |
| Archivos | 3.043 en `C:\Program Files (x86)\SAP BusinessObjects\...` |
| GAC | `CrystalDecisions.CrystalReports.Engine 13.0.4000.0` |
| Paquete VS | `Common7\IDE\Extensions\SAP\CRVsPackage_x64.vsixmanifest` |
| Registro en VS | `Crystal Reports Tools Package` en `privateregistry.bin` |
| Módulos cargados en `devenv` | `CRVsPackage.dll`, `CRVsPackageUI.dll`, `CRDesigner.dll`, `CRDESIGNERUI.DLL`, `crpe32.dll`, `crpe32_res_es.dll` |
| Diseñador en VS 18 | Galería de Crystal Reports **en español** (asistentes Estándar / referencias cruzadas / etiqueta) |
| Plantillas | 22 idiomas (incl. 3082): `CrystalReport.rpt`, proyectos C#/VB `CrystalReportsApplication`, `CrystalReportWPFApplication`, `CrystalReportsWebSite` |

Detalle curioso: si se genera un `.mst` con `MsiDatabaseGenerateTransform` y se lanza con `setup.exe`, Windows Installer lo rechaza con `1624` / `2758` (*"Transform doesn't contain a MSI version"*). Por eso la vía recomendada es instalar el MSI ya traducido, sin transformación.

---

## Aviso legal

Este repositorio **no contiene ni redistribuye** software de SAP. El código (scripts y documentación) es propio y se publica bajo licencia MIT. Necesitas descargar el instalador original desde el portal de SAP y aceptar su licencia; este proyecto solo automatiza la traducción de la interfaz en **tu propia copia**.

Crystal Reports es una marca de SAP SE. Este proyecto no está afiliado a SAP.
