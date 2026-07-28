package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.HorarioMedico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioMedicoRepository extends JpaRepository<HorarioMedico, UUID> {
    List<HorarioMedico> findByMedicoId(UUID medicoId);
    List<HorarioMedico> findByMedicoIdAndDiaSemana(UUID medicoId, Short diaSemana);
    boolean existsByMedicoIdAndDiaSemana(UUID medicoId, Short diaSemana);
    boolean existsByMedicoIdAndDiaSemanaAndIdNot(UUID medicoId, Short diaSemana, UUID id);
}