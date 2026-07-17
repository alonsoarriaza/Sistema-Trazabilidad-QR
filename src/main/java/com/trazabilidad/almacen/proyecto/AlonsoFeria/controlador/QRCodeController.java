package com.trazabilidad.almacen.proyecto.AlonsoFeria.controlador;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.trazabilidad.almacen.proyecto.AlonsoFeria.servicio.QRCodeService;

@RestController
public class QRCodeController {

    @Autowired
    private QRCodeService qrCodeService;

    @GetMapping("/v1/qrcode")
    public void generateQRCode(HttpServletResponse response,
                               @RequestParam String text,
                               @RequestParam(defaultValue = "128") int width,
                               @RequestParam(defaultValue = "128") int height) throws Exception {
        BufferedImage image = qrCodeService.generateQRCode(text, width, height);
        response.setContentType("image/png");
        ServletOutputStream outputStream = response.getOutputStream();
        ImageIO.write(image, "png", outputStream);
        outputStream.flush();
        outputStream.close();
    }
}

