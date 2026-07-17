package com.techcorp.almacen.proyecto.AlonsoFeria;

import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Maquina;
import com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Pieza;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.MaquinaRepository;
import com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.PiezaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApplicationTests {

	@Autowired
	private MaquinaRepository maquinaRepository;

	@Autowired
	private PiezaRepository piezaRepository;

	@Autowired
	private jakarta.persistence.EntityManager entityManager;

	@Test
	void contextLoads() {
	}


	@Test
	@SuppressWarnings("unchecked")
	void printTableColumns() {
		try {
			java.util.List<Object> maquinasCols = entityManager.createNativeQuery(
					"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'almacen_techcorp' AND TABLE_NAME = 'maquinas'")
					.getResultList();
			System.out.println("COLUMNAS DE MAQUINAS: " + maquinasCols);

			java.util.List<Object> piezasCols = entityManager.createNativeQuery(
					"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'almacen_techcorp' AND TABLE_NAME = 'piezas'")
					.getResultList();
			System.out.println("COLUMNAS DE PIEZAS: " + piezasCols);

			java.util.List<Object> tonersCols = entityManager.createNativeQuery(
					"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'almacen_techcorp' AND TABLE_NAME = 'toners'")
					.getResultList();
			System.out.println("COLUMNAS DE TONERS: " + tonersCols);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	@Test
	void testEscrituraBaseDatos() {
		// Creamos un código QR único para evitar colisiones en pruebas
		String qrUnico = "TEST-QR-" + System.currentTimeMillis();
		String numSerieUnico = "SERIE-" + System.currentTimeMillis();

		Maquina maquina = new Maquina();
		maquina.setCodigoQr(qrUnico);
		maquina.setMarca("HP");
		maquina.setModelo("LaserJet Pro");
		maquina.setNumeroSerie(numSerieUnico);
		maquina.setNumeroCopias(1500);
		maquina.setClienteProcedencia("Cliente de Prueba SL");
		maquina.setFechaEntrada(LocalDate.now());
		maquina.setEstadoFuncionamiento("Funcional");
		maquina.setAveriasCodigos("Ninguna");
		maquina.setDecisionTecnica("Almacenar");
		maquina.setUbicacionFisica("Estantería de pruebas");
		maquina.setObservaciones("Registro creado automáticamente por test de integración.");

		// Guardamos la máquina en la base de datos
		Maquina guardada = maquinaRepository.save(maquina);
		assertNotNull(guardada.getId(), "El ID autogenerado no debería ser nulo.");

		// Recuperamos la máquina por código QR para validar la persistencia
		Optional<Maquina> recuperadaOpt = maquinaRepository.findByCodigoQr(qrUnico);
		assertTrue(recuperadaOpt.isPresent(), "La máquina debería poder recuperarse de la base de datos.");
		Maquina recuperada = recuperadaOpt.get();

		assertEquals("HP", recuperada.getMarca());
		assertEquals("LaserJet Pro", recuperada.getModelo());
		assertEquals(numSerieUnico, recuperada.getNumeroSerie());
		assertEquals(1500, recuperada.getNumeroCopias());
		assertEquals("Cliente de Prueba SL", recuperada.getClienteProcedencia());

		// Limpiamos los datos del test para mantener limpia la base de datos
		maquinaRepository.delete(recuperada);

		// Confirmamos la eliminación
		Optional<Maquina> eliminadaOpt = maquinaRepository.findByCodigoQr(qrUnico);
		assertFalse(eliminadaOpt.isPresent(), "La máquina debería haber sido eliminada tras la limpieza.");
	}

	@Test
	void testEscrituraPieza() {
		// Creamos una máquina de origen para satisfacer la Foreign Key (numero_serie)
		String qrMaqUnico = "TEST-QR-MAQ-ORIGEN-" + System.currentTimeMillis();
		String numSerieOrigen = "SERIE-ORIGEN-" + System.currentTimeMillis();

		Maquina maquinaOrigen = new Maquina();
		maquinaOrigen.setCodigoQr(qrMaqUnico);
		maquinaOrigen.setMarca("HP");
		maquinaOrigen.setModelo("LaserJet Pro");
		maquinaOrigen.setNumeroSerie(numSerieOrigen);
		maquinaOrigen.setNumeroCopias(100);
		maquinaOrigen.setClienteProcedencia("Cliente techcorp");
		maquinaOrigen.setFechaEntrada(LocalDate.now());
		maquinaOrigen.setEstadoFuncionamiento("Funcional");
		maquinaOrigen.setAveriasCodigos("Ninguna");
		maquinaOrigen.setDecisionTecnica("Almacenar");
		maquinaOrigen.setUbicacionFisica("Taller");
		maquinaOrigen.setObservaciones("Maquina para test de FK");

		Maquina maquinaGuardada = maquinaRepository.save(maquinaOrigen);
		assertNotNull(maquinaGuardada.getId());

		// Creamos la pieza asociada a dicha máquina de origen
		String qrPiezaUnico = "TEST-QR-PIEZA-" + System.currentTimeMillis();

		Pieza pieza = new Pieza();
		pieza.setCodigoQrPieza(qrPiezaUnico);
		pieza.setTipoPieza("Fusor");
		pieza.setReferencia("REF-12345");
		pieza.setMarcaModeloCompatible("HP LaserJet Pro");
		pieza.setNumeroSerieMaquinaOrigen(numSerieOrigen); // Relacionada con la máquina creada arriba
		pieza.setNivelEstado(1);
		pieza.setProcedenciaEstadoMaquina("Funcionamiento");
		pieza.setCodigoAveriaMaquina("Ninguna");
		pieza.setRelacionAveriaPieza("Sin relación aparente");
		pieza.setUbicacionFisica("Cajón A-1");
		pieza.setFechaAlta(LocalDate.now());
		pieza.setObservaciones("Registro creado automáticamente por test de integración de pieza.");

		// Guardamos la pieza en la base de datos
		Pieza guardada = piezaRepository.save(pieza);
		assertNotNull(guardada.getId(), "El ID de la pieza no debería ser nulo.");

		// Recuperamos la pieza por ID para validar
		Optional<Pieza> recuperadaOpt = piezaRepository.findById(guardada.getId());
		assertTrue(recuperadaOpt.isPresent(), "La pieza debería poder recuperarse de la base de datos.");
		Pieza recuperada = recuperadaOpt.get();

		assertEquals("Fusor", recuperada.getTipoPieza());
		assertEquals("REF-12345", recuperada.getReferencia());
		assertEquals("Cajón A-1", recuperada.getUbicacionFisica());
		assertEquals(1, recuperada.getNivelEstado());
		assertEquals("Funcionamiento", recuperada.getProcedenciaEstadoMaquina()); // Debe recuperarse como
																					// "Funcionamiento" gracias al
																					// converter

		// Limpiamos
		piezaRepository.delete(recuperada);
		maquinaRepository.delete(maquinaGuardada);

		// Confirmamos la eliminación
		Optional<Pieza> eliminadaOpt = piezaRepository.findById(guardada.getId());
		assertFalse(eliminadaOpt.isPresent(), "La pieza debería haber sido eliminada.");
	}

	@Autowired
	private com.techcorp.almacen.proyecto.AlonsoFeria.servicio.MaquinaService maquinaService;

	@Test
	void testValidacionComercialYExcepcion() {
		String qrMaq = "TEST-VAL-COMM-" + System.currentTimeMillis();
		String numSerie = "SERIE-VAL-COMM-" + System.currentTimeMillis();

		Maquina maquina = new Maquina();
		maquina.setCodigoQr(qrMaq);
		maquina.setMarca("HP");
		maquina.setModelo("LaserJet");
		maquina.setNumeroSerie(numSerie);
		maquina.setEstadoVisual("Deficiente"); // No es óptimo

		Maquina guardada = maquinaRepository.save(maquina);
		assertNotNull(guardada.getId());

		// 1. Intentar marcar como preparada sin comentario de excepción (debe lanzar error)
		assertThrows(IllegalArgumentException.class, () -> {
			maquinaService.marcarComoPreparada(guardada.getId(), "Técnico Juan", null);
		}, "Debería fallar porque el estado visual es Deficiente.");

		// 2. Forzar excepción mediante un comentario
		Maquina preparadaExcepcion = maquinaService.marcarComoPreparada(guardada.getId(), "Técnico Juan", "Autorizado por desgaste aceptable para venta secundaria");
		assertNotNull(preparadaExcepcion);
		assertTrue(preparadaExcepcion.getPreparadaComercial());
		assertEquals("Autorizado por desgaste aceptable para venta secundaria", preparadaExcepcion.getComentarioExcepcion());

		// Limpiar
		maquinaRepository.delete(preparadaExcepcion);
	}

	@Autowired
	private com.techcorp.almacen.proyecto.AlonsoFeria.repositorio.TonerRepository tonerRepository;

	@Autowired
	private com.techcorp.almacen.proyecto.AlonsoFeria.servicio.TonerService tonerService;

	@Test
	void testDevolucionComercialYNotificacion() {
		String qrMaq = "TEST-DEV-COMM-" + System.currentTimeMillis();
		String numSerie = "SERIE-DEV-COMM-" + System.currentTimeMillis();

		Maquina maquina = new Maquina();
		maquina.setCodigoQr(qrMaq);
		maquina.setMarca("Brother");
		maquina.setModelo("HL-L2350DW");
		maquina.setNumeroSerie(numSerie);
		maquina.setPreparadaComercial(true);

		Maquina guardada = maquinaRepository.save(maquina);
		assertNotNull(guardada.getId());

		// 1. Devolver al almacén (revocar) indicando motivo
		Maquina devuelta = maquinaService.revocarDeComercial(guardada.getId(), "El cliente canceló la compra");
		assertNotNull(devuelta);
		assertFalse(devuelta.getPreparadaComercial());
		assertEquals("El cliente canceló la compra", devuelta.getMotivoDevolucion());

		// 2. Limpiar la notificación (marcar como leída)
		Maquina leida = maquinaService.limpiarDevolucion(guardada.getId());
		assertNotNull(leida);
		assertNull(leida.getMotivoDevolucion());

		// Limpiar base de datos
		maquinaRepository.delete(leida);
	}

	@Autowired
	private javax.sql.DataSource dataSource;

	@Test
	void addReservationColumnsToMaquinas() throws Exception {
		try (java.sql.Connection conn = dataSource.getConnection();
			 java.sql.Statement stmt = conn.createStatement()) {
			stmt.execute("ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS estado_venta VARCHAR(50) NULL");
			stmt.execute("ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS observacion_venta TEXT NULL");
			stmt.execute("ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS comercial_reserva VARCHAR(100) NULL");
			stmt.execute("ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS copias_bn INT NULL");
			stmt.execute("ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS copias_color INT NULL");
			System.out.println("✅ Columnas de reserva y contadores de copias añadidas correctamente a 'maquinas'");

			java.sql.ResultSet rs = stmt.executeQuery(
				"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
				"WHERE TABLE_SCHEMA='almacen_techcorp' AND TABLE_NAME='maquinas' " +
				"AND COLUMN_NAME IN ('estado_venta','observacion_venta','comercial_reserva','copias_bn','copias_color') ORDER BY COLUMN_NAME");
			java.util.List<String> cols = new java.util.ArrayList<>();
			while (rs.next()) cols.add(rs.getString(1));
			System.out.println("Columnas encontradas: " + cols);
			assertEquals(5, cols.size(), "Deben existir las 5 columnas nuevas");
		}
	}


	@Test
	void testBuscarToner() {
		com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Toner t1 = new com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Toner();
		t1.setCodigoQr("TNR-TEST-123");
		t1.setModelo("Brother TN2420");
		t1.setNivelToner("50%");
		t1.setUbicacionFisica("Estantería Toner C1");
		t1.setEstado("Disponible");
		t1.setFechaRegistro(LocalDate.now());

		tonerRepository.save(t1);

		// Buscar por modelo (case-insensitive)
		java.util.List<com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Toner> resModelo = tonerService.buscar("tn24");
		assertFalse(resModelo.isEmpty());
		assertEquals("TNR-TEST-123", resModelo.get(0).getCodigoQr());

		// Buscar por QR (case-insensitive)
		java.util.List<com.techcorp.almacen.proyecto.AlonsoFeria.modelo.Toner> resQr = tonerService.buscar("test-123");
		assertFalse(resQr.isEmpty());

		// Limpiar
		tonerRepository.delete(t1);
	}

}



