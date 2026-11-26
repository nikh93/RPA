# HeimKamera – 3 Zimmer, 1 Raspberry Pi, 1 Android-App

```
 Zimmer 1   Zimmer 2   Zimmer 3
 [Kamera]   [Kamera]   [Kamera]        Tapo C100 (RTSP), ESP32-CAM (MJPEG) oder Pi-Kameramodul
     \         |         /
      \        |        /     Heim-WLAN
       v       v       v
     +-----------------------+
     |  Raspberry Pi (WLAN)  |   go2rtc: sammelt alle Kameras ein
     |  :1984 API/Standbild  |   - http://PI:1984/api/streams      -> Kameraliste
     |  :8554 RTSP live      |   - http://PI:1984/api/frame.jpeg   -> Vorschaubild
     +-----------+-----------+   - rtsp://PI:8554/<name>            -> Live-Video
                 |
                 |  Tailscale (verschlüsselter Tunnel, kostenlos, keine Portfreigabe)
                 v
        +-----------------+
        |  Handy-App      |   Kotlin / Jetpack Compose / Media3
        |  "HeimKamera"   |   Übersicht mit Vorschaubildern -> Tippen = Live-Bild
        +-----------------+
```

Die Kameras melden sich **nur beim Pi**. Das Handy spricht **nur mit dem Pi**, zuhause
genauso wie unterwegs (über Tailscale). Kein Cloud-Dienst, keine offenen Ports am Router.

| Ordner     | Inhalt |
|------------|--------|
| `pi/`      | `install.sh` (Einrichtung), `go2rtc.yaml` (Kameras + Passwort), `go2rtc.service` |
| `android/` | Android-Projekt (Kotlin) – in Android Studio öffnen |

---

## 1. Raspberry Pi einrichten (ca. 20 Minuten)

1. **Raspberry Pi OS Lite (64-bit)** mit dem [Raspberry Pi Imager](https://www.raspberrypi.com/software/)
   auf die SD-Karte schreiben. Im Imager unter „Einstellungen bearbeiten“:
   Hostname `kamera-pi`, Benutzer + Passwort, **WLAN-Name + Passwort**, **SSH aktivieren**.
2. Pi starten, vom PC aus verbinden: `ssh <benutzer>@kamera-pi.local`
3. Diesen Ordner auf den Pi holen und Installation starten:
   ```bash
   sudo apt-get install -y git
   git clone https://github.com/nikh93/RPA.git
   cd RPA/kamera-system/pi
   sudo ./install.sh
   ```
   Ist das Repository privat, kopierst du den Ordner stattdessen vom PC aus:
   `scp -r kamera-system/pi <benutzer>@kamera-pi.local:~/` und startest dann auf dem Pi
   `cd ~/pi && sudo ./install.sh`.
4. Kameras und Passwort eintragen:
   ```bash
   sudo nano /etc/go2rtc/go2rtc.yaml      # BITTE_AENDERN ersetzen, Kamera-IPs anpassen
   sudo systemctl restart go2rtc
   ```
5. **Test im Heimnetz:** Am PC im Browser `http://kamera-pi.local:1984` öffnen
   (Benutzer/Passwort aus der yaml). Dort siehst du alle 3 Kameras und kannst sie abspielen.
6. **Fernzugriff einrichten:**
   ```bash
   sudo tailscale up        # zeigt einen Link -> im Browser mit Google/Microsoft/GitHub anmelden
   tailscale ip -4          # z. B. 100.101.102.103  <- diese Adresse kommt in die App
   ```

> Tipp: Im Router dem Pi und den Kameras **feste IP-Adressen** geben (DHCP-Reservierung),
> sonst stimmen die Adressen in `go2rtc.yaml` nach einem Router-Neustart evtl. nicht mehr.

## 2. Kameras vorbereiten

**Tapo C100 / C200 (empfohlen)**
1. Mit der Tapo-App ins WLAN bringen.
2. In der Tapo-App: Kamera → Einstellungen → Erweiterte Einstellungen → **Kamerakonto** anlegen.
   Diese Zugangsdaten kommen in die RTSP-Zeile in `go2rtc.yaml`.
3. **Mikrofon ausschalten** (rechtlich sicherer, siehe unten).
4. Optional, aber empfohlen: im Router den Internetzugang der Kameras sperren –
   RTSP zum Pi funktioniert trotzdem, die Bilder gehen dann nicht mehr in die TP-Link-Cloud.

**ESP32-CAM (billigste Variante)**
1. In der Arduino-IDE das ESP32-Board-Paket installieren, Beispiel
   *ESP32 → Camera → CameraWebServer* öffnen, Modell `CAMERA_MODEL_AI_THINKER` wählen,
   WLAN-Daten eintragen, flashen (mit ESP32-CAM-MB-Adapter einfach per USB).
2. Der Stream liegt dann unter `http://<ip>:81/stream` – genau so in `go2rtc.yaml` eintragen.
   Die Zeile `ffmpeg:<name>#video=h264` wandelt das Bild für die App um.

## 3. Handy einrichten

1. **Tailscale** aus dem Play Store installieren und mit **demselben Konto** anmelden wie am Pi.
2. App installieren (siehe Abschnitt 4).
3. App öffnen → Adresse = Tailscale-IP des Pi (`100.x.y.z`), Benutzer/Passwort aus `go2rtc.yaml`
   → **Verbindung testen** → **Speichern**.

Fertig: Die Übersicht zeigt alle Kameras mit Vorschaubild (alle 3 s aktualisiert),
ein Tipp öffnet das Live-Bild. Das funktioniert im WLAN und über Mobilfunk.

## 4. App bauen und installieren

**Mit Android Studio (am einfachsten):** Ordner `android/` öffnen → Handy per USB anschließen
(USB-Debugging an) → ▶ Run.

**Ohne Android Studio:**
```bash
cd android
./gradlew assembleDebug          # braucht JDK 17+ und das Android SDK (ANDROID_HOME)
# APK: app/build/outputs/apk/debug/app-debug.apk -> aufs Handy kopieren und installieren
```

Tests: `./gradlew testDebugUnitTest`

### Aufbau der App

| Datei | Aufgabe |
|-------|---------|
| `Go2RtcClient.kt` | Kameraliste, Standbilder (HTTP + Basic-Auth), RTSP-Adresse |
| `PiConfig.kt` | Verbindungsdaten, gespeichert in den App-Einstellungen |
| `ui/CameraListScreen.kt` | Übersicht mit Vorschaubildern (lädt nur, solange die App sichtbar ist) |
| `ui/LiveScreen.kt` | Live-Bild mit Media3/ExoPlayer, RTSP über TCP, kleiner Puffer = wenig Verzögerung |
| `ui/SettingsScreen.kt` | Adresse/Benutzer/Passwort eingeben, Verbindung testen |

## Fehlerbehebung

| Problem | Lösung |
|---------|--------|
| „Pi nicht erreichbar“ | Tailscale auf dem Handy aktiv? Pi an? `systemctl status go2rtc` auf dem Pi |
| „Benutzername oder Passwort falsch“ | Werte unter `api:` in `/etc/go2rtc/go2rtc.yaml` prüfen |
| „Kamera nicht erreichbar“ in einer Kachel | Kamera-IP/Zugangsdaten in der yaml prüfen; im Browser unter `http://kamera-pi.local:1984` testen |
| Live-Bild ruckelt unterwegs | In der yaml `stream1` → `stream2` (360p) bei der Tapo verwenden |
| Logs ansehen | `journalctl -u go2rtc -f` |

## Sicherheit & Recht

- Zugriff von außen nur über Tailscale (WireGuard-verschlüsselt). **Keine Portfreigabe am Router einrichten.**
- Im Heimnetz läuft HTTP/RTSP unverschlüsselt (nur durch das Passwort geschützt) – deshalb ein eigenes, starkes Passwort in `go2rtc.yaml` setzen.
- Mitbewohner, Besucher, Putzkraft etc. über die Kameras informieren. Mikrofone aus:
  Das Aufnehmen nicht-öffentlich gesprochener Worte kann nach § 201 StGB strafbar sein.

## Mögliche Erweiterungen

- Aufnahmen bei Bewegung (z. B. motionEye oder Frigate neben go2rtc) und Liste der Clips in der App
- Push-Benachrichtigung bei Bewegung
- Gegensprechen / Ton (go2rtc kann das, App spielt derzeit ohne Ton ab)
