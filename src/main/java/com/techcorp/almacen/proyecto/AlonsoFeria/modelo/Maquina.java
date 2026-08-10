package com.techcorp.almacen.proyecto.AlonsoFeria.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa una máquina (impresora) dentro del sistema de
 * techcorp del taller de techcorp.
 *
 * Cada instancia refleja la ficha completa definida en la Plantilla 10.1,
 * incluyendo la identidad física del equipo, su procedencia, su estado
 * técnico y la decisión operativa tomada por el técnico responsable.
 *
 * La tabla asociada en la base de datos es "maquinas" dentro del esquema
 * almacen_techcorp. Se utiliza ddl-auto=none, por lo que la estructura de
 * la tabla se gestiona manualmente en MySQL.
 */
@Entity
@Table(name = "maquinas")
public class Maquina {

    // ───────────────────── Clave primaria ─────────────────────

    /**
     * Identificador auto-incremental generado por MySQL.
     * Se emplea como clave técnica interna; el campo funcional
     * de identificación única es el número de serie.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ───────────────── Campos de la Plantilla 10.1 ─────────────────

    /**
     * Código QR asociado a la máquina. Se genera y se pega físicamente
     * sobre el chasis para que el técnico pueda escanearlo con el móvil.
     */
    @Column(name = "qr_codigo", unique = true)
    private String codigoQr;

    /** Marca comercial del equipo (ej. HP, Canon, Ricoh). */
    @Column(name = "marca")
    private String marca;

    /** Modelo específico dentro de la gama del fabricante. */
    @Column(name = "modelo")
    private String modelo;

    /**
     * Número de serie único proporcionado por el fabricante.
     * Es la columna de referencia funcional: las piezas extraídas
     * mantienen una FK hacia este valor para garantizar la techcorp.
     */
    @Column(name = "numero_serie", unique = true)
    private String numeroSerie;

    /**
     * Contador de copias/impresiones registrado en el momento de la
     * recepción. Permite estimar el desgaste acumulado del equipo.
     */
    @Column(name = "numero_copias")
    private Integer numeroCopias;

    /**
     * Cliente o empresa de procedencia que entrega la máquina al taller.
     * Se conserva para techcorp comercial y posibles reclamaciones.
     */
    @Column(name = "cliente_procedencia")
    private String clienteProcedencia;

    /**
     * Fecha en la que la máquina ingresa físicamente en el taller.
     * Se registra automáticamente o la introduce el técnico.
     */
    @Column(name = "fecha_entrada")
    private LocalDate fechaEntrada;

    /**
     * Estado de funcionamiento general observado en la recepción
     * (ej. "Funcional", "No enciende", "Atasco permanente").
     */
    @Column(name = "estado_funcionamiento")
    private String estadoFuncionamiento;

    /**
     * Códigos de avería o errores conocidos que presenta la máquina
     * (ej. "SC 552", "E-001"). Pueden ser varios separados por coma.
     */
    @Column(name = "averias_conocidas")
    private String averiasCodigos;

    /**
     * Decisión técnica tomada tras la evaluación inicial.
     * Valores permitidos: "Almacenar", "Reparar", "Reutilizar", "Despiezar".
     */
    @Column(name = "decision_tecnica")
    private String decisionTecnica;

    /**
     * Ubicación física actual del equipo dentro del taller
     * (ej. "Zona de recepción", "Estantería B-3", "Chatarrería").
     */
    @Column(name = "ubicacion_fisica")
    private String ubicacionFisica;

    /**
     * Resultado de la inspección visual del equipo en el momento de
     * la recepción. Valores permitidos: "Óptimo", "Aceptable", "Deficiente".
     */
    @Column(name = "estado_visual")
    private String estadoVisual;

    /**
     * Indica si la máquina ha sido marcada como preparada para el
     * departamento comercial. Solo las máquinas con este flag en true
     * serán visibles para el usuario comercial.
     */
    @Column(name = "preparada_comercial")
    private Boolean preparadaComercial = false;

    /**
     * Nombre o identificador del técnico que marcó la máquina como
     * preparada para el departamento comercial. Se registra para que
     * el equipo comercial sepa quién realizó la preparación.
     */
    @Column(name = "tecnico_preparado_comercial")
    private String tecnicoPreparadoComercial;

    @Column(name = "nivel_toner")
    private String nivelToner;

    @Transient
    private String modeloToner;

    @Transient
    private Long tonerId;

    /**
     * Campo libre para anotaciones adicionales del técnico que recibe
     * o evalúa la máquina (ej. "Falta tapa lateral", "Olor a quemado").
     */
    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "comentario_excepcion")
    private String comentarioExcepcion;

    @Column(name = "motivo_devolucion")
    private String motivoDevolucion;

    /**
     * Contador de copias en blanco y negro registrado en el momento
     * de la recepción del equipo.
     */
    @Column(name = "copias_bn")
    private Integer copiasBn;

    /**
     * Contador de copias en color registrado en el momento
     * de la recepción del equipo.
     */
    @Column(name = "copias_color")
    private Integer copiasColor;

    /**
     * Estado de venta comercial de la máquina.
     * Valores: null (disponible), "RESERVADA" (reservada por un comercial).
     */
    @Column(name = "estado_venta")
    private String estadoVenta;

    /**
     * Observación o nombre del cliente al que se reserva la máquina.
     * Se rellena cuando un comercial realiza una reserva.
     */
    @Column(name = "observacion_venta")
    private String observacionVenta;

    /**
     * Nombre del comercial que realizó la reserva.
     * Permite al supervisor identificar quién la reservó.
     */
    @Column(name = "comercial_reserva")
    private String comercialReserva;

    @Column(name = "es_color")
    private Boolean esColor = false;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquina_origen_serie", referencedColumnName = "numero_serie", insertable = false, updatable = false)
    private List<Toner> toneres = new ArrayList<>();

    // ───────────────── Constructor vacío (JPA) ─────────────────

    /**
     * Constructor sin argumentos requerido por el estándar JPA/Hibernate
     * para la instanciación reflectiva de las entidades.
     */
    public Maquina() {
    }

    // ───────────────── Constructor completo ─────────────────

    /**
     * Constructor con todos los campos de la Plantilla 10.1.
     * Permite crear una instancia de Maquina con todos los datos
     * disponibles en el momento del alta.
     */
    public Maquina(String codigoQr, String marca, String modelo,
            String numeroSerie, Integer numeroCopias,
            String clienteProcedencia, LocalDate fechaEntrada,
            String estadoFuncionamiento, String averiasCodigos,
            String decisionTecnica, String estadoVisual,
            Boolean preparadaComercial, String ubicacionFisica,
            String observaciones) {
        this.codigoQr = codigoQr;
        this.marca = marca;
        this.modelo = modelo;
        this.numeroSerie = numeroSerie;
        this.numeroCopias = numeroCopias;
        this.clienteProcedencia = clienteProcedencia;
        this.fechaEntrada = fechaEntrada;
        this.estadoFuncionamiento = estadoFuncionamiento;
        this.averiasCodigos = averiasCodigos;
        this.decisionTecnica = decisionTecnica;
        this.estadoVisual = estadoVisual;
        this.preparadaComercial = preparadaComercial;
        this.ubicacionFisica = ubicacionFisica;
        this.observaciones = observaciones;
    }

    // ───────────────── Getters y Setters ─────────────────

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

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public void setNumeroSerie(String numeroSerie) {
        this.numeroSerie = numeroSerie;
    }

    public Integer getNumeroCopias() {
        return numeroCopias;
    }

    public void setNumeroCopias(Integer numeroCopias) {
        this.numeroCopias = numeroCopias;
    }

    public String getClienteProcedencia() {
        return clienteProcedencia;
    }

    public void setClienteProcedencia(String clienteProcedencia) {
        this.clienteProcedencia = clienteProcedencia;
    }

    public LocalDate getFechaEntrada() {
        return fechaEntrada;
    }

    public void setFechaEntrada(LocalDate fechaEntrada) {
        this.fechaEntrada = fechaEntrada;
    }

    public String getEstadoFuncionamiento() {
        return estadoFuncionamiento;
    }

    public void setEstadoFuncionamiento(String estadoFuncionamiento) {
        this.estadoFuncionamiento = estadoFuncionamiento;
    }

    public String getAveriasCodigos() {
        return averiasCodigos;
    }

    public void setAveriasCodigos(String averiasCodigos) {
        this.averiasCodigos = averiasCodigos;
    }

    public String getDecisionTecnica() {
        return decisionTecnica;
    }

    public void setDecisionTecnica(String decisionTecnica) {
        this.decisionTecnica = decisionTecnica;
    }

    public String getUbicacionFisica() {
        return ubicacionFisica;
    }

    public void setUbicacionFisica(String ubicacionFisica) {
        this.ubicacionFisica = ubicacionFisica;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstadoVisual() {
        return estadoVisual;
    }

    public void setEstadoVisual(String estadoVisual) {
        this.estadoVisual = estadoVisual;
    }

    public Boolean getPreparadaComercial() {
        return preparadaComercial;
    }

    public void setPreparadaComercial(Boolean preparadaComercial) {
        this.preparadaComercial = preparadaComercial;
    }

    public String getTecnicoPreparadoComercial() {
        return tecnicoPreparadoComercial;
    }

    public void setTecnicoPreparadoComercial(String tecnicoPreparadoComercial) {
        this.tecnicoPreparadoComercial = tecnicoPreparadoComercial;
    }

    public String getNivelToner() {
        return nivelToner;
    }

    public void setNivelToner(String nivelToner) {
        this.nivelToner = nivelToner;
    }

    public String getComentarioExcepcion() {
        return comentarioExcepcion;
    }

    public void setComentarioExcepcion(String comentarioExcepcion) {
        this.comentarioExcepcion = comentarioExcepcion;
    }

    public String getMotivoDevolucion() {
        return motivoDevolucion;
    }

    public void setMotivoDevolucion(String motivoDevolucion) {
        this.motivoDevolucion = motivoDevolucion;
    }

    public String getEstadoVenta() {
        return estadoVenta;
    }

    public void setEstadoVenta(String estadoVenta) {
        this.estadoVenta = estadoVenta;
    }

    public String getObservacionVenta() {
        return observacionVenta;
    }

    public void setObservacionVenta(String observacionVenta) {
        this.observacionVenta = observacionVenta;
    }

    public String getComercialReserva() {
        return comercialReserva;
    }

    public void setComercialReserva(String comercialReserva) {
        this.comercialReserva = comercialReserva;
    }

    public Integer getCopiasBn() {
        return copiasBn;
    }

    public void setCopiasBn(Integer copiasBn) {
        this.copiasBn = copiasBn;
    }

    public Integer getCopiasColor() {
        return copiasColor;
    }

    public void setCopiasColor(Integer copiasColor) {
        this.copiasColor = copiasColor;
    }

    public List<Toner> getToneres() {
        return toneres;
    }

    public void setToneres(List<Toner> toneres) {
        this.toneres = toneres;
    }

    public String getModeloToner() {
        return modeloToner;
    }

    public void setModeloToner(String modeloToner) {
        this.modeloToner = modeloToner;
    }

    public Long getTonerId() {
        return tonerId;
    }

    public void setTonerId(Long tonerId) {
        this.tonerId = tonerId;
    }

    public Boolean getEsColor() {
        return esColor;
    }

    public void setEsColor(Boolean esColor) {
        this.esColor = esColor;
    }
}



