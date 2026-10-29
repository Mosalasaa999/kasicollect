package main

import (
    "crypto/hmac"
    "crypto/sha256"
    "encoding/hex"
    "encoding/json"
    "fmt"
    "io"
    "log"
    "net/http"
    "os"
    "strings"
    "time"
)

type WebhookPayload struct {
    Object string `json:"object"`
    Entry  []struct {
        ID      string `json:"id"`
        Changes []struct {
            Value struct {
                MessagingProduct string `json:"messaging_product"`
                Metadata         struct {
                    DisplayPhoneNumber string `json:"display_phone_number"`
                    PhoneNumberID      string `json:"phone_number_id"`
                } `json:"metadata"`
                Messages []struct {
                    From string `json:"from"`
                    ID   string `json:"id"`
                    Type string `json:"type"`
                    Text *struct {
                        Body string `json:"body"`
                    } `json:"text,omitempty"`
                    Interactive *struct {
                        Type        string `json:"type"`
                        ButtonReply *struct {
                            ID    string `json:"id"`
                            Title string `json:"title"`
                        } `json:"button_reply,omitempty"`
                    } `json:"interactive,omitempty"`
                } `json:"messages"`
            } `json:"value"`
        } `json:"changes"`
    } `json:"entry"`
}

var (
    appSecret   = os.Getenv("META_APP_SECRET")
    verifyToken = os.Getenv("WHATSAPP_VERIFY_TOKEN")
    apiToken    = os.Getenv("WHATSAPP_SYSTEM_USER_TOKEN")
)

func verifySignature(payload []byte, signatureHeader string) bool {
    if signatureHeader == "" || !strings.HasPrefix(signatureHeader, "sha256=") {
        return false
    }
    actualSig := strings.TrimPrefix(signatureHeader, "sha256=")
    mac := hmac.New(sha256.New, []byte(appSecret))
    mac.Write(payload)
    expectedSig := hex.EncodeToString(mac.Sum(nil))
    return hmac.Equal([]byte(actualSig), []byte(expectedSig))
}

func handleVerification(w http.ResponseWriter, r *http.Request) {
    mode := r.URL.Query().Get("hub.mode")
    token := r.URL.Query().Get("hub.verify_token")
    challenge := r.URL.Query().Get("hub.challenge")

    if mode == "subscribe" && token == verifyToken {
        log.Println("[WHATSAPP] Webhook verified successfully.")
        w.WriteHeader(http.StatusOK)
        w.Write([]byte(challenge))
        return
    }
    w.WriteHeader(http.StatusForbidden)
}

func handleWebhook(w http.ResponseWriter, r *http.Request) {
    body, err := io.ReadAll(r.Body)
    if err != nil {
        http.Error(w, "Read error", http.StatusBadRequest)
        return
    }

    sig := r.Header.Get("X-Hub-Signature-256")
    if !verifySignature(body, sig) {
        log.Printf("[SECURITY] Invalid webhook signature detected from %s", r.RemoteAddr)
        http.Error(w, "Invalid signature", http.StatusUnauthorized)
        return
    }

    var payload WebhookPayload
    if err := json.Unmarshal(body, &payload); err != nil {
        http.Error(w, "JSON parse error", http.StatusBadRequest)
        return
    }

    go processPayload(&payload)
    w.WriteHeader(http.StatusOK)
    w.Write([]byte("EVENT_RECEIVED"))
}

func processPayload(payload *WebhookPayload) {
    for _, entry := range payload.Entry {
        for _, change := range entry.Changes {
            for _, msg := range change.Value.Messages {
                senderMSISDN := msg.From
                phoneNumberID := change.Value.Metadata.PhoneNumberID

                if msg.Type == "text" && strings.EqualFold(strings.TrimSpace(msg.Text.Body), "RECYCLE") {
                    sendMaterialSelection(phoneNumberID, senderMSISDN)
                    continue
                }

                if msg.Type == "interactive" && msg.Interactive != nil && msg.Interactive.ButtonReply != nil {
                    handleButtonSelection(phoneNumberID, senderMSISDN, msg.Interactive.ButtonReply.ID)
                }
            }
        }
    }
}

func sendMaterialSelection(phoneNumberID, to string) {
    url := fmt.Sprintf("https://graph.facebook.com/v20.0/%s/messages", phoneNumberID)
    payload := map[string]interface{}{
        "messaging_product": "whatsapp",
        "recipient_type":    "individual",
        "to":                to,
        "type":              "interactive",
        "interactive": map[string]interface{}{
            "type": "button",
            "body": map[string]string{
                "text": "Siyakwamukela ku-KasiCollect (Alex Ward 108).\n\nWhat recyclables are ready for pickup?",
            },
            "action": map[string]interface{}{
                "buttons": []map[string]interface{}{
                    {"type": "reply", "reply": map[string]string{"id": "MAT_PET", "title": "Clear Bottles (PET)"}},
                    {"type": "reply", "reply": map[string]string{"id": "MAT_CANS", "title": "Drink Cans"}},
                    {"type": "reply", "reply": map[string]string{"id": "MAT_MIXED", "title": "Mixed Sorted Bag"}},
                },
            },
        },
    }
    executeMetaRequest(url, payload)
}

func handleButtonSelection(phoneNumberID, to, selectionID string) {
    hasher := sha256.New()
    hasher.Write([]byte(fmt.Sprintf("%s:%s:%d", to, selectionID, time.Now().UnixNano())))
    manifestCode := hex.EncodeToString(hasher.Sum(nil))[0:6]

    url := fmt.Sprintf("https://graph.facebook.com/v20.0/%s/messages", phoneNumberID)
    msg := fmt.Sprintf("Ngiyabonga! Pickup confirmed for 8th-12th Ave Corridor.\n\nYour Pickup Code is: *%s*\n\nShow this code to the trolley reclaimer to trigger your reward.", manifestCode)

    payload := map[string]interface{}{
        "messaging_product": "whatsapp",
        "recipient_type":    "individual",
        "to":                to,
        "type":              "text",
        "text":              map[string]string{"body": msg},
    }
    executeMetaRequest(url, payload)
}

func executeMetaRequest(url string, payload interface{}) {
    data, _ := json.Marshal(payload)
    req, _ := http.NewRequest("POST", url, strings.NewReader(string(data)))
    req.Header.Set("Authorization", "Bearer "+apiToken)
    req.Header.Set("Content-Type", "application/json")

    client := &http.Client{Timeout: 5 * time.Second}
    resp, err := client.Do(req)
    if err != nil {
        log.Printf("[ERR] WhatsApp API error: %v", err)
        return
    }
    defer resp.Body.Close()
}

func main() {
    http.HandleFunc("/v1/whatsapp/webhook", func(w http.ResponseWriter, r *http.Request) {
        if r.Method == http.MethodGet {
            handleVerification(w, r)
            return
        } else if r.Method == http.MethodPost {
            handleWebhook(w, r)
            return
        }
        w.WriteHeader(http.StatusMethodNotAllowed)
    })

    port := os.Getenv("PORT")
    if port == "" {
        port = "8080"
    }
    log.Printf("[BOOT] KasiCollect Ingestion Gateway listening on :%s", port)
    log.Fatal(http.ListenAndServe(":"+port, nil))
}
