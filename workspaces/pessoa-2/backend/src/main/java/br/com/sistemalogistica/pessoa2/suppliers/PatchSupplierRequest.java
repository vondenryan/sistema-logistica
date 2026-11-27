package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSetter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PatchSupplierRequest {

	@Pattern(regexp = "(?s).*\\S.*", message = "Must not be blank.")
	@Size(max = 160)
	private String name;

	@Email
	@Size(max = 254)
	private String contactEmail;

	private UUID storeId;

	private Boolean active;

	public PatchSupplierRequest() {
	}

	public PatchSupplierRequest(String name, String contactEmail, UUID storeId, Boolean active) {
		this.name = name;
		this.contactEmail = contactEmail;
		this.storeId = storeId;
		this.active = active;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String contactEmail() {
		return contactEmail;
	}

	@JsonSetter("contactEmail")
	public void setContactEmail(String contactEmail) {
		if (contactEmail == null) {
			throw new IllegalArgumentException("contactEmail must not be null.");
		}
		this.contactEmail = contactEmail;
	}

	public UUID storeId() {
		return storeId;
	}

	public void setStoreId(UUID storeId) {
		this.storeId = storeId;
	}

	public Boolean active() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}
}
