# Flip2Calendar

A native Android calendar app built for the **TCL Flip 2** running jailbroken AOSP 11.
Syncs with Google Calendar via the REST API — no Google Play Services required, no WebView dependency, fully D-pad navigable.

## Why This Exists

Most calendar apps on Android require Google Play Services or an up-to-date WebView. The TCL Flip 2 runs stripped AOSP 11 (32-bit) with neither. This app was built from scratch to fill that gap.

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

Scroll to View Events using the D-pad (or use a virtual mouse):<br/>
<img width="598" height="753" alt="Untitled" src="https://github.com/user-attachments/assets/b715c510-fa30-4f8e-b4ff-cb24fdab3960" /><p/>
Click on an Event using the D-pad (or use a virtual mouse) to view details:<br/>
<img width="598" height="754" alt="Untitled2" src="https://github.com/user-attachments/assets/82eccc9b-f926-46fe-a506-1afd17f8cc73" /><p/>
Editing an existing Event, optionally set to All Day and schedule alerts:<br/>
(scroll up and down to view or set more properties using D-Pad, type from keyboard or use voice dictation is you installed Google Voice on your phone)<br/>
<img width="598" height="754" alt="Untitled3" src="https://github.com/user-attachments/assets/2d603dd3-a34c-41ec-928f-be2e345ac314" /><br/>
<img width="598" height="754" alt="Untitled4" src="https://github.com/user-attachments/assets/e84ee1df-335c-4ba2-9a58-8c91e0536a28" /><br/>
<img width="618" height="756" alt="Untitled5" src="https://github.com/user-attachments/assets/2a36214f-034e-493f-897c-8a1da4147bf3" /><br/>
<img width="612" height="756" alt="Untitled6" src="https://github.com/user-attachments/assets/b7db818c-391c-472c-9db4-8f792336d435" /><br/>
<img width="612" height="756" alt="Untitled7" src="https://github.com/user-attachments/assets/f5a448e9-eda8-4f7e-a6c4-bc59ae81eee6" /><br/>
<img width="612" height="756" alt="Untitled8" src="https://github.com/user-attachments/assets/734b6289-8f20-418b-8871-34cb244b2ca5" /><br/>
<img width="589" height="756" alt="Untitled9" src="https://github.com/user-attachments/assets/8487189f-b114-4001-bc2c-184a9a2479c3" /><br/>
<p>&nbsp;</p>
Create A New Event:<br/>
<img width="601" height="757" alt="Untitled10" src="https://github.com/user-attachments/assets/89a4730f-e8a4-42af-a913-f8c651e7879f" /><br/>
<img width="601" height="757" alt="Untitled11" src="https://github.com/user-attachments/assets/83a40e1b-e828-4aa1-97de-c5b61ac8ed76" /><br/>
<img width="601" height="757" alt="Untitled12" src="https://github.com/user-attachments/assets/78af96ea-ba07-420c-b058-1ab5aa7d48ec" /><br/>
<p>&nbsp;</p>
Optionally show Holidays:<br/>
<img width="601" height="757" alt="Untitled13" src="https://github.com/user-attachments/assets/0533457c-49ce-482d-89c5-93a90eeb8aed" />


# Installation

### Option A — Install the APK directly

1. Download `flip2calendar-v1.0.apk` from the [latest release](https://github.com/jevdemon/flip2calendar/releases/latest)
2. Install via ADB:
```bash
adb install flip2calendar-v1.0.apk
```
Or enable **Install from unknown sources** on your device and open the APK directly.

3. On first launch the app will show a **Setup screen** — follow the steps below to create your Google OAuth credentials.

### Option B — Build from source

See the [Build from Source](#build-from-source) section below.

---

## Google OAuth Setup (Required)

Every user needs their own Google OAuth credentials. This is a one-time setup and takes about 5 minutes. The app's setup screen will guide you through it.

### Step 1 — Create a Google Cloud project

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Click the project dropdown at the top → **New Project**
3. Name it anything (e.g. "MyCalendar") → click **Create**

### Step 2 — Enable the Google Calendar API

1. In your new project, go to **APIs & Services → Library**
2. Search for **Google Calendar API**
3. Click on it → click **Enable**

### Step 3 — Configure the OAuth consent screen

1. Go to **APIs & Services → OAuth consent screen** (or **Audience** in the new UI)
2. Choose **External** → click **Create**
3. Fill in:
   - **App name**: anything you like (e.g. "MyCalendar")
   - **User support email**: your email
   - **Developer contact email**: your email
4. Click **Save and Continue** through the remaining steps
5. Go back to the **Audience** page and click **Publish App** → **Confirm**
   > Publishing removes the 7-day token expiry that applies to apps in testing mode

### Step 4 — Create OAuth credentials

1. Go to **APIs & Services → Credentials** (or **Clients** in the new UI)
2. Click **+ Create Credentials → OAuth client ID**
3. For **Application type** choose **Web application**
4. Name it anything (e.g. "MyCalendar")
5. Under **Authorized redirect URIs** click **+ Add URI** and enter exactly: http://localhost
6. Click **Create**
7. Copy the **Client ID** and **Client Secret** — you will need these in the app

### Step 5 — Enter credentials in the app

1. Launch Flip2Calendar — the Setup screen will appear on first launch
2. Enter your **Client ID** (ends in `.apps.googleusercontent.com`)
3. Enter your **Client Secret** (starts with `GOCSPX-`)
4. Tap **Save and Continue**
5. Your browser will open for Google sign-in — approve calendar access
6. The app will load your calendar

> Your credentials are stored locally on your device and never sent anywhere except to Google's OAuth servers.

---

## Build from Source

### Requirements

- Android Studio
- Android SDK API 30+
- A Google account

### Steps

1. Clone the repo:
```bash
git clone https://github.com/jevdemon/flip2calendar.git
cd flip2calendar
```

2. Open in Android Studio

3. Build and install:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. Follow the OAuth setup steps above when the app launches

---

## How OAuth Works on AOSP

Standard Android OAuth uses custom URI schemes (e.g. `myapp://`) but AOSP's intent system doesn't handle these reliably. This app uses a **Web Application** OAuth client with `http://localhost` as the redirect URI instead. An `AuthActivity` intercepts the redirect and forwards the authorization code to `MainActivity` for token exchange.

## Architecture

| Component | Role |
|---|---|
| `MainActivity` | Event list, OAuth flow, calendar fetching |
| `SetupActivity` | First-launch credential setup screen |
| `AuthActivity` | Intercepts OAuth redirect from browser |
| `EventEditActivity` | Create / edit / delete events |
| `ReminderSchedulerService` | Schedules AlarmManager alarms for reminders |
| `ReminderReceiver` | Posts notification when alarm fires |
| `BootReceiver` | Reschedules alarms after device reboot |

## Notifications Without Play Services

Google Calendar notifications normally rely on Firebase Cloud Messaging (FCM), which requires Play Services. This app schedules local `AlarmManager` alarms instead — they fire at the correct reminder time even when the app is closed, without any Google services.

## Tested On

- TCL Flip 2 running neutronscott's jailbreak (AOSP 11, 32-bit)
- Should work on any de-Googled Android device with API 30+

## Troubleshooting

**Auth loop — browser keeps reopening after sign-in**
Go to [myaccount.google.com/permissions](https://myaccount.google.com/permissions), remove the app's access, then relaunch the app and sign in again.

**"The OAuth client was deleted" error**
Your Client ID no longer exists in Google Cloud Console. Go back to Step 4 above and create new credentials, then re-enter them in the app's setup screen.

**"invalid_client" error**
Your Client Secret doesn't match. Go to Google Cloud Console → Clients → click your client → add a new secret and update the app.

**Notifications not firing**
Open the app at least once after install to trigger alarm scheduling. If the device was rebooted, the app reschedules automatically on next open.

## Contributing

Pull requests welcome. If you've tested on another device please open an issue to let others know.

## License

MIT — see [LICENSE](LICENSE)
