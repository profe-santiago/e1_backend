# CliniClick Frontend

Frontend React + Vite para consumir el backend Spring Boot de CliniClick.

## Levantarlo

Desde la raiz del repo:

```powershell
.\.tools\node-v24.15.0-win-x64\npm.cmd --prefix frontend run dev
```

Abrir:

```text
http://localhost:5173
```

El puerto `5173` coincide con el CORS configurado en Spring Boot.

## Backend esperado

Por defecto apunta a:

```text
http://localhost:8081
```

Para cambiarlo en desarrollo o deploy del frontend, crea `frontend/.env`:

```env
VITE_API_URL=http://localhost:8081
```

Tambien se puede ajustar desde la vista Ajustes cuando ya iniciaste sesion.
