<p align="center">
  <img src="docs/images/banner.png" width="420" alt="XCPE Guard ikonu">
</p>

<h1 align="center">XCPE Guard</h1>

<p align="center">
  <b>Android TV kutularındaki XCPE botnetini kutunun kendisinden bulur ve kapatır. Bilgisayar ya da root gerekmez.</b>
</p>

<p align="center">
  <a href="https://github.com/burakztrk/xcpe-guard/releases/latest"><img src="https://img.shields.io/github/v/release/burakztrk/xcpe-guard?label=son%20APK&color=81C995&labelColor=151A22" alt="Son sürüm"></a>
  <a href="https://github.com/burakztrk/xcpe-guard/actions/workflows/release.yml"><img src="https://img.shields.io/github/actions/workflow/status/burakztrk/xcpe-guard/release.yml?branch=main&label=derleme&labelColor=151A22" alt="Derleme"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-81C995?labelColor=151A22" alt="Android 8.0+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/lisans-GPL--3.0-8AB4F8?labelColor=151A22" alt="Lisans"></a>
</p>

<p align="center">
  <a href="https://github.com/burakztrk/xcpe-guard/releases/latest/download/xcpe-guard.apk"><b>⬇ Son APK'yı indir</b></a>
  · <a href="README.md">English</a>
</p>

---

Google Threat Intelligence 2026'da
[XCPE botnet raporunu](https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet)
yayımladı. Pek çok Android TV kutusunun üreticisi, cihazların fabrika yazılımına bir arka kapı (`x.cpe` /
`com.oemservice`) koymuştu. Bu arka kapı sistem yetkisiyle çalışıyor; sessizce uygulama kuruyor, komut çalıştırıyor,
ekranı kaydediyor, tuşlara basıyor ve internet bağlantını proxy olarak kiralıyor. **1,3 milyondan** fazla kutu botnete
katılmış.

Google sunucuları kapattı, ama arka kapı o kutuların hepsinin fabrika yazılımında duruyor. XCPE Guard kutuyu rapordaki
göstergelere göre tarar, arka kapıyı kapatır ve her gün yeniden kontrol eder.

## Ekran görüntüleri

| Dil seçimi | İzni bir kez ver |
|---|---|
| ![Dil](docs/images/language.png) | ![Kurulum](docs/images/setup.png) |
| **Ayarlar'ın üstünde kodla eşleme** | **Sorunlar otomatik düzeltilir** |
| ![Eşleme](docs/images/pairing.png) | ![Rapor](docs/images/report-fixed.png) |
| **Her satır kendini açıklar** | **Arkadaşınla paylaş** |
| ![Ayrıntı](docs/images/report-expanded.png) | ![Hakkında](docs/images/about.png) |

## Nasıl çalışır

Android, sahibi izin verdiğinde bir uygulamanın cihazın kendi hata ayıklama servisiyle (adbd) konuşmasına izin verir.
Botnet de bu servisi kötüye kullanmıştı; burada senin için, kutunun içinden çalışır ve ağından dışarı hiçbir şey çıkmaz.

1. **Dil**: Türkçe ya da English.
2. **Geliştirici seçenekleri**: uygulama nereye 7 kez basacağını gösterir.
3. **Hata ayıklama izni**, iki yoldan biriyle:
   - **Kablosuz hata ayıklama (Android 11+)**: uygulama Ayarlar'ın üstünde küçük bir tuş takımı tutar. "Eşleme kodu
     ile cihaz eşle"yi açıp kodu tuş takımına yazarsın; altıncı hane girilince eşleme kendiliğinden başlar.
   - **USB / ağ hata ayıklama**: kutu 5555 portunda zaten dinliyorsa (bu kutuların çoğu öyle) TV bir kez
     "Hata ayıklamaya izin verilsin mi?" diye sorar.
4. İlk bağlantıdan sonra uygulama kendine `WRITE_SECURE_SETTINGS` verir. Her taramada **hata ayıklamayı açar, tarar,
   yeniden kapatır**.

Geri gelen her şey hemen kapatılır (`pm disable-user`), arka kapının erişilebilirlik kancası kaldırılır ve Play Protect
yeniden açılır. Hiçbir şey kaldırılmaz; her şey geri alınabilir.

## Neleri kontrol eder

| Alan | Kontrol |
|---|---|
| Zararlı paketler | Google'ın listelediği 10 paket: `x.cpe`, `com.oemservice`, `com.abc.nexus`, dropper'lar, proxy SDK'ları |
| Şüpheli üretici uygulamaları | `com.oem.qa`: arka kapıyla birlikte güncellenen, sessiz kurulum servisli fabrika test uygulaması |
| Kalıcılık | `/oem` APK parmak izleri (arka kapının yedek kopyası), firmware güncellemesi olmadan beliren sistem uygulamaları, yeni `/system/priv-app` klasörleri, yeni privapp izin dosyaları, `persist.networkstack.*`, zararlı yerel kütüphaneler, yeniden açılan kapalı uygulamalar |
| Ayarlar | Erişilebilirlik servisleri, Play Protect, çalışan süreçler, hata ayıklama durumu |
| Ağ | C2 adreslerine ve 3885–3887 MQTT portlarına giden canlı TCP bağlantıları, DNS'in C2 adlarını engelleyip engellemediği, ev filtresini atlayan Özel DNS |

## Kurulum

1. [`xcpe-guard.apk`](https://github.com/burakztrk/xcpe-guard/releases/latest/download/xcpe-guard.apk) dosyasını indir
   ya da uygulamanın Hakkında ekranındaki QR kodu telefonla okut.
2. Kutuya yükle; örneğin Downloader uygulamasıyla ya da bir bilgisayardan:
   ```bash
   adb install xcpe-guard.apk
   ```
3. Uygulamalar satırından **XCPE Guard**'ı aç ve üç adımı izle.

Her sürümde indirmeyi doğrulamak için bir `.sha256` dosyası da bulunur.

## Derleme

JDK 17 ve Android SDK (platform 34) gerekir.

```bash
git clone https://github.com/burakztrk/xcpe-guard.git
cd xcpe-guard
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

Keystore yoksa release derlemesi yerel debug anahtarınla imzalanır. Kendi anahtarınla imzalamak için:

```bash
export KEYSTORE_FILE=/yol/release.jks
export KEYSTORE_PASSWORD=…  KEY_ALIAS=…  KEY_PASSWORD=…
export VERSION_CODE=42         # isteğe bağlı; versionCode 42, versionName 1.0.42 olur
./gradlew assembleRelease
```

### Sürümler

`main`'e her push [`.github/workflows/release.yml`](.github/workflows/release.yml) iş akışını çalıştırır: release APK'yı
derler, imzalar ve `v1.0.<çalıştırma numarası>` sürümü olarak yayımlar. Yukarıdaki indirme linki ve sürüm rozeti hep en
sonuncuyu gösterir. İş akışı dört repo secret'ı okur (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64`
(keystore'un base64 hali), `KEYSTORE_PASSWORD`, `KEY_ALIAS` ve `KEY_PASSWORD`. Bunlar yoksa sürüm yayımlamaz, yalnızca
imzasız bir derleme yükler; çünkü güncellemelerin her seferinde aynı anahtarla imzalanması gerekir.

## Ağında engelle

XCPE Guard kutunun içinde çalışır. Ağındaki her cihazı botnetin sunucularından uzak tutmak için aynı Google raporundan
çıkan [AdGuard engelleme listesini](docs/adguard-blocklist.md) (154 kural) AdGuard Home'a ya da AdGuard
sözdizimini anlayan herhangi bir DNS filtresine ekle. Uygulamadaki "C2 alan adları (DNS)" satırı o zaman yeşile döner.

## Sınırlar

- **Root yok.** Arka kapının salt-okunur `/oem` bölümündeki yedek kopyası silinemez. Paketi kapalı kaldığı sürece de
  çalışamaz; uygulama bunu sağlar.
- **Fabrika ayarlarına sıfırlama** kutuyu fabrika durumuna döndürür: sonra uygulamayı yeniden çalıştır.
- **IP adresleri** root olmadan bir uygulamadan engellenemez. C2 alan adlarını router'da ya da DNS filtrende
  (AdGuard Home, Pi-hole) engelle; uygulama hâlâ çözülüp çözülmediklerini gösterir.
- Android kullanılmayan bir hata ayıklama anahtarını **7 gün** sonra unutur. Günlük tarama bunu taze tutar; otomatik
  tarama kapalıysa ya da kutu daha uzun kapalı kaldıysa uygulamayı açıp yeniden bağlan.

## Gizlilik

Hesap yok, analiz yok, kendine ait sunucu yok. Tek ağ trafiği, ağının engelleyip engellemediğini görmek için yedi C2
alan adının DNS sorgusudur.

## Teşekkürler

- Göstergeler: Google Threat Intelligence Group.
- ADB eşleme ve TLS: Muntashir Al-Islam'ın [libadb-android](https://github.com/MuntashirAkon/libadb-android)
  kütüphanesi; tüm kütüphaneler için [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
- [Clean Your TV](https://github.com/Gathin23/cleanyourtv) ile yapılan bir temizlikten doğdu.

## Lisans

[GPL-3.0](LICENSE). Özgür ve açık kaynak: her amaçla kullanabilir, inceleyebilir, değiştirebilir ve paylaşabilirsin.
Değiştirilmiş bir sürümü dağıtırsan, o sürümün de aynı lisansla açık kaynak kalması gerekir.
