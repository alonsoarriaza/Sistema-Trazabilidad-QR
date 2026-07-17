package com.techcorp.almacen.proyecto.AlonsoFeria.controlador;

import com.techcorp.almacen.proyecto.AlonsoFeria.servicio.TonerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controlador REST para operaciones relacionadas con el catálogo de compatibilidades.
 */
@RestController
@RequestMapping("/api/catalogo")
@CrossOrigin(origins = "*")
public class CatalogoController {

    @Autowired
    private TonerService tonerService;

    /**
     * Endpoint que recibe el modelo de una máquina por parámetro y devuelve
     * la lista de modelos de tóneres compatibles asociados a ese modelo.
     *
     * URL: GET /api/catalogo/compatibles?modelo=...
     *
     * @param modelo Nombre o modelo de la máquina.
     * @return ResponseEntity con la lista de tóneres compatibles y código HTTP 200 (OK).
     */
    @GetMapping("/compatibles")
    public ResponseEntity<List<String>> obtenerToneresCompatibles(@RequestParam(name = "modelo", required = false) String modelo) {
        if (modelo == null || modelo.trim().isEmpty()) {
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }
        List<String> compatibles = tonerService.buscarToneresCompatibles(modelo.trim());
        return new ResponseEntity<>(compatibles, HttpStatus.OK);
    }

    /**
     * Endpoint que recibe el modelo de un tóner por parámetro y devuelve
     * la lista de máquinas compatibles (marca y modelo).
     *
     * URL: GET /api/catalogo/maquinas-compatibles?toner=...
     */
    @GetMapping("/maquinas-compatibles")
    public ResponseEntity<List<java.util.Map<String, String>>> obtenerMaquinasCompatibles(@RequestParam(name = "toner", required = false) String toner) {
        List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        if (toner == null || toner.trim().isEmpty()) {
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        List<Object[]> queryRes = tonerService.buscarMaquinasCompatibles(toner.trim());
        for (Object[] row : queryRes) {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("marca", row[0] != null ? row[0].toString() : "");
            map.put("modelo", row[1] != null ? row[1].toString() : "");
            result.add(map);
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}




