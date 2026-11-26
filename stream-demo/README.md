# StreamDemo – Handy-Kamera live auf ein zweites Handy

Funktioniert komplett ohne Raspberry Pi oder Server: Die beiden Handys verbinden sich direkt im lokalen Netz.

Kleine Android-Demo (Kotlin, Jetpack Compose, CameraX) mit zwei Modi:

| Modus | Was passiert |
|-------|--------------|
| **Stream** | Kamera-Vorschau. „Aufnahme starten“ öffnet einen Server auf **Port 8080** und sendet das Kamerabild live. Angezeigt werden die IP-Adresse(n) des Geräts und eine Statusanzeige: gelb „Warte auf Empfänger“, grün „Empfänger verbunden“ mit der IP des Control-Handys. Oben rechts lässt sich zwischen Front- und Rückkamera wechseln. |
| **Control** | IP-Adresse (und Port) des Stream-Geräts eingeben → „Verbinden“ → das Live-Bild erscheint mit fps-Anzeige. Die zuletzt benutzte IP wird gespeichert. |

Beide Geräte müssen im **selben WLAN** sein (oder eins macht einen Hotspot, das andere verbindet sich).

## Wie wird gestreamt?

Als **MJPEG über HTTP**: Jedes Kamerabild wird zu einem JPEG (640×480, max. 15 fps)
und nacheinander in einer `multipart/x-mixed-replace`-Antwort gesendet.
Das ist einfach, hat wenig Verzögerung und funktioniert ohne Zusatzbibliotheken.
Der Stream lässt sich deshalb auch in **jedem Browser** (`http://<ip>:8080`) oder in VLC öffnen.

```
[Stream-Handy]  CameraX → JPEG → MjpegServer :8080  ──WLAN──▶  MjpegClient → Bild  [Control-Handy]
```

| Datei | Aufgabe |
|-------|---------|
| `net/MjpegServer.kt` | HTTP-Server, verteilt das jeweils neueste Bild an alle Zuschauer |
| `net/MjpegReader.kt` | Liest den Stream (Content-Length oder JPEG-Endmarker), `MjpegClient` baut die Verbindung auf |
| `stream/JpegFrameEncoder.kt` | CameraX-Analyzer: Bild drehen, als JPEG kodieren (nur wenn jemand zuschaut) |
| `stream/StreamScreen.kt` | Stream-Modus: Berechtigung, Kamera, Start/Stopp, IP-Anzeige |
| `control/ControlScreen.kt` | Control-Modus: IP-Eingabe, Empfang, Anzeige |

## Bauen

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # Tests für Server und Client
```
Oder den Ordner in Android Studio öffnen und ▶ Run.

## Grenzen der Demo

- Unverschlüsselt und ohne Passwort – jeder im selben WLAN kann zuschauen. Nur im eigenen Netz benutzen.
- Gestreamt wird nur, solange die App im Stream-Modus im Vordergrund ist (kein Hintergrunddienst).
- Kein Ton. MJPEG braucht mehr Bandbreite als H.264 (bei 640×480/15 fps ca. 3–6 Mbit/s).
