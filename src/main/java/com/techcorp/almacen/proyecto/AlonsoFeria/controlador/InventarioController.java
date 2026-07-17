package com.techcorp.almacen.proyecto.AlonsoFeria.controlador;

import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Toner;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.MaquinaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario")
@CrossOrigin(origins = "*")
public class InventarioController {

    @Autowired
    private MaquinaRepository maquinaRepository;

    @GetMapping("/buscar")
    public ResponseEntity<List<Maquina>> buscar(@RequestParam("termino") String termino) {
        if (termino == null || termino.trim().isEmpty()) {
            return new ResponseEntity<>(new java.util.ArrayList<>(), HttpStatus.OK);
        }
        
        List<Maquina> todas = maquinaRepository.findAllConToneres();
        String[] tokens = termino.trim().toLowerCase().split("\\s+");
        List<Maquina> filtradas = new java.util.ArrayList<>();
        
        for (Maquina m : todas) {
            boolean coincideTodosLosTokens = true;
            for (String token : tokens) {
                boolean coincideToken = false;
                
                if (m.getMarca() != null && m.getMarca().toLowerCase().contains(token)) coincideToken = true;
                if (m.getModelo() != null && m.getModelo().toLowerCase().contains(token)) coincideToken = true;
                if (m.getNumeroSerie() != null && m.getNumeroSerie().toLowerCase().contains(token)) coincideToken = true;
                if (m.getCodigoQr() != null && m.getCodigoQr().toLowerCase().contains(token)) coincideToken = true;
                if (m.getUbicacionFisica() != null && m.getUbicacionFisica().toLowerCase().contains(token)) coincideToken = true;
                
                if (m.getToneres() != null) {
                    for (Toner t : m.getToneres()) {
                        if (t.getModelo() != null && t.getModelo().toLowerCase().contains(token)) coincideToken = true;
                        if (t.getCodigoQr() != null && t.getCodigoQr().toLowerCase().contains(token)) coincideToken = true;
                    }
                }
                
                if (!coincideToken) {
                    coincideTodosLosTokens = false;
                    break;
                }
            }
            if (coincideTodosLosTokens) {
                filtradas.add(m);
            }
        }
        
        return new ResponseEntity<>(filtradas, HttpStatus.OK);
    }
}



