# NFCRobot — Robotik NFC Test Kontrol Paneli

Bu depo, bir masaüstü kontrol uygulaması ile ESP8266 (WiFi) ve STM32F103RB
(UART) tabanlı bir donanım zinciri arasındaki haberleşmeyi kurmayı hedefleyen
bir çalışmadır. Şu an itibarıyla **uçtan uca çalışan tek zincir**: masaüstü
uygulaması → WiFi (TCP) → ESP8266 → UART → STM32 → GPIO'ya bağlı bir test
LED'i. Nihai hedef, bu zincirin üzerine motorlu bir NFC test robotu inşa
etmek (bkz. "Bilinen sınırlamalar / sıradaki adımlar").

## Sistem mimarisi

```
[Masaüstü Uygulaması]  --WiFi TCP (port 5000)-->  [ESP8266, AP modu]  --UART (115200 8N1)-->  [STM32F103RB]  --GPIO-->  [Harici LED, PA4]
      (Java/JavaFX)                                  SSID: ESP_STM32_Bridge         D7/D8 <-> PA9/PA10 (çapraz)      Nucleo board
```

- Masaüstü uygulaması ESP8266'nın açtığı WiFi Access Point'e TCP ile bağlanır.
- ESP8266, TCP'den aldığı komutu UART üzerinden tek byte olarak STM32'ye iletir.
- STM32, USART1'den okuduğu byte'a göre PA4'e bağlı LED'i register seviyesinde açar/kapatır.

## Klasör yapısı

| Yol | İçerik |
|---|---|
| `src/main/java/com/visiumfarm/` | Masaüstü kontrol uygulaması (Java 17, JavaFX) |
| `esp8266/esp_blink/esp_blink.ino` | ESP8266 firmware (Arduino framework) |
| `stm32/stm32_uart_blinker/` | STM32CubeIDE projesi (register-level C) |
| `pom.xml` | Maven build tanımı (Java 17, JavaFX 21.0.2, jSerialComm 2.11.4) |

## Donanım ve pinout

### STM32F103RB (Nucleo-64)

- **Saat**: HSI (8 MHz) → /2 → PLL×4 → **16 MHz** SYSCLK/HCLK/PCLK1/PCLK2, FLASH_LATENCY_0.
  (`.ioc` içinde RCC ayarları, `SystemClock_Config()` HAL üzerinden kuruyor.)
- **PA4**: GPIO Output, Push-Pull, Low Speed (2 MHz), No pull. Harici LED'in
  anotu dirençle PA4'e, katotu GND'ye bağlı → **PA4 = HIGH demek LED yanık**.
- **PA9**: `USART1_TX` (Alternate Function Push-Pull).
- **PA10**: `USART1_RX` (Input Floating).
- **USART1**: Asenkron, **115200 baud, 8N1**, donanım akış kontrolü yok.
- NVIC'te `USART1_IRQn` açık görünür ama şu an **kullanılmıyor** — RX işlemi
  interrupt ile değil, `main.c` içindeki polling döngüsüyle yapılıyor
  (`HAL_UART_IRQHandler` çağrılır ama RXNEIE hiç enable edilmediği için pratikte
  tetiklenmez). İleride interrupt-driven RX'e geçilirse bu ayar zaten hazır.

### ESP8266 (NodeMCU/Amica tarzı kart)

- **WiFi**: Access Point modu. SSID `ESP_STM32_Bridge`, şifre `12345678`,
  statik IP `192.168.2.100/24`, kanal 1, maksimum 2 istemci, TX gücü 16 dBm
  (brown-out riskini azaltmak için düşürülmüş).
- **TCP sunucu**: port `5000`. Masaüstü uygulaması buraya bağlanır.
- **UART0 swap**: `setup()` içinde `Serial.begin(115200)` sonrası
  `Serial.swap()` çağrılır → UART0 fiziksel pinleri **GPIO1/GPIO3'ten
  GPIO15(D8, TXD0)/GPIO13(D7, RXD0)'e taşınır**. Bunun nedeni: GPIO1/GPIO3
  aynı zamanda kartın USB-seri (flash/programlama) hattı; STM32 kabloları o
  pinlere bağlı kalsaydı, USB bağlıyken iki sürücü aynı hattı sürmeye çalışıp
  çakışırdı. Swap sonrası GPIO1/3 tamamen boşta kalır, USB ile flaşlama STM32
  kabloları takılıyken de güvenlidir.
- **Serial1 (GPIO2, TX-only)**: Debug logları (`"Client baglandi"`,
  `"Client ayrildi"`, `"TCP sunucu port 5000 START"`) buraya yönlendirildi.
  **Önemli**: bu pine şu an fiziksel olarak hiçbir şey bağlı değil, yani bu
  loglar hiçbir yerde görünmüyor (havaya gidiyor). Görmek için GPIO2+GND'ye
  ayrı bir USB-TTL adaptör bağlamak, ya da bu logları TCP üzerinden masaüstü
  uygulamasına iletmek gerekir (bu ikinci seçenek konuşuldu, henüz
  uygulanmadı).
- `LED_BUILTIN`: kartın kendi göstergesi, Amica'da **aktif LOW**.

### Kartlar arası UART bağlantısı

| ESP8266 | STM32 |
|---|---|
| D7 / GPIO13 (RXD0, swap sonrası) | PA9 (USART1_TX) |
| D8 / GPIO15 (TXD0, swap sonrası) | PA10 (USART1_RX) |
| GND | GND |

Her iki taraf da 3.3V lojik seviyesinde çalışıyor, seviye çevirici gerekmiyor.
VCC hatları birbirine bağlanmıyor, her kart kendi USB'sinden besleniyor.

## Komut protokolü (uçtan uca akış)

1. Masaüstü uygulaması ESP8266'ya TCP üzerinden `"1"`, `"0"` veya `"PING"`
   (satır sonu `\n`) gönderir.
2. ESP8266 `handleCommand()`:
   - `"1"` → yerel `LED_BUILTIN`'i yakar, `Serial.write('1')` ile STM32'ye
     tek byte gönderir, TCP istemcisine `"OK LED ON"` yanıtı döner.
   - `"0"` → aynı şekilde `Serial.write('0')`, yanıt `"OK LED OFF"`.
   - `"PING"` → sadece `"PONG"` döner (masaüstü uygulamasının heartbeat'i,
     STM32'ye iletilmez).
   - Tanınmayan komut → `"ERROR UNSUPPORTED COMMAND"`.
3. STM32 `main.c` içindeki `UART1_ReadByte()`, `USART1->SR` RXNE bayrağını
   polling ile bekler, `USART1->DR`'den byte'ı okur.
4. Ana döngü: byte `'1'` ise `GPIOA->BSRR = LED_PIN_MASK` (PA4 HIGH, LED
   yanar), `'0'` ise `GPIOA->BSRR = LED_PIN_MASK << 16` (PA4 LOW, LED söner),
   başka bir byte gelirse yok sayılır.

## STM32 firmware yaklaşımı: register-level

CubeMX/HAL tamamen devre dışı bırakılmadı — pragmatik bir ayrım yapıldı:

- **CubeMX/HAL katmanı** (`SystemClock_Config`, `MX_GPIO_Init`,
  `MX_USART1_UART_Init`): saat ağacı, pin mux ve peripheral registerlarının
  ilk kurulumu için kullanılıyor. Bu fonksiyonlar `.ioc` her
  "Generate Code" işleminde otomatik yeniden üretiliyor.
- **Register-level katman** (`USER CODE` bloklarında, kalıcı —
  `Core/Src/main.c`): `LED_Init()`, `UART1_ReadByte()` ve ana döngüdeki komut
  işleme mantığı tamamen CMSIS register erişimiyle yazıldı
  (`RCC->APB2ENR`, `GPIOA->CRL`, `GPIOA->BSRR`, `USART1->SR`, `USART1->DR`).
  `HAL_GPIO_WritePin`, `HAL_UART_Receive`, `HAL_Delay` gibi HAL çağrıları
  çalışma zamanı mantığında **kullanılmıyor**.

## Masaüstü uygulaması (Java / JavaFX)

- `MainApp` → pencereyi açar, `UIBuilder.build()` ile tüm arayüzü kurar.
- `UIBuilder`: iki ana panel barındırır:
  - **"ESP8266 WIFI LED KONTROLÜ"** — şu an çalışan zincirin arayüzü.
    `EspClientManager` üzerinden IP/port ile TCP bağlantısı kurar, LED
    ON/OFF butonları `sendLedCommand("1"/"0")` çağırır, 3 saniyede bir
    PING/PONG heartbeat atar.
  - **"MANUEL MOTOR KONTROLÜ (DPAD)"** — `SerialManager` (jSerialComm) +
    `CommandManager` üzerinden doğrudan bir seri porta `"YÖN:ADIM"` (ör.
    `"ILERI:5"`) veya `"HOME"` komutları gönderen panel. Bu, **ileride
    eklenecek motorlu NFC test robotu için hazırlanmış ama henüz gerçek
    donanıma bağlanmamış** ayrı bir alt sistem; şu anki ESP8266/STM32 LED
    zincirinden bağımsızdır. Arayüzde tuhaf görünebilecek bir detay: bu panel
    WiFi (ESP) bağlantısı kurulana kadar devre dışı bırakılıyor.
- `LogManager`: uygulama içi log alanını (TextArea) yönetir.
- `repodeneme.java`: boş, kullanılmayan yer tutucu sınıf.

## Bilinen sınırlamalar / sıradaki adımlar

- STM32 tarafında tek byte'lık basit protokol var; framing/overrun hata
  kontrolü yok (şimdilik gerekli görülmedi).
- ESP8266 debug logları (`Serial1`/GPIO2) şu an fiziksel olarak izlenemiyor;
- Manuel motor kontrol paneli (DPAD/HOME) gerçek donanıma bağlı değil,
  ileriye dönük bir arayüz.
