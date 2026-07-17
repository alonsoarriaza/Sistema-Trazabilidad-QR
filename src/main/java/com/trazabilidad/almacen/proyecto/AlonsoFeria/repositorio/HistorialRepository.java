package com.trazabilidad.almacen.proyecto.AlonsoFeria.repositorio;

import com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo.HistorialMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HistorialRepository extends JpaRepository<HistorialMovimiento, Long> {
    List<HistorialMovimiento> findByTipoEntidadAndEntidadIdOrderByFechaDesc(String tipoEntidad, Long entidadId);
    List<HistorialMovimiento> findAllByOrderByFechaDesc();

}

