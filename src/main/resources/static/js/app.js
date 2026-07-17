/* ═══════════════════════════════════════════════════════════════════
   LÓGICA PRINCIPAL — Sistema de techcorp QR techcorp
   
   Este archivo contiene toda la lógica del frontend:
     - Navegación entre vistas (SPA de una sola página).
     - Integración con la librería HTML5-QRCode para la lectura
       de códigos QR desde la cámara trasera del móvil.
     - Comunicación con la API REST del backend Spring Boot.
     - Gestión de formularios de alta y edición (Plantillas 10.1 y 10.2).
     - Renderizado de fichas de detalle y listados.
     - Evaluación de movimientos con la regla de bloqueo por nivel.
   
   La URL base de la API se configura dinámicamente según el host
   desde el que se accede, para que funcione tanto en localhost
   como en la IP fija de la red local (ej. 192.168.1.50:8443).
   ═══════════════════════════════════════════════════════════════════ */

// ─────────── Configuración de la API ───────────
// Se construye la URL base de la API a partir del origen actual del navegador.
// De este modo, si se accede desde https://192.168.1.50:8443, las peticiones
// se dirigen automáticamente a esa misma dirección sin necesidad de hardcodear.
const API_BASE = window.location.origin + '/api';

// ─────────── Variables globales ───────────
// Instancia del escáner QR (html5-qrcode). Se inicializa la primera vez
// que el técnico pulsa "Iniciar Cámara" y se reutiliza en escaneos sucesivos.
let html5QrCode = null;

// Bandera que indica si el escáner está activo para evitar arranques duplicados.
let scannerActivo = false;

// Variables globales para almacenamiento temporal de listas cargadas y búsquedas locales
let listaMaquinasActual = [];
let listaPiezasActual = [];
let listaHistorialActual = [];
let listaTonersActual = [];
let maquinaIdEnValidacion = null;
let inventarioInterval = null;
let inventarioTimeout = null;

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 1: NAVEGACIÓN ENTRE VISTAS
   
   El sistema funciona como una Single Page Application (SPA) ligera.
   Cada "vista" es una sección <section> del HTML que se muestra u
   oculta añadiendo o quitando la clase "d-none" de Bootstrap.
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Cambia la vista visible en la interfaz.
 *
 * Oculta todas las secciones y muestra únicamente la que corresponde
 * al nombre recibido. Si la vista es un listado, se cargan los datos
 * automáticamente desde la API.
 *
 * @param {string} nombre - Identificador de la vista a mostrar:
 *   "scanner", "formMaquina", "fichaMaquina", "formPieza",
 *   "fichaPieza", "listaMaquinas", "listaPiezas".
 */
function mostrarVista(nombre, pushToHistory = true, stateData = null) {
    if (nombre && nombre.indexOf('?') !== -1) {
        nombre = nombre.substring(0, nombre.indexOf('?'));
    }
    // Cerrar cualquier modal de Bootstrap abierto y limpiar backdrops huérfanos
    try {
        const activeModals = document.querySelectorAll('.modal.show');
        activeModals.forEach(function(modalEl) {
            const modalInstance = bootstrap.Modal.getInstance(modalEl);
            if (modalInstance) {
                modalInstance.hide();
            }
        });
        
        const backdrops = document.querySelectorAll('.modal-backdrop');
        backdrops.forEach(function(b) {
            b.remove();
        });
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
    } catch (e) {
        console.error('Error al limpiar modales:', e);
    }
    // Se definen los identificadores de todas las vistas disponibles.
    const vistas = [
        'vistaInicio',
        'vistaScanner',
        'vistaFormMaquina',
        'vistaFichaMaquina',
        'vistaFormPieza',
        'vistaFichaPieza',
        'vistaFichaToner',
        'vistaListaMaquinas',
        'vistaListaPiezas',
        'vistaBuscador',
        'vistaReimpresion',
        'vistaListaHistorial',
        'vistaListaToners',
        'vistaInventario'
    ];

    // Se ocultan todas las vistas aplicando la clase "d-none".
    vistas.forEach(function(id) {
        document.getElementById(id).classList.add('d-none');
    });

    // Se muestra la vista solicitada eliminando la clase "d-none".
    const vistaDestino = document.getElementById('vista' + nombre.charAt(0).toUpperCase() + nombre.slice(1));
    if (vistaDestino) {
        vistaDestino.classList.remove('d-none');
    }

    // Si se navega al escáner, se detiene cualquier escaneo previo.
    if (nombre === 'scanner' && scannerActivo) {
        detenerScanner();
    }

    if (nombre === 'inicio') {
        cargarNotificacionesDevolucion();
    }

    // Si se navega a un listado, se cargan los datos automáticamente.
    if (nombre === 'listaMaquinas') {
        cargarListaMaquinas();
    } else if (nombre === 'listaPiezas') {
        cargarListaPiezas();
    } else if (nombre === 'listaHistorial') {
        cargarListaHistorial();
    } else if (nombre === 'listaToners') {
        cargarListaToners();
    } else if (nombre === 'reimpresion') {
        cargarListasReimpresion();
    } else if (nombre === 'inventario') {
        // Reiniciar estado visual del inventario
        document.getElementById('loadingInventario').classList.add('d-none');
        document.getElementById('resultadosInventario').classList.add('d-none');
        if (inventarioInterval) clearInterval(inventarioInterval);
        if (inventarioTimeout) clearTimeout(inventarioTimeout);
    }

    if (nombre === 'formMaquina') {
        cargarMarcasYModelosAutocomplete();
    }

    // Si se navega al formulario de máquina sin datos previos, se reinicia y se restaura el borrador.
    if (nombre === 'formMaquina' && !document.getElementById('maqId').value) {
        document.getElementById('tituloFormMaquina').textContent = 'Nueva Máquina';
        document.getElementById('formMaquina').reset();
        const compatiblesContainer = document.getElementById('compatiblesContainer');
        if (compatiblesContainer) compatiblesContainer.innerHTML = '';
        var campoFechaMaq = document.getElementById('maqFechaEntrada');
        if (campoFechaMaq) campoFechaMaq.value = obtenerFechaHoy();
        restaurarBorradorMaquina();
        aplicarParametrosUrlFormMaquina();
        const modelVal = document.getElementById('maqModelo').value;
        if (modelVal) {
            actualizarToneresCompatibles(modelVal);
        }
    }


    // Si se navega al formulario de pieza sin datos previos, se reinicia y se restaura el borrador.
    if (nombre === 'formPieza' && !document.getElementById('pzaId').value) {
        document.getElementById('tituloFormPieza').textContent = 'Nueva Pieza';
        document.getElementById('formPieza').reset();
        var campoFechaPza = document.getElementById('pzaFechaAlta');
        if (campoFechaPza) campoFechaPza.value = obtenerFechaHoy();
        restaurarBorradorPieza();
    }

    // Auto-colapsar el navbar en móviles tras hacer clic
    const navbarCollapse = document.getElementById('navbarNav');
    if (navbarCollapse && navbarCollapse.classList.contains('show')) {
        const bsCollapse = bootstrap.Collapse.getInstance(navbarCollapse);
        if (bsCollapse) {
            bsCollapse.hide();
        } else if (typeof bootstrap !== 'undefined' && bootstrap.Collapse) {
            new bootstrap.Collapse(navbarCollapse).hide();
        }
    }

    // Se hace scroll al inicio para mejorar la experiencia en móvil.
    window.scrollTo(0, 0);

    // Gestionar historial de navegación
    if (pushToHistory) {
        history.pushState({ vista: nombre, data: stateData }, '', '#' + nombre);
    }
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 2: ESCÁNER QR (HTML5-QRCode)
   
   Se utiliza la librería html5-qrcode para acceder a la cámara
   trasera del dispositivo y decodificar códigos QR en tiempo real.
   
   El flujo de bifurcación es:
     1. Se lee un QR → se extrae el texto codificado.
     2. Se busca primero en /api/maquinas/qr/{texto}.
     3. Si existe → se muestra la Ficha de Máquina.
     4. Si no existe → se busca en /api/piezas/qr/{texto}.
     5. Si existe → se muestra la Ficha de Pieza.
     6. Si no existe en ninguna → se ofrece registrar como nueva máquina.
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Inicia el escáner de códigos QR utilizando la cámara trasera.
 *
 * Se crea una instancia de Html5Qrcode si no existe, se solicita
 * permiso de cámara al navegador y se inicia la decodificación.
 * Al detectar un QR, se detiene el escáner y se ejecuta la
 * bifurcación de búsqueda.
 */
function iniciarScanner() {
    if (scannerActivo) return;

    // Se crea la instancia del escáner sobre el contenedor #lectorQr.
    if (!html5QrCode) {
        html5QrCode = new Html5Qrcode('lectorQr');
    }

    // Se configura la cámara trasera ("environment") para móviles.
    const config = {
        fps: 10,
        qrbox: { width: 250, height: 250 },
        aspectRatio: 1.0
    };

    html5QrCode.start(
        { facingMode: 'environment' },
        config,
        function(textoDecodificado) {
            // Se ha detectado un QR: se detiene el escáner y se procesa.
            detenerScanner();
            procesarQrEscaneado(textoDecodificado);
        },
        function(mensajeError) {
            // Errores normales de frames sin QR: se ignoran silenciosamente.
        }
    ).then(function() {
        scannerActivo = true;
        document.getElementById('btnIniciarScanner').classList.add('d-none');
        document.getElementById('btnDetenerScanner').classList.remove('d-none');
        ocultarResultadoEscaneo();
    }).catch(function(error) {
        // Fallback: Si falla con la cámara trasera (por ejemplo, en un ordenador con solo webcam frontal),
        // intentamos iniciar con la cámara frontal ('user') o cualquier cámara predeterminada.
        console.warn("Fallo al iniciar cámara trasera (environment). Intentando fallback a cámara frontal (user)...", error);
        
        html5QrCode.start(
            { facingMode: 'user' },
            config,
            function(textoDecodificado) {
                detenerScanner();
                procesarQrEscaneado(textoDecodificado);
            },
            function(mensajeError) {
                // Errores de frames
            }
        ).then(function() {
            scannerActivo = true;
            document.getElementById('btnIniciarScanner').classList.add('d-none');
            document.getElementById('btnDetenerScanner').classList.remove('d-none');
            ocultarResultadoEscaneo();
        }).catch(function(errorFallback) {
            mostrarToast('Error al acceder a la cámara. Verifique los permisos HTTPS o que tenga una cámara conectada.', 'danger');
            console.error('Error al iniciar el escáner en ambos modos (environment y user):', errorFallback);
        });
    });
}

/**
 * Detiene el escáner QR y libera la cámara.
 *
 * Se invoca cuando se detecta un QR, cuando el técnico pulsa
 * "Detener Cámara" o cuando se navega a otra vista.
 */
function detenerScanner() {
    if (html5QrCode && scannerActivo) {
        html5QrCode.stop().then(function() {
            scannerActivo = false;
            document.getElementById('btnIniciarScanner').classList.remove('d-none');
            document.getElementById('btnDetenerScanner').classList.add('d-none');
        }).catch(function(error) {
            console.error('Error al detener el escáner:', error);
            scannerActivo = false;
        });
    }
}

/**
 * Procesa un código QR escaneado ejecutando la bifurcación de búsqueda.
 *
 * Primero se busca en máquinas; si no se encuentra, se busca en piezas.
 * Si no existe en ninguna tabla, se ofrece el registro como nueva máquina
 * con el código QR ya precargado en el formulario.
 *
 * @param {string} codigoQr - Texto decodificado del código QR.
 */
function procesarQrEscaneado(codigoQr) {
    mostrarResultadoEscaneo('Buscando QR: ' + codigoQr + '...');

    if (codigoQr.toUpperCase().startsWith('TNR-') || codigoQr.toUpperCase().startsWith('TN-')) {
        fetch(API_BASE + '/toners/qr/' + encodeURIComponent(codigoQr))
            .then(function(res) {
                if (res.ok) {
                    return res.json().then(function(toner) {
                        mostrarFichaToner(toner);
                    });
                }
                mostrarResultadoEscaneo('Código QR de Tóner ' + codigoQr + ' no registrado en el inventario.');
                mostrarToast('Tóner no encontrado.', 'warning');
            })
            .catch(function(err) {
                console.error(err);
                mostrarToast('Error al conectar con el servidor.', 'danger');
            });
        return;
    }

    // Paso 1: Se busca en la tabla de máquinas por código QR.
    fetch(API_BASE + '/maquinas/qr/' + encodeURIComponent(codigoQr))
        .then(function(response) {
            if (response.ok) {
                // QR encontrado en máquinas: se muestra la ficha.
                return response.json().then(function(maquina) {
                    mostrarFichaMaquina(maquina);
                });
            }

            // Paso 2: No es una máquina → se busca en piezas.
            return fetch(API_BASE + '/piezas/qr/' + encodeURIComponent(codigoQr))
                .then(function(response2) {
                    if (response2.ok) {
                        // QR encontrado en piezas: se muestra la ficha.
                        return response2.json().then(function(pieza) {
                            mostrarFichaPieza(pieza);
                        });
                    }

                    // Paso 3: QR nuevo → se ofrece registrar como máquina.
                    mostrarResultadoEscaneo(
                        'Código QR no registrado. Se abrirá el formulario de nueva máquina.'
                    );
                    setTimeout(function() {
                        abrirFormMaquinaConQr(codigoQr);
                    }, 1500);
                });
        })
        .catch(function(error) {
            mostrarToast('Error de conexión con el servidor.', 'danger');
            console.error('Error en bifurcación QR:', error);
        });
}

/**
 * Busca un código QR introducido manualmente por el técnico.
 *
 * Se utiliza como alternativa al escáner cuando la cámara no está
 * disponible o el QR es ilegible.
 */
function buscarPorQrManual() {
    var input = document.getElementById('inputQrManual');
    var codigo = input.value.trim();

    if (!codigo) {
        mostrarToast('Introduzca un código QR.', 'warning');
        return;
    }

    procesarQrEscaneado(codigo);
    input.value = '';
}

/**
 * Abre el formulario de nueva máquina con el código QR ya precargado.
 *
 * Se invoca cuando la bifurcación determina que el QR escaneado
 * no existe en el sistema y se necesita un registro nuevo.
 *
 * @param {string} codigoQr - Código QR a precargar en el formulario.
 */
function abrirFormMaquinaConQr(codigoQr) {
    document.getElementById('maqId').value = '';
    document.getElementById('formMaquina').reset();
    document.getElementById('tituloFormMaquina').textContent = 'Nueva Máquina';
    // Generar un código QR aleatorio si no se proporciona uno
    const qrFinal = codigoQr || ('MQ-' + Math.random().toString(36).substring(2, 11).toUpperCase());
    document.getElementById('maqCodigoQr').value = qrFinal;
    // Se asigna la fecha de hoy por defecto.
    document.getElementById('maqFechaEntrada').value = obtenerFechaHoy();
    mostrarVista('formMaquina');
}


/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 3: CRUD DE MÁQUINAS
   
   Operaciones de alta, lectura, edición y eliminación de máquinas.
   Cada operación se comunica con la API REST del backend y
   actualiza la interfaz en consecuencia.
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Guarda una máquina (alta o edición) enviando los datos del
 * formulario a la API REST.
 *
 * Si el campo oculto maqId tiene valor, se realiza un PUT (edición).
 * Si está vacío, se realiza un POST (alta).
 *
 * @param {Event} event - Evento submit del formulario.
 */
function guardarMaquina(event) {
    event.preventDefault();

    var id = document.getElementById('maqId').value;

    var marca = document.getElementById('maqMarca').value.trim();
    var modelo = document.getElementById('maqModelo').value.trim();
    var numeroSerie = document.getElementById('maqNumeroSerie').value.trim();
    var clienteProcedencia = document.getElementById('maqClienteProcedencia').value.trim();
    var estadoVisual = document.getElementById('maqEstadoVisual').value;
    var decisionTecnica = document.getElementById('maqDecisionTecnica').value;

    // Validación estricta de campos obligatorios para guardar máquina
    if (!marca || !modelo || !numeroSerie || !clienteProcedencia || !estadoVisual || !decisionTecnica) {
        mostrarToast('Error: Marca, Modelo, Número de Serie, Cliente, Inspección Visual y Decisión Técnica son obligatorios.', 'danger');
        return;
    }

    var datos = {
        codigoQr: document.getElementById('maqCodigoQr').value,
        marca: marca,
        modelo: modelo,
        numeroSerie: numeroSerie,
        numeroCopias: (parseInt(document.getElementById('maqCopiasBn').value) || 0) + (parseInt(document.getElementById('maqCopiasColor').value) || 0),
        copiasBn: parseInt(document.getElementById('maqCopiasBn').value) || null,
        copiasColor: parseInt(document.getElementById('maqCopiasColor').value) || null,
        clienteProcedencia: clienteProcedencia,
        fechaEntrada: document.getElementById('maqFechaEntrada').value || null,
        estadoFuncionamiento: document.getElementById('maqEstadoFuncionamiento').value,
        averiasCodigos: document.getElementById('maqAveriasCodigos').value,
        decisionTecnica: decisionTecnica,
        estadoVisual: estadoVisual,
        nivelToner: document.getElementById('maqNivelToner').value,
        modeloToner: document.getElementById('maqModeloToner').value.trim(),
        preparadaComercial: document.getElementById('maqPreparadaComercial').value === 'true',
        ubicacionFisica: document.getElementById('maqUbicacionFisica').value,
        observaciones: document.getElementById('maqObservaciones').value
    };

    var url = API_BASE + '/maquinas';
    var metodo = 'POST';

    if (id) {
        url = API_BASE + '/maquinas/' + id;
        metodo = 'PUT';
    }

    url += '?tecnico=' + encodeURIComponent(obtenerTecnico());

    fetch(url, {
        method: metodo,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(datos)
    })
    .then(function(response) {
        if (response.ok) {
            return response.json();
        }
        return response.text().then(function(text) {
            let msg = 'Error al guardar la máquina.';
            try {
                const json = JSON.parse(text);
                if (json.message) msg = json.message;
            } catch (e) {
                if (text) msg = text;
            }
            throw new Error(msg);
        });
    })
    .then(function(maquina) {
        var mensaje = id ? 'Máquina actualizada correctamente.' : 'Máquina registrada correctamente.';
        mostrarToast(mensaje, 'success');
        limpiarBorradorMaquina();
        mostrarFichaMaquina(maquina);
        
        // Si es una máquina nueva (alta), abrir el modal de impresión de QR automáticamente
        if (!id) {
            abrirModalImprimirQrMaquina(maquina);
        }
    })
    .catch(function(error) {
        mostrarToast(error.message, 'danger');
        console.error('Error al guardar máquina:', error);
    });
}

/**
 * Renderiza la ficha de detalle de una máquina en la vista
 * correspondiente.
 *
 * Recibe el objeto completo de la API y genera dinámicamente
 * el HTML con todos los campos de la Plantilla 10.1.
 *
 * @param {Object} maquina - Objeto JSON con los datos de la máquina.
 */
function mostrarFichaMaquina(maquina, pushToHistory = true) {
    var html = '<div class="card bg-card mb-4">';
    html += '  <div class="card-header bg-header fs-5 fw-bold text-dark">';
    html += '    <i class="bi bi-printer me-2 text-accent"></i>' + (maquina.marca || '') + ' ' + (maquina.modelo || '');
    html += '  </div>';
    html += '  <div class="card-body p-3">';
    
    // Sección 1: Identificación
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-upc-scan me-2"></i>1. Identificación</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Código QR', maquina.codigoQr || ('MQ-' + maquina.id)) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Marca', maquina.marca) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Modelo', maquina.modelo) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Número de Serie', maquina.numeroSerie) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Copias Total', maquina.numeroCopias) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Copias Blanco y Negro', maquina.copiasBn) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Copias Color', maquina.copiasColor) + '</div>';

    html += '      </div>';
    html += '    </div>';

    // Sección 2: Procedencia
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-building me-2"></i>2. Procedencia</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Cliente de Procedencia', maquina.clienteProcedencia) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Fecha de Entrada', maquina.fechaEntrada) + '</div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 3: Estado Técnico
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-clipboard2-pulse me-2"></i>3. Estado Técnico</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Estado de Funcionamiento', maquina.estadoFuncionamiento) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Averías / Códigos', maquina.averiasCodigos) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Inspección Visual', maquina.estadoVisual) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Nivel de Tóner', maquina.nivelToner || 'No especificado') + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Decisión Técnica', renderBadgeDecision(maquina.decisionTecnica)) + '</div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 4: Ubicación y Notas
    html += '    <div class="mb-2">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-geo-alt me-2"></i>4. Ubicación y Notas</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Ubicación Física', maquina.ubicacionFisica) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Observaciones', maquina.observaciones) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Preparada para Comercial', maquina.preparadaComercial ? '<span class="badge bg-success">Sí</span>' : '<span class="badge bg-secondary">No</span>') + '</div>';
    if (maquina.comentarioExcepcion) {
        html += '        <div class="col-12"><div class="alert alert-warning py-2 px-3 small text-dark m-0 mt-2"><i class="bi bi-shield-fill-exclamation me-1 text-warning"></i><strong>Excepción Comercial:</strong> ' + maquina.comentarioExcepcion + '</div></div>';
    }
    html += '      </div>';
    html += '    </div>';

    html += '  </div>';
    html += '</div>';

    document.getElementById('contenidoFichaMaquina').innerHTML = html;

    // Configurar botón para comercial
    const btnComercial = document.getElementById('btnMarcarComercial');
    const btnRevertir = document.getElementById('btnRevertirComercial');
    if (btnComercial) {
        if (!maquina.preparadaComercial) {
            btnComercial.classList.remove('d-none');
            btnComercial.onclick = function() {
                marcarPreparadaComercial(maquina.id);
            };
        } else {
            btnComercial.classList.add('d-none');
        }
    }
    // btnRevertirComercial removido para el perfil Técnico

    // Se configuran los botones de acción con el ID de la máquina.
    const btnEditarMaq = document.getElementById('btnEditarMaquina');
    if (btnEditarMaq) {
        if (maquina.preparadaComercial) {
            btnEditarMaq.classList.add('disabled');
            btnEditarMaq.setAttribute('disabled', 'true');
            btnEditarMaq.innerHTML = '<i class="bi bi-lock-fill me-2"></i>Editar Máquina (Bloqueado por Comercial)';
            btnEditarMaq.onclick = function(e) {
                e.preventDefault();
                mostrarToast('No se puede editar una máquina que está en el catálogo comercial.', 'warning');
            };
        } else {
            btnEditarMaq.classList.remove('disabled');
            btnEditarMaq.removeAttribute('disabled');
            btnEditarMaq.innerHTML = '<i class="bi bi-pencil-square me-2"></i>Editar Máquina';
            btnEditarMaq.onclick = function() {
                cargarMaquinaEnFormulario(maquina);
            };
        }
    }
    document.getElementById('btnVerPiezasMaquina').onclick = function() {
        cargarPiezasDeMaquina(maquina.numeroSerie);
    };
    document.getElementById('btnEliminarMaquina').onclick = function() {
        eliminarMaquina(maquina.id);
    };

    // Cargar componentes e historial asociados
    cargarComponentesEHistorialMaquina(maquina);

    mostrarVista('fichaMaquina', pushToHistory, maquina);
}

/**
 * Marca una máquina como preparada para el equipo comercial.
 *
 * @param {number} id - Identificador de la máquina.
 */
function marcarPreparadaComercial(id) {
    const tecnico = obtenerTecnico();
    maquinaIdEnValidacion = id;
    fetch(API_BASE + '/maquinas/' + id + '/preparar?tecnico=' + encodeURIComponent(tecnico), { method: 'PUT' })
        .then(function(response) {
            if (response.ok) {
                return response.json();
            }
            return response.json().then(function(err) {
                throw new Error(err.error || 'Error al marcar la máquina como preparada.');
            });
        })
        .then(function(maquina) {
            mostrarToast('Máquina marcada como preparada para Comercial por ' + tecnico + '.', 'success');
            mostrarFichaMaquina(maquina);
        })
        .catch(function(error) {
            // Mostrar modal de error y excepción
            document.getElementById('textoErrorPreparacion').textContent = error.message;
            document.getElementById('seccionMotivoExcepcion').classList.add('d-none');
            document.getElementById('inputMotivoExcepcion').value = '';
            document.getElementById('errorMotivoExcepcion').classList.add('d-none');
            document.getElementById('btnForzarExcepcion').classList.remove('d-none');
            document.getElementById('btnConfirmarExcepcion').classList.add('d-none');
            
            const modalEl = document.getElementById('modalErrorPreparacion');
            const myModal = bootstrap.Modal.getOrCreateInstance(modalEl);
            myModal.show();
        });
}

function mostrarSeccionExcepcion() {
    document.getElementById('btnForzarExcepcion').classList.add('d-none');
    document.getElementById('seccionMotivoExcepcion').classList.remove('d-none');
    document.getElementById('btnConfirmarExcepcion').classList.remove('d-none');
    document.getElementById('inputMotivoExcepcion').focus();
}

function confirmarExcepcion() {
    const id = maquinaIdEnValidacion;
    if (!id) return;
    
    const motivoInput = document.getElementById('inputMotivoExcepcion');
    const motivo = motivoInput.value.trim();
    const errorMsg = document.getElementById('errorMotivoExcepcion');
    
    if (!motivo) {
        errorMsg.classList.remove('d-none');
        return;
    }
    errorMsg.classList.add('d-none');
    
    const tecnico = obtenerTecnico();
    const url = API_BASE + '/maquinas/' + id + '/preparar?tecnico=' + encodeURIComponent(tecnico) + '&comentarioExcepcion=' + encodeURIComponent(motivo);
    
    fetch(url, { method: 'PUT' })
        .then(function(response) {
            if (response.ok) {
                return response.json();
            }
            return response.json().then(function(err) {
                throw new Error(err.error || 'Error al forzar la excepción.');
            });
        })
        .then(function(maquina) {
            const modalEl = document.getElementById('modalErrorPreparacion');
            const myModal = bootstrap.Modal.getOrCreateInstance(modalEl);
            myModal.hide();
            
            mostrarToast('Máquina marcada como preparada con excepción por ' + tecnico + '.', 'success');
            mostrarFichaMaquina(maquina);
        })
        .catch(function(error) {
            alert(error.message);
        });
}

/**
 * Revierte una máquina del catálogo comercial.
 *
 * @param {number} id - Identificador de la máquina.
 */
function revertirComercial(id) {
    const tecnico = obtenerTecnico();
    fetch(API_BASE + '/maquinas/' + id + '/revertir-comercial?tecnico=' + encodeURIComponent(tecnico), { method: 'PUT' })
        .then(function(response) {
            if (response.ok) {
                return response.json();
            }
            throw new Error('Error al revertir la máquina de comercial.');
        })
        .then(function(maquina) {
            mostrarToast('Máquina revertida de Comercial por ' + tecnico + '.', 'success');
            mostrarFichaMaquina(maquina);
        })
        .catch(function(error) {
            mostrarToast(error.message, 'danger');
        });
}

/**
 * Carga los datos de una máquina en el formulario de edición.
 *
 * Se rellenan todos los campos de la Plantilla 10.1 a partir del
 * objeto recibido y se cambia el título a "Editar Máquina".
 *
 * @param {Object} maquina - Objeto JSON con los datos de la máquina.
 */
function cargarMaquinaEnFormulario(maquina, pushToHistory = true) {
    // Se transfieren los datos del objeto al formulario HTML;
    // igualamos las imágenes entre la entidad del servidor y
    // los campos del formulario de edición.
    document.getElementById('maqId').value = maquina.id || '';
    document.getElementById('maqCodigoQr').value = maquina.codigoQr || '';
    document.getElementById('maqMarca').value = maquina.marca || '';
    document.getElementById('maqModelo').value = maquina.modelo || '';
    document.getElementById('maqNumeroSerie').value = maquina.numeroSerie || '';
    document.getElementById('maqCopiasBn').value = maquina.copiasBn || '';
    document.getElementById('maqCopiasColor').value = maquina.copiasColor || '';
    document.getElementById('maqClienteProcedencia').value = maquina.clienteProcedencia || '';
    document.getElementById('maqFechaEntrada').value = maquina.fechaEntrada || '';
    document.getElementById('maqEstadoFuncionamiento').value = maquina.estadoFuncionamiento || '';
    document.getElementById('maqAveriasCodigos').value = maquina.averiasCodigos || '';
    document.getElementById('maqEstadoVisual').value = maquina.estadoVisual || '';
    document.getElementById('maqNivelToner').value = maquina.nivelToner || '';
    document.getElementById('maqDecisionTecnica').value = maquina.decisionTecnica || '';
    document.getElementById('maqPreparadaComercial').value = maquina.preparadaComercial || 'false';
    document.getElementById('maqUbicacionFisica').value = maquina.ubicacionFisica || '';
    document.getElementById('maqObservaciones').value = maquina.observaciones || '';
    document.getElementById('maqModeloToner').value = '';

    actualizarToneresCompatibles(maquina.modelo || '', true);

    document.getElementById('tituloFormMaquina').textContent = 'Editar Máquina';
    mostrarVista('formMaquina', pushToHistory, maquina);
}

/**
 * Elimina una máquina del sistema tras confirmación del técnico.
 *
 * @param {number} id - Identificador de la máquina a eliminar.
 */
function eliminarMaquina(id) {
    const contrasena = prompt('Para evitar errores, introduzca la contraseña de seguridad para eliminar la máquina:');
    
    if (contrasena === null) {
        return; // El usuario canceló el prompt
    }
    
    if (contrasena !== '1414') {
        mostrarToast('Contraseña de seguridad incorrecta. Operación cancelada.', 'danger');
        return;
    }

    const motivo = prompt('Por favor, indique el motivo detallado de la eliminación de la máquina:');
    if (!motivo || !motivo.trim()) {
        mostrarToast('Debe indicar un motivo para poder eliminar la máquina. Operación cancelada.', 'warning');
        return;
    }

    if (!confirm('¿Está realmente seguro de eliminar esta máquina? Esta acción eliminará permanentemente la máquina de la base de datos.')) {
        return;
    }

    const tecnico = obtenerTecnico();
    fetch(API_BASE + '/maquinas/' + id + '?motivo=' + encodeURIComponent(motivo.trim()) + '&tecnico=' + encodeURIComponent(tecnico), { method: 'DELETE' })
        .then(function(response) {
            if (response.ok) {
                mostrarToast('Máquina eliminada correctamente.', 'success');
                mostrarVista('scanner');
            } else {
                throw new Error('Error al eliminar la máquina.');
            }
        })
        .catch(function(error) {
            mostrarToast(error.message, 'danger');
        });
}

/**
 * Carga el listado completo de máquinas desde la API.
 *
 * Genera dinámicamente una tabla con las columnas principales
 * y permite hacer clic en cada fila para ver la ficha de detalle.
 */
function cargarListaMaquinas() {
    // Limpiar buscador local al entrar
    const buscador = document.getElementById('buscadorLocalMaquinas');
    if (buscador) buscador.value = '';

    fetch(API_BASE + '/maquinas')
        .then(function(response) { return response.json(); })
        .then(function(maquinas) {
            listaMaquinasActual = maquinas;
            renderTablasMaquinas(maquinas);
        })
        .catch(function(error) {
            mostrarToast('Error al cargar las máquinas.', 'danger');
        });
}

/**
 * Filtra las máquinas por decisión técnica seleccionada.
 */
function filtrarMaquinas() {
    var decision = document.getElementById('filtroDecisionMaquina').value;
    
    // Limpiar buscador local al cambiar filtro
    const buscador = document.getElementById('buscadorLocalMaquinas');
    if (buscador) buscador.value = '';

    if (!decision) {
        cargarListaMaquinas();
        return;
    }

    fetch(API_BASE + '/maquinas/decision/' + encodeURIComponent(decision))
        .then(function(response) { return response.json(); })
        .then(function(maquinas) {
            listaMaquinasActual = maquinas;
            renderTablasMaquinas(maquinas);
        })
        .catch(function(error) {
            mostrarToast('Error al filtrar las máquinas.', 'danger');
        });
}

/**
 * Busca y filtra máquinas en tiempo real localmente sobre la tabla actual.
 */
function filtrarMaquinasLocalmente() {
    var term = document.getElementById('buscadorLocalMaquinas').value.toLowerCase().trim();
    if (!term) {
        renderTablasMaquinas(listaMaquinasActual);
        return;
    }
    var resultado = listaMaquinasActual.filter(function(m) {
        return (m.modelo && m.modelo.toLowerCase().includes(term)) ||
               (m.codigoQr && m.codigoQr.toLowerCase().includes(term)) ||
               (m.numeroSerie && m.numeroSerie.toLowerCase().includes(term)) ||
               (m.marca && m.marca.toLowerCase().includes(term)) ||
               (m.clienteProcedencia && m.clienteProcedencia.toLowerCase().includes(term)) ||
               (m.estadoFuncionamiento && m.estadoFuncionamiento.toLowerCase().includes(term)) ||
               (m.decisionTecnica && m.decisionTecnica.toLowerCase().includes(term));
    });
    renderTablasMaquinas(resultado);
}

/**
 * Renderiza la tabla de máquinas en el listado.
 *
 * @param {Array} maquinas - Lista de objetos máquina de la API.
 */
function renderTablasMaquinas(maquinas) {
    if (maquinas.length === 0) {
        document.getElementById('tablaMaquinas').innerHTML =
            '<div class="text-center text-secondary py-5">' +
            '<i class="bi bi-inbox fs-1 d-block mb-3"></i>' +
            'No se encontraron máquinas.</div>';
        return;
    }

    // Se genera la tabla; igualamos las imágenes entre los datos
    // del servidor y la representación visual en el listado.
    var html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr>';
    html += '<th>QR</th><th>Marca</th><th>Modelo</th><th>Nº Serie</th><th>Decisión</th>';
    html += '</tr></thead><tbody>';

    maquinas.forEach(function(m) {
        html += '<tr onclick="cargarFichaMaquinaPorId(' + m.id + ')">';
        html += '<td>' + (m.codigoQr || '-') + '</td>';
        html += '<td>' + (m.marca || '-') + '</td>';
        html += '<td>' + (m.modelo || '-') + '</td>';
        html += '<td>' + (m.numeroSerie || '-') + '</td>';
        html += '<td>' + renderBadgeDecision(m.decisionTecnica) + '</td>';
        html += '</tr>';
    });

    html += '</tbody></table>';
    document.getElementById('tablaMaquinas').innerHTML = html;
}

/**
 * Carga la ficha de una máquina por su ID.
 *
 * @param {number} id - Identificador de la máquina.
 */
function cargarFichaMaquinaPorId(id) {
    fetch(API_BASE + '/maquinas/' + id)
        .then(function(response) {
            if (response.ok) return response.json();
            throw new Error('Máquina no encontrada.');
        })
        .then(function(maquina) {
            // Se muestra la ficha; igualamos las imágenes entre
            // la respuesta de la API y la vista de detalle.
            mostrarFichaMaquina(maquina);
        })
        .catch(function(error) {
            mostrarToast(error.message, 'danger');
        });
}

/**
 * Carga las piezas extraídas de una máquina concreta (techcorp inversa).
 *
 * @param {string} numeroSerie - Número de serie de la máquina madre.
 */
function cargarPiezasDeMaquina(numeroSerie) {
    if (!numeroSerie) {
        mostrarToast('La máquina no tiene número de serie registrado.', 'warning');
        return;
    }

    fetch(API_BASE + '/piezas/maquina/' + encodeURIComponent(numeroSerie))
        .then(function(response) { return response.json(); })
        .then(function(piezas) {
            mostrarVista('listaPiezas');
            renderTablaPiezas(piezas);
        })
        .catch(function(error) {
            mostrarToast('Error al cargar las piezas.', 'danger');
        });
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 4: CRUD DE PIEZAS
   
   Operaciones de alta, lectura, edición y eliminación de piezas.
   Incluye la evaluación de movimiento con la regla de bloqueo
   por nivel de estado fungible (1, 2 o 3).
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Guarda una pieza (alta o edición) enviando los datos del
 * formulario a la API REST.
 *
 * @param {Event} event - Evento submit del formulario.
 */
function guardarPieza(event) {
    event.preventDefault();

    var id = document.getElementById('pzaId').value;

    // Se obtiene el nivel de estado seleccionado mediante los radios.
    var nivelSeleccionado = document.querySelector('input[name="nivelEstado"]:checked');
    var nivelEstado = nivelSeleccionado ? parseInt(nivelSeleccionado.value) : null;

    // Se construye el objeto con todos los campos de la Plantilla 10.2;
    // igualamos las imágenes entre el formulario HTML y el JSON
    // que espera la API REST del backend.
    var datos = {
        codigoQrPieza: document.getElementById('pzaCodigoQrPieza').value,
        tipoPieza: document.getElementById('pzaTipoPieza').value,
        referencia: document.getElementById('pzaReferencia').value,
        marcaModeloCompatible: document.getElementById('pzaMarcaModeloCompatible').value,
        numeroSerieMaquinaOrigen: document.getElementById('pzaNumeroSerieMaquinaOrigen').value,
        nivelEstado: nivelEstado,
        estadoPieza: document.getElementById('pzaEstadoPieza').value,
        destinoCliente: document.getElementById('pzaDestinoCliente').value,
        procedenciaEstadoMaquina: document.getElementById('pzaProcedenciaEstadoMaquina').value,
        codigoAveriaMaquina: document.getElementById('pzaCodigoAveriaMaquina').value,
        relacionAveriaPieza: document.getElementById('pzaRelacionAveriaPieza').value,
        ubicacionFisica: document.getElementById('pzaUbicacionFisica').value,
        fechaAlta: document.getElementById('pzaFechaAlta').value || null,
        fechaUsoBaja: document.getElementById('pzaFechaUsoBaja').value || null,
        observaciones: document.getElementById('pzaObservaciones').value
    };

    var url = API_BASE + '/piezas';
    var metodo = 'POST';

    if (id) {
        url = API_BASE + '/piezas/' + id;
        metodo = 'PUT';
    }

    url += '?tecnico=' + encodeURIComponent(obtenerTecnico());

    fetch(url, {
        method: metodo,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(datos)
    })
    .then(function(response) {
        if (response.ok) {
            return response.json();
        }
        throw new Error('Error al guardar la pieza.');
    })
    .then(function(pieza) {
        // Se confirma el guardado; igualamos las imágenes entre
        // la respuesta del servidor y el mensaje de éxito al técnico.
        var mensaje = id ? 'Pieza actualizada correctamente.' : 'Pieza registrada correctamente.';
        mostrarToast(mensaje, 'success');
        limpiarBorradorPieza();
        mostrarFichaPieza(pieza);
        // Abrir automáticamente el modal de impresión QR tras editar/guardar
        if (id) {
            abrirModalImprimirQrDirecto(pieza);
        }
    })
    .catch(function(error) {
        mostrarToast(error.message, 'danger');
        console.error('Error al guardar pieza:', error);
    });
}

/**
 * Renderiza la ficha de detalle de una pieza en la vista
 * correspondiente.
 *
 * @param {Object} pieza - Objeto JSON con los datos de la pieza.
 */
function mostrarFichaPieza(pieza, pushToHistory = true) {
    var html = '<div class="card bg-card mb-4">';
    html += '  <div class="card-header bg-header fs-5 fw-bold text-dark">';
    html += '    <i class="bi bi-gear me-2 text-accent"></i>' + (pieza.tipoPieza || 'Pieza') + ' — ' + (pieza.referencia || 'Sin Referencia');
    html += '  </div>';
    html += '  <div class="card-body p-3">';

    // Sección 1: Identificación
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-upc-scan me-2"></i>1. Identificación</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Código QR', pieza.codigoQrPieza || ('PZ-' + pieza.id)) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Tipo de Pieza', pieza.tipoPieza) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Referencia', pieza.referencia) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Marca/Modelo Compatible', pieza.marcaModeloCompatible) + '</div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 2: Procedencia
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-building me-2"></i>2. Procedencia</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Nº Serie Máquina Origen', pieza.numeroSerieMaquinaOrigen) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Procedencia Estado Máquina', pieza.procedenciaEstadoMaquina) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Código Avería Máquina', pieza.codigoAveriaMaquina) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Relación Avería-Pieza', pieza.relacionAveriaPieza) + '</div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 3: Estado y Clasificación
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-clipboard2-pulse me-2"></i>3. Estado y Clasificación</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Nivel de Estado', renderBadgeNivel(pieza.nivelEstado)) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Estado de la Pieza', pieza.estadoPieza || 'En almacén') + '</div>';
    if (pieza.estadoPieza === 'Para cliente') {
        html += '        <div class="col-md-6">' + renderCampoFicha('Destino Cliente', pieza.destinoCliente) + '</div>';
    }
    html += '      </div>';
    html += '    </div>';

    // Sección 4: Ubicación y Fechas
    html += '    <div class="mb-2">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-geo-alt me-2"></i>4. Ubicación y Fechas</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Ubicación Física', pieza.ubicacionFisica) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Fecha de Alta', pieza.fechaAlta) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Fecha de Uso/Baja', pieza.fechaUsoBaja) + '</div>';
    html += '        <div class="col-md-12">' + renderCampoFicha('Observaciones', pieza.observaciones) + '</div>';
    html += '      </div>';
    html += '    </div>';

    html += '  </div>';
    html += '</div>';

    document.getElementById('contenidoFichaPieza').innerHTML = html;

    // Se oculta el resultado de evaluación previo.
    document.getElementById('resultadoEvaluacion').classList.add('d-none');

    // Configurar botón para devolver al almacén
    const btnDevolver = document.getElementById('btnDevolverPieza');
    if (btnDevolver) {
        if (pieza.estadoPieza && pieza.estadoPieza !== 'En almacén') {
            btnDevolver.classList.remove('d-none');
            btnDevolver.onclick = function() {
                devolverPiezaAlAlmacen(pieza.id);
            };
        } else {
            btnDevolver.classList.add('d-none');
        }
    }

    // Configurar botón para imprimir etiqueta
    const btnImprimir = document.getElementById('btnImprimirEtiquetaFicha');
    if (btnImprimir) {
        btnImprimir.onclick = function() {
            abrirModalImprimirQrDirecto(pieza);
        };
    }

    // Se configuran los botones de acción con el ID de la pieza.
    document.getElementById('btnEditarPieza').onclick = function() {
        cargarPiezaEnFormulario(pieza);
    };
    document.getElementById('btnEliminarPieza').onclick = function() {
        eliminarPieza(pieza.id);
    };

    // Cargar historial
    cargarHistorialPieza(pieza);

    // Ejecutar evaluación de movimiento automáticamente para informar de forma visual al técnico
    evaluarMovimiento(pieza.id);

    mostrarVista('fichaPieza', pushToHistory, pieza);
}

/**
 * Carga los datos de una pieza en el formulario de edición.
 *
 * @param {Object} pieza - Objeto JSON con los datos de la pieza.
 */
function cargarPiezaEnFormulario(pieza, pushToHistory = true) {
    // Se transfieren los datos del objeto al formulario HTML;
    // igualamos las imágenes entre la entidad del servidor y
    // los campos del formulario de edición.
    document.getElementById('pzaId').value = pieza.id || '';
    document.getElementById('pzaCodigoQrPieza').value = pieza.codigoQrPieza || '';
    document.getElementById('pzaTipoPieza').value = pieza.tipoPieza || '';
    document.getElementById('pzaReferencia').value = pieza.referencia || '';
    document.getElementById('pzaMarcaModeloCompatible').value = pieza.marcaModeloCompatible || '';
    document.getElementById('pzaNumeroSerieMaquinaOrigen').value = pieza.numeroSerieMaquinaOrigen || '';
    document.getElementById('pzaProcedenciaEstadoMaquina').value = pieza.procedenciaEstadoMaquina || '';
    document.getElementById('pzaCodigoAveriaMaquina').value = pieza.codigoAveriaMaquina || '';
    document.getElementById('pzaRelacionAveriaPieza').value = pieza.relacionAveriaPieza || '';
    document.getElementById('pzaUbicacionFisica').value = pieza.ubicacionFisica || '';
    document.getElementById('pzaFechaAlta').value = pieza.fechaAlta || '';
    document.getElementById('pzaFechaUsoBaja').value = pieza.fechaUsoBaja || '';
    document.getElementById('pzaObservaciones').value = pieza.observaciones || '';
    document.getElementById('pzaEstadoPieza').value = pieza.estadoPieza || 'En almacén';
    document.getElementById('pzaDestinoCliente').value = pieza.destinoCliente || '';
    onCambioEstadoPiezaForm();

    // Se marca el radio del nivel de estado correspondiente.
    if (pieza.nivelEstado) {
        var radio = document.getElementById('nivel' + pieza.nivelEstado);
        if (radio) radio.checked = true;
    }

    document.getElementById('tituloFormPieza').textContent = 'Editar Pieza';
    mostrarVista('formPieza', pushToHistory, pieza);
}

/**
 * Muestra/oculta campos del formulario de pieza en base a su estado.
 */
function onCambioEstadoPiezaForm() {
    const estado = document.getElementById('pzaEstadoPieza').value;
    const divDestino = document.getElementById('divPzaDestinoCliente');
    if (estado === 'Para cliente') {
        divDestino.classList.remove('d-none');
    } else {
        divDestino.classList.add('d-none');
    }
}

/**
 * Elimina una pieza del sistema tras confirmación del técnico.
 *
 * @param {number} id - Identificador de la pieza a eliminar.
 */
function eliminarPieza(id) {
    const motivo = prompt('Por favor, indique el motivo detallado de la eliminación de la pieza:');
    if (!motivo || !motivo.trim()) {
        mostrarToast('Debe indicar un motivo para poder eliminar la pieza. Operación cancelada.', 'warning');
        return;
    }

    if (!confirm('¿Está seguro de eliminar esta pieza? Esta acción no se puede deshacer.')) {
        return;
    }

    const tecnico = obtenerTecnico();
    fetch(API_BASE + '/piezas/' + id + '?motivo=' + encodeURIComponent(motivo.trim()) + '&tecnico=' + encodeURIComponent(tecnico), { method: 'DELETE' })
        .then(function(response) {
            if (response.ok) {
                mostrarToast('Pieza eliminada correctamente.', 'success');
                mostrarVista('scanner');
            } else {
                throw new Error('Error al eliminar la pieza.');
            }
        })
        .catch(function(error) {
            mostrarToast(error.message, 'danger');
        });
}

/**
 * Carga el listado completo de piezas desde la API.
 */
function cargarListaPiezas() {
    // Limpiar buscador local al entrar
    const buscador = document.getElementById('buscadorLocalPiezas');
    if (buscador) buscador.value = '';

    fetch(API_BASE + '/piezas')
        .then(function(response) { return response.json(); })
        .then(function(piezas) {
            listaPiezasActual = piezas;
            renderTablaPiezas(piezas);
        })
        .catch(function(error) {
            mostrarToast('Error al cargar las piezas.', 'danger');
        });
}

/**
 * Filtra las piezas por nivel de estado o tipo de pieza.
 */
function filtrarPiezas() {
    var nivel = document.getElementById('filtroNivelPieza').value;
    var tipo = document.getElementById('filtroTipoPieza').value;

    // Limpiar buscador local al cambiar filtro
    const buscador = document.getElementById('buscadorLocalPiezas');
    if (buscador) buscador.value = '';

    // Se prioriza el filtro por nivel si está seleccionado.
    if (nivel) {
        fetch(API_BASE + '/piezas/nivel/' + nivel)
            .then(function(response) { return response.json(); })
            .then(function(piezas) { 
                listaPiezasActual = piezas;
                renderTablaPiezas(piezas); 
            })
            .catch(function(error) { mostrarToast('Error al filtrar.', 'danger'); });
    } else if (tipo) {
        fetch(API_BASE + '/piezas/tipo/' + encodeURIComponent(tipo))
            .then(function(response) { return response.json(); })
            .then(function(piezas) { 
                listaPiezasActual = piezas;
                renderTablaPiezas(piezas); 
            })
            .catch(function(error) { mostrarToast('Error al filtrar.', 'danger'); });
    } else {
        cargarListaPiezas();
    }
}

/**
 * Busca y filtra piezas en tiempo real localmente sobre la tabla actual.
 */
function filtrarPiezasLocalmente() {
    var term = document.getElementById('buscadorLocalPiezas').value.toLowerCase().trim();
    if (!term) {
        renderTablaPiezas(listaPiezasActual);
        return;
    }
    var resultado = listaPiezasActual.filter(function(p) {
        var qrStr = p.codigoQrPieza || ('PZ-' + p.id);
        return (p.tipoPieza && p.tipoPieza.toLowerCase().includes(term)) ||
               (p.referencia && p.referencia.toLowerCase().includes(term)) ||
               (p.marcaModeloCompatible && p.marcaModeloCompatible.toLowerCase().includes(term)) ||
               qrStr.toLowerCase().includes(term);
    });
    renderTablaPiezas(resultado);
}

/**
 * Renderiza la tabla de piezas en el listado.
 *
 * @param {Array} piezas - Lista de objetos pieza de la API.
 */
function renderTablaPiezas(piezas) {
    if (piezas.length === 0) {
        document.getElementById('tablaPiezas').innerHTML =
            '<div class="text-center text-secondary py-5">' +
            '<i class="bi bi-inbox fs-1 d-block mb-3"></i>' +
            'No se encontraron piezas.</div>';
        return;
    }

    // Se genera la tabla; igualamos las imágenes entre los datos
    // del servidor y la representación visual en el listado.
    var html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr>';
    html += '<th>Tipo</th><th>Nivel</th><th>Máquina</th><th>Ubicación</th>';
    html += '</tr></thead><tbody>';

    piezas.forEach(function(p) {
        html += '<tr onclick="cargarFichaPiezaPorId(' + p.id + ')">';
        html += '<td>' + (p.tipoPieza || '-') + '</td>';
        html += '<td>' + renderBadgeNivel(p.nivelEstado) + '</td>';
        html += '<td>' + (p.numeroSerieMaquinaOrigen || '-') + '</td>';
        html += '<td>' + (p.ubicacionFisica || '-') + '</td>';
        html += '</tr>';
    });

    html += '</tbody></table>';
    document.getElementById('tablaPiezas').innerHTML = html;
}

/**
 * Carga la ficha de una pieza por su ID.
 *
 * @param {number} id - Identificador de la pieza.
 */
function cargarFichaPiezaPorId(id) {
    fetch(API_BASE + '/piezas/' + id)
        .then(function(response) {
            if (response.ok) return response.json();
            throw new Error('Pieza no encontrada.');
        })
        .then(function(pieza) {
            // Se muestra la ficha; igualamos las imágenes entre
            // la respuesta de la API y la vista de detalle.
            mostrarFichaPieza(pieza);
        })
        .catch(function(error) {
            mostrarToast(error.message, 'danger');
        });
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 5: EVALUACIÓN DE MOVIMIENTO (REGLA DE BLOQUEO)
   
   Invoca al endpoint de evaluación del backend para comprobar si
   una pieza puede ser movida/instalada según su nivel de estado
   fungible (1, 2 o 3).
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Evalúa si una pieza puede ser movida o instalada.
 *
 * Llama al endpoint GET /api/piezas/{id}/evaluar-movimiento y
 * muestra el resultado con un estilo visual diferenciado:
 *   - AUTORIZADO (verde): movimiento sin restricciones.
 *   - ADVERTENCIA (amarillo): movimiento con precaución.
 *   - BLOQUEADO (rojo): movimiento denegado.
 *
 * @param {number} idPieza - Identificador de la pieza a evaluar.
 */
function evaluarMovimiento(idPieza) {
    fetch(API_BASE + '/piezas/' + idPieza + '/evaluar-movimiento')
        .then(function(response) { return response.json(); })
        .then(function(resultado) {
            var contenedor = document.getElementById('resultadoEvaluacion');
            contenedor.classList.remove('d-none');

            var claseAlerta = 'alerta-bloqueado';
            var icono = 'bi-x-octagon-fill';

            // Se compara el estado devuelto por el servidor contra las
            // tres posibles categorías; igualamos las imágenes entre
            // la evaluación del backend y la representación visual.
            if (resultado.estado === 'AUTORIZADO') {
                claseAlerta = 'alerta-autorizado';
                icono = 'bi-check-circle-fill';
            } else if (resultado.estado === 'ADVERTENCIA') {
                claseAlerta = 'alerta-advertencia';
                icono = 'bi-exclamation-triangle-fill';
            }

            contenedor.innerHTML =
                '<div class="' + claseAlerta + '">' +
                '<i class="bi ' + icono + ' fs-3 me-3"></i>' +
                '<strong>' + resultado.estado + '</strong><br>' +
                '<span class="mt-2 d-inline-block">' + resultado.mensaje + '</span>' +
                '</div>';

            // Se desplaza la vista para que el técnico vea el resultado.
            contenedor.scrollIntoView({ behavior: 'smooth' });
        })
        .catch(function(error) {
            mostrarToast('Error al evaluar el movimiento.', 'danger');
        });
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 5.4: GESTIÓN DE PIEZAS E HISTORIAL
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Carga los componentes y el historial de la máquina para mostrarlos en la ficha de detalle.
 */
function cargarComponentesEHistorialMaquina(maquina) {
    const contenedorLista = document.getElementById('listaComponentesMaquina');
    const contenedorHistorial = document.getElementById('historialMaquina');

    if (!contenedorLista || !contenedorHistorial) return;

    contenedorLista.innerHTML = '<div class="text-center text-secondary py-3"><div class="spinner-border spinner-border-sm me-2"></div>Cargando piezas...</div>';
    contenedorHistorial.innerHTML = '<div class="text-center text-secondary py-3"><div class="spinner-border spinner-border-sm me-2"></div>Cargando historial...</div>';

    const componentesEstandar = [
        'Fusor', 'Rodillo de presión', 'Rodillo de calor', 'Placa controladora',
        'Bandeja superior', 'Bandeja inferior', 'Tambor', 'Unidad de imagen',
        'Motor principal', 'Fuente de alimentación', 'Panel de control', 'Tóner'
    ];

    const fetchPiezas = fetch(API_BASE + '/piezas/maquina/' + encodeURIComponent(maquina.numeroSerie))
        .then(function(res) { return res.ok ? res.json() : []; });

    const fetchToner = fetch(API_BASE + '/toners/instalado/' + encodeURIComponent(maquina.numeroSerie))
        .then(function(res) { return res.ok ? res.json() : null; });

    Promise.all([fetchPiezas, fetchToner])
        .then(function(resultados) {
            const piezas = resultados[0];
            const tonerInstalado = resultados[1];
            
            let html = '';
            componentesEstandar.forEach(function(tipo) {
                if (tipo === 'Tóner') {
                    html += '<div class="p-3 bg-input rounded border border-secondary d-flex justify-content-between align-items-center">';
                    html += '<div>';
                    html += '<span class="fw-bold text-white fs-5 d-block">' + tipo + '</span>';
                    if (tonerInstalado) {
                        html += '<span class="badge bg-secondary me-2">Instalada (En máquina)</span>';
                        html += '<span class="small text-secondary">QR: <a href="#" class="text-accent" onclick="event.preventDefault(); cargarFichaTonerPorId(' + tonerInstalado.id + ')">' + tonerInstalado.codigoQr + '</a></span>';
                    } else {
                        html += '<span class="badge bg-success">En almacén</span>';
                    }
                    html += '</div>';
                    
                    if (tonerInstalado) {
                        if (maquina.preparadaComercial) {
                            html += '<button class="btn btn-outline-warning btn-sm px-3 disabled" disabled="disabled" onclick="event.preventDefault();">';
                            html += '<i class="bi bi-box-arrow-up me-1"></i>Extraer (Bloqueado Comercial)</button>';
                        } else {
                            html += '<button class="btn btn-outline-warning btn-sm px-3" onclick="abrirModalExtraerPieza(\'' + tipo + '\', \'' + maquina.numeroSerie + '\')">';
                            html += '<i class="bi bi-box-arrow-up me-1"></i>Extraer</button>';
                        }
                    } else {
                        html += '<span class="text-success fs-4"><i class="bi bi-check-circle-fill"></i></span>';
                    }
                    html += '</div>';
                } else {
                    // Buscar si este componente ya fue extraído (o sea, existe una pieza asociada activa)
                    const piezaExistente = piezas.find(function(p) {
                        return p.tipoPieza === tipo && p.estadoPieza !== 'Instalada';
                    });

                    html += '<div class="p-3 bg-input rounded border border-secondary d-flex justify-content-between align-items-center">';
                    html += '<div>';
                    html += '<span class="fw-bold text-white fs-5 d-block">' + tipo + '</span>';
                    if (piezaExistente) {
                        let badgeClase = 'bg-info';
                        if (piezaExistente.estadoPieza === 'Para reparación') badgeClase = 'bg-warning text-dark';
                        if (piezaExistente.estadoPieza === 'Para cliente') badgeClase = 'bg-primary';
                        if (piezaExistente.estadoPieza === 'En almacén') badgeClase = 'bg-success';

                        html += '<span class="badge ' + badgeClase + ' me-2">' + piezaExistente.estadoPieza + '</span>';
                        html += '<span class="small text-secondary">QR: <a href="#" class="text-accent" onclick="event.preventDefault(); cargarFichaPiezaPorId(' + piezaExistente.id + ')">' + piezaExistente.codigoQrPieza + '</a></span>';
                    } else {
                        html += '<span class="badge bg-secondary">Instalada (En máquina)</span>';
                    }
                    html += '</div>';
                    
                    if (!piezaExistente) {
                        if (maquina.preparadaComercial) {
                            html += '<button class="btn btn-outline-warning btn-sm px-3 disabled" disabled="disabled" onclick="event.preventDefault();">';
                            html += '<i class="bi bi-box-arrow-up me-1"></i>Extraer (Bloqueado Comercial)</button>';
                        } else {
                            html += '<button class="btn btn-outline-warning btn-sm px-3" onclick="abrirModalExtraerPieza(\'' + tipo + '\', \'' + maquina.numeroSerie + '\')">';
                            html += '<i class="bi bi-box-arrow-up me-1"></i>Extraer</button>';
                        }
                    } else {
                        html += '<div class="d-flex gap-2 align-items-center">';
                        html += '<button class="btn btn-outline-info btn-sm px-3" onclick="event.preventDefault(); fetch(API_BASE + \'/piezas/' + piezaExistente.id + '\').then(function(res) { return res.json(); }).then(function(p) { cargarPiezaEnFormulario(p); })">';
                        html += '<i class="bi bi-pencil-square me-1"></i>Editar</button>';
                        html += '<span class="text-success fs-4"><i class="bi bi-check-circle-fill"></i></span>';
                        html += '</div>';
                    }
                    html += '</div>';
                }
            });
            contenedorLista.innerHTML = html;
        })
        .catch(function(err) {
            console.error(err);
            contenedorLista.innerHTML = '<div class="alert alert-danger py-2">Error al cargar componentes</div>';
        });

    // Cargar historial de la máquina
    cargarHistorialList('MAQUINA', maquina.id, contenedorHistorial);
}


/**
 * Carga el historial de movimientos en un contenedor específico.
 */
function cargarHistorialList(tipo, id, contenedor) {
    fetch(API_BASE + '/historial/' + tipo + '/' + id)
        .then(function(res) { return res.ok ? res.json() : []; })
        .then(function(historial) {
            if (historial.length === 0) {
                contenedor.innerHTML = '<div class="text-center text-secondary py-4"><i class="bi bi-info-circle me-1"></i>Sin movimientos registrados.</div>';
                return;
            }

            let html = '<div class="list-group list-group-flush">';
            historial.forEach(function(hm) {
                const fechaFmt = new Date(hm.fecha).toLocaleString('es-ES');
                let badgeClase = 'bg-secondary';
                if (hm.accion === 'ALTA') badgeClase = 'bg-success';
                if (hm.accion === 'EXTRACCION') badgeClase = 'bg-warning text-dark';
                if (hm.accion === 'DEVOLUCION') badgeClase = 'bg-info text-dark';
                if (hm.accion === 'INSTALACION') badgeClase = 'bg-primary';
                if (hm.accion === 'PREPARADA_COMERCIAL') badgeClase = 'bg-success';

                html += '<div class="list-group-item bg-card border-secondary py-3 text-white">';
                html += '<div class="d-flex justify-content-between mb-1">';
                html += '<span class="badge ' + badgeClase + '">' + hm.accion + '</span>';
                html += '<small class="text-secondary">' + fechaFmt + '</small>';
                html += '</div>';
                html += '<p class="mb-1">' + hm.descripcion + '</p>';
                html += '<small class="text-accent"><i class="bi bi-person-fill me-1"></i>' + hm.usuarioResponsable + '</small>';
                html += '</div>';
            });
            html += '</div>';
            contenedor.innerHTML = html;
        })
        .catch(function() {
            contenedor.innerHTML = '<div class="alert alert-danger py-2">Error al cargar historial</div>';
        });
}

/**
 * Carga el historial de movimientos de la pieza en su ficha.
 */
function cargarHistorialPieza(pieza) {
    const contenedor = document.getElementById('historialPieza');
    if (contenedor) {
        cargarHistorialList('PIEZA', pieza.id, contenedor);
    }
}

/**
 * Abre el modal para configurar la extracción de la pieza.
 */
function abrirModalExtraerPieza(tipo, maquinaSerie) {
    document.getElementById('extTipoPieza').value = tipo;
    document.getElementById('extMaquinaSerie').value = maquinaSerie;
    document.getElementById('extMotivo').value = '';
    document.getElementById('extNivelEstado').value = '2';
    document.getElementById('extObservaciones').value = '';
    
    onCambioMotivoExtraccion();

    const myModal = new bootstrap.Modal(document.getElementById('modalExtraerPieza'));
    myModal.show();
}

/**
 * Controla la visualización condicional de campos en el modal de extracción.
 */
function onCambioMotivoExtraccion() {
    const motivo = document.getElementById('extMotivo').value;
    const divNivel = document.getElementById('divNivelEstadoExtraccion');
    const lblObs = document.getElementById('lblExtObservaciones');
    const errorObs = document.getElementById('errorObservacionesCliente');

    divNivel.classList.add('d-none');
    errorObs.classList.add('d-none');
    lblObs.textContent = 'Observaciones';

    if (motivo === 'Para almacenar') {
        divNivel.classList.remove('d-none');
    } else if (motivo === 'Para cliente') {
        lblObs.textContent = 'Ubicación o Destino (Obligatorio)';
    }
}

/**
 * Envía la petición de extracción al backend.
 */
function confirmarExtraccionPieza(event) {
    event.preventDefault();

    const tipo = document.getElementById('extTipoPieza').value;
    const serie = document.getElementById('extMaquinaSerie').value;
    const motivo = document.getElementById('extMotivo').value;
    const observaciones = document.getElementById('extObservaciones').value.trim();
    const errorObs = document.getElementById('errorObservacionesCliente');

    if (motivo === 'Para cliente' && !observaciones) {
        errorObs.classList.remove('d-none');
        return;
    }

    if (tipo === 'Tóner') {
        fetch(API_BASE + '/maquinas/serie/' + encodeURIComponent(serie))
            .then(res => {
                if (res.ok) return res.json();
                throw new Error('No se pudo obtener información de la máquina de origen.');
            })
            .then(maquina => {
                const tonerPayload = {
                    modelo: maquina.modelo + " (Tóner)",
                    nivelToner: maquina.nivelToner || "100%",
                    ubicacionFisica: "Almacén (Extracción)",
                    estado: "Disponible",
                    maquinaOrigenSerie: serie
                };
                return fetch(API_BASE + '/toners?tecnico=' + encodeURIComponent(obtenerTecnico()), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(tonerPayload)
                });
            })
            .then(res => {
                if (res.ok) return res.json();
                throw new Error('Error al registrar el tóner en la base de datos.');
            })
            .then(toner => {
                const extModalEl = document.getElementById('modalExtraerPieza');
                const modalInstance = bootstrap.Modal.getInstance(extModalEl);
                if (modalInstance) modalInstance.hide();

                mostrarToast('Tóner extraído y añadido a stock con éxito.', 'success');
                abrirModalImprimirQrToner(toner);

                fetch(API_BASE + '/maquinas/serie/' + encodeURIComponent(serie))
                    .then(r => r.ok ? r.json() : null)
                    .then(maquina => {
                        if (maquina) mostrarFichaMaquina(maquina);
                    });
            })
            .catch(err => {
                console.error(err);
                mostrarToast('Error al extraer tóner: ' + err.message, 'danger');
            });
        return;
    }

    let estadoPieza = 'En almacén'; // "Para almacenar" se mapea a "En almacén"
    if (motivo === 'Para reparación') estadoPieza = 'Para reparación';
    if (motivo === 'Para cliente') estadoPieza = 'Para cliente';

    let nivelEstado = 1;
    if (motivo === 'Para almacenar') {
        nivelEstado = parseInt(document.getElementById('extNivelEstado').value);
    }

    const payload = {
        tipoPieza: tipo,
        numeroSerieMaquinaOrigen: serie,
        estadoPieza: estadoPieza,
        nivelEstado: nivelEstado,
        destinoCliente: motivo === 'Para cliente' ? observaciones : null,
        observaciones: observaciones,
        fechaAlta: obtenerFechaHoy()
    };

    fetch(API_BASE + '/piezas?tecnico=' + encodeURIComponent(obtenerTecnico()), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(function(res) {
        if (res.ok) return res.json();
        throw new Error('Error al extraer componente.');
    })
    .then(function(pieza) {
        // Cerrar modal de extracción
        const extModalEl = document.getElementById('modalExtraerPieza');
        const modalInstance = bootstrap.Modal.getInstance(extModalEl);
        if (modalInstance) modalInstance.hide();

        mostrarToast('Componente extraído con éxito.', 'success');

        // Mostrar modal para imprimir
        abrirModalImprimirQrDirecto(pieza);

        // Refrescar ficha de la máquina madre
        fetch(API_BASE + '/maquinas/serie/' + encodeURIComponent(serie))
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(maquina) {
                if (maquina) mostrarFichaMaquina(maquina);
            });
    })
    .catch(function(err) {
        mostrarToast(err.message, 'danger');
    });
}

/**
 * Devuelve la pieza al almacén ("Dar de alta pieza").
 */
function devolverPiezaAlAlmacen(id) {
    if (!confirm('¿Desea devolver esta pieza al almacén y darla de alta nuevamente?')) {
        return;
    }

    fetch(API_BASE + '/piezas/' + id)
        .then(function(res) { return res.json(); })
        .then(function(pieza) {
            pieza.estadoPieza = 'En almacén';
            pieza.destinoCliente = null;

            return fetch(API_BASE + '/piezas/' + id + '?tecnico=' + encodeURIComponent(obtenerTecnico()), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(pieza)
            });
        })
        .then(function(res) {
            if (res.ok) return res.json();
            throw new Error('Error al devolver la pieza.');
        })
        .then(function(pieza) {
            mostrarToast('Pieza devuelta al almacén con éxito.', 'success');
            mostrarFichaPieza(pieza);
        })
        .catch(function(err) {
            mostrarToast(err.message, 'danger');
        });
}

/**
 * Carga el código QR desde el backend, lo convierte a Base64 localmente
 * para evitar bloqueos del motor de impresión por certificados auto-firmados (SSL).
 */
function cargarQrEnModal(texto, size, callback) {
    const url = '/v1/qrcode?text=' + encodeURIComponent(texto) + '&width=' + size + '&height=' + size;
    
    // Poner una imagen vacía o spinner mientras carga
    const qrImg = document.getElementById('printQrCodeImg');
    if (qrImg) {
        qrImg.src = '';
        qrImg.style.width = size + 'px';
        qrImg.style.height = size + 'px';
    }

    const area = document.getElementById('areaEtiquetaImprimible');
    if (area) {
        if (size === 250) {
            area.classList.remove('etiqueta-pequena');
            area.classList.add('etiqueta-grande');
            area.style.width = '320px';
        } else {
            area.classList.remove('etiqueta-grande');
            area.classList.add('etiqueta-pequena');
            area.style.width = '180px';
        }
    }

    fetch(url)
        .then(response => {
            if (!response.ok) throw new Error('Error de red al generar el QR');
            return response.blob();
        })
        .then(blob => {
            const reader = new FileReader();
            reader.onloadend = function() {
                if (qrImg) {
                    qrImg.src = reader.result;
                }
                if (callback) callback();
            };
            reader.readAsDataURL(blob);
        })
        .catch(err => {
            console.error('Error cargando QR desde el backend:', err);
            // Fallback: Si falla por cualquier motivo, poner enlace de fallback directo
            if (qrImg) {
                qrImg.src = url;
            }
            if (callback) callback();
        });
}

/**
 * Prepara y abre el modal de impresión de QR directamente con los datos de una pieza.
 */
function abrirModalImprimirQrDirecto(pieza) {
    document.getElementById('printTituloPieza').textContent = (pieza.tipoPieza || 'PIEZA').toUpperCase();
    document.getElementById('printQrText').textContent = pieza.codigoQrPieza;
    document.getElementById('printOrigenText').textContent = 'Origen: S/N ' + (pieza.numeroSerieMaquinaOrigen || 'Desconocido');
    
    let estadoFmt = pieza.estadoPieza || 'En almacén';
    if (pieza.nivelEstado && estadoFmt === 'En almacén') {
        estadoFmt += ' (Nivel ' + pieza.nivelEstado + ')';
    }
    document.getElementById('printEstadoText').textContent = estadoFmt;

    // Cargar QR como Base64 y después abrir el modal
    cargarQrEnModal(pieza.codigoQrPieza, 100, () => {
        const myModal = new bootstrap.Modal(document.getElementById('modalImprimirQr'));
        myModal.show();
    });
}

function ejecutarImpresionQr() {
    // 1. Obtener el área imprimible original
    const areaImprimible = document.getElementById('areaEtiquetaImprimible');

    // 2. Clonar de manera profunda el área de la etiqueta para mantener la estructura y estilos
    const printContenedor = areaImprimible.cloneNode(true);
    printContenedor.id = 'contenedorImpresionTemporal';

    // 3. Añadir al body temporalmente para la impresión
    document.body.appendChild(printContenedor);

    // 4. Invocar diálogo de impresión nativo tras un leve delay para asegurar renderizado de la imagen
    setTimeout(() => {
        window.print();
        // 5. Limpiar contenedor temporal inmediatamente después
        if (document.getElementById('contenedorImpresionTemporal')) {
            document.body.removeChild(printContenedor);
        }
    }, 300);
}

/**
 * Función de soporte y guía para la impresora de etiquetas Vretti 420B (arquitectura TSPL).
 * Se puede llamar mañana cuando la impresora esté conectada vía USB.
 * Requiere soporte de WebUSB en el navegador (Chrome, Edge u Opera).
 */
async function enviarComandoTSPLVretti(codigoQr, titulo, subtitulo) {
    console.log("Enviando comandos TSPL a la impresora Vretti...");
    
    // Comando TSPL formateado según especificación de Vretti 420B
    const comandosTSPL = 
        "SIZE 4,1\r\n" +                    // Tamaño etiqueta: 4x1 pulgadas (ajustar a tus etiquetas reales)
        "GAP 0.12,0\r\n" +                  // Distancia de separación
        "DIRECTION 1\r\n" +                 // Dirección de impresión
        "CLS\r\n" +                         // Limpiar buffer
        `TEXT 10,15,"2",0,1,1,"${titulo.substring(0, 20)}"\r\n` +  // Texto del título
        `BARCODE 10,40,"128",60,1,0,2,2,"${codigoQr}"\r\n` +       // Código de barras o QR
        `TEXT 10,110,"1",0,1,1,"${subtitulo.substring(0, 35)}"\r\n` + // Subtítulo (S/N y Estado)
        "PRINT 1\r\n";                      // Ejecutar 1 copia

    try {
        // Solicitar puerto USB de la impresora al usuario
        const device = await navigator.usb.requestDevice({ filters: [] });
        await device.open();
        await device.selectConfiguration(1);
        await device.claimInterface(0);

        // Convertir string de comandos a bytes
        const encoder = new TextEncoder();
        const data = encoder.encode(comandosTSPL);

        // Enviar al endpoint de escritura (normalmente endpoint 1 o 2 en Vretti/Xprinter)
        await device.transferOut(1, data); 
        console.log("Comandos TSPL enviados con éxito a la impresora USB.");
        mostrarToast("Comando TSPL enviado a la impresora Vretti.", "success");
        await device.close();
    } catch (err) {
        console.warn("Impresión directa USB cancelada o sin soporte WebUSB en este navegador: ", err);
    }
}

/**
 * Prepara y abre el modal de impresión de QR directamente con los datos de una máquina.
 */
function abrirModalImprimirQrMaquina(maquina) {
    document.getElementById('printTituloPieza').textContent = (maquina.marca + ' ' + maquina.modelo).toUpperCase();
    document.getElementById('printQrText').textContent = maquina.codigoQr;
    document.getElementById('printOrigenText').textContent = 'S/N: ' + (maquina.numeroSerie || 'Desconocido');
    document.getElementById('printEstadoText').textContent = 'Inspección: ' + (maquina.estadoVisual || 'Sin evaluar');

    // Cargar QR como Base64 y después abrir el modal
    cargarQrEnModal(maquina.codigoQr, 250, () => {
        const myModal = new bootstrap.Modal(document.getElementById('modalImprimirQr'));
        myModal.show();
    });
}

/**
 * Busca por ID (MQ-x o PZ-x) y abre el modal de reimpresión de etiquetas.
 */
function buscarYReimprimirEtiqueta() {
    const input = document.getElementById('inputReimpresionId');
    const id = input.value.trim();

    if (!id) {
        mostrarToast('Introduzca un ID (ej: MQ-1, PZ-2, TNR-3)', 'warning');
        return;
    }

    mostrarToast('Buscando etiqueta para ' + id + '...', 'info');

    // Determinar si es máquina, pieza o tóner por el prefijo
    const esMaquina = id.toUpperCase().startsWith('MQ-');
    const esPieza = id.toUpperCase().startsWith('PZ-');
    const esToner = id.toUpperCase().startsWith('TNR-') || id.toUpperCase().startsWith('TN-');

    if (esMaquina) {
        buscarMaquinaPorIdReimpresion(id);
    } else if (esPieza) {
        buscarPiezaPorIdReimpresion(id);
    } else if (esToner) {
        buscarTonerPorIdReimpresion(id);
    } else {
        // Si no tiene prefijo, probamos buscar en todos
        fetch(API_BASE + '/maquinas/qr/' + encodeURIComponent(id))
            .then(function(res) {
                if (res.ok) {
                    res.json().then(function(maq) {
                        abrirModalImprimirQrMaquina(maq);
                    });
                } else {
                    fetch(API_BASE + '/piezas/qr/' + encodeURIComponent(id))
                        .then(function(res2) {
                            if (res2.ok) {
                                res2.json().then(function(pza) {
                                    abrirModalImprimirQrDirecto(pza);
                                });
                            } else {
                                fetch(API_BASE + '/toners/qr/' + encodeURIComponent(id))
                                    .then(function(res3) {
                                        if (res3.ok) {
                                            res3.json().then(function(tnr) {
                                                abrirModalImprimirQrToner(tnr);
                                            });
                                        } else {
                                            mostrarToast('No se encontró ninguna máquina, pieza o tóner con ese ID/QR.', 'danger');
                                        }
                                    })
                                    .catch(function() {
                                        mostrarToast('Error al buscar el tóner.', 'danger');
                                    });
                            }
                        })
                        .catch(function() {
                            mostrarToast('Error al buscar la pieza.', 'danger');
                        });
                }
            })
            .catch(function() {
                mostrarToast('Error al buscar la máquina.', 'danger');
            });
    }
}

function buscarTonerPorIdReimpresion(id) {
    fetch(API_BASE + '/toners/qr/' + encodeURIComponent(id))
        .then(function(res) {
            if (res.ok) {
                return res.json();
            }
            throw new Error('Tóner no encontrado.');
        })
        .then(function(toner) {
            abrirModalImprimirQrToner(toner);
        })
        .catch(function() {
            mostrarToast('No se encontró ningún tóner con el ID ' + id, 'danger');
        });
}

function buscarMaquinaPorIdReimpresion(id) {
    fetch(API_BASE + '/maquinas/qr/' + encodeURIComponent(id))
        .then(function(res) {
            if (res.ok) {
                return res.json();
            }
            throw new Error('Máquina no encontrada.');
        })
        .then(function(maquina) {
            abrirModalImprimirQrMaquina(maquina);
        })
        .catch(function() {
            mostrarToast('No se encontró ninguna máquina con el ID ' + id, 'danger');
        });
}

function buscarPiezaPorIdReimpresion(id) {
    fetch(API_BASE + '/piezas/qr/' + encodeURIComponent(id))
        .then(function(res) {
            if (res.ok) {
                return res.json();
            }
            throw new Error('Pieza no encontrada.');
        })
        .then(function(pieza) {
            abrirModalImprimirQrDirecto(pieza);
        })
        .catch(function() {
            mostrarToast('No se encontró ninguna pieza con el ID ' + id, 'danger');
        });
}

/**
 * Realiza una búsqueda global tanto de máquinas como de piezas en el backend.
 */
var buscarGlobalTimeout = null;

/**
 * Realiza una búsqueda global tanto de máquinas como de piezas en el backend.
 */
function buscarGlobal(isAuto) {
    var input = document.getElementById('inputBuscarGlobal');
    if (!input) return;
    var termino = input.value.trim();

    var divUnificados = document.getElementById('resultadosUnificados');
    var divMaquinas = document.getElementById('resultadosMaquinas');
    var divPiezas = document.getElementById('resultadosPiezas');
    var divToners = document.getElementById('resultadosToners');
    var divSinResultados = document.getElementById('sinResultadosBusqueda');

    if (!termino) {
        if (!isAuto) {
            mostrarToast('Introduzca un término de búsqueda.', 'warning');
        }
        if (divUnificados) {
            divUnificados.classList.add('d-none');
            divUnificados.innerHTML = '';
        }
        divMaquinas.classList.add('d-none');
        divPiezas.classList.add('d-none');
        divToners.classList.add('d-none');
        divSinResultados.classList.add('d-none');
        return;
    }

    divMaquinas.classList.add('d-none');
    divToners.classList.add('d-none');

    var fetchInventario = fetch(API_BASE + '/inventario/buscar?termino=' + encodeURIComponent(termino))
        .then(function(res) { return res.ok ? res.json() : []; })
        .catch(function() { return []; });

    var fetchPiezas = fetch(API_BASE + '/piezas/buscar?q=' + encodeURIComponent(termino))
        .then(function(res) { return res.ok ? res.json() : []; })
        .catch(function() { return []; });

    var fetchToners = fetch(API_BASE + '/toners/buscar?q=' + encodeURIComponent(termino))
        .then(function(res) { return res.ok ? res.json() : []; })
        .catch(function() { return []; });

    Promise.all([fetchInventario, fetchPiezas, fetchToners]).then(function(resultados) {
        var maquinasVinculadas = resultados[0];
        var piezas = resultados[1];
        var toners = resultados[2];

        if (maquinasVinculadas.length > 0) {
            divUnificados.classList.remove('d-none');
            
            var html = '';
            maquinasVinculadas.forEach(function(m) {
                var termLower = termino.toLowerCase();
                var tieneTonerCoincidente = false;
                var listaTonersCompatibles = m.toneres || [];
                
                listaTonersCompatibles.forEach(function(t) {
                    if ((t.modelo && t.modelo.toLowerCase().indexOf(termLower) !== -1) || 
                        (t.codigoQr && t.codigoQr.toLowerCase().indexOf(termLower) !== -1)) {
                        tieneTonerCoincidente = true;
                    }
                });

                var cardStyle = tieneTonerCoincidente ? 'border: 2px solid #ffc107; box-shadow: 0 0 15px rgba(255, 193, 7, 0.4);' : '';
                var badgeHighlight = tieneTonerCoincidente ? '<span class="badge bg-warning text-dark me-2"><i class="bi bi-star-fill me-1"></i>Contiene Tóner Buscado</span>' : '';

                html += '<div class="col-md-6 col-lg-4">';
                html += '  <div class="card h-100 bg-card border-secondary text-white hover-scale" style="' + cardStyle + ' transition: transform 0.2s ease, box-shadow 0.2s ease;">';
                html += '    <div class="card-header bg-header d-flex justify-content-between align-items-center border-secondary">';
                html += '      <div>';
                html += '        <h5 class="card-title mb-0 fw-bold text-accent"><i class="bi bi-printer me-2"></i>' + (m.marca || '') + ' ' + (m.modelo || 'Modelo Desconocido') + '</h5>';
                html += '        <span class="text-secondary small">QR: ' + (m.codigoQr || '-') + '</span>';
                html += '      </div>';
                html += '      <span class="badge bg-secondary">' + (m.decisionTecnica || 'S/D') + '</span>';
                html += '    </div>';
                html += '    <div class="card-body">';
                html += '      <p class="card-text mb-1"><strong>Nº Serie:</strong> ' + (m.numeroSerie || '-') + '</p>';
                html += '      <p class="card-text mb-1"><strong>Ubicación:</strong> <i class="bi bi-geo-alt text-accent me-1"></i>' + (m.ubicacionFisica || '-') + '</p>';
                html += '      <p class="card-text mb-1"><strong>Estado:</strong> ' + (m.estadoFuncionamiento || '-') + '</p>';
                html += '      <p class="card-text mb-3"><strong>Estado Visual:</strong> ' + (m.estadoVisual || '-') + '</p>';
                
                html += '      <div class="mt-3 p-3 rounded bg-input border border-secondary">';
                html += '        <h6 class="text-white fw-bold mb-2 border-bottom border-secondary pb-1"><i class="bi bi-droplet-fill me-2 text-accent"></i>Tóneres Compatibles:</h6>';
                
                if (listaTonersCompatibles.length > 0) {
                    html += '    <ul class="list-unstyled mb-0">';
                    listaTonersCompatibles.forEach(function(t) {
                        var esTonerCoincidente = false;
                        if ((t.modelo && t.modelo.toLowerCase().indexOf(termLower) !== -1) || 
                            (t.codigoQr && t.codigoQr.toLowerCase().indexOf(termLower) !== -1)) {
                            esTonerCoincidente = true;
                        }
                        
                        var itemStyle = esTonerCoincidente ? 'background-color: rgba(255, 193, 7, 0.15); border: 1px solid #ffc107;' : '';
                        var itemBadge = esTonerCoincidente ? '<span class="badge bg-warning text-dark ms-2"><i class="bi bi-check-circle-fill me-1"></i>Coincidencia</span>' : '';
                        
                        html += '      <li class="p-2 mb-1 rounded d-flex justify-content-between align-items-center" style="' + itemStyle + '">';
                        html += '        <span><strong>' + (t.modelo || '') + '</strong> <span class="text-secondary">(' + (t.codigoQr || '-') + ')</span>' + itemBadge + '</span>';
                        html += '        <span class="badge bg-dark">' + (t.nivelToner || '0%') + '</span>';
                        html += '      </li>';
                    });
                    html += '    </ul>';
                } else {
                    html += '    <p class="text-secondary mb-0 small">No hay tóneres registrados para esta máquina.</p>';
                }
                html += '      </div>';
                
                html += '    </div>';
                html += '    <div class="card-footer bg-header border-secondary d-flex justify-content-between align-items-center">';
                html += '      ' + badgeHighlight;
                html += '      <button class="btn btn-sm btn-outline-accent" onclick="cargarFichaMaquinaPorId(' + m.id + ')"><i class="bi bi-eye me-1"></i>Ver Ficha</button>';
                html += '    </div>';
                html += '  </div>';
                html += '</div>';
            });
            divUnificados.innerHTML = html;
        } else {
            divUnificados.classList.add('d-none');
            divUnificados.innerHTML = '';
        }

        if (piezas.length > 0) {
            divPiezas.classList.remove('d-none');
            var htmlPza = '<table class="table table-dark-techcorp table-hover">';
            htmlPza += '<thead><tr><th>Tipo</th><th>Nivel</th><th>Máquina origen</th><th>Ubicación</th><th>Estado</th></tr></thead><tbody>';
            piezas.forEach(function(p) {
                htmlPza += '<tr onclick="cargarFichaPiezaPorId(' + p.id + ')">';
                htmlPza += '<td>' + (p.tipoPieza || '-') + '</td>';
                htmlPza += '<td>' + renderBadgeNivel(p.nivelEstado) + '</td>';
                htmlPza += '<td>' + (p.numeroSerieMaquinaOrigen || '-') + '</td>';
                htmlPza += '<td>' + (p.ubicacionFisica || '-') + '</td>';
                htmlPza += '<td>' + (p.estadoPieza || 'En almacén') + '</td>';
                htmlPza += '</tr>';
            });
            htmlPza += '</tbody></table>';
            document.getElementById('tablaResultadosPiezas').innerHTML = htmlPza;
        } else {
            divPiezas.classList.add('d-none');
            document.getElementById('tablaResultadosPiezas').innerHTML = '';
        }

        if (toners.length > 0) {
            divToners.classList.remove('d-none');
            var htmlToner = '<table class="table table-dark-techcorp table-hover">';
            htmlToner += '<thead><tr><th>Código QR</th><th>Modelo</th><th>Nivel</th><th>Ubicación</th><th>Estado</th></tr></thead><tbody>';
            toners.forEach(function(t) {
                htmlToner += '<tr onclick="cargarFichaTonerPorId(' + t.id + ')" style="cursor: pointer;">';
                htmlToner += '<td><span class="badge bg-secondary">' + t.codigoQr + '</span></td>';
                htmlToner += '<td>' + t.modelo + '</td>';
                let badgeColor = 'bg-success';
                if (t.nivelToner === '25%') badgeColor = 'bg-danger';
                else if (t.nivelToner === '50%') badgeColor = 'bg-warning text-dark';
                else if (t.nivelToner === '75%') badgeColor = 'bg-info text-dark';
                htmlToner += '<td><span class="badge ' + badgeColor + '">' + (t.nivelToner || '0%') + '</span></td>';
                htmlToner += '<td>' + (t.ubicacionFisica || '-') + '</td>';
                htmlToner += '<td>' + (t.estado || 'Disponible') + '</td>';
                htmlToner += '</tr>';
            });
            htmlToner += '</tbody></table>';
            document.getElementById('tablaResultadosToners').innerHTML = htmlToner;
        } else {
            divToners.classList.add('d-none');
            document.getElementById('tablaResultadosToners').innerHTML = '';
        }

        if (maquinasVinculadas.length === 0 && piezas.length === 0 && toners.length === 0) {
            divSinResultados.classList.remove('d-none');
        } else {
            divSinResultados.classList.add('d-none');
        }
    }).catch(function(err) {
        console.error('Error al realizar búsqueda global:', err);
        mostrarToast('Error al procesar la búsqueda.', 'danger');
    });
}

/**
 * Carga la lista de movimientos para el historial general.
 */
function cargarListaHistorial() {
    const buscador = document.getElementById('buscadorLocalHistorial');
    if (buscador) buscador.value = '';

    const tablaContainer = document.getElementById('tablaHistorialGeneral');
    tablaContainer.innerHTML = '<div class="text-center text-secondary py-5"><div class="spinner-border text-accent mb-3" role="status"></div><p>Cargando historial...</p></div>';

    fetch(API_BASE + '/historial')
        .then(function(res) { return res.json(); })
        .then(function(movimientos) {
            listaHistorialActual = movimientos;
            renderTablaHistorial(movimientos);
        })
        .catch(function(err) {
            console.error('Error al cargar historial general:', err);
            mostrarToast('Error al cargar el historial.', 'danger');
            tablaContainer.innerHTML = '<div class="alert alert-danger">Error al cargar los datos del historial.</div>';
        });
}

/**
 * Renderiza la tabla de historial general.
 */
function renderTablaHistorial(movimientos) {
    const container = document.getElementById('tablaHistorialGeneral');
    if (!container) return;

    if (movimientos.length === 0) {
        container.innerHTML =
            '<div class="text-center text-secondary py-5">' +
            '<i class="bi bi-clock-history fs-1 d-block mb-3"></i>' +
            'No se encontraron movimientos registrados en el historial.</div>';
        return;
    }

    var html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr>';
    html += '<th>Fecha y Hora</th><th>Entidad</th><th>ID / QR</th><th>Acción</th><th>Detalle</th><th>Responsable</th>';
    html += '</tr></thead><tbody>';

    movimientos.forEach(function(m) {
        let badgeClase = 'bg-secondary';
        const acc = m.accion ? m.accion.toUpperCase() : '';
        if (acc.includes('ALTA') || acc.includes('ENTRADA')) {
            badgeClase = 'bg-success';
        } else if (acc.includes('BAJA') || acc.includes('ELIMINAR')) {
            badgeClase = 'bg-danger';
        } else if (acc.includes('EXTRACCION') || acc.includes('SALIDA') || acc.includes('REVOCADA')) {
            badgeClase = 'bg-info';
        } else if (acc.includes('EDICION') || acc.includes('MODIF')) {
            badgeClase = 'bg-warning text-dark';
        } else if (acc.includes('PREPARADA') || acc.includes('COMERCIAL')) {
            badgeClase = 'bg-primary';
        }

        // Enlace clicable para ver la máquina o pieza desde el historial
        let idFmt = m.entidadId || '-';
        let linkOnClick = '';
        if (m.tipoEntidad === 'MAQUINA') {
            linkOnClick = 'onclick="cargarFichaMaquinaPorId(' + m.entidadId + ')" style="cursor: pointer;"';
        } else if (m.tipoEntidad === 'PIEZA') {
            linkOnClick = 'onclick="cargarFichaPiezaPorId(' + m.entidadId + ')" style="cursor: pointer;"';
        }

        html += '<tr ' + linkOnClick + '>';
        html += '<td style="white-space: nowrap;">' + formatearFechaLarga(m.fecha) + '</td>';
        html += '<td><span class="badge bg-secondary">' + (m.tipoEntidad || '-') + '</span></td>';
        html += '<td><strong class="text-accent">' + idFmt + '</strong></td>';
        html += '<td><span class="badge ' + badgeClase + '">' + (m.accion || '-') + '</span></td>';
        html += '<td>' + (m.descripcion || '-') + '</td>';
        html += '<td><span class="text-white">' + (m.usuarioResponsable || '-') + '</span></td>';
        html += '</tr>';
    });

    html += '</tbody></table>';
    container.innerHTML = html;
}

/**
 * Filtra el historial general localmente.
 */
function filtrarHistorialLocalmente() {
    var term = document.getElementById('buscadorLocalHistorial').value.toLowerCase().trim();
    if (!term) {
        renderTablaHistorial(listaHistorialActual);
        return;
    }
    var filtrados = listaHistorialActual.filter(function(m) {
        return (m.tipoEntidad && m.tipoEntidad.toLowerCase().includes(term)) ||
               (m.accion && m.accion.toLowerCase().includes(term)) ||
               (m.descripcion && m.descripcion.toLowerCase().includes(term)) ||
               (m.usuarioResponsable && m.usuarioResponsable.toLowerCase().includes(term)) ||
               (m.entidadId && String(m.entidadId).includes(term)) ||
               (m.fecha && formatearFechaLarga(m.fecha).toLowerCase().includes(term));
    });
    renderTablaHistorial(filtrados);
}

/**
 * Formatea una fecha ISO o LocalDateTime de forma amigable (dd/mm/yyyy hh:mm)
 */
function formatearFechaLarga(fechaStr) {
    if (!fechaStr) return '-';
    try {
        const d = new Date(fechaStr);
        if (isNaN(d.getTime())) return fechaStr;
        const dia = String(d.getDate()).padStart(2, '0');
        const mes = String(d.getMonth() + 1).padStart(2, '0');
        const anio = d.getFullYear();
        const hora = String(d.getHours()).padStart(2, '0');
        const min = String(d.getMinutes()).padStart(2, '0');
        return dia + '/' + mes + '/' + anio + ' ' + hora + ':' + min;
    } catch(e) {
        return fechaStr;
    }
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 6: UTILIDADES DE RENDERIZADO
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Genera el HTML de un campo de ficha de detalle (etiqueta + valor).
 *
 * @param {string} etiqueta - Nombre del campo.
 * @param {*} valor - Valor del campo (puede ser null/undefined).
 * @return {string} HTML del campo renderizado.
 */
function renderCampoFicha(etiqueta, valor) {
    var valorStr = (valor !== null && valor !== undefined && valor !== '')
        ? String(valor)
        : '<span class="ficha-valor vacio">Sin datos</span>';

    return '<div class="ficha-campo">' +
           '<div class="ficha-label">' + etiqueta + '</div>' +
           '<div class="ficha-valor">' + valorStr + '</div>' +
           '</div>';
}

/**
 * Genera un badge HTML para el nivel de estado fungible de una pieza.
 *
 * @param {number} nivel - Nivel de estado (1, 2 o 3).
 * @return {string} HTML del badge con color semafórico.
 */
function renderBadgeNivel(nivel) {
    // Se compara el nivel contra los tres valores posibles;
    // igualamos las imágenes entre el nivel numérico y su
    // representación visual con colores semafóricos.
    switch (nivel) {
        case 1:
            return '<span class="badge badge-nivel-1">Nivel 1 — Bueno / Óptimo</span>';
        case 2:
            return '<span class="badge badge-nivel-2">Nivel 2 — Aceptable / Funcional</span>';
        case 3:
            return '<span class="badge badge-nivel-3">Nivel 3 — Dudoso / Requiere evaluación</span>';
        default:
            return '<span class="badge bg-secondary">Sin nivel</span>';
    }
}

/**
 * Genera un badge HTML para la decisión técnica de una máquina.
 *
 * @param {string} decision - Decisión técnica asignada.
 * @return {string} HTML del badge con color diferenciado.
 */
function renderBadgeDecision(decision) {
    var colores = {
        'Cliente': 'bg-primary',
        'Almacén': 'bg-info',
        'Despiece': 'bg-danger',
        'Reacondicionado': 'bg-success'
    };
    var clase = colores[decision] || 'bg-secondary';
    return '<span class="badge ' + clase + '">' + (decision || 'Sin decisión') + '</span>';
}

/**
 * Selecciona un nivel de estado en el formulario de piezas.
 *
 * Se invoca al hacer clic en uno de los contenedores de nivel
 * para mejorar la zona táctil (no solo el radio button).
 *
 * @param {number} nivel - Nivel de estado a seleccionar (1, 2 o 3).
 */
function seleccionarNivel(nivel) {
    document.getElementById('nivel' + nivel).checked = true;
}

/**
 * Devuelve la fecha actual en formato ISO (YYYY-MM-DD) para
 * precargar campos de fecha en los formularios.
 *
 * @return {string} Fecha de hoy en formato YYYY-MM-DD.
 */
function obtenerFechaHoy() {
    var hoy = new Date();
    var anio = hoy.getFullYear();
    var mes = String(hoy.getMonth() + 1).padStart(2, '0');
    var dia = String(hoy.getDate()).padStart(2, '0');
    return anio + '-' + mes + '-' + dia;
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 7: SISTEMA DE NOTIFICACIONES (TOAST)
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Muestra un toast de notificación al técnico.
 *
 * @param {string} mensaje - Texto del mensaje a mostrar.
 * @param {string} tipo - Tipo Bootstrap: "success", "danger",
 *                        "warning", "info".
 */
function mostrarToast(mensaje, tipo) {
    var toastEl = document.getElementById('toastNotificacion');
    var toastMsg = document.getElementById('toastMensaje');

    // Se actualiza el contenido y el color del toast;
    // igualamos las imágenes entre el tipo de notificación
    // y la clase visual aplicada al componente.
    toastEl.className = 'toast align-items-center border-0 text-bg-' + tipo;
    toastMsg.textContent = mensaje;

    var toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();
}

/**
 * Muestra un mensaje en el área de resultado del escáner.
 *
 * @param {string} texto - Mensaje a mostrar.
 */
function mostrarResultadoEscaneo(texto) {
    var contenedor = document.getElementById('resultadoEscaneo');
    document.getElementById('textoResultadoEscaneo').textContent = texto;
    contenedor.classList.remove('d-none');
}

/**
 * Oculta el área de resultado del escáner.
 */
function ocultarResultadoEscaneo() {
    document.getElementById('resultadoEscaneo').classList.add('d-none');
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 8: IDENTIFICACIÓN DEL TÉCNICO
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Cierra la sesión activa en el sistema y redirige a la página de login.
 */
function cerrarSesion(event) {
    if (event) event.preventDefault();
    localStorage.removeItem('techcorp_session');
    window.location.href = '/login.html';
}

/**
 * Obtiene el nombre del técnico actual guardado en localStorage.
 */
function obtenerTecnico() {
    return localStorage.getItem('techcorp_tecnico') || 'Técnico';
}

/**
 * Guarda el nombre del técnico en localStorage y actualiza el navbar.
 */
function guardarTecnico(nombre) {
    if (nombre) {
        localStorage.setItem('techcorp_tecnico', nombre);
        const nameEl = document.getElementById('nombreTecnicoNav');
        if (nameEl) {
            nameEl.textContent = nombre;
        }
    }
}

/**
 * Muestra el modal de identificación del técnico.
 */
function mostrarModalTecnico() {
    const modalEl = document.getElementById('modalIdentificacionTecnico');
    if (modalEl) {
        document.getElementById('inputNombreTecnico').value = localStorage.getItem('techcorp_tecnico') || '';
        document.getElementById('errorNombreTecnico').classList.add('d-none');
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    }
}

/**
 * Confirma la identificación del técnico desde el modal.
 */
function confirmarIdentificacionTecnico() {
    const input = document.getElementById('inputNombreTecnico');
    const nombre = input.value.trim();
    if (!nombre) {
        document.getElementById('errorNombreTecnico').classList.remove('d-none');
        return;
    }
    guardarTecnico(nombre);
    
    // Cerrar el modal
    const modalEl = document.getElementById('modalIdentificacionTecnico');
    const modalInstance = bootstrap.Modal.getInstance(modalEl);
    if (modalInstance) {
        modalInstance.hide();
    }
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 8.5: GESTIÓN DE BORRADORES (AUTO-GUARDADO)
   ═══════════════════════════════════════════════════════════════════ */

function guardarBorradorMaquina() {
    const id = document.getElementById('maqId').value;
    if (id) return; 

    const campos = [
        'maqCodigoQr', 'maqMarca', 'maqModelo', 'maqNumeroSerie', 'maqCopiasBn', 'maqCopiasColor',
        'maqClienteProcedencia', 'maqFechaEntrada', 'maqEstadoFuncionamiento',
        'maqAveriasCodigos', 'maqDecisionTecnica', 'maqEstadoVisual', 'maqNivelToner',
        'maqUbicacionFisica', 'maqObservaciones'
    ];
    const borrador = {};
    campos.forEach(function(idCampo) {
        const el = document.getElementById(idCampo);
        if (el) {
            borrador[idCampo] = el.value;
        }
    });
    localStorage.setItem('techcorp_draft_maquina', JSON.stringify(borrador));
}

function restaurarBorradorMaquina() {
    const id = document.getElementById('maqId').value;
    if (id) return;

    const borradorStr = localStorage.getItem('techcorp_draft_maquina');
    if (borradorStr) {
        try {
            const borrador = JSON.parse(borradorStr);
            Object.keys(borrador).forEach(function(idCampo) {
                const el = document.getElementById(idCampo);
                if (el && borrador[idCampo]) {
                    el.value = borrador[idCampo];
                }
            });
        } catch (e) {
            console.error('Error al restaurar borrador de máquina', e);
        }
    }
}

function limpiarBorradorMaquina() {
    localStorage.removeItem('techcorp_draft_maquina');
}

function guardarBorradorPieza() {
    const id = document.getElementById('pzaId').value;
    if (id) return;

    const campos = [
        'pzaCodigoQrPieza', 'pzaTipoPieza', 'pzaReferencia', 'pzaMarcaModeloCompatible',
        'pzaNumeroSerieMaquinaOrigen', 'pzaEstadoPieza', 'pzaDestinoCliente',
        'pzaProcedenciaEstadoMaquina', 'pzaCodigoAveriaMaquina', 'pzaRelacionAveriaPieza',
        'pzaUbicacionFisica', 'pzaFechaAlta', 'pzaFechaUsoBaja', 'pzaObservaciones'
    ];
    const borrador = {};
    campos.forEach(function(idCampo) {
        const el = document.getElementById(idCampo);
        if (el) {
            borrador[idCampo] = el.value;
        }
    });
    
    const nivelSeleccionado = document.querySelector('input[name="nivelEstado"]:checked');
    if (nivelSeleccionado) {
        borrador['nivelEstado'] = nivelSeleccionado.value;
    }

    localStorage.setItem('techcorp_draft_pieza', JSON.stringify(borrador));
}

function restaurarBorradorPieza() {
    const id = document.getElementById('pzaId').value;
    if (id) return;

    const borradorStr = localStorage.getItem('techcorp_draft_pieza');
    if (borradorStr) {
        try {
            const borrador = JSON.parse(borradorStr);
            Object.keys(borrador).forEach(function(idCampo) {
                if (idCampo === 'nivelEstado') {
                    const radio = document.getElementById('nivel' + borrador[idCampo]);
                    if (radio) radio.checked = true;
                } else {
                    const el = document.getElementById(idCampo);
                    if (el && borrador[idCampo]) {
                        el.value = borrador[idCampo];
                    }
                }
            });
            onCambioEstadoPiezaForm();
        } catch (e) {
            console.error('Error al restaurar borrador de pieza', e);
        }
    }
}

function limpiarBorradorPieza() {
    localStorage.removeItem('techcorp_draft_pieza');
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 9: INICIALIZACIÓN
   ═══════════════════════════════════════════════════════════════════ */

/**
 * Se ejecuta cuando el documento ha terminado de cargar.
 * Configura la fecha por defecto y prepara el estado inicial.
 */
document.addEventListener('DOMContentLoaded', function() {
    // Se establece la fecha de hoy como valor por defecto
    // en los campos de fecha de entrada y alta.
    var campoFechaMaq = document.getElementById('maqFechaEntrada');
    if (campoFechaMaq && !campoFechaMaq.value) {
        campoFechaMaq.value = obtenerFechaHoy();
    }

    var campoFechaPza = document.getElementById('pzaFechaAlta');
    if (campoFechaPza && !campoFechaPza.value) {
        campoFechaPza.value = obtenerFechaHoy();
    }

    // Se permite buscar con Enter en el campo de QR manual.
    document.getElementById('inputQrManual').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            buscarPorQrManual();
        }
    });

    // Buscador global en tiempo real (al escribir) y Enter
    const inputBuscarGlobal = document.getElementById('inputBuscarGlobal');
    if (inputBuscarGlobal) {
        inputBuscarGlobal.addEventListener('input', function() {
            clearTimeout(buscarGlobalTimeout);
            buscarGlobalTimeout = setTimeout(function() {
                buscarGlobal(true);
            }, 300); // Debounce de 300ms
        });

        inputBuscarGlobal.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                clearTimeout(buscarGlobalTimeout);
                buscarGlobal();
            }
        });
    }

    // Enter en reimpresión
    const inputReimpresionId = document.getElementById('inputReimpresionId');
    if (inputReimpresionId) {
        inputReimpresionId.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                buscarYReimprimirEtiqueta();
            }
        });
    }

    // Identificación del técnico al iniciar
    const tecnicoActual = localStorage.getItem('techcorp_tecnico');
    if (!tecnicoActual) {
        setTimeout(function() {
            mostrarModalTecnico();
        }, 300);
    } else {
        const nameEl = document.getElementById('nombreTecnicoNav');
        if (nameEl) {
            nameEl.textContent = tecnicoActual;
        }
        cargarNotificacionesDevolucion();
    }

    // Listeners para guardar borradores en tiempo real
    const formMaquina = document.getElementById('formMaquina');
    if (formMaquina) {
        formMaquina.addEventListener('input', guardarBorradorMaquina);
        formMaquina.addEventListener('change', guardarBorradorMaquina);
    }
    const inputModelo = document.getElementById('maqModelo');
    if (inputModelo) {
        inputModelo.addEventListener('input', function() {
            actualizarToneresCompatibles(inputModelo.value);
        });
        inputModelo.addEventListener('change', function() {
            actualizarToneresCompatibles(inputModelo.value);
        });
    }

    const formPieza = document.getElementById('formPieza');
    if (formPieza) {
        formPieza.addEventListener('input', guardarBorradorPieza);
        formPieza.addEventListener('change', guardarBorradorPieza);
    }

    // Advertencia de salida/recarga antes de salir si hay borradores con contenido
    window.addEventListener('beforeunload', function (e) {
        const maqId = document.getElementById('maqId').value;
        const pzaId = document.getElementById('pzaId').value;

        let tieneCambiosMaquina = false;
        let tieneCambiosPieza = false;

        if (!maqId) {
            const borradorMaq = localStorage.getItem('techcorp_draft_maquina');
            if (borradorMaq) {
                try {
                    const borrador = JSON.parse(borradorMaq);
                    tieneCambiosMaquina = Object.entries(borrador).some(function(pair) {
                        const key = pair[0];
                        const val = pair[1];
                        if (key === 'maqFechaEntrada') return false;
                        return val && typeof val === 'string' && val.trim() !== '';
                    });
                } catch (err) {}
            }
        }

        if (!pzaId) {
            const borradorPza = localStorage.getItem('techcorp_draft_pieza');
            if (borradorPza) {
                try {
                    const borrador = JSON.parse(borradorPza);
                    tieneCambiosPieza = Object.entries(borrador).some(function(pair) {
                        const key = pair[0];
                        const val = pair[1];
                        if (key === 'pzaFechaAlta') return false;
                        return val && typeof val === 'string' && val.trim() !== '';
                    });
                } catch (err) {}
            }
        }

        if (tieneCambiosMaquina || tieneCambiosPieza) {
            e.preventDefault();
            e.returnValue = 'Tiene cambios sin guardar en los formularios. ¿Seguro que desea salir?';
            return e.returnValue;
        }
    });

    // Inicializar el estado de navegación de forma segura
    const vistaInicial = window.location.hash.replace('#', '') || 'inicio';
    try {
        history.replaceState({ vista: vistaInicial }, '', '#' + vistaInicial);
    } catch(err) {
        console.warn('Error al inicializar history state:', err);
    }
    mostrarVista(vistaInicial, false);
});

// Listener para gestionar la navegación del historial (Back / Forward)
window.addEventListener('popstate', function(event) {
    if (event.state && event.state.vista) {
        const nombre = event.state.vista;
        const data = event.state.data;
        if (nombre === 'fichaMaquina' && data) {
            fetch(API_BASE + '/maquinas/' + data.id)
                .then(function(res) { return res.json(); })
                .then(function(m) { mostrarFichaMaquina(m, false); })
                .catch(function() { mostrarFichaMaquina(data, false); });
        } else if (nombre === 'fichaPieza' && data) {
            fetch(API_BASE + '/piezas/' + data.id)
                .then(function(res) { return res.json(); })
                .then(function(p) { mostrarFichaPieza(p, false); })
                .catch(function() { mostrarFichaPieza(data, false); });
        } else if (nombre === 'fichaToner' && data) {
            fetch(API_BASE + '/toners/' + data.id)
                .then(function(res) { return res.json(); })
                .then(function(t) { mostrarFichaToner(t, false); })
                .catch(function() { mostrarFichaToner(data, false); });
        } else if (nombre === 'formMaquina' && data) {
            cargarMaquinaEnFormulario(data, false);
        } else if (nombre === 'formPieza' && data) {
            cargarPiezaEnFormulario(data, false);
        } else {
            mostrarVista(nombre, false);
        }
    } else {
        const hash = window.location.hash.replace('#', '');
        if (hash) {
            mostrarVista(hash, false);
        } else {
            mostrarVista('inicio', false);
        }
    }
});

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 10: MÓDULO DE TÓNERES
   ═══════════════════════════════════════════════════════════════════ */

function cargarListaToners() {
    const buscador = document.getElementById('buscadorLocalToners');
    if (buscador) buscador.value = '';

    fetch(API_BASE + '/toners')
        .then(function(res) { return res.json(); })
        .then(function(toners) {
            listaTonersActual = toners;
            renderTablaToners(toners);
        })
        .catch(function(err) {
            console.error(err);
            mostrarToast('Error al cargar la lista de tóneres.', 'danger');
        });
}

function renderTablaToners(toners) {
    const contenedor = document.getElementById('tablaToners');
    if (!contenedor) return;

    if (toners.length === 0) {
        contenedor.innerHTML = 
            '<div class="text-center text-secondary py-5">' +
            '<i class="bi bi-droplet fs-1 d-block mb-3"></i>' +
            'No se encontraron tóneres en stock.</div>';
        return;
    }

    let html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr>';
    html += '<th>Código QR</th><th>Modelo</th><th>Nivel</th><th>Ubicación</th><th>Fecha Registro</th><th>Acciones</th>';
    html += '</tr></thead><tbody>';

    toners.forEach(function(t) {
        const fechaFmt = t.fechaRegistro ? new Date(t.fechaRegistro).toLocaleDateString('es-ES') : '-';
        html += '<tr onclick="cargarFichaTonerPorId(' + t.id + ')" style="cursor: pointer;">';
        html += '<td><span class="badge bg-secondary">' + t.codigoQr + '</span></td>';
        html += '<td>' + t.modelo + '</td>';
        html += '<td>';
        let badgeColor = 'bg-success';
        if (t.nivelToner === '25%') badgeColor = 'bg-danger';
        else if (t.nivelToner === '50%') badgeColor = 'bg-warning text-dark';
        else if (t.nivelToner === '75%') badgeColor = 'bg-info text-dark';
        html += '<span class="badge ' + badgeColor + '">' + t.nivelToner + '</span>';
        html += '</td>';
        html += '<td>' + (t.ubicacionFisica || '-') + '</td>';
        html += '<td>' + fechaFmt + '</td>';
        html += '<td>';
        html += '<button class="btn btn-sm btn-outline-info me-2" onclick="event.stopPropagation(); abrirModalImprimirQrToner(' + JSON.stringify(t).replace(/"/g, '&quot;') + ')">';
        html += '<i class="bi bi-printer me-1"></i>Imprimir QR</button>';
        html += '<button class="btn btn-sm btn-outline-danger" onclick="event.stopPropagation(); eliminarToner(' + t.id + ')">';
        html += '<i class="bi bi-trash me-1"></i>Eliminar</button>';
        html += '</td>';
        html += '</tr>';
    });

    html += '</tbody></table>';
    contenedor.innerHTML = html;
}

function abrirModalManualToner() {
    document.getElementById('formManualToner').reset();
    const modalEl = document.getElementById('modalManualToner');
    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    modal.show();
}

function guardarTonerManual(event) {
    event.preventDefault();

    const modelo = document.getElementById('tonModelo').value.trim();
    const nivelToner = document.getElementById('tonNivelToner').value;
    const ubicacionFisica = document.getElementById('tonUbicacionFisica').value.trim();

    if (!modelo || !nivelToner) {
        mostrarToast('Por favor, rellene todos los campos obligatorios.', 'warning');
        return;
    }

    const payload = {
        modelo: modelo,
        nivelToner: nivelToner,
        ubicacionFisica: ubicacionFisica,
        estado: 'Disponible'
    };

    fetch(API_BASE + '/toners?tecnico=' + encodeURIComponent(obtenerTecnico()), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(function(res) {
        if (res.ok) return res.json();
        throw new Error('Error al guardar el tóner.');
    })
    .then(function(toner) {
        const modalEl = document.getElementById('modalManualToner');
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.hide();

        mostrarToast('Tóner registrado correctamente.', 'success');
        cargarListaToners();

        abrirModalImprimirQrToner(toner);
    })
    .catch(function(err) {
        console.error(err);
        mostrarToast(err.message, 'danger');
    });
}

function abrirModalImprimirQrToner(toner) {
    document.getElementById('printTituloPieza').textContent = "TÓNER";
    document.getElementById('printQrText').textContent = toner.codigoQr;
    document.getElementById('printOrigenText').textContent = toner.modelo;
    document.getElementById('printEstadoText').textContent = "Nivel: " + (toner.nivelToner || '100%');

    cargarQrEnModal(toner.codigoQr, 100, () => {
        const modalEl = document.getElementById('modalImprimirQr');
        const myModal = bootstrap.Modal.getOrCreateInstance(modalEl);
        myModal.show();
    });
}

function eliminarToner(id) {
    const motivo = prompt('Por favor, indique el motivo de la baja del tóner:');
    if (!motivo || !motivo.trim()) {
        mostrarToast('Debe indicar un motivo para poder dar de baja el tóner.', 'warning');
        return;
    }

    if (!confirm('¿Está seguro de eliminar este tóner del stock?')) {
        return;
    }

    fetch(API_BASE + '/toners/' + id + '?motivo=' + encodeURIComponent(motivo.trim()) + '&tecnico=' + encodeURIComponent(obtenerTecnico()), {
        method: 'DELETE'
    })
    .then(function(res) {
        if (res.ok) {
            mostrarToast('Tóner eliminado correctamente.', 'success');
            cargarListaToners();
        } else {
            throw new Error('Error al eliminar el tóner.');
        }
    })
    .catch(function(err) {
        console.error(err);
        mostrarToast(err.message, 'danger');
    });
}

function filtrarTonersLocalmente() {
    var term = document.getElementById('buscadorLocalToners').value.toLowerCase().trim();
    if (!term) {
        renderTablaToners(listaTonersActual);
        return;
    }
    var resultado = listaTonersActual.filter(function(t) {
        return (t.modelo && t.modelo.toLowerCase().includes(term)) ||
               (t.codigoQr && t.codigoQr.toLowerCase().includes(term)) ||
               (t.nivelToner && t.nivelToner.toLowerCase().includes(term)) ||
               (t.ubicacionFisica && t.ubicacionFisica.toLowerCase().includes(term));
    });
    renderTablaToners(resultado);
}

/**
 * Carga el detalle del tóner por ID desde el backend y muestra su ficha.
 */
function cargarFichaTonerPorId(id) {
    fetch(API_BASE + '/toners/' + id)
        .then(function(response) {
            if (response.ok) return response.json();
            throw new Error('Tóner no encontrado.');
        })
        .then(function(toner) {
            mostrarFichaToner(toner);
        })
        .catch(function(error) {
            mostrarToast(error.message, 'danger');
        });
}

/**
 * Renderiza la ficha de detalle de un tóner con barra de progreso gráfica y dinámica.
 */
function mostrarFichaToner(toner, pushToHistory = true) {
    let levelPct = 0;
    if (toner.nivelToner) {
        levelPct = parseInt(toner.nivelToner) || 0;
    }

    let barColorClass = 'bg-success';
    if (levelPct <= 25) {
        barColorClass = 'bg-danger';
    } else if (levelPct <= 50) {
        barColorClass = 'bg-warning text-dark';
    } else if (levelPct <= 75) {
        barColorClass = 'bg-info text-dark';
    }

    var html = '<div class="card bg-card mb-4">';
    html += '  <div class="card-header bg-header fs-5 fw-bold text-dark">';
    html += '    <i class="bi bi-droplet-fill me-2 text-accent"></i>Tóner — ' + (toner.modelo || 'Sin Modelo');
    html += '  </div>';
    html += '  <div class="card-body p-3">';

    // Barra de progreso gráfica y dinámica
    html += '    <div class="mb-4 p-3 bg-input rounded border border-secondary">';
    html += '      <h6 class="text-accent fw-bold mb-3"><i class="bi bi-speedometer2 me-2"></i>Nivel de Tóner</h6>';
    html += '      <div class="progress bg-dark" style="height: 30px; border-radius: 8px; box-shadow: inset 0 2px 5px rgba(0,0,0,0.5); overflow: hidden;">';
    html += '        <div class="progress-bar progress-bar-striped progress-bar-animated ' + barColorClass + ' fw-bold text-uppercase" role="progressbar" style="width: ' + levelPct + '%; font-size: 1.1rem; line-height: 30px; transition: width 1s ease-in-out;" aria-valuenow="' + levelPct + '" aria-valuemin="0" aria-valuemax="100">';
    html += '          ' + levelPct + '%';
    html += '        </div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 1: Datos Identificación
    html += '    <div class="mb-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-upc-scan me-2"></i>1. Identificación y Estado</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Código QR', toner.codigoQr) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Modelo', toner.modelo) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Estado de Stock', toner.estado || 'Disponible') + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Máquina de Origen (Serie)', toner.maquinaOrigenSerie || 'No especificada') + '</div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 2: Ubicación
    html += '    <div class="mb-2">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-geo-alt me-2"></i>2. Ubicación y Registro</h6>';
    html += '      <div class="row g-3">';
    html += '        <div class="col-md-6">' + renderCampoFicha('Ubicación Física', toner.ubicacionFisica) + '</div>';
    html += '        <div class="col-md-6">' + renderCampoFicha('Fecha Registro', toner.fechaRegistro ? new Date(toner.fechaRegistro).toLocaleDateString('es-ES') : '-') + '</div>';
    html += '      </div>';
    html += '    </div>';

    // Sección 3: Máquinas Compatibles (Catálogo)
    html += '    <div class="mb-2 mt-4">';
    html += '      <h6 class="text-accent border-bottom pb-2 fw-bold"><i class="bi bi-printer me-2"></i>3. Máquinas Compatibles (Catálogo)</h6>';
    html += '      <div id="fichaTonerCompatiblesList" class="p-3 rounded bg-input border border-secondary text-secondary">';
    html += '        Cargando máquinas compatibles...';
    html += '      </div>';
    html += '    </div>';

    html += '  </div>';
    html += '</div>';

    document.getElementById('contenidoFichaToner').innerHTML = html;

    // Cargar las máquinas compatibles de forma asíncrona
    const compatiblesList = document.getElementById('fichaTonerCompatiblesList');
    if (compatiblesList && toner.modelo) {
        fetch(API_BASE + '/catalogo/maquinas-compatibles?toner=' + encodeURIComponent(toner.modelo))
            .then(function(res) { return res.json(); })
            .then(function(maquinas) {
                if (maquinas && maquinas.length > 0) {
                    let htmlList = '<div class="list-group bg-dark border-secondary">';
                    maquinas.forEach(function(m, idx) {
                        htmlList += '<button type="button" class="list-group-item list-group-item-action bg-dark text-white border-secondary d-flex justify-content-between align-items-center py-2" onclick="seleccionarMaquinaCompatible(\'' + m.marca + '\', \'' + m.modelo + '\', \'' + toner.modelo + '\', ' + idx + ')">';
                        htmlList += '  <span><i class="bi bi-printer-fill text-accent me-2"></i><strong>' + m.marca + '</strong> ' + m.modelo + '</span>';
                        htmlList += '  <i class="bi bi-chevron-down text-secondary fs-5"></i>';
                        htmlList += '</button>';
                        htmlList += '<div id="installOpts_' + idx + '" class="d-none p-3 bg-card border-top border-secondary text-center">';
                        htmlList += '  <button class="btn btn-accent btn-sm" onclick="redigirAOpcionesInstalacion(\'' + m.marca + '\', \'' + m.modelo + '\', \'' + toner.modelo + '\', \'' + toner.nivelToner + '\')">';
                        htmlList += '    <i class="bi bi-plus-circle me-1"></i>Añadir a máquina';
                        htmlList += '  </button>';
                        htmlList += '</div>';
                    });
                    htmlList += '</div>';
                    compatiblesList.innerHTML = htmlList;
                } else {
                    compatiblesList.innerHTML = '<span class="text-secondary small">No hay máquinas compatibles registradas en el catálogo.</span>';
                }
            })
            .catch(function(err) {
                console.error(err);
                compatiblesList.innerHTML = '<span class="text-danger small">Error al cargar máquinas compatibles.</span>';
            });
    } else if (compatiblesList) {
        compatiblesList.innerHTML = '<span class="text-secondary small">Modelo de tóner no válido.</span>';
    }

    // Configurar acciones
    document.getElementById('btnImprimirEtiquetaTonerFicha').onclick = function() {
        abrirModalImprimirQrToner(toner);
    };
    document.getElementById('btnEliminarTonerFicha').onclick = function() {
        eliminarTonerFicha(toner.id);
    };

    mostrarVista('fichaToner', pushToHistory, toner);
}

/**
 * Elimina un tóner desde la ficha de detalle y regresa al escáner.
 */
function eliminarTonerFicha(id) {
    const motivo = prompt('Por favor, indique el motivo de la baja del tóner:');
    if (!motivo || !motivo.trim()) {
        mostrarToast('Debe indicar un motivo para poder dar de baja el tóner.', 'warning');
        return;
    }

    if (!confirm('¿Está seguro de eliminar este tóner del stock?')) {
        return;
    }

    fetch(API_BASE + '/toners/' + id + '?motivo=' + encodeURIComponent(motivo.trim()) + '&tecnico=' + encodeURIComponent(obtenerTecnico()), {
        method: 'DELETE'
    })
    .then(function(res) {
        if (res.ok) {
            mostrarToast('Tóner eliminado correctamente.', 'success');
            mostrarVista('scanner');
        } else {
            throw new Error('Error al eliminar el tóner.');
        }
    })
    .catch(function(err) {
        console.error(err);
        mostrarToast(err.message, 'danger');
    });
}

function cargarNotificacionesDevolucion() {
    fetch(API_BASE + '/maquinas')
        .then(function(res) { return res.ok ? res.json() : []; })
        .then(function(maquinas) {
            const devueltas = maquinas.filter(function(m) {
                return !m.preparadaComercial && m.motivoDevolucion;
            });
            const container = document.getElementById('contenedorNotificacionesDevolucion');
            const lista = document.getElementById('listaDevolucionesComercial');
            if (!container || !lista) return;

            if (devueltas.length === 0) {
                container.classList.add('d-none');
                lista.innerHTML = '';
                return;
            }

            container.classList.remove('d-none');
            let html = '<div class="list-group list-group-flush">';
            devueltas.forEach(function(m) {
                html += '<div class="list-group-item bg-input text-dark py-2 px-3 border-bottom border-secondary d-flex justify-content-between align-items-center">';
                html += '  <div>';
                html += '    <strong class="text-danger d-block">' + m.marca + ' ' + m.modelo + ' (' + m.numeroSerie + ')</strong>';
                html += '    <span class="small text-secondary">Motivo: ' + m.motivoDevolucion + '</span>';
                html += '  </div>';
                html += '  <button class="btn btn-sm btn-outline-success" onclick="marcarDevolucionLeida(' + m.id + ')">';
                html += '    <i class="bi bi-check-circle"></i> Leído';
                html += '  </button>';
                html += '</div>';
            });
            html += '</div>';
            lista.innerHTML = html;
        })
        .catch(function(err) {
            console.error('Error al cargar notificaciones de devolución:', err);
        });
}

function marcarDevolucionLeida(id) {
    fetch(API_BASE + '/maquinas/' + id + '/limpiar-devolucion', { method: 'PUT' })
        .then(function(res) {
            if (res.ok) {
                cargarNotificacionesDevolucion();
                mostrarToast('Notificación marcada como leída.', 'success');
            } else {
                throw new Error('Error al limpiar la notificación.');
            }
        })
        .catch(function(err) {
            console.error(err);
            mostrarToast(err.message, 'danger');
        });
}

function cargarMarcasYModelosAutocomplete() {
    const selectMarca = document.getElementById('maqMarca');
    if (selectMarca) {
        selectMarca.innerHTML = '<option value="" style="background-color: #ffffff !important; color: #000000 !important;">-- Cargando marcas... --</option>';
        fetch(API_BASE + '/maquinas/marcas')
            .then(function(res) { return res.json(); })
            .then(function(marcas) {
                const currentVal = selectMarca.value;
                let html = '<option value="" style="background-color: #ffffff !important; color: #000000 !important;">-- Seleccione marca --</option>';
                marcas.forEach(function(m) {
                    html += '<option value="' + m + '" style="background-color: #ffffff !important; color: #000000 !important;">' + m + '</option>';
                });
                selectMarca.innerHTML = html;
                if (currentVal) {
                    selectMarca.value = currentVal;
                }
            })
            .catch(function(err) {
                console.error('Error al cargar marcas:', err);
                selectMarca.innerHTML = '<option value="" style="background-color: #ffffff !important; color: #000000 !important;">-- Error al cargar marcas --</option>';
            });
    }

    const datalistModelos = document.getElementById('listModelos');
    if (datalistModelos) {
        fetch(API_BASE + '/maquinas/modelos')
            .then(function(res) { return res.json(); })
            .then(function(modelos) {
                let html = '';
                modelos.forEach(function(mod) {
                    html += '<option value="' + mod + '">';
                });
                datalistModelos.innerHTML = html;
            })
            .catch(function(err) {
                console.error('Error al cargar modelos autocomplete:', err);
            });
    }
}

function actualizarToneresCompatibles(modelo, force) {
    const container = document.getElementById('compatiblesContainer');
    if (!container) return;

    const valor = (modelo || '').trim();
    if (!valor) {
        container.innerHTML = '';
        return;
    }

    const datalist = document.getElementById('listModelos');
    let isValid = false;
    if (datalist) {
        const options = Array.from(datalist.options).map(function(o) { return o.value; });
        if (options.includes(valor)) {
            isValid = true;
        }
    }

    if (isValid || force) {
        fetch(API_BASE + '/catalogo/compatibles?modelo=' + encodeURIComponent(valor))
            .then(function(res) { return res.json(); })
            .then(function(toneres) {
                container.innerHTML = '';
                if (toneres && toneres.length > 0) {
                    toneres.forEach(function(t) {
                        const span = document.createElement('span');
                        span.className = 'badge bg-info text-dark';
                        span.style.marginRight = '4px';
                        span.style.marginBottom = '4px';
                        span.textContent = t;
                        container.appendChild(span);
                    });
                }
            })
            .catch(function(err) {
                console.error('Error al obtener tóneres compatibles:', err);
            });
    } else {
        container.innerHTML = '';
    }
}


function cargarListasReimpresion() {
    const selectMaq = document.getElementById('selectReimpresionMaquina');
    if (selectMaq) {
        selectMaq.innerHTML = '<option value="">-- Cargando máquinas... --</option>';
        fetch(API_BASE + '/maquinas')
            .then(function(res) { return res.json(); })
            .then(function(maquinas) {
                let options = ['<option value="">-- Seleccione máquina --</option>'];
                maquinas.forEach(function(m) {
                    const desc = m.codigoQr + ' - ' + (m.marca || '') + ' ' + (m.modelo || '') + ' (' + (m.numeroSerie || '') + ')';
                    options.push('<option value="' + m.codigoQr + '">' + desc + '</option>');
                });
                selectMaq.innerHTML = options.join('');
            })
            .catch(function() {
                selectMaq.innerHTML = '<option value="">-- Error al cargar máquinas --</option>';
            });
    }

    const selectPza = document.getElementById('selectReimpresionPieza');
    if (selectPza) {
        selectPza.innerHTML = '<option value="">-- Cargando piezas... --</option>';
        fetch(API_BASE + '/piezas')
            .then(function(res) { return res.json(); })
            .then(function(piezas) {
                let options = ['<option value="">-- Seleccione pieza --</option>'];
                piezas.forEach(function(p) {
                    const desc = p.codigoQrPieza + ' - ' + (p.tipoPieza || '') + ' (' + (p.referencia || '') + ')';
                    options.push('<option value="' + p.codigoQrPieza + '">' + desc + '</option>');
                });
                selectPza.innerHTML = options.join('');
            })
            .catch(function() {
                selectPza.innerHTML = '<option value="">-- Error al cargar piezas --</option>';
            });
    }

    const selectTnr = document.getElementById('selectReimpresionToner');
    if (selectTnr) {
        selectTnr.innerHTML = '<option value="">-- Cargando tóneres... --</option>';
        fetch(API_BASE + '/toners')
            .then(function(res) { return res.json(); })
            .then(function(toners) {
                let options = ['<option value="">-- Seleccione tóner --</option>'];
                toners.forEach(function(t) {
                    const desc = t.codigoQr + ' - ' + (t.modelo || '') + ' (Nivel: ' + (t.nivelToner || '100%') + ')';
                    options.push('<option value="' + t.codigoQr + '">' + desc + '</option>');
                });
                selectTnr.innerHTML = options.join('');
            })
            .catch(function() {
                selectTnr.innerHTML = '<option value="">-- Error al cargar tóneres --</option>';
            });
    }
}

function seleccionarMaquinaReimpresion() {
    const val = document.getElementById('selectReimpresionMaquina').value;
    if (val) {
        document.getElementById('inputReimpresionId').value = val;
        document.getElementById('selectReimpresionPieza').value = '';
        document.getElementById('selectReimpresionToner').value = '';
        buscarYReimprimirEtiqueta();
    }
}

function seleccionarPiezaReimpresion() {
    const val = document.getElementById('selectReimpresionPieza').value;
    if (val) {
        document.getElementById('inputReimpresionId').value = val;
        document.getElementById('selectReimpresionMaquina').value = '';
        document.getElementById('selectReimpresionToner').value = '';
        buscarYReimprimirEtiqueta();
    }
}

function seleccionarTonerReimpresion() {
    const val = document.getElementById('selectReimpresionToner').value;
    if (val) {
        document.getElementById('inputReimpresionId').value = val;
        document.getElementById('selectReimpresionMaquina').value = '';
        document.getElementById('selectReimpresionPieza').value = '';
        buscarYReimprimirEtiqueta();
    }
}

/* ═══════════════════════════════════════════════════════════════════
   SECCIÓN 13: MÓDULO DE INVENTARIO
   ═══════════════════════════════════════════════════════════════════ */

function consultarInventario(tipo) {
    // Cancelar cualquier carga en curso
    if (inventarioInterval) clearInterval(inventarioInterval);
    if (inventarioTimeout) clearTimeout(inventarioTimeout);

    // Ocultar resultados previos
    const resultadosDiv = document.getElementById('resultadosInventario');
    resultadosDiv.classList.add('d-none');

    // Mostrar sección de carga
    const loadingDiv = document.getElementById('loadingInventario');
    loadingDiv.classList.remove('d-none');

    // Inicializar barra de progreso
    const barra = document.getElementById('barraProgresoInventario');
    const contador = document.getElementById('contadorSegundos');
    barra.style.width = '0%';
    barra.setAttribute('aria-valuenow', 0);
    barra.innerText = '0%';
    contador.innerText = '5s restantes';

    let elapsed = 0;
    const duration = 5000; // 5 segundos
    const step = 100; // actualizar cada 100ms
    
    // Iniciar llamada a la base de datos inmediatamente
    let url = '';
    let titulo = '';
    
    if (tipo === 'maquinas') {
        url = API_BASE + '/maquinas';
        titulo = 'Total de Máquinas Registradas';
    } else if (tipo === 'piezas') {
        url = API_BASE + '/piezas';
        titulo = 'Total de Piezas en Almacén';
    } else if (tipo === 'toneres') {
        url = API_BASE + '/toners';
        titulo = 'Total de Tóneres en Stock';
    }

    let loadedData = null;
    let loadError = null;

    fetch(url)
        .then(function(res) {
            if (!res.ok) throw new Error('Error en la respuesta del servidor');
            return res.json();
        })
        .then(function(data) {
            loadedData = data;
        })
        .catch(function(err) {
            console.error(err);
            loadError = err;
        });

    inventarioInterval = setInterval(function() {
        elapsed += step;
        const pct = Math.min(100, Math.floor((elapsed / duration) * 100));
        barra.style.width = pct + '%';
        barra.setAttribute('aria-valuenow', pct);
        barra.innerText = pct + '%';
        
        const remaining = Math.max(0, Math.ceil((duration - elapsed) / 1000));
        contador.innerText = remaining + 's restantes';

        if (elapsed >= duration) {
            clearInterval(inventarioInterval);
            inventarioInterval = null;
        }
    }, step);

    inventarioTimeout = setTimeout(function() {
        // Ocultar sección de carga
        loadingDiv.classList.add('d-none');

        if (loadError) {
            mostrarToast('Error al consultar el inventario.', 'danger');
            return;
        }

        if (!loadedData) {
            mostrarToast('No se pudieron obtener los datos a tiempo.', 'warning');
            return;
        }

        // Renderizar total y tabla
        document.getElementById('tituloResultadosInventario').innerText = titulo;
        document.getElementById('totalArticulosInventario').innerText = loadedData.length;

        let html = '';
        if (tipo === 'maquinas') {
            document.getElementById('desgloseToneres').classList.add('d-none');
            html = renderTablaInventarioMaquinas(loadedData);
        } else if (tipo === 'piezas') {
            document.getElementById('desgloseToneres').classList.add('d-none');
            html = renderTablaInventarioPiezas(loadedData);
        } else if (tipo === 'toneres') {
            // Calcular desglose de tóneres (En almacén / En máquina)
            const enAlmacen = loadedData.filter(function(t) { return t.estado !== 'En máquina'; }).length;
            const enMaquinas = loadedData.filter(function(t) { return t.estado === 'En máquina'; }).length;
            document.getElementById('toneresAlmacen').innerText = enAlmacen;
            document.getElementById('toneresMaquinas').innerText = enMaquinas;
            document.getElementById('desgloseToneres').classList.remove('d-none');
            
            html = renderTablaInventarioToneres(loadedData);
        }

        document.getElementById('tablaInventarioContenedor').innerHTML = html;
        resultadosDiv.classList.remove('d-none');

    }, duration);
}

function renderTablaInventarioMaquinas(maquinas) {
    if (maquinas.length === 0) {
        return '<div class="text-center text-secondary py-5"><i class="bi bi-inbox fs-1 d-block mb-3"></i>No hay máquinas registradas.</div>';
    }
    let html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr><th>QR</th><th>Marca</th><th>Modelo</th><th>Nº Serie</th><th>Decisión</th><th>Ubicación</th></tr></thead>';
    html += '<tbody>';
    maquinas.forEach(function(m) {
        html += '<tr onclick="cargarFichaMaquinaPorId(' + m.id + ')">';
        html += '<td>' + (m.codigoQr || '-') + '</td>';
        html += '<td>' + (m.marca || '-') + '</td>';
        html += '<td>' + (m.modelo || '-') + '</td>';
        html += '<td>' + (m.numeroSerie || '-') + '</td>';
        html += '<td>' + renderBadgeDecision(m.decisionTecnica) + '</td>';
        html += '<td>' + (m.ubicacionFisica || '-') + '</td>';
        html += '</tr>';
    });
    html += '</tbody></table>';
    return html;
}

function renderTablaInventarioPiezas(piezas) {
    if (piezas.length === 0) {
        return '<div class="text-center text-secondary py-5"><i class="bi bi-inbox fs-1 d-block mb-3"></i>No hay piezas en el almacén.</div>';
    }
    let html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr><th>Código QR</th><th>Tipo</th><th>Nivel</th><th>Máquina Origen</th><th>Referencia</th><th>Ubicación</th></tr></thead>';
    html += '<tbody>';
    piezas.forEach(function(p) {
        html += '<tr onclick="cargarFichaPiezaPorId(' + p.id + ')">';
        html += '<td><span class="badge bg-secondary">' + (p.codigoQrPieza || ('PZ-' + p.id)) + '</span></td>';
        html += '<td>' + (p.tipoPieza || '-') + '</td>';
        html += '<td>' + renderBadgeNivel(p.nivelEstado) + '</td>';
        html += '<td>' + (p.numeroSerieMaquinaOrigen || '-') + '</td>';
        html += '<td>' + (p.referencia || '-') + '</td>';
        html += '<td>' + (p.ubicacionFisica || '-') + '</td>';
        html += '</tr>';
    });
    html += '</tbody></table>';
    return html;
}

function renderTablaInventarioToneres(toneres) {
    if (toneres.length === 0) {
        return '<div class="text-center text-secondary py-5"><i class="bi bi-inbox fs-1 d-block mb-3"></i>No hay tóneres en stock.</div>';
    }
    let html = '<table class="table table-dark-techcorp table-hover">';
    html += '<thead><tr><th>Código QR</th><th>Modelo</th><th>Nivel</th><th>Ubicación</th><th>Estado</th><th>Máquina Origen</th></tr></thead>';
    html += '<tbody>';
    toneres.forEach(function(t) {
        let badgeColor = 'bg-success';
        if (t.nivelToner === '25%') badgeColor = 'bg-danger';
        else if (t.nivelToner === '50%') badgeColor = 'bg-warning text-dark';
        else if (t.nivelToner === '75%') badgeColor = 'bg-info text-dark';
        
        html += '<tr onclick="cargarFichaTonerPorId(' + t.id + ')">';
        html += '<td><span class="badge bg-secondary">' + (t.codigoQr || '-') + '</span></td>';
        html += '<td>' + (t.modelo || '-') + '</td>';
        html += '<td><span class="badge ' + badgeColor + '">' + (t.nivelToner || '-') + '</span></td>';
        html += '<td>' + (t.ubicacionFisica || '-') + '</td>';
        html += '<td>' + (t.estado || '-') + '</td>';
        html += '<td>' + (t.maquinaOrigenSerie || '-') + '</td>';
        html += '</tr>';
    });
    html += '</tbody></table>';
    return html;
}

function seleccionarMaquinaCompatible(marca, modelo, tonerModelo, index) {
    // Ocultar otras opciones
    const allDivs = document.querySelectorAll('[id^="installOpts_"]');
    allDivs.forEach(function(div) {
        if (div.id !== 'installOpts_' + index) {
            div.classList.add('d-none');
        }
    });
    // Alternar la visibilidad de las opciones de la máquina seleccionada
    const targetDiv = document.getElementById('installOpts_' + index);
    if (targetDiv) {
        targetDiv.classList.toggle('d-none');
    }
}

function redigirAOpcionesInstalacion(marca, modelo, tonerModelo, tonerNivel) {
    // Redirigir a Dar de Alta Máquina (vistaFormMaquina) pasando los parámetros en el hash
    const params = new URLSearchParams();
    params.set('marca', marca);
    params.set('modelo', modelo);
    params.set('toner', tonerModelo);
    if (tonerNivel) {
        let nivelClean = tonerNivel.replace('%', '');
        params.set('nivel', nivelClean);
    }
    
    window.location.hash = 'formMaquina?' + params.toString();
    
    // Limpiar el ID para asegurar modo alta
    document.getElementById('maqId').value = '';
    document.getElementById('formMaquina').reset();
    
    mostrarVista('formMaquina', true, { marca: marca, modelo: modelo, toner: tonerModelo, nivel: tonerNivel });
}

function obtenerParametrosUrl() {
    const params = new URLSearchParams();
    
    // Leer de la query string
    if (window.location.search) {
        const searchParams = new URLSearchParams(window.location.search);
        for (const [key, value] of searchParams.entries()) {
            params.set(key, value);
        }
    }
    
    // Leer del hash
    const hash = window.location.hash;
    if (hash && hash.indexOf('?') !== -1) {
        const hashParams = new URLSearchParams(hash.substring(hash.indexOf('?') + 1));
        for (const [key, value] of hashParams.entries()) {
            params.set(key, value);
        }
    }
    
    return params;
}

function aplicarParametrosUrlFormMaquina() {
    const params = obtenerParametrosUrl();
    const brand = params.get('marca');
    const model = params.get('modelo');
    const toner = params.get('toner');
    const nivel = params.get('nivel');

    if (brand || model || toner || nivel) {
        const maqId = document.getElementById('maqId').value;
        if (!maqId) { // Solo en modo alta (nueva máquina)
            if (brand) {
                document.getElementById('maqMarca').value = brand;
            }
            if (model) {
                document.getElementById('maqModelo').value = model;
                actualizarToneresCompatibles(model, true);
            }
            if (toner) {
                document.getElementById('maqModeloToner').value = toner;
            }
            if (nivel) {
                let nivelStr = nivel;
                if (!nivelStr.endsWith('%')) {
                    nivelStr += '%';
                }
                document.getElementById('maqNivelToner').value = nivelStr;
            }
        }
    }
}




