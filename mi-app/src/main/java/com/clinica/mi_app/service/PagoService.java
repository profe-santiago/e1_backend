package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.request.PagoRequest;
import com.clinica.mi_app.dto.response.PagoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.PagoMapper;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Pago;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.PagoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagoService {

    private final PagoRepository repo;
    private final OrganizacionRepository orgRepo;
    private final CitaRepository citaRepo;
    private final ClinicaReloj reloj;

    public PagoService(PagoRepository repo, OrganizacionRepository orgRepo, CitaRepository citaRepo,
                       ClinicaReloj reloj) {
        this.repo = repo;
        this.orgRepo = orgRepo;
        this.citaRepo = citaRepo;
        this.reloj = reloj;
    }

    /** Pagos de una cita: lista vacía si no hay ninguno (nunca 404). */
    public List<PagoResponse> listarPorCita(UUID citaId) {
        return repo.findByCitaId(citaId).stream().map(PagoMapper::toResponse).collect(Collectors.toList());
    }

    public List<PagoResponse> listarPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionId(organizacionId).stream().map(PagoMapper::toResponse).collect(Collectors.toList());
    }

    public List<PagoResponse> listarPorEstado(UUID organizacionId, String estado) {
        return repo.findByOrganizacionIdAndEstado(organizacionId, estado).stream().map(PagoMapper::toResponse).collect(Collectors.toList());
    }

    public PagoResponse buscarPorId(UUID id) {
        return repo.findById(id).map(PagoMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id.toString()));
    }

    public PagoResponse crear(PagoRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organización", req.getOrganizacionId().toString()));
        Cita cita = citaRepo.findById(req.getCitaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita", req.getCitaId().toString()));
        Pago p = new Pago();
        p.setOrganizacion(org);
        p.setCita(cita);
        p.setMonto(req.getMonto());
        p.setMetodo(req.getMetodo());
        p.setConcepto(req.getConcepto());
        p.setReferencia(req.getReferencia());
        aplicarEstado(p, req.getEstado());
        return PagoMapper.toResponse(repo.save(p));
    }

    public PagoResponse actualizar(UUID id, PagoRequest req) {
        Pago p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id.toString()));
        p.setMonto(req.getMonto());
        p.setMetodo(req.getMetodo());
        p.setConcepto(req.getConcepto());
        p.setReferencia(req.getReferencia());
        aplicarEstado(p, req.getEstado());
        return PagoMapper.toResponse(repo.save(p));
    }

    /**
     * Cambia el estado del pago. Al quedar PAGADO sin pagadoEn, se registra la hora actual
     * (hora de reloj de la clínica en UTC, misma convención que cita.fechaHora).
     * Al volver a PENDIENTE se limpia pagadoEn.
     */
    private void aplicarEstado(Pago p, String estado) {
        if (estado == null || estado.isBlank()) {
            return;
        }
        p.setEstado(estado);
        if ("PAGADO".equals(estado) && p.getPagadoEn() == null) {
            p.setPagadoEn(reloj.ahora());
        } else if ("PENDIENTE".equals(estado)) {
            p.setPagadoEn(null);
        }
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }
}
