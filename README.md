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
