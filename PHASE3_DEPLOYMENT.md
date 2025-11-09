# SyncOne Health - Phase 3 Deployment Guide

## Overview

Phase 3 implements the production backend for SyncOne Health with:
- ASP.NET Core 8 Web API
- Microsoft Semantic Kernel Agent Framework
- PostgreSQL with pgvector for RAG
- MedGemma-27B cloud inference
- Alert dispatch system
- Feedback ingestion
- Remote wipe capability

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Android App (Phase 1 & 2)              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ SMS Gateway  │  │ Local ML     │  │ Smart Router │      │
│  │              │  │ MedGemma-4B  │  │              │      │
│  └──────┬───────┘  └──────────────┘  └──────┬───────┘      │
└─────────┼────────────────────────────────────┼──────────────┘
          │                                    │
          │ HTTPS                              │
          ▼                                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    ASP.NET Core Backend                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          Coordinator Agent (PII Shield)              │   │
│  └─────┬──────────┬──────────┬──────────┬──────────────┘   │
│        │          │          │          │                   │
│  ┌─────▼─────┐  ┌─▼────────┐ ┌▼────────┐ ┌▼───────────┐   │
│  │Primary    │  │Maternal  │ │RxSafety │ │Referral    │   │
│  │Care Agent │  │Health    │ │Agent    │ │Agent       │   │
│  │           │  │Agent     │ │         │ │(Alerts)    │   │
│  └─────┬─────┘  └─┬────────┘ └┬────────┘ └┬───────────┘   │
│        │          │           │           │                │
│        └──────────┴───────────┴───────────┘                │
│                        │                                    │
│        ┌───────────────┼───────────────┐                   │
│        │               │               │                   │
│  ┌─────▼─────┐  ┌──────▼──────┐  ┌────▼─────┐            │
│  │MedGemma   │  │RAG Service  │  │Alert     │            │
│  │27B Service│  │(pgvector)   │  │Dispatch  │            │
│  └───────────┘  └─────────────┘  └──────────┘            │
└─────────────────────────────────────────────────────────────┘
          │                    │                  │
          │                    │                  │
    ┌─────▼─────┐      ┌───────▼────────┐  ┌─────▼──────┐
    │MedGemma   │      │PostgreSQL      │  │FCM/Twilio  │
    │Cloud API  │      │with pgvector   │  │(Alerts)    │
    └───────────┘      └────────────────┘  └────────────┘
```

## Prerequisites

### Software Requirements
- .NET 8 SDK
- PostgreSQL 15+ with pgvector extension
- Docker (optional, for containerized deployment)
- Azure CLI or AWS CLI (for cloud deployment)

### API Keys Required
- MedGemma-27B endpoint & API key (Azure ML, SageMaker, or self-hosted)
- OpenAI API key (for Semantic Kernel classification)
- Embedding service API key (BGE-small-en or similar)
- FCM Server Key (Firebase Cloud Messaging for alerts)

## Local Development Setup

### 1. Database Setup

```bash
# Install PostgreSQL 15+
sudo apt install postgresql-15 postgresql-contrib

# Install pgvector extension
sudo apt install postgresql-15-pgvector

# Create database
sudo -u postgres psql -c "CREATE DATABASE syncone;"
sudo -u postgres psql -d syncone -c "CREATE EXTENSION vector;"

# Create user
sudo -u postgres psql -c "CREATE USER syncone_user WITH PASSWORD 'your_password';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE syncone TO syncone_user;"
```

### 2. Configuration

Create `backend/SyncOne.Backend/appsettings.Development.json`:

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=syncone;Username=syncone_user;Password=your_password"
  },
  "MedGemma": {
    "Endpoint": "http://localhost:8000",
    "ApiKey": "test-key"
  },
  "Embeddings": {
    "Endpoint": "http://localhost:8001",
    "ApiKey": "test-key"
  },
  "OpenAI": {
    "ModelId": "gpt-4",
    "ApiKey": "sk-your-openai-key"
  }
}
```

### 3. Run Migrations

```bash
cd backend/SyncOne.Backend

# Install EF Core tools if not already installed
dotnet tool install --global dotnet-ef

# Create initial migration
dotnet ef migrations add InitialCreate --project ../SyncOne.Infrastructure

# Apply migrations
dotnet ef database update
```

### 4. Run Backend

```bash
cd backend/SyncOne.Backend
dotnet run
```

The API will be available at:
- HTTP: `http://localhost:5000`
- HTTPS: `https://localhost:5001`
- Swagger UI: `https://localhost:5001/swagger`

## Production Deployment

### Option 1: Docker Deployment

#### Create Dockerfile

```dockerfile
# backend/SyncOne.Backend/Dockerfile
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY ["backend/SyncOne.Backend/SyncOne.Backend.csproj", "backend/SyncOne.Backend/"]
COPY ["backend/SyncOne.Core/SyncOne.Core.csproj", "backend/SyncOne.Core/"]
COPY ["backend/SyncOne.Infrastructure/SyncOne.Infrastructure.csproj", "backend/SyncOne.Infrastructure/"]
RUN dotnet restore "backend/SyncOne.Backend/SyncOne.Backend.csproj"
COPY . .
WORKDIR "/src/backend/SyncOne.Backend"
RUN dotnet build "SyncOne.Backend.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "SyncOne.Backend.csproj" -c Release -o /app/publish

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "SyncOne.Backend.dll"]
```

#### Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg15
    environment:
      POSTGRES_DB: syncone
      POSTGRES_USER: syncone_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U syncone_user -d syncone"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: .
      dockerfile: backend/SyncOne.Backend/Dockerfile
    environment:
      - ConnectionStrings__DefaultConnection=Host=postgres;Database=syncone;Username=syncone_user;Password=${DB_PASSWORD}
      - MedGemma__Endpoint=${MEDGEMMA_ENDPOINT}
      - MedGemma__ApiKey=${MEDGEMMA_API_KEY}
      - Embeddings__Endpoint=${EMBEDDINGS_ENDPOINT}
      - Embeddings__ApiKey=${EMBEDDINGS_API_KEY}
      - OpenAI__ApiKey=${OPENAI_API_KEY}
      - FCM__ServerKey=${FCM_SERVER_KEY}
      - RemoteWipe__Secret=${REMOTE_WIPE_SECRET}
    ports:
      - "8080:80"
      - "8443:443"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

#### Deploy with Docker

```bash
# Create .env file
cat > .env <<EOF
DB_PASSWORD=your_secure_password
MEDGEMMA_ENDPOINT=https://your-medgemma-endpoint.com
MEDGEMMA_API_KEY=your_key
EMBEDDINGS_ENDPOINT=https://your-embeddings-endpoint.com
EMBEDDINGS_API_KEY=your_key
OPENAI_API_KEY=sk-your-openai-key
FCM_SERVER_KEY=your_fcm_key
REMOTE_WIPE_SECRET=$(openssl rand -base64 32)
EOF

# Build and run
docker-compose up -d

# Check logs
docker-compose logs -f backend
```

### Option 2: Azure Deployment

```bash
# Login to Azure
az login

# Create resource group
az group create --name syncone-rg --location eastus

# Create PostgreSQL server
az postgres flexible-server create \
  --resource-group syncone-rg \
  --name syncone-db \
  --location eastus \
  --admin-user syncone_admin \
  --admin-password YourSecurePassword123! \
  --sku-name Standard_B1ms \
  --tier Burstable \
  --version 15

# Install pgvector extension
az postgres flexible-server parameter set \
  --resource-group syncone-rg \
  --server-name syncone-db \
  --name azure.extensions \
  --value pgvector

# Create App Service Plan
az appservice plan create \
  --name syncone-plan \
  --resource-group syncone-rg \
  --sku B1 \
  --is-linux

# Create Web App
az webapp create \
  --resource-group syncone-rg \
  --plan syncone-plan \
  --name syncone-backend \
  --runtime "DOTNETCORE:8.0"

# Configure app settings
az webapp config appsettings set \
  --resource-group syncone-rg \
  --name syncone-backend \
  --settings \
    ConnectionStrings__DefaultConnection="Host=syncone-db.postgres.database.azure.com;Database=syncone;Username=syncone_admin;Password=YourSecurePassword123!" \
    MedGemma__Endpoint="https://your-endpoint.com" \
    MedGemma__ApiKey="your-key"

# Deploy
cd backend/SyncOne.Backend
dotnet publish -c Release -o ./publish
cd publish
zip -r ../deploy.zip .
az webapp deployment source config-zip \
  --resource-group syncone-rg \
  --name syncone-backend \
  --src ../deploy.zip
```

### Option 3: AWS ECS Deployment

```bash
# Login to AWS
aws configure

# Create ECR repository
aws ecr create-repository --repository-name syncone-backend

# Get ECR login
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build and push Docker image
docker build -t syncone-backend -f backend/SyncOne.Backend/Dockerfile .
docker tag syncone-backend:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/syncone-backend:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/syncone-backend:latest

# Create RDS PostgreSQL instance
aws rds create-db-instance \
  --db-instance-identifier syncone-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 15.3 \
  --master-username syncone_admin \
  --master-user-password YourSecurePassword123! \
  --allocated-storage 20

# Deploy to ECS (use AWS Console or CloudFormation)
```

## Environment Variables

Required environment variables for production:

```bash
# Database
export ConnectionStrings__DefaultConnection="Host=prod-db;Database=syncone;Username=user;Password=pass"

# ML Services
export MedGemma__Endpoint="https://your-medgemma-endpoint.com"
export MedGemma__ApiKey="your-medgemma-api-key"
export Embeddings__Endpoint="https://your-embeddings-endpoint.com"
export Embeddings__ApiKey="your-embeddings-api-key"
export OpenAI__ApiKey="sk-your-openai-api-key"

# Alerts
export FCM__ServerKey="your-fcm-server-key"

# Security
export RemoteWipe__Secret="your-32-char-secret-change-in-production"

# CORS
export Cors__AllowedOrigins__0="https://your-app.com"
```

## Database Migrations in Production

```bash
# Backup database before migration
pg_dump -h prod-db-host -U syncone_user syncone > backup_$(date +%Y%m%d).sql

# Run migrations
dotnet ef database update --connection "Host=prod-db-host;Database=syncone;Username=syncone_user;Password=xxx"
```

## Monitoring

### Health Check Endpoint

```bash
curl https://your-api.com/api/v1/inference/health
```

### Logs

Logs are written to:
- Development: `backend/SyncOne.Backend/logs/`
- Production (Docker): Container stdout/stderr
- Production (Azure): Application Insights
- Production (AWS): CloudWatch Logs

### Metrics to Monitor

1. **Request Rate**: Requests per minute to `/api/v1/inference`
2. **Error Rate**: 5xx responses / total responses
3. **Response Time**: P50, P95, P99 latencies
4. **Model Latency**: MedGemma-27B response time
5. **Database Connections**: Active connections to PostgreSQL
6. **Alert Dispatch**: Number of alerts dispatched per hour

## Security Checklist

- [ ] Change all default passwords and secrets
- [ ] Enable HTTPS (TLS 1.3)
- [ ] Configure firewall to allow only necessary ports
- [ ] Implement rate limiting (configured in Program.cs)
- [ ] Enable audit logging for admin operations
- [ ] Rotate API keys every 90 days
- [ ] Never log PII (phone numbers, messages)
- [ ] Use Azure Key Vault or AWS Secrets Manager for secrets
- [ ] Enable database encryption at rest
- [ ] Configure backup retention (30 days minimum)

## Testing

### Integration Tests

```bash
cd backend/SyncOne.Backend.Tests
dotnet test
```

### API Testing with curl

```bash
# Health check
curl -X GET https://your-api.com/api/v1/inference/health

# Inference request
curl -X POST https://your-api.com/api/v1/inference \
  -H "Content-Type: application/json" \
  -d '{
    "message": "I have a headache and fever",
    "conversationHistory": [],
    "metadata": {
      "phoneNumber": null,
      "urgencyLevel": "NORMAL",
      "tokenCount": 10
    }
  }'

# Submit feedback
curl -X POST https://your-api.com/api/v1/feedback \
  -H "Content-Type: application/json" \
  -d '{
    "messageId": "msg-123",
    "originalResponse": "Original response text",
    "editedResponse": "Edited response text",
    "rating": "GOOD",
    "chw_id": "chw-001",
    "timestamp": 1699564800000
  }'
```

## Troubleshooting

### Database Connection Issues

```bash
# Test PostgreSQL connection
psql -h your-db-host -U syncone_user -d syncone

# Check pgvector extension
psql -h your-db-host -U syncone_user -d syncone -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

### Migration Errors

```bash
# Reset migrations (CAUTION: Data loss!)
dotnet ef database drop
dotnet ef database update

# Or manually fix migration state
psql -h your-db-host -U syncone_user -d syncone -c "DELETE FROM __EFMigrationsHistory WHERE MigrationId = 'problematic_migration';"
```

### High Latency

1. Check MedGemma-27B endpoint latency
2. Review database query performance (use `EXPLAIN ANALYZE`)
3. Check pgvector index: `SELECT * FROM pg_indexes WHERE tablename = 'GuidelineChunks';`
4. Monitor network latency between services

## Next Steps

After deploying Phase 3:

1. **Index Medical Guidelines**: Run guideline seeding script
2. **Load Testing**: Test with 1000 concurrent requests
3. **Security Audit**: Penetration testing, vulnerability scan
4. **Pilot Deployment**: Deploy to 5 test communities
5. **Training**: Create CHW training materials
6. **Documentation**: API docs, troubleshooting guides

## Support

For issues:
1. Check logs: `docker-compose logs -f backend`
2. Review health endpoint: `curl /api/v1/inference/health`
3. Contact: devops@syncone.health

---

**Last Updated**: Phase 3 Implementation
**Version**: 1.0.0
