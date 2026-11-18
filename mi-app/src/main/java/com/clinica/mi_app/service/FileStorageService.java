package com.clinica.mi_app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Gestiona la carga de archivos clínicos a Supabase Storage.
 *
 * <p><b>Bucket:</b> {@code adjuntos} (acceso público, sin autenticación para lectura).
 *
 * <p><b>Ruta del objeto:</b> {@code YYYY/MM/{uuid}.ext}
 * — garantiza unicidad y facilita limpieza periódica por mes.
 *
 * <p><b>URL pública:</b>
 * {@code {supabase_url}/storage/v1/object/public/{bucket}/YYYY/MM/{uuid}.ext}
 * — accesible directamente en el navegador sin firma ni token.
 *
 * <p><b>Descarga forzada:</b> añadir {@code ?download={nombre-original}} a la URL.
 *
 * <p><b>HTTP client:</b> usa {@code java.net.http.HttpClient} (JDK 11+) para no
 * depender de WebClient / Reactor y evitar conflictos de auto-configuración entre
 * {@code spring-boot-starter-web} y {@code spring-boot-starter-webflux}.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    // ── Listas blancas ───────────────────────────────────────────────────────

    /** Extensiones aceptadas (minúsculas). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "webp"
    );

    /**
     * MIME Types permitidos, validados de forma independiente a la extensión.
     * Un archivo .jpg enviado con MIME "application/octet-stream" será rechazado.
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",   // algunos navegadores/S.O. usan image/jpg
            "image/png",
            "image/webp"
    );

    // ── Configuración inyectada ──────────────────────────────────────────────

    /** URL base del proyecto Supabase, sin trailing slash. */
    private final String supabaseUrl;

    /** Service-role key para autenticar el upload (no se expone al cliente). */
    private final String serviceKey;

    /** Nombre del bucket donde se almacenan los adjuntos. */
    private final String bucket;

    /** Cliente HTTP nativo de Java (thread-safe, reutilizable). */
    private final HttpClient httpClient;

    public FileStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${supabase.storage.bucket}") String bucket
    ) {
        // Normalizar: quitar trailing slash y /rest/v1 si la URL viene con ellos
        this.supabaseUrl = supabaseUrl
                .replaceAll("/rest/v1/?$", "")
                .replaceAll("/$", "");
        this.serviceKey  = serviceKey;
        this.bucket      = bucket;
        this.httpClient  = HttpClient.newHttpClient();
    }

    // ── DTO de resultado ─────────────────────────────────────────────────────

    /**
     * Resultado de una carga exitosa.
     *
     * @param nombreArchivo Nombre original del archivo (para mostrar al usuario).
     * @param urlArchivo    URL pública HTTPS de Supabase; sirve el archivo real.
     * @param mimeType      Content-Type reportado por el cliente del paciente.
     */
    public record FileUploadResult(String nombreArchivo, String urlArchivo, String mimeType) {}

    // ── Método principal ─────────────────────────────────────────────────────

    /**
     * Valida y sube el archivo a Supabase Storage.
     *
     * @param archivo Archivo recibido vía multipart/form-data.
     * @return {@link FileUploadResult} con nombre, URL pública y MIME del archivo.
     * @throws ResponseStatusException 400 si la extensión o MIME no están permitidos,
     *                                 500 si Supabase falla.
     */
    public FileUploadResult subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo es obligatorio");
        }

        String originalName = archivo.getOriginalFilename() != null
                ? archivo.getOriginalFilename().trim()
                : "archivo";

        // 1. Validar extensión
        String ext = extension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Extensión no permitida. Formatos aceptados: PDF, JPG, PNG, WEBP");
        }

        // 2. Validar MIME (independientemente de la extensión)
        String contentType = archivo.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de archivo no válido. Solo se aceptan: PDF e imágenes JPG, PNG o WEBP");
        }

        // 3. Construir ruta del objeto: YYYY/MM/{uuid}.ext
        LocalDate hoy = LocalDate.now();
        String objectPath = "%d/%02d/%s.%s".formatted(
                hoy.getYear(), hoy.getMonthValue(), UUID.randomUUID(), ext
        );

        // 4. Leer bytes del archivo
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            log.error("[FileStorage] Error leyendo bytes del archivo '{}': {}", originalName, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al leer el archivo: " + e.getMessage());
        }

        // 5. Subir a Supabase Storage
        //    POST /storage/v1/object/{bucket}/{path}
        //    Headers: Authorization: Bearer <service-key>
        //             Content-Type: <mime del archivo>
        //             x-upsert: false  (la ruta es única por UUID; nunca sobreescribir)
        String uploadUrl = "%s/storage/v1/object/%s/%s".formatted(supabaseUrl, bucket, objectPath);

        log.info("[FileStorage] Subiendo a Supabase — archivo: '{}', ext: {}, mime: {}, path: {}",
                originalName, ext, contentType, objectPath);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization",  "Bearer " + serviceKey)
                    .header("Content-Type",   contentType)
                    .header("x-upsert",       "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.error("[Supabase] Error HTTP {} al subir '{}': {}", status, originalName, response.body());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Supabase devolvió HTTP " + status + ": " + response.body());
            }

            log.info("[Supabase] Upload OK (HTTP {}) — path: {}", status, objectPath);

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Upload interrumpido: " + e.getMessage());
        } catch (Exception e) {
            log.error("[Supabase] Error inesperado al subir '{}': {}", originalName, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al subir el archivo: " + e.getMessage());
        }

        // 6. Construir URL pública
        //    /storage/v1/object/public/{bucket}/{path}
        String publicUrl = "%s/storage/v1/object/public/%s/%s".formatted(supabaseUrl, bucket, objectPath);

        log.info("[Supabase] URL pública: {}", publicUrl);

        return new FileUploadResult(originalName, publicUrl, contentType);
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
