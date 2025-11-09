# SyncOne Health - Configuration Guide

This guide explains how to configure all API keys and services for SyncOne Health. All TODOs have been implemented - you just need to add your credentials!

## Table of Contents
- [Backend Configuration](#backend-configuration)
- [Android App Configuration](#android-app-configuration)
- [Third-Party Service Setup](#third-party-service-setup)
- [Testing Configuration](#testing-configuration)

---

## Backend Configuration

### 1. Basic Setup

Copy the environment template:
```bash
cp .env.example .env
```

Edit `.env` with your actual credentials, or configure `appsettings.json` directly.

### 2. Database (PostgreSQL with pgvector)

```json
// appsettings.json
"ConnectionStrings": {
  "DefaultConnection": "Host=your-db-host;Database=syncone;Username=your-user;Password=your-password"
}
```

**Setup Instructions:**
```bash
# Install PostgreSQL 15+ with pgvector
sudo apt install postgresql-15 postgresql-15-pgvector

# Create database
sudo -u postgres psql -c "CREATE DATABASE syncone;"
sudo -u postgres psql -d syncone -c "CREATE EXTENSION vector;"
```

### 3. MedGemma-27B Cloud Endpoint

```json
"MedGemma": {
  "Endpoint": "https://your-medgemma-endpoint.azure.com",
  "ApiKey": "your-api-key"
}
```

**Options:**
- **Azure ML**: Deploy MedGemma-27B to Azure ML endpoint
- **AWS SageMaker**: Deploy to SageMaker real-time endpoint
- **Self-hosted**: Run with vLLM or TGI

**Example (Azure ML):**
```bash
# Deploy to Azure ML (requires model files)
az ml online-endpoint create --name medgemma-endpoint
az ml online-deployment create --name medgemma-27b --endpoint medgemma-endpoint
```

### 4. Embeddings Service (BGE-small-en)

```json
"Embeddings": {
  "Endpoint": "https://your-embeddings-endpoint.com",
  "ApiKey": "your-api-key"
}
```

**Options:**
- **Hugging Face Inference API**: https://api-inference.huggingface.co/models/BAAI/bge-small-en
- **Self-hosted**: Run with `text-embeddings-inference` or `sentence-transformers`

**Example (Self-hosted with Docker):**
```bash
docker run -p 8001:80 ghcr.io/huggingface/text-embeddings-inference:latest \
  --model-id BAAI/bge-small-en \
  --revision main
```

### 5. OpenAI (for Query Classification)

```json
"OpenAI": {
  "ModelId": "gpt-4",
  "ApiKey": "sk-your-openai-api-key"
}
```

**Get API Key:**
1. Go to: https://platform.openai.com/api-keys
2. Create new secret key
3. Copy and paste into appsettings.json

**Cost-saving tip**: Use `gpt-3.5-turbo` for classification instead of `gpt-4`

### 6. Twilio SMS (for CHW Alerts)

```json
"Twilio": {
  "AccountSid": "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "AuthToken": "your_auth_token",
  "FromPhoneNumber": "+1234567890"
}
```

**Setup Instructions:**
1. Sign up at: https://www.twilio.com/try-twilio
2. Go to Console → Account → API credentials
3. Copy **Account SID** and **Auth Token**
4. Buy a phone number: https://www.twilio.com/console/phone-numbers/incoming
5. Copy phone number to `FromPhoneNumber`

**Free tier**: $15 credit, enough for ~500 SMS messages

### 7. Firebase Cloud Messaging (for Push Notifications)

```json
"FCM": {
  "ServerKey": "your-fcm-server-key",
  "SenderID": "your-sender-id"
}
```

**Setup Instructions:**
1. Go to: https://console.firebase.google.com
2. Create new project (or select existing)
3. Add Android app with package name: `com.syncone.health`
4. Download `google-services.json` → place in `app/`
5. Go to Project Settings → Cloud Messaging
6. Copy **Server Key** and **Sender ID**

**Android App Setup** (add to `app/build.gradle.kts`):
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.0"
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
```

### 8. Alert Configuration

```json
"Alerts": {
  "CHW": {
    "PhoneNumber": "+1234567890",
    "DeviceToken": "fcm-device-token-here"
  }
}
```

**PhoneNumber**: CHW's phone for receiving SMS alerts
**DeviceToken**: FCM device token from CHW Android app

**Get Device Token:**
```kotlin
// In Android app
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val token = task.result
        Log.d("FCM", "Device token: $token")
        // Send this token to backend configuration
    }
}
```

### 9. Remote Wipe Secret

```json
"RemoteWipe": {
  "Secret": "your-32-char-secret-here"
}
```

**Generate Secure Secret:**
```bash
# Linux/Mac
openssl rand -base64 32

# PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

⚠️ **CRITICAL**: Change this in production! Never commit the real secret to git.

---

## Android App Configuration

### 1. Build Configuration

The Android app automatically uses different configurations for debug and release builds:

**Debug Build** (local development):
- Base URL: `http://10.0.2.2:8080/api/v1/` (Android emulator localhost)
- API Key: `dev-key`

**Release Build** (production):
- Base URL: `https://api.syncone.health/api/v1/`
- API Key: Set via `gradle.properties` or environment variable

### 2. Set Production API Key

**Option A: gradle.properties** (NOT committed to git)
```properties
# gradle.properties (add to .gitignore!)
SYNCONE_API_KEY=your-production-api-key
```

Then update `app/build.gradle.kts`:
```kotlin
release {
    buildConfigField("String", "API_KEY", "\"${project.findProperty("SYNCONE_API_KEY")}\"")
}
```

**Option B: Environment Variable**
```bash
export SYNCONE_API_KEY="your-production-api-key"
./gradlew assembleRelease
```

### 3. Firebase Setup (if not done)

1. Add `google-services.json` to `app/` directory
2. Add Firebase dependencies (see FCM section above)
3. Subscribe to device topic:
```kotlin
FirebaseMessaging.getInstance().subscribeToTopic("device_${deviceId}")
```

---

## Third-Party Service Setup

### Quick Start (All Services)

1. **Twilio** (SMS): https://www.twilio.com/try-twilio → Free $15 credit
2. **Firebase** (FCM): https://console.firebase.google.com → Free tier
3. **OpenAI** (Classification): https://platform.openai.com → $5 free credit
4. **Hugging Face** (Embeddings): https://huggingface.co/settings/tokens → Free tier

### Cost Estimates (Monthly)

| Service | Usage | Cost |
|---------|-------|------|
| Twilio SMS | 500 messages | $7.50 |
| Firebase FCM | Unlimited | Free |
| OpenAI gpt-3.5-turbo | 10,000 calls | $10 |
| PostgreSQL (Azure) | Basic tier | $5 |
| **Total** | | **~$22.50/month** |

For production, consider:
- **MedGemma-27B**: Self-hosted on GPU server (~$100/month) or serverless
- **Embeddings**: Self-hosted (free) or Hugging Face Inference ($0.06/1K requests)

---

## Testing Configuration

### Local Development (No API Keys Required)

You can test the system without real API keys:

1. **Mock Mode**: Services gracefully degrade if not configured
2. **Stub Responses**: Alert dispatch logs warnings instead of sending
3. **Local Models**: Android app uses TensorFlow Lite (no backend needed)

**Run Backend Locally:**
```bash
cd backend/SyncOne.Backend

# Uses defaults from appsettings.json
dotnet run

# Backend runs at: https://localhost:5001
```

**Run Android App:**
```bash
# Debug build points to localhost (10.0.2.2:8080)
./gradlew installDebug
```

### Integration Testing

**Test Alert Dispatch** (without real SMS/FCM):
```bash
curl -X POST https://localhost:5001/api/v1/inference \
  -H "Content-Type: application/json" \
  -d '{
    "message": "severe bleeding",
    "conversationHistory": [],
    "metadata": {
      "phoneNumber": "+1234567890",
      "urgencyLevel": "CRITICAL",
      "tokenCount": 10
    }
  }'
```

Check logs for:
```
[WARN] ALERT DISPATCHED: CRITICAL - Phone: +1234567890
[INFO] Twilio service not configured, skipping SMS
[INFO] FCM service not configured, skipping push notification
```

---

## Environment Variables Summary

For Docker/production deployment:

```bash
# Database
export ConnectionStrings__DefaultConnection="Host=prod-db;Database=syncone;..."

# AI Services
export MedGemma__Endpoint="https://..."
export MedGemma__ApiKey="..."
export Embeddings__Endpoint="https://..."
export Embeddings__ApiKey="..."
export OpenAI__ApiKey="sk-..."

# Alerts
export Twilio__AccountSid="AC..."
export Twilio__AuthToken="..."
export Twilio__FromPhoneNumber="+..."
export FCM__ServerKey="..."
export Alerts__CHW__PhoneNumber="+..."
export Alerts__CHW__DeviceToken="..."

# Security
export RemoteWipe__Secret="..."
```

---

## Verification Checklist

- [ ] PostgreSQL database created with pgvector extension
- [ ] Backend builds and runs: `dotnet run`
- [ ] Swagger UI accessible: https://localhost:5001/swagger
- [ ] Health check passes: `curl https://localhost:5001/api/v1/inference/health`
- [ ] Twilio credentials configured (or skipped for testing)
- [ ] FCM credentials configured (or skipped for testing)
- [ ] OpenAI API key configured
- [ ] Android app builds: `./gradlew assembleDebug`
- [ ] Android app connects to backend (check Logcat for HTTP requests)

---

## Troubleshooting

### "Twilio service not configured"
✅ Expected if you haven't set up Twilio. Alerts still work via webhooks/FCM.

### "FCM service not configured"
✅ Expected if you haven't set up Firebase. Alerts still work via SMS/webhooks.

### "MedGemma API request failed"
⚠️ Check `MedGemma:Endpoint` and `MedGemma:ApiKey` in appsettings.json

### "Health check failed"
⚠️ Backend not running or wrong URL. Check `BuildConfig.API_BASE_URL` in Android

### Database connection failed
```bash
# Test connection
psql -h localhost -U postgres -d syncone

# Check pgvector
psql -d syncone -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

---

## Production Deployment

See **PHASE3_DEPLOYMENT.md** for:
- Docker deployment with docker-compose
- Azure App Service deployment
- AWS ECS deployment
- Kubernetes manifests
- Security best practices
- Monitoring and logging

---

## Support

For issues:
1. Check logs: `docker-compose logs -f backend` or `dotnet run` output
2. Verify environment variables: `printenv | grep SYNCONE`
3. Test API endpoints: https://localhost:5001/swagger
4. Contact: support@syncone.health

---

**Last Updated**: Phase 3 - All TODOs Implemented
**Version**: 1.0.0
