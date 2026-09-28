# مِرصد AI

تطبيق Android عربي لاكتشاف وتنظيم المشاريع والنماذج والأدوات والأخبار والبرومبتات. يتصل التطبيق مباشرة بمصادر عامة عبر HTTPS، ويحفظ البيانات في Room لعرضها والبحث فيها دون اتصال. لا يملك التطبيق Backend.

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
- `data/remote`: محللات GitHub وHugging Face وRSS وprompts.chat وعميل HTTPS.
- `data/mapper`, `data/repository`, `data/sync`, `data/dedup`, `data/translation`: التحويل والتخزين والمزامنة وإزالة التكرار والترجمة.
- `feature`: شاشات Compose وViewModel.
- `navigation`: التنقل السفلي وتفاصيل العناصر.
- `worker`: مزامنة فريدة عبر WorkManager عند الاتصال؛ لكل مصدر Cache وحالة مستقلة.
- `core`: تجميع الاعتمادات ومراقبة الاتصال.

## الحالة الحالية

تتصل المزامنة بمصادر GitHub وHugging Face Models وSpaces وثلاث خلاصات رسمية وDataset Server لمجموعة prompts.chat. تعرض Home وSearch وDetails وFavorites ما حُفظ في Room؛ البحث محلي ولا يرسل طلبات عند الكتابة. التحديث عند بدء التطبيق يحترم صلاحية Cache، والتحديث اليدوي يستخدم WorkManager فريدًا. المزامنة الدورية غير مفعلة.

تبدأ حالة المجانية بـ`UNKNOWN` لكل المصادر في هذه المرحلة. أخطاء مصدر أو حدود معدله لا تحذف البيانات المخزنة ولا توقف بقية المصادر. تفاصيل نقاط النهاية، مدد Cache، ونتائج التحقق الشبكي موجودة في [المصادر](docs/SOURCES.md).

التطبيق لا يحتوي Backend أو تسجيل دخول أو مفاتيح API. راجع [المصادر](docs/SOURCES.md) و[الخصوصية](docs/PRIVACY.md).

## الترخيص

Apache License 2.0، راجع [LICENSE](LICENSE).
