package br.com.sistemalogistica.pessoa2.origins;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressSnapshot(
		@NotBlank @Size(max = 200) String line1,
		@Size(max = 100) String line2,
		@NotBlank @Size(max = 100) String city,
		@NotBlank @Pattern(regexp = "[A-Z]{2}") String state,
		@Pattern(regexp = "\\d{8}|\\d{5}-\\d{3}") String postalCode,
		@NotBlank @Pattern(regexp = "BR") String country,
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {
}
