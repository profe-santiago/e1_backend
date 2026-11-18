package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.Notificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {
    List<Notificacion> findByCitaId(UUID citaId);
    List<Notificacion> findByOrganizacionIdAndEstado(UUID organizacionId, String estado);
    List<Notificacion> findByEstado(String estado);

    List<Notificacion> findByOrganizacionIdAndCitaIdIn(UUID organizacionId, Collection<UUID> citaIds);

    // Campana de notificaciones (admin: toda la organización; médico: solo sus citas)
    @EntityGraph(attributePaths = {"cita", "cita.paciente", "cita.medico"})
    List<Notificacion> findTop20ByOrganizacionIdAndTipoOrderByCreadaEnDesc(UUID organizacionId, String tipo);

    @EntityGraph(attributePaths = {"cita", "cita.paciente", "cita.medico"})
    List<Notificacion> findTop20ByOrganizacionIdAndTipoAndCita_Medico_IdOrderByCreadaEnDesc(
            UUID organizacionId, String tipo, UUID medicoId);

    long countByOrganizacionIdAndTipoAndLeidaFalse(UUID organizacionId, String tipo);

    // Vista "Actividad": últimos avisos por organización (y opcionalmente por médico)
    @EntityGraph(attributePaths = {"cita", "cita.paciente", "cita.medico"})
    List<Notificacion> findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(
            UUID organizacionId, Collection<String> tipos, java.time.OffsetDateTime desde);

    @EntityGraph(attributePaths = {"cita", "cita.paciente", "cita.medico"})
    List<Notificacion> findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualAndCita_Medico_IdOrderByCreadaEnDesc(
            UUID organizacionId, Collection<String> tipos, java.time.OffsetDateTime desde, UUID medicoId);

    // Campana con varios tipos (cancelaciones y confirmaciones del paciente)
    @EntityGraph(attributePaths = {"cita", "cita.paciente", "cita.medico"})
    List<Notificacion> findTop20ByOrganizacionIdAndTipoInOrderByCreadaEnDesc(UUID organizacionId, Collection<String> tipos);

    @EntityGraph(attributePaths = {"cita", "cita.paciente", "cita.medico"})
    List<Notificacion> findTop20ByOrganizacionIdAndTipoInAndCita_Medico_IdOrderByCreadaEnDesc(
            UUID organizacionId, Collection<String> tipos, UUID medicoId);

    long countByOrganizacionIdAndTipoInAndLeidaFalse(UUID organizacionId, Collection<String> tipos);

    long countByOrganizacionIdAndTipoInAndCita_Medico_IdAndLeidaFalse(UUID organizacionId, Collection<String> tipos, UUID medicoId);

    List<Notificacion> findByOrganizacionIdAndTipoInAndLeidaFalse(UUID organizacionId, Collection<String> tipos);

    List<Notificacion> findByOrganizacionIdAndTipoInAndCita_Medico_IdAndLeidaFalse(
            UUID organizacionId, Collection<String> tipos, UUID medicoId);

    long countByOrganizacionIdAndTipoAndCita_Medico_IdAndLeidaFalse(UUID organizacionId, String tipo, UUID medicoId);

    List<Notificacion> findByOrganizacionIdAndTipoAndLeidaFalse(UUID organizacionId, String tipo);

    List<Notificacion> findByOrganizacionIdAndTipoAndCita_Medico_IdAndLeidaFalse(
            UUID organizacionId, String tipo, UUID medicoId);
}