package br.com.sistemalogistica.pessoa2.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FleetSeedPersistenceTests {

	private static final UUID CARRIER_ALFA_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
	private static final UUID DRIVER_ID = UUID.fromString("55555555-5555-4555-8555-555555555557");
	private static final UUID VEHICLE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");

	@Autowired
	private CarrierRepository carrierRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private FleetSeed fleetSeed;

	@Test
	void testProfileSeedsFixedBaseRecordsAndRerunningIsIdempotent() throws Exception {
		Carrier carrier = carrierRepository.findById(CARRIER_ALFA_ID).orElseThrow();
		Driver driver = driverRepository.findById(DRIVER_ID).orElseThrow();
		Vehicle vehicle = vehicleRepository.findById(VEHICLE_ID).orElseThrow();

		assertEquals("Transportadora Parceira Alfa", carrier.getName());
		assertEquals(CARRIER_ALFA_ID, driver.getCarrierId());
		assertEquals("VEI-001", vehicle.getIdentifier());
		assertEquals("1000.000", vehicle.getCapacityKg().toPlainString());

		ApplicationArguments args = new DefaultApplicationArguments(new String[0]);
		fleetSeed.run(args);

		assertTrue(carrierRepository.findById(CARRIER_ALFA_ID).isPresent());
		assertTrue(driverRepository.findById(DRIVER_ID).isPresent());
		assertTrue(vehicleRepository.findById(VEHICLE_ID).isPresent());
	}
}
