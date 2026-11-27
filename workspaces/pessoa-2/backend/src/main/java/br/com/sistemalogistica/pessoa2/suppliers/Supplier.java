package br.com.sistemalogistica.pessoa2.suppliers;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(name = "contact_email", length = 254)
	private String contactEmail;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Supplier() {
	}

	public Supplier(String name, String contactEmail, UUID storeId, boolean active) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.contactEmail = contactEmail;
		this.storeId = storeId;
		this.active = active;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void update(String name, String contactEmail, UUID storeId, boolean active) {
		this.name = name;
		this.contactEmail = contactEmail;
		this.storeId = storeId;
		this.active = active;
	}
}
