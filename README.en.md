<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="180" alt="NotiHook Icon" />
</p>

<div align="center">

# NotiHook

</div>

<p align="center">
  <strong>Catch Notifications. Trigger APIs.</strong>
</p>

<div align="center">

<a href="./README.md">README Bahasa Indonesia</a>

</div>

<p align="center">
  <img alt="API" src="https://img.shields.io/badge/API%2029+-34A853?logo=android&logoColor=white&style=for-the-badge"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge"/>
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white"/>
  <img alt="Material 3" src="https://img.shields.io/badge/Material%203-5F6368?style=for-the-badge&logo=materialdesign&logoColor=white"/>
  <br/>
  <br/>
  <img alt="Downloads" src="https://img.shields.io/github/downloads/rickicode/NotiHook/total?color=aeff4d&style=for-the-badge&logo=github&label=Downloads&labelColor=4b731a"/>
  <a href="https://github.com/rickicode/NotiHook/stargazers">
    <img alt="GitHub Stars" src="https://img.shields.io/github/stars/rickicode/NotiHook?color=ffff00&style=for-the-badge&logo=github&labelColor=a1a116"/>
  </a>
  <a href="https://github.com/rickicode/NotiHook/releases/latest">
    <img alt="GitHub Release" src="https://img.shields.io/github/v/release/rickicode/NotiHook?color=a1168e&include_prereleases&logo=github&style=for-the-badge&labelColor=700f63"/>
  </a>
</p>

<p align="center">
  <a href="https://github.com/rickicode/NotiHook/releases/latest">
    <img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" height="80" alt="Get it on GitHub Releases" />
  </a>
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/rickicode/NotiHook/">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="60" alt="Get it on Obtainium" />
  </a>
</p>

<p align="center">
  <img src="docs/images/overview-horizontal.png" alt="NotiHook Overview" width="960" />
</p>

NotiHook is an open-source Android app for developers built to capture notifications from selected apps, store them locally, and optionally forward them to API endpoints without adding heavy operational overhead. Its release APK also stays small at around 6 MB.

## Main Features

- Capture notifications from selected apps
- Keep the release APK lightweight at around 6 MB
- Store notification history locally for auditing and review
- Optionally forward notifications to an API endpoint
- Configure URL, method, payload type, and additional values per app
- Verify the required Android system permissions so the catcher can keep running in the background

## Purpose

NotiHook is built for developers who want to treat Android notifications as an operational data source. It is suitable for notification logging, lightweight webhook integration, backend automation, or transaction monitoring from specific apps without building native integrations for each vendor.

## Install via Obtainium

NotiHook can also be installed and updated through Obtainium because its release source is GitHub Releases.

Source URL:

```text
https://github.com/rickicode/NotiHook
```

Quick steps:

1. Open Obtainium
2. Add a new app from a GitHub source
3. Enter the repo URL `https://github.com/rickicode/NotiHook`
4. Save it, then install the latest release

## Configuration

### Global

- `User-Agent`

Default:

```text
NotiHook/1.0
```

### Per App

- `Enable notification catch`
- `Enable forward`
- `API URL`
- `HTTP Method`
- `Payload Type`
- `Additional Values`

## Notification Payload

Primary notification fields:

- `title`
- `text`
- `bigtext`
- `subtext`
- `infotext`
- `name`
- `pkg`

Example payload:

```json
{
  "apikey": "hijilabs",
  "bigtext": "Payment transaction invoice HJ4133362026031119585662 for Rp 111 via static QRIS was successfully paid. RRN : 1mi5mnz51389",
  "infotext": "",
  "name": "bale merchant",
  "pkg": "com.btn.btnmerchant",
  "subtext": "",
  "text": "Payment transaction invoice HJ4133362026031119585662 for Rp 111 via static QRIS was successfully paid. RRN : 1mi5mnz51389",
  "title": "Static QR Merchant Payment"
}
```

## Forward Behavior

Forwarding only runs when all of these are true:

- `notification catch` is enabled
- `forward` is enabled
- `API URL` is valid
- `additional values` are valid

If payload type is `JSON`, the request uses:

```text
Content-Type: application/json; charset=utf-8
```

If payload type is `FORM`, the request uses:

```text
Content-Type: application/x-www-form-urlencoded
```

## Permissions Used

Because this app operates at Android system level, it uses:

- `Notification Listener`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `QUERY_ALL_PACKAGES`
- `INTERNET`

## Notes

- `QUERY_ALL_PACKAGES` is currently used so app listing remains more complete on some devices, including Samsung.
- Direct APK distribution outside the Play Store may be flagged more aggressively by Play Protect because the app handles notifications, background behavior, and API forwarding.
