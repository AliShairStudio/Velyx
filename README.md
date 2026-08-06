# Velyx SOS 🛡️

**Velyx SOS** is a comprehensive personal safety and emergency response application designed to provide users with a reliable way to call for help in critical situations. Whether it's through a physical button press, a shake, or a voice command, Velyx SOS ensures that your trusted contacts are alerted immediately with your real-time location.

---

## 🚀 Key Features

### 1. Multi-Trigger SOS System
The app provides three distinct ways to trigger an emergency alert, ensuring you can call for help even if you cannot look at or touch your screen:
*   **Volume Button Trigger**: Press the **Volume Up** button twice in quick succession to send an SOS. This works even when the **screen is off** and the phone is locked. 
    *   *Smart Logic*: If your volume is already at maximum, the app nudges it down slightly so the next press is always detected.
*   **Shake Detection**: Simply shake your phone twice to trigger an alert. Sensitivity is optimized to prevent accidental triggers while remaining responsive in emergencies.
*   **Voice Trigger Phrase**: Configure a custom phrase (e.g., "Help Help") that the app monitors for. Once heard, it automatically executes the SOS procedure.

### 2. Intelligent Emergency Alerts
When an SOS is triggered, the app performs the following actions:
*   **Real-time Location**: Fetches your precise GPS coordinates.
*   **Automatic SMS**: Sends a custom emergency message along with a Google Maps link to all your selected trusted contacts.
*   **Vibration Feedback**: Provides a tactile confirmation so you know the alert was successfully sent without needing to look at the device.
*   **Audio SOS Clips**: Allows you to record and save an audio message that can be used for reference or evidence.

### 3. Management & History
*   **Trusted Contacts**: Manage a list of recipients who should receive your alerts.
*   **SOS History**: A detailed log of all sent alerts, including the time, trigger type, message sent, and the list of recipients.
*   **Configurable Settings**: Independent toggles for each trigger type (Shake, Volume, Voice) so you can customize your protection.

### 4. Modern User Interface
*   **Vibrant/Glassy Design**: A beautiful, modern UI with "glassmorphism" effects and vibrant gradients.
*   **Dark/Light Mode**: Full support for system-wide theme preferences.
*   **Multi-language Support**: Available in English, Urdu, Spanish, Russian, Chinese, Japanese, and Arabic.

---

## 🛠️ Technology Stack
*   **Platform**: Android (Java)
*   **Architecture**: Foreground Service for 24/7 background monitoring.
*   **Location Services**: Google Play Services (Fused Location Provider).
*   **Persistence**: SharedPreferences with GSON for complex data handling.
*   **UI Components**: Material Design 3, ConstraintLayout, RecyclerView.

---

## 📋 How It Works (The Core Logic)

### Background Monitoring
The app uses a **Foreground Service** (`VelyxService`) to stay active in the background. It employs a **WakeLock** strategy to ensure that during an emergency, the CPU stays awake long enough to finish sending the SMS and fetching the location, even if the device is in "Doze" mode.

### Volume Detection when Screen is Off
To detect volume presses when the screen is dark, the app uses a combination of:
1.  **BroadcastReceivers** with high system priority.
2.  **ContentObservers** that monitor system settings changes.
3.  **MediaSession** integration to keep the volume stream responsive to the app.

---

## 📥 Getting Started
1.  **Clone the Repository**: `git clone https://github.com/yourusername/velyx.git`
2.  **Open in Android Studio**: Open the project and wait for Gradle sync.
3.  **Add Permissions**: Ensure you grant SMS, Location, and Microphone permissions when prompted.
4.  **Configure SOS**: Go to the "Alert Message" tab, type your message, and select your trusted contacts.
5.  **Enable Triggers**: Go to Settings and turn on the Shake or Volume triggers.

---

## 🛡️ Privacy & Safety
Velyx SOS is built with user privacy in mind. Location data is only fetched and transmitted during an active SOS event and is sent only to the contacts *you* explicitly choose.

---

**Velyx SOS - Your Safety, Our Priority.**


