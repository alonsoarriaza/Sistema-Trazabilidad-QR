package com.trazabilidad.almacen.proyecto.AlonsoFeria.repositorio;

import com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz de acceso a datos para la entidad Maquina.
 *
 * Extiende JpaRepository proporcionando de forma automática las operaciones
 * CRUD estándar (save, findById, findAll, deleteById, etc.) sin necesidad
 * de implementación manual.
 *
 * Se añaden métodos de consulta derivada (Query Methods) para cubrir las
 * búsquedas específicas que el taller de trazabilidad necesita en su día a día:
 * localizar una máquina por su QR, por su número de serie, o filtrar por
 * la decisión técnica asignada.
 */
@Repository
public interface MaquinaRepository extends JpaRepository<Maquina, Long> {

    /**
     * Busca una máquina por su código QR.
     * Se utiliza en la bifurcación del escaneo inicial: si el QR existe
     * se muestra la ficha; si no, se abre el formulario de registro.
     *
     * @param codigoQr código QR leído por el escáner del móvil.
     * @return Optional con la máquina encontrada o vacío si no existe.
     */
    Optional<Maquina> findByCodigoQr(String codigoQr);
    Optional<Maquina> findByCodigoQrIgnoreCase(String codigoQr);

    /**
     * Busca una máquina por su número de serie único.
     * Este campo es la referencia funcional principal y la FK lógica
     * que utilizan las piezas para mantener la trazabilidad.
     *
     * @param numeroSerie número de serie del fabricante.
     * @return Optional con la máquina encontrada o vacío si no existe.
     */
    Optional<Maquina> findByNumeroSerie(String numeroSerie);
    Optional<Maquina> findByNumeroSerieIgnoreCase(String numeroSerie);

    /**
     * Lista todas las máquinas que comparten una misma decisión técnica.
     * Permite al jefe de taller obtener, por ejemplo, todas las que están
     * pendientes de "Despiezar" o de "Reparar".
     *
     * @param decisionTecnica valor de la decisión (Almacenar, Reparar,
     *                        Reutilizar o Despiezar).
     * @return lista de máquinas que coinciden con la decisión indicada.
     */
    List<Maquina> findByDecisionTecnica(String decisionTecnica);
    List<Maquina> findByDecisionTecnicaIgnoreCase(String decisionTecnica);

    @Override
    @Query("SELECT m FROM Maquina m WHERE m.codigoQr IS NOT NULL AND m.codigoQr <> ''")
    List<Maquina> findAll();

    /**
     * Busca máquinas filtrando por marca, modelo, número de serie, código QR o ubicación física (ignorando mayúsculas/minúsculas).
     */
    @Query("SELECT m FROM Maquina m WHERE (m.codigoQr IS NOT NULL AND m.codigoQr <> '') AND (" +
           "LOWER(m.marca) LIKE LOWER(CONCAT('%', :marca, '%')) OR " +
           "LOWER(m.modelo) LIKE LOWER(CONCAT('%', :modelo, '%')) OR " +
           "LOWER(m.numeroSerie) LIKE LOWER(CONCAT('%', :numeroSerie, '%')) OR " +
           "LOWER(m.codigoQr) LIKE LOWER(CONCAT('%', :codigoQr, '%')) OR " +
           "LOWER(m.ubicacionFisica) LIKE LOWER(CONCAT('%', :ubicacionFisica, '%'))" +
           ")")
    List<Maquina> findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCaseOrNumeroSerieContainingIgnoreCaseOrCodigoQrContainingIgnoreCaseOrUbicacionFisicaContainingIgnoreCase(
            @Param("marca") String marca, @Param("modelo") String modelo, @Param("numeroSerie") String numeroSerie, 
            @Param("codigoQr") String codigoQr, @Param("ubicacionFisica") String ubicacionFisica);

    /**
     * Devuelve todas las máquinas que han sido marcadas como preparadas para el equipo comercial.
     */
    List<Maquina> findByPreparadaComercialTrue();

    /**
     * Devuelve las máquinas comerciales con reserva activa (estadoVenta = 'RESERVADA').
     */
    List<Maquina> findByPreparadaComercialTrueAndEstadoVentaIsNotNull();

    /**
     * Devuelve las máquinas comerciales disponibles (sin reserva activa, estadoVenta = null).
     */
    List<Maquina> findByPreparadaComercialTrueAndEstadoVentaIsNull();

    @Query("SELECT DISTINCT m FROM Maquina m LEFT JOIN FETCH m.toneres t WHERE " +
           "(m.codigoQr IS NOT NULL AND m.codigoQr <> '') AND (" +
           "LOWER(m.modelo) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(m.codigoQr) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(t.modelo) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(t.codigoQr) LIKE LOWER(CONCAT('%', :termino, '%'))" +
           ")")
    List<Maquina> buscarVinculados(@Param("termino") String termino);

    @Query("SELECT DISTINCT m.marca FROM Maquina m WHERE m.marca IS NOT NULL AND m.marca <> '' ORDER BY m.marca")
    List<String> findDistinctMarcas();

    @Query("SELECT DISTINCT m.modelo FROM Maquina m WHERE m.modelo IS NOT NULL AND m.modelo <> '' ORDER BY m.modelo")
    List<String> findDistinctModelos();

    @Query("SELECT DISTINCT m.marca, m.modelo FROM Maquina m WHERE m.numeroSerie IN " +
           "(SELECT t.maquinaOrigenSerie FROM Toner t WHERE LOWER(t.modelo) = LOWER(:tonerModelo)) " +
           "AND m.modelo IS NOT NULL AND m.modelo <> '' ORDER BY m.modelo")
    List<Object[]> findCompatibleMaquinasByTonerModelo(@Param("tonerModelo") String tonerModelo);

    @Query("SELECT DISTINCT m FROM Maquina m LEFT JOIN FETCH m.toneres t WHERE m.codigoQr IS NOT NULL AND m.codigoQr <> ''")
    List<Maquina> findAllConToneres();
}



