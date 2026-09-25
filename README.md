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

Each service generates request-shaped logs. A request lasts 2–5 lines and shares one `request_id`, method, and path. A warn, error, or critical line ends that request.

Levels are weighted, not uniform. `FATAL` is in the set at weight 0 so a fatal line never stops the process. Go and Java have no non-exiting critical level, so those lines use the error API and set `severity=CRITICAL`.

| Level | Weight |
| --- | ---: |
| TRACE | 5 |
| DEBUG | 15 |
| INFO | 62 |
| WARN | 12 |
| ERROR | 5 |
| CRITICAL | 1 |
| FATAL | 0 |

Fields on every line: `language`, `severity`, `seq`, `request_id`, `component` (`api`, `db`, `cache`, `worker`), `method`, `path`. Completion and slow-query lines add `duration_ms`. HTTP outcomes add `status`. Errors add `error` (`connection timeout`, `invalid credentials`, `resource not found`, `permission denied`, `upstream 5xx`).

Python and Java print text. Go and Node print JSON.

```text
2026-09-24 22:00:00,123 INFO language=python severity=INFO seq=12 request_id=482193 component=api method=GET path=/v1/users/1042 status=200 duration_ms=37.40 error=- msg=request completed
```

```text
2026-09-24T22:00:00.123-05:00 INFO  [main] Main - request completed language=java severity=INFO seq=12 request_id=482193 component=api method=GET path=/v1/users/1042 status=200 duration_ms=37.40
```

```json
{"level":"info","msg":"request completed","language":"go","severity":"INFO","seq":12,"request_id":482193,"component":"api","method":"GET","path":"/v1/users/1042","status":200,"duration_ms":37.4}
```

```json
{"level":"info","message":"request completed","language":"node","severity":"INFO","seq":12,"request_id":482193,"component":"api","method":"GET","path":"/v1/users/1042","status":200,"duration_ms":37.4}
```

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

