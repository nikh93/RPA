#!/usr/bin/env bash
# Richtet den Raspberry Pi als Kamera-Server ein.
# Aufruf auf dem Pi (im Ordner kamera-system/pi):   sudo ./install.sh
#
# Was passiert:
#   1. ffmpeg installieren (für Standbilder und ESP32-CAM-Umwandlung)
#   2. go2rtc herunterladen nach /usr/local/bin
#   3. Konfiguration nach /etc/go2rtc kopieren (vorhandene bleibt erhalten)
#   4. go2rtc als Dienst starten (startet bei jedem Booten automatisch)
#   5. Tailscale installieren (Zugriff von unterwegs, ohne Portfreigabe)
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Bitte mit sudo ausführen: sudo ./install.sh" >&2
  exit 1
fi

cd "$(dirname "$0")"

echo "==> 1/5 ffmpeg installieren"
apt-get update
apt-get install -y ffmpeg curl

echo "==> 2/5 go2rtc herunterladen"
case "$(uname -m)" in
  aarch64) ARCH=arm64 ;;
  armv7l)  ARCH=arm ;;
  armv6l)  ARCH=armv6 ;;
  x86_64)  ARCH=amd64 ;;
  *) echo "Unbekannte Architektur: $(uname -m)" >&2; exit 1 ;;
esac
curl -fL -o /usr/local/bin/go2rtc \
  "https://github.com/AlexxIT/go2rtc/releases/latest/download/go2rtc_linux_${ARCH}"
chmod +x /usr/local/bin/go2rtc
/usr/local/bin/go2rtc -version || true

echo "==> 3/5 Konfiguration"
id go2rtc >/dev/null 2>&1 || useradd --system --no-create-home --groups video go2rtc
mkdir -p /etc/go2rtc
if [[ -f /etc/go2rtc/go2rtc.yaml ]]; then
  echo "    /etc/go2rtc/go2rtc.yaml existiert schon - wird nicht überschrieben."
else
  cp go2rtc.yaml /etc/go2rtc/go2rtc.yaml
fi
chown -R go2rtc:go2rtc /etc/go2rtc
chmod 600 /etc/go2rtc/go2rtc.yaml

echo "==> 4/5 Dienst einrichten"
cp go2rtc.service /etc/systemd/system/go2rtc.service
systemctl daemon-reload
systemctl enable --now go2rtc
systemctl restart go2rtc

echo "==> 5/5 Tailscale (Fernzugriff)"
if ! command -v tailscale >/dev/null 2>&1; then
  curl -fsSL https://tailscale.com/install.sh | sh
fi
echo
echo "Fertig. Nächste Schritte:"
echo "  1. Passwörter und Kamera-IPs anpassen:  sudo nano /etc/go2rtc/go2rtc.yaml"
echo "     danach:                               sudo systemctl restart go2rtc"
echo "  2. Tailscale anmelden:                   sudo tailscale up"
echo "     Tailscale-IP des Pi anzeigen:         tailscale ip -4"
echo "  3. Im WLAN testen (Browser am PC):       http://$(hostname -I | awk '{print $1}'):1984"
