package dev.lourencogabriel.inatead.person.service;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;

public final class PersonTypeRules {

    private PersonTypeRules() {
    }

    public static void requireType(Person person, PersonTypeCode type) {
        if (person == null || !person.hasType(type)) {
            throw new BusinessRuleException("The person must have the " + type + " person type");
        }
    }
}
