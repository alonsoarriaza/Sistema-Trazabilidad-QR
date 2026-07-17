package com.techcorp.almacen.proyecto.AlonsoFeria.servicio;

import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Pieza;
import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.PiezaRepository;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.MaquinaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * Capa de servicio que encapsula toda la lógica de negocio asociada a la
 * entidad Pieza (componentes internos de impresoras).
 *
 * Proporciona las operaciones CRUD completas (alta, lectura, actualización
 * y eliminación) junto con la lógica específica del taller de techcorp:
 *
 *  - Búsqueda por QR de pieza.
 *  - techcorp inversa: listar piezas por máquina de origen.
 *  - Filtrado por nivel de estado fungible (1, 2 o 3).
 *  - Filtrado por tipo de componente.
 *  - Regla de bloqueo por nivel de estado en movimientos de salida
 *    o instalación (lógica de negocio clave del taller).
 *
 * La regla de bloqueo por nivel de estado funciona así:
 *
 *   Nivel 1 → Buen estado aparente. Pieza prioritaria para reutilización.
 *             Se autoriza el movimiento de forma directa.
 *
 *   Nivel 2 → Estado aceptable. Pieza válida como apoyo, pendiente de
 *             revisión si procede. Se autoriza con una advertencia
 *             informativa que se devuelve al frontend.
 *
 *   Nivel 3 → Estado dudoso o desgaste visible. Se debe revisar antes
 *             de guardar o valorar descarte. El movimiento queda
 *             bloqueado y el sistema devuelve un mensaje de rechazo.
 *
 * Cada método que compara, actualiza o devuelve datos sincronizados con el
 * frontend incluye el comentario "igualamos las imágenes" para señalizar
 * esos puntos de sincronización.
 */
@Service
public class PiezaService {

    /**
     * Repositorio inyectado por Spring para el acceso a la tabla "piezas".
     * Se utiliza @Autowired para que el contenedor IoC resuelva la dependencia
     * de forma automática en el arranque de la aplicación.
     */
    @Autowired
    private PiezaRepository piezaRepository;

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private HistorialService historialService;

    // ═══════════════════════════════════════════════════════════════
    //  OPERACIONES CRUD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Registra una nueva pieza en el sistema (alta).
     *
     * Persiste todos los campos de la Plantilla 10.2 en la base de datos
     * y devuelve la entidad guardada con su ID auto-generado.
     * Si no se proporciona fecha de alta, se asigna la fecha actual
     * como valor por defecto.
     *
     * @param pieza entidad con todos los datos del formulario de registro.
     * @return la pieza persistida con su ID asignado.
     */
    public Pieza guardarPieza(Pieza pieza, String tecnico) {
        boolean esNueva = (pieza.getId() == null);
        if (esNueva && pieza.getNumeroSerieMaquinaOrigen() != null && !pieza.getNumeroSerieMaquinaOrigen().trim().isEmpty()) {
            Optional<Maquina> maquinaOpt = maquinaRepository.findByNumeroSerieIgnoreCase(pieza.getNumeroSerieMaquinaOrigen());
            if (maquinaOpt.isPresent() && Boolean.TRUE.equals(maquinaOpt.get().getPreparadaComercial())) {
                throw new IllegalStateException("No se puede extraer una pieza de una máquina que está en estado comercial.");
            }
        }

        if (pieza.getFechaAlta() == null) {
            pieza.setFechaAlta(LocalDate.now());
        }

        if (pieza.getEstadoPieza() == null) {
            pieza.setEstadoPieza("En almacén");
        }

        // Si es una pieza nueva, primero guardamos para obtener el ID y generar el QR
        boolean generarQr = esNueva && (pieza.getCodigoQrPieza() == null || pieza.getCodigoQrPieza().trim().isEmpty());

        Pieza guardada = piezaRepository.save(pieza);

        if (generarQr) {
            guardada.setCodigoQrPieza("PZ-" + guardada.getId());
            guardada = piezaRepository.save(guardada);
        }

        if (esNueva) {
            String desc = "Dada de alta la pieza " + guardada.getTipoPieza() + " (QR: " + guardada.getCodigoQrPieza() + ")";
            String accion = "ALTA";
            if ("Instalada".equals(guardada.getEstadoPieza())) {
                accion = "INSTALACION";
                desc = "Pieza instalada directamente";
            } else if ("Para reparación".equals(guardada.getEstadoPieza())) {
                accion = "EXTRACCION";
                desc = "Extraída para reparación";
            } else if ("Para cliente".equals(guardada.getEstadoPieza())) {
                accion = "EXTRACCION";
                desc = "Extraída para cliente: " + guardada.getDestinoCliente();
            }
            historialService.registrarMovimiento("PIEZA", guardada.getId(), accion, desc, tecnico);

            // Registrar también en el historial de la máquina madre si existe
            if (guardada.getNumeroSerieMaquinaOrigen() != null && !guardada.getNumeroSerieMaquinaOrigen().trim().isEmpty()) {
                Optional<Maquina> maqOp = maquinaRepository.findByNumeroSerie(guardada.getNumeroSerieMaquinaOrigen());
                if (maqOp.isPresent()) {
                    Maquina maquina = maqOp.get();
                    historialService.registrarMovimiento("MAQUINA", maquina.getId(), "EXTRACCION_PIEZA", 
                            "Se ha extraído la pieza: " + guardada.getTipoPieza() + " (QR: " + guardada.getCodigoQrPieza() + ")", tecnico);
                }
            }
        } else {
            historialService.registrarMovimiento("PIEZA", guardada.getId(), "EDICION", "Edición de datos de la pieza", tecnico);
        }

        return guardada;
    }

    /**
     * Recupera la lista completa de piezas registradas en el sistema.
     *
     * Devuelve todas las fichas de componentes almacenados para su
     * visualización en el listado general del inventario.
     *
     * @return lista con todas las piezas existentes.
     */
    public List<Pieza> listarTodas() {
        // Se recuperan todos los registros; igualamos las imágenes
        // entre la base de datos y la respuesta al frontend.
        return piezaRepository.findAll();
    }

    /**
     * Busca una pieza por su identificador técnico interno (ID).
     *
     * Se utiliza cuando se necesita acceder a la ficha de un componente
     * específico a través de la clave primaria auto-generada.
     *
     * @param id identificador numérico de la pieza.
     * @return Optional con la pieza encontrada o vacío si no existe.
     */
    public Optional<Pieza> buscarPorId(Long id) {
        // Se consulta por clave primaria; igualamos las imágenes
        // entre el ID solicitado y el registro almacenado.
        return piezaRepository.findById(id);
    }

    /**
     * Actualiza todos los campos de una pieza ya registrada.
     *
     * Recibe el ID de la pieza a modificar y un objeto con los datos
     * nuevos. Si la pieza existe, se actualizan absolutamente todos
     * los campos de la Plantilla 10.2; si no existe, se devuelve null
     * para que el controlador responda con un 404.
     *
     * @param id             identificador de la pieza a actualizar.
     * @param datosNuevos    entidad con los valores actualizados.
     * @return la pieza actualizada o null si no se encontró.
     */
    public Pieza actualizarPieza(Long id, Pieza datosNuevos, String tecnico) {
        Optional<Pieza> existente = piezaRepository.findById(id);

        if (existente.isPresent()) {
            Pieza pieza = existente.get();
            String descripcionCambios = obtenerCambiosPieza(pieza, datosNuevos);

            // Se transfieren todos los campos de la Plantilla 10.2
            // desde el objeto recibido hacia la entidad persistida;
            // igualamos las imágenes entre el formulario de edición
            // y el registro en base de datos campo a campo.
            pieza.setCodigoQrPieza(datosNuevos.getCodigoQrPieza());
            pieza.setTipoPieza(datosNuevos.getTipoPieza());
            pieza.setReferencia(datosNuevos.getReferencia());
            pieza.setMarcaModeloCompatible(datosNuevos.getMarcaModeloCompatible());
            pieza.setNumeroSerieMaquinaOrigen(datosNuevos.getNumeroSerieMaquinaOrigen());
            pieza.setNivelEstado(datosNuevos.getNivelEstado());
            pieza.setProcedenciaEstadoMaquina(datosNuevos.getProcedenciaEstadoMaquina());
            pieza.setCodigoAveriaMaquina(datosNuevos.getCodigoAveriaMaquina());
            pieza.setRelacionAveriaPieza(datosNuevos.getRelacionAveriaPieza());
            pieza.setUbicacionFisica(datosNuevos.getUbicacionFisica());
            pieza.setFechaAlta(datosNuevos.getFechaAlta());
            pieza.setFechaUsoBaja(datosNuevos.getFechaUsoBaja());
            pieza.setObservaciones(datosNuevos.getObservaciones());
            
            String estadoAnterior = pieza.getEstadoPieza();
            pieza.setEstadoPieza(datosNuevos.getEstadoPieza());
            pieza.setDestinoCliente(datosNuevos.getDestinoCliente());

            Pieza guardada = piezaRepository.save(pieza);
            
            if (estadoAnterior == null || !estadoAnterior.equals(guardada.getEstadoPieza())) {
                String desc = "Estado de pieza cambiado a: " + guardada.getEstadoPieza();
                String accion = "EDICION";
                
                if ("Para cliente".equals(guardada.getEstadoPieza())) {
                    accion = "SALIDA_CLIENTE";
                    desc = "Salida de pieza para cliente: " + (guardada.getDestinoCliente() != null ? guardada.getDestinoCliente() : "No especificado") 
                         + " (Observaciones: " + (guardada.getObservaciones() != null ? guardada.getObservaciones() : "Sin observaciones") + ")";
                } else if ("En almacén".equals(guardada.getEstadoPieza())) {
                    accion = "ENTRADA_ALMACEN";
                    String descNivel = guardada.getNivelEstado() == 1 ? "Buen estado / Óptimo" : (guardada.getNivelEstado() == 2 ? "Aceptable / Funcional" : "Dudoso / Requiere evaluación");
                    desc = "Re-entrada de la pieza al almacén. Clasificación de estado: Nivel " 
                         + guardada.getNivelEstado() + " — " + descNivel;
                } else if ("Instalada".equals(guardada.getEstadoPieza())) {
                    accion = "INSTALACION";
                } else if ("Para reparación".equals(guardada.getEstadoPieza())) {
                    accion = "EXTRACCION";
                    desc = "Extraída para reparación. Ubicación actual: taller de reparación.";
                }
                historialService.registrarMovimiento("PIEZA", guardada.getId(), accion, desc, tecnico);
            } else {
                historialService.registrarMovimiento("PIEZA", guardada.getId(), "EDICION", descripcionCambios, tecnico);
            }

            return guardada;
        }

        // La pieza no existe: se devuelve null para señalizar al
        // controlador que debe responder con HTTP 404.
        return null;
    }

    /**
     * Elimina una pieza del sistema por su ID.
     *
     * Antes de eliminar se verifica que el registro exista para evitar
     * excepciones de integridad. Devuelve true si se eliminó con éxito
     * y false si la pieza no fue encontrada.
     *
     * @param id identificador de la pieza a eliminar.
     * @return true si se eliminó correctamente, false si no existía.
     */
    /**
     * Elimina una pieza del sistema por su ID, registrando el motivo e identificando al técnico.
     */
    public boolean eliminarPieza(Long id, String motivo, String tecnico) {
        Optional<Pieza> op = piezaRepository.findById(id);
        if (op.isPresent()) {
            Pieza pieza = op.get();
            historialService.registrarMovimiento("PIEZA", id, "BAJA", 
                    "Pieza eliminada. Motivo: " + motivo, tecnico);

            // Registrar en el historial de la máquina madre si existe
            if (pieza.getNumeroSerieMaquinaOrigen() != null && !pieza.getNumeroSerieMaquinaOrigen().trim().isEmpty()) {
                Optional<Maquina> maqOp = maquinaRepository.findByNumeroSerie(pieza.getNumeroSerieMaquinaOrigen());
                if (maqOp.isPresent()) {
                    Maquina maquina = maqOp.get();
                    historialService.registrarMovimiento("MAQUINA", maquina.getId(), "BAJA_PIEZA", 
                            "Pieza eliminada de esta máquina: " + pieza.getTipoPieza() + " (QR: " + pieza.getCodigoQrPieza() + "). Motivo: " + motivo, tecnico);
                }
            }
            piezaRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  BÚSQUEDAS ESPECÍFICAS DEL TALLER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Busca una pieza por su código QR individual.
     *
     * Se utiliza cuando el técnico escanea la etiqueta QR adherida
     * al componente para consultar su ficha.
     *
     * @param codigoQrPieza código QR escaneado.
     * @return Optional con la pieza o vacío si no existe.
     */
    public Optional<Pieza> buscarPorCodigoQr(String codigoQrPieza) {
        // Se busca la coincidencia exacta del QR; igualamos las imágenes
        // entre el código escaneado y el registro en base de datos.
        return piezaRepository.findByCodigoQrPiezaIgnoreCase(codigoQrPieza);
    }

    /**
     * Lista todas las piezas extraídas de una máquina concreta.
     *
     * Proporciona techcorp inversa completa: dado un número de serie,
     * se obtienen todos los componentes que se han desmontado de esa
     * impresora. También se utiliza para detectar "Máquinas Zombi"
     * (chasis a los que no les quedan piezas útiles).
     *
     * @param numeroSerieMaquinaOrigen número de serie de la máquina madre.
     * @return lista de piezas asociadas a esa máquina.
     */
    public List<Pieza> listarPorMaquinaOrigen(String numeroSerieMaquinaOrigen) {
        // Se filtra por FK lógica; igualamos las imágenes entre
        // el número de serie proporcionado y las piezas vinculadas.
        return piezaRepository.findByNumeroSerieMaquinaOrigen(numeroSerieMaquinaOrigen);
    }

    /**
     * Lista todas las piezas con un nivel de estado fungible concreto.
     *
     * Permite al técnico localizar rápidamente componentes por su
     * calidad estimada:
     *   Nivel 1 → Buen estado (prioritarias).
     *   Nivel 2 → Aceptable (apoyo/revisión).
     *   Nivel 3 → Dudoso (descarte o revisión profunda).
     *
     * @param nivelEstado nivel de estado (1, 2 o 3).
     * @return lista de piezas con ese nivel.
     */
    public List<Pieza> listarPorNivelEstado(Integer nivelEstado) {
        // Se filtra por nivel de estado fungible; igualamos las imágenes
        // entre el nivel solicitado y los resultados obtenidos.
        return piezaRepository.findByNivelEstado(nivelEstado);
    }

    /**
     * Lista todas las piezas de un tipo concreto (ej. "Fusor", "Rodillo").
     *
     * Facilita la búsqueda transversal cuando el técnico necesita un
     * componente específico sin importar de qué máquina provenga.
     *
     * @param tipoPieza tipo del componente.
     * @return lista de piezas de ese tipo.
     */
    public List<Pieza> listarPorTipoPieza(String tipoPieza) {
        // Se filtra por tipo de componente; igualamos las imágenes
        // entre el tipo solicitado y las piezas encontradas.
        return piezaRepository.findByTipoPieza(tipoPieza);
    }

    // ═══════════════════════════════════════════════════════════════
    //  REGLA DE BLOQUEO POR NIVEL DE ESTADO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Evalúa si una pieza puede ser movida o instalada en función de
     * su nivel de estado fungible. Implementa la regla de bloqueo
     * definida por el taller de techcorp.
     *
     * La lógica de decisión se basa en la comparación directa del
     * nivel de estado de la pieza:
     *
     *   Nivel 1 → Buen estado aparente. Pieza prioritaria para
     *             reutilización. Se autoriza el movimiento directamente
     *             y se devuelve un mensaje de confirmación.
     *
     *   Nivel 2 → Estado aceptable. Pieza válida como apoyo, pendiente
     *             de revisión si procede. Se autoriza el movimiento
     *             pero se adjunta una advertencia informativa para que
     *             el técnico la tenga en cuenta.
     *
     *   Nivel 3 → Estado dudoso o desgaste visible. Se debe revisar la
     *             pieza antes de guardar o valorar su descarte. El
     *             movimiento queda BLOQUEADO y se devuelve un mensaje
     *             de rechazo claro.
     *
     * @param idPieza identificador de la pieza que se pretende mover.
     * @return mensaje descriptivo con el resultado de la evaluación.
     * @throws RuntimeException si la pieza no existe en el sistema.
     */
    public String evaluarMovimientoPorNivel(Long idPieza) {
        Optional<Pieza> resultado = piezaRepository.findById(idPieza);

        if (resultado.isEmpty()) {
            throw new RuntimeException(
                "La pieza con ID " + idPieza + " no existe en el sistema. "
                + "No se puede evaluar el movimiento."
            );
        }

        Pieza pieza = resultado.get();
        Integer nivel = pieza.getNivelEstado();

        // Se compara el nivel de estado de la pieza contra las reglas
        // del taller para determinar si el movimiento se autoriza,
        // se advierte o se bloquea; igualamos las imágenes entre
        // el nivel almacenado y la política de bloqueo vigente.

        if (nivel == null) {
            return "BLOQUEADO: La pieza '" + pieza.getTipoPieza()
                 + "' (QR: " + pieza.getCodigoQrPieza() + ") no tiene "
                 + "un nivel de estado asignado. Se requiere clasificación "
                 + "antes de autorizar cualquier movimiento.";
        }

        switch (nivel) {
            case 1:
                // Nivel 1: Buen estado aparente.
                // La pieza es prioritaria para reutilización.
                // Se autoriza el movimiento de forma directa;
                // igualamos las imágenes entre la autorización
                // y la respuesta sincronizada al frontend.
                return "AUTORIZADO: La pieza '" + pieza.getTipoPieza()
                     + "' (QR: " + pieza.getCodigoQrPieza() + ") se "
                     + "encuentra en Nivel 1 (Buen estado aparente). "
                     + "Es prioritaria para reutilización. Movimiento "
                     + "autorizado sin restricciones.";

            case 2:
                // Nivel 2: Estado aceptable.
                // La pieza es válida como apoyo, pendiente de revisión.
                // Se autoriza el movimiento con una advertencia;
                // igualamos las imágenes entre la advertencia generada
                // y la información que se muestra al técnico.
                return "ADVERTENCIA: La pieza '" + pieza.getTipoPieza()
                     + "' (QR: " + pieza.getCodigoQrPieza() + ") se "
                     + "encuentra en Nivel 2 (Estado aceptable). Es "
                     + "válida como apoyo pero puede requerir revisión "
                     + "posterior. Movimiento autorizado con precaución.";

            case 3:
                // Nivel 3: Estado dudoso o desgaste visible.
                // La pieza debe revisarse antes de guardar o valorar
                // su descarte. El movimiento queda BLOQUEADO;
                // igualamos las imágenes entre el bloqueo aplicado
                // y el mensaje de rechazo devuelto al frontend.
                return "BLOQUEADO: La pieza '" + pieza.getTipoPieza()
                     + "' (QR: " + pieza.getCodigoQrPieza() + ") se "
                     + "encuentra en Nivel 3 (Estado dudoso o desgaste "
                     + "visible). Se debe revisar antes de guardar o "
                     + "valorar su descarte. Movimiento NO autorizado.";

            default:
                // Nivel no reconocido: se bloquea por seguridad;
                // igualamos las imágenes entre el valor inesperado
                // y la respuesta de bloqueo preventivo.
                return "BLOQUEADO: La pieza '" + pieza.getTipoPieza()
                     + "' (QR: " + pieza.getCodigoQrPieza() + ") tiene "
                     + "un nivel de estado no reconocido (" + nivel + "). "
                     + "Se requiere reclasificación antes de autorizar "
                     + "cualquier movimiento.";
        }
    }

    /**
     * Busca piezas por tipo, referencia, marca/modelo compatible, código QR o ubicación física.
     */
    public List<Pieza> buscar(String termino) {
        List<Pieza> todas = piezaRepository.findAll();
        if (termino == null || termino.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        String[] tokens = termino.trim().toLowerCase().split("\\s+");
        List<Pieza> filtradas = new java.util.ArrayList<>();
        
        for (Pieza p : todas) {
            if (p.getCodigoQrPieza() == null || p.getCodigoQrPieza().trim().isEmpty()) {
                continue;
            }
            boolean coincideTodosLosTokens = true;
            for (String token : tokens) {
                boolean coincideToken = false;
                if (p.getTipoPieza() != null && p.getTipoPieza().toLowerCase().contains(token)) coincideToken = true;
                if (p.getReferencia() != null && p.getReferencia().toLowerCase().contains(token)) coincideToken = true;
                if (p.getMarcaModeloCompatible() != null && p.getMarcaModeloCompatible().toLowerCase().contains(token)) coincideToken = true;
                if (p.getCodigoQrPieza() != null && p.getCodigoQrPieza().toLowerCase().contains(token)) coincideToken = true;
                if (p.getUbicacionFisica() != null && p.getUbicacionFisica().toLowerCase().contains(token)) coincideToken = true;
                if (p.getNumeroSerieMaquinaOrigen() != null && p.getNumeroSerieMaquinaOrigen().toLowerCase().contains(token)) coincideToken = true;
                if (p.getEstadoPieza() != null && p.getEstadoPieza().toLowerCase().contains(token)) coincideToken = true;
                
                if (!coincideToken) {
                    coincideTodosLosTokens = false;
                    break;
                }
            }
            if (coincideTodosLosTokens) {
                filtradas.add(p);
            }
        }
        return filtradas;
    }

    private String obtenerCambiosPieza(Pieza vieja, Pieza nueva) {
        StringBuilder sb = new StringBuilder("Se modificó: ");
        boolean hayCambios = false;

        if (!Objects.equals(vieja.getCodigoQrPieza(), nueva.getCodigoQrPieza())) {
            sb.append("Código QR ('").append(vieja.getCodigoQrPieza()).append("' -> '").append(nueva.getCodigoQrPieza()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getTipoPieza(), nueva.getTipoPieza())) {
            sb.append("Tipo ('").append(vieja.getTipoPieza()).append("' -> '").append(nueva.getTipoPieza()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getReferencia(), nueva.getReferencia())) {
            sb.append("Referencia ('").append(vieja.getReferencia()).append("' -> '").append(nueva.getReferencia()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getMarcaModeloCompatible(), nueva.getMarcaModeloCompatible())) {
            sb.append("Modelo compatible ('").append(vieja.getMarcaModeloCompatible()).append("' -> '").append(nueva.getMarcaModeloCompatible()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getNumeroSerieMaquinaOrigen(), nueva.getNumeroSerieMaquinaOrigen())) {
            sb.append("Máquina origen ('").append(vieja.getNumeroSerieMaquinaOrigen()).append("' -> '").append(nueva.getNumeroSerieMaquinaOrigen()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getNivelEstado(), nueva.getNivelEstado())) {
            sb.append("Nivel estado (").append(vieja.getNivelEstado()).append(" -> ").append(nueva.getNivelEstado()).append("), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getEstadoPieza(), nueva.getEstadoPieza())) {
            sb.append("Estado pieza ('").append(vieja.getEstadoPieza()).append("' -> '").append(nueva.getEstadoPieza()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getDestinoCliente(), nueva.getDestinoCliente())) {
            sb.append("Destino cliente ('").append(vieja.getDestinoCliente()).append("' -> '").append(nueva.getDestinoCliente()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getProcedenciaEstadoMaquina(), nueva.getProcedenciaEstadoMaquina())) {
            sb.append("Procedencia máquina ('").append(vieja.getProcedenciaEstadoMaquina()).append("' -> '").append(nueva.getProcedenciaEstadoMaquina()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getCodigoAveriaMaquina(), nueva.getCodigoAveriaMaquina())) {
            sb.append("Código avería ('").append(vieja.getCodigoAveriaMaquina()).append("' -> '").append(nueva.getCodigoAveriaMaquina()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getRelacionAveriaPieza(), nueva.getRelacionAveriaPieza())) {
            sb.append("Relación avería ('").append(vieja.getRelacionAveriaPieza()).append("' -> '").append(nueva.getRelacionAveriaPieza()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getUbicacionFisica(), nueva.getUbicacionFisica())) {
            sb.append("Ubicación ('").append(vieja.getUbicacionFisica()).append("' -> '").append(nueva.getUbicacionFisica()).append("'), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getFechaAlta(), nueva.getFechaAlta())) {
            sb.append("Fecha alta (").append(vieja.getFechaAlta()).append(" -> ").append(nueva.getFechaAlta()).append("), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getFechaUsoBaja(), nueva.getFechaUsoBaja())) {
            sb.append("Fecha baja (").append(vieja.getFechaUsoBaja()).append(" -> ").append(nueva.getFechaUsoBaja()).append("), ");
            hayCambios = true;
        }
        if (!Objects.equals(vieja.getObservaciones(), nueva.getObservaciones())) {
            sb.append("Observaciones ('").append(vieja.getObservaciones()).append("' -> '").append(nueva.getObservaciones()).append("'), ");
            hayCambios = true;
        }

        if (hayCambios) {
            String res = sb.toString();
            return res.substring(0, res.length() - 2);
        } else {
            return "Edición de datos de la pieza (sin cambios de valor)";
        }
    }
}



