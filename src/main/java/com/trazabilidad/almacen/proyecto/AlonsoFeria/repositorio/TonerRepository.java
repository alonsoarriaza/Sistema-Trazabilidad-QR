package com.trazabilidad.almacen.proyecto.AlonsoFeria.repositorio;

import com.trazabilidad.almacen.proyecto.AlonsoFeria.modelo.Toner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TonerRepository extends JpaRepository<Toner, Long> {
    Optional<Toner> findByCodigoQr(String codigoQr);
    Optional<Toner> findByCodigoQrIgnoreCase(String codigoQr);

    @Override
    @Query("SELECT t FROM Toner t WHERE t.codigoQr IS NOT NULL AND t.codigoQr <> ''")
    List<Toner> findAll();

    @Query("SELECT t FROM Toner t WHERE t.codigoQr IS NOT NULL AND t.codigoQr <> '' ORDER BY t.fechaRegistro DESC")
    List<Toner> findAllByOrderByFechaRegistroDesc();

    @Query("SELECT t FROM Toner t WHERE (t.codigoQr IS NOT NULL AND t.codigoQr <> '') AND (" +
           "LOWER(t.modelo) LIKE LOWER(CONCAT('%', :modelo, '%')) OR " +
           "LOWER(t.codigoQr) LIKE LOWER(CONCAT('%', :codigoQr, '%')) OR " +
           "LOWER(t.ubicacionFisica) LIKE LOWER(CONCAT('%', :ubicacionFisica, '%'))" +
           ")")
    List<Toner> findByModeloContainingIgnoreCaseOrCodigoQrContainingIgnoreCaseOrUbicacionFisicaContainingIgnoreCase(
            @Param("modelo") String modelo, @Param("codigoQr") String codigoQr, @Param("ubicacionFisica") String ubicacionFisica);

    Optional<Toner> findFirstByMaquinaOrigenSerieAndEstadoIgnoreCase(String maquinaOrigenSerie, String estado);

    @Query("SELECT DISTINCT t.modelo FROM Toner t WHERE t.maquinaOrigenSerie IN " +
           "(SELECT m.numeroSerie FROM Maquina m WHERE LOWER(m.modelo) = LOWER(:modelo)) " +
           "AND t.modelo IS NOT NULL AND t.modelo <> '' ORDER BY t.modelo")
    List<String> findCompatibleTonerModelsByMaquinaModelo(@Param("modelo") String modelo);
}



