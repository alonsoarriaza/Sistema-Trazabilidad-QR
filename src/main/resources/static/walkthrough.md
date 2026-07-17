# Walkthrough - Evolución del Sistema de Trazabilidad QR trazabilidad

Se han completado e integrado con éxito todas las funcionalidades de evolución solicitadas para el taller trazabilidad.

## Cambios Realizados

### 1. Formulario de Máquinas (Fase 1)
- **Decisión Técnica**: Actualizado con los nuevos campos en `index.html` (`Cliente`, `Almacén`, `Despiece`, `Reacondicionado`).
- **Inspección Visual**: Añadido este nuevo desplegable con terminología profesional en `index.html` (`Óptimo`, `Aceptable`, `Deficiente`).
- **Persistencia**: Añadidos campos `estadoVisual` y `preparadaComercial` en `Maquina.java` y sincronizados mediante `MaquinaService.java`.

### 2. Buscador Global (Fase 2)
- Creado en `index.html` la vista de buscador global accesible desde el menú lateral.
- Permite buscar de forma unificada tanto máquinas como piezas por términos parciales.
- Muestra la información completa y organizada en dos tablas independientes (Máquinas encontradas y Piezas encontradas), permitiendo navegar al detalle de cada una haciendo click en las filas.

### 3. Historial de Movimientos (Fase 3)
- **Entidad**: Creado `HistorialMovimiento.java` y su repositorio y controlador correspondientes.
- **Registro Automático**: Modificado `MaquinaService` y `PiezaService` para registrar en el historial acciones clave (registro inicial `ALTA`, cambios de estado, extracciones, devoluciones, marcación comercial, etc.).
- **Visualización**: Renderizado de un listado timeline en las fichas de detalle tanto de máquinas como de piezas en el frontend.

### 4. Extracción de Componentes y Generador de QR (Fase 4 y Fase 6)
- **Checkbox y Extracción**: En la ficha de máquina, ahora se muestra la lista de componentes estándar. Si no se han extraído, aparece un botón "Extraer" que abre un modal solicitando motivo (Para reparación, Para almacenar con su estado, o Para cliente).
- **Destino del Cliente**: Si se selecciona "Para cliente", el campo de observaciones cambia a "Ubicación o Destino (Obligatorio)".
- **Generación Automática de QR**: Al extraer, el backend le asigna automáticamente un QR con formato `PZ-0000XX` correlativo usando su ID.
- **Impresión**: Se abre un modal de impresión de etiqueta con el QR e información de la pieza y un botón para imprimir, optimizado para etiquetas adhesivas.
- **Devolución**: Al ver la ficha de una pieza extraída, aparece el botón "Dar de alta pieza (Devolver al almacén)" para reintegrarla al stock en un solo clic.

### 5. Rol Comercial (Fase 5)
- **Autenticación con Spring Security**: Configurado `SecurityConfig.java` para proteger `/comercial.html` con rol `COMERCIAL` (usuario `comercial` / contraseña `trazabilidad2026`). Las demás páginas e interfaces REST del taller permanecen abiertas.
- **Páginas**: Creadas las páginas `login.html` y `comercial.html` en el frontend.
- **Catálogo comercial**: En `comercial.html` se muestran únicamente las máquinas que el técnico ha marcado como "Preparadas para Comercial" mediante el nuevo botón en la ficha de máquina.

---

## Cómo Verificar los Cambios

1. **Técnico (Taller)**:
   - Abra la aplicación (Taller) en `https://localhost:8443/index.html`.
   - Vaya a "Dar entrada a máquina", rellene el formulario seleccionando la nueva "Inspección Visual" y "Decisión Técnica", y guarde.
   - En la ficha de la máquina creada, pruebe a extraer un componente (por ejemplo, el *Fusor*). Seleccione el motivo y confirme. Se abrirá la etiqueta QR generada automáticamente.
   - Use el menú lateral y pulse en "Buscador Global" para filtrar por texto y verificar que aparecen tanto la máquina como la pieza extraída.
   - Escanee o busque la pieza extraída, y pulse "Dar de alta pieza (Devolver al almacén)" para comprobar que vuelve a ingresar al almacén e incrementa el historial.

2. **Comercial**:
   - Marque una máquina como preparada pulsando el botón "Preparada para Comercial" en la ficha de máquina.
   - Ingrese a `https://localhost:8443/comercial.html`. Será redirigido al formulario de Login.
   - Inicie sesión con usuario `comercial` y clave `trazabilidad2026`.
   - Compruebe que solo se visualizan en el catálogo las máquinas marcadas como preparadas, con su información técnica, estado visual y observaciones de venta.

