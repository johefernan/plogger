import os
import random
import time
import logging

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s %(levelname)s language=python seq=%(seq)s request_id=%(request_id)s duration_ms=%(duration_ms)s msg=%(message)s",
)
LOGGER = logging.getLogger("poly.python")

LEVELS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL", "FATAL"]
ACTIONS = [
    "processing request",
    "connecting to database",
    "fetching user data",
    "updating cache",
    "validating input",
    "generating report",
    "sending notification",
]
USERS = ["user123", "admin", "guest", "service_account", "api_client"]
ERRORS = ["connection timeout", "invalid credentials", "resource not found", "permission denied", "internal server error"]


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


def build_message(level: str) -> str:
    action = random.choice(ACTIONS)
    user = random.choice(USERS)
    error = random.choice(ERRORS)
    if level == "TRACE":
        return f"trace {action}"
    if level == "DEBUG":
        return f"debug {action}"
    if level == "INFO":
        return f"info {action} user={user}"
    if level == "WARN":
        return f"warn possible issue action={action}"
    return f"error {error} user={user}"


def main() -> None:
    interval = parse_interval(os.getenv("LOG_INTERVAL"))
    total = parse_total(os.getenv("TOTAL_LOGS"))

    seq = 1
    while total == -1 or seq <= total:
        level = random.choice(LEVELS)
        msg = build_message(level)
        extra = {"seq": seq, "request_id": "-", "duration_ms": "-"}
        if random.random() < 0.5:
            extra["request_id"] = str(random.randint(0, 9999))
        if random.random() < 0.3:
            extra["duration_ms"] = f"{random.random() * 1000.0:.2f}"

        if level == "TRACE":
            LOGGER.log(logging.DEBUG, msg, extra=extra)
        elif level == "DEBUG":
            LOGGER.debug(msg, extra=extra)
        elif level == "INFO":
            LOGGER.info(msg, extra=extra)
        elif level == "WARN":
            LOGGER.warning(msg, extra=extra)
        else:
            LOGGER.error(msg, extra=extra)
        seq += 1
        time.sleep(interval)


if __name__ == "__main__":
    main()

