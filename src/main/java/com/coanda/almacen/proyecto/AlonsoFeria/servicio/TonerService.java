package com.coanda.almacen.proyecto.AlonsoFeria.servicio;

import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Toner;
import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.coanda.almacen.proyecto.AlonsoFeria.repositorio.TonerRepository;
import com.coanda.almacen.proyecto.AlonsoFeria.repositorio.MaquinaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class TonerService {

    @Autowired
    private TonerRepository tonerRepository;

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private HistorialService historialService;

    public synchronized Toner guardarToner(Toner toner, String tecnico) {
        if (toner.getUbicacionFisica() != null && toner.getUbicacionFisica().trim().isEmpty()) {
            toner.setUbicacionFisica(null);
        }

        if (toner.getMaquinaOrigenSerie() != null && !toner.getMaquinaOrigenSerie().trim().isEmpty()) {
            Optional<Maquina> maquinaOpt = maquinaRepository.findByNumeroSerieIgnoreCase(toner.getMaquinaOrigenSerie());
            if (maquinaOpt.isPresent() && Boolean.TRUE.equals(maquinaOpt.get().getPreparadaComercial())) {
                throw new IllegalStateException("No se puede extraer un tóner de una máquina que está en estado comercial.");
            }
        }

        // Si tiene máquina de origen y el estado solicitado es "Disponible" (Extracción)
        if (toner.getMaquinaOrigenSerie() != null && !toner.getMaquinaOrigenSerie().trim().isEmpty()
                && "Disponible".equalsIgnoreCase(toner.getEstado())) {
            
            // Buscar si ya existe un tóner instalado en esa máquina ("En máquina")
            Optional<Toner> existenteOpt = tonerRepository.findFirstByMaquinaOrigenSerieAndEstadoIgnoreCase(
                    toner.getMaquinaOrigenSerie(), "En máquina");
            
            if (existenteOpt.isPresent()) {
                Toner existente = existenteOpt.get();
                String maquinaSerie = existente.getMaquinaOrigenSerie();
                
                // Actualizar su estado a "Disponible"
                existente.setEstado("Disponible");
                // Poner su maquina_id (maquinaOrigenSerie) = null
                existente.setMaquinaOrigenSerie(null);
                // Poner su ubicación a 'Almacén'
                existente.setUbicacionFisica("Almacén");
                
                // Guardar el existente para evitar duplicar el stock
                Toner guardado = tonerRepository.save(existente);
                
                String desc = "Tóner " + guardado.getCodigoQr() + " extraído de máquina serie: " + maquinaSerie
                              + " (Modelo: " + guardado.getModelo() + ", Nivel: " + guardado.getNivelToner() + "). Ubicado en: Almacén";
                
                // Registrar movimiento de extracción del tóner (apuntando al ID del tóner original)
                historialService.registrarMovimiento("TONER", guardado.getId(), "EXTRACCION", desc, tecnico);
                
                // Registrar también en el historial de la máquina madre si existe
                if (maquinaSerie != null && !maquinaSerie.trim().isEmpty()) {
                    Optional<Maquina> maqOp = maquinaRepository.findByNumeroSerieIgnoreCase(maquinaSerie);
                    if (maqOp.isPresent()) {
                        Maquina maquina = maqOp.get();
                        historialService.registrarMovimiento("MAQUINA", maquina.getId(), "EXTRACCION_TONER", 
                                "Se ha extraído el tóner: " + guardado.getModelo() + " (QR: " + guardado.getCodigoQr() + ")", tecnico);
                    }
                }
                
                return guardado;
            }
        }

        // Comportamiento por defecto para nuevos tóners
        if (toner.getCodigoQr() == null || toner.getCodigoQr().trim().isEmpty()) {
            toner.setCodigoQr(generarSiguienteQr());
        }

        Toner guardado = tonerRepository.save(toner);

        String desc = "Tóner registrado en stock (Modelo: " + guardado.getModelo() 
                      + ", Nivel: " + guardado.getNivelToner() + ").";
        
        String accion = "ALTA";
        if (guardado.getMaquinaOrigenSerie() != null && !guardado.getMaquinaOrigenSerie().isEmpty()) {
            if ("En máquina".equalsIgnoreCase(guardado.getEstado())) {
                desc += " Instalado en máquina serie: " + guardado.getMaquinaOrigenSerie();
                accion = "INSTALACION";
            } else {
                desc += " Extraído de máquina serie: " + guardado.getMaquinaOrigenSerie();
                accion = "EXTRACCION";
            }
        }
        
        historialService.registrarMovimiento("TONER", guardado.getId(), 
                accion, 
                desc, tecnico);

        // Registrar también en el historial de la máquina madre si se crea un tóner
        if (guardado.getMaquinaOrigenSerie() != null && !guardado.getMaquinaOrigenSerie().trim().isEmpty()) {
            Optional<Maquina> maqOp = maquinaRepository.findByNumeroSerieIgnoreCase(guardado.getMaquinaOrigenSerie());
            if (maqOp.isPresent()) {
                Maquina maquina = maqOp.get();
                if ("En máquina".equalsIgnoreCase(guardado.getEstado())) {
                    historialService.registrarMovimiento("MAQUINA", maquina.getId(), "INSTALACION_TONER", 
                            "Se ha instalado el tóner: " + guardado.getModelo() + " (QR: " + guardado.getCodigoQr() + ")", tecnico);
                } else {
                    historialService.registrarMovimiento("MAQUINA", maquina.getId(), "EXTRACCION_TONER", 
                            "Se ha extraído el tóner: " + guardado.getModelo() + " (QR: " + guardado.getCodigoQr() + ")", tecnico);
                }
            }
        }

        return guardado;

    }

    public Optional<Toner> buscarTonerInstalado(String numeroSerie) {
        return tonerRepository.findFirstByMaquinaOrigenSerieAndEstadoIgnoreCase(numeroSerie, "En máquina");
    }



    public List<Toner> listarTodos() {
        return tonerRepository.findAllByOrderByFechaRegistroDesc();
    }

    public Optional<Toner> buscarPorId(Long id) {
        return tonerRepository.findById(id);
    }

    public Optional<Toner> buscarPorCodigoQr(String codigoQr) {
        return tonerRepository.findByCodigoQrIgnoreCase(codigoQr);
    }

    public boolean eliminarToner(Long id, String motivo, String tecnico) {
        Optional<Toner> existente = tonerRepository.findById(id);
        if (existente.isPresent()) {
            Toner t = existente.get();
            historialService.registrarMovimiento("TONER", id, "BAJA",
                    "Tóner " + t.getCodigoQr() + " eliminado. Motivo: " + motivo, tecnico);
            tonerRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private String generarSiguienteQr() {
        long count = tonerRepository.count();
        String qr = "TNR-" + String.format("%05d", count + 1);
        int attempts = 0;
        while (tonerRepository.findByCodigoQr(qr).isPresent() && attempts < 100) {
            count++;
            qr = "TNR-" + String.format("%05d", count + 1);
            attempts++;
        }
        return qr;
    }

    public List<Toner> buscar(String termino) {
        List<Toner> todos = tonerRepository.findAll();
        if (termino == null || termino.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        String[] tokens = termino.trim().toLowerCase().split("\\s+");
        List<Toner> filtrados = new java.util.ArrayList<>();
        
        for (Toner t : todos) {
            if (t.getCodigoQr() == null || t.getCodigoQr().trim().isEmpty()) {
                continue;
            }
            boolean coincideTodosLosTokens = true;
            for (String token : tokens) {
                boolean coincideToken = false;
                if (t.getModelo() != null && t.getModelo().toLowerCase().contains(token)) coincideToken = true;
                if (t.getCodigoQr() != null && t.getCodigoQr().toLowerCase().contains(token)) coincideToken = true;
                if (t.getUbicacionFisica() != null && t.getUbicacionFisica().toLowerCase().contains(token)) coincideToken = true;
                if (t.getMaquinaOrigenSerie() != null && t.getMaquinaOrigenSerie().toLowerCase().contains(token)) coincideToken = true;
                if (t.getEstado() != null && t.getEstado().toLowerCase().contains(token)) coincideToken = true;
                
                if (!coincideToken) {
                    coincideTodosLosTokens = false;
                    break;
                }
            }
            if (coincideTodosLosTokens) {
                filtrados.add(t);
            }
        }
        return filtrados;
    }

    public List<String> buscarToneresCompatibles(String modelo) {
        return tonerRepository.findCompatibleTonerModelsByMaquinaModelo(modelo);
    }

    public List<Object[]> buscarMaquinasCompatibles(String tonerModelo) {
        return maquinaRepository.findCompatibleMaquinasByTonerModelo(tonerModelo);
    }
}




