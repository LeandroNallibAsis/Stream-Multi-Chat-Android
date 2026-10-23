# Stream Multi-Chat Android 🚀

Lector y agregador de chat unificado en tiempo real para streamers en Android. Conéctate simultáneamente a múltiples plataformas de streaming en vivo sin requerir claves de API complejas ni configuraciones pesadas de PC.

---

## 📱 Plataformas Soportadas

* 🟣 **Twitch**: Conexión IRC/WebSocket en tiempo real con soporte de emotes globales, insignias (Broadcaster, Mod, VIP, Sub), alertas de suscripciones, raids y donaciones de Bits.
* 🟢 **Kick**: Protocolo Centrifugo WebSocket con soporte de badges, emotes personalizados, suscripciones y hosts.
* 🔴 **YouTube Live**: Integración directa InnerTube sin necesidad de Google Cloud API Key ni cuotas de desarrollador; soporte para SuperChats y mensajes destacados.
* 🟠 **Starvios**: Conexión nativa a la plataforma comunitaria con métricas en vivo.

---

## ✨ Características Principales

* 🎙️ **Lector de Voz Nativo (TTS)**:
  * Modos: *Desactivado*, *Solo Alertas / Donaciones* y *Todos los mensajes*.
  * **Timbre Acústico de Alerta**: Emite un sonido nítido por hardware (*Chime*) antes de anunciar cualquier donación, suscripción o raid.
  * Prioridad inmediata (`QUEUE_FLUSH`) para que las alertas interrumpan el chat ordinario al instante.
  * Control de velocidad de habla (0.5x - 2.5x), tono (pitch), límite de caracteres y pronunciación del autor.
  * Filtro anti-spam y protección contra acumulación de mensajes (*backlog drop*).
* 🪟 **Overlay Flotante (PiP / Floating Window)**:
  * Burbuja flotante movible con contador de mensajes no leídos.
  * Ventana transparente sobrepuesta encima de juegos o aplicaciones de streaming móvil.
  * Selector de opacidad de fondo (10% - 90%) para máxima visibilidad sin obstaculizar la pantalla.
* 📺 **Monitor & Preview On-Demand**:
  * Visor integrado ligero de stream para verificar imagen, espectadores en vivo, tiempo transcurrido y título sin saturar la red ni la memoria.
* ⚡ **Optimización de Hardware Extrema**:
  * Certificado y verificado en hardware real (**Samsung Galaxy A04** - Octa-Core 2.3 GHz, 3GB RAM).
  * Consumo de CPU promedio: `< 3.8%`.
  * Memoria RAM promedio: `< 65 MB`.
  * Tamaño del APK Release: **2.18 MB** (gracias a Proguard/R8 y optimizaciones de recursos vectoriales).
  * Tasa de refresco: 60 FPS estables con buffer circular de 250 mensajes.

---

## 🏗️ Arquitectura Modular

El proyecto está estructurado con **Clean Architecture** y principios de modularización por plataforma:

```text
├── app/                  # UI (Jetpack Compose, Material 3), ViewModel, Foreground Service, Overlay
├── core/
│   ├── model/            # Entidades de dominio (ChatMessage, StreamNotification, StreamHealth)
│   ├── network/          # OkHttpClient singleton, BackoffPolicy, NetworkClientProvider
│   └── chat/             # UnifiedChatProcessor (Ring buffer, batching 40ms, dedup cache, simulación)
└── platform/
    ├── twitch/           # TwitchConnector (IRC client, WebSocket, Parser de badges y bits)
    ├── kick/             # KickConnector (Centrifugo WebSocket, JSON frame unpacker)
    ├── youtube/          # YouTubeConnector (InnerTube live parser, continuations)
    └── starvios/         # StarviosConnector (Polling ligero)
```

---

## 🛠️ Tecnologías y Librerías

* **Lenguaje**: Kotlin 2.0+
* **UI**: Jetpack Compose con Material 3 (Material You)
* **Arquitectura**: MVVM + StateFlow / SharedFlow + Coroutines
* **Networking**: OkHttp 4 (WebSockets, HTTP/2, Connection Pooling)
* **Serialización**: KotlinX Serialization JSON
* **Persistencia**: Jetpack DataStore Preferences
* **Síntesis de Audio**: Android Native TextToSpeech Engine + ToneGenerator (Hardware sonification)

---

## 🚀 Compilación e Instalación

### Requisitos
* Android Studio Ladybug / Koala o superior.
* JDK 17 / 21 (incluido en Android Studio JBR).
* Android SDK 34 / 35.

### Comandos de Compilación

```bash
# Clonar el repositorio
git clone https://github.com/LeandroNallibAsis/Stream-Multi-Chat-Android.git
cd Stream-Multi-Chat-Android

# Compilar APK de Release optimizado con Proguard/R8
./gradlew assembleRelease

# Instalar directamente en dispositivo conectado por ADB
./gradlew installRelease
```

El archivo APK generado se ubica en:
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 Licencia

Desarrollado por **Leandro Asis** (2026).
Distribuido bajo la licencia [MIT](LICENSE).
