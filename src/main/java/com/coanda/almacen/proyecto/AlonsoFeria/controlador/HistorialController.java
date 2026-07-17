package com.coanda.almacen.proyecto.AlonsoFeria.controlador;

import com.coanda.almacen.proyecto.AlonsoFeria.modelo.HistorialMovimiento;
import com.coanda.almacen.proyecto.AlonsoFeria.servicio.HistorialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/historial")
@CrossOrigin(origins = "*")
public class HistorialController {

    @Autowired
    private HistorialService historialService;

    /**
     * Devuelve el historial general de todos los movimientos.
     */
    @GetMapping
    public ResponseEntity<List<HistorialMovimiento>> obtenerTodos() {
        List<HistorialMovimiento> historial = historialService.obtenerTodos();
        return new ResponseEntity<>(historial, HttpStatus.OK);
    }

    /**
     * Devuelve el historial de movimientos de una máquina o pieza.
     * tipo: "MAQUINA" o "PIEZA"
     */
    @GetMapping("/{tipo}/{id}")
    public ResponseEntity<List<HistorialMovimiento>> obtenerHistorial(
            @PathVariable String tipo,
            @PathVariable Long id) {
        List<HistorialMovimiento> historial = historialService.obtenerHistorial(tipo.toUpperCase(), id);
        return new ResponseEntity<>(historial, HttpStatus.OK);
    }
}


