package com.coanda.almacen.proyecto.AlonsoFeria.servicio;

import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.coanda.almacen.proyecto.AlonsoFeria.repositorio.MaquinaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import com.coanda.almacen.proyecto.AlonsoFeria.repositorio.PiezaRepository;
import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Pieza;
import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Toner;
import java.util.stream.Collectors;


/**
 * Capa de servicio que encapsula toda la lógica de negocio asociada a la
 * entidad Maquina (impresoras).
 *
 * Proporciona las operaciones CRUD completas (alta, lectura, actualización
 * y eliminación) así como métodos de búsqueda específicos para cubrir los
 * flujos operativos del taller de coanda:
 *
 * - Bifurcación por escaneo QR (buscar por código QR).
 * - Consulta de ficha por número de serie.
 * - Filtrado por decisión técnica (Almacenar, Reparar, Reutilizar, Despiezar).
 *
 * Cada método que compara, actualiza o devuelve datos sincronizados con el
 * frontend incluye el comentario "igualamos las imágenes" para señalizar
 * esos puntos de sincronización.
 */
@Service
public class MaquinaService {

    /**
     * Repositorio inyectado por Spring para el acceso a la tabla "maquinas".
     * Se utiliza @Autowired para que el contenedor IoC resuelva la dependencia
     * de forma automática en el arranque de la aplicación.
     */
    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private HistorialService historialService;

    @Autowired
    private PiezaRepository piezaRepository;

    @Autowired
    private TonerService tonerService;


    // ═══════════════════════════════════════════════════════════════
    // OPERACIONES CRUD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Registra una nueva máquina en el sistema (alta).
     *
     * Persiste todos los campos de la Plantilla 10.1 en la base de datos
     * y devuelve la entidad guardada con su ID auto-generado.
     *
     * @param maquina entidad con todos los datos del formulario de registro.
     * @return la máquina persistida con su ID asignado.
     */
    public Maquina guardarMaquina(Maquina maquina, String tecnico) {
        boolean esNueva = (maquina.getId() == null);
        boolean generarQr = esNueva && (maquina.getCodigoQr() == null || maquina.getCodigoQr().trim().isEmpty());

        if (generarQr) {
            maquina.setCodigoQr("TEMP-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }

        Maquina guardada = maquinaRepository.save(maquina);

        if (generarQr) {
            guardada.setCodigoQr("MQ-" + guardada.getId());
            guardada = maquinaRepository.save(guardada);
        }

        if (esNueva) {
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "ALTA",
                    "Registro inicial de la máquina " + guardada.getMarca() + " " + guardada.getModelo() + " (S/N: "
                            + guardada.getNumeroSerie() + ")",
                    tecnico);
            
            // Crear automáticamente el tóner inicial instalado en la máquina
            Toner toner = new Toner();
            if (maquina.getModeloToner() != null && !maquina.getModeloToner().trim().isEmpty()) {
                toner.setModelo(maquina.getModeloToner().trim());
            } else {
                toner.setModelo(guardada.getModelo() + " (Tóner)");
            }
            toner.setNivelToner(guardada.getNivelToner() != null && !guardada.getNivelToner().isEmpty() ? guardada.getNivelToner() : "100%");
            toner.setEstado("En máquina");
            toner.setMaquinaOrigenSerie(guardada.getNumeroSerie());
            toner.setUbicacionFisica("En máquina");
            toner.setFechaRegistro(java.time.LocalDate.now());
            tonerService.guardarToner(toner, tecnico);
        } else {
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "EDICION",
                    "Edición de datos de la máquina", tecnico);
        }
        return guardada;

    }

    /**
     * Recupera la lista completa de máquinas registradas en el sistema.
     *
     * Devuelve todas las fichas almacenadas para su visualización
     * en el listado general del panel de control.
     *
     * @return lista con todas las máquinas existentes.
     */
    public List<Maquina> listarTodas() {
        // Se recuperan todos los registros; igualamos las imágenes
        // entre la base de datos y la respuesta al frontend.
        return maquinaRepository.findAll();
    }

    /**
     * Busca una máquina por su identificador técnico interno (ID).
     *
     * Se utiliza cuando se necesita acceder a una ficha específica
     * a través de la clave primaria auto-generada.
     *
     * @param id identificador numérico de la máquina.
     * @return Optional con la máquina encontrada o vacío si no existe.
     */
    public Optional<Maquina> buscarPorId(Long id) {
        // Se consulta por clave primaria; igualamos las imágenes
        // entre el ID solicitado y el registro almacenado.
        return maquinaRepository.findById(id);
    }

    /**
     * Actualiza todos los campos de una máquina ya registrada.
     *
     * Recibe el ID de la máquina a modificar y un objeto con los datos
     * nuevos. Si la máquina existe, se actualizan absolutamente todos
     * los campos de la Plantilla 10.1; si no existe, se devuelve null
     * para que el controlador responda con un 404.
     *
     * @param id          identificador de la máquina a actualizar.
     * @param datosNuevos entidad con los valores actualizados.
     * @return la máquina actualizada o null si no se encontró.
     */
    public Maquina actualizarMaquina(Long id, Maquina datosNuevos, String tecnico) {
        Optional<Maquina> existente = maquinaRepository.findById(id);

        if (existente.isPresent()) {
            Maquina maquina = existente.get();

            if (Boolean.TRUE.equals(maquina.getPreparadaComercial())) {
                throw new IllegalStateException("No se puede editar una máquina que está asignada a Comercial.");
            }

            String descripcionCambios = obtenerCambiosMaquina(maquina, datosNuevos);

            // Se transfieren todos los campos de la Plantilla 10.1
            // desde el objeto recibido hacia la entidad persistida;
            // igualamos las imágenes entre el formulario de edición
            // y el registro en base de datos campo a campo.
            maquina.setCodigoQr(datosNuevos.getCodigoQr());
            maquina.setMarca(datosNuevos.getMarca());
            maquina.setModelo(datosNuevos.getModelo());
            maquina.setNumeroSerie(datosNuevos.getNumeroSerie());
            maquina.setNumeroCopias(datosNuevos.getNumeroCopias());
            maquina.setClienteProcedencia(datosNuevos.getClienteProcedencia());
            maquina.setFechaEntrada(datosNuevos.getFechaEntrada());
            maquina.setEstadoFuncionamiento(datosNuevos.getEstadoFuncionamiento());
            maquina.setAveriasCodigos(datosNuevos.getAveriasCodigos());
            maquina.setDecisionTecnica(datosNuevos.getDecisionTecnica());
            maquina.setUbicacionFisica(datosNuevos.getUbicacionFisica());
            maquina.setObservaciones(datosNuevos.getObservaciones());
            maquina.setEstadoVisual(datosNuevos.getEstadoVisual());
            maquina.setNivelToner(datosNuevos.getNivelToner());
            maquina.setPreparadaComercial(datosNuevos.getPreparadaComercial());

            Maquina guardada = maquinaRepository.save(maquina);
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "EDICION",
                    descripcionCambios, tecnico);
            return guardada;
        }

        // La máquina no existe: se devuelve null para señalizar al
        // controlador que debe responder con HTTP 404.
        return null;
    }

    /**
     * Elimina una máquina del sistema por su ID, registrando el motivo e
     * identificando al técnico.
     */
    public boolean eliminarMaquina(Long id, String motivo, String tecnico) {
        if (maquinaRepository.existsById(id)) {
            historialService.registrarMovimiento("MAQUINA", id, "BAJA",
                    "Máquina eliminada. Motivo: " + motivo, tecnico);
            maquinaRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // BÚSQUEDAS ESPECÍFICAS DEL TALLER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Busca una máquina por su código QR.
     *
     * Este método es el corazón de la bifurcación del escaneo inicial:
     * el controlador lo invoca al recibir un QR leído por el móvil.
     * Si devuelve un resultado, se muestra la ficha de la máquina;
     * si devuelve vacío, se redirige al formulario de registro.
     *
     * @param codigoQr código QR escaneado.
     * @return Optional con la máquina o vacío si el QR es nuevo.
     */
    public Optional<Maquina> buscarPorCodigoQr(String codigoQr) {
        // Se busca la coincidencia exacta del QR; igualamos las imágenes
        // entre el código escaneado y el registro en base de datos.
        return maquinaRepository.findByCodigoQrIgnoreCase(codigoQr);
    }

    /**
     * Busca una máquina por su número de serie único.
     *
     * Se utiliza para validar la existencia de la máquina madre antes
     * de registrar una pieza que referencia su número de serie como FK.
     *
     * @param numeroSerie número de serie del fabricante.
     * @return Optional con la máquina o vacío si no existe.
     */
    public Optional<Maquina> buscarPorNumeroSerie(String numeroSerie) {
        // Se busca por la referencia funcional; igualamos las imágenes
        // entre el número de serie proporcionado y la tabla de máquinas.
        return maquinaRepository.findByNumeroSerieIgnoreCase(numeroSerie);
    }

    /**
     * Lista todas las máquinas que comparten una misma decisión técnica.
     *
     * Permite al jefe de taller filtrar, por ejemplo, todas las que están
     * pendientes de "Despiezar" para planificar el trabajo del día.
     *
     * @param decisionTecnica valor de la decisión (Almacenar, Reparar,
     *                        Reutilizar o Despiezar).
     * @return lista de máquinas filtradas por esa decisión.
     */
    public List<Maquina> listarPorDecisionTecnica(String decisionTecnica) {
        // Se filtra por decisión técnica; igualamos las imágenes
        // entre el filtro solicitado y los resultados obtenidos.
        return maquinaRepository.findByDecisionTecnicaIgnoreCase(decisionTecnica);
    }

    /**
     * Valida si una máquina cumple las condiciones para ser preparada para comercial.
     */
    public void validarPreparadaComercial(Maquina maquina) {
        StringBuilder errorMsg = new StringBuilder();
        
        if (!"Óptimo".equals(maquina.getEstadoVisual())) {
            errorMsg.append("El estado visual de la máquina no es Óptimo (es: ").append(maquina.getEstadoVisual() != null ? maquina.getEstadoVisual() : "Sin evaluar").append("). ");
        }
        
        List<Pieza> piezas = piezaRepository.findByNumeroSerieMaquinaOrigen(maquina.getNumeroSerie());
        List<String> piezasExtraidas = piezas.stream()
            .filter(p -> !"Instalada".equals(p.getEstadoPieza()))
            .map(Pieza::getTipoPieza)
            .collect(Collectors.toList());
            
        if (!piezasExtraidas.isEmpty()) {
            errorMsg.append("Faltan las siguientes piezas: ").append(String.join(", ", piezasExtraidas)).append(".");
        }
        
        if (errorMsg.length() > 0) {
            throw new IllegalArgumentException(errorMsg.toString());
        }
    }

    /**
     * Marca una máquina como preparada para comercial.
     */
    public Maquina marcarComoPreparada(Long id, String tecnico, String comentarioExcepcion) {
        Optional<Maquina> existente = maquinaRepository.findById(id);
        if (existente.isPresent()) {
            Maquina maquina = existente.get();
            
            if (comentarioExcepcion != null && !comentarioExcepcion.trim().isEmpty()) {
                maquina.setComentarioExcepcion(comentarioExcepcion.trim());
            } else {
                // Si no hay excepción, forzar la validación
                validarPreparadaComercial(maquina);
                // Limpiar cualquier excepción previa en caso de que ahora cumpla
                maquina.setComentarioExcepcion(null);
            }
            
            maquina.setPreparadaComercial(true);
            maquina.setTecnicoPreparadoComercial(tecnico);
            Maquina guardada = maquinaRepository.save(maquina);
            
            String descMovimiento = "Máquina marcada como lista para comercializar por " + (tecnico != null ? tecnico : "Técnico");
            if (maquina.getComentarioExcepcion() != null) {
                descMovimiento += " (Excepción: " + maquina.getComentarioExcepcion() + ")";
            }
            
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "PREPARADA_COMERCIAL",
                    descMovimiento, tecnico);
            return guardada;
        }
        return null;
    }

    /**
     * Revierte una máquina de comercial (la quita del catálogo comercial).
     */
    public Maquina revertirComercial(Long id, String tecnico) {
        throw new IllegalStateException("Un usuario técnico no puede retirar una máquina del estado COMERCIAL.");
    }

    /**
     * Revoca una máquina de comercial y la devuelve al almacén.
     */
    public Maquina revocarDeComercial(Long id, String motivo) {
        Optional<Maquina> existente = maquinaRepository.findById(id);
        if (existente.isPresent()) {
            Maquina maquina = existente.get();
            maquina.setPreparadaComercial(false);
            maquina.setTecnicoPreparadoComercial(null); // Limpiar firma de preparación
            maquina.setComentarioExcepcion(null); // Limpiar excepción al revocar
            maquina.setMotivoDevolucion(motivo); // Guardar motivo de devolución
            maquina.setUbicacionFisica("Revocada por Comercial (Se necesita ubicar)");
            Maquina guardada = maquinaRepository.save(maquina);
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "REVOCADA_COMERCIAL",
                    "Máquina devuelta al almacén (revocada por comercial). Motivo: " + motivo, "Comercial");
            return guardada;
        }
        return null;
    }

    /**
     * Limpia la notificación de devolución de una máquina.
     */
    public Maquina limpiarDevolucion(Long id) {
        Optional<Maquina> existente = maquinaRepository.findById(id);
        if (existente.isPresent()) {
            Maquina maquina = existente.get();
            maquina.setMotivoDevolucion(null);
            return maquinaRepository.save(maquina);
        }
        return null;
    }

    /**
     * Busca máquinas por marca, modelo, número de serie, código QR o ubicación física.
     */
    public List<Maquina> buscar(String termino) {
        return maquinaRepository
                .findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCaseOrNumeroSerieContainingIgnoreCaseOrCodigoQrContainingIgnoreCaseOrUbicacionFisicaContainingIgnoreCase(
                        termino, termino, termino, termino, termino);
    }

    /**
     * Lista todas las máquinas preparadas comercialmente.
     */
    public List<Maquina> listarPreparadasComercial() {
        return maquinaRepository.findByPreparadaComercialTrue();
    }

    /**
     * Lista las máquinas comerciales disponibles (sin reserva activa).
     */
    public List<Maquina> listarDisponiblesComercial() {
        return maquinaRepository.findByPreparadaComercialTrueAndEstadoVentaIsNull();
    }

    /**
     * Lista las máquinas comerciales con reserva activa.
     */
    public List<Maquina> listarReservadas() {
        return maquinaRepository.findByPreparadaComercialTrueAndEstadoVentaIsNotNull();
    }

    /**
     * Reserva una máquina para un cliente, registrando quién la reservó.
     * Solo se puede reservar si no hay ya una reserva activa.
     */
    public Maquina reservarMaquina(Long id, String observacion, String usuario) {
        Optional<Maquina> existente = maquinaRepository.findById(id);
        if (existente.isPresent()) {
            Maquina maquina = existente.get();
            if ("RESERVADA".equals(maquina.getEstadoVenta())) {
                throw new IllegalStateException("La máquina ya está reservada.");
            }
            maquina.setEstadoVenta("RESERVADA");
            maquina.setObservacionVenta(observacion);
            maquina.setComercialReserva(usuario);
            Maquina guardada = maquinaRepository.save(maquina);
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "RESERVA_COMERCIAL",
                    "Máquina reservada por " + usuario + ". Cliente/Observación: " + observacion, usuario);
            return guardada;
        }
        return null;
    }

    /**
     * Revoca la reserva de una máquina, devolviéndola al estado disponible.
     * Solo el supervisor puede ejecutar esta acción.
     */
    public Maquina revocarReserva(Long id, String supervisor) {
        Optional<Maquina> existente = maquinaRepository.findById(id);
        if (existente.isPresent()) {
            Maquina maquina = existente.get();
            String observacionAnterior = maquina.getObservacionVenta();
            String comercialAnterior = maquina.getComercialReserva();
            maquina.setEstadoVenta(null);
            maquina.setObservacionVenta(null);
            maquina.setComercialReserva(null);
            Maquina guardada = maquinaRepository.save(maquina);
            historialService.registrarMovimiento("MAQUINA", guardada.getId(), "REVOCACION_SUPERVISOR",
                    "Reserva revocada por supervisor " + supervisor +
                    ". Reserva original de: " + comercialAnterior + " — " + observacionAnterior, supervisor);
            return guardada;
        }
        return null;
    }

    private String obtenerCambiosMaquina(Maquina vieja, Maquina nueva) {
        StringBuilder sb = new StringBuilder("Se modificó: ");
        boolean hayCambios = false;

        if (!Objects.equals(vieja.getCodigoQr(), nueva.getCodigoQr())) {
            sb.append("Código QR ('").append(vieja.getCodigoQr()).append("' -> '").append(nueva.getCodigoQr())
                    .append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getMarca(), nueva.getMarca())) {
            sb.append("Marca ('").append(vieja.getMarca()).append("' -> '").append(nueva.getMarca()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getModelo(), nueva.getModelo())) {
            sb.append("Modelo ('").append(vieja.getModelo()).append("' -> '").append(nueva.getModelo()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getNumeroSerie(), nueva.getNumeroSerie())) {
            sb.append("S/N ('").append(vieja.getNumeroSerie()).append("' -> '").append(nueva.getNumeroSerie())
                    .append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getNumeroCopias(), nueva.getNumeroCopias())) {
            sb.append("Copias (").append(vieja.getNumeroCopias()).append(" -> ").append(nueva.getNumeroCopias())
                    .append("), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getClienteProcedencia(), nueva.getClienteProcedencia())) {
            sb.append("Cliente procedencia ('").append(vieja.getClienteProcedencia()).append("' -> '")
                    .append(nueva.getClienteProcedencia()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getFechaEntrada(), nueva.getFechaEntrada())) {
            sb.append("Fecha entrada (").append(vieja.getFechaEntrada()).append(" -> ").append(nueva.getFechaEntrada())
                    .append("), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getEstadoFuncionamiento(), nueva.getEstadoFuncionamiento())) {
            sb.append("Funcionamiento ('").append(vieja.getEstadoFuncionamiento()).append("' -> '")
                    .append(nueva.getEstadoFuncionamiento()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getAveriasCodigos(), nueva.getAveriasCodigos())) {
            sb.append("Códigos avería ('").append(vieja.getAveriasCodigos()).append("' -> '")
                    .append(nueva.getAveriasCodigos()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getDecisionTecnica(), nueva.getDecisionTecnica())) {
            sb.append("Decisión ('").append(vieja.getDecisionTecnica()).append("' -> '")
                    .append(nueva.getDecisionTecnica()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getEstadoVisual(), nueva.getEstadoVisual())) {
            sb.append("Estado visual ('").append(vieja.getEstadoVisual()).append("' -> '")
                    .append(nueva.getEstadoVisual()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getUbicacionFisica(), nueva.getUbicacionFisica())) {
            sb.append("Ubicación ('").append(vieja.getUbicacionFisica()).append("' -> '")
                    .append(nueva.getUbicacionFisica()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getObservaciones(), nueva.getObservaciones())) {
            sb.append("Observaciones ('").append(vieja.getObservaciones()).append("' -> '")
                    .append(nueva.getObservaciones()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getNivelToner(), nueva.getNivelToner())) {
            sb.append("Nivel Tóner ('").append(vieja.getNivelToner()).append("' -> '")
                    .append(nueva.getNivelToner()).append("'), ");
            hayCambios = true;
        }

        if (hayCambios) {
            String res = sb.toString();
            return res.substring(0, res.length() - 2);
        } else {
            return "Edición de datos de la máquina (sin cambios de valor)";
        }
    }

    public List<String> obtenerMarcasUnicas() {
        return maquinaRepository.findDistinctMarcas();
    }

    public List<String> obtenerModelosUnicos() {
        return maquinaRepository.findDistinctModelos();
    }
}


