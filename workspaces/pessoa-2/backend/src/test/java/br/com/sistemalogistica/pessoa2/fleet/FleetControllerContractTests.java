package br.com.sistemalogistica.pessoa2.fleet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractProblemHandler;

class FleetControllerContractTests {

	private final CarrierService carrierService = org.mockito.Mockito.mock(CarrierService.class);
	private final DriverService driverService = org.mockito.Mockito.mock(DriverService.class);
	private final VehicleService vehicleService = org.mockito.Mockito.mock(VehicleService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
						new CarrierController(carrierService),
						new DriverController(driverService),
						new VehicleController(vehicleService))
				.setControllerAdvice(new ContractProblemHandler())
				.build();
	}

	@Test
	void createsResourcesWithContractResponseFields() throws Exception {
		UUID carrierId = UUID.randomUUID();
		UUID driverId = UUID.randomUUID();
		UUID vehicleId = UUID.randomUUID();
		when(carrierService.create(any(CarrierCreateRequest.class)))
				.thenReturn(new CarrierResponse(carrierId, "Parceira", true));
		when(driverService.create(any(DriverCreateRequest.class)))
				.thenReturn(new DriverResponse(driverId, "Motorista", null, true));
		when(vehicleService.create(any(VehicleCreateRequest.class)))
				.thenReturn(new VehicleResponse(
						vehicleId, "VEI-1", new BigDecimal("1000.000"), FleetType.OWN, null, true));

		mockMvc.perform(post("/api/v1/carriers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Parceira\",\"active\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(carrierId.toString()))
				.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(post("/api/v1/drivers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Motorista\",\"active\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(driverId.toString()))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.carrierId").doesNotExist());

		mockMvc.perform(post("/api/v1/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"VEI-1\",\"capacityKg\":\"1000.000\","
								+ "\"fleetType\":\"OWN\",\"active\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.capacityKg").value("1000.000"))
				.andExpect(jsonPath("$.fleetType").value("OWN"))
				.andExpect(jsonPath("$.carrierId").doesNotExist())
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void returnsPagedEnvelopeAndProblemDetailsForConflicts() throws Exception {
		UUID id = UUID.randomUUID();
		when(carrierService.list(0, 1)).thenReturn(
				new FleetPageResponse<>(List.of(new CarrierResponse(id, "Parceira", true)), 0, 1, 1));
		when(vehicleService.create(any(VehicleCreateRequest.class))).thenThrow(
				new ContractException("CONFLICT", "A vehicle with this identifier already exists."));

		mockMvc.perform(get("/api/v1/carriers").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(id.toString()))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalItems").value(1));

		mockMvc.perform(post("/api/v1/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"VEI-1\",\"capacityKg\":\"1.000\","
								+ "\"fleetType\":\"OWN\",\"active\":true}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void rejectsMalformedPayloadsNullCarrierIdsAndUnknownProperties() throws Exception {
		mockMvc.perform(post("/api/v1/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"VEI-1\",\"capacityKg\":1000,"
								+ "\"fleetType\":\"OWN\",\"active\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"VEI-1\",\"capacityKg\":\"1.000\","
								+ "\"fleetType\":\"OWN\",\"carrierId\":null,\"active\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(patch("/api/v1/drivers/{driverId}", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"carrierId\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("carrierId"));

		mockMvc.perform(post("/api/v1/carriers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Parceira\",\"active\":true,\"unknown\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/drivers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Motorista\",\"carrierId\":null,\"active\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsNonPositiveOverScaleAndOverPrecisionWeights() throws Exception {
		for (String capacity : List.of("\"0.000\"", "\"100.0000\"", "\"1000000000.000\"")) {
			mockMvc.perform(post("/api/v1/vehicles")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"identifier\":\"VEI-1\",\"capacityKg\":" + capacity
									+ ",\"fleetType\":\"OWN\",\"active\":true}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Test
	void invalidUuidPathParametersReturnValidationProblem() throws Exception {
		mockMvc.perform(get("/api/v1/carriers/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(patch("/api/v1/vehicles/not-a-uuid")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsMissingIdentityTo401AndOtherRolesTo403() throws Exception {
		when(carrierService.list(0, 20)).thenThrow(
				new ContractException("UNAUTHENTICATED", "Authentication is required."));
		when(driverService.list(0, 20)).thenThrow(
				new ContractException("FORBIDDEN", "The actor is not allowed to perform this operation."));

		mockMvc.perform(get("/api/v1/carriers"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/drivers"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}
}
