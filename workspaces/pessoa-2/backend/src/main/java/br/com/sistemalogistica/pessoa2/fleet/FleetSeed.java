package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import contracts.v1.FleetType;

@Component
@Profile({"dev", "test", "integration-demo & integration"})
public class FleetSeed implements ApplicationRunner {

	private static final UUID CARRIER_ALFA_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
	private static final UUID CARRIER_BETA_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
	private static final UUID DRIVER_1_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final UUID DRIVER_2_ID = UUID.fromString("55555555-5555-4555-8555-555555555556");
	private static final UUID DRIVER_3_ID = UUID.fromString("55555555-5555-4555-8555-555555555557");
	private static final UUID VEHICLE_1_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
	private static final UUID VEHICLE_2_ID = UUID.fromString("66666666-6666-4666-8666-666666666667");
	private static final UUID VEHICLE_3_ID = UUID.fromString("66666666-6666-4666-8666-666666666668");

	private final CarrierRepository carrierRepository;
	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;

	public FleetSeed(
			CarrierRepository carrierRepository,
			DriverRepository driverRepository,
			VehicleRepository vehicleRepository) {
		this.carrierRepository = carrierRepository;
		this.driverRepository = driverRepository;
		this.vehicleRepository = vehicleRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedCarriers();
		seedDrivers();
		seedVehicles();
	}

	private void seedCarriers() {
		if (!carrierRepository.existsById(CARRIER_ALFA_ID)) {
			carrierRepository.saveAndFlush(new Carrier(CARRIER_ALFA_ID, "Transportadora Parceira Alfa", true));
		}
		if (!carrierRepository.existsById(CARRIER_BETA_ID)) {
			carrierRepository.saveAndFlush(new Carrier(CARRIER_BETA_ID, "Transportadora Parceira Beta", true));
		}
	}

	private void seedDrivers() {
		List<Driver> drivers = List.of(
				new Driver(DRIVER_1_ID, "Motorista Próprio 1", null, true),
				new Driver(DRIVER_2_ID, "Motorista Próprio 2", null, true),
				new Driver(DRIVER_3_ID, "Motorista Terceirizado 1", CARRIER_ALFA_ID, true));
		drivers.stream()
				.filter(driver -> !driverRepository.existsById(driver.getId()))
				.forEach(driverRepository::saveAndFlush);
	}

	private void seedVehicles() {
		List<Vehicle> vehicles = List.of(
				new Vehicle(VEHICLE_1_ID, "VEI-001", new BigDecimal("1000.000"), FleetType.OWN, null, true),
				new Vehicle(VEHICLE_2_ID, "VEI-002", new BigDecimal("500.000"), FleetType.OWN, null, true),
				new Vehicle(
						VEHICLE_3_ID,
						"TER-001",
						new BigDecimal("2000.000"),
						FleetType.THIRD_PARTY,
						CARRIER_ALFA_ID,
						true));
		vehicles.stream()
				.filter(vehicle -> !vehicleRepository.existsById(vehicle.getId()))
				.forEach(vehicleRepository::saveAndFlush);
	}
}
