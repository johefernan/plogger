# Plogger

A containerized polyglot log generator that runs multiple language services in parallel:

- Go
- Java
- Python
- Node

## Logging libraries used

- Go: `logrus`
- Java: `SLF4J` + `Logback`
- Python: built-in `logging`
- Node: `winston`

Each service generates random logs with:

- timestamp
- level (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`, `FATAL`)
- sequential log number
- randomized message
- optional context (`request_id`, `duration_ms`)

## Configuration

All services use the same environment variables:

- `LOG_INTERVAL` (default: `1s`) - supports `ms`, `s`, `m`, `h` suffixes
- `TOTAL_LOGS` (default: `-1`) - `-1` means infinite logs

## Run all services

```bash
docker compose up --build
```

## Run with custom settings

```bash
LOG_INTERVAL=500ms TOTAL_LOGS=10 docker compose up --build
```

## Run one language service

```bash
docker compose up --build node
```

