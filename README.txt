Aquí tienes el README rediseñado para que sea mucho más visual, estructurado y profesional, utilizando un nombre genérico (**TechCorp**) y términos de trazabilidad para sustituir el nombre de la empresa por privacidad.

---

# 📦 SISTEMA DE GESTIÓN DE INVENTARIO Y SISTEMA QR

**🏢 TechCorp - 2026**

> **📌 Propósito del Documento**
> Este documento sirve como manual general del usuario y de referencia rápida para el taller y el equipo comercial. El sistema permite el control de entrada de máquinas, la extracción y asignación de piezas (fungibles), la gestión de stock de tóneres con representación gráfica del nivel de consumible, y el control estricto de accesos basado en roles (RBAC).

---

## 🚀 1. CONFIGURACIÓN Y PUESTA EN MARCHA

El sistema consta de un **Backend** robusto y un **Frontend** ágil y reactivo:

* **Backend:** Desarrollado en Spring Boot (Java 21, MariaDB).
* **Frontend:** Single Page Application (SPA) desarrollado en HTML, CSS y JS.

### 🛠️ Instrucciones de Inicio:

1. Navega a la carpeta principal del proyecto.
2. Ejecuta el archivo de arranque:
```bash
arrancar.bat

```


3. Accede a la aplicación de forma segura (HTTPS) desde tu navegador:
🔗 `https://localhost:8443` *(o mediante la IP local del equipo servidor).*

---

## 🔐 2. ROLES, CREDENCIALES Y FLUJO DE TRABAJO (RBAC)

El acceso al sistema está protegido por un login inicial. Para ingresar, es necesario seleccionar el **Rol**, ingresar el **Nombre de usuario** y la **Contraseña**.

### 🔧 ROL: TÉCNICO

* **Contraseña de Acceso:** `1111`
* **Contraseña de Seguridad (Borrado):** `1414` (Requerida para eliminar máquinas).
* ✅ **Permisos y Acceso:**
* Acceso total a la aplicación del taller (`index.html`).
* Entrada de nuevas máquinas (registro de ficha, generación de QR).
* Gestión y edición de fichas de máquinas, componentes y piezas.
* Escaneo de códigos QR (mediante cámara del dispositivo o entrada manual).
* Gestión del stock de Tóneres (registro manual y visualización detallada).
* Visualización del Historial de Movimientos general.
* Generación e impresión de etiquetas QR para máquinas, piezas y tóneres.


* 🚫 **Bloqueos:**
* Prohibido el acceso a la vista Comercial (`comercial.html`). Los intentos de navegación directa redirigirán automáticamente al taller.



### 💼 ROL: COMERCIAL

* **Contraseña de Acceso:** `techcorp2026`
* ✅ **Permisos y Acceso:**
* Redirección automática al catálogo comercial (`comercial.html`) al iniciar sesión.
* Visualización **EXCLUSIVA** de los equipos marcados por el servicio técnico como *"Preparada para Comercial"* (`preparada_comercial = true`).
* Acceso a detalles completos de los equipos en venta mediante una vista modal.
* **Acción "Volver a Almacén":** Si un equipo debe retirarse del catálogo de ventas, el comercial puede devolverlo al almacén. Esto actualiza el estado (`preparada_comercial = false`) y remueve la máquina del catálogo instantáneamente.


* 🚫 **Bloqueos:**
* Prohibido el acceso a la aplicación del taller (`index.html`). Los intentos de navegación directa redirigirán automáticamente al catálogo.



---

## ⭐ 3. FUNCIONALIDADES CLAVE Y REGLAS DE NEGOCIO

* 🔄 **Trazabilidad Inversa:**
Las piezas extraídas de una máquina madre conservan su número de serie de origen. Esto permite rastrear la procedencia exacta y el ciclo de vida de cada repuesto.
* 🛡️ **Regla de Bloqueo por Nivel de Estado:**
Las piezas se clasifican en **Niveles 1, 2 y 3**. El sistema evalúa automáticamente si una pieza puede ser reutilizada basándose en su nivel. Las piezas de Nivel 3 o en estado dudoso quedan bloqueadas a la espera de una revisión exhaustiva.
* 📊 **Barra de Progreso Dinámica de Tóneres:**
En la Ficha de Tóner se despliega un indicador visual que representa gráficamente el porcentaje de consumible restante **(0%, 25%, 50%, 75%, 100%)**. Utiliza colores semafóricos de alerta (🔴 Rojo, 🟡 Amarillo, 🔵 Azul y 🟢 Verde) para una lectura rápida.
* 💾 **Guardado Local de Sesiones y Borradores:**
Toda la sesión activa y los borradores de los formularios se persisten de manera local utilizando `LocalStorage`. Esto previene cualquier pérdida de información debido a fallos de red o recargas accidentales del navegador, especialmente optimizado para entornos móviles.