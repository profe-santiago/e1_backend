# MediFlow — Sistema de Gestión de Clínicas (Multi-tenant)

**Equipo 1**

### Integrantes:
* **Alonso Cruz Yahir Jibsam**
* **Martinez Lopez Fatima**
* **Santiago Jimenez Galilea**

**Stack:** Spring Boot, FastAPI (IA), React + Vite, PostgreSQL.

---
## Resumen del Proyecto
MediFlow es una solución integral diseñada para gestionar múltiples clínicas de forma independiente dentro de una misma plataforma. El sistema permite la gestión de citas médicas, procesamiento de pagos, diagnósticos especializados y cuenta con un microservicio dedicado de Inteligencia Artificial para asistencia en el dominio médico.
# CliniClick (ClinicaMVP)
Sistema de administración para clínicas y consultorios privados · DSD-2303

[![Tests CI](https://github.com/galileaCrazy/e1_backend/actions/workflows/test.yml/badge.svg)](https://github.com/galileaCrazy/e1_backend/actions/workflows/test.yml)

Sistema web para gestionar citas, pacientes, médicos, cobros y notificaciones desde un solo lugar. Incluye un asistente de IA para agendar citas por chat usando lenguaje natural.

---

## ¿Por qué dos servicios separados?

Este proyecto usa una arquitectura de **dos microservicios independientes** en lugar de un monolito:

# Comparación de Tecnologías

| Característica           | Spring Boot (`mi-app`)              | FastAPI (`medinflow-ia`)        |
|--------------------------|-------------------------------------|---------------------------------|
| **Lenguaje**             | Java 21                             | Python 3.11                    |
| **Responsabilidad**      | Lógica de negocio, BD, seguridad    | Inteligencia artificial, chat  |
| **Base de datos**        | PostgreSQL                          | Ninguna (proxy + Groq API)     |
| **Puerto**               | 8080                                | 8000                           |
| **ORM**                  | JPA / Hibernate                     | —                              |
| **Validación**           | Bean Validation (`@NotBlank`)       | Pydantic                       |
| **Documentación**        | SpringDoc OpenAPI                   | Swagger integrado              |
| **Servidor de desarrollo** | `.\mvnw spring-boot:run`          | `uvicorn --reload`             |

**¿Por qué no todo en Spring Boot?**
Los modelos de IA se consumen mejor desde Python (ecosistema más maduro: LangChain, Groq SDK, transformers). Separar la IA en su propio servicio permite escalarla o reemplazarla sin tocar el backend clínico.

**¿Por qué no todo en FastAPI?**
Spring Boot ofrece tipado estricto, inyección de dependencias nativa, seguridad enterprise y un ecosistema maduro para datos relacionales complejos. La lógica clínica (citas, diagnósticos, pagos) requiere transacciones ACID que PostgreSQL + JPA manejan mejor.

---

## Stack

### Spring Boot (`mi-app/`)

| Componente | Versión |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.0.5 |
| Maven | 3.9 (wrapper incluido) |
| spring-boot-starter-webmvc | incluido en Boot 4 |
| spring-boot-starter-data-jpa | incluido en Boot 4 |
| spring-boot-starter-security | incluido en Boot 4 |
| spring-boot-starter-webflux | incluido en Boot 4 |
| spring-boot-starter-validation | incluido en Boot 4 |
| postgresql (driver) | incluido en Boot 4 |
| flyway-core + flyway-database-postgresql | incluido en Boot 4 |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 |
| springdoc-openapi-starter-webmvc-ui | 2.8.6 |
| resilience4j-spring-boot3 | 2.2.0 |
| h2 (solo tests) | incluido en Boot 4 |

### FastAPI (`medinflow-ia/`)

| Componente | Versión |
|---|---|
| Python | 3.11 |
| fastapi | 0.136.x |
| uvicorn | última estable |
| groq | SDK oficial |
| PyJWT | 2.8+ |
| httpx | 0.28+ |
| python-dotenv | última estable |

---

## Arquitectura

```
Usuario (navegador)
        │
        ├──── REST /api/*  ────────────► Spring Boot :8080
        │                                      │
        │                                      ├── JPA ──► PostgreSQL :5432
        │                                      │           (MVPClinica)
        │                                      │
        │                                      └── WebClient + CircuitBreaker ──►┐
        │                                                                         │
        └──── REST /chat   ────────────► FastAPI :8000 ◄───────────────────────┘
                                                │
                                                └── HTTP ──► Groq API (Llama 3.3)
```

**Flujo de datos en Spring Boot:**
```
Request → Controller (@Valid DTO) → Service → Repository → PostgreSQL
                                                                ↓
Response ← Controller (DTO) ←———————— Service ←————————— Entity
```

**Circuit Breaker en la llamada a FastAPI:**
Cuando Spring Boot consulta a FastAPI, usa Resilience4j. Si la IA no responde, el sistema clínico no se cae — devuelve una respuesta degradada en lugar de propagar el error.

---

## Estructura de carpetas

```
ClinicaMVP/
├── mi-app/                                          ← Backend Spring Boot
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/clinica/mi_app/
│   │   │   │   ├── MiAppApplication.java            ← Punto de entrada
│   │   │   │   ├── config/
│   │   │   │   │   ├── AppConfig.java               ← WebClient + BCrypt beans
│   │   │   │   │   ├── OpenApiConfig.java            ← Swagger UI
│   │   │   │   │   └── SecurityConfig.java           ← Spring Security + JWT
│   │   │   │   ├── controller/                       ← Endpoints HTTP (13 controllers)
│   │   │   │   │   ├── AuthController.java
│   │   │   │   │   ├── CitaController.java
│   │   │   │   │   ├── PacienteController.java
│   │   │   │   │   └── ...
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/                      ← Validación de entradas
│   │   │   │   │   └── response/                     ← Forma de las respuestas
│   │   │   │   ├── exception/
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   └── ErrorResponse.java
│   │   │   │   ├── model/                            ← 11 entidades JPA
│   │   │   │   │   ├── Organizacion.java
│   │   │   │   │   ├── Consultorio.java
│   │   │   │   │   ├── Medico.java
│   │   │   │   │   ├── HorarioMedico.java
│   │   │   │   │   ├── Paciente.java
│   │   │   │   │   ├── Usuario.java
│   │   │   │   │   ├── Cita.java
│   │   │   │   │   ├── Pago.java
│   │   │   │   │   ├── Adjunto.java
│   │   │   │   │   ├── Notificacion.java
│   │   │   │   │   └── Diagnostico.java
│   │   │   │   ├── repository/                       ← Spring Data JPA (interfaces)
│   │   │   │   ├── security/
│   │   │   │   │   ├── JwtUtil.java                  ← Generar y validar tokens
│   │   │   │   │   ├── JwtFilter.java                ← Filtro HTTP (antes del controller)
│   │   │   │   │   ├── UserDetailsServiceImpl.java   ← Cargar usuario desde BD
│   │   │   │   │   ├── AuthenticatedUser.java        ← Helper: leer claims del token
│   │   │   │   │   └── Roles.java                    ← Constantes: ADMIN, MEDICO, PACIENTE
│   │   │   │   └── service/                          ← Lógica de negocio (13 services)
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/
│   │   │           ├── V1__medinflow_mvp.sql          ← 11 tablas base
│   │   │           └── V2__add_paciente_rol.sql       ← Rol PACIENTE en usuario
│   │   └── test/
│   │       ├── java/com/clinica/mi_app/
│   │       │   ├── controller/
│   │       │   │   ├── AuthControllerTest.java
│   │       │   │   ├── CitaControllerTest.java
│   │       │   │   └── PacienteControllerTest.java
│   │       │   └── service/
│   │       │       └── AuthServiceTest.java
│   │       └── resources/
│   │           └── application.properties             ← H2 en memoria para tests
│   └── pom.xml
│
├── medinflow-ia/                                    ← Microservicio IA FastAPI
│   ├── routers/
│   │   ├── auth.py                                  ← Proxy a Spring Boot
│   │   └── chat.py                                  ← Chat + disponibilidad
│   ├── security/
│   │   └── jwt_dep.py                               ← Dependencias JWT FastAPI
│   ├── services/
│   │   ├── auth_service.py                          ← HTTP hacia Spring Boot
│   │   ├── groq_service.py                          ← Groq API (Llama 3.3)
│   │   └── spring_service.py
│   ├── tests/
│   │   ├── conftest.py                              ← Fixtures: client, make_token
│   │   ├── test_auth.py                             ← 5 tests de autenticación
│   │   └── test_ia.py                               ← 2 tests de chat IA
│   ├── conftest.py                                  ← Variables de entorno para pytest
│   ├── main.py                                      ← App FastAPI + routers
│   ├── pytest.ini
│   └── requirements-dev.txt
│
└── README.md
```

---

## Arquitectura multicapa (Spring Boot)

| Capa | Archivo ejemplo | Responsabilidad |
|---|---|---|
| Controller | `CitaController.java` | Recibe HTTP, valida con `@Valid`, delega al service, devuelve DTO |
| DTO | `CitaRequest.java`, `CitaResponse.java` | Define la forma del input/output; nunca expone la entidad directamente |
| Service | `CitaService.java` | Toda la lógica de negocio; filtrado por rol; mapea entidad → DTO |
| Repository | `CitaRepository.java` | Acceso a datos; Spring Data JPA genera la implementación |
| Model | `Cita.java` | Esquema de la tabla en PostgreSQL (JPA / Hibernate) |
| Security | `JwtFilter.java` | Intercepta cada request, valida token, inyecta claims al contexto |

**Regla de oro:** cada capa solo habla con la inmediatamente adyacente. El controller nunca toca el repository. El service nunca retorna `ResponseEntity`.

---

## Configuración local

### Requisitos previos

| Herramienta | Versión mínima |
|---|---|
| Java | 21 |
| Maven | 3.9 (mvnw incluido — no es necesario instalarlo) |
| Python | 3.11 |
| PostgreSQL | 15 |

### 1. Base de datos

```sql
CREATE DATABASE "MVPClinica";
```

Flyway crea las tablas automáticamente al arrancar Spring Boot — no hay que ejecutar SQL manualmente.

### 2. Backend Spring Boot

Crear `mi-app/src/main/resources/application.properties`:

```properties
# Servidor
server.port=8080

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/MVPClinica
spring.datasource.username=tu_usuario
spring.datasource.password=tu_password
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# JWT
jwt.secret=claveSecretaBase64MuyLargaDeAlMenos32Caracteres==
jwt.expiration=86400000

# URL del microservicio IA
fastapi.url=http://localhost:8000

# Swagger
springdoc.api-docs.path=/api/schema
springdoc.swagger-ui.path=/swagger-ui.html
```

`ddl-auto=validate` — Hibernate verifica que el esquema de la BD coincida con las entidades pero nunca lo modifica. Las migraciones las maneja Flyway.

Arrancar:

```bash
cd mi-app
.\mvnw spring-boot:run
```

- API disponible en `http://localhost:8080`
- Swagger UI en `http://localhost:8080/swagger-ui.html`

### 3. Microservicio IA (FastAPI)

```bash
cd medinflow-ia
python -m venv venv311
venv311\Scripts\activate
pip install fastapi uvicorn groq python-dotenv PyJWT httpx
```

Crear `medinflow-ia/.env`:

```env
GROQ_API_KEY=gsk_...
JWT_SECRET=claveSecretaBase64MuyLargaDeAlMenos32Caracteres==
SPRING_BOOT_URL=http://localhost:8080
```

Arrancar:

```bash
uvicorn main:app --reload --port 8000
```

- API disponible en `http://localhost:8000`
- Documentación en `http://localhost:8000/docs`

⚠️ `JWT_SECRET` debe ser exactamente la misma cadena que se usa en Spring Boot. Ambos servicios firman y validan tokens con la misma clave.

---

## Migraciones Flyway

Flyway ejecuta los archivos `V{n}__{descripción}.sql` en orden ascendente al arrancar la app. Nunca vuelve a ejecutar un archivo ya aplicado.

| Archivo | Contenido |
|---|---|
| `V1__medinflow_mvp.sql` | Las 11 tablas del MVP: organizacion, consultorio, medico, horario_medico, paciente, usuario, cita, pago, adjunto, notificacion, diagnostico |
| `V2__add_paciente_rol.sql` | Agrega el valor `'PACIENTE'` al CHECK constraint de la columna `rol` en la tabla `usuario` |

Convención obligatoria de nombres: `V{versión}__{descripción}.sql` — el doble guion bajo `__` es requerido por Flyway.

---

## JWT — diseño del token

El token JWT incluye estos claims además del estándar (`sub`, `iat`, `exp`):

| Claim | Tipo | Contenido |
|---|---|---|
| `sub` | String | Email del usuario |
| `rol` | String | `ADMIN`, `MEDICO` o `PACIENTE` |
| `organizacionId` | UUID | Organización a la que pertenece |
| `usuarioId` | UUID | ID del usuario en la tabla `usuario` |

**¿Por qué incluir `rol` y `organizacionId` en el token?**
Para que cada request sea self-contained — el backend no necesita ir a la BD para saber qué puede hacer el usuario. El filtro JWT extrae esos claims y los deja disponibles en el contexto de seguridad para que los services los lean con `AuthenticatedUser.getRol()` y `AuthenticatedUser.getEmail()`.

**Filtrado por rol PACIENTE:**
Un PACIENTE solo puede ver sus propios datos. Si intenta acceder a datos de otro paciente, el service devuelve `ResourceNotFoundException` (404) en lugar de 403 — esto evita revelar que el recurso existe pero no tiene acceso.

---

## Roles y control de acceso

| Rol | Puede hacer |
|---|---|
| ADMIN | Todo: crear médicos, consultorios, ver reportes, gestionar cualquier paciente |
| MEDICO | Ver su agenda, registrar diagnósticos, ver expediente de sus pacientes |
| PACIENTE | Solo sus citas, sus diagnósticos, sus adjuntos — nada más |

---

## Endpoints

### Autenticación (público)

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| POST | `/api/auth/registro` | Registrar nuevo usuario | 201 + token JWT |
| POST | `/api/auth/login` | Iniciar sesión | 200 + token JWT |

### Pacientes

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/api/pacientes/organizacion/{orgId}` | ADMIN, MEDICO | Listar todos los pacientes |
| GET | `/api/pacientes/{id}` | ADMIN, MEDICO, PACIENTE* | Ver un paciente |
| POST | `/api/pacientes` | ADMIN | Crear paciente |
| PUT | `/api/pacientes/{id}` | ADMIN | Actualizar paciente |
| DELETE | `/api/pacientes/{id}` | ADMIN | Eliminar paciente |

### Citas

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/api/citas/paciente/{pacienteId}` | ADMIN, MEDICO, PACIENTE* | Citas de un paciente |
| GET | `/api/citas/medico/{medicoId}` | ADMIN, MEDICO | Citas de un médico |
| POST | `/api/citas` | ADMIN, MEDICO | Crear cita |
| DELETE | `/api/citas/{id}` | ADMIN, MEDICO | Eliminar cita |

### Diagnósticos

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/api/diagnosticos/cita/{citaId}` | ADMIN, MEDICO, PACIENTE* | Diagnósticos de una cita |
| POST | `/api/diagnosticos` | ADMIN, MEDICO | Registrar diagnóstico |

### Adjuntos

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/api/adjuntos/paciente/{pacienteId}` | ADMIN, MEDICO, PACIENTE* | Adjuntos del paciente |
| POST | `/api/adjuntos` | ADMIN, MEDICO | Subir adjunto |

### IA (FastAPI — puerto 8000)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/chat` | Autenticado | Chat con asistente Llama 3.3 |
| GET | `/disponibilidad/{orgId}` | ADMIN, MEDICO | Consultar disponibilidad |
| POST | `/api/auth/registro` | Público | Proxy a Spring Boot |
| POST | `/api/auth/login` | Público | Proxy a Spring Boot |

> (*) El rol PACIENTE solo accede a sus propios datos. Intentar ver datos de otro paciente devuelve 404.

Todos los endpoints protegidos requieren header:
```
Authorization: Bearer <token>
```

---

## Ejecutar tests

### Backend Spring Boot

```bash
cd mi-app
.\mvnw test
```

No requiere PostgreSQL — usa H2 en memoria. Cubre 10 tests en total:

| Clase | Tests |
|---|---|
| `AuthControllerTest` | registro exitoso, login exitoso, sin token (401), token inválido (401), rol insuficiente (403) |
| `CitaControllerTest` | listar citas autenticado, crear cita como ADMIN, eliminar como PACIENTE (403) |
| `PacienteControllerTest` | listar pacientes como MEDICO, crear como ADMIN, eliminar como PACIENTE (403) |
| `AuthServiceTest` | password se guarda hasheado con BCrypt, login con credenciales incorrectas lanza excepción |

Tecnología: JUnit 5 + MockMvc + `@MockitoBean` (Spring Boot 4). No se usa `@SpringBootTest`.

### Microservicio IA

```bash
cd medinflow-ia
venv311\Scripts\activate
pytest -v
```

No requiere Spring Boot ni Groq corriendo — usa `monkeypatch`. Cubre 7 tests:

| Archivo | Tests |
|---|---|
| `test_auth.py` | registro 201, login 200+token, sin token 401, token inválido 401, rol insuficiente 403 |
| `test_ia.py` | chat responde 200, payload incompleto 422 |

---

## Decisiones de arquitectura

### ¿Por qué Circuit Breaker con FastAPI?
FastAPI es un servicio externo que puede fallar (Groq API caída, contenedor detenido). Sin Circuit Breaker, un fallo en la IA bloquea hilos en Spring Boot y puede colapsar toda la aplicación. Resilience4j detecta fallos consecutivos, abre el circuito y llama al `fallbackMethod` para devolver una respuesta degradada.

### ¿Por qué WebClient en lugar de RestTemplate?
RestTemplate es síncrono y bloquea el hilo hasta recibir respuesta. WebClient (reactivo, parte de WebFlux) libera el hilo durante la espera. Para un servicio de IA que puede tardar varios segundos en responder, esto mejora la concurrencia del backend clínico.

### ¿Por qué Maven y no Gradle?
El scaffolding del profe usa Gradle, pero Maven sigue siendo el estándar en proyectos enterprise Java. `mvnw` está incluido en el repositorio — no es necesario instalar Maven. El wrapper garantiza la misma versión en todos los entornos.

### ¿Por qué SOAP para datos médicos sensibles?
REST es suficiente para CRUD clínico. SOAP se reserva para historial clínico completo y expedientes porque ofrece contrato estricto con WSDL, WS-Security y trazabilidad de mensajes — requerimientos de cumplimiento que REST no garantiza por defecto.

### ¿Por qué no Event Driven / SAGA?
El MVP tiene un solo backend principal con una base de datos. SAGA aplica cuando hay transacciones distribuidas entre múltiples servicios que pueden fallar independientemente. Añadirlo ahora sería sobrediseño prematuro — está documentado en el backlog v2.

---

## Errores frecuentes y soluciones

| Error | Causa | Solución |
|---|---|---|
| `Connection refused` al arrancar Spring Boot | PostgreSQL no está corriendo o la BD no existe | Iniciar PostgreSQL y ejecutar `CREATE DATABASE "MVPClinica";` |
| `FlywayException: Validate failed` | El esquema de la BD no coincide con las entidades JPA | Verificar que Flyway corrió todas las migraciones o limpiar la BD en dev |
| `Weak key` al generar JWT | `jwt.secret` tiene menos de 32 caracteres | Usar una clave Base64 de al menos 256 bits (32 caracteres ASCII) |
| `401 Unauthorized` en un endpoint protegido | Token no enviado o expirado | Enviar `Authorization: Bearer <token>` con un token vigente |
| `403 Forbidden` | El rol del usuario no tiene permiso en ese endpoint | Verificar el rol en el token y los permisos en `SecurityConfig` |
| `404 Not Found` inesperado como PACIENTE | Intentando acceder a datos de otro paciente | Comportamiento correcto — el sistema oculta la existencia del recurso ajeno |
| `No qualifying bean` para `BCryptPasswordEncoder` | Bean no declarado en `@Configuration` | Verificar `AppConfig.java` — debe tener `@Bean BCryptPasswordEncoder` |
| `HttpMessageNotReadableException` | Body del request no es JSON válido | Verificar `Content-Type: application/json` en el request |
| `MethodArgumentNotValidException` | Bean Validation falló (`@NotBlank`, `@Email`) | Spring devuelve 400 automáticamente — revisar el cuerpo del request |
| `pytest: command not found` | pytest no está instalado en el venv activo | Activar `venv311\Scripts\activate` y ejecutar `pip install pytest PyJWT httpx` |
| `ImportError: No module named 'groq'` | Groq SDK no instalado | `pip install groq` dentro del venv activo |
| FastAPI devuelve 422 en lugar de 401 | Request sin body requerido antes de validar auth | Comportamiento correcto de Pydantic — el payload es inválido antes de llegar al JWT |

---

## Verificación final

Después de arrancar ambos servicios:

```
http://localhost:8080/swagger-ui.html   → Swagger del backend clínico   ✅
http://localhost:8000/docs              → Swagger del microservicio IA    ✅
POST /api/auth/registro                 → Devuelve token JWT              ✅
POST /api/auth/login                    → Devuelve token JWT              ✅
GET  /api/pacientes/organizacion/{id}   → Lista pacientes (requiere JWT)  ✅
POST /chat                              → Responde el asistente IA        ✅
```

⚠️ Flyway ejecuta las migraciones automáticamente al arrancar — no hay comando separado. Si cambia el esquema, crear un nuevo archivo `V3__descripcion.sql` (nunca modificar migraciones ya aplicadas).

