package main

import (
	"fmt"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/sirupsen/logrus"
)

type weightedLevel struct {
	label  string
	weight int
}

// FATAL stays in the set at weight 0. logrus.Fatal would exit the process.
var levelWeights = []weightedLevel{
	{"TRACE", 5},
	{"DEBUG", 15},
	{"INFO", 62},
	{"WARN", 12},
	{"ERROR", 5},
	{"CRITICAL", 1},
	{"FATAL", 0},
}

var routes = [][2]string{
	{"GET", "/v1/users/{id}"},
	{"POST", "/v1/orders"},
	{"GET", "/v1/orders/{id}"},
	{"PUT", "/v1/users/{id}"},
	{"PATCH", "/v1/orders/{id}"},
	{"DELETE", "/v1/sessions/{id}"},
	{"GET", "/v1/reports/{id}"},
	{"POST", "/v1/notifications"},
}

type requestCtx struct {
	requestID  int
	method     string
	path       string
	resourceID int
	remaining  int
}

type logEvent struct {
	message     string
	component   string
	status      int
	hasStatus   bool
	durationMs  float64
	hasDuration bool
	errReason   string
}

func main() {
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	logger := logrus.New()
	logger.SetFormatter(&logrus.JSONFormatter{
		TimestampFormat: time.RFC3339Nano,
	})
	logger.SetOutput(os.Stdout)
	logger.SetLevel(logrus.TraceLevel)

	interval := time.Second
	if v := os.Getenv("LOG_INTERVAL"); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			interval = d
		}
	}

	total := -1
	if v := os.Getenv("TOTAL_LOGS"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			total = n
		}
	}

	count := 1
	var request *requestCtx
	for total == -1 || count <= total {
		if request == nil || request.remaining <= 0 {
			request = newRequest(rng)
		}
		level := pickWeighted(rng)
		request.remaining--
		closing := request.remaining <= 0
		if level == "WARN" || level == "ERROR" || level == "CRITICAL" {
			request.remaining = 0
			closing = true
		}

		event := buildEvent(level, request, closing, rng)
		fields := logrus.Fields{
			"language":   "go",
			"severity":   level,
			"seq":        count,
			"request_id": request.requestID,
			"component":  event.component,
			"method":     request.method,
			"path":       request.path,
		}
		if event.hasStatus {
			fields["status"] = event.status
		}
		if event.hasDuration {
			fields["duration_ms"] = event.durationMs
		}
		if event.errReason != "" {
			fields["error"] = event.errReason
		}

		logger.WithFields(fields).Log(toLogrusLevel(level), event.message)
		count++
		time.Sleep(interval)
	}
}

func pickWeighted(rng *rand.Rand) string {
	total := 0
	for _, item := range levelWeights {
		total += item.weight
	}
	roll := rng.Intn(total)
	cursor := 0
	for _, item := range levelWeights {
		cursor += item.weight
		if roll < cursor {
			return item.label
		}
	}
	return "INFO"
}

func newRequest(rng *rand.Rand) *requestCtx {
	route := routes[rng.Intn(len(routes))]
	resourceID := 1000 + rng.Intn(9000)
	return &requestCtx{
		requestID:  100000 + rng.Intn(900000),
		method:     route[0],
		path:       strings.ReplaceAll(route[1], "{id}", strconv.Itoa(resourceID)),
		resourceID: resourceID,
		remaining:  2 + rng.Intn(4),
	}
}

func buildEvent(level string, request *requestCtx, closing bool, rng *rand.Rand) logEvent {
	key := fmt.Sprintf("user:%d", request.resourceID)
	parts := strings.Split(request.path, "/")
	table := "users"
	if len(parts) > 2 {
		table = parts[2]
	}
	jobID := 10000 + rng.Intn(90000)

	switch level {
	case "TRACE":
		switch rng.Intn(4) {
		case 0:
			return logEvent{
				component: "api",
				message: fmt.Sprintf(
					"accepted connection remote=10.%d.%d.%d protocol=http/1.1 bytes_in=%d",
					rng.Intn(256), rng.Intn(256), 1+rng.Intn(254), 128+rng.Intn(8065),
				),
			}
		case 1:
			return logEvent{component: "cache", message: "cache lookup key=" + key + " namespace=app"}
		case 2:
			return logEvent{
				component: "db",
				message: fmt.Sprintf(
					"acquired connection pool=primary active=%d idle=%d wait_ms=%d",
					1+rng.Intn(12), rng.Intn(9), rng.Intn(16),
				),
			}
		default:
			return logEvent{
				component: "worker",
				message:   fmt.Sprintf("job dequeued queue=notifications job_id=%d attempt=1", jobID),
			}
		}
	case "DEBUG":
		switch rng.Intn(4) {
		case 0:
			return logEvent{
				component: "api",
				message: fmt.Sprintf(
					"parsed request body bytes=%d content_type=application/json fields=%d",
					200+rng.Intn(11801), 2+rng.Intn(17),
				),
			}
		case 1:
			return logEvent{
				component: "cache",
				message:   fmt.Sprintf("cache entry fresh key=%s ttl_s=%d bytes=%d", key, 5+rng.Intn(296), 64+rng.Intn(4033)),
			}
		case 2:
			return logEvent{
				component: "db",
				message:   fmt.Sprintf("executing query table=%s bind_params=%d statement_id=q_%d", table, 1+rng.Intn(6), 100+rng.Intn(900)),
			}
		default:
			return logEvent{
				component: "worker",
				message:   fmt.Sprintf("rendered template channel=email template=order_update bytes=%d", 400+rng.Intn(7601)),
			}
		}
	case "INFO":
		if closing {
			status := 200
			if request.method == "DELETE" {
				status = 204
			} else if request.method == "POST" {
				status = 201
			}
			return logEvent{
				component:   "api",
				message:     "request completed",
				status:      status,
				hasStatus:   true,
				durationMs:  round2(8 + rng.Float64()*372),
				hasDuration: true,
			}
		}
		switch rng.Intn(4) {
		case 0:
			return logEvent{component: "api", message: fmt.Sprintf("dispatching handler handler=%s_%s", lower(request.method), table)}
		case 1:
			return logEvent{component: "cache", message: fmt.Sprintf("cache updated key=%s ttl_s=300 bytes=%d", key, 64+rng.Intn(4033))}
		case 2:
			return logEvent{component: "db", message: fmt.Sprintf("loaded record table=%s id=%d source=primary", table, request.resourceID)}
		default:
			return logEvent{component: "worker", message: fmt.Sprintf("notification queued channel=email recipient_id=%d job_id=%d", request.resourceID, jobID)}
		}
	case "WARN":
		switch rng.Intn(4) {
		case 0:
			duration := round2(500 + rng.Float64()*3500)
			return logEvent{
				component:   "db",
				message:     fmt.Sprintf("slow query duration_ms=%.0f threshold_ms=500 table=%s", duration, table),
				durationMs:  duration,
				hasDuration: true,
			}
		case 1:
			return logEvent{
				component: "api",
				message:   fmt.Sprintf("retrying upstream attempt=%d max=3 reason=connection reset service=payments", 2+rng.Intn(2)),
			}
		case 2:
			return logEvent{component: "cache", message: "cache miss, reading from database key=" + key}
		default:
			return logEvent{component: "api", message: "deprecated field user_id ignored; client should send id"}
		}
	case "ERROR":
		switch rng.Intn(5) {
		case 0:
			return logEvent{component: "api", message: "upstream request failed service=payments status=503", status: 503, hasStatus: true, errReason: "upstream 5xx"}
		case 1:
			return logEvent{component: "db", message: "connection timeout service=postgres timeout_ms=5000", errReason: "connection timeout"}
		case 2:
			return logEvent{
				component: "api",
				message:   fmt.Sprintf("permission denied user=u_%d action=update resource=orders", request.resourceID),
				status:    403,
				hasStatus: true,
				errReason: "permission denied",
			}
		case 3:
			return logEvent{
				component: "api",
				message:   fmt.Sprintf("resource not found id=%d", request.resourceID),
				status:    404,
				hasStatus: true,
				errReason: "resource not found",
			}
		default:
			return logEvent{component: "api", message: "rejected credentials realm=api client_id=api_client", status: 401, hasStatus: true, errReason: "invalid credentials"}
		}
	default:
		if rng.Intn(2) == 0 {
			return logEvent{
				component: "db",
				message:   fmt.Sprintf("database pool exhausted active=20 max=20 waiting=%d", 4+rng.Intn(37)),
				errReason: "connection timeout",
			}
		}
		return logEvent{
			component: "api",
			message:   fmt.Sprintf("authentication backend unavailable consecutive_failures=%d dependency=auth", 5+rng.Intn(26)),
			errReason: "upstream 5xx",
		}
	}
}

func toLogrusLevel(level string) logrus.Level {
	switch level {
	case "TRACE":
		return logrus.TraceLevel
	case "DEBUG":
		return logrus.DebugLevel
	case "INFO":
		return logrus.InfoLevel
	case "WARN":
		return logrus.WarnLevel
	case "CRITICAL":
		return logrus.ErrorLevel
	default:
		return logrus.ErrorLevel
	}
}

func round2(v float64) float64 {
	return float64(int(v*100)) / 100
}

func lower(s string) string {
	out := make([]byte, len(s))
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		out[i] = c
	}
	return string(out)
}
