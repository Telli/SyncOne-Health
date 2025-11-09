# SyncOne Health Backend

ASP.NET Core 8 backend for SyncOne Health with AI agent framework, RAG, and alert dispatch.

## Project Structure

```
backend/
├── SyncOne.Backend/              # API layer (controllers, middleware)
│   ├── Controllers/
│   │   ├── InferenceController.cs   # AI inference endpoint
│   │   ├── FeedbackController.cs     # CHW feedback collection
│   │   └── AdminController.cs        # Admin operations (remote wipe)
│   ├── Program.cs                    # App configuration
│   └── appsettings.json             # Configuration
├── SyncOne.Core/                 # Domain logic
│   ├── Agents/
│   │   ├── CoordinatorAgent.cs      # Routes to specialists
│   │   ├── PrimaryCareAgent.cs      # General medical queries
│   │   ├── MaternalHealthAgent.cs   # Pregnancy care
│   │   ├── RxSafetyAgent.cs         # Medication safety
│   │   └── ReferralAgent.cs         # Emergency triage
│   ├── Models/                      # DTOs
│   └── Services/                    # Interfaces
└── SyncOne.Infrastructure/       # Data & external services
    ├── Data/
    │   ├── ApplicationDbContext.cs  # EF Core context
    │   └── Entities/                # Database entities
    ├── Services/
    │   ├── MedGemmaService.cs       # MedGemma-27B client
    │   ├── RagService.cs            # pgvector RAG
    │   ├── AlertDispatchService.cs  # Alert system
    │   └── FeedbackStorageService.cs # Feedback storage
    └── Repositories/
```

## Quick Start

### Prerequisites
- .NET 8 SDK
- PostgreSQL 15+ with pgvector
- MedGemma-27B API endpoint (or use mock)

### Run Locally

```bash
# 1. Setup database
sudo -u postgres psql -c "CREATE DATABASE syncone;"
sudo -u postgres psql -d syncone -c "CREATE EXTENSION vector;"

# 2. Update connection string in appsettings.Development.json

# 3. Run migrations
cd SyncOne.Backend
dotnet ef database update --project ../SyncOne.Infrastructure

# 4. Run backend
dotnet run
```

Visit: https://localhost:5001/swagger

## API Endpoints

### Inference
**POST** `/api/v1/inference`

Request:
```json
{
  "message": "I have fever and cough",
  "conversationHistory": [],
  "metadata": {
    "phoneNumber": "+1234567890",
    "urgencyLevel": "NORMAL",
    "tokenCount": 10
  }
}
```

Response:
```json
{
  "message": "For fever and cough: Rest, drink fluids, take acetaminophen. See a doctor if fever >39°C or symptoms worsen.",
  "confidence": 0.85,
  "agentUsed": "PrimaryCare",
  "urgencyLevel": "NORMAL",
  "sources": ["WHO Fever Management Guidelines"]
}
```

### Feedback
**POST** `/api/v1/feedback`

Request:
```json
{
  "messageId": "msg-123",
  "originalResponse": "Take paracetamol",
  "editedResponse": "Take paracetamol 500mg every 6 hours",
  "rating": "GOOD",
  "chwId": "chw-001",
  "timestamp": 1699564800000
}
```

### Health Check
**GET** `/api/v1/inference/health`

### Remote Wipe (Admin Only)
**POST** `/api/v1/admin/remote-wipe`

Requires authentication with `Admin` role.

Request:
```json
{
  "deviceId": "device-123",
  "reason": "Device reported lost/stolen"
}
```

Response:
```json
{
  "success": true,
  "message": "Wipe initiated"
}
```

The backend sends an FCM message to the device with:
```json
{
  "action": "REMOTE_WIPE",
  "auth_token": "base64(payload):base64(signature)",
  "timestamp": 1699564800000
}
```

#### Android Client Token Validation

The Android client **MUST** validate the auth token before performing the wipe:

```kotlin
// Example Kotlin validation code
fun validateWipeToken(
    token: String,
    deviceId: String,
    adminId: String,
    secret: String
): Boolean {
    try {
        // Parse token format: base64(payload):base64(signature)
        val parts = token.split(":")
        if (parts.size != 2) return false
        
        val payloadBase64 = parts[0]
        val providedSignature = parts[1]
        
        // Decode payload
        val payload = String(Base64.decode(payloadBase64, Base64.DEFAULT))
        val payloadParts = payload.split(":")
        
        if (payloadParts.size != 3) return false
        
        val tokenDeviceId = payloadParts[0]
        val tokenAdminId = payloadParts[1]
        val tokenTimestamp = payloadParts[2].toLong()
        
        // Validate device ID and admin ID match
        if (tokenDeviceId != deviceId) return false
        
        // Verify HMAC signature
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val expectedHash = mac.doFinal(payload.toByteArray())
        val expectedSignature = Base64.encodeToString(expectedHash, Base64.NO_WRAP)
        
        if (providedSignature != expectedSignature) return false
        
        // Check token expiration (5 minutes default)
        val currentTimestamp = System.currentTimeMillis() / 1000
        val tokenAge = currentTimestamp - tokenTimestamp
        
        if (tokenAge > 300) return false // 300 seconds = 5 minutes
        if (tokenAge < 0) return false // Token from the future
        
        return true
    } catch (e: Exception) {
        Log.e("RemoteWipe", "Token validation failed", e)
        return false
    }
}
```

**Security Notes:**
- The token expires after 5 minutes (configurable via `RemoteWipe:TokenExpirationMinutes`)
- Tokens are signed with HMAC-SHA256 to prevent tampering
- Each token includes device ID and admin ID to prevent replay across devices
- Timestamp validation prevents replay attacks
- The secret key must be securely stored on both server and device

## Configuration

### appsettings.json

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=syncone;Username=postgres;Password=xxx"
  },
  "MedGemma": {
    "Endpoint": "https://your-model-endpoint.com",
    "ApiKey": "your-api-key"
  },
  "OpenAI": {
    "ApiKey": "sk-your-openai-key"
  }
}
```

## Development

### Add Migration

```bash
dotnet ef migrations add MigrationName --project SyncOne.Infrastructure
```

### Apply Migration

```bash
dotnet ef database update
```

### Run Tests

```bash
dotnet test
```

## Deployment

See [PHASE3_DEPLOYMENT.md](../PHASE3_DEPLOYMENT.md) for detailed deployment instructions.

### Docker

```bash
docker build -t syncone-backend -f SyncOne.Backend/Dockerfile .
docker run -p 8080:80 syncone-backend
```

### Azure

```bash
az webapp up --name syncone-backend --runtime "DOTNETCORE:8.0"
```

## Architecture

### Agent Framework

The system uses a coordinator-specialist pattern:

1. **CoordinatorAgent** receives all queries
2. Applies **PII shield** (masks phone, email)
3. **Classifies** query → routes to specialist
4. Specialist uses **RAG + MedGemma-27B**
5. **Safety filter** checks response
6. Formats for **SMS (≤480 chars)**

### Specialists

- **PrimaryCare**: Fever, cough, general symptoms
- **MaternalHealth**: Pregnancy, prenatal, postnatal
- **RxSafety**: Medication questions, drug interactions
- **Referral**: Emergencies, facility referrals

### RAG Service

- PostgreSQL with pgvector extension
- 384-dimensional embeddings (BGE-small-en)
- Cosine similarity search
- Medical guidelines from WHO, CDC, local protocols

## Monitoring

### Logs

```bash
# View logs
tail -f logs/syncone-*.log

# Docker logs
docker logs -f container_name
```

### Metrics

- Request rate: `/api/v1/inference`
- Error rate: 5xx responses
- Latency: P50, P95, P99
- Model latency: MedGemma-27B
- DB connections

## Troubleshooting

### Database Connection Failed

```bash
# Test connection
psql -h localhost -U postgres -d syncone

# Check pgvector
psql -d syncone -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

### Migration Errors

```bash
# Rollback migration
dotnet ef database update PreviousMigrationName

# Reset database (CAUTION!)
dotnet ef database drop
dotnet ef database update
```

## Security

- ✅ Rate limiting: 100 req/min per IP
- ✅ PII masking in logs
- ✅ HTTPS enforced
- ✅ SQL injection protection (EF Core)
- ✅ CORS configured
- ⚠️ Change default secrets in production!

## License

Proprietary - SyncOne Health

## Support

For issues, contact: backend@syncone.health
