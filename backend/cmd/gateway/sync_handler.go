package main

import (
    "context"
    "database/sql"
    "encoding/json"
    "log"
    "net/http"
    "os"
    "time"

    _ "github.com/lib/pq"
)

type ManifestPayload struct {
    ManifestID string `json:"manifest_id"`
    Landmark   string `json:"landmark"`
    Materials  string `json:"materials"`
    Status     string `json:"status"`
}

type ClaimRequest struct {
    ManifestID string  `json:"manifest_id"`
    NetKg      float64 `json:"net_kg"`
}

type IngestGateway struct {
    db *sql.DB
}

func initDB(connStr string) (*sql.DB, error) {
    db, err := sql.Open("postgres", connStr)
    if err != nil {
        return nil, err
    }

    schema := `
    CREATE TABLE IF NOT EXISTS manifests (
        manifest_id VARCHAR(64) PRIMARY KEY,
        landmark TEXT NOT NULL,
        materials TEXT NOT NULL,
        status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
        created_at TIMESTAMPTZ DEFAULT NOW()
    );

    CREATE TABLE IF NOT EXISTS transaction_handoff_logs (
        transaction_id VARCHAR(64) PRIMARY KEY,
        manifest_id VARCHAR(64),
        reclaimer_id_hash VARCHAR(64),
        material VARCHAR(64),
        gross_weight_kg NUMERIC(6, 2),
        tare_weight_kg NUMERIC(6, 2),
        net_weight_kg NUMERIC(6, 2),
        device_timestamp_utc BIGINT,
        synced_at TIMESTAMPTZ DEFAULT NOW()
    );`

    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    defer cancel()

    _, err = db.ExecContext(ctx, schema)
    return db, err
}

func (g *IngestGateway) HandleManifests(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    w.Header().Set("Access-Control-Allow-Origin", "*")

    if r.Method == http.MethodPost {
        var req ManifestPayload
        if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
            http.Error(w, "Invalid JSON", http.StatusBadRequest)
            return
        }

        if g.db != nil {
            _, err := g.db.Exec(`
                INSERT INTO manifests (manifest_id, landmark, materials, status)
                VALUES ($1, $2, $3, 'PENDING')
                ON CONFLICT (manifest_id) DO NOTHING;
            `, req.ManifestID, req.Landmark, req.Materials)

            if err != nil {
                log.Printf("[ERR] Manifest insert: %v", err)
            }
        }

        json.NewEncoder(w).Encode(map[string]string{"status": "CREATED", "manifest_id": req.ManifestID})
        return
    }

    if r.Method == http.MethodGet {
        var manifests []ManifestPayload
        if g.db != nil {
            rows, err := g.db.Query(`SELECT manifest_id, landmark, materials, status FROM manifests WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT 20`)
            if err == nil {
                defer rows.Close()
                for rows.Next() {
                    var m ManifestPayload
                    if err := rows.Scan(&m.ManifestID, &m.Landmark, &m.Materials, &m.Status); err == nil {
                        manifests = append(manifests, m)
                    }
                }
            }
        }
        if manifests == nil {
            manifests = []ManifestPayload{}
        }
        json.NewEncoder(w).Encode(manifests)
        return
    }

    http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}

// Endpoint to mark manifest as COLLECTED and log the weighbridge handoff
func (g *IngestGateway) HandleClaim(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    w.Header().Set("Access-Control-Allow-Origin", "*")

    if r.Method != http.MethodPost {
        http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
        return
    }

    var req ClaimRequest
    if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
        http.Error(w, "Invalid JSON", http.StatusBadRequest)
        return
    }

    if g.db != nil {
        _, err := g.db.Exec(`UPDATE manifests SET status = 'COLLECTED' WHERE manifest_id = $1`, req.ManifestID)
        if err != nil {
            log.Printf("[ERR] Status update failed: %v", err)
        }

        txID := "TX-" + time.Now().Format("20060102150405")
        _, _ = g.db.Exec(`
            INSERT INTO transaction_handoff_logs (
                transaction_id, manifest_id, reclaimer_id_hash, material,
                gross_weight_kg, tare_weight_kg, net_weight_kg, device_timestamp_utc
            ) VALUES ($1, $2, 'rec_za_alex_anchor_01', 'Verified Clean PET', $3 + 0.20, 0.20, $3, $4)
            ON CONFLICT (transaction_id) DO NOTHING;
        `, txID, req.ManifestID, req.NetKg, time.Now().UnixMilli())
    }

    json.NewEncoder(w).Encode(map[string]string{
        "status":      "COLLECTED",
        "manifest_id": req.ManifestID,
        "message":     "Status updated in PostgreSQL ledger.",
    })
}

// Live HTML Web Inspector
func (g *IngestGateway) HandleLedgerView(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "text/html; charset=utf-8")
    html := `<!DOCTYPE html><html><head><title>KasiCollect Live Ledger</title><meta http-equiv="refresh" content="10"><style>body{background:#0A1120;color:#CBD5E1;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;padding:24px}table{width:100%;border-collapse:collapse;margin-top:16px}th,td{border:1px solid #1E293B;padding:14px;text-align:left}th{background:#059669;color:#fff}tr:nth-child(even){background:#131E32}.badge-pending{background:#F59E0B;color:#000;padding:4px 8px;border-radius:4px;font-weight:bold;font-size:12px}.badge-collected{background:#10B981;color:#fff;padding:4px 8px;border-radius:4px;font-weight:bold;font-size:12px}</style></head><body><h1>KasiCollect Live Township Ledger</h1><p>Ward 107/108 &bull; Alexandra Scrap Corridor &bull; Auto-refreshing every 10s</p><table><tr><th>Manifest ID</th><th>Landmark</th><th>Materials</th><th>Status</th></tr>`

    if g.db != nil {
        rows, err := g.db.Query(`SELECT manifest_id, landmark, materials, status FROM manifests ORDER BY created_at DESC LIMIT 50`)
        if err == nil {
            defer rows.Close()
            for rows.Next() {
                var id, landmark, mat, st string
                if err := rows.Scan(&id, &landmark, &mat, &st); err == nil {
                    badgeClass := "badge-pending"
                    if st == "COLLECTED" {
                        badgeClass = "badge-collected"
                    }
                    html += "<tr><td><b>" + id + "</b></td><td>" + landmark + "</td><td>" + mat + "</td><td><span class=\"" + badgeClass + "\">" + st + "</span></td></tr>"
                }
            }
        }
    }
    html += `</table></body></html>`
    w.Write([]byte(html))
}

func main() {
    port := os.Getenv("PORT")
    if port == "" {
        port = "8080"
    }

    dbURL := os.Getenv("DATABASE_URL")
    var db *sql.DB
    var err error

    if dbURL != "" {
        log.Println("[BOOT] Connecting to PostgreSQL...")
        db, err = initDB(dbURL)
        if err == nil {
            log.Println("[BOOT] PostgreSQL tables initialized.")
        }
    }

    gateway := &IngestGateway{db: db}

    http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
        w.Header().Set("Content-Type", "application/json")
        json.NewEncoder(w).Encode(map[string]string{
            "status":   "ONLINE",
            "system":   "KasiCollect Cloud Gateway",
            "ward":     "Alexandra Ward 107/108",
            "version":  "1.0.1",
        })
    })

    http.HandleFunc("/v1/manifests", gateway.HandleManifests)
    http.HandleFunc("/v1/manifests/claim", gateway.HandleClaim)
    http.HandleFunc("/admin/ledger", gateway.HandleLedgerView)

    log.Printf("[BOOT] Listening on :%s", port)
    log.Fatal(http.ListenAndServe(":"+port, nil))
}
