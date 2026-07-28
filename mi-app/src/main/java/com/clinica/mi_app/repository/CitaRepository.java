package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.Cita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitaRepository extends JpaRepository<Cita, UUID> {
    List<Cita> findByOrganizacionId(UUID organizacionId);
    List<Cita> findByMedicoId(UUID medicoId);
    List<Cita> findByPacienteId(UUID pacienteId);
    List<Cita> findByOrganizacionIdAndEstado(UUID organizacionId, String estado);
    List<Cita> findByPacienteIdAndEstado(UUID pacienteId, String estado);
    List<Cita> findByMedicoIdAndFechaHoraBetween(UUID medicoId, OffsetDateTime inicio, OffsetDateTime fin);
    List<Cita> findByFechaHoraBetweenAndEstadoNot(OffsetDateTime desde, OffsetDateTime hasta, String estadoExcluido);

    Optional<Cita> findFirstByPaciente_TelefonoAndEstadoAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
            String telefono, String estado, OffsetDateTime desde);
}