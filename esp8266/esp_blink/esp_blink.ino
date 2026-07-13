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

void setup() {
  Serial.begin(115200); // UART0 TX0=GPIO1 ve RX0=GPIO3 BASLAR

  /*
  Serial.swap() sorun çıkardı, bakılmalı. Su anda debug/programlama ile uart pinleri cakismada, STM32'ye
  emir verilirken gercek zamanli programlama debug atamayiz.
  */

  // Serial.swap(); // UART GPIO5 -> TX ve GPIO13 -> RX Olarak degistirmek

  /*
  GPIO15, ESP8266'nın boot mode pinidir. (boot sırasında LOW olmalı).
  STM32 tarafında bu pime bağlı hattın boot anında yanlışlıkla HIGH
  cekmediginden emin olun, flash/boot moduna gecisler sorun yaratabilir.
  */

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, HIGH); // Amica'da HIGH = LED sonuk (baslangic durumu)

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
  Serial.println("TCP sunucu port 5000 START");
}

void handleCommand(WiFiClient& client, String cmd) {
  cmd.trim();

  if (cmd == "1") {
    digitalWrite(LED_BUILTIN, LOW); // Amica'da LOW = LED yanik
    client.println("OK LED ON");
  } else if (cmd == "0") {
    digitalWrite(LED_BUILTIN, HIGH);
    client.println("OK LED OFF");
  } else if (cmd == "PING") {
    client.println("PONG"); // masaustu tarafi baglantiyi canli tutmak/dogrulamak icin kullanir
  } else if (cmd.length() > 0) {
    client.println("ERROR UNSUPPORTED COMMAND");
  }
}

void loop() {
  WiFiClient client = server.accept();

  if (client) {
    Serial.println("Client baglandi");
    client.keepAlive(10, 4, 3); // 10sn idle sonrasi 4sn arayla 3 probe; cevap yoksa baglanti vefad
    while (client.connected()) {
      if (client.available()) {
        String cmd = client.readStringUntil('\n');
        handleCommand(client, cmd);
      }
      yield(); // watchdog'u besle, arka plan wifi islemlerine izin ver
    }
    client.stop();
    Serial.println("Client ayrildi");
  }

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