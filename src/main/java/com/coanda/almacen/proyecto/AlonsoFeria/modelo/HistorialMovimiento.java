package com.coanda.almacen.proyecto.AlonsoFeria.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_movimientos", indexes = {
    @Index(name = "idx_hm_entidad", columnList = "tipo_entidad, entidad_id"),
    @Index(name = "idx_hm_fecha", columnList = "fecha")
})
public class HistorialMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_entidad")
    private String tipoEntidad; // "MAQUINA" o "PIEZA"

    @Column(name = "entidad_id")
    private Long entidadId; // ID de la máquina o pieza

    @Column(name = "accion")
    private String accion; // "ALTA", "EXTRACCION", "DEVOLUCION", "INSTALACION", "SALIDA_CLIENTE", "SALIDA_REPARACION", "PREPARADA_COMERCIAL", "EDICION"

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "usuario_responsable")
    private String usuarioResponsable;

    public HistorialMovimiento() {
    }

    public HistorialMovimiento(String tipoEntidad, Long entidadId, String accion, String descripcion, String usuarioResponsable) {
        this.tipoEntidad = tipoEntidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.descripcion = descripcion;
        this.usuarioResponsable = usuarioResponsable;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipoEntidad() {
        return tipoEntidad;
    }

    public void setTipoEntidad(String tipoEntidad) {
        this.tipoEntidad = tipoEntidad;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(Long entidadId) {
        this.entidadId = entidadId;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getUsuarioResponsable() {
        return usuarioResponsable;
    }

    public void setUsuarioResponsable(String usuarioResponsable) {
        this.usuarioResponsable = usuarioResponsable;
    }
}


