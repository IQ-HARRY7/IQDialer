<div align="center">

# 📞 IQDialer

**A modern, native Android dialer built with Kotlin & Jetpack Compose.**

<p>
  <strong>📱 Calling</strong> •
  <strong>👥 Contacts</strong> •
  <strong>📞 Telecom</strong> •
  <strong>🎥 VoLTE Video</strong> •
  <strong>✨ Liquid Glass UI</strong>
</p>

![Android](https://img.shields.io/badge/Android-16-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)

</div>

---

## ✨ Overview

**IQDialer** is a full-featured Android phone application designed as a replacement for the system dialer. 

It combines **Android Telecom**, **Contacts**, and **CallLog APIs** with a custom **Jetpack Compose + Material 3** interface, providing a modern calling experience while remaining deeply integrated with Android's native telephony framework.

---

## 🚀 Highlights

| Feature | Description |
| :--- | :--- |
| 📞 **Native Calling** | Full-screen incoming and active call experience |
| 👥 **Contacts** | Contacts, search, details, and call history |
| 🕘 **Recents** | Call history with synchronized CallLog data |
| 🎥 **VoLTE Video** | Android Telecom-based carrier video calling |
| 🛡️ **Call Screening** | Call screening and blocking support |
| 💬 **Call Bubble** | Floating in-call interface |
| 🎨 **Liquid Glass** | Custom modern glass-based UI system |
| 🧭 **Navigation** | Animated nested navigation with spring transitions |
| ⚡ **Caching** | Process-level Contacts and CallLog caching |
| 🔔 **Notifications**| Native Android notification integration |

---

## 🛠️ Technology

| Component | Version |
| :--- | :--- |
| **Language** | Kotlin 2.0.21 |
| **UI Toolkit** | Jetpack Compose |
| **Design System**| Material 3 |
| **AGP** | 8.9.1 |
| **Gradle Wrapper**| 9.7.x |
| **JDK** | 21 |
| **SDK (Compile/Target)** | 36 |
| **Minimum SDK** | 29 |
| **Package** | `com.iqstudio.dialer` |

---

## 🏗️ Architecture

```text
                         ┌─────────────────┐
                         │    IQDialer     │
                         └────────┬────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
        ┌───────────┐       ┌───────────┐       ┌───────────┐
        │    UI     │       │   Data    │       │ Telecom   │
        ├───────────┤       ├───────────┤       ├───────────┤
        │ Compose   │       │ DataCache │       │ InCall    │
        │ Material3 │       │ Contact   │       │ Screening │
        │ Glass UI  │       │ Cache     │       │ Bubble    │
        └───────────┘       └───────────┘       └───────────┘
              │                   │                   │
              └───────────────────┼───────────────────┘
                                  ▼
                    ┌─────────────────────────┐
                    │    Android Framework    │
                    ├─────────────────────────┤
                    │ Telecom │ Contacts      │
                    │ CallLog │ Notifications │
                    └─────────────────────────┘
```

---

## 🎨 UI & Design

**IQDialer** uses a custom **Liquid Glass** component system built specifically for Jetpack Compose. The application features animated nested navigation, spring-based transitions, and a custom floating navigation experience.

**Core Components:**
- `GlassButton`
- `GlassOutlinedButton`
- `GlassIconButton`
- `GlassChip`
- `GlassCard`
- `GlassRow`
- `Modifier.liquidGlass()`
- `Modifier.pressScale()`

---

## 📞 Calling

IQDialer integrates directly with the **Android Telecom framework** instead of implementing calls as an independent VoIP system.

```text
TurboInCallService
        │
        ├── Incoming calls
        ├── Active calls
        └── Call state
                │
                ▼
          InCallActivity
                │
                ▼
        CallBubbleService
```

Call-screening functionality is provided securely through `TurboCallScreeningService`.

---

## 🎥 VoLTE Video Calling

IQDialer supports **carrier/VoLTE video calling** natively through Android Telecom. Outgoing calls use Android's native video-call state APIs, while incoming calls respect the video state provided by the Telecom framework.

> 💡 **Note:** IQDialer does not implement an independent Internet-based video-calling service.

---

## 💾 Data Layer

IQDialer communicates efficiently with Android's native providers. `ContentObserver` notifications are utilized to react to real-time changes instead of continuously polling the providers.

```text
┌──────────────────────┐        ┌──────────────────────┐
│ Android Contacts     │        │ Android CallLog      │
│      Provider        │        │      Provider        │
└──────────┬───────────┘        └──────────┬───────────┘
           │                               │
           ▼                               ▼
      ┌─────────┐                     ┌─────────┐
      │Contact  │                     │DataCache│
      │ Cache   │                     └─────────┘
      └─────────┘
```

---

## 📋 Requirements

**Recommended Development Environment:**
- Android Studio
- JDK 21
- Android SDK 36
- Android Build Tools
- Git

> 💡 **Note:** The repository includes the Gradle Wrapper, so a globally installed Gradle distribution is not required. Termux and command-line development are fully supported, though not required for building.

---

## 📥 Getting Started

**1. Clone the repository**
```bash
git clone https://github.com/IQ-HARRY7/IQDialer.git
cd IQDialer
```

**2. Open in Android Studio**
Open the project directory. Android Studio will automatically detect and sync the included Gradle configuration.

**3. Build the APK**

*Linux / macOS:*
```bash
./gradlew assembleDebug
```

*Windows:*
```cmd
gradlew.bat assembleDebug
```

> The generated APK will be available at:  
> `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔨 Build Commands

| Command | Linux / macOS | Windows |
| :--- | :--- | :--- |
| 🐛 **Debug** | `./gradlew assembleDebug` | `gradlew.bat assembleDebug` |
| 📦 **Release** | `./gradlew assembleRelease` | `gradlew.bat assembleRelease` |
| 🧹 **Clean** | `./gradlew clean` | `gradlew.bat clean` |
| 🔄 **Clean + Debug** | `./gradlew clean assembleDebug` | `gradlew.bat clean assembleDebug` |

---

## 📲 Install with ADB

After a successful build, install the app directly to your connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ensure USB debugging is enabled and your device is recognized by running `adb devices`. After installation, **IQDialer** can be selected as your device's default phone application.

---

## ⚠️ Current Limitations

> These are known project limitations rather than build requirements.

*   **Full Camera Rendering:** Local and remote camera preview rendering for VoLTE video calls is not currently implemented.
*   **Multi-Call Support:** Multi-call and call-waiting functionality is not currently implemented.
*   **Liquid Glass:** The current implementation is a custom visual system and does not provide true system-level backdrop blur.

---

## 🔐 Permissions

IQDialer respectfully requests only the Android permissions strictly required for native phone functionality:

*   📞 Phone calls and phone state
*   🕘 Call history
*   👥 Contacts
*   🔔 Notifications
*   🎙️ Audio recording
*   🖥️ Full-screen call interfaces
*   💬 Foreground phone services
*   🪟 System overlays

*(Permissions are requested progressively depending on the enabled functionality and device OS version).*

---

## 📄 License

**IQDialer** is licensed under the GNU General Public License v3.0 or later.
Copyright © **IQ-STUDIO 2026** (ptv limited).

---

<div align="center">

**📞 IQDialer** <br>
Built by **IQ-STUDIO** <br>
*Kotlin • Jetpack Compose • Android Telecom • Material 3*

</div>
