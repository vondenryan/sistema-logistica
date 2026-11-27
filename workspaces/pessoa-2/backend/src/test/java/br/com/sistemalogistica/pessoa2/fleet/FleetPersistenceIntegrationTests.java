package br.com.sistemalogistica.pessoa2.fleet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.contracts.v1.FleetType;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class FleetPersistenceIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CarrierService carrierService;

	@Autowired
	private DriverService driverService;

	@Autowired
	private VehicleService vehicleService;

	@Test
	void persistsAndReadsFleetResourcesWithOptionalCarrierIds() throws Exception {
		String suffix = UUID.randomUUID().toString();
		CarrierResponse carrier = carrierService.create(new CarrierCreateRequest("Carrier " + suffix, true));
		DriverResponse ownDriver = driverService.create(new DriverCreateRequest("Driver " + suffix, null, true));
		DriverResponse thirdPartyDriver = driverService.create(
				new DriverCreateRequest("Contract Driver " + suffix, carrier.id(), true));
		VehicleResponse ownVehicle = vehicleService.create(new VehicleCreateRequest(
				"V-" + suffix,
				new BigDecimal("1250.500"),
				FleetType.OWN,
				null,
				true));
		VehicleResponse thirdPartyVehicle = vehicleService.create(new VehicleCreateRequest(
				"T-" + suffix,
				new BigDecimal("2000.000"),
				FleetType.THIRD_PARTY,
				carrier.id(),
				true));

		mockMvc.perform(get("/api/v1/carriers/{id}", carrier.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));
		mockMvc.perform(get("/api/v1/drivers/{id}", ownDriver.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.carrierId").doesNotExist())
				.andExpect(jsonPath("$.active").value(true));
		mockMvc.perform(get("/api/v1/drivers/{id}", thirdPartyDriver.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.carrierId").value(carrier.id().toString()));
		mockMvc.perform(get("/api/v1/vehicles/{id}", ownVehicle.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.capacityKg").value("1250.500"))
				.andExpect(jsonPath("$.carrierId").doesNotExist())
				.andExpect(jsonPath("$.active").value(true));
		mockMvc.perform(get("/api/v1/vehicles/{id}", thirdPartyVehicle.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fleetType").value("THIRD_PARTY"))
				.andExpect(jsonPath("$.carrierId").value(carrier.id().toString()));

		mockMvc.perform(patch("/api/v1/vehicles/{id}", ownVehicle.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fleetType\":\"THIRD_PARTY\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(patch("/api/v1/vehicles/{id}", ownVehicle.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fleetType\":\"THIRD_PARTY\",\"carrierId\":\"" + carrier.id() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fleetType").value("THIRD_PARTY"))
				.andExpect(jsonPath("$.carrierId").value(carrier.id().toString()));

		mockMvc.perform(get("/api/v1/vehicles").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1));

		mockMvc.perform(patch("/api/v1/vehicles/{id}", thirdPartyVehicle.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fleetType\":\"OWN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fleetType").value("OWN"))
				.andExpect(jsonPath("$.carrierId").doesNotExist());

		mockMvc.perform(patch("/api/v1/carriers/{id}", carrier.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void appliesOnlyContractDefinedConflictsAndNotFoundRules() {
		String suffix = UUID.randomUUID().toString();
		String carrierName = "Unique Carrier " + suffix;
		CarrierResponse carrier = carrierService.create(new CarrierCreateRequest(carrierName, true));
		CarrierResponse otherCarrier =
				carrierService.create(new CarrierCreateRequest("Other Carrier " + suffix, true));

		ContractException duplicateCarrier = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> carrierService.create(new CarrierCreateRequest(carrierName.toUpperCase(), true)));
		org.junit.jupiter.api.Assertions.assertEquals("CONFLICT", duplicateCarrier.getCode());
		ContractException duplicateCarrierRename = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> carrierService.patch(
						otherCarrier.id(),
						new CarrierPatchRequest(carrierName.toUpperCase(), null)));
		org.junit.jupiter.api.Assertions.assertEquals("CONFLICT", duplicateCarrierRename.getCode());

		String identifier = "V-" + suffix;
		vehicleService.create(new VehicleCreateRequest(
				identifier, new BigDecimal("100.000"), FleetType.OWN, null, true));
		VehicleResponse secondVehicle = vehicleService.create(new VehicleCreateRequest(
				"T-" + suffix, new BigDecimal("100.000"), FleetType.OWN, null, true));
		ContractException duplicateVehicle = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> vehicleService.create(new VehicleCreateRequest(
						identifier, new BigDecimal("100.000"), FleetType.OWN, null, true)));
		org.junit.jupiter.api.Assertions.assertEquals("CONFLICT", duplicateVehicle.getCode());
		ContractException duplicateVehicleRename = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> vehicleService.patch(secondVehicle.id(), new VehiclePatchRequest(
						identifier, null, null, null, false, null)));
		org.junit.jupiter.api.Assertions.assertEquals("CONFLICT", duplicateVehicleRename.getCode());

		driverService.create(new DriverCreateRequest("Same Name " + suffix, null, true));
		driverService.create(new DriverCreateRequest("Same Name " + suffix, null, true));

		UUID missingCarrier = UUID.randomUUID();
		ContractException missingDriverCarrier = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> driverService.create(new DriverCreateRequest("Missing Carrier", missingCarrier, true)));
		org.junit.jupiter.api.Assertions.assertEquals("NOT_FOUND", missingDriverCarrier.getCode());
		ContractException missingVehicleCarrier = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> vehicleService.create(new VehicleCreateRequest(
						"T-" + suffix, new BigDecimal("100.000"), FleetType.THIRD_PARTY, missingCarrier, true)));
		org.junit.jupiter.api.Assertions.assertEquals("NOT_FOUND", missingVehicleCarrier.getCode());

		ContractException ownVehicleCarrier = org.junit.jupiter.api.Assertions.assertThrows(
				ContractException.class,
				() -> vehicleService.create(new VehicleCreateRequest(
						"T-" + suffix, new BigDecimal("100.000"), FleetType.OWN, carrier.id(), true)));
		org.junit.jupiter.api.Assertions.assertEquals("VALIDATION_ERROR", ownVehicleCarrier.getCode());
	}

	@Test
	void acceptsFortyCharacterIdentifierAndRejectsOutOfRangeIdentifier() throws Exception {
		String identifier = ("V" + UUID.randomUUID().toString().replace("-", "") + "123456789")
				.substring(0, 40);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"" + identifier
								+ "\",\"capacityKg\":\"1.000\",\"fleetType\":\"OWN\",\"active\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.identifier").value(identifier));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"" + "X".repeat(41)
								+ "\",\"capacityKg\":\"1.000\",\"fleetType\":\"OWN\",\"active\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
