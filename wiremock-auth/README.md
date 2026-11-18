# Mock del AuthService para probar Sprint 1 localmente

Esto NO toca tus contenedores existentes (`mi-app`, `medinflow-ia`, `postgres`). Es un contenedor nuevo y separado que simula el AuthService de InToGlobe respondiendo con un token RS256 real, firmado con una llave de prueba.

## Qué hay en esta carpeta

- `keys/private_test.pem` — llave privada de prueba (firma el token falso). **Solo para tu máquina, nunca subir a git ni usar en producción.**
- `keys/public_test.pem` — llave pública de prueba. Esta es la que va en tu `AUTH_PUBLIC_KEY`.
- `mappings/login.json` — le dice a WireMock: "cuando te llegue un POST a /auth/login, responde con este token".
- `mock_token.txt` — el token ya generado, por si lo quieres probar suelto (ej. pegarlo directo en un endpoint protegido sin pasar por login).
- `docker-compose.wiremock.yml` — levanta WireMock en el puerto 4001.

## Paso 1 — Configura tu `.env` / `application.properties`

```
AUTH_PUBLIC_KEY=<pega aquí el contenido completo de keys/public_test.pem>
AUTH_SERVICE_URL=http://auth-mock:4001
```

⚠️ Si tu `mi-app` corre en Docker (que es tu caso, según tu captura), `AUTH_SERVICE_URL` debe usar el **nombre del contenedor** (`auth-mock`), no `localhost` — desde dentro de un contenedor, `localhost` se refiere a sí mismo, no a tu máquina ni a otros contenedores.

## Paso 2 — Conecta el mock a la misma red que tu `mi-app`

Necesitas que `auth-mock` y `mi-app` estén en la misma red de Docker para que se vean por nombre.

Para saber el nombre real de tu red, corre:
```bash
docker network ls
```
Busca algo como `clinicamvp_default` o `clinicamvp_clinicamvp` (Docker Compose nombra la red según la carpeta del proyecto). Edita `docker-compose.wiremock.yml` y reemplaza `clinicamvp_default` por el nombre real que veas ahí.

Si no tienes claro el nombre, mándame tu `docker-compose.yml` real (el que ya usas para `mi-app`/`medinflow-ia`/`postgres`) y te ajusto este archivo exacto en vez de que adivines.

## Paso 3 — Levanta el mock

```bash
docker compose -f docker-compose.wiremock.yml up -d
```

Verifica que responde:
```bash
curl http://localhost:4001/__admin/mappings
```
Debe devolverte el mapping de `/auth/login` en JSON.

## Paso 4 — Prueba el login real contra tu app

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Host: eldoc.med.intoglobe.com.mx" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456"}'
```

Si todo está bien conectado, tu `mi-app` recibe el request → `AuthController` lo proxea a `auth-mock:4001/auth/login` → WireMock responde el token → tu app te lo regresa tal cual.

**Importante:** revisa en tu `AuthService.java` qué path exacto usa el WebClient (ej. `.uri("/auth/login")` o `.uri("/api/v1/login")` o algo distinto). El mapping que te di asume `/auth/login` — si tu código usa otro path, dime cuál es o ábreme el archivo y lo ajusto, o cambia `urlPath` en `mappings/login.json` para que coincida.

## Paso 5 — Usa el token contra un endpoint protegido

```bash
TOKEN=$(cat mock_token.txt)
curl http://localhost:8080/api/pacientes \
  -H "Authorization: Bearer $TOKEN"
```

Si te regresa datos (o un 200/403 por permisos, pero NO un 401), tu `JwtFilter` está validando RS256 correctamente.

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| `mi-app` no puede conectar a `auth-mock` | No están en la misma red Docker — revisa Paso 2 |
| `auth-mock` responde pero tu app da 500 | El path del mapping no coincide con el que llama tu `AuthService.java` |
| Login funciona pero el endpoint protegido da 401 | `AUTH_PUBLIC_KEY` no coincide exactamente con `keys/public_test.pem` (revisa que copiaste el PEM completo, con `-----BEGIN/END-----`) |
| Token "expirado" | No debería pasar — este token de prueba dura 10 años |
