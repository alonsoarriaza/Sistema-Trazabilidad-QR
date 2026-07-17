package com.techcorp.almacen.proyecto.AlonsoFeria.config;

import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Toner;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.MaquinaRepository;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.TonerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final MaquinaRepository maquinaRepository;
    private final TonerRepository tonerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;

    public DataInitializer(MaquinaRepository maquinaRepository, 
                           TonerRepository tonerRepository,
                           JdbcTemplate jdbcTemplate,
                           ResourceLoader resourceLoader) {
        this.maquinaRepository = maquinaRepository;
        this.tonerRepository = tonerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Si la base de datos está vacía, importamos el catálogo maestro
        if (maquinaRepository.count() == 0) {
            System.out.println("Base de datos vacía. Inicializando catálogo maestro desde importacion.csv...");
            Resource resource = resourceLoader.getResource("classpath:importacion.csv");
            if (resource.exists()) {
                Map<String, Maquina> maquinasCreadas = new HashMap<>();
                Set<String> tonersCreados = new HashSet<>();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = br.readLine(); // Leer cabecera
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        String delimiter = line.contains(";") ? ";" : ",";
                        String[] parts = line.split(delimiter, -1);
                        if (parts.length < 4) continue;
                        
                        String maquinaCodigo = parts[0].trim();
                        String maquinaModel = parts[1].trim();
                        String tonerProduct = parts[2].trim();
                        String tonerModel = parts[3].trim();
                        
                        if (maquinaCodigo.isEmpty() || maquinaModel.isEmpty()) continue;
                        
                        Maquina maquina = maquinasCreadas.get(maquinaCodigo);
                        if (maquina == null) {
                            maquina = new Maquina();
                            maquina.setCodigoQr(null);
                            maquina.setMarca(determinarMarca(maquinaModel));
                            maquina.setModelo(maquinaModel);
                            maquina.setNumeroSerie(maquinaCodigo);
                            maquina.setNumeroCopias(0);
                            maquina.setClienteProcedencia(null);
                            maquina.setFechaEntrada(null);
                            maquina.setEstadoFuncionamiento(null);
                            maquina.setEstadoVisual(null);
                            maquina.setUbicacionFisica(null);
                            maquina.setDecisionTecnica(null);
                            maquina.setPreparadaComercial(false);
                            
                            maquina = maquinaRepository.save(maquina);
                            maquinasCreadas.put(maquinaCodigo, maquina);
                        }
                        
                        if (!tonerProduct.isEmpty() && !tonerModel.isEmpty()) {
                            String tonerKey = tonerProduct + "-" + maquinaCodigo;
                            if (!tonersCreados.contains(tonerKey)) {
                                Toner toner = new Toner();
                                toner.setCodigoQr(null);
                                toner.setModelo(tonerModel);
                                toner.setNivelToner(null);
                                toner.setUbicacionFisica(null);
                                toner.setEstado(null);
                                toner.setFechaRegistro(null);
                                toner.setMaquinaOrigenSerie(maquina.getNumeroSerie());
                                
                                tonerRepository.save(toner);
                                tonersCreados.add(tonerKey);
                            }
                        }
                    }
                }
                System.out.println("Catálogo maestro inicializado con éxito.");
            } else {
                System.err.println("ERROR: El archivo importacion.csv no existe en el classpath.");
            }
        }

        // 2. Ejecutar la Limpieza Parcial (Soft Reset)
        System.out.println("=== INICIANDO LIMPIEZA PARCIAL (SOFT RESET) ===");
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("TRUNCATE TABLE historial_movimientos");
            jdbcTemplate.execute("TRUNCATE TABLE piezas");
            
            // UPDATE para limpiar campos transaccionales de maquinas
            jdbcTemplate.execute("UPDATE maquinas SET " +
                    "qr_codigo = NULL, " +
                    "numero_copias = 0, " +
                    "cliente_procedencia = NULL, " +
                    "fecha_entrada = NULL, " +
                    "estado_funcionamiento = NULL, " +
                    "averias_conocidas = NULL, " +
                    "decision_tecnica = NULL, " +
                    "ubicacion_fisica = NULL, " +
                    "estado_visual = NULL, " +
                    "preparada_comercial = 0, " +
                    "tecnico_preparado_comercial = NULL, " +
                    "nivel_toner = NULL, " +
                    "observaciones = NULL, " +
                    "comentario_excepcion = NULL, " +
                    "motivo_devolucion = NULL, " +
                    "copias_bn = 0, " +
                    "copias_color = 0, " +
                    "estado_venta = NULL, " +
                    "observacion_venta = NULL, " +
                    "comercial_reserva = NULL");

            // UPDATE para limpiar campos transaccionales de toners
            jdbcTemplate.execute("UPDATE toners SET " +
                    "qr_codigo = NULL, " +
                    "nivel_toner = NULL, " +
                    "ubicacion_fisica = NULL, " +
                    "fecha_registro = NULL, " +
                    "estado = NULL");

            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            System.out.println("=== LIMPIEZA PARCIAL COMPLETADA CON ÉXITO ===");
        } catch (Exception e) {
            System.err.println("Error durante la limpieza parcial: " + e.getMessage());
            e.printStackTrace();
            try {
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            } catch (Exception ex) {
                // Ignore
            }
            throw e;
        }
    }

    private String determinarMarca(String modelo) {
        if (modelo == null) {
            return "Kyocera";
        }
        String modLower = modelo.toLowerCase();
        if (modLower.contains("brother")) {
            return "Brother";
        } else if (modLower.contains("hp") || modLower.contains("laserjet")) {
            return "HP";
        } else if (modLower.contains("canon") || modLower.contains("imagerunner")) {
            return "Canon";
        } else if (modLower.contains("ricoh")) {
            return "Ricoh";
        } else {
            return "Kyocera";
        }
    }
}







