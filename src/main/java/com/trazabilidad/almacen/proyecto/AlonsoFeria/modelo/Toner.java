package com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "toners")
public class Toner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qr_codigo", unique = true)
    private String codigoQr;

    @Column(name = "modelo")
    private String modelo;

    @Column(name = "nivel_toner")
    private String nivelToner; // "0%", "25%", "50%", "75%", "100%"

    @Column(name = "ubicacion_fisica")
    private String ubicacionFisica;

    @Column(name = "fecha_registro")
    private LocalDate fechaRegistro;

    @Column(name = "estado")
    private String estado; // "Disponible", "En máquina"

    @Column(name = "maquina_origen_serie")
    private String maquinaOrigenSerie;

    public Toner() {
    }

    public Toner(String codigoQr, String modelo, String nivelToner, String ubicacionFisica, String estado, String maquinaOrigenSerie) {
        this.codigoQr = codigoQr;
        this.modelo = modelo;
        this.nivelToner = nivelToner;
        this.ubicacionFisica = ubicacionFisica;
        this.fechaRegistro = LocalDate.now();
        this.estado = estado;
        this.maquinaOrigenSerie = maquinaOrigenSerie;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoQr() {
        return codigoQr;
    }

    public void setCodigoQr(String codigoQr) {
        this.codigoQr = codigoQr;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getNivelToner() {
        return nivelToner;
    }

    public void setNivelToner(String nivelToner) {
        if ("0%".equals(nivelToner)) {
            throw new IllegalArgumentException("El nivel de tóner no puede ser 0%. El mínimo permitido es 25%.");
        }
        this.nivelToner = nivelToner;
    }

    public String getUbicacionFisica() {
        return ubicacionFisica;
    }

    public void setUbicacionFisica(String ubicacionFisica) {
        this.ubicacionFisica = ubicacionFisica;
    }

    public LocalDate getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDate fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMaquinaOrigenSerie() {
        return maquinaOrigenSerie;
    }

    public void setMaquinaOrigenSerie(String maquinaOrigenSerie) {
        this.maquinaOrigenSerie = maquinaOrigenSerie;
    }
}

