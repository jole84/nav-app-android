# Nav-App-Android

Android WebView wrapper that injects background location updates into a hosted OpenLayers web app.

This repository contains a minimal Android Studio project that: 

- Starts a native foreground Location Service (Fused Location Provider) to receive continuous GPS updates.
- Hosts a WebView that loads your hosted web app at https://jole84.se/nav-app/index.html and forwards location updates into the page via evaluateJavascript (calling `window.onLocationUpdate(lat, lng)`).
- Uses a BroadcastReceiver in MainActivity to receive location broadcasts from the Service and queue updates until the page signals it's ready.

Notes
- Background location requires runtime permission `ACCESS_FINE_LOCATION` and (on Android 10+/API 29+) `ACCESS_BACKGROUND_LOCATION` if you need updates while the app is not in the foreground.
- The WebView JavaScript will not run while the Activity is paused or the device is asleep; the native service continues to collect locations. MainActivity queues updates and flushes them when the page is ready.
- Open this project in Android Studio and let it download Gradle and SDK components as needed.

How to run
1. Clone the repo and open it in Android Studio.
2. Build and run on a real Android device (emulators may not behave the same for location/background behavior).
3. Grant location permissions when the app asks.

Files of interest
- app/src/main/java/com/jole84/navapp/MainActivity.kt
- app/src/main/java/com/jole84/navapp/LocationUpdatesService.kt
- app/src/main/assets/www/index.html (fallback local OpenLayers page)

Replace the web app URL in MainActivity if you want to point to a different host.

License: MIT
