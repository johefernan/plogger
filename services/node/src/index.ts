import winston from "winston";

const levels = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL", "FATAL"] as const;
const actions = [
  "processing request",
  "connecting to database",
  "fetching user data",
  "updating cache",
  "validating input",
  "generating report",
  "sending notification",
];
const users = ["user123", "admin", "guest", "service_account", "api_client"];
const errors = ["connection timeout", "invalid credentials", "resource not found", "permission denied", "internal server error"];
const logger = winston.createLogger({
  level: "silly",
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

const pick = <T>(arr: readonly T[]): T => arr[Math.floor(Math.random() * arr.length)] as T;

const buildMessage = (level: string): string => {
  const action = pick(actions);
  const user = pick(users);
  const error = pick(errors);
  if (level === "TRACE") return `trace ${action}`;
  if (level === "DEBUG") return `debug ${action}`;
  if (level === "INFO") return `info ${action} user=${user}`;
  if (level === "WARN") return `warn possible issue action=${action}`;
  return `error ${error} user=${user}`;
};

const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

const run = async (): Promise<void> => {
  const intervalMs = parseInterval(process.env.LOG_INTERVAL);
  const totalLogs = parseTotal(process.env.TOTAL_LOGS);

  let seq = 1;
  while (totalLogs === -1 || seq <= totalLogs) {
    const level = pick(levels);
    logger.log({
      level: toWinstonLevel(level),
      message: buildMessage(level),
      seq,
      request_id: Math.random() < 0.5 ? Math.floor(Math.random() * 10_000) : undefined,
      duration_ms: Math.random() < 0.3 ? Number((Math.random() * 1000).toFixed(2)) : undefined,
    });
    seq += 1;
    await sleep(intervalMs);
  }
};

const toWinstonLevel = (level: string): string => {
  if (level === "TRACE") return "silly";
  if (level === "DEBUG") return "debug";
  if (level === "INFO") return "info";
  if (level === "WARN") return "warn";
  return "error";
};

void run();

