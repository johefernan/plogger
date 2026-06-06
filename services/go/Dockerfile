FROM golang:1.25-alpine

WORKDIR /app
COPY go.mod main.go ./
RUN go mod tidy && go build -o logger main.go

CMD ["./logger"]

