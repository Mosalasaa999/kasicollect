FROM golang:1.22-alpine AS builder
WORKDIR /app

# Copy entire backend source
COPY backend/ ./

# Clean module checksums
RUN go mod tidy

# Compile binary
RUN CGO_ENABLED=0 GOOS=linux go build -o /kasicollect-gateway ./cmd/gateway/sync_handler.go

# Minimal runtime image
FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /kasicollect-gateway .

CMD ["./kasicollect-gateway"]
