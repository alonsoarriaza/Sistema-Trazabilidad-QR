========================================================================
             SISTEMA DE GESTIÓN DE INVENTARIO Y coanda QR
                              coanda - 2026
========================================================================

Este documento sirve como manual general del usuario y de referencia para el
taller y el equipo comercial. El sistema permite el control de entrada de
máquinas, la extracción y coanda de piezas (fungibles), la gestión de stock
de tóneres con representación gráfica del nivel de consumible, y el control
estricto de accesos basado en roles (RBAC).

------------------------------------------------------------------------
1. CONFIGURACIÓN Y PUESTA EN MARCHA
------------------------------------------------------------------------
El sistema consta de un backend desarrollado en Spring Boot (Java 21, MariaDB)
y un frontend Single Page Application (SPA) desarrollado en HTML, CSS y JS.

Para iniciar la aplicación:
- Ejecute el archivo "arrancar.bat" ubicado en la carpeta del proyecto.
- La aplicación se levanta de forma segura bajo HTTPS en el puerto:
  https://localhost:8443 (o la IP local del equipo servidor).

------------------------------------------------------------------------
2. ROLES, CREDENCIALES Y FLUJO DE TRABAJO (RBAC)
------------------------------------------------------------------------
El acceso al sistema está controlado por un login inicial que requiere
seleccionar el Rol, ingresar el Nombre del usuario e introducir la contraseña:

A) ROL: TÉCNICO
   - Contraseña de Acceso: 1111
   - Permisos y Acceso:
     * Acceso total a la aplicación del taller ("index.html").
     * Entrada de nuevas máquinas (registro de ficha, generación de QR).
     * Gestión y edición de fichas de máquinas, componentes y piezas.
     * Escaneo de códigos QR mediante cámara del dispositivo o entrada manual.
     * Gestión del stock de Tóneres (registro manual y visualización detallada).
     * Visualización del Historial de Movimientos general.
     * Generación e impresión de etiquetas QR para máquinas, piezas y tóneres.
     * Contraseña de seguridad adicional para eliminar máquinas: 1414.
   - Bloqueos:
     * Tiene prohibido el acceso a la vista Comercial ("comercial.html").
       Cualquier intento de navegación directa le redirigirá al taller.

B) ROL: COMERCIAL
   - Contraseña de Acceso: coanda2026
   - Permisos y Acceso:
     * Al iniciar sesión, es redirigido directamente al catálogo comercial
       ("comercial.html").
     * Visualiza EXCLUSIVAMENTE los equipos marcados por el equipo técnico
       como "Preparada para Comercial" (preparada_comercial = 1 / true).
     * Visualiza los detalles completos de los equipos en venta en un modal.
     * Acción "Volver a Almacén": Si un equipo necesita ser retirado del
       catálogo de ventas, el comercial puede devolverlo al almacén. Esta
       acción actualiza el estado (preparada_comercial = 0 / false) y remueve
       la máquina del catálogo comercial de forma instantánea.
   - Bloqueos:
     * Tiene prohibido el acceso a la aplicación del taller ("index.html").
       Cualquier intento de navegación directa le redirigirá al catálogo.

------------------------------------------------------------------------
3. FUNCIONALIDADES CLAVE Y REGLAS DE NEGOCIO
------------------------------------------------------------------------
- coanda Inversa:
  Las piezas extraídas de una máquina madre conservan su número de serie
  de origen, permitiendo rastrear la procedencia de cada repuesto.

- Regla de Bloqueo por Nivel de Estado:
  Las piezas se clasifican en Niveles 1, 2 y 3. El sistema evalúa
  automáticamente si una pieza puede ser reutilizada según su nivel.
  Las piezas Nivel 3 o en estado dudoso requerirán revisión exhaustiva.

- Barra de Progreso Dinámica de Tóneres:
  En la Ficha de Tóner se despliega una barra de progreso que representa
  gráficamente el porcentaje de consumible restante (0%, 25%, 50%, 75%, 100%),
  con colores semafóricos de alerta (Rojo, Amarillo, Azul y Verde).

- Guardado Local de Sesiones y Borradores:
  Las sesiones y los borradores de formularios se persisten localmente en
  LocalStorage para prevenir pérdida de información por fallos de red
  o recargas involuntarias del navegador en entornos móviles.
========================================================================


