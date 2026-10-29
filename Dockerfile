FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY backend/go.mod ./
RUN go mod download || true
COPY backend/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /kasicollect-gateway ./cmd/gateway/sync_handler.go

FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /kasicollect-gateway .
EXPOSE 8443
CMD ["./kasicollect-gateway"]
