package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.CitaEnlace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitaEnlaceRepository extends JpaRepository<CitaEnlace, UUID> {

    @EntityGraph(attributePaths = {"organizacion", "cita", "cita.organizacion", "cita.paciente",
            "cita.medico", "cita.consultorio"})
    Optional<CitaEnlace> findByTokenHash(String tokenHash);

    List<CitaEnlace> findByCitaIdAndUsadoEnIsNull(UUID citaId);

    /**
     * Marca el enlace como usado solo si nadie lo usó antes (operación atómica: dos clics
     * simultáneos no pueden consumir el mismo token). Devuelve 1 si lo consumió, 0 si ya estaba usado.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CitaEnlace e SET e.usadoEn = :ahora, e.accion = :accion WHERE e.id = :id AND e.usadoEn IS NULL")
    int consumir(@Param("id") UUID id, @Param("accion") String accion, @Param("ahora") OffsetDateTime ahora);
}
