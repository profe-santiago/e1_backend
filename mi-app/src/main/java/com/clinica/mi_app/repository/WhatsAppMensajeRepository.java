package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.WhatsAppMensaje;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppMensajeRepository extends JpaRepository<WhatsAppMensaje, UUID> {

    boolean existsByWamid(String wamid);

    Optional<WhatsAppMensaje> findFirstByWamid(String wamid);

    // Webhooks públicos: el número del negocio es de la plataforma, se busca por teléfono en todas las orgs
    @EntityGraph(attributePaths = {"organizacion", "cita"})
    List<WhatsAppMensaje> findByTelefonoAndDireccionOrderByCreatedAtDesc(String telefono, String direccion);

    // Simulador y consultas: siempre acotado a la organización del JWT
    @EntityGraph(attributePaths = {"organizacion", "cita"})
    List<WhatsAppMensaje> findByOrganizacionIdAndTelefonoAndDireccionOrderByCreatedAtDesc(
            UUID organizacionId, String telefono, String direccion);

    @EntityGraph(attributePaths = {"cita", "cita.paciente"})
    List<WhatsAppMensaje> findByOrganizacionIdAndTelefonoOrderByCreatedAtAsc(UUID organizacionId, String telefono);

    @EntityGraph(attributePaths = {"cita", "cita.paciente"})
    List<WhatsAppMensaje> findByOrganizacionIdAndCitaIdOrderByCreatedAtAsc(UUID organizacionId, UUID citaId);

    @EntityGraph(attributePaths = {"cita", "cita.paciente"})
    List<WhatsAppMensaje> findTop500ByOrganizacionIdOrderByCreatedAtDesc(UUID organizacionId);

    List<WhatsAppMensaje> findByOrganizacionIdAndCitaIdInAndDireccion(
            UUID organizacionId, Collection<UUID> citaIds, String direccion);
}
