package br.com.sistemalogistica.pessoa2.contracts.v1;

public interface FleetAssignmentPort {

	AssignmentSnapshot assign(AssignmentRequest request);
}
