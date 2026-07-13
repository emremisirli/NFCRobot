# ESP8266 LED Kontrol Sistemi — Java İstemci Entegrasyon Rehberi

Bu doküman, `esp_blink.ino` dosyasındaki firmware'in nasıl çalıştığını ve bir Java
istemci (arayüz) geliştirilirken nelere dikkat edilmesi gerektiğini açıklar.

## 1. Sistemin Çalışma Mantığı

### 1.1 Genel Mimari

```
[Java Uygulaması] --WiFi (TCP)--> [ESP8266 NodeMCU Amica] --(ileride)--> [STM32F103RB Nucleo]
```

Şu an sistemde sadece ESP8266 var ve kendi üzerindeki `LED_BUILTIN`'i (dahili LED)
kontrol ediyor. STM32 entegrasyonu ileride eklenecek, bu yüzden Java tarafı bugün
"LED aç / LED kapat" komutlarını gönderip cevap almaya odaklanmalı.

### 1.2 WiFi Modu: Access Point (AP)

ESP8266, bir WiFi ağına *bağlanmıyor* — kendisi bir WiFi ağı (hotspot) **yayınlıyor**:

- **SSID:** `ESP_STM32_Bridge`
- **Şifre:** `12345678`
- **IP adresi (ESP'nin kendisi):** `192.168.2.100`
- **Subnet:** `255.255.255.0`
- **Kanal:** 1
- **Maks. eş zamanlı istemci:** 2

Java arkadaşının bilgisayarı/telefonu, çalışan uygulamayla test yapmadan önce
**bu WiFi ağına bağlanmalı**. Yani Java uygulaması kendi ağınızdaki bir cihazda
değil, ESP'nin yayınladığı ağa bağlı bir cihazda çalışmalı.

> Not: `maxConnections = 2` olduğu için aynı anda en fazla 2 cihaz bu AP'ye
> bağlanabilir. Geliştirme sırasında hem laptop hem telefon bağlıysa bu sınıra dikkat.

### 1.3 İletişim Protokolü: Ham TCP Soket (port 5000)

ESP, `192.168.2.100:5000` adresinde bir TCP sunucusu dinliyor (HTTP değil, düz
TCP). Java tarafında bir `java.net.Socket` ile bağlanılmalı — REST/HTTP client
kütüphanelerine gerek yok.

**Komut formatı:** Satır sonu (`\n`) ile biten düz metin komutları.

| Gönderilen Komut | Davranış                          | Cevap                        |
|-------------------|-----------------------------------|-------------------------------|
| `1\n`             | LED'i yakar (`digitalWrite LOW`)  | `OK LED ON`                  |
| `0\n`             | LED'i söndürür (`digitalWrite HIGH`) | `OK LED OFF`               |
| başka herhangi bir şey | hiçbir şey yapmaz              | `ERROR UNSUPPORTED COMMAND`  |

Firmware `client.readStringUntil('\n')` kullandığı için **her komutun sonunda
mutlaka `\n` gönderilmeli**, yoksa ESP komutu okumak için bekler ve satır hiç
tamamlanmaz.

> Dikkat: Amica kartında dahili LED **ters mantıkla** çalışıyor —
> `LOW` = LED yanık, `HIGH` = LED sönük. Bu firmware seviyesinde zaten
> soyutlanmış durumda (Java tarafı sadece `1`/`0` gönderir), bunu bilmek
> sadece debug ederken kafa karışıklığını önlemek için önemli.

### 1.4 Bağlantı Yaşam Döngüsü

- ESP `loop()` içinde `server.accept()` ile tek seferde bir istemci kabul eder.
- İstemci bağlı kaldığı sürece (`client.connected()`) gelen komutları okumaya
  devam eder.
- Bağlantı Java tarafından kapatılırsa (`socket.close()`) veya kopmasi
  durumunda ESP bunu algılar ve tekrar yeni bağlantı kabul etmeye döner.
- **Önemli:** Şu anki firmware **tek istemcili** bir döngü mantığıyla
  yazılmış (`while(client.connected())` bloğu bitmeden yeni `accept()`
  çağrılmıyor). Yani Java uygulaması bağlantıyı açık tutup üzerinden çoklu
  komut gönderebilir (önerilen kullanım budur — her komut için yeni bağlantı
  açıp kapatmak yerine, tek soket üzerinden `1\n`, `0\n` şeklinde art arda
  komut gönderilebilir).

## 2. Java Tarafında Yapılması Gerekenler

### 2.1 Bağlantı Kurulumu

```java
Socket socket = new Socket("192.168.2.100", 5000);
PrintWriter out = new PrintWriter(socket.getOutputStream(), true); // autoFlush=true
BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
```

- `autoFlush = true` önemli, yoksa `\n` gönderilse bile veri buffer'da
  bekleyip ESP'ye ulaşmayabilir.
- Bağlantı kurulmadan önce kullanıcının cihazının **ESP'nin WiFi ağına bağlı
  olduğunu** doğrulaması gerekiyor (ileride bunu UI'da bir durum göstergesiyle
  belirtmek iyi olur — "ESP ağına bağlı değilsiniz" gibi).

### 2.2 Komut Gönderme

```java
out.println("1"); // LED ON
String response = in.readLine(); // "OK LED ON" beklenir
```

### 2.3 UI Tarafı İçin Öneriler

- Basit iki buton: **LED ON** / **LED OFF**, her tıklamada `1` veya `0`
  gönderilsin.
- Butonlar tıklanınca gelen cevabı (`OK LED ON` / `ERROR ...`) küçük bir
  durum etiketinde göstermek, debug'ı kolaylaştırır.
- Soket işlemlerini **UI thread'inde değil** ayrı bir thread'de / `SwingWorker`
  veya benzeri bir mekanizmada yapmak gerekir — TCP bağlantı/okuma bloklayıcı
  (blocking) olduğu için arayüz donabilir.
- Bağlantı hatası (ESP kapalı, ağa bağlı değil, timeout) durumları için
  try/catch ile kullanıcıya anlamlı bir hata mesajı gösterilmeli
  (`ConnectException`, `SocketTimeoutException` vb.).
- Soket üzerinde makul bir `connectTimeout` ve `soTimeout` ayarlamak iyi olur
  (örn. 3-5 saniye), aksi halde UI süresiz beklemede kalabilir.
- Uygulama kapanırken veya "bağlantıyı kes" gibi bir aksiyon varsa soketi
  düzgün `close()` etmek gerekir; aksi halde ESP tarafında bağlı istemci
  "askıda" kalabilir ve yeni istemciler `accept()` edilemez (çünkü firmware
  tek istemcili döngüde çalışıyor).

### 2.4 Test Sırası (Önerilen)

1. Önce Java kodu yazmadan, `telnet 192.168.2.100 5000` veya `nc 192.168.2.100
   5000` ile manuel test edin — `1` ve `0` gönderip LED'in yanıp söndüğünü
   doğrulayın. Bu, protokolün çalıştığını Java kodundan bağımsız kanıtlar.
2. Ardından basit bir Java konsol uygulaması ile (UI'sız) soket bağlantısı
   kurup komut gönderip cevap okuma akışını doğrulayın.
3. En son Swing/JavaFX arayüzünü bu çalışan mantığın üzerine inşa edin.

## 3. Bilinen Kısıtlar / İleride Değişebilecekler

- Şu an kimlik doğrulama / güvenlik katmanı yok — ağa bağlanan herhangi bir
  cihaz port 5000'e komut gönderebilir. WiFi şifresi (`12345678`) tek koruma.
  İleride STM32 entegrasyonu geldiğinde bu konunun (ör. basit bir token/komut
  onayı) gözden geçirilmesi gerekebilir.
- `readStringUntil('\n')` **timeout olmadan** bekler; istemci `\n` göndermeden
  bağlantıyı açık bırakırsa ESP o bağlantıda takılı kalabilir. Java tarafı
  her zaman `\n` ile biten komut göndermeli.
- Şu anki protokol sadece `1`/`0` destekliyor. STM32 entegrasyonu geldiğinde
  muhtemelen yeni komutlar (`handleCommand` içinde yeni `else if` dalları)
  eklenecek — Java tarafında komut gönderme fonksiyonunu şimdiden
  parametrik yazmak (`sendCommand(String cmd)`) ileride buton eklemeyi
  kolaylaştırır.
