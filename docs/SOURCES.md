# المصادر العامة

يتصل تطبيق Android مباشرة بالمصادر عبر HTTPS؛ لا يوجد Backend أو Proxy ولا Token. جرى اختبار نقاط النهاية من Terminal باستخدام `curl` في 2026-09-29. نتائج الاختبار تصف تلك اللحظة ولا تضمن التوفر المستقبلي.

| المصدر | Endpoint والبيانات المستخدمة | Cache TTL | تحقق curl |
| --- | --- | --- | --- |
| GitHub REST API | `GET https://api.github.com/search/repositories` بثلاثة استعلامات بحث ثابتة، `sort=updated`، `order=desc`، `per_page=20`. الحقول تشمل `id`, `full_name`, `description`, `html_url`, `owner`, اللغة، النجوم، forks، issues، license، topics والتواريخ وحالتي archive/fork. | 6 ساعات | HTTP 200، `application/json; charset=utf-8`، 33,413 bytes لطلب العينة ذي 5 نتائج في إعادة الفحص؛ الجذر `total_count`, `incomplete_results`, `items`. |
| Hugging Face Models | `GET https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=20&full=true`. Metadata المتوفرة مثل `id`, `author`, `createdAt`, `lastModified`, `downloads`, `likes`, `trendingScore`, `pipeline_tag`, `library_name`, `tags`, `gated`, `private`. | 3 ساعات | HTTP 200، `application/json; charset=utf-8`، 10,031 bytes لعينة 5؛ Array. ظهرت الحقول الأساسية، ولم يظهر `cardData` في أول عنصر العينة. |
| Hugging Face Spaces | `GET https://huggingface.co/api/spaces?sort=trendingScore&direction=-1&limit=20&full=true`. الحقول تشمل `id`, `author`, `lastModified`, `likes`, `trendingScore`, `sdk`, `tags`, `cardData`, `createdAt`. | 3 ساعات | HTTP 200، `application/json; charset=utf-8`، 12,476 bytes لعينة 5؛ Array. |
| Android Developers RSS | `https://android-developers.googleblog.com/feeds/posts/default?alt=rss`، عنوان ورابط ووصف وتاريخ وGUID وتصنيفات فقط. | 6 ساعات | HTTP 200، `application/rss+xml; charset=UTF-8`، 486,737 bytes؛ RSS/XML. |
| Google Developers RSS | `https://developers.googleblog.com/feeds/posts/default?alt=rss`، حقول RSS المذكورة أعلاه. | 6 ساعات | HTTP 200، `application/rss+xml; charset=utf-8`، 18,985 bytes؛ RSS 2.0. |
| OpenAI News RSS | `https://openai.com/news/rss.xml`، حقول RSS المذكورة أعلاه. | 6 ساعات | HTTP 200، `text/xml; charset=utf-8`، 750,532 bytes؛ RSS 2.0 مع namespaces وCDATA. |
| prompts.chat | `GET https://datasets-server.huggingface.co/rows?dataset=fka%2Fprompts.chat&config=default&split=train&offset=0&length=100`. تُستخدم `row_idx` وحقول `act`, `prompt`, `for_devs`, `type`, `contributor` و`num_rows_total`. يحتفظ العنصر بترخيص بيانات المجموعة CC0-1.0 ورابط prompts.chat العام؛ لا يُخترع رابط فردي لكل صف ولا يثبت الترخيص أن خدمة ما مجانية. | 24 ساعة | HTTP 200، `application/json`، 3,687 bytes لعينة 5؛ الجذر `features`, `num_rows_per_page`, `num_rows_total`, `partial`, `rows`. ظهر داخل `row` الحقول الخمسة المذكورة. |
| AI Video Prompt Book 2026 | `GET https://datasets-server.huggingface.co/rows?dataset=hrrcne%2Fai-video-prompt-book-2026&config=default&split=train`. يستعلم التطبيق عن `num_rows_total` أولاً، ثم يجلب أحدث صفحة (`offset=max(total-100,0)`, `length=100`). تُستخدم `row_idx`, `id`, `category`, `group`, `prompt`, و`num_rows_total`. تُصنف الصفوف آلياً إلى أفكار ريلز أو تحولات شخصيات بحسب وصف المجموعة والنص. رابط المصدر ظاهر لكل عنصر؛ رخصة المجموعة CC BY 4.0. | 6 ساعات | HTTP 200 عند الفحص؛ مجموعة عامة، 698 صفاً، split `default/train`، وآخر تعديل ظاهر 2026-09-26. أكدت عينة الصفوف الحقول المذكورة ووسوم Veo وSora وKling. |
| FactVerse — r/Futurology | `https://www.reddit.com/r/Futurology/new/.rss?limit=100`. يقرأ Workflow التغذية ويحتفظ بعنوان المنشور ورابطه وتاريخه؛ المحتوى يظل مصنفًا كمصدر مجتمعي. | تحديث يومي عبر GitHub Actions | يُتحقق من صيغة RSS عند كل تشغيل؛ المقالات المقبولة تتطلب رابط HTTPS. |
| FactVerse — ScienceDaily | `https://www.sciencedaily.com/rss/top/science.xml`. يجلب Workflow عناوين ووصف وروابط وتواريخ أخبار العلوم. | تحديث يومي عبر GitHub Actions | يُتحقق من صيغة RSS عند كل تشغيل؛ المقالات المقبولة تتطلب رابط HTTPS. |
| FactVerse — Singularity Hub | `https://singularityhub.com/feed/`. يجلب Workflow عناوين ووصف وروابط وتواريخ العلوم والتقنية والمستقبل. | تحديث يومي عبر GitHub Actions | يُتحقق من صيغة RSS عند كل تشغيل؛ المقالات المقبولة تتطلب رابط HTTPS. |
| FactVerse cloud feed | تحفظ العناصر في قسم `factverse-science` داخل `remote_prompts.json`، ويزامنها التطبيق من raw GitHub على `main`. تحتفظ التغذية بالمقالات الحديثة عند تعذر مصدر، وتزيل عناصر أقدم من 180 يومًا. | Workflow يومي؛ التطبيق كل 12 ساعة | بيانات المصدر والخبر تُعرض كرابط للمقال الأصلي ولا يجري scraping لنص المقال الكامل. |

## المزامنة والتعامل مع الأخطاء

- لكل مصدر سجل مستقل في `sync_state`. Room هي مصدر الحقيقة للواجهة، وتبقى البيانات القديمة عند الفشل أو `304`.
- التشغيل الأولي يفحص الصلاحية؛ التحديث اليدوي يتجاوز TTL. يستخدم WorkManager عملاً واحدًا فريدًا مع شرط اتصال الشبكة.
- GitHub يرسل ثلاثة استعلامات كحد أقصى في التشغيل، بحد 20 نتيجة لكل استعلام. لا تُطلب بيانات Releases.
- Hugging Face يطلب 20 Model و20 Space، وDataset Server يطلب حتى 100 صف لكل مجموعة؛ لا تُحمّل ملفات المجموعة كاملة.
- البحث محلي باستعلام Room محدود الحقول و`LIKE` لأن هذا الإصدار يخزن صفحات صغيرة محدودة؛ لم نضف FTS لتجنب Migration وفهرسة إضافية لهذا الحجم.
- تُرتب Home النتائج بمجموعات حتمية من `sourceId` و`ContentType` وحقول المصدر. لا يصنف Space كأداة AI إلا بوسم مهمة/AI صريح؛ وإلا يبقى `OTHER`. لا يظهر قسم فارغ، و«جديد» يعتمد على `publishedAt` أو `pushedAt` أو `sourceUpdatedAt` الحقيقي ضمن الأيام السبعة الأخيرة؛ لا يُستخدم `createdAt` الاحتياطي لهذه الشارة.
- تُترجم العناوين والأوصاف المؤهلة تدريجيًا عند ظهور البطاقات عبر ML Kit، بينما لا يُترجم نص Prompt إلا بطلب صريح من التفاصيل. تحفظ الترجمة محليًا في Room مع النص الأصلي واللغة المصدرية. لا تحدث ترجمة أثناء جلب المصادر، ولا تُستبدل النصوص الأصلية.
- يُنظف وصف RSS لبطاقة العرض مع الاحتفاظ بنص الوصف الأصلي في Room. تُعرض الصور فقط إذا أرسل الخلاصة رابط HTTPS صريحًا.
- البحث يشمل العناوين والأوصاف الأصلية والترجمات المحفوظة والمصدر والوسوم والتصنيفات وأنواع المحتوى، ولا ينفذ طلبات شبكة عند الكتابة.
- `403` و`429` لا يؤديان إلى Retry تلقائي؛ يحترم التطبيق `Retry-After` أو وقت reset المتاح، وإلا ينتظر ساعة. الأخطاء المؤقتة مثل انقطاع الشبكة و5xx لها Retry واحد محدود.
- تُرسل `If-None-Match` و`If-Modified-Since` عندما يعيد المصدر هذه الرؤوس. `304` نجاح بلا حذف أو إعادة كتابة للمحتوى.
- مصدر واحد متعطل لا يوقف بقية المصادر. لا يحدث scraping ولا تنزيل لنماذج أو README أو نص المقال الكامل.
- يجلب Workflow تغذيات FactVerse الثلاث إلى القسم `factverse-science` ويُبقي العناصر الإنجليزية مع روابطها الأصلية. يضم تصنيف FactVerse الأخبار العلمية والبرومبتات الإنجليزية، وقوالب الفيديو فيه تحتفظ بالتعليق الصوتي الإنجليزي وتوقيع FactVerse دون إعادة تنسيق التوقيع العربي العام.
- `freeStatus` يبقى `UNKNOWN`؛ وجود عنصر على GitHub أو Hub لا يثبت أنه مجاني.

## الإشعارات المحلية والأداء

- بعد كل مزامنة ناجحة، يغيّر التطبيق سجل الاكتشافات المحلية ويُقيّم الفئات المفعّلة في إعدادات المستخدم (`notificationsEnabled` ومجموعات `notifyAiTools` و`notifyAndroidProjects` و`notifyModels` و`notifyPrompts` و`notifyNews`).
- تُعالج الإشعارات محليًا في الجهاز فقط، ولا تُرسل إلى Backend أو إلى خادم طرف ثالث. يمكن للمستخدم إيقافها كليًا أو اختيار الفئات المراد تنبيهها بها.
- يتم تجميع العناصر الحديثة داخل نافذة 7 أيام، مع خصم التكرار حسب المفتاح الفريد للاكتشاف، وتجاهل العناصر التي لا تزال خارج فئة المفعّلة أو التواريخ غير الحديثة.
- إذا لم تُمنح صلاحية الإشعارات على Android 13+، أو إذا كانت مجموعة العناصر الفرعية فارغة، لا تُنشأ إشعارات، لكن مزامنة المصادر تظل ناجحة وتُحفظ البيانات محليًا.
- تُستخدم قناة إشعارات محلية واحدة باسم `mersad_discoveries`، مع عنوان عام مثل «اكتشافات جديدة» وملخّص يتضمن عدد العناصر لكل فئة، ويؤدي النقر إلى فتح شاشة التطبيق مباشرةً.
- يبقى ترتيب Home ونتائج البحث مستقرًا، ويُستخدم `debounce(250)` لتقليل عمليات البحث المتكررة، مع تجنب إظهار الأقسام الفارغة مع `HomeSection.entries` و`takeIf { it.isNotEmpty() }`، ما يقلل الحمل على الواجهة بينما يبقي التحديثات في Room فقط مصدر الحقيقة.
- لا تؤخر الإشعارات أو الترجمة عرض محتوى Room؛ كلاهما تابعٌ للخطوة التالية بعد مزامنة ناجحة، ولا يعطل انقطاع الشبكة أو فشل مصدر واحد أعمال بقية المصادر.

## حدود معروفة

- استعلامات GitHub بحث محدودة وليست قائمة Trending رسمية؛ قد تتضمن مستودعات غير مرخصة أو غير نشطة، لذلك لا يُستنتج ترخيص أو تقييم جودة من النجوم.
- قد يغيب `cardData` أو وصف أو تاريخ أو رخصة؛ تبقى هذه القيم فارغة ولا يُختلق بديل.
- RSS قد يتغير أو يتوقف، والخلاصة الكبيرة لـOpenAI تُحلل لاستخراج ملخص قصير فقط ولا تُخزن كاملة.
- prompts.chat مجتمع المصدر؛ البيانات لا تحدد رابطًا عامًا منفصلًا لكل صف، ولا يجري ترجمة النصوص أثناء المزامنة.
- AI Video Prompt Book مجموعة مجتمعية قابلة للتغيير؛ يوفر Hugging Face صفحة واحدة بحد أقصى 100 صف في كل مزامنة. تُحفظ العناصر في Room، ويظهر رابط المصدر وترخيص CC BY 4.0 للمستخدم.
- اختبار `curl` تحقق من الاستجابة في التاريخ أعلاه؛ Unit Tests تستخدم fixtures محلية ولا تعتمد على الإنترنت.
