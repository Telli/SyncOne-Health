# SyncOne Health

> AI-Powered SMS Health Assistant for Community Health Workers

SyncOne Health is an Android application that enables Community Health Workers (CHWs) to provide intelligent, AI-assisted medical guidance via SMS. The app processes incoming health queries, uses on-device or cloud-based AI inference to generate medically-informed responses, and maintains conversation context across SMS threads.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 🎯 Overview

SyncOne Health bridges the gap between remote patients and healthcare providers by enabling CHWs to efficiently respond to health queries via SMS. The app uses medical AI models (MedGemma) to suggest responses while maintaining human oversight for quality and safety.

### Key Capabilities

- **Automated SMS Processing**: Receives and processes health-related SMS queries
- **AI-Powered Responses**: Generates medical guidance using TensorFlow Lite (MedGemma 4B) or cloud API
- **Smart Routing**: Automatically routes complex queries to cloud-based models, simple ones to on-device inference
- **RAG Integration**: Retrieves relevant medical guidelines using vector embeddings (BGE-small-en)
- **Context Awareness**: Maintains conversation history and detects multi-turn consultations
- **Urgency Detection**: Automatically flags critical medical situations
- **CHW Review Workflow**: Low-confidence responses are held for manual review before sending
- **Secure & Private**: Encrypted database (SQLCipher), biometric authentication, audit logging

## ✨ Features

### For Community Health Workers
- 📱 **SMS Monitor Dashboard**: Real-time view of all active health consultations
- 🔍 **Thread Management**: Track conversation history with patients
- ✏️ **Manual Override**: Edit or write manual responses when needed
- ⚡ **Urgency Flags**: Visual indicators for critical cases requiring immediate attention
- 📞 **Quick Actions**: Call patients directly or export conversation logs
- 🔒 **App Lock**: Biometric/PIN protection for sensitive health data

### For Patients
- 💬 **SMS-Based Access**: No app installation required, works on any phone
- 🤖 **Instant Responses**: AI-generated replies for common health queries (2-6 seconds)
- 🔄 **Context Retention**: Follow-up questions are understood in conversation context
- 🆘 **Emergency Detection**: Critical symptoms automatically escalate to CHW

### Technical Features
- 🧠 **On-Device AI**: TensorFlow Lite inference (MedGemma 4B quantized to INT4)
- ☁️ **Cloud Fallback**: Switches to cloud API for complex medical queries
- 🔎 **RAG System**: Vector store with medical guidelines for enhanced responses
- 🔐 **End-to-End Security**: SQLCipher encryption, secure key management
- 📊 **Audit Trail**: Complete logging of all AI decisions and message flow
- 🎨 **Modern UI**: Material Design 3 with Jetpack Compose

## 🏗️ Architecture

SyncOne Health follows **Clean Architecture** principles with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (Jetpack Compose UI, ViewModels, Navigation)               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  (Use Cases, Business Logic, Domain Models, Repositories)   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  (Database, ML Models, SMS, Network, Security)              │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

**SMS Flow:**
```
Incoming SMS → SmsReceiver → SmsProcessingWorker
    ↓
ProcessIncomingSmsUseCase (thread management, urgency detection)
    ↓
SmartRoutingUseCase (decide: local vs cloud)
    ↓                           ↓
LocalInferenceUseCase    CloudInferenceUseCase
    ↓                           ↓
VectorStore (RAG)         AgentClient (API)
    ↓                           ↓
MedGemmaInference         Cloud MedGemma-27B
    ↓                           ↓
← InferenceResult (confidence score)
    ↓
if confidence < 0.7: Hold for CHW review
if confidence ≥ 0.7: Auto-send SMS
```

**Dependency Injection:** Hilt (Dagger)  
**Database:** Room with SQLCipher encryption  
**Background Processing:** WorkManager  
**ML Inference:** TensorFlow Lite, ONNX Runtime  

## 🚀 Technology Stack

### Core
- **Language**: Kotlin 1.9.20
- **Android SDK**: Min 26, Target 34
- **Build System**: Gradle (Kotlin DSL)

### Architecture & DI
- **Jetpack Compose**: Modern declarative UI
- **Hilt**: Dependency injection
- **Navigation Compose**: Type-safe navigation
- **Lifecycle & ViewModel**: Android Architecture Components

### Database & Storage
- **Room**: Local database (version 2.6.1)
- **SQLCipher**: Database encryption (version 4.5.4)
- **Security Crypto**: Encrypted SharedPreferences
- **DataStore**: Preferences storage

### AI & Machine Learning
- **TensorFlow Lite**: On-device inference (version 2.14.0)
- **TFLite GPU**: GPU acceleration
- **ONNX Runtime**: Alternative model runtime (version 1.16.3)
- **Custom Tokenizers**: BERT-style and MedGemma tokenization

### Networking
- **Retrofit**: REST API client (version 2.9.0)
- **OkHttp**: HTTP client with logging interceptor
- **Gson**: JSON serialization
- **Kotlin Serialization**: Alternative JSON handling

### Security
- **Biometric**: Fingerprint/Face authentication
- **SQLCipher**: Database encryption
- **Android Keystore**: Secure key storage
- **Encrypted SharedPreferences**: Secure settings

### Background Processing
- **WorkManager**: Background SMS processing and thread expiration
- **Coroutines**: Asynchronous programming (version 1.7.3)

### Logging & Debugging
- **Timber**: Logging framework (version 5.0.1)

## 📦 Setup & Installation

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK 26+ (Android 8.0+)
- ~4GB RAM on target device (for local ML models)

### 1. Clone the Repository
```bash
git clone https://github.com/Telli/SyncOne-Health.git
cd SyncOne-Health
```

### 2. Open in Android Studio
- Open Android Studio
- Select "Open an Existing Project"
- Navigate to the cloned directory
- Wait for Gradle sync to complete

### 3. Configure Model Files (Required for Phase 2 AI)

The app requires TensorFlow Lite model files for local inference. Create the following directory structure:

```
app/src/main/assets/models/
├── medgemma_4b_int4.tflite  (~1.2GB) - Main medical LLM
├── medgemma_vocab.json       - Vocabulary for MedGemma
├── bge_small_en_int8.tflite (~90MB)  - Embedding model for RAG
└── bge_vocab.txt             - Vocabulary for BGE embeddings
```

**Model File Formats:**

`medgemma_vocab.json`:
```json
{
  "<pad>": 0,
  "<bos>": 1,
  "<eos>": 2,
  "<unk>": 3,
  "fever": 100,
  "cough": 101,
  ...
}
```

`bge_vocab.txt`:
```
[PAD]
[UNK]
[CLS]
[SEP]
[MASK]
a
about
...
```

> **Note**: The app will run without model files but will use fallback/stub responses. See [PHASE2_IMPLEMENTATION.md](PHASE2_IMPLEMENTATION.md) for detailed model setup instructions.

### 4. Build & Run
```bash
./gradlew assembleDebug
```

Or use Android Studio's "Run" button (Shift+F10).

### 5. Grant Permissions
On first launch, grant the following permissions:
- SMS (receive, send, read)
- Phone (read state, call)
- Biometric (optional, for app lock)

## 🎮 Usage

### For CHWs (App Interface)

1. **Launch App**: Authenticate with biometric/PIN if app lock is enabled
2. **Monitor Dashboard**: View all active SMS threads
   - Green = Normal priority
   - Orange = Urgent
   - Red = Critical (requires immediate attention)
3. **Review Threads**: Tap a thread to see full conversation
4. **Handle AI Responses**:
   - High-confidence responses are auto-sent
   - Low-confidence responses show feedback dialog for review
5. **Manual Intervention**:
   - Edit AI-suggested responses
   - Write completely manual replies
   - Call patient for complex cases

### For Patients (SMS Interface)

1. **Send SMS**: Text health query to CHW's phone number
   ```
   "I have fever and headache for 2 days"
   ```
2. **Receive Response**: AI-generated guidance (2-6 seconds)
   ```
   "For fever: Rest, drink fluids, take paracetamol. 
   Seek care if fever persists beyond 3 days or 
   if you develop difficulty breathing."
   ```
3. **Follow-up**: Continue conversation naturally
   ```
   "What about the headache?"
   ```
4. **Reset Context**: Send `RESET` to start new topic
   ```
   "RESET"
   → "Conversation reset. You can start a new query."
   ```

### Special Commands
- `RESET` - Clears conversation context and starts fresh

## 🔐 Security Features

1. **Encrypted Database**: All SMS content, patient data, and AI responses encrypted with SQLCipher
2. **Biometric Authentication**: Optional fingerprint/face unlock
3. **App Lock**: Auto-locks after configurable timeout
4. **Secure Key Storage**: Encryption keys stored in Android Keystore
5. **Audit Logging**: Complete trail of all AI decisions, message flow, and user actions
6. **No PHI Logging**: Patient health information never logged to system logs
7. **ProGuard**: Code obfuscation in release builds

## 🧪 Testing

### Run Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

### Testing Without Models
The app includes fallback behaviors that allow testing without ML model files:
- Stub responses are generated for all queries
- Warnings logged to Logcat: "Model file not found"
- App remains fully functional for testing UI/workflow

## 📊 Performance Expectations

With real models on a mid-range device (4GB RAM):
- **Local Inference**: 2-5 seconds for 120 tokens
- **Embedding Generation**: <500ms
- **RAG Search**: <200ms (up to 1000 chunks)
- **Total Response Time**: 3-6 seconds (local) or 1-3 seconds (cloud)
- **Memory Usage**: <800MB

## 🛣️ Project Status & Roadmap

### ✅ Phase 1: Core SMS Gateway (Complete)
- SMS receive/send functionality
- Thread management
- Basic UI with Compose
- Encrypted database
- Biometric authentication

### ✅ Phase 2: AI Integration (Complete - Requires Models)
- TensorFlow Lite integration
- MedGemma inference engine
- RAG vector store
- Smart routing (local vs cloud)
- Confidence scoring
- Cloud API client
- Medical guideline seeding

### 🚧 Future Enhancements
- [ ] Cloud API endpoint deployment
- [ ] Enhanced medical knowledge base
- [ ] Multi-language support
- [ ] Voice message support
- [ ] Analytics dashboard for health trends
- [ ] Integration with health records systems
- [ ] Offline-first sync architecture
- [ ] Model quantization optimization

## 📝 Development

### Code Structure
```
app/src/main/kotlin/com/syncone/health/
├── data/              # Data layer
│   ├── local/        # Database, ML models, security
│   ├── remote/       # API clients
│   └── sms/          # SMS handling
├── domain/           # Business logic
│   ├── model/        # Domain models
│   ├── repository/   # Repository interfaces
│   └── usecase/      # Use cases
├── presentation/     # UI layer
│   ├── auth/         # App lock screen
│   ├── detail/       # Thread detail screen
│   ├── monitor/      # SMS monitor dashboard
│   ├── navigation/   # Navigation setup
│   └── theme/        # Material theming
├── di/               # Dependency injection modules
├── service/          # Background workers
├── security/         # Security utilities
└── util/             # Helper classes
```

### Building for Release
```bash
./gradlew assembleRelease
```

Signed APK will be generated in `app/build/outputs/apk/release/`

### Linting & Code Quality
```bash
./gradlew lint
```

## 🤝 Contributing

This is a healthcare application handling sensitive medical data. Contributions should:
1. Follow Clean Architecture principles
2. Include unit tests for business logic
3. Never log PHI (Protected Health Information)
4. Use dependency injection (Hilt)
5. Follow Kotlin coding conventions
6. Update documentation for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer

**This application provides AI-assisted medical information guidance and is designed to support Community Health Workers. It is NOT a replacement for professional medical diagnosis or treatment. Always consult qualified healthcare professionals for medical advice.**

## 📧 Contact & Support

For questions, issues, or contributions:
- **GitHub Issues**: [Report a bug or request a feature](https://github.com/Telli/SyncOne-Health/issues)
- **Documentation**: See [PHASE2_IMPLEMENTATION.md](PHASE2_IMPLEMENTATION.md) for detailed technical documentation

---

**Built with ❤️ for Community Health Workers**