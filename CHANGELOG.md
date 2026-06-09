# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Expanded README with output format table, log-count verification, and Kubernetes usage notes

## [1.0.1] - 2026-06-05

### Added

- Polyglot log generators for Go, Java, Node and Python.
- Shared configuration via `LOG_INTERVAL` and `TOTAL_LOGS` environment variables
- Randomized log levels, messages, and optional fields (`seq`, `request_id`, `duration_ms`, `language`)
- Docker Compose setup to run all services locally
- GitHub Actions workflow to build and publish images to `ghcr.io/johefernan/plogger-<service>`

[Unreleased]: https://github.com/johefernan/plogger/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/johefernan/plogger/releases/tag/v1.0.1
