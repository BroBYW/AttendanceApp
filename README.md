# AttendanceApp

AttendanceApp is an Android application designed for robust employee attendance tracking. It features secure clock-in/clock-out mechanisms, real-time GPS location tracking, background hourly location logging, and map-based check-in verification.

## Features

- **User Authentication:** Login system with "Remember Me" functionality.
- **Smart Clock-In System:** 
  - Prevents clock-ins before 6:00 AM.
  - Normal clock-in (6:00 AM - 7:00 AM) requires a selfie/photo.
  - Late clock-in (after 7:00 AM) requires a reason, optional attachments, and a photo.
- **Real-time GPS Tracking:** Validates user location upon check-in to ensure attendance is recorded at the correct location.
- **Background Location Logging:** Utilizes a Foreground Service and precise AlarmManager to record user GPS coordinates continuously (every hour) while clocked in, even if the app is closed or the device reboots.
- **Map Visualization:** Integrates OSMDroid to display user check-in locations on a map interface.
- **Offline Reliability:** Handles location data using a local Room Database for robust persistence.

## Tech Stack & Libraries

- **Language:** Kotlin
- **Minimum SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)
- **Architecture & UI:**
  - AndroidX & Material Design 3
  - ViewBinding
  - Jetpack Compose (Configured)
  - Lottie (for animations)
- **Location & Mapping:**
  - Google Play Services Location (FusedLocationProvider)
  - OSMDroid (OpenStreetMap for Android)
- **Background Tasks:**
  - Android Foreground Services
  - WorkManager
  - AlarmManager (for doze-resistant precise hourly tracking)
- **Data Persistence:**
  - Room Database
  - SharedPreferences (via `AppPreferences`)
- **Media & Images:**
  - Camera/Photo capture intents
  - Coil (Image loading)

## Required Permissions

The app enforces strict privacy and requires the following permissions to function correctly:
- **Camera:** To capture attendance selfies and supporting documents.
- **Location (Fine & Coarse):** To verify clock-in coordinates.
- **Background Location & Foreground Service:** To enable continuous tracking while the user is clocked in.
- **Post Notifications:** Needed for Android 13+ to show foreground service tracking alerts.
- **Schedule Exact Alarm:** To trigger hourly GPS logs reliably.

## Getting Started

1. **Clone the repository:**
   Import the project into Android Studio (agp compatible).
2. **Sync Project with Gradle Files** to download all necessary dependencies (Room, OSMDroid, Play Services).
3. **Run the App:**
   - Deploy to an Android emulator or a physical device running Android 7.0 (API 24) or higher.
   - Note: For accurate map functionality and GPS tracking, testing on a physical device is highly recommended.

## Usage Note

- **Location Tracking:** The app will only track location continuously once a user successfully performs a "Clock In". Tracking stops immediately upon "Clock Out".
- **Battery Optimization:** To ensure the exact 1-hour GPS logging on modern Android versions, the user may need to allow "Alarms & Reminders" in the system App settings.
