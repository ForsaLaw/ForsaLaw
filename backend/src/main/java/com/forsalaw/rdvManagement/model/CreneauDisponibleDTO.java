package com.forsalaw.rdvManagement.model;

import java.time.OffsetDateTime;

public record CreneauDisponibleDTO(
        OffsetDateTime debut,
        OffsetDateTime fin
) {}
