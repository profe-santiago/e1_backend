package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.Adjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdjuntoRepository extends JpaRepository<Adjunto, UUID> {
    List<Adjunto> findByPacienteId(UUID pacienteId);
    List<Adjunto> findByPacienteIdOrderByCreatedAtDesc(UUID pacienteId);
    List<Adjunto> findByCitaId(UUID citaId);
    List<Adjunto> findByOrganizacionId(UUID organizacionId);
    List<Adjunto> findByOrganizacionIdAndNotificarTrue(UUID organizacionId);
}