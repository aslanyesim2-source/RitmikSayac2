# 🎙️ Ritmik Sayaç — Android

Mikrofon ile ses tanıyan, ritmik olarak söylenen kelime veya cümleleri sayan native Android uygulaması.

## Özellikler
- ✅ Android SpeechRecognizer ile gerçek zamanlı ses tanıma
- ✅ 3 eşleştirme modu: Tam / İçinde / Yaklaşık (Levenshtein)
- ✅ Türkçe & İngilizce dil desteği
- ✅ Canlı ses dalgası görselleştirmesi
- ✅ Metin transcript (eşleşen kelime vurgulanır)
- ✅ Zaman damgalı eşleşme geçmişi
- ✅ Sayaç animasyonu

## Gereksinimler
- Android Studio Hedgehog (2023.1.1) veya üzeri
- Android SDK 34
- Kotlin 1.9.22
- Minimum Android API 24 (Android 7.0)

## Kurulum

### 1. Projeyi Aç
```
Android Studio → File → Open → RitmikSayac klasörünü seç
```

### 2. Gradle Sync
Android Studio otomatik olarak Gradle sync yapacaktır.  
Eğer yapmazsa: **File → Sync Project with Gradle Files**

### 3. Çalıştır
- Fiziksel Android cihaz veya emülatör bağla
- **▶ Run** butonuna bas
- İlk açılışta **Mikrofon izni** iste — **İzin Ver**'e bas

## Kullanım
1. Sayılacak kelimeyi/cümleyi girin (örn: "ya", "tamam", "değil mi")
2. Eşleştirme modunu seçin:
   - **TAM**: Kelime birebir geçmeli
   - **İÇİNDE**: Metnin herhangi bir yerinde geçmeli
   - **YAKIN**: Yazım hatalarını tolere eder (%30 fark)
3. Dili seçin (Türkçe / İngilizce)
4. **▶ BAŞLAT** — konuşmaya başlayın
5. Her eşleşmede sayaç büyür ve animasyon oynar

## Proje Yapısı
```
RitmikSayac/
├── app/
│   ├── src/main/
│   │   ├── java/com/ritmik/sayac/
│   │   │   └── MainActivity.kt        ← Tüm uygulama mantığı
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── drawable/              ← UI bileşenleri
│   │   │   └── values/               ← Renkler, temalar
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## İzinler
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```
> Not: Google'ın SpeechRecognizer servisi için internet bağlantısı gereklidir.

## Notlar
- Ses tanıma için internet bağlantısı gerekir (Google Speech API)
- Gürültülü ortamlarda YAKIN mod daha iyi sonuç verir
- Cihaz mikrofonu kapalı değil, erişilebilir olmalı
