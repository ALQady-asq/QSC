# QSC · ALQady Screen Recorder

نسخة مخصصة من [Open Recorder 1.0.3](https://github.com/NoneBaiano/OpenRecorder/releases/tag/v1.0.3) لأجهزة MatePad التي تشغّل تطبيقات Android. تحتفظ بحزمة النسخة السابقة `com.openrecorder.app.matepad` للتحديث مع الاحتفاظ بالإعدادات والتسجيلات. راجع [LICENSE](LICENSE) و[NOTICE](NOTICE).

## الإعدادات والتسجيل

- تعرض شاشة **Settings** الأقسام والتفاصيل جنبًا إلى جنب عندما تسمح مساحة النافذة. في العرض الضيق تظهر الأقسام في قائمة واحدة.
- من **Camera and floating controls** اختر **Allow display over other apps** وامنح QSC الإذن. يظهر عدّاد صغير أعلى الشاشة. اضغط العدّاد لفتح بطاقة التحكم، أو اضغط خارجها لطيّها. يتوفر الإيقاف مباشرةً بجوار العدّاد. يظهر زر الكتم عندما يتضمن مصدر التسجيل الميكروفون.
- اختر **Front camera** أو **Rear camera**. اسحب النافذة لنقلها أو كبّرها وصغّرها بإصبعين. يتيح **Camera layout** تعديل الحجم والموقع من داخل التطبيق، حتى أثناء الجلسة، ويحفظ إعدادًا مستقلًا للأفقي والرأسي. يحفظ **Save original camera video** ملفًا إضافيًا بلا صوت بأعلى ملف فيديو تدعمه الكاميرا عند إمكان الترميز بالتوازي.
- خيار **Keep bar out of video** مفعّل افتراضيًا. مع تعطيل **Show bar only when paused**، يحاول إبقاء الشريط ظاهرًا وتفاعليًا أثناء التسجيل واستبعاد طبقته من الفيديو باستخدام `SurfaceControl.Transaction.setSkipScreenshot`، من دون `FLAG_SECURE` الذي قد ينتج مستطيلًا أسود. يظل الشريط ظاهرًا أيضًا عند إخفاء شريط حالة النظام في التطبيقات بملء الشاشة. لا يتطلب الخيار Root أو Shizuku، لكنه يستخدم واجهة Android داخلية قد تختلف حسب الجهاز والتحديث.
- إذا لم تتوفر واجهة الاستبعاد أو فشل استدعاؤها، يعرض التطبيق تنبيهًا ويتحول تلقائيًا إلى إظهار الشريط عند الإيقاف المؤقت فقط. أوقف التسجيل مؤقتًا من الإشعار للتحكم، وعند الاستئناف يُخفى الشريط قبل متابعة الالتقاط. نجاح الاستدعاء لا يثبت أن نظام الشركة المصنّعة يحترمه؛ جرّب تسجيلًا قصيرًا على **MatePad 11.5s / HarmonyOS 4.2** و**Redmi Turbo 4 Pro / HyperOS 3.0** قبل الاعتماد عليه. لم تُختبر هذه النسخة فعليًا على هذين الجهازين.
- يحتفظ **Show bar only when paused** بالأولوية إن كان مفعّلًا مسبقًا. يعرض **Show camera preview only when paused** الكاميرا أثناء الإيقاف المؤقت فقط، ويمكن معه حفظ ملف الكاميرا المستقل. عند تعطيل **Keep bar out of video** وخياري الإظهار أثناء التوقف، يلتقط الفيديو نوافذ الشريط والكاميرا التي تراها.
- يضبط **Native detail preset** التسجيل على أبعاد الشاشة الأصلية والاتجاه التلقائي و50 FPS، مع معدل بت مستهدف 60 Mbps وترميز H.265 عند توفره.
- يضبط **Smooth recording preset** الدقة على 1080p ومعدل الإطارات على 60 FPS والترميز على H.264 ومعدل البت على 16 Mbps. تظل الدقة الأصلية متاحة، ومعدلات الإطارات المتاحة هي 30 و50 و60 و90 و120 و144 أو الوضع التلقائي. يختار QSC معدل ترميز أدنى عند غياب دعم المرمّز للدقة والمعدل المختارين، ويعرض الهدف الفعلي في إشعار التسجيل.
- يتيح **Audio codec in video** اختيار AAC أو Opus أو PCM بلا فقد. يستخدم Opus ‏48 kHz، ويمكن اختيار 44.1 أو 48 kHz لـAAC وPCM. دعم تشغيل Opus وPCM داخل MP4 يختلف حسب المشغّل.
- اختر **Native (maximum detail)** مع تعطيل **Force 16:9 with letterboxing** لالتقاط دقة شاشة MatePad 11.5 S الأصلية عند تسجيل الشاشة بالكامل. اختر الاتجاه ومعدل الإطارات من الإعدادات. اضغط **Start recording** ووافق على طلب النظام. تجد الملفات في **Movies/Open Recorder**.

## Build

Use JDK 21, SDK Platform 37.0, and Build Tools 36.0.0. Run `./gradlew :app:testDebugUnitTest :app:assembleRelease`. The release APK is unsigned. Use `zipalign` and `apksigner` to prepare an installable package. The optional `development` build uses an application ID suffix so it can coexist with the release.

Camera capture uses AndroidX CameraX. Extended audio tracks use AndroidX Media3, while AAC uses the platform muxer.
