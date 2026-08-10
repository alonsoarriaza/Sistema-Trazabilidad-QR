package com.coanda.almacen.proyecto.AlonsoFeria.controlador;

import com.coanda.almacen.proyecto.AlonsoFeria.modelo.Toner;
import com.coanda.almacen.proyecto.AlonsoFeria.servicio.TonerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/toners")
@CrossOrigin(origins = "*")
public class TonerController {

    @Autowired
    private TonerService tonerService;

    @PostMapping
    public ResponseEntity<?> crearToner(@RequestBody Toner toner,
                                             @RequestParam(required = false) String tecnico) {
        try {
            Toner nuevo = tonerService.guardarToner(toner, tecnico);
            return new ResponseEntity<>(nuevo, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Toner>> listarTodos() {
        List<Toner> list = tonerService.listarTodos();
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Toner> buscarPorId(@PathVariable Long id) {
        Optional<Toner> opt = tonerService.buscarPorId(id);
        return opt.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                  .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/qr/{codigoQr}")
    public ResponseEntity<Toner> buscarPorCodigoQr(@PathVariable String codigoQr) {
        Optional<Toner> opt = tonerService.buscarPorCodigoQr(codigoQr);
        return opt.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                  .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/instalado/{numeroSerie}")
    public ResponseEntity<Toner> buscarTonerInstalado(@PathVariable String numeroSerie) {
        Optional<Toner> opt = tonerService.buscarTonerInstalado(numeroSerie);
        return opt.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                  .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarToner(@PathVariable Long id,
                                               @RequestParam String motivo,
                                               @RequestParam String tecnico) {
        boolean eliminado = tonerService.eliminarToner(id, motivo, tecnico);
        if (eliminado) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Toner>> buscar(@RequestParam("q") String termino) {
        List<Toner> list = tonerService.buscar(termino);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
}


