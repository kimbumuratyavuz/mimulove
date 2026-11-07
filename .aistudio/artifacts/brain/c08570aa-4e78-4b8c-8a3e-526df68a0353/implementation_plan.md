# Mimu LoveApp - Çiftler İçin Anlık Kalp, Eşleşme ve Aşk Sayacı Uygulaması

**Mimu LoveApp**, sevgililerin 6 haneli benzersiz eşleşme koduyla bağlandığı; **uygulama kapalıyken dahi anlık bildirim, özel ses ve titreşim alan**, bildirime tıklandığında uygulamayı doğrudan açarak kalbi coşkuyla oynatan ve mesajı gösteren; **Açık (Light), Koyu (Dark) ve Sistem (Otomatik Gece/Gündüz)** tema desteğine sahip, özel simgeli modern bir Android uygulamasıdır.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Kullanıcının Talep Ettiği Kesinleşen Özellikler**
>
> 1. **Uygulama Adı ve Kimliği**: Uygulama adı **"Mimu LoveApp"** olarak belirlendi (`metadata.json`, `strings.xml`, `settings.gradle.kts` ve launcher etiketi tam uyumlu olacak).
> 2. **Özel Uygulama İkonu (Custom App Icon)**: Romantik kırmızı/pembe tonlarında, parlak ve modern bir 3D kalp amblemi içeren özel adaptif uygulama ikonu oluşturulacak (`ic_launcher`).
> 3. **3 Kademeli Tema Sistemi**:
>    - **Açık Tema (Light)**: Yumuşak pudra, krem ve canlı gül pembesi/yakut kırmızısı tonları.
>    - **Koyu Tema (Dark)**: Derin gece moru, siyah-kadife ve parlayan neon yakut kırmızısı vurguları.
>    - **Sistem Teması (Auto)**: Cihazın gündüz/gece moduna göre otomatik geçiş yapan akıllı tema.
> 4. **Uygulama Kapalıyken Kesintisiz Bildirim**:
>    - Android `ForegroundService` ve `NotificationManager` ile uygulama tamamen kapalıyken dahi anlık eşleşme ve kalp bildirimleri alınacak.
> 5. **Bildirime Dokununca Kalp Oynatma & Mesaj**:
>    - `PendingIntent` ile bildirime tıklandığında uygulama açılacak, kalp animasyonu otomatik olarak büyüyecek/atacak ve partnerin aşk notu ekranda belirecektir.
> 6. **Ayarlar ve Karşılıklı Eşleşme Sonlandırma**:
>    - Ayarlar menüsü üzerinden "Eşleşmeyi Sonlandır" seçeneği eklendi. Çiftlerden biri eşleşmeyi sonlandırdığında, her iki tarafın cihazında da eşleşme anında sonlandırılır ve kullanıcılar kod ekranına döner.
>    - Test simülasyon modu tamamen kaldırıldı.

---

## 1. Overview & Core Concept

- **Uygulama Adı**: Mimu LoveApp
- **Amaç**: Çiftlerin birbirine olan sevgisini ve bağlılığını tek bir dokunuşla hissettiren, mesafeleri kaldıran, özel sesli ve titreşimli romantik çift alanı.
- **Hedef Kitle**: Birbirini seven, gün içinde birbirini haberdar etmek ve "Aklımdasın" demek isteyen çiftler.
- **Temel Değer**: Uygulama kapalı olsa dahi eşinden gelen kalbi kaçırmama, ekranda canlı atan kalp ritmi ve ilişki sayacıyla aşkı diri tutma.

---

## 2. User Experience & Visual Design

### 1. Açılış, Profil ve Eşleşme Akışı
- **Google ile Tek Tıkla Giriş**: Hızlı oturum açma (`CredentialManager`).
- **6 Haneli Eşleşme Kodu**: Kullanıcıya özel üretilen kod (Örn: `M7U9X2`).
- **Uygulama Kapalıyken Eşleşme Bildirimi**: Karşı taraf kodu girdiğinde, diğer kullanıcının ekranına uygulama kapalı olsa dahi *"❤️ [İsim] seninle Mimu LoveApp üzerinden eşleşmek istiyor!"* bildirimi düşer. Bildirime tıklanınca doğrudan onay kartı açılır.

### 2. Canlı Kalp ve Dokunuş Alanı (Ana Ekran)
- **Nefes Alan Kalp**: Ekranın merkezinde yumuşak ritimle büyüyüp küçülen parıltılı 3D kalp.
- **Dokunma & Gönderme Reaksiyonu**:
  - Kalbe dokunulduğunda dışa doğru romantik dalgalar (ripple rings) ve yukarı süzülen minik kalp parçacıkları.
  - Cihazda çift vuruşlu haptik titreşim (`pulse-pulse`) ve tatlı romantik sevgi sesi.
  - Karşı tarafa anlık Firestore güncellemesiyle ulaştırılır.
- **Gelen Kalbin Karşılanması**:
  - Uygulama açıkken: Kalp aniden parlayarak büyür, ekran pembe ışıkla aydınlanır, ses çalar.
  - **Uygulama kapalıyken**: Durum çubuğunda yüksek öncelikli bildirim belirir. Bildirime dokunulduğu an uygulama açılır, kalp ekranda hemen atmaya başlar ve partnerin mesajı gösterilir.

### 3. Aşk Sayacı ve Anlık Notlar
- **Birliktelik Sayacı**: "Birlikte geçen her saniye çok değerli" — Gün, saat, dakika ve saniye canlı akar.
- **Hızlı Fısıltılar (Aşk Notları)**:
  - "Seni çok özledim", "Aklımdasın birtanem", "Bir öpücük gönderdim", "Günün güzel geçsin".
  - Kullanıcının kendi özel romantik mesajını yazıp gönderebileceği pratik giriş alanı.

### 4. Tema Seçici (Settings Modal / Top Bar)
- Kullanıcı dilediği an **Açık**, **Koyu** veya **Sistem (Otomatik)** temasını seçebilir; tercih DataStore / bellek üzerinde kalıcı tutulur.

---

## 3. Technical Architecture & Data Strategy

### Sistem Şeması

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Kapalı Cihaz / Arka Plan                        │
│                                                                        │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │   CoupleHeartbeatService (Foreground Service + Firestore)    │     │
│   │   • couples/{id} ve users/{uid} gerçek zamanlı izleme        │     │
│   │   • Ekran kilitli veya uygulama kapalıyken tetiklenir        │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  │                                     │
│                        Yeni Kalp veya Eşleşme                          │
│                                  │                                     │
│   ┌──────────────────────────────▼───────────────────────────────┐     │
│   │   NotificationManager (Özel Ses Kanalı + Haptik)             │     │
│   │   • PendingIntent (ACTION_OPEN_HEART, senderName, noteText)  │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
└──────────────────────────────────┼─────────────────────────────────────┘
                                   │
                      (Bildirime Dokunulunca)
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                 MainActivity / MimuLoveApp Navigation                  │
│   • OnNewIntent / Intent Extras incelenir                              │
│   • Kalp büyüme animasyonu anında oynatılır (Heart Burst Effect)       │
│   • Gönderilen aşk notu romantik kartla ekrana yansıtılır              │
│   • Özel melodi ve haptik titreşim eşzamanlı verilir                   │
└────────────────────────────────────────────────────────────────────────┘
```

### Renk Paletleri (Mimu LoveApp Theme)

| Token | Açık Tema (Light) | Koyu Tema (Dark) |
|---|---|---|
| `primary` | `#E11D48` (Rose Red) | `#FB7185` (Glowing Rose) |
| `onPrimary` | `#FFFFFF` | `#4C0519` |
| `primaryContainer` | `#FFE4E6` (Soft Pink) | `#881337` (Deep Wine) |
| `background` | `#FFF1F2` (Warm Rose White) | `#0F070B` (Night Velvet) |
| `surface` | `#FFFFFF` (Buzlu Beyaz) | `#1A0C14` (Buzlu Gece Kartı) |
| `onSurface` | `#1E1B1D` | `#FCE7F3` |

---

## 4. Uygulama İkonu Tasarımı (`app-icon-generation`)

- **Konsept**: Gece mürdümü ve yakut kırmızısı gradyan zemin üzerinde, etrafında tatlı ışık halkası olan parıltılı 3D kalp ve zarif sonsuzluk/bağ çizgisi.
- **Uygulama**: `ic_launcher_foreground.xml` ve `ic_launcher_background.xml` ile uyumlu yüksek kaliteli vektör / adaptif ikon tasarımı.

---

## 5. Doğrulama ve Test Adımları

1. **Uygulama İsmi ve İkon Kontrolü**:
   - `metadata.json`, `strings.xml`, `settings.gradle.kts` dosyalarında "Mimu LoveApp" isminin teyidi.
   - Launcher ikonunun ana ekranda kusursuz görünmesi.
2. **Tema Testi**:
   - Açık, Koyu ve Sistem temaları arasında geçiş yapıldığında renklerin anında adapte olması.
3. **Kapalıyken Bildirim & Tıklama Testi**:
   - Uygulama arka plana atılıp / kapatıldığında test kalbi gönderimi.
   - Bildirimin özel ses ve titreşimle belirmesi.
   - Bildirime tıklandığında uygulamanın açılıp kalbi otomatik oynatması ve mesajı göstermesi.
4. **Eşleşme Kodu Doğrulaması**:
   - 6 haneli kod üretimi ve karşı tarafa eşleşme bildiriminin ulaştırılması.
5. **Derleme Doğrulaması**:
   - `compile_applet` ile projenin başarıyla derlenmesi.
