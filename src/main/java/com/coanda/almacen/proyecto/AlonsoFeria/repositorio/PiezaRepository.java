package com.coanda.almacen.proyecto.AlonsoFeria.repositorio;

import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Pieza;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz de acceso a datos para la entidad Pieza.
 *
 * Extiende JpaRepository proporcionando de forma automática las operaciones
 * CRUD estándar (save, findById, findAll, deleteById, etc.) sin necesidad
 * de implementación manual.
 *
 * Se añaden métodos de consulta derivada (Query Methods) para cubrir las
 * búsquedas habituales del taller: localizar una pieza por su QR, obtener
 * todas las piezas de una máquina concreta, filtrar por nivel de estado
 * fungible o por tipo de componente.
 */
@Repository
public interface PiezaRepository extends JpaRepository<Pieza, Long> {

    /**
     * Busca una pieza por su código QR individual.
     * Se utiliza cuando el técnico escanea la etiqueta QR del componente.
     *
     * @param codigoQrPieza código QR adherido a la pieza.
     * @return Optional con la pieza encontrada o vacío si no existe.
     */
    Optional<Pieza> findByCodigoQrPieza(String codigoQrPieza);
    Optional<Pieza> findByCodigoQrPiezaIgnoreCase(String codigoQrPieza);

    @Override
    @Query("SELECT p FROM Pieza p WHERE p.codigoQrPieza IS NOT NULL AND p.codigoQrPieza <> ''")
    List<Pieza> findAll();

    /**
     * Lista todas las piezas que provienen de una máquina concreta,
     * identificada por su número de serie.
     * Permite, por ejemplo, saber qué componentes se han extraído
     * de una impresora específica para detectar "Máquinas Zombi"
     * (chasis sin piezas restantes).
     *
     * @param numeroSerieMaquinaOrigen número de serie de la máquina madre.
     * @return lista de piezas asociadas a esa máquina.
     */
    List<Pieza> findByNumeroSerieMaquinaOrigen(String numeroSerieMaquinaOrigen);

    /**
     * Lista todas las piezas que se encuentran en un nivel de estado
     * fungible determinado (1, 2 o 3).
     * Resulta útil para que el técnico localice rápidamente, por ejemplo,
     * todas las piezas de Nivel 1 (buen estado) cuando necesita un repuesto.
     *
     * @param nivelEstado nivel de estado (1 = bueno, 2 = aceptable, 3 = dudoso).
     * @return lista de piezas con ese nivel de estado.
     */
    @Query("SELECT p FROM Pieza p WHERE (p.codigoQrPieza IS NOT NULL AND p.codigoQrPieza <> '') AND p.nivelEstado = :nivelEstado")
    List<Pieza> findByNivelEstado(@Param("nivelEstado") Integer nivelEstado);

    /**
     * Lista todas las piezas de un tipo concreto (ej. "Fusor", "Rodillo").
     * Facilita la búsqueda cuando el técnico necesita un componente
     * específico sin importar de qué máquina provenga.
     *
     * @param tipoPieza tipo del componente.
     * @return lista de piezas de ese tipo.
     */
    @Query("SELECT p FROM Pieza p WHERE (p.codigoQrPieza IS NOT NULL AND p.codigoQrPieza <> '') AND p.tipoPieza = :tipoPieza")
    List<Pieza> findByTipoPieza(@Param("tipoPieza") String tipoPieza);

    /**
     * Busca piezas filtrando por tipo, referencia, marca/modelo compatible, código QR o ubicación física (ignorando mayúsculas/minúsculas).
     */
    @Query("SELECT p FROM Pieza p WHERE (p.codigoQrPieza IS NOT NULL AND p.codigoQrPieza <> '') AND (" +
           "LOWER(p.tipoPieza) LIKE LOWER(CONCAT('%', :tipoPieza, '%')) OR " +
           "LOWER(p.referencia) LIKE LOWER(CONCAT('%', :referencia, '%')) OR " +
           "LOWER(p.marcaModeloCompatible) LIKE LOWER(CONCAT('%', :marcaModeloCompatible, '%')) OR " +
           "LOWER(p.codigoQrPieza) LIKE LOWER(CONCAT('%', :codigoQrPieza, '%')) OR " +
           "LOWER(p.ubicacionFisica) LIKE LOWER(CONCAT('%', :ubicacionFisica, '%'))" +
           ")")
    List<Pieza> findByTipoPiezaContainingIgnoreCaseOrReferenciaContainingIgnoreCaseOrMarcaModeloCompatibleContainingIgnoreCaseOrCodigoQrPiezaContainingIgnoreCaseOrUbicacionFisicaContainingIgnoreCase(
            @Param("tipoPieza") String tipoPieza, @Param("referencia") String referencia, 
            @Param("marcaModeloCompatible") String marcaModeloCompatible, @Param("codigoQrPieza") String codigoQrPieza, 
            @Param("ubicacionFisica") String ubicacionFisica);
}


