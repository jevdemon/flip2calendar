# Flip2Calendar

A native Android calendar app built for the TCL Flip 2 running jailbroken AOSP 11.
Syncs with Google Calendar via the REST API — no Google Play Services required, no WebView dependency, fully D-pad navigable.

If you need to jailbreak your TCL FLip 2 I suggest consulting one of the many online guides such as this one: https://docs.google.com/document/d/1-MIkXxDkNXKJvHGgAItGbsWlFX1xQ2j5q-o0eOtPYNk/mobilebasic and https://github.com/neutronscott/flip2/wiki.

## Why This Exists

Most calendar apps on Android require Google Play Services or an up-to-date WebView. The TCL Flip 2 runs a stripped down AOSP 11 (32-bit) that doesn't have them. I also wanted a D-pad friendly app because the Flip2 doesn't have a touch screen. 
This app was built to fill that gap.

It should also work on any de-Googled Android device running API 30+.

## Features

- Syncs with Google Calendar (read + write)
- Create, edit, and delete events
- Fully D-pad navigable — no touchscreen required
- Recurring event support (edit this occurrence or all)
- Local notifications via AlarmManager — fires even when app is closed
- Location and description fields
- 60-day forward view
- Holiday calendar toggle
- Read-only calendar detection when displaying events from 3rd party calendar providers 
- Dark theme optimised for small screens

## Screenshots

<!-- Add screenshots here -->

## Requirements

- Android API 30+ (Android 11)
- A Google account with Google Calendar
- Your own Google Cloud OAuth credentials (see Setup below)

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/yourusername/flip2calendar.git
cd flip2calendar
```

### 2. Create your Google Cloud credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project (e.g. "Flip2Calendar")
3. Enable the **Google Calendar API**
4. Go to **APIs & Services → OAuth consent screen**
   - Choose **External**
   - Fill in app name and your email
   - Add your Gmail as a test user (or publish the app)
5. Go to **APIs & Services → Credentials → Create Credentials → OAuth Client ID**
   - Application type: **Web Application**
   - Add `http://localhost` as an Authorized Redirect URI
6. Note your **Client ID** and **Client Secret**

### 3. Configure credentials

Copy the example secrets file and fill in your credentials:

```bash
cp secrets.properties.example secrets.properties
```

Edit `secrets.properties`:

```properties
GOOGLE_CLIENT_ID=your_client_id_here.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret_here
```

> ⚠️ Never commit `secrets.properties` — it is listed in `.gitignore`

### 4. Build and install

Open the project in Android Studio and build, or via command line:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. First launch

On first launch the app will open Firefox (or your default browser) to authenticate with Google. After approving access, the browser will redirect back to the app automatically.

## How OAuth Works on AOSP

Standard Android OAuth uses custom URI schemes (e.g. `myapp://`) but AOSP's intent system doesn't handle these reliably. This app uses a **Web Application** OAuth client with `http://localhost` as the redirect URI instead. An `AuthActivity` intercepts the redirect and forwards the authorization code to `MainActivity` for token exchange.

## Architecture

| Component | Role |
|---|---|
| `MainActivity` | Event list, OAuth flow, calendar fetching |
| `AuthActivity` | Intercepts OAuth redirect from browser |
| `EventEditActivity` | Create / edit / delete events |
| `ReminderSchedulerService` | Schedules AlarmManager alarms for reminders |
| `ReminderReceiver` | Posts notification when alarm fires |
| `BootReceiver` | Reschedules alarms after device reboot |

## Notifications Without Play Services

Google Calendar notifications normally rely on Firebase Cloud Messaging (FCM), which requires Play Services. This app schedules local `AlarmManager` alarms instead — they fire at the correct reminder time even when the app is closed, without any Google services.

## Known Limitations

- OAuth consent screen may show "unverified app" warning — click Continue to proceed
- Recurring event edits apply to single occurrence by default; check "Apply to ALL" to update the series
- Read-only calendars (e.g. shared from Zoho) are displayed but cannot be edited
- 60-day forward view only — past events not shown

## Tested On

- TCL Flip 2 running neutronscott's jailbreak (AOSP 11, 32-bit)
- Should work on any de-Googled Android device with API 30+

## Contributing

Pull requests welcome. If you've tested on another device please open an issue to let me know.

## License

MIT — see [LICENSE](LICENSE)