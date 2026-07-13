# ESP8266 Entegrasyon ve Hata Tespit Rehberi

Bu rehber, JavaFX istemcisi ile ESP8266 firmware'i arasındaki WiFi TCP soket iletişiminin çalışma mantığını açıklar ve olası yazılım/bağlantı hatalarının tespiti ve çözümü için yol haritası sunar.

---

## 1. İletişim Mantığı ve Kod Mimarisi

Sistem, **WiFi üzerinden Ham TCP Soket (Port 5000)** protokolünü kullanır. ESP8266 kartı Access Point (AP) olarak çalışarak kendi WiFi ağını yayınlar ve gelen TCP bağlantılarını dinler.

### 1.1 Asenkron Bağlantı Kurulumu (Java)
JavaFX arayüzünün donmasını engellemek için tüm bağlantı istekleri ayrı bir `Thread` üzerinde çalıştırılır. Bağlantı esnasında 3 saniyelik bir zaman aşımı (`connectTimeout`) uygulanır.

```java
// EspClientManager.java - Bağlantı Mantığı
public void connect(String ip, int port, ConnectionCallback callback) {
    new Thread(() -> {
        try {
            socket = new Socket();
            // 3 saniye bağlantı timeout
            socket.connect(new InetSocketAddress(ip, port), 3000);
            // 3 saniye okuma timeout
            socket.setSoTimeout(3000);

            out = new PrintWriter(socket.getOutputStream(), true); // autoFlush = true
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Platform.runLater(() -> callback.onConnectionStateChanged(true, "Bağlı (" + ip + ")"));
        } catch (Exception e) {
            disconnect();
            Platform.runLater(() -> callback.onConnectionStateChanged(false, "Bağlantı Hatası"));
        }
    }).start();
}
```

### 1.2 Veri Gönderme ve Satır Sonu (`\n`) Protokolü
ESP8266 firmware'i satır bazlı okuma yaptığı için Java tarafındaki her komutun sonuna mutlaka satır sonu (`\n`) karakteri eklenmelidir. Java tarafında `PrintWriter` nesnesi `autoFlush = true` olarak oluşturulduğundan, `println()` metodu veriyi tamponda bekletmeden doğrudan sokete yazar.

```java
// EspClientManager.java - Komut Mantığı
public void sendLedCommand(String command, ConnectionCallback callback) {
    new Thread(() -> {
        try {
            // println() metodu komutun sonuna otomatik \n ekler ve tamponu temizler (flush)
            out.println(command); 
            
            // ESP'den gelen yanıtı oku
            String response = in.readLine();
            if (response != null) {
                Platform.runLater(() -> callback.onResponseReceived(response));
            }
        } catch (Exception e) {
            Platform.runLater(() -> callback.onResponseReceived("HATA: " + e.getMessage()));
        }
    }).start();
}
```

---

## 2. ESP8266 Tarafındaki Olası Hatalar ve Teşhis Rehberi

ESP8266 üzerinde çalışan firmware'in (`esp_blink.ino`) çalışma döngüsü ve sık karşılaşılan hataların çözümleri aşağıda listelenmiştir.

### HATA 1: Java Uygulaması Bağlantı Kuramıyor (`ConnectException` / `Timeout`)
* **Nedeni 1 (WiFi Ağı):** Test cihazınız (PC/Laptop) ESP'nin yayınladığı `ESP_STM32_Bridge` ağına bağlı olmayabilir.
  * *Çözüm:* WiFi bağlantınızı kontrol edin. Ağ şifresi: `12345678`.
* **Nedeni 2 (Maksimum Cihaz Sınırı):** Firmware'de WiFi AP ayarlarında maksimum bağlantı sayısı 2 olarak sınırlandırılmıştır (`maxConnections = 2`). Eğer halihazırda iki cihaz (örneğin telefon ve başka bir bilgisayar) bağlıysa 3. cihaz bağlanamaz.
  * *Çözüm:* Diğer cihazların WiFi bağlantısını kesin.
* **Nedeni 3 (Askıda Kalan Bağlantı):** Firmware tek istemcili bir döngüde çalışır. Önceki Java bağlantısı kapatılmadan (soket kapatılmadan) uygulama çöktüyse veya zorla kapatıldıysa ESP hala o istemciyi bağlı sanıyor olabilir.
  * *Çözüm:* ESP8266 kartını resetleyin veya Java tarafındaki `cleanup()` / `disconnect()` işlemlerinin çalıştığından emin olun.

### HATA 2: Komut Gönderiliyor Ancak Yanıt Gelmiyor (Sonsuz Bekleme / Donma)
* **Nedeni (Eksik `\n` Karakteri):** ESP8266 firmware'i gelen veriyi `client.readStringUntil('\n')` satırı ile okur. Eğer Java'dan gelen komutun sonunda satır sonu (`\n`) yoksa, ESP satırın bittiğini anlayamaz ve soketi okumak için sonsuz bir döngüde takılı kalır.
  * *Çözüm:* Java tarafında mutlaka `PrintWriter.println()` kullanıldığını veya gönderilen ham verinin sonuna `\n` eklendiğini kontrol edin.

### HATA 3: ESP8266 Kendi Kendine Reset Atıyor (Watchdog Timer - WDT Çökmesi)
* **Nedeni (Bloklayıcı Kodlar):** ESP8266 işlemcisi tek çekirdeklidir ve arka planda WiFi bağlantısını ayakta tutan bir Watchdog Timer (Bekçi Köpeği Zamanlayıcısı) bulunur. Eğer `loop()` döngüsü içinde veya soket okuma esnasında kod 2-3 saniyeden fazla tamamen bloke edilirse (örneğin uzun `delay()` kullanımı veya sonsuz döngüler), WDT işlemciyi resetler.
  * *Çözüm:* Firmware kodunda uzun bekleme içeren yerlerde `yield()` veya `delay(0)` fonksiyonunu çağırarak WiFi arka plan görevlerinin çalışmasına izin verin.

### HATA 4: LED Durumu Yanlış Çalışıyor (Ters Mantık - Active Low)
* **Nedeni (Donanım Mimarisi):** NodeMCU (Amica) gibi ESP8266 geliştirme kartlarında dahili LED (`LED_BUILTIN`) genellikle **Active-Low** mimarisine sahiptir. Yani pini `LOW` (0V) yapmak LED'i yakar, `HIGH` (3.3V) yapmak LED'i söndürür.
  * *Çözüm:* Firmware'de kod yazarken bu duruma dikkat edilmelidir. Java istemcisi yalnızca `1` veya `0` gönderir; bu soyutlama ESP firmware'i içinde aşağıdaki gibi yapılmalıdır:
    ```cpp
    // ESP8266 Arduino Sketch Örneği
    if (command == "1") {
        digitalWrite(LED_BUILTIN, LOW);  // LED YANAR (Ters mantık)
        client.println("OK LED ON");
    } else if (command == "0") {
        digitalWrite(LED_BUILTIN, HIGH); // LED SÖNER (Ters mantık)
        client.println("OK LED OFF");
    }
    ```

---

## 3. Manuel Hata Analiz Adımları (Adım Adım Debug)

Eğer Java uygulamasında bir sorun yaşıyorsanız, hatanın Java kodundan mı yoksa ESP donanımından mı kaynaklandığını anlamak için şu adımları izleyin:

1. **Ping Testi Yapın:**
   `ESP_STM32_Bridge` ağına bağlıyken terminale `ping 192.168.2.100` yazarak ESP kartına ağ seviyesinde erişebildiğinizi doğrulayın.
2. **Ham TCP Bağlantısı Kurun (netcat veya telnet):**
   Java kodundan bağımsız olarak, ESP portuna manuel bağlanın:
   * Windows PowerShell: `test-netconnection 192.168.2.100 -port 5000`
   * Linux/Mac / Windows Bash: `nc 192.168.2.100 5000`
3. **Komut Gönderin:**
   Bağlantı kurulduğunda konsola `1` yazıp Enter (`\n`) tuşuna basın. Kart üzerindeki LED'in yandığını ve terminale `OK LED ON` yanıtının düştüğünü görün. Bu test başarılıysa ESP donanımınız ve firmware'iniz kusursuz çalışıyor demektir; hata Java tarafındaki UI/Soket kodlarındadır.
