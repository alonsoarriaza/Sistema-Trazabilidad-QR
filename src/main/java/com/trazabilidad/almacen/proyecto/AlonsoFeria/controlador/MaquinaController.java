package com.trazabilidad.almacen.proyecto.AlonsoFeria.controlador;

import com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.trazabilidad.almacen.proyecto.AlonsoFeria.servicio.MaquinaService;
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

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST que expone los endpoints HTTP para la gestión de
 * máquinas (impresoras) dentro del sistema de trazabilidad de trazabilidad.
 *
 * Todas las rutas comparten el prefijo "/api/maquinas" y producen
 * respuestas en formato JSON. Se habilita CORS de forma global para
 * que el frontend móvil (accesible por HTTPS en red local) pueda
 * consumir los endpoints sin restricciones de origen cruzado.
 *
 * Los endpoints cubren:
 *  - CRUD completo (POST, GET, PUT, DELETE).
 *  - Bifurcación por escaneo QR (GET por código QR).
 *  - Consulta por número de serie (GET por número de serie).
 *  - Filtrado por decisión técnica (GET por decisión).
 *
 * Cada respuesta sincronizada al frontend incluye el comentario
 * "igualamos las imágenes" en el punto donde se construye el
 * ResponseEntity final.
 */
@RestController
@RequestMapping("/api/maquinas")
@CrossOrigin(origins = "*")
public class MaquinaController {

    /**
     * Servicio de lógica de negocio inyectado por Spring.
     * Se utiliza @Autowired para que el contenedor IoC resuelva la
     * dependencia de forma automática en el arranque.
     */
    @Autowired
    private MaquinaService maquinaService;

    // ═══════════════════════════════════════════════════════════════
    //  ENDPOINTS CRUD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Endpoint de ALTA: registra una nueva máquina en el sistema.
     *
     * Recibe un JSON con todos los campos de la Plantilla 10.1 en el
     * cuerpo de la petición y devuelve la entidad persistida con su
     * ID auto-generado y un código HTTP 201 (CREATED).
     *
     * URL: POST /api/maquinas
     *
     * @param maquina cuerpo JSON mapeado a la entidad Maquina.
     * @return ResponseEntity con la máquina creada y HTTP 201.
     */
    @PostMapping
    public ResponseEntity<Maquina> crearMaquina(@RequestBody Maquina maquina,
            @RequestParam(required = false) String tecnico) {
        Maquina nueva = maquinaService.guardarMaquina(maquina, tecnico);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    /**
     * Endpoint de LECTURA: devuelve la lista completa de máquinas.
     *
     * Recupera todas las fichas registradas en el sistema para su
     * visualización en el listado general del panel de control.
     *
     * URL: GET /api/maquinas
     *
     * @return ResponseEntity con la lista de máquinas y HTTP 200.
     */
    @GetMapping
    public ResponseEntity<List<Maquina>> listarTodas() {
        List<Maquina> maquinas = maquinaService.listarTodas();
        // Se devuelve la lista completa; igualamos las imágenes
        // entre la base de datos y la respuesta sincronizada al frontend.
        return new ResponseEntity<>(maquinas, HttpStatus.OK);
    }

    /**
     * Endpoint de LECTURA por ID: devuelve la ficha de una máquina
     * específica identificada por su clave primaria.
     *
     * Si la máquina existe, responde con HTTP 200 y la ficha completa.
     * Si no existe, responde con HTTP 404.
     *
     * URL: GET /api/maquinas/{id}
     *
     * @param id identificador numérico de la máquina.
     * @return ResponseEntity con la máquina o HTTP 404 si no existe.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Maquina> buscarPorId(@PathVariable Long id) {
        Optional<Maquina> resultado = maquinaService.buscarPorId(id);

        if (resultado.isPresent()) {
            // Se encontró la máquina; igualamos las imágenes entre
            // el registro y la respuesta sincronizada al frontend.
            return new ResponseEntity<>(resultado.get(), HttpStatus.OK);
        }

        // La máquina no existe; se devuelve HTTP 404 sin cuerpo.
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de ACTUALIZACIÓN: modifica todos los campos de una
     * máquina ya registrada.
     *
     * Recibe el ID en la URL y un JSON con los datos actualizados en
     * el cuerpo. Si la máquina existe, se actualizan todos los campos
     * de la Plantilla 10.1 y se responde con HTTP 200. Si no existe,
     * se responde con HTTP 404.
     *
     * URL: PUT /api/maquinas/{id}
     *
     * @param id          identificador de la máquina a actualizar.
     * @param datosNuevos cuerpo JSON con los valores actualizados.
     * @return ResponseEntity con la máquina actualizada o HTTP 404.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Maquina> actualizarMaquina(
            @PathVariable Long id,
            @RequestBody Maquina datosNuevos,
            @RequestParam(required = false) String tecnico) {

        Maquina actualizada = maquinaService.actualizarMaquina(id, datosNuevos, tecnico);

        if (actualizada != null) {
            return new ResponseEntity<>(actualizada, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de ELIMINACIÓN: borra una máquina del sistema por su ID.
     *
     * Si la máquina existe, se elimina y se responde con HTTP 204
     * (No Content). Si no existe, se responde con HTTP 404.
     *
     * URL: DELETE /api/maquinas/{id}
     *
     * @param id identificador de la máquina a eliminar.
     * @return ResponseEntity con HTTP 204 si se eliminó o HTTP 404.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMaquina(@PathVariable Long id,
            @RequestParam String motivo,
            @RequestParam String tecnico) {
        boolean eliminada = maquinaService.eliminarMaquina(id, motivo, tecnico);

        if (eliminada) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ENDPOINTS DE BÚSQUEDA ESPECÍFICA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Endpoint de BIFURCACIÓN POR QR: busca una máquina por su código QR.
     *
     * Este endpoint es la pieza central del flujo de escaneo inicial.
     * Cuando el técnico escanea un QR con su móvil, el frontend invoca
     * esta ruta:
     *
     *  - Si se encuentra una máquina → HTTP 200 con la ficha completa.
     *    El frontend muestra la "Ficha de la Máquina".
     *
     *  - Si NO se encuentra → HTTP 404 sin cuerpo.
     *    El frontend abre automáticamente el "Formulario de Registro".
     *
     * URL: GET /api/maquinas/qr/{codigoQr}
     *
     * @param codigoQr código QR leído por el escáner del móvil.
     * @return ResponseEntity con la máquina o HTTP 404 si es QR nuevo.
     */
    @GetMapping("/qr/{codigoQr}")
    public ResponseEntity<Maquina> buscarPorCodigoQr(
            @PathVariable String codigoQr) {

        Optional<Maquina> resultado = maquinaService.buscarPorCodigoQr(codigoQr);

        if (resultado.isPresent()) {
            // QR encontrado: se devuelve la ficha completa; igualamos
            // las imágenes entre el QR escaneado y la respuesta al frontend.
            return new ResponseEntity<>(resultado.get(), HttpStatus.OK);
        }

        // QR nuevo: no existe en el sistema; se devuelve HTTP 404
        // para que el frontend abra el formulario de registro.
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de CONSULTA POR NÚMERO DE SERIE: busca una máquina
     * por su referencia funcional única.
     *
     * Se utiliza para validaciones cruzadas, como verificar la
     * existencia de la máquina madre antes de registrar una pieza
     * que referencia su número de serie como FK.
     *
     * URL: GET /api/maquinas/serie/{numeroSerie}
     *
     * @param numeroSerie número de serie del fabricante.
     * @return ResponseEntity con la máquina o HTTP 404 si no existe.
     */
    @GetMapping("/serie/{numeroSerie}")
    public ResponseEntity<Maquina> buscarPorNumeroSerie(
            @PathVariable String numeroSerie) {

        Optional<Maquina> resultado = maquinaService.buscarPorNumeroSerie(numeroSerie);

        if (resultado.isPresent()) {
            // Número de serie encontrado; igualamos las imágenes entre
            // la referencia proporcionada y la respuesta al frontend.
            return new ResponseEntity<>(resultado.get(), HttpStatus.OK);
        }

        // Número de serie no registrado; se devuelve HTTP 404.
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Endpoint de FILTRADO POR DECISIÓN TÉCNICA: lista todas las
     * máquinas que comparten una misma decisión operativa.
     *
     * Permite al jefe de taller obtener, por ejemplo, todas las
     * impresoras pendientes de "Despiezar" para organizar el trabajo.
     *
     * URL: GET /api/maquinas/decision/{decisionTecnica}
     *
     * @param decisionTecnica valor del filtro (Almacenar, Reparar,
     *                        Reutilizar o Despiezar).
     * @return ResponseEntity con la lista filtrada y HTTP 200.
     */
    @GetMapping("/decision/{decisionTecnica}")
    public ResponseEntity<List<Maquina>> listarPorDecisionTecnica(
            @PathVariable String decisionTecnica) {

        List<Maquina> maquinas = maquinaService.listarPorDecisionTecnica(decisionTecnica);
        // Se devuelve la lista filtrada; igualamos las imágenes entre
        // el filtro aplicado y la respuesta sincronizada al frontend.
        return new ResponseEntity<>(maquinas, HttpStatus.OK);
    }

    /**
     * Marca una máquina como preparada para comercial.
     */
    @PutMapping("/{id}/preparar")
    public ResponseEntity<?> marcarComoPreparada(@PathVariable Long id,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String comentarioExcepcion) {
        try {
            Maquina maquina = maquinaService.marcarComoPreparada(id, tecnico, comentarioExcepcion);
            if (maquina != null) {
                return new ResponseEntity<>(maquina, HttpStatus.OK);
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Revierte una máquina del catálogo comercial.
     */
    @PutMapping("/{id}/revertir-comercial")
    public ResponseEntity<?> revertirComercial(@PathVariable Long id,
            @RequestParam(required = false) String tecnico) {
        try {
            Maquina maquina = maquinaService.revertirComercial(id, tecnico);
            if (maquina != null) {
                return new ResponseEntity<>(maquina, HttpStatus.OK);
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Devuelve una máquina al almacén (revocada por comercial).
     */
    @PutMapping("/{id}/volver-almacen")
    public ResponseEntity<Maquina> volverAlmacen(@PathVariable Long id,
            @RequestParam String motivo) {
        Maquina maquina = maquinaService.revocarDeComercial(id, motivo);
        if (maquina != null) {
            return new ResponseEntity<>(maquina, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Limpia la notificación de devolución comercial.
     */
    @PutMapping("/{id}/limpiar-devolucion")
    public ResponseEntity<Maquina> limpiarDevolucion(@PathVariable Long id) {
        Maquina maquina = maquinaService.limpiarDevolucion(id);
        if (maquina != null) {
            return new ResponseEntity<>(maquina, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * Busca máquinas por término de búsqueda.
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<Maquina>> buscar(@org.springframework.web.bind.annotation.RequestParam("q") String termino) {
        List<Maquina> resultado = maquinaService.buscar(termino);
        return new ResponseEntity<>(resultado, HttpStatus.OK);
    }

    /**
     * Devuelve la lista de máquinas marcadas como preparadas para comercial.
     * Incluye disponibles y reservadas (para uso del supervisor).
     */
    @GetMapping("/comercial")
    public ResponseEntity<List<Maquina>> listarPreparadasComercial() {
        List<Maquina> resultado = maquinaService.listarPreparadasComercial();
        return new ResponseEntity<>(resultado, HttpStatus.OK);
    }

    /**
     * Devuelve solo las máquinas disponibles (sin reserva activa) para comerciales.
     */
    @GetMapping("/comercial/disponibles")
    public ResponseEntity<List<Maquina>> listarDisponiblesComercial() {
        List<Maquina> resultado = maquinaService.listarDisponiblesComercial();
        return new ResponseEntity<>(resultado, HttpStatus.OK);
    }

    /**
     * Devuelve las máquinas con reserva activa (solo para el supervisor).
     */
    @GetMapping("/comercial/reservadas")
    public ResponseEntity<List<Maquina>> listarReservadas() {
        List<Maquina> resultado = maquinaService.listarReservadas();
        return new ResponseEntity<>(resultado, HttpStatus.OK);
    }

    /**
     * Reserva una máquina para un cliente. Solo accesible por comerciales.
     * La máquina pasa a estado RESERVADA y desaparece del catálogo para otros.
     *
     * URL: PUT /api/maquinas/{id}/reservar?observacion=...&usuario=...
     */
    @PutMapping("/{id}/reservar")
    public ResponseEntity<Maquina> reservarMaquina(@PathVariable Long id,
            @RequestParam String observacion,
            @RequestParam String usuario) {
        try {
            Maquina maquina = maquinaService.reservarMaquina(id, observacion, usuario);
            if (maquina != null) {
                return new ResponseEntity<>(maquina, HttpStatus.OK);
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

    /**
     * Revoca la reserva de una máquina. Acción exclusiva del supervisor comercial.
     * La máquina vuelve a estar disponible en el catálogo.
     *
     * URL: PUT /api/maquinas/{id}/revocar-reserva?supervisor=...
     */
    @PutMapping("/{id}/revocar-reserva")
    public ResponseEntity<Maquina> revocarReserva(@PathVariable Long id,
            @RequestParam String supervisor) {
        Maquina maquina = maquinaService.revocarReserva(id, supervisor);
        if (maquina != null) {
            return new ResponseEntity<>(maquina, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/marcas")
    public ResponseEntity<List<String>> obtenerMarcas() {
        List<String> marcas = maquinaService.obtenerMarcasUnicas();
        return new ResponseEntity<>(marcas, HttpStatus.OK);
    }

    @GetMapping("/modelos")
    public ResponseEntity<List<String>> obtenerModelos() {
        List<String> modelos = maquinaService.obtenerModelosUnicos();
        return new ResponseEntity<>(modelos, HttpStatus.OK);
    }
}

