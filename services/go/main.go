package main

import (
	"math/rand"
	"os"
	"strconv"
	"time"

	"github.com/sirupsen/logrus"
)

var levels = []string{"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL", "FATAL"}
var actions = []string{
	"processing request", "connecting to database", "fetching user data",
	"updating cache", "validating input", "generating report", "sending notification",
}
var users = []string{"user123", "admin", "guest", "service_account", "api_client"}
var errors = []string{"connection timeout", "invalid credentials", "resource not found", "permission denied", "internal server error"}

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
	for total == -1 || count <= total {
		level := levels[rng.Intn(len(levels))]
		msg := buildMessage(level, rng)
		fields := logrus.Fields{
			"language": "go",
			"seq":      count,
		}
		if rng.Float32() < 0.5 {
			fields["request_id"] = rng.Intn(10000)
		}
		if rng.Float32() < 0.3 {
			fields["duration_ms"] = float64(int(rng.Float64()*100000)) / 100.0
		}

		logger.WithFields(fields).Log(toLogrusLevel(level), msg)
		count++
		time.Sleep(interval)
	}
}

func buildMessage(level string, rng *rand.Rand) string {
	action := actions[rng.Intn(len(actions))]
	user := users[rng.Intn(len(users))]
	errMsg := errors[rng.Intn(len(errors))]

	switch level {
	case "TRACE":
		return "trace " + action
	case "DEBUG":
		return "debug " + action
	case "INFO":
		return "info " + action + " user=" + user
	case "WARN":
		return "warn possible issue action=" + action
	default:
		return "error " + errMsg + " user=" + user
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
	default:
		return logrus.ErrorLevel
	}
}

