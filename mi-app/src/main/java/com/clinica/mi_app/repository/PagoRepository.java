package com.clinica.mi_app.repository;

import com.clinica.mi_app.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface PagoRepository extends JpaRepository<Pago, UUID> {
    List<Pago> findByCitaId(UUID citaId);
    List<Pago> findByOrganizacionId(UUID organizacionId);
    List<Pago> findByOrganizacionIdAndEstado(UUID organizacionId, String estado);

    /**
     * Pagos PAGADO cuya fecha efectiva COALESCE(pagadoEn, cita.fechaHora) cae en [desde, hasta).
     * Trae cita y médico para agrupar sin consultas N+1.
     */
    @Query("select p from Pago p join fetch p.cita c join fetch c.medico " +
           "where p.organizacion.id = :orgId and p.estado = 'PAGADO' " +
           "and coalesce(p.pagadoEn, c.fechaHora) >= :desde " +
           "and coalesce(p.pagadoEn, c.fechaHora) < :hasta")
    List<Pago> findPagadosEnRango(@Param("orgId") UUID organizacionId,
                                  @Param("desde") OffsetDateTime desde,
                                  @Param("hasta") OffsetDateTime hasta);
}
