package com.rentalops.common;

import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Small helpers for building optional list filters with JPA Specifications. */
public final class SpecFilters {
    private SpecFilters() {
    }

    public static <T> Specification<T> combine(List<Specification<T>> specs) {
        return specs.stream().reduce(Specification::and).orElse(null);
    }

    /** Wrap a value for a case-insensitive LIKE (`%value%`). */
    public static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    public static boolean has(String value) {
        return value != null && !value.isBlank();
    }
}
