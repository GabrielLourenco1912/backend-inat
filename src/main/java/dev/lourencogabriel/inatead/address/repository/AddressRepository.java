package dev.lourencogabriel.inatead.address.repository;

import dev.lourencogabriel.inatead.address.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, String> {
}
