# مِرصد AI

تطبيق Android عربي لاكتشاف وتنظيم الأدوات والمشاريع والنماذج والمحتوى التقني. هذه النسخة تؤسس التطبيق محليًا؛ لم تُربط مصادر البيانات بعد، ولا تُدرج بيانات تجريبية في قاعدة بيانات المستخدم.

## المتطلبات

- JDK 17 أو أحدث متوافق مع Gradle Wrapper.
- Android SDK 35، مع Android SDK Platform 35 وBuild Tools المناسبة.

## البناء والاختبارات

```bash
./gradlew --version
./gradlew test
./gradlew assembleDebug
```

لاختبارات Room وواجهة Compose على جهاز أو محاكي متصل:

```bash
./gradlew connectedDebugAndroidTest
```

توجد نسخة Debug في `app/build/outputs/apk/debug/app-debug.apk` عند نجاح التجميع. لا يوقّع المشروع نسخة Release.

## المعمارية

- `domain`: نماذج المحتوى وحالات المجانية والتحقق والمزامنة وعقد المستودع.
- `data/local`: Room والجداول وDAOs وDataStore؛ Room هو مصدر القراءة للواجهة.
- `data/remote`: واجهات Retrofit لـGitHub وHugging Face وRSS وإعداد HTTPS وحدود المعدل.
- `data/mapper`, `data/repository`, `data/sync`, `data/dedup`, `data/translation`: التحويل والتخزين والمزامنة وإزالة التكرار والترجمة.
- `feature`: شاشات Compose وViewModel.
- `navigation`: التنقل السفلي وتفاصيل العناصر.
- `worker`: مهمة مزامنة يدوية فريدة عبر WorkManager.
- `core`: تجميع الاعتمادات ومراقبة الاتصال.

## الحالة الحالية

الرئيسية والبحث المحلي والمفضلة والإعدادات والتفاصيل جاهزة كأساس. تعمل الجداول والاستعلامات محليًا، لكن المكتبة تبدأ فارغة عمدًا. مهمة التحديث الفريدة تسجل أن المصادر لم تُربط بعد. خيار التحديث التلقائي محفوظ في DataStore فقط؛ لا توجد مزامنة دورية أو إشعارات.

التطبيق لا يحتوي Backend أو تسجيل دخول أو مفاتيح API. راجع [المصادر المخطط لها](docs/SOURCES.md) و[الخصوصية](docs/PRIVACY.md).

## الترخيص

Apache License 2.0، راجع [LICENSE](LICENSE).
