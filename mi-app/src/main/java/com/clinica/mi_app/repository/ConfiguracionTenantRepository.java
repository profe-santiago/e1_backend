package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.ConfiguracionTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfiguracionTenantRepository extends JpaRepository<ConfiguracionTenant, UUID> {
    Optional<ConfiguracionTenant> findByOrganizacionId(UUID organizacionId);
}
