#include <ESP8266WiFi.h>

// WIFI PARAMETRE
const char* ssid = "ESP_STM32_Bridge";
const char* password = "12345678";  // WPA2 min 8 karakter

// Define the static IP, Gateway, and Subnet
IPAddress apIP(192, 168, 2, 100);
IPAddress gateway(192, 168, 2, 100);
IPAddress subnet(255, 255, 255, 0);

const int wifiChannel = 1;
const int maxConnections = 2;
const float txPowerDbm = 16.0;

// TCP komut sunucusu
WiFiServer server(5000);

const int servoLedPin = D5; // Amica'da D5 = GPIO14
const unsigned long servoLedDurationMs = 1500;
unsigned long servoLedOffAt = 0;
bool servoLedActive = false;

void setup() {
  Serial.begin(115200); // UART0 TX0=GPIO1 ve RX0=GPIO3 ile BASLAR (USB-seri hatti)
  Serial.println("Boot, USB-seri hatti uzerinden");
  Serial.flush(); // swap oncesi mesaji GPIO1/3 uzerinden tamamen gonder

  Serial.swap(); // UART0'i GPIO15 -> TXD0 ve GPIO13 -> RXD0'a tasir (STM32 hatti)
  /*
  Swap sonrasi Serial, STM32'nin komut hatti olur; debug metni buraya YAZILMAZ,
  aksi halde metin icindeki 'A'/'B'/'C' karakterleri STM32'de yanlis komut sanilir.
  Debug icin GPIO2 (Serial1, TX-only) kullanilir.
  */
  Serial1.begin(115200);

  pinMode(servoLedPin, OUTPUT);
  digitalWrite(servoLedPin, LOW); // harici LED: LOW = sonuk (baslangic durumu)

  WiFi.persistent(false);          // AP ayarlarini flash'a yazma
  WiFi.setSleepMode(WIFI_NONE_SLEEP);
  WiFi.mode(WIFI_AP);              // sadece AP modu
  WiFi.softAPConfig(apIP, gateway, subnet);
  WiFi.softAP(ssid, password, wifiChannel, false, maxConnections);

  /*
  Brownout, elektrik şebekelerindeki voltajın tamamen sıfırlanmadan, güvenli çalışma seviyesinin altına geçici olarak düşmesi durumudur. 
  IoT (Nesnelerin İnterneti) bağlamında ise mikrodenetleyicilerin ve sensörlerin hatalı çalışmasına, veri kaybına ve 
  cihaz kilitlenmelerine yol açan kritik bir güç problemidir.
  */

  WiFi.setOutputPower(txPowerDbm); // 16 dBm, brown-out riskini azaltir

  server.begin();
  Serial1.println("TCP sunucu port 5000 START");
}

void startServoLedPulse() {
  digitalWrite(servoLedPin, HIGH); // harici LED: HIGH = yanik
  servoLedOffAt = millis() + servoLedDurationMs;
  servoLedActive = true;
}

void updateServoLed() {
  if (servoLedActive && (long)(millis() - servoLedOffAt) >= 0) {
    digitalWrite(servoLedPin, LOW); // LED sonuk
    servoLedActive = false;
  }
}

void handleCommand(WiFiClient& client, String cmd) {
  cmd.trim();

  if (cmd == "HOME") {
    Serial.write('B'); // STM32'ye ilet (servo HOME = 90 derece)
    startServoLedPulse();
    client.println("OK HOME");
  } else if (cmd == "TARGET") {
    Serial.write('C'); // STM32'ye ilet (servo TARGET = 180 derece)
    startServoLedPulse();
    client.println("OK TARGET");
  } else if (cmd == "PING") {
    client.println("PONG"); // masaustu tarafi baglantiyi canli tutmak/dogrulamak icin kullanir
  } else if (cmd.length() > 0) {
    client.println("ERROR UNSUPPORTED COMMAND");
  }
}

void loop() {
  WiFiClient client = server.accept();

  if (client) {
    Serial1.println("Client baglandi");
    client.keepAlive(10, 10, 2); // 10sn idle sonrasi 10sn arayla 2 probe; cevap yoksa baglanti vefad
    while (client.connected()) {
      if (client.available()) {
        String cmd = client.readStringUntil('\n');
        handleCommand(client, cmd);
      }
      updateServoLed();
      yield(); // watchdog'u besle, arka plan wifi islemlerine izin ver
    }
    client.stop();
    Serial1.println("Client ayrildi");
  }

  updateServoLed();
  yield();
}

/*
NOT: yield()
Neden yield() Kullanırız? (WDT Reset Problemi)
ESP8266 gibi kartlarda bir Watchdog Timer bulunur.
Bu donanım, kodun sonsuz döngüye girip kilitlenmesini önlemek için sürekli geri sayım yapar.
Eğer kodun işlemciyi çok uzun süre meşgul ederse ve arka plan görevlerine sıra gelmezse,
WDT sistemin donduğunu varsayar ve kartı otomatik olarak resetler.
*/