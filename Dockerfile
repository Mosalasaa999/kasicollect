FROM golang:1.22-alpine AS builder
WORKDIR /app

# Copy dependency definitions and auto-generate go.sum
COPY backend/go.mod ./
RUN go mod tidy

# Copy application source code and compile
COPY backend/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /kasicollect-gateway ./cmd/gateway/sync_handler.go

# Production minimal container
FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /kasicollect-gateway .

CMD ["./kasicollect-gateway"]
