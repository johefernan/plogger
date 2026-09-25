import os
import random
import time
import logging

TRACE_LEVEL = 5
logging.addLevelName(TRACE_LEVEL, "TRACE")

logging.basicConfig(
    level=TRACE_LEVEL,
    format=(
        "%(asctime)s %(levelname)s language=python severity=%(severity)s seq=%(seq)s "
        "request_id=%(request_id)s component=%(component)s method=%(method)s path=%(path)s "
        "status=%(status)s duration_ms=%(duration_ms)s error=%(error)s msg=%(message)s"
    ),
)
LOGGER = logging.getLogger("poly.python")

# FATAL stays in the set at weight 0. loggers must not exit on it.
LEVEL_WEIGHTS = (
    ("TRACE", 5),
    ("DEBUG", 15),
    ("INFO", 62),
    ("WARN", 12),
    ("ERROR", 5),
    ("CRITICAL", 1),
    ("FATAL", 0),
)

ROUTES = (
    ("GET", "/v1/users/{id}"),
    ("POST", "/v1/orders"),
    ("GET", "/v1/orders/{id}"),
    ("PUT", "/v1/users/{id}"),
    ("PATCH", "/v1/orders/{id}"),
    ("DELETE", "/v1/sessions/{id}"),
    ("GET", "/v1/reports/{id}"),
    ("POST", "/v1/notifications"),
)
def parse_interval(value: str | None) -> float:
    if not value:
        return 1.0
    v = value.strip().lower()
    try:
        if v.endswith("ms"):
            return float(v[:-2]) / 1000.0
        if v.endswith("s"):
            return float(v[:-1])
        if v.endswith("m"):
            return float(v[:-1]) * 60.0
        if v.endswith("h"):
            return float(v[:-1]) * 3600.0
    except ValueError:
        return 1.0
    return 1.0


def parse_total(value: str | None) -> int:
    if not value:
        return -1
    try:
        return int(value.strip())
    except ValueError:
        return -1


def pick_weighted(pairs: tuple[tuple[str, int], ...]) -> str:
    total = sum(weight for _, weight in pairs)
    roll = random.randrange(total)
    cursor = 0
    for label, weight in pairs:
        cursor += weight
        if roll < cursor:
            return label
    return pairs[-1][0]


def new_request() -> dict[str, object]:
    method, template = random.choice(ROUTES)
    resource_id = random.randint(1000, 9999)
    return {
        "request_id": random.randint(100000, 999999),
        "method": method,
        "path": template.replace("{id}", str(resource_id)),
        "resource_id": resource_id,
        "remaining": random.randint(2, 5),
    }


def _blank() -> dict[str, object]:
    return {"status": "-", "duration_ms": "-", "error": "-"}


def build_event(level: str, request: dict[str, object], closing: bool) -> tuple[str, dict[str, object]]:
    resource_id = request["resource_id"]
    key = f"user:{resource_id}"
    table = str(request["path"]).split("/")[2]
    job_id = random.randint(10000, 99999)
    fields = _blank()

    if level == "TRACE":
        choice = random.randrange(4)
        if choice == 0:
            fields["component"] = "api"
            msg = (
                f"accepted connection remote=10.{random.randint(0, 255)}.{random.randint(0, 255)}."
                f"{random.randint(1, 254)} protocol=http/1.1 bytes_in={random.randint(128, 8192)}"
            )
        elif choice == 1:
            fields["component"] = "cache"
            msg = f"cache lookup key={key} namespace=app"
        elif choice == 2:
            fields["component"] = "db"
            msg = (
                f"acquired connection pool=primary active={random.randint(1, 12)} "
                f"idle={random.randint(0, 8)} wait_ms={random.randint(0, 15)}"
            )
        else:
            fields["component"] = "worker"
            msg = f"job dequeued queue=notifications job_id={job_id} attempt=1"
    elif level == "DEBUG":
        choice = random.randrange(4)
        if choice == 0:
            fields["component"] = "api"
            msg = (
                f"parsed request body bytes={random.randint(200, 12000)} "
                f"content_type=application/json fields={random.randint(2, 18)}"
            )
        elif choice == 1:
            fields["component"] = "cache"
            msg = f"cache entry fresh key={key} ttl_s={random.randint(5, 300)} bytes={random.randint(64, 4096)}"
        elif choice == 2:
            fields["component"] = "db"
            msg = f"executing query table={table} bind_params={random.randint(1, 6)} statement_id=q_{random.randint(100, 999)}"
        else:
            fields["component"] = "worker"
            msg = f"rendered template channel=email template=order_update bytes={random.randint(400, 8000)}"
    elif level == "INFO" and closing:
        fields["component"] = "api"
        if request["method"] == "DELETE":
            fields["status"] = 204
        elif request["method"] == "POST":
            fields["status"] = 201
        else:
            fields["status"] = 200
        fields["duration_ms"] = f"{random.uniform(8, 380):.2f}"
        msg = "request completed"
    elif level == "INFO":
        choice = random.randrange(4)
        if choice == 0:
            fields["component"] = "api"
            msg = f"dispatching handler handler={str(request['method']).lower()}_{table}"
        elif choice == 1:
            fields["component"] = "cache"
            msg = f"cache updated key={key} ttl_s=300 bytes={random.randint(64, 4096)}"
        elif choice == 2:
            fields["component"] = "db"
            msg = f"loaded record table={table} id={resource_id} source=primary"
        else:
            fields["component"] = "worker"
            msg = f"notification queued channel=email recipient_id={resource_id} job_id={job_id}"
    elif level == "WARN":
        choice = random.randrange(4)
        if choice == 0:
            fields["component"] = "db"
            duration = random.uniform(500, 4000)
            fields["duration_ms"] = f"{duration:.2f}"
            msg = f"slow query duration_ms={duration:.0f} threshold_ms=500 table={table}"
        elif choice == 1:
            fields["component"] = "api"
            msg = (
                f"retrying upstream attempt={random.randint(2, 3)} max=3 "
                "reason=connection reset service=payments"
            )
        elif choice == 2:
            fields["component"] = "cache"
            msg = f"cache miss, reading from database key={key}"
        else:
            fields["component"] = "api"
            msg = "deprecated field user_id ignored; client should send id"
    elif level == "ERROR":
        choice = random.randrange(5)
        if choice == 0:
            fields["component"] = "api"
            fields["status"] = 503
            fields["error"] = "upstream 5xx"
            msg = "upstream request failed service=payments status=503"
        elif choice == 1:
            fields["component"] = "db"
            fields["error"] = "connection timeout"
            msg = "connection timeout service=postgres timeout_ms=5000"
        elif choice == 2:
            fields["component"] = "api"
            fields["status"] = 403
            fields["error"] = "permission denied"
            msg = f"permission denied user=u_{resource_id} action=update resource=orders"
        elif choice == 3:
            fields["component"] = "api"
            fields["status"] = 404
            fields["error"] = "resource not found"
            msg = f"resource not found id={resource_id}"
        else:
            fields["component"] = "api"
            fields["status"] = 401
            fields["error"] = "invalid credentials"
            msg = "rejected credentials realm=api client_id=api_client"
    else:
        if random.randrange(2) == 0:
            fields["component"] = "db"
            fields["error"] = "connection timeout"
            msg = f"database pool exhausted active=20 max=20 waiting={random.randint(4, 40)}"
        else:
            fields["component"] = "api"
            fields["error"] = "upstream 5xx"
            msg = (
                f"authentication backend unavailable consecutive_failures={random.randint(5, 30)} "
                "dependency=auth"
            )

    fields["component"] = fields.get("component", "api")
    return msg, fields


def emit(level: str, msg: str, extra: dict[str, object]) -> None:
    if level == "TRACE":
        LOGGER.log(TRACE_LEVEL, msg, extra=extra)
    elif level == "DEBUG":
        LOGGER.debug(msg, extra=extra)
    elif level == "INFO":
        LOGGER.info(msg, extra=extra)
    elif level == "WARN":
        LOGGER.warning(msg, extra=extra)
    elif level == "CRITICAL":
        LOGGER.critical(msg, extra=extra)
    else:
        LOGGER.error(msg, extra=extra)


def main() -> None:
    interval = parse_interval(os.getenv("LOG_INTERVAL"))
    total = parse_total(os.getenv("TOTAL_LOGS"))

    seq = 1
    request: dict[str, object] | None = None
    while total == -1 or seq <= total:
        if request is None or int(request["remaining"]) <= 0:
            request = new_request()
        level = pick_weighted(LEVEL_WEIGHTS)
        request["remaining"] = int(request["remaining"]) - 1
        closing = int(request["remaining"]) <= 0
        if level in ("WARN", "ERROR", "CRITICAL"):
            request["remaining"] = 0
            closing = True

        msg, event = build_event(level, request, closing)
        extra = {
            "severity": level,
            "seq": seq,
            "request_id": request["request_id"],
            "component": event["component"],
            "method": request["method"],
            "path": request["path"],
            "status": event["status"],
            "duration_ms": event["duration_ms"],
            "error": event["error"],
        }
        emit(level, msg, extra)
        seq += 1
        time.sleep(interval)


if __name__ == "__main__":
    main()
