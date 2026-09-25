import winston from "winston";

const levelWeights = [
  ["TRACE", 5],
  ["DEBUG", 15],
  ["INFO", 62],
  ["WARN", 12],
  ["ERROR", 5],
  ["CRITICAL", 1],
  ["FATAL", 0],
] as const;

const routes: ReadonlyArray<readonly [string, string]> = [
  ["GET", "/v1/users/{id}"],
  ["POST", "/v1/orders"],
  ["GET", "/v1/orders/{id}"],
  ["PUT", "/v1/users/{id}"],
  ["PATCH", "/v1/orders/{id}"],
  ["DELETE", "/v1/sessions/{id}"],
  ["GET", "/v1/reports/{id}"],
  ["POST", "/v1/notifications"],
];
const logger = winston.createLogger({
  levels: { critical: 0, error: 1, warn: 2, info: 3, debug: 4, trace: 5 },
  level: "trace",
  format: winston.format.combine(winston.format.timestamp(), winston.format.json()),
  defaultMeta: { language: "node" },
  transports: [new winston.transports.Console()],
});

const parseInterval = (value: string | undefined): number => {
  if (!value) return 1000;
  const v = value.trim().toLowerCase();
  const num = Number.parseFloat(v);
  if (Number.isNaN(num)) return 1000;
  if (v.endsWith("ms")) return num;
  if (v.endsWith("s")) return num * 1000;
  if (v.endsWith("m")) return num * 60_000;
  if (v.endsWith("h")) return num * 3_600_000;
  return 1000;
};

const parseTotal = (value: string | undefined): number => {
  if (!value) return -1;
  const n = Number.parseInt(value.trim(), 10);
  return Number.isNaN(n) ? -1 : n;
};

const randInt = (min: number, max: number): number => min + Math.floor(Math.random() * (max - min + 1));

const pickWeighted = (): string => {
  const total = levelWeights.reduce((sum, [, weight]) => sum + weight, 0);
  let roll = Math.floor(Math.random() * total);
  for (const [label, weight] of levelWeights) {
    roll -= weight;
    if (roll < 0) return label;
  }
  return "INFO";
};

type RequestCtx = {
  requestId: number;
  method: string;
  path: string;
  resourceId: number;
  remaining: number;
};

const newRequest = (): RequestCtx => {
  const [method, template] = routes[randInt(0, routes.length - 1)] as [string, string];
  const resourceId = randInt(1000, 9999);
  return {
    requestId: randInt(100000, 999999),
    method,
    path: template.replace("{id}", String(resourceId)),
    resourceId,
    remaining: randInt(2, 5),
  };
};

type EventFields = {
  component: string;
  message: string;
  status?: number;
  duration_ms?: number;
  error?: string;
};

const buildEvent = (level: string, request: RequestCtx, closing: boolean): EventFields => {
  const key = `user:${request.resourceId}`;
  const table = request.path.split("/")[2] ?? "users";
  const jobId = randInt(10000, 99999);

  if (level === "TRACE") {
    const choice = randInt(0, 3);
    if (choice === 0) {
      return {
        component: "api",
        message: `accepted connection remote=10.${randInt(0, 255)}.${randInt(0, 255)}.${randInt(1, 254)} protocol=http/1.1 bytes_in=${randInt(128, 8192)}`,
      };
    }
    if (choice === 1) return { component: "cache", message: `cache lookup key=${key} namespace=app` };
    if (choice === 2) {
      return {
        component: "db",
        message: `acquired connection pool=primary active=${randInt(1, 12)} idle=${randInt(0, 8)} wait_ms=${randInt(0, 15)}`,
      };
    }
    return { component: "worker", message: `job dequeued queue=notifications job_id=${jobId} attempt=1` };
  }

  if (level === "DEBUG") {
    const choice = randInt(0, 3);
    if (choice === 0) {
      return {
        component: "api",
        message: `parsed request body bytes=${randInt(200, 12000)} content_type=application/json fields=${randInt(2, 18)}`,
      };
    }
    if (choice === 1) {
      return { component: "cache", message: `cache entry fresh key=${key} ttl_s=${randInt(5, 300)} bytes=${randInt(64, 4096)}` };
    }
    if (choice === 2) {
      return {
        component: "db",
        message: `executing query table=${table} bind_params=${randInt(1, 6)} statement_id=q_${randInt(100, 999)}`,
      };
    }
    return { component: "worker", message: `rendered template channel=email template=order_update bytes=${randInt(400, 8000)}` };
  }

  if (level === "INFO" && closing) {
    const status = request.method === "DELETE" ? 204 : request.method === "POST" ? 201 : 200;
    return {
      component: "api",
      message: "request completed",
      status,
      duration_ms: Number((8 + Math.random() * 372).toFixed(2)),
    };
  }

  if (level === "INFO") {
    const choice = randInt(0, 3);
    if (choice === 0) return { component: "api", message: `dispatching handler handler=${request.method.toLowerCase()}_${table}` };
    if (choice === 1) return { component: "cache", message: `cache updated key=${key} ttl_s=300 bytes=${randInt(64, 4096)}` };
    if (choice === 2) return { component: "db", message: `loaded record table=${table} id=${request.resourceId} source=primary` };
    return { component: "worker", message: `notification queued channel=email recipient_id=${request.resourceId} job_id=${jobId}` };
  }

  if (level === "WARN") {
    const choice = randInt(0, 3);
    if (choice === 0) {
      const duration = Number((500 + Math.random() * 3500).toFixed(2));
      return {
        component: "db",
        message: `slow query duration_ms=${duration.toFixed(0)} threshold_ms=500 table=${table}`,
        duration_ms: duration,
      };
    }
    if (choice === 1) {
      return {
        component: "api",
        message: `retrying upstream attempt=${randInt(2, 3)} max=3 reason=connection reset service=payments`,
      };
    }
    if (choice === 2) return { component: "cache", message: `cache miss, reading from database key=${key}` };
    return { component: "api", message: "deprecated field user_id ignored; client should send id" };
  }

  if (level === "ERROR") {
    const choice = randInt(0, 4);
    if (choice === 0) {
      return { component: "api", message: "upstream request failed service=payments status=503", status: 503, error: "upstream 5xx" };
    }
    if (choice === 1) {
      return { component: "db", message: "connection timeout service=postgres timeout_ms=5000", error: "connection timeout" };
    }
    if (choice === 2) {
      return {
        component: "api",
        message: `permission denied user=u_${request.resourceId} action=update resource=orders`,
        status: 403,
        error: "permission denied",
      };
    }
    if (choice === 3) {
      return { component: "api", message: `resource not found id=${request.resourceId}`, status: 404, error: "resource not found" };
    }
    return { component: "api", message: "rejected credentials realm=api client_id=api_client", status: 401, error: "invalid credentials" };
  }

  if (randInt(0, 1) === 0) {
    return {
      component: "db",
      message: `database pool exhausted active=20 max=20 waiting=${randInt(4, 40)}`,
      error: "connection timeout",
    };
  }
  return {
    component: "api",
    message: `authentication backend unavailable consecutive_failures=${randInt(5, 30)} dependency=auth`,
    error: "upstream 5xx",
  };
};

const toWinstonLevel = (level: string): string => {
  if (level === "TRACE") return "trace";
  if (level === "DEBUG") return "debug";
  if (level === "INFO") return "info";
  if (level === "WARN") return "warn";
  if (level === "CRITICAL") return "critical";
  return "error";
};

const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

const run = async (): Promise<void> => {
  const intervalMs = parseInterval(process.env.LOG_INTERVAL);
  const totalLogs = parseTotal(process.env.TOTAL_LOGS);

  let seq = 1;
  let request: RequestCtx | undefined;
  while (totalLogs === -1 || seq <= totalLogs) {
    if (!request || request.remaining <= 0) request = newRequest();
    const severity = pickWeighted();
    request.remaining -= 1;
    let closing = request.remaining <= 0;
    if (severity === "WARN" || severity === "ERROR" || severity === "CRITICAL") {
      request.remaining = 0;
      closing = true;
    }
    const event = buildEvent(severity, request, closing);
    logger.log({
      level: toWinstonLevel(severity),
      message: event.message,
      severity,
      seq,
      request_id: request.requestId,
      component: event.component,
      method: request.method,
      path: request.path,
      status: event.status,
      duration_ms: event.duration_ms,
      error: event.error,
    });
    seq += 1;
    await sleep(intervalMs);
  }
};

void run();
