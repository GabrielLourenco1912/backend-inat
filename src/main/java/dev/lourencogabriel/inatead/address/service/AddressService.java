package dev.lourencogabriel.inatead.address.service;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.address.model.Address;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AddressService {

    public Address apply(Address current, AddressDtos.Request request) {
        Address address = current == null ? new Address() : current;
        address.setPostalCode(request.postalCode());
        address.setStreet(request.street().trim());
        address.setStreetNumber(request.streetNumber().trim());
        address.setAddressLine2(normalizeNullable(request.addressLine2()));
        address.setDistrict(request.district().trim());
        address.setCity(request.city().trim());
        address.setStateCode(request.stateCode().toUpperCase(Locale.ROOT));
        address.setCountryCode(request.countryCode().toUpperCase(Locale.ROOT));
        return address;
    }

    public AddressDtos.Response toResponse(Address entity) {
        return new AddressDtos.Response(
                entity.getId(),
                entity.getPostalCode(),
                entity.getStreet(),
                entity.getStreetNumber(),
                entity.getAddressLine2(),
                entity.getDistrict(),
                entity.getCity(),
                entity.getStateCode(),
                entity.getCountryCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
