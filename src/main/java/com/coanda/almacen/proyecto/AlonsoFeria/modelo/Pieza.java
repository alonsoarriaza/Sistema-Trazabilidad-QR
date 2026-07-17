package com.coanda.almacen.proyecto.AlonsoFeria.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Entidad que representa una pieza o componente interno extraído de
 * una máquina dentro del sistema de coanda del taller de coanda.
 *
 * Cada instancia refleja la ficha completa definida en la Plantilla 10.2,
 * incluyendo la identidad de la pieza, su nivel de estado fungible (1, 2 o 3),
 * la relación con la máquina de origen y su ubicación actual en el taller.
 *
 * La Foreign Key "numero_serie_maquina_origen" permite rastrear en cualquier
 * momento de qué impresora procede cada componente, facilitando diagnósticos
 * cruzados si la pieza falla meses después de su extracción.
 *
 * La tabla asociada en la base de datos es "piezas" dentro del esquema
 * almacen_coanda. Se utiliza ddl-auto=none, por lo que la estructura de
 * la tabla se gestiona manualmente en MySQL.
 */
@Entity
@Table(name = "piezas")
public class Pieza {

    // ───────────────────── Clave primaria ─────────────────────

    /**
     * Identificador auto-incremental generado por MySQL.
     * Se emplea como clave técnica interna de cada pieza.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ───────────────── Campos de la Plantilla 10.2 ─────────────────

    /**
     * Código QR individual de la pieza. Se genera y se adhiere
     * físicamente al componente para su escaneo con el móvil.
     */
    @Column(name = "qr_codigo_pieza", unique = true)
    private String codigoQrPieza;

    /**
     * Tipo de pieza extraída (ej. "Fusor", "Rodillo de presión",
     * "Placa controladora", "Bandeja superior").
     */
    @Column(name = "tipo_pieza")
    private String tipoPieza;

    /**
     * Referencia del fabricante o código de la pieza, si se conoce.
     * Permite búsquedas rápidas por número de parte en catálogos.
     */
    @Column(name = "referencia")
    private String referencia;

    /**
     * Marca y modelo de la máquina con la que esta pieza es compatible.
     * Facilita la reutilización al buscar repuestos para otras máquinas.
     */
    @Column(name = "marca_modelo_compatible")
    private String marcaModeloCompatible;

    /**
     * Número de serie de la máquina de la que se extrajo la pieza.
     * Esta columna actúa como Foreign Key lógica hacia la tabla "maquinas",
     * permitiendo la coanda inversa completa.
     */
    @Column(name = "numero_serie_maquina_origen")
    private String numeroSerieMaquinaOrigen;

    /**
     * Nivel de estado fungible de la pieza según la catalogación del taller:
     *   1 → Buen estado aparente (prioritaria para reutilización).
     *   2 → Estado aceptable (apoyo / pendiente de revisión).
     *   3 → Estado dudoso o desgaste visible (revisar o descartar).
     */
    @Column(name = "nivel_estado")
    private Integer nivelEstado;

    /**
     * Indica si la máquina de la que procede la pieza estaba en
     * funcionamiento o averiada en el momento de la extracción
     * (ej. "Funcionamiento", "Averiada").
     */
    @Column(name = "maquina_origen_funcionando")
    @jakarta.persistence.Convert(converter = ProcedenciaEstadoConverter.class)
    private String procedenciaEstadoMaquina;

    /**
     * Código de avería que presentaba la máquina de origen
     * (ej. "SC 552"). Complementa la coanda para diagnósticos
     * cruzados de fallos recurrentes.
     */
    @Column(name = "codigo_averia_maquina")
    private String codigoAveriaMaquina;

    /**
     * Descripción de la relación entre la avería registrada en la
     * máquina de origen y esta pieza concreta (ej. "Directamente
     * afectada", "Sin relación aparente", "Posible causa raíz").
     */
    @Column(name = "relacion_averia_pieza")
    @jakarta.persistence.Convert(converter = RelacionAveriaConverter.class)
    private String relacionAveriaPieza;

    /**
     * Ubicación física actual de la pieza en el taller
     * (ej. "Estantería A", "Cajón B-2", "Desechada").
     */
    @Column(name = "ubicacion_fisica")
    private String ubicacionFisica;

    /**
     * Fecha en la que se da de alta la pieza en el sistema,
     * normalmente coincide con el momento de su extracción.
     */
    @Column(name = "fecha_alta")
    private LocalDate fechaAlta;

    /**
     * Fecha en la que la pieza se instala en otra máquina (uso)
     * o se da de baja definitiva del inventario (descarte/chatarrería).
     */
    @Column(name = "fecha_uso_baja")
    private LocalDate fechaUsoBaja;

    /**
     * Campo libre para anotaciones adicionales del técnico
     * (ej. "Rodillo con marca leve en el extremo derecho").
     */
    @Column(name = "observaciones")
    private String observaciones;

    /**
     * Estado actual de la pieza dentro del flujo de gestión.
     * Valores permitidos: "En almacén", "Para reparación",
     * "Para cliente", "Instalada".
     */
    @Column(name = "estado_pieza")
    private String estadoPieza;

    /**
     * Destino del cliente cuando el estado es "Para cliente".
     * El técnico debe indicar a dónde va la pieza.
     */
    @Column(name = "destino_cliente")
    private String destinoCliente;

    // ───────────────── Constructor vacío (JPA) ─────────────────

    /**
     * Constructor sin argumentos requerido por el estándar JPA/Hibernate
     * para la instanciación reflectiva de las entidades.
     */
    public Pieza() {
    }

    // ───────────────── Constructor completo ─────────────────

    /**
     * Constructor con todos los campos de la Plantilla 10.2.
     * Permite crear una instancia de Pieza con todos los datos
     * disponibles en el momento del alta.
     */
    public Pieza(String codigoQrPieza, String tipoPieza, String referencia,
                 String marcaModeloCompatible, String numeroSerieMaquinaOrigen,
                 Integer nivelEstado, String procedenciaEstadoMaquina,
                 String codigoAveriaMaquina, String relacionAveriaPieza,
                 String ubicacionFisica, LocalDate fechaAlta,
                 LocalDate fechaUsoBaja, String observaciones) {
        this.codigoQrPieza = codigoQrPieza;
        this.tipoPieza = tipoPieza;
        this.referencia = referencia;
        this.marcaModeloCompatible = marcaModeloCompatible;
        this.numeroSerieMaquinaOrigen = numeroSerieMaquinaOrigen;
        this.nivelEstado = nivelEstado;
        this.procedenciaEstadoMaquina = procedenciaEstadoMaquina;
        this.codigoAveriaMaquina = codigoAveriaMaquina;
        this.relacionAveriaPieza = relacionAveriaPieza;
        this.ubicacionFisica = ubicacionFisica;
        this.fechaAlta = fechaAlta;
        this.fechaUsoBaja = fechaUsoBaja;
        this.observaciones = observaciones;
    }

    // ───────────────── Getters y Setters ─────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoQrPieza() {
        return codigoQrPieza;
    }

    public void setCodigoQrPieza(String codigoQrPieza) {
        this.codigoQrPieza = codigoQrPieza;
    }

    public String getTipoPieza() {
        return tipoPieza;
    }

    public void setTipoPieza(String tipoPieza) {
        this.tipoPieza = tipoPieza;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getMarcaModeloCompatible() {
        return marcaModeloCompatible;
    }

    public void setMarcaModeloCompatible(String marcaModeloCompatible) {
        this.marcaModeloCompatible = marcaModeloCompatible;
    }

    public String getNumeroSerieMaquinaOrigen() {
        return numeroSerieMaquinaOrigen;
    }

    public void setNumeroSerieMaquinaOrigen(String numeroSerieMaquinaOrigen) {
        this.numeroSerieMaquinaOrigen = numeroSerieMaquinaOrigen;
    }

    public Integer getNivelEstado() {
        return nivelEstado;
    }

    public void setNivelEstado(Integer nivelEstado) {
        this.nivelEstado = nivelEstado;
    }

    public String getProcedenciaEstadoMaquina() {
        return procedenciaEstadoMaquina;
    }

    public void setProcedenciaEstadoMaquina(String procedenciaEstadoMaquina) {
        this.procedenciaEstadoMaquina = procedenciaEstadoMaquina;
    }

    public String getCodigoAveriaMaquina() {
        return codigoAveriaMaquina;
    }

    public void setCodigoAveriaMaquina(String codigoAveriaMaquina) {
        this.codigoAveriaMaquina = codigoAveriaMaquina;
    }

    public String getRelacionAveriaPieza() {
        return relacionAveriaPieza;
    }

    public void setRelacionAveriaPieza(String relacionAveriaPieza) {
        this.relacionAveriaPieza = relacionAveriaPieza;
    }

    public String getUbicacionFisica() {
        return ubicacionFisica;
    }

    public void setUbicacionFisica(String ubicacionFisica) {
        this.ubicacionFisica = ubicacionFisica;
    }

    public LocalDate getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(LocalDate fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    public LocalDate getFechaUsoBaja() {
        return fechaUsoBaja;
    }

    public void setFechaUsoBaja(LocalDate fechaUsoBaja) {
        this.fechaUsoBaja = fechaUsoBaja;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstadoPieza() {
        return estadoPieza;
    }

    public void setEstadoPieza(String estadoPieza) {
        this.estadoPieza = estadoPieza;
    }

    public String getDestinoCliente() {
        return destinoCliente;
    }

    public void setDestinoCliente(String destinoCliente) {
        this.destinoCliente = destinoCliente;
    }

    // ───────────────── Conversores JPA Custom ─────────────────

    @jakarta.persistence.Converter(autoApply = false)
    public static class ProcedenciaEstadoConverter implements jakarta.persistence.AttributeConverter<String, Integer> {
        @Override
        public Integer convertToDatabaseColumn(String attribute) {
            if (attribute == null) return null;
            if ("Funcionamiento".equalsIgnoreCase(attribute) || "En funcionamiento".equalsIgnoreCase(attribute)) {
                return 1;
            }
            if ("Averiada".equalsIgnoreCase(attribute)) {
                return 0;
            }
            return null;
        }

        @Override
        public String convertToEntityAttribute(Integer dbData) {
            if (dbData == null) return null;
            return dbData == 1 ? "Funcionamiento" : "Averiada";
        }
    }

    @jakarta.persistence.Converter(autoApply = false)
    public static class RelacionAveriaConverter implements jakarta.persistence.AttributeConverter<String, Integer> {
        @Override
        public Integer convertToDatabaseColumn(String attribute) {
            if (attribute == null) return null;
            if ("Directamente afectada".equalsIgnoreCase(attribute) || "Posible causa raíz".equalsIgnoreCase(attribute)) {
                return 1;
            }
            if ("Sin relación aparente".equalsIgnoreCase(attribute)) {
                return 0;
            }
            return null;
        }

        @Override
        public String convertToEntityAttribute(Integer dbData) {
            if (dbData == null) return null;
            return dbData == 1 ? "Directamente afectada" : "Sin relación aparente";
        }
    }
}


