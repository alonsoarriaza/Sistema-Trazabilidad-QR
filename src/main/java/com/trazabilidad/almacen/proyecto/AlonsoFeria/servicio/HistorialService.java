package com.trazabilidad.almacen.proyecto.AlonsoFeria.servicio;

import com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo.HistorialMovimiento;
import com.trazabilidad.almacen.proyecto.AlonsoFeria.repositorio.HistorialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HistorialService {

    @Autowired
    private HistorialRepository historialRepository;

    /**
     * Registra un movimiento en el historial.
     */
    public HistorialMovimiento registrarMovimiento(String tipoEntidad, Long entidadId, String accion, String descripcion, String usuario) {
        String usr = (usuario != null && !usuario.trim().isEmpty()) ? usuario : "Técnico";
        HistorialMovimiento hm = new HistorialMovimiento(tipoEntidad, entidadId, accion, descripcion, usr);
        return historialRepository.save(hm);
    }

    /**
     * Obtiene el historial de una entidad ordenado por fecha descendente.
     */
    public List<HistorialMovimiento> obtenerHistorial(String tipoEntidad, Long entidadId) {
        return historialRepository.findByTipoEntidadAndEntidadIdOrderByFechaDesc(tipoEntidad, entidadId);
    }

    /**
     * Obtiene todo el historial general ordenado por fecha descendente.
     */
    public List<HistorialMovimiento> obtenerTodos() {
        return historialRepository.findAllByOrderByFechaDesc();
    }
}

