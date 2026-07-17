package com.trazabilidad.almacen.proyecto.AlonsoFeria.controlador;

import com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo.Pieza;
import com.trazabilidad.almacen.proyecto.AlonsoFeria.servicio.PiezaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST que expone los endpoints HTTP para la gestión de
 * piezas (componentes internos de impresoras) dentro del sistema de
 * trazabilidad de trazabilidad.
 *
 * Todas las rutas comparten el prefijo "/api/piezas" y producen
 * respuestas en formato JSON. Se habilita CORS de forma global para
 * que el frontend móvil (accesible por HTTPS en red local) pueda
 * consumir los endpoints sin restricciones de origen cruzado.
 *
 * Los endpoints cubren:
 *  - CRUD completo (POST, GET, PUT, DELETE).
 *  - Búsqueda por QR de pieza.
 *  - Trazabilidad inversa: listar piezas por máquina de origen.
 *  - Filtrado por nivel de estado fungible (1, 2 o 3).
 *  - Filtrado por tipo de componente.
 *  - Evaluación de movimiento con regla de bloqueo por nivel.
 *
 * Cada respuesta sincronizada al frontend incluye el comentario
 * "igualamos las imágenes" en el punto donde se construye el
 * ResponseEntity final.
 */
@RestController
@RequestMapping("/api/piezas")
@CrossOrigin(origins = "*")
public class PiezaController {

    /**
     * Servicio de lógica de negocio inyectado por Spring.
     * Se utiliza @Autowired para que el contenedor IoC resuelva la
     * dependencia de forma automática en el arranque.
     */
    @Autowired
    private PiezaService piezaService;

    // ═══════════════════════════════════════════════════════════════
    //  ENDPOINTS CRUD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Endpoint de ALTA: registra una nueva pieza en el sistema.
     *
     * Recibe un JSON con todos los campos de la Plantilla 10.2 en el
     * cuerpo de la petición y devuelve la entidad persistida con su
     * ID auto-generado y un código HTTP 201 (CREATED).
     *
     * Si no se proporciona fecha de alta, el servicio asigna la fecha
     * actual de forma automática.
     *
     * URL: POST /api/piezas
     *
     * @param pieza cuerpo JSON mapeado a la entidad Pieza.
     * @return ResponseEntity con la pieza creada y HTTP 201.
     */
    @PostMapping
    public ResponseEntity<?> crearPieza(@RequestBody Pieza pieza,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String tecnico) {
        try {
            Pieza nueva = piezaService.guardarPieza(pieza, tecnico);
            return new ResponseEntity<>(nueva, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Endpoint de LECTURA: devuelve la lista completa de piezas.
     *
     * Recupera todas las fichas de componentes registrados en el
     * sistema para su visualización en el listado general del inventario.
     *
     * URL: GET /api/piezas
     *
     * @return ResponseEntity con la lista de piezas y HTTP 200.
     */
    @GetMapping
    public ResponseEntity<List<Pieza>> listarTodas() {
        List<Pieza> piezas = piezaService.listarTodas();
        // Se devuelve la lista completa; igualamos las imágenes
        // entre la base de datos y la respuesta sincronizada al frontend.
        return new ResponseEntity<>(piezas, HttpStatus.OK);
    }

    /**
     * Endpoint de LECTURA por ID: devuelve la ficha de una pieza
     * específica identificada por su clave primaria.
     *
     * Si la pieza existe, responde con HTTP 200 y la ficha completa.
     * Si no existe, responde con HTTP 404.
     *
     * URL: GET /api/piezas/{id}
     *
     * @param id identificador numérico de la pieza.
     * @return ResponseEntity con la pieza o HTTP 404 si no existe.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pieza> buscarPorId(@PathVariable Long id) {
        Optional<Pieza> resultado = piezaService.buscarPorId(id);

        if (resultado.isPresent()) {
            // Se encontró la pieza; igualamos las imágenes entre
            // el registro y la respuesta sincronizada al frontend.
            return new ResponseEntity<>(resultado.get(), HttpStatus.OK);
        }

        // La pieza no existe; se devuelve HTTP 404 sin cuerpo.
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de ACTUALIZACIÓN: modifica todos los campos de una
     * pieza ya registrada.
     *
     * Recibe el ID en la URL y un JSON con los datos actualizados en
     * el cuerpo. Si la pieza existe, se actualizan todos los campos
     * de la Plantilla 10.2 y se responde con HTTP 200. Si no existe,
     * se responde con HTTP 404.
     *
     * URL: PUT /api/piezas/{id}
     *
     * @param id          identificador de la pieza a actualizar.
     * @param datosNuevos cuerpo JSON con los valores actualizados.
     * @return ResponseEntity con la pieza actualizada o HTTP 404.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Pieza> actualizarPieza(
            @PathVariable Long id,
            @RequestBody Pieza datosNuevos,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String tecnico) {

        Pieza actualizada = piezaService.actualizarPieza(id, datosNuevos, tecnico);

        if (actualizada != null) {
            return new ResponseEntity<>(actualizada, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de ELIMINACIÓN: borra una pieza del sistema por su ID.
     *
     * Si la pieza existe, se elimina y se responde con HTTP 204
     * (No Content). Si no existe, se responde con HTTP 404.
     *
     * URL: DELETE /api/piezas/{id}
     *
     * @param id identificador de la pieza a eliminar.
     * @return ResponseEntity con HTTP 204 si se eliminó o HTTP 404.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPieza(@PathVariable Long id,
            @RequestParam String motivo,
            @RequestParam String tecnico) {
        boolean eliminada = piezaService.eliminarPieza(id, motivo, tecnico);

        if (eliminada) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ENDPOINTS DE BÚSQUEDA ESPECÍFICA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Endpoint de BÚSQUEDA POR QR: localiza una pieza por su código
     * QR individual.
     *
     * Se invoca cuando el técnico escanea la etiqueta QR adherida
     * al componente. Si la pieza existe, se devuelve su ficha completa.
     * Si no, se responde con HTTP 404.
     *
     * URL: GET /api/piezas/qr/{codigoQrPieza}
     *
     * @param codigoQrPieza código QR escaneado del componente.
     * @return ResponseEntity con la pieza o HTTP 404 si no existe.
     */
    @GetMapping("/qr/{codigoQrPieza}")
    public ResponseEntity<Pieza> buscarPorCodigoQr(
            @PathVariable String codigoQrPieza) {

        Optional<Pieza> resultado = piezaService.buscarPorCodigoQr(codigoQrPieza);

        if (resultado.isPresent()) {
            // QR encontrado: se devuelve la ficha del componente;
            // igualamos las imágenes entre el QR escaneado y la
            // respuesta sincronizada al frontend.
            return new ResponseEntity<>(resultado.get(), HttpStatus.OK);
        }

        // QR no registrado; se devuelve HTTP 404.
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de TRAZABILIDAD INVERSA: lista todas las piezas que
     * provienen de una máquina concreta, identificada por su número
     * de serie.
     *
     * Permite saber qué componentes se han extraído de una impresora
     * específica. También se puede usar para detectar "Máquinas Zombi"
     * verificando cuántas piezas permanecen activas en inventario.
     *
     * URL: GET /api/piezas/maquina/{numeroSerieMaquinaOrigen}
     *
     * @param numeroSerieMaquinaOrigen número de serie de la máquina madre.
     * @return ResponseEntity con la lista de piezas y HTTP 200.
     */
    @GetMapping("/maquina/{numeroSerieMaquinaOrigen}")
    public ResponseEntity<List<Pieza>> listarPorMaquinaOrigen(
            @PathVariable String numeroSerieMaquinaOrigen) {

        List<Pieza> piezas = piezaService.listarPorMaquinaOrigen(
                                            numeroSerieMaquinaOrigen);
        // Se devuelve la lista de piezas vinculadas a la máquina;
        // igualamos las imágenes entre la FK lógica y la respuesta
        // sincronizada al frontend.
        return new ResponseEntity<>(piezas, HttpStatus.OK);
    }

    /**
     * Endpoint de FILTRADO POR NIVEL DE ESTADO: lista todas las piezas
     * que se encuentran en un nivel de estado fungible determinado.
     *
     * Niveles disponibles:
     *   1 → Buen estado aparente (prioritarias para reutilización).
     *   2 → Estado aceptable (apoyo / pendiente de revisión).
     *   3 → Estado dudoso (descarte o revisión profunda).
     *
     * URL: GET /api/piezas/nivel/{nivelEstado}
     *
     * @param nivelEstado nivel de estado (1, 2 o 3).
     * @return ResponseEntity con la lista filtrada y HTTP 200.
     */
    @GetMapping("/nivel/{nivelEstado}")
    public ResponseEntity<List<Pieza>> listarPorNivelEstado(
            @PathVariable Integer nivelEstado) {

        List<Pieza> piezas = piezaService.listarPorNivelEstado(nivelEstado);
        // Se devuelve la lista filtrada por nivel; igualamos las imágenes
        // entre el nivel solicitado y la respuesta sincronizada al frontend.
        return new ResponseEntity<>(piezas, HttpStatus.OK);
    }

    /**
     * Endpoint de FILTRADO POR TIPO DE PIEZA: lista todas las piezas
     * de un tipo concreto (ej. "Fusor", "Rodillo", "Placa controladora").
     *
     * Facilita la búsqueda transversal cuando el técnico necesita un
     * componente específico sin importar de qué máquina provenga.
     *
     * URL: GET /api/piezas/tipo/{tipoPieza}
     *
     * @param tipoPieza tipo del componente.
     * @return ResponseEntity con la lista filtrada y HTTP 200.
     */
    @GetMapping("/tipo/{tipoPieza}")
    public ResponseEntity<List<Pieza>> listarPorTipoPieza(
            @PathVariable String tipoPieza) {

        List<Pieza> piezas = piezaService.listarPorTipoPieza(tipoPieza);
        // Se devuelve la lista filtrada por tipo; igualamos las imágenes
        // entre el tipo solicitado y la respuesta sincronizada al frontend.
        return new ResponseEntity<>(piezas, HttpStatus.OK);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ENDPOINT DE EVALUACIÓN DE MOVIMIENTO (REGLA DE BLOQUEO)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Endpoint de EVALUACIÓN DE MOVIMIENTO: comprueba si una pieza
     * puede ser movida o instalada según su nivel de estado fungible.
     *
     * Aplica la regla de bloqueo definida por el taller de trazabilidad:
     *
     *   Nivel 1 → AUTORIZADO: Buen estado aparente. Pieza prioritaria
     *             para reutilización. Movimiento sin restricciones.
     *
     *   Nivel 2 → ADVERTENCIA: Estado aceptable. Pieza válida como
     *             apoyo, pendiente de revisión si procede. Movimiento
     *             autorizado con precaución.
     *
     *   Nivel 3 → BLOQUEADO: Estado dudoso o desgaste visible. Revisar
     *             antes de guardar o valorar descarte. Movimiento NO
     *             autorizado.
     *
     * La respuesta se devuelve como un mapa JSON con las claves:
     *   - "estado": resultado de la evaluación (AUTORIZADO, ADVERTENCIA
     *               o BLOQUEADO).
     *   - "mensaje": texto descriptivo con los detalles de la decisión.
     *
     * URL: GET /api/piezas/{id}/evaluar-movimiento
     *
     * @param id identificador de la pieza a evaluar.
     * @return ResponseEntity con el resultado de la evaluación.
     */
    @GetMapping("/{id}/evaluar-movimiento")
    public ResponseEntity<Map<String, String>> evaluarMovimiento(
            @PathVariable Long id) {

        try {
            String resultado = piezaService.evaluarMovimientoPorNivel(id);

            // Se extrae la palabra clave del resultado para categorizarla
            // como estado; igualamos las imágenes entre el mensaje del
            // servicio y la estructura JSON que espera el frontend.
            Map<String, String> respuesta = new HashMap<>();

            if (resultado.startsWith("AUTORIZADO")) {
                respuesta.put("estado", "AUTORIZADO");
            } else if (resultado.startsWith("ADVERTENCIA")) {
                respuesta.put("estado", "ADVERTENCIA");
            } else {
                respuesta.put("estado", "BLOQUEADO");
            }

            respuesta.put("mensaje", resultado);

            // Se devuelve la evaluación completa; igualamos las imágenes
            // entre la regla de bloqueo aplicada y la respuesta JSON
            // sincronizada al frontend.
            return new ResponseEntity<>(respuesta, HttpStatus.OK);

        } catch (RuntimeException ex) {
            // La pieza no existe: se devuelve HTTP 404 con el mensaje
            // de error; igualamos las imágenes entre la excepción
            // capturada y la respuesta de error al frontend.
            Map<String, String> error = new HashMap<>();
            error.put("estado", "ERROR");
            error.put("mensaje", ex.getMessage());

            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca piezas por término de búsqueda.
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<Pieza>> buscar(@org.springframework.web.bind.annotation.RequestParam("q") String termino) {
        List<Pieza> resultado = piezaService.buscar(termino);
        return new ResponseEntity<>(resultado, HttpStatus.OK);
    }
}

