# -*- coding: utf-8 -*-
import json, sys
from pathlib import Path

P = Path(sys.argv[1])
J = P / "app/src/main/assets/curriculum_2026_2027.json"
A = P / "app/src/main/java/com/kazanim/app/MainActivity.java"
G = P / "app/build.gradle.kts"

TYMM = {
    1: "https://tymm.meb.gov.tr/din-kulturu-ve-ahlak-bilgisi-dersi-2/unite/144",
    2: "https://tymm.meb.gov.tr/din-kulturu-ve-ahlak-bilgisi-dersi-2/unite/145",
    3: "https://tymm.meb.gov.tr/din-kulturu-ve-ahlak-bilgisi-dersi-2/unite/146",
    4: "https://tymm.meb.gov.tr/din-kulturu-ve-ahlak-bilgisi-dersi-2/unite/147",
    5: "https://tymm.meb.gov.tr/din-kulturu-ve-ahlak-bilgisi-dersi-2/unite/148",
}

def B(title, minutes, text, questions=None):
    return {"title": title, "minutes": minutes, "text": text, "questions": questions or []}

def Pn(title, style, *blocks):
    return {"title": title, "style": style, "blocks": list(blocks)}

def H(unit, method, plan_a, plan_b, note):
    return {
        "totalMinutes": 30,
        "plans": [plan_a, plan_b],
        "verificationStatus": "curated-official-2026",
        "qualityLevel": "curated-official-2026",
        "label": "10. sınıf • TYMM temelli • 2 alternatif • 30 dk",
        "researchBasis": [
            TYMM[unit],
            "MEB/TYMM öğrenme çıktısı ve süreç bileşenleri",
            method
        ],
        "sourceNote": note
    }

W = {}

# 1. ÜNİTE - İSLAM'DA VARLIK VE BİLGİ
W[1] = H(1, "Zihin haritası; bilgi okuryazarlığı",
    Pn("Bilginin İzini Sür", "kaynak-haritasi",
       B("Gündelik İddialar", 10, "Tahtaya üç iddia yazın: biri doğrudan gözlemle, biri akıl yürütmeyle, biri güvenilir haberle bilinebilecek türden olsun. Öğrenciler her iddianın nasıl bilinebileceğini ve hangi durumda yanılma ihtimali bulunduğunu gerekçelendirsin.", ["Bir bilgiyi 'biliyorum' diyebilmek için ne gerekir?"]),
       B("Kaynakları İlişkilendir", 15, "Salim duyular, selim akıl ve sadık haber kavramlarını MEB/TYMM materyalinden netleştirin. Gruplar bu üç kaynağı ayrı kutulara kapatmak yerine birbirini nasıl tamamladığını oklarla göstersin. Her ok için 'doğrular, sınar, anlamlandırır, haber verir' gibi bir ilişki fiili seçsin.", ["Aynı bilgiye birden fazla yoldan ulaşmak güveni nasıl etkiler?", "Bir kaynak tek başına her tür bilgi için yeterli olabilir mi?"]),
       B("Doğru Bilgi Zinciri", 5, "Öğrenciler 'doğru bilgi -> doğru inanç -> doğru davranış' zincirinde bir kopukluk örneği yazıp hangi halkada sorun bulunduğunu belirtir.")),
    Pn("Haber Masası", "dogrulama-vakasi",
       B("Kaynak Etiketi", 5, "Dört kısa bilgi kartı verin: kaynağı belli uzman açıklaması, anonim sosyal medya paylaşımı, doğrudan gözlem, aktarılmış fakat doğrulanmamış haber. Öğrenciler yalnız 'inanırım/inanmam' demeden önce hangi ek bilgiye ihtiyaç duyduğunu yazsın."),
       B("Doğrulama Kurulu", 20, "Dörtlü gruplar her kart için 'kaynak nedir, kanıt nedir, hangi bilgi yolu kullanılıyor, hangi yanılma ihtimali var?' sorularını cevaplasın. Sonra öznel değerlendirme ile nesnel bilgi kaynağını karıştıran ifadeleri düzeltmeye çalışsın.", ["Çok paylaşılması bir bilgiyi nesnel yapar mı?", "Akıl yürütme hangi durumda duyudan gelen bilgiyi yeniden değerlendirebilir?"]),
       B("Paylaşmadan Önce", 5, "Her öğrenci bir bilgiyi paylaşmadan önce uygulayacağı iki maddelik kişisel kontrol listesi yazar.")),
    "DKAB.10.1.1; TYMM bilgi kaynakları arasındaki ilişkinin belirlenmesini ve zihin haritasını önerir.")

W[2] = H(1, "Kavram haritası; sorumluluk bağlantısı",
    Pn("Bilgi Kaynaklarının Sınırları", "sinir-karsilastirma",
       B("Dört Soru", 10, "Gruplara dört soru verin: 'Yan odada ne oluyor?', '100 yıl önce ne oldu?', 'Bu kararın sonucu ne olabilir?', 'Gayb hakkında kesin bilgiye nasıl ulaşılır?'. Her soru için hangi bilgi kaynağının işe yarayacağını ve hangisinin yetersiz kalacağını tartışsınlar."),
       B("Sınır ve Tamamlama", 15, "Gruplar salim duyu, selim akıl ve sadık haber için birer 'imkân' ve birer 'sınır' cümlesi yazsın. Ardından bu sınırın başka bir bilgi kaynağıyla nasıl tamamlandığını örneklesin.", ["Bir bilgi kaynağının sınırlı olması değersiz olduğu anlamına gelir mi?", "Kaynaklar çatışıyor gibi göründüğünde ilk yapılacak iş nedir?"]),
       B("Kavram Haritası", 5, "Sınıf tek bir ortak haritada bilgi, kaynak, doğruluk, sorumluluk ve davranış kavramlarını ilişkilendirir.")),
    Pn("Doğru Bilginin Bedeli", "karar-senaryosu",
       B("Karar Anı", 10, "Bir öğrencinin doğruluğunu kontrol etmeden arkadaşının özel hayatıyla ilgili bir haberi sınıf grubunda paylaştığı kısa senaryoyu verin. Öğrenciler olayın yalnız 'yanlış davranış' değil 'yanlış bilgi süreci' yönünü bulsun."),
       B("Nerede Koptu?", 10, "İkililer olayı bilgi edinme, doğrulama, yorumlama ve paylaşma aşamalarına ayırır; sorumluluğun hangi aşamalarda başladığını tartışır.", ["Bilgi edinirken de ahlaki sorumluluk var mıdır?", "Doğrulanmamış bilgiyi paylaşmak neden yalnız kişisel bir hata değildir?"]),
       B("İlke Yaz", 10, "Her ikili bilgi ile sorumluluk ilişkisini açıklayan bir ilke cümlesi üretir; sınıf üç güçlü ilkeyi seçip gerekçesini konuşur.")),
    "DKAB.10.1.1; TYMM doğru bilgi, sorumluluk ve davranış ilişkisini özellikle vurgular.")

W[3] = H(1, "Bilgi kutusu; soru sorma",
    Pn("Merak Kutusu: Allah-Âlem", "bilgi-kutusu",
       B("Soruyu Üret", 10, "Öğrenciler 'Allah-âlem ilişkisi' konusunda gerçekten merak ettikleri bir soruyu anonim yazar. Sorular olgu sorusu, anlam sorusu, delil sorusu ve kavram sorusu olarak sınıflandırılır."),
       B("Soruyu Güçlendir", 15, "Her grup zayıf veya çok geniş bir soruyu seçip araştırılabilir ve gerekçelendirme gerektiren hâle getirir. MEB/TYMM materyalindeki görülen (şehadet) ve görülemeyen (gayb) âlem kavramlarını kullanarak sorunun hangi bilgi alanına ait olduğunu belirler.", ["Bir soruyu güçlü yapan nedir?", "Gözlemleyemediğimiz her şey hakkında konuşmak anlamsız mıdır?"]),
       B("Bir Sonraki Adım", 5, "Her grup seçtiği soruya cevap vermeden önce hangi kaynaktan hangi tür bilgi toplaması gerektiğini söyler.")),
    Pn("Görülen - Görülmeyen Haritası", "zihin-haritasi",
       B("Kavramları Yerleştir", 10, "İnsan, hayvan, bitki, melek, cin, şeytan ve 'âlem' kavramlarını MEB/TYMM çerçevesine göre zihin haritasına yerleştirin. Öğrenciler her yerleştirme için 'bu bilgiyi hangi yolla biliyoruz?' sorusunu cevaplasın."),
       B("Bilgi Yolunu Sorgula", 10, "Haritadaki her kavramın yanına gözlem, akıl yürütme veya sadık haber etiketlerinden uygun olanları ekleyin. Birden fazla etiket konulabilen örnekler üzerinde durun.", ["Görülemeyen bir varlık hakkında bilgi edinmenin ölçütü ne olabilir?"]),
       B("İnsanın Yeri", 10, "Öğrenciler 'İnsan âlemin parçasıdır; fakat âlemi anlamlandırmaya çalışan bir varlıktır.' cümlesini destekleyen veya sınırlayan iki gerekçe yazıp eşleriyle paylaşır.")),
    "DKAB.10.1.2; TYMM bilgi kutusu ve görülen/görülmeyen âlem için zihin haritası önerir.")

W[4] = H(1, "Düşün-eşleş-paylaş; gözlemden çıkarım",
    Pn("Gözlemden Çıkarıma", "gozlem-sorgu",
       B("Bir Gözlem Seç", 5, "Öğrenciler çevreden gözlemleyebildikleri düzenli bir olgu seçsin: gece-gündüz, canlıların ihtiyaçları, büyüme, neden-sonuç ilişkileri gibi. Yalnızca gözlemi yazıp yorum eklemesin."),
       B("Üç Katmanlı Sorgu", 20, "Dörtlü gruplar seçilen olguyu üç katmanda inceler: 'Ne gözlüyoruz?', 'Bu gözlem hangi soruları doğuruyor?', 'Bu sorulardan Allah-âlem ilişkisi hakkında hangi çıkarımlar yapılabilir ve hangileri yapılamaz?'. Öğretmen gözlem ile yorumun karıştırıldığı cümleleri geri soruyla açar.", ["Gözlemden yapılan her çıkarım zorunlu mudur?", "Bir çıkarımın güçlü olması için hangi dayanak gerekir?"]),
       B("Sınır Cümlesi", 5, "Öğrenciler bir güçlü çıkarım ve bir 'buradan kesin olarak çıkaramayız' cümlesi yazar.")),
    Pn("Düşün - Eşleş - Paylaş", "dusun-esles-paylas",
       B("Düşün", 10, "Soru: 'İnsanın âlemdeki yeri, onun Allah karşısındaki konumunu anlamasını nasıl etkiler?' Her öğrenci önce tek başına üç cümle yazar."),
       B("Eşleş", 10, "İkililer cevaplarında ortak olan bir varsayımı ve farklı olan bir varsayımı bulur. İki görüşü birleştiren daha güçlü bir cevap üretmeye çalışır."),
       B("Paylaş", 10, "Her ikili yalnız en güçlü gerekçesini sınıfa sunar. Sınıf gerekçeleri 'gözleme dayalı', 'kavramsal', 'temel kaynağa dayalı' diye sınıflandırır.")),
    "DKAB.10.1.2; TYMM gözlem, temel kaynaklardan bilgi toplama ve düşün-eşleş-paylaş önerir.")

W[5] = H(1, "Temel kaynaklardan bilgi toplama; deneme hazırlığı",
    Pn("Kaynak - İddia - Çıkarım", "kaynak-delil",
       B("İddiaları Ayır", 10, "Üç ifade verin: 'Âlem kendi kendine yeterlidir.', 'İnsan âlemde sorumluluk taşıyan bir varlıktır.', 'Görülemeyen alan hakkında hiçbir bilgi edinilemez.'. Öğrenciler bu ifadelerin hangilerinin cevaplanması için gözlem, akıl yürütme veya temel kaynak bilgisine ihtiyaç olduğunu belirlesin."),
       B("Kaynakla Sına", 15, "Gruplar MEB/TYMM materyalindeki ilgili ayet/metin parçalarını kullanarak bir iddiayı destekleyen, sınırlayan veya yeniden formüle ettiren verileri bulsun. 'Metin bunu söylüyor' ile 'biz bundan şu çıkarımı yapıyoruz' cümlelerini ayrı yazsın.", ["Kaynak bilgisi ile yorum arasındaki sınır nerede başlar?"]),
       B("Savunulabilir Sonuç", 5, "Her grup yalnız dayanak gösterebildiği tek bir sonuç cümlesi sunar.")),
    Pn("Mini Deneme Atölyesi", "deneme",
       B("Tez Seç", 10, "TYMM'nin performans görevi önerisine uygun olarak 'Allah-âlem ilişkisini düşünmek insanın kendisini anlamasına nasıl katkı sağlar?' sorusuna bir tez cümlesi yazdırın."),
       B("Gerekçe İskeleti", 10, "Öğrenciler tezi destekleyen iki gerekçe ve her gerekçe için kullanacağı bilgi türünü/kaynağı belirler. Bir karşı soru ekler."),
       B("80 Kelimelik Deneme", 10, "Öğrenciler kısa denemeyi yazar. Son iki dakikada 'iddia, gerekçe, dayanak, sonuç' kontrolü yapar.")),
    "DKAB.10.1.2; TYMM Allah-âlem ilişkisi konusunda bilgi toplama, değerlendirme, çıkarım ve deneme yazma önerir.")

W[6] = H(1, "Bilgi kartları; Diyanet mealiyle ayet çözümleme",
    Pn("İki Ayet - Dört Başlık", "ayet-kartlari",
       B("Metni Oku", 10, "İsrâ 36 ve Mülk 23 ayetlerini Diyanet İşleri Başkanlığı mealinden okuyun. Öğrenciler metinde doğrudan geçen kavramları ve fiilleri işaretlesin; önce yorum eklemesin."),
       B("Bilgi Kartları", 15, "Gruplar ayet mesajlarını 'bilgi', 'duyu imkânları', 'sorumluluk', 'şükür/hesap' gibi metinden çıkarılan başlıklara ayıran kartlar hazırlar. Her kartın arkasına hangi ifadeden hareket ettiğini yazar.", ["İki ayetin ortaklaştığı mesaj nedir?", "Duyu organlarına sahip olmak neden sorumlulukla ilişkilendirilebilir?"]),
       B("Açıkla", 5, "Her grup bir kartı seçip ayetteki mesajı kendi cümlesiyle, anlamı genişletmeden açıklar.")),
    Pn("Metin Dedektifleri", "ayet-cozumleme",
       B("Soru Listesi", 10, "Öğrenciler iki ayet için 'kim/ne, hangi davranış, hangi imkân, hangi sonuç' sorularını kullanarak metin içi bir çözümleme yapar."),
       B("Ortak - Farklı", 10, "İkililer ayetlerin ortak ve farklı vurgularını iki sütuna yazar. Sonra iki ayeti tek bir tema altında ilişkilendirir.", ["Bir ayetin mesajını özetlerken hangi ayrıntılar vazgeçilmezdir?"]),
       B("Üç Cümle", 10, "Öğrenci iki ayetin ortak mesajını üç cümlede yazar; son cümlede günlük hayatla bağ kurar fakat ayete söylemediği bir şeyi söyletmemeye dikkat eder.")),
    "DKAB.10.1.3; TYMM Diyanet meali, çözümleme, sınıflandırma ve bilgi kartlarını önerir.")

W[7] = H(1, "Özetleme; çalışma yaprağı",
    Pn("Özet Editörlüğü", "ozet-edit",
       B("Ana Mesajı Bul", 10, "İsrâ 36 ve Mülk 23 için hazırlanmış kısa çalışma yaprağında ana fikir, destekleyici unsur ve günlük hayat bağlantısını ayrı kutulara doldurun."),
       B("Fazlalığı Ayıkla", 10, "Öğrencilere ayetlerin mesajını anlatan fakat gereksiz ayrıntılar ve yorumlar içeren örnek bir paragraf verin. Metne dayanmayan veya ana mesajı dağıtan cümleleri çıkarsınlar."),
       B("40 Kelimelik Özet", 10, "İki ayeti birlikte en fazla 40 kelimeyle özetlesinler. Akranıyla değiştirip 'ana mesaj var mı, ekleme var mı?' kontrolü yapsınlar.")),
    Pn("Sorumluluk Çemberi", "cember",
       B("Kavram Çekirdeği", 5, "Çemberin ortasına 'bilgi karşısında sorumluluk' yazın. Öğrenciler iki ayetten bu temayla ilişkili bir kelime veya fikir söyler."),
       B("Soru Çemberi", 20, "Sırayla şu tür sorular konuşulsun: 'Bilmediğimiz şeyi kesinmiş gibi söylemek neden sorun olabilir?', 'Duyma ve görme imkânı nasıl sorumluluk doğurur?', 'Bir bilgiyi araştırma sorumluluğu nerede başlar?'. Her öğrenci önceki cevaba bir ek veya itiraz getirir."),
       B("Çıkış Kartı", 5, "Öğrenci 'Bugün iki ayetten çıkardığım ortak ilke...' cümlesini tamamlar.")),
    "DKAB.10.1.3; TYMM çalışma yaprağı ve açık uçlu sorularla özetleme kanıtı önerir.")

# 2. ÜNİTE - ALLAH'I TANIMAK
W[8] = H(2, "Düşün-eşleş-paylaş; gözlem ve örüntü",
    Pn("Örüntü Avı", "oruntu",
       B("Gözlem Listesi", 10, "Gökyüzü, canlılar, mevsimler, insan bedeni veya ekosistemlerden öğrencilerin bildiği düzenli olguları listeleyin. 'Düzen' demeden önce gözlenen örüntünün ne olduğunu somutlaştırın."),
       B("Örüntüden Genellemeye", 15, "Gruplar üç gözlemi karşılaştırıp ortak bir örüntü tanımlar. Sonra bu örüntüden hangi genellemenin yapılabileceğini ve hangi genellemenin fazla ileri gideceğini tartışır.", ["Gözlem ile delil arasındaki fark nedir?", "Bir örüntü nasıl akıl yürütmeye dönüşür?"]),
       B("Öğrenme Günlüğü", 5, "Öğrenci 'Bugün gözlemden genellemeye geçerken dikkat edilmesi gereken...' diye başlayan iki cümle yazar.")),
    Pn("Düşün - Eşleş - Paylaş", "dusun-esles-paylas",
       B("Düşün", 10, "Soru: 'Yaratılanlar Allah'ın varlığına nasıl işaret edebilir?' Öğrenci önce örnek ve gerekçesini tek başına yazar."),
       B("Eşleş", 10, "İkililer iki örneği karşılaştırıp hangisinin daha güçlü bir akıl yürütme sunduğunu ölçüt belirleyerek tartışır."),
       B("Paylaş", 10, "Sınıf, verilen gerekçeleri 'gözlem', 'örüntü', 'genelleme' basamaklarına yerleştirir. Basamağı eksik olan gerekçeler birlikte düzeltilir.")),
    "DKAB.10.2.1; TYMM yaratılanlar üzerinde gözlem, örüntü bulma ve genelleme ile düşün-eşleş-paylaş önerir.")

W[9] = H(2, "Delil türleri; beyin fırtınası",
    Pn("Dört Delil - Dört Soru", "delil-karsilastirma",
       B("İlk Fikirler", 10, "Kozmolojik, teleolojik (gaye ve nizam), ontolojik ve dinî tecrübe delillerinin adlarını MEB/TYMM materyalinden verin. Öğrenciler her birinin 'hangi tür sorudan hareket ettiğini' tahmin etsin; ayrıntılı felsefe tarihi anlatımına girmeyin."),
       B("Soruyu Delille Eşleştir", 15, "Kartlarda 'Evren neden var?', 'Düzendeki amaçlılık ne anlatır?', 'Zorunlu varlık fikri neyi sorgular?', 'Dinî tecrübe kişiye ne tür bir dayanak sağlar?' gibi sorular bulunsun. Gruplar kartları uygun başlığa yerleştirip gerekçe versin.", ["Bu deliller birbirinin alternatifi mi, farklı akıl yürütme yolları mı?"]),
       B("Bir Cümlelik Fark", 5, "Her öğrenci iki delil türü arasındaki farkı tek cümleyle ifade eder.")),
    Pn("Karşı Soru Beyin Fırtınası", "beyin-firtinasi",
       B("Serbest İfade", 10, "TYMM'nin özgür ifade vurgusuna uygun olarak 'Allah'ın varlığı konusunda insanlar hangi tür gerekçeler kullanır?' sorusuna eleştiri yapmadan cevap toplayın."),
       B("Gerekçeyi Sınıflandır", 10, "Toplanan gerekçeler deneyim, evrenin varlığı, düzen/gaye ve kavramsal akıl yürütme gibi başlıklarda sınıflandırılır. Öğrenciler başlıkların neden farklı olduğunu açıklar."),
       B("Güçlü Soru", 10, "Her grup kendi başlığını sınayan bir karşı soru üretir ve bu sorunun delili çürütmekten çok akıl yürütmeyi netleştirmedeki rolünü tartışır.")),
    "DKAB.10.2.1; TYMM delillerin ayrıntıya girilmeden verilmesini ve beyin fırtınasıyla özgür ifade ortamı oluşturulmasını önerir.")

W[10] = H(2, "Hz. İbrahim kıssası; akıl yürütme günlüğü",
    Pn("Akıl Yürütmenin Basamakları", "kissa-analizi",
       B("Kıssayı İzle", 10, "MEB/TYMM materyalinde Allah'ın varlığı delilleri bağlamında verilen Hz. İbrahim kıssasının ilgili bölümünü okuyun. Öğrenciler yalnız olay sırasını ve sorulan temel soruları çıkarır."),
       B("Basamakları Bul", 15, "Gruplar kıssadaki gözlem, sorgulama, geçicilik/değişim üzerine düşünme ve sonuç basamaklarını ayırır. 'Sonuç nereden çıktı?' sorusuyla her basamağın öncekiyle bağlantısını gösterir.", ["Bir akıl yürütmede ara basamakları atlamak ne tür sorun doğurur?"]),
       B("Günlük", 5, "Öğrenci kıssadan öğrendiği bir düşünme alışkanlığını 'Bir iddiayı değerlendirirken artık...' cümlesiyle yazar.")),
    Pn("Delil Savunma Masası", "mini-panel",
       B("Rol Seç", 5, "Gruplar kozmolojik veya teleolojik delilden birini seçer; amaç 'kanıtlamak' yarışması değil akıl yürütmenin mantığını doğru aktarmaktır."),
       B("Mini Panel", 20, "Her grup seçtiği delilin başlangıç gözlemini, sorusunu ve ulaştığı genellemeyi açıklar. Diğer gruplar 'hangi basamak daha fazla açıklama istiyor?' diye soru sorar.", ["Delili güçlü anlatmak için hangi kavramların doğru kullanılması gerekir?"]),
       B("Ölçülü Sonuç", 5, "Öğrenciler 'Bu delilin temel akıl yürütmesi...' diye başlayan, abartısız iki cümlelik sonuç yazar.")),
    "DKAB.10.2.1; TYMM Hz. İbrahim kıssasına ve öğrenme günlüğüne yer verir.")

W[11] = H(2, "Ayet ve hadis incelemesi; isim-sıfat ilişkisi",
    Pn("İsimden Anlama", "esma-inceleme",
       B("Kaynak Kartları", 10, "MEB/TYMM materyalindeki ayet ve hadislerden Rahman, Rahim, Melik, Rezzak, Halık gibi isimlerle ilgili kısa kaynak kartları kullanın. Öğrenciler her kartta ismin hangi anlam bağlamında geçtiğini bulsun."),
       B("İsim - Varlık İlişkisi", 15, "Gruplar isimleri 'Allah'a mahsus oluşu', 'kâinatın yaratılış/işleyişiyle ilişkisi' ve 'insanı muhatap alan yönü' açısından düşünür. Bir ismin birden fazla bağlamla ilişkili olabileceğini tartışır.", ["Bir ismi yalnız sözlük anlamıyla bilmek Allah tasavvurunu kurmaya yeter mi?"]),
       B("Bütüncül Cümle", 5, "Her grup iki ismi ilişkilendirerek Allah hakkında tek bir bütüncül cümle kurar.")),
    Pn("Kavram Eşleştirme Atölyesi", "grafik-duzenleyici",
       B("İsim ve Sıfat", 10, "Kelam, ilim, irade, kudret, tekvin gibi sıfatları ve seçilmiş isimleri ayrı kartlarda verin. Öğrenciler kavramların anlamlarını MEB/TYMM materyalinden kontrol eder."),
       B("İlişki Ağı", 10, "Kartlar grafik düzenleyici üzerinde 'yaratma', 'bilme', 'dileme', 'bildirme/konuşma', 'rızık verme' gibi fiiller etrafında ilişkilendirilir. Her bağlantı için kısa gerekçe yazılır."),
       B("Yanlış Eşleşmeyi Düzelt", 10, "Öğretmen bilerek iki zayıf/yanlış bağlantı önerir; öğrenciler kaynağa dönüp neden sorunlu olduğunu açıklar ve daha uygun bağlantıyı kurar.")),
    "DKAB.10.2.2; TYMM ayet-hadislerden inceleme ve grafik düzenleyicilerle isim-sıfat ilişkisi kurmayı önerir.")

W[12] = H(2, "Üç açıdan isimler; varoluşa yansıma",
    Pn("Üç Pencere", "uc-kategori",
       B("Kategoriyi Kur", 10, "TYMM'de isimlerin Allah'a mahsus olan, kâinatı ilgilendiren ve insanı ilgilendiren yönlerle ele alındığını hatırlatın. Gruplar seçilmiş isimleri bu üç pencere altında gerekçeli biçimde yerleştirir; bir ismin birden çok pencereye temas edebileceğini tartışır."),
       B("Varoluşa Yansıma", 15, "Her grup bir isim seçip 'Bu isim kâinatı veya insan-Allah ilişkisini anlamada hangi bakış açısını açar?' sorusuna kaynak temelli örnek üretir. İnsan davranışı için doğrudan Allah'a ait sıfatları taklit eder gibi ifade kurmamaya dikkat edilir.", ["Rahman ismini anlamak canlılara bakışımızı nasıl etkileyebilir?"]),
       B("Sınıf Sentezi", 5, "Üç gruptan gelen sonuçlar tek bir 'Allah'ı isim ve sıfatlarıyla tanımak' şemasında birleştirilir.")),
    Pn("Fanzin Taslağı", "fanzin",
       B("Sayfa Planı", 10, "TYMM'nin fanzin önerisine uygun olarak ikililer bir sayfalık mini fanzin taslağı kurar: bir isim/sıfat, anlamı, kaynak cümlesi ve hayata yansıması başlıkları."),
       B("İçerik Üret", 15, "İkililer metni yazar ve görsel sembol yerine anlamı yanlış çağrıştırmayacak kavram/ilişki şeması ekler. Akran grubu 'kaynakla uyum, kavram doğruluğu, ilişki açıklığı' açısından kontrol eder."),
       B("Tek Düzeltme", 5, "Her ikili geri bildirimden sonra en önemli bir düzeltmeyi yapar ve nedenini açıklar.")),
    "DKAB.10.2.2; TYMM isimleri üç açıdan ele alır, varoluşa yansımalarını örneklendirir ve fanzin önerir.")

W[13] = H(2, "Sokratik çember; bütüncül bakış",
    Pn("Sokratik Çember", "sokratik-cember",
       B("Merkez Sorusu", 5, "Soru: 'Allah'ın isim ve sıfatlarını parça parça bilmek ile bütüncül bir Allah tasavvuru oluşturmak arasında ne fark vardır?' Öğrenciler önce kısa cevap yazar."),
       B("Çember Tartışması", 20, "İç çember cevapları tartışır; dış çember yalnız 'hangi isim/sıfat arasında ilişki kuruldu, hangi gerekçe kaynağa dayanıyor, hangi ifade açıklanmaya muhtaç?' notlarını alır. 10. dakikada roller değişir.", ["İsimler arasında ilişki kurarken çelişki gibi görünen durumlar nasıl açıklanabilir?", "Bütüncül bakış neden tek bir isme indirgenemez?"]),
       B("Sentez", 5, "Her öğrenci tartışmadan sonra ilk cevabını iki cümleyle yeniden yazar.")),
    Pn("Kavramdan Tutuma", "deger-yansimasi",
       B("Bir İsim Seç", 10, "Öğrenciler Rahman, Rahim, Rezzak, Halık gibi programda örneklenen isimlerden birini seçip anlamını kaynaktan kontrol eder."),
       B("Yanlış ve Doğru Yansıtma", 10, "İkililer ismin hayata yansımasını anlatan iki cümle yazar: biri kavramı insan davranışına hatalı/abartılı taşıyan, diğeri saygı, şefkat veya sorumluluk açısından daha ölçülü olan. Sınıf hangisinin neden uygun olduğunu tartışır."),
       B("3-2-1", 10, "3 öğrendiğim ilişki, 2 hâlâ düşündüğüm soru, 1 günlük hayatta dikkat edeceğim tutum yazılır.")),
    "DKAB.10.2.2; TYMM panel, zıt panel, kollegyum, münazara ve sokratik çember gibi tartışma teknikleri ile bütüncül bakış önerir.")

W[14] = H(2, "Diyanet meali; bilgi kartları; esma sözlüğü",
    Pn("Haşr 22-24 Mesaj Kartları", "ayet-kartlari",
       B("Metni İşaretle", 10, "Haşr 22-24 ayetlerini Diyanet meali üzerinden okuyun. Öğrenciler Allah'ın isimlerini ve bunlarla ilişkili ana mesajları iki farklı işaretle belirlesin."),
       B("Kartları Sınıflandır", 15, "Her isim için 'ayet içindeki ifade', 'anlam', 'hangi yönü vurguluyor?' alanlarından oluşan bilgi kartı hazırlanır. Kartlar benzer anlam ilişkilerine göre gruplanır.", ["Ayetlerdeki isimlerin birlikte verilmesi nasıl bir bütünlük oluşturuyor?"]),
       B("Bir Kartı Açıkla", 5, "Her grup bir kartı sınıfa açıklar; açıklamanın ayet metninden kopmamasına dikkat edilir.")),
    Pn("Esma Sözlüğü Mini Atölyesi", "sozluk",
       B("Madde Başlığı", 10, "TYMM zenginleştirme önerisini sınıf içine uyarlayın: ikililer ayetlerde geçen bir isim için mini sözlük maddesi tasarlar; anlam, ayet bağlantısı ve ilgili başka isim/sıfat alanlarını doldurur."),
       B("Çapraz Kontrol", 10, "İkililer sözlük maddelerini değiştirip 'anlam doğru mu, ayetle ilişki açık mı, gereksiz yorum var mı?' kontrolü yapar."),
       B("Ortak Sözlük", 10, "Düzeltmelerden sonra maddeler sınıf panosunda/defterde ortak bir esma sözlüğü sırasına konur. Öğrenciler iki madde arasındaki anlam ilişkisini söyler.")),
    "DKAB.10.2.3; TYMM Haşr 22-24 için Diyanet meali, bilgi kartları ve esma sözlüğü zenginleştirmesini önerir.")

W[15] = H(2, "3-2-1 kartı; özetleme",
    Pn("3-2-1 ile Haşr Özeti", "321",
       B("3 Mesaj", 10, "Öğrenciler Haşr 22-24'ten üç ana mesajı metne dayalı biçimde yazar; her mesajın yanına ayetteki dayanağı işaretler."),
       B("2 İlişki", 10, "Ayetlerde geçen iki isim/sıfat arasında kurduğu ilişkiyi açıklayan iki cümle yazar. İkililer birbirlerinin ilişkisini 'metinle uyumlu mu?' diye kontrol eder."),
       B("1 Bütüncül Özet", 10, "En fazla 50 kelimelik tek bir özet yazılır. Öğrenci özetinde yalnız isim listesi değil ayetlerin ana yönelimini de göstermeye çalışır.")),
    Pn("Başlık - Alt Başlık - Cümle", "ozet-yapisi",
       B("Başlıklandır", 10, "Ayet metnindeki içerikleri öğrenciler üç başlık altında organize eder: Allah'ın bilgisi/hâkimiyeti, isim ve sıfatlar, yaratılış ve tesbih gibi metinde görülen temalar. Başlıklar sınıfça tartışılarak netleştirilir."),
       B("Alt Başlıkları Birleştir", 15, "Gruplar her başlık için bir ana cümle yazıp ayrıntıların o cümleyi nasıl desteklediğini açıklar. Gereksiz tekrarlar çıkarılır."),
       B("Tek Paragraf", 5, "Üç ana cümle bağlaçlarla tek, akıcı bir paragrafta birleştirilir.")),
    "DKAB.10.2.3; TYMM 3-2-1 kartını ve ayet mesajlarının çözümleme-sınıflandırma-açıklama basamaklarını önerir.")

# 3. ÜNİTE - İSLAM'IN EVRENSEL MESAJLARI
W[16] = H(3, "Kaynak araştırma; ayet-hadisle doğrulama",
    Pn("Tevhit Kaynak Avı", "kaynak-arastirma",
       B("Kaynak Ölçütü", 10, "Gruplar 'tevhit hakkında bilgi ararken hangi kaynaklara güvenilir?' sorusuna ölçütler üretir. Kaynağın türü, güvenilirliği ve temel kaynakla bağlantısı konuşulur."),
       B("Bilgiyi Bul ve Doğrula", 15, "MEB/TYMM materyali ve öğretmenin önceden seçtiği güvenilir ayet/hadis kaynaklarında tevhit hakkında iki bilgi bulunur. Öğrenciler bir web/ikincil kaynak cümlesini ayet-hadis ışığında doğrulama mantığını uygular.", ["Bir bilgi temel kaynakla doğrulanmıyorsa nasıl ifade edilmelidir?"]),
       B("Kaydı Düzenle", 5, "Her grup kaynak adı, bulduğu bilgi ve doğrulama dayanağını üç satırlık kayıt kartına yazar.")),
    Pn("Peygamberlerin Ortak Mesajı", "kissa-kaynak",
       B("Hz. Nuh Bağlantısı", 10, "TYMM'nin yönlendirmesiyle Hz. Nuh kıssasında tevhit mesajıyla ilişkili temel bölümü MEB/TYMM materyalinden okuyun. Öğrenciler kıssanın ana mesajını tek cümleyle belirler."),
       B("Ortaklık Soruşturması", 10, "Gruplar peygamberlerin ortak mesajının tevhit olduğu iddiasını desteklemek için program materyalindeki başka örnekleri arar. 'Örnek' ile 'genelleme' arasındaki bağı açıklar."),
       B("Düşün-Eşleş-Paylaş", 10, "Öğrenciler 'Tevhidin evrensel mesaj olması ne demektir?' sorusuna önce bireysel cevap verir, eşle geliştirir, sonra sınıfta paylaşır.")),
    "DKAB.10.3.1; TYMM kaynak araştırma, ayet-hadisle doğrulama, Hz. Nuh kıssası ve düşün-eşleş-paylaş önerir.")

W[17] = H(3, "Özet metin; kaynak doğrulama",
    Pn("Tevhit Bilgi Dosyası", "bilgi-dosyasi",
       B("Dört Bilgi Kartı", 10, "Önceki haftadan gelen kayıtları 'tanım', 'temel kaynak dayanağı', 'peygamberlerin ortak mesajı', 'evrensellik' başlıklarında dört karta dönüştürün."),
       B("Kaynak Eleştirisi", 10, "Her grup başka grubun kartlarını inceler; 'kaynağı belli mi, bilgi ile yorum ayrılmış mı, ayet/hadis doğrulaması açık mı?' sorularıyla bir düzeltme önerir."),
       B("Özet Metin", 10, "Öğrenci 70-90 kelimelik 'Tevhit neden İslam'ın evrensel mesajıdır?' metni yazar; en az bir gerekçesini kaynak bilgisinden hareketle kurar.")),
    Pn("Bir Kavramı Yanlış Anlatmak", "yanlis-bilgi",
       B("Eksik Tanımlar", 10, "Tevhidi yalnız 'Allah vardır', yalnız 'Allah birdir' veya yalnız 'ibadet' boyutuna indirgeyen üç eksik açıklama verin. Öğrenciler her açıklamada eksik olan yönü bulur."),
       B("Düzeltme Kurulu", 15, "Gruplar MEB/TYMM materyaline dönerek daha dengeli bir açıklama yazar. Her ekledikleri cümlenin hangi bilgiye dayandığını not eder.", ["Bir kavramın doğru fakat eksik anlatımı neden yanıltıcı olabilir?"]),
       B("En Kısa Doğru Tanım", 5, "Sınıf, anlamı bozmadan olabildiğince kısa ortak bir tevhit açıklaması üretir.")),
    "DKAB.10.3.1; TYMM bilgi toplama sürecinin bulma-doğrulama-kaydetme basamaklarını ve özet metni önerir.")

W[19] = H(3, "Örnek olay; adalet-eşitlik ilişkisi",
    Pn("Aynı mı, Adil mi?", "adalet-vaka",
       B("İkilem", 5, "Bir okul destek programında herkese aynı kaynak verilmesi ile ihtiyacı daha fazla olana ek destek verilmesi seçeneklerini sunun. Öğrenciler ilk anda hangisini 'eşit', hangisini 'adil' bulduğunu yazar."),
       B("Örnek Olay İncelemesi", 20, "Gruplar olayı eşitlik, hakkaniyet, tarafsızlık, liyakat, emanet, insanın saygınlığı ve hesap verebilirlik kavramlarından en az üçüyle değerlendirir. Sonra kendi kararlarının hangi kavrama dayandığını savunur.", ["Herkese aynı davranmak hangi durumda adaletsiz olabilir?", "Farklı davranmak hangi ölçütle ayrımcılıktan ayrılır?"]),
       B("Sentez Cümlesi", 5, "Her grup 'Adalet eşitliği reddetmez; ...' diye başlayan bir sentez cümlesi yazar.")),
    Pn("Altıgen Düşünme", "altigen",
       B("Kavram Altıgenleri", 10, "Adalet, eşitlik, hakkaniyet, liyakat, emanet, dürüstlük, şeffaflık, tarafsızlık kavramlarını altıgen kartlara yazın. Öğrenciler güçlü ilişkileri yan yana getirir."),
       B("Bağlantıyı Savun", 15, "Gruplar kurduğu her komşuluk için bir gerekçe ve günlük hayattan bir örnek verir. Başka grup bir bağlantıyı sorgular; ilk grup ya savunur ya değiştirir."),
       B("Bütün Oluştur", 5, "Kavramlardan hareketle 'İslam'da adalet ve eşitlik ilişkisi' için tek cümlelik ortak tanım üretilir.")),
    "DKAB.10.3.2; TYMM örnek olay ve altıgen düşünme tekniklerini, adalet-eşitlik-hakkaniyet-liyakat ilişkisini önerir.")

W[20] = H(3, "Veda Hutbesi; küçürek hikâye",
    Pn("Veda Hutbesiyle Ölçüt Arama", "metin-tahlili",
       B("Metni Tara", 10, "TYMM'nin zenginleştirme önerisine uygun olarak Veda Hutbesi'nin adalet/eşitlik bağlamıyla ilgili seçilmiş, güvenilir bir bölümünü okuyun. Öğrenciler üstünlük, hak, insan saygınlığı veya sorumlulukla ilişkili ifadeleri işaretler."),
       B("İlke Çıkar", 15, "Gruplar metinden üç ilke çıkarır ve bunların eşitlik mi, adalet mi, ikisiyle de mi ilgili olduğunu gerekçelendirir. İlkeyi bugünkü bir okul/iş/toplum örneğine uygulamaya çalışır.", ["İnsanların değer bakımından eşit olması görev ve sorumlulukların her durumda aynı olması demek midir?"]),
       B("Ölçüt Listesi", 5, "Sınıf adil bir karar için 4 maddelik ölçüt listesi oluşturur.")),
    Pn("Küçürek Hikâye: Bir Karar Anı", "kucurek-hikaye",
       B("Durumu Kur", 10, "Öğrenciler adalet-eşitlik gerilimi içeren gerçekçi bir okul durumu seçer: görev dağılımı, yarışma, yardım, ekip seçimi gibi. 3 karakter ve karar anını belirler."),
       B("100 Kelimelik Hikâye", 15, "En fazla 100 kelimelik küçürek hikâye yazılır. Hikâye hangi kararın adil olduğunu doğrudan söylemek yerine ikilemi görünür kılmalıdır."),
       B("Kavram Etiketi", 5, "Akran, hikâyede hangi kavramların çatıştığını iki etiketle belirtir ve tek soru yazar.")),
    "DKAB.10.3.2; TYMM küçürek hikâye ve Veda Hutbesi tahlilini önerir.")

W[21] = H(3, "Medine örnek olayı; barış kavramları",
    Pn("Barış mı, Sessizlik mi?", "baris-vaka",
       B("İlk Ayrım", 10, "İki grubun tartışmayı bıraktığı fakat birbirine güvenmediği bir durumla; tarafların hak ve sorumlulukları konuşarak anlaşmaya vardığı ikinci durumu karşılaştırın. Öğrenciler hangisinin 'barış' olduğunu ve nedenini tartışır."),
       B("Kavram Haritası", 10, "Sulh, selam, emniyet ve güven kavramlarını MEB/TYMM materyalindeki anlam ilişkileriyle bir haritada birleştirin. Her kavram için barışı güçlendiren bir durum örneği ekleyin."),
       B("Kendi Hayatına Taşı", 10, "Öğrenciler sınıf/arkadaşlık düzeyinde bir çatışmada güveni yeniden kurmak için üç adım yazar. Adımların 'barış' kavramının hangi yönüne dayandığını belirtir.")),
    Pn("Medine'den Örnek Olay", "medine-vaka",
       B("Kaynaklı Olay", 10, "MEB/TYMM materyalinde Medine Dönemi'nde toplumsal barışın kurulmasına dair verilen güvenilir bir örnek olayı okuyun. Öğrenciler olayın taraflarını, sorunu ve uygulanan çözümü ayırır."),
       B("Neden İşe Yaradı?", 15, "Gruplar çözümün güven, hukuk, ahde vefa, birlikte yaşama veya sorumluluk bakımından hangi mekanizmayla barışa katkı verdiğini tartışır.", ["Bir anlaşma tek başına barış için yeterli midir?", "Güven ve adalet barışın neresindedir?"]),
       B("Bugüne İlke", 5, "Her grup tarihî olayı bugüne kopyalamadan, ondan çıkardığı bir genel ilkeyi yazar.")),
    "DKAB.10.3.3; TYMM Medine Dönemi'nden örnek olay üzerinden İslam-barış ilişkisini anlamlandırmayı önerir.")

W[22] = H(3, "Cihat kavramının anlam genişliği; nesnel ifade",
    Pn("Kavram Alanı: Cihat", "kavram-alani",
       B("Kelime Alanı", 10, "MEB/TYMM materyalinde cihatla ilişkili ceht, mücahede, davet, tebliğ, irşat, emir bilmaruf-nehiy anilmünker, kıtal/mukatele ve şehadet kavramlarını listeleyin. Öğrenciler kavramları 'çaba/mücadele', 'iletişim/çağrı', 'silahlı mücadele bağlamı' gibi açıklayıcı kümelere ayırır."),
       B("Daraltma Hatasını Bul", 10, "Cihadı yalnız savaşa indirgeyen bir cümleyle, hiçbir mücadele boyutu yokmuş gibi sunan başka bir cümleyi karşılaştırın. Öğrenciler her ikisinin de neden eksik olduğunu programın anlam genişliği vurgusuna göre açıklar."),
       B("Nesnel Tanım", 10, "Gruplar bağlamı ve anlam genişliğini koruyan 3-4 cümlelik bir açıklama yazar. Başka grup metni 'anlamı daraltıyor mu/genişletiyor mu?' diye kontrol eder.")),
    Pn("Barış ve Sorumluluk Paneli", "mini-panel",
       B("Dört Başlık", 5, "Dört mini grup kurun: kişinin nefsiyle mücadelesi, toplumsal sorumluluk, vatanı savunma, barışın tesisi. Her grup başlığının İslam-barış ilişkisiyle nasıl bağlandığını hazırlasın."),
       B("Panel", 20, "Gruplar 3'er dakikalık sunum yapar; dinleyenler yalnız kavramın bağlamını netleştiren sorular sorar. Öğretmen güncel savaş görüntülerinden veya taraflaştırıcı örneklerden kaçınarak kavram doğruluğunu korur.", ["Bir kavramı tarihî örneklerden koparmadan ama yalnız tarihe de hapsetmeden nasıl anlatırız?"]),
       B("Ortak Sonuç", 5, "Sınıf 'Barış pasiflik değildir; ...' cümlesini programın sorumluluk ve güven kavramlarıyla tamamlar.")),
    "DKAB.10.3.3; TYMM cihat kavramını salt savaşla sınırlandırmadan anlam genişliğiyle ve nesnel biçimde ele almayı ister.")

W[23] = H(3, "Bilgi kartları; ayetleri sınıflandırma",
    Pn("Üç Ayet - Üç Eksen", "ayet-karsilastirma",
       B("Metni Oku", 10, "Nahl 90, Nisâ 58 ve Bakara 208 ayetlerini Diyanet meali üzerinden okuyun. Öğrenciler doğrudan geçen emir, değer ve sorumluluk ifadelerini işaretler."),
       B("Mesaj Kartları", 15, "Kartlar 'adalet/iyilik', 'emanet/ehliyet ve hüküm', 'barışa bütüncül yöneliş' gibi ayet metinlerinden çıkan eksenlerde gruplanır. Her kart hangi ayetten geldiğini ve hangi ifadeye dayandığını taşır.", ["Üç ayetin ortak bir toplumsal ilke etrafında buluştuğu söylenebilir mi?", "Farklı ayetlerin mesajlarını tekleştirirken neyi kaybetmemek gerekir?"]),
       B("Üçten Bire", 5, "Her öğrenci üç ayeti tek bir üst başlık altında ilişkilendirir ve başlığı neden seçtiğini bir cümleyle açıklar.")),
    Pn("Ayetlerden İlke Tablosu", "calisma-yapragi",
       B("Çözümle", 10, "Çalışma yaprağında her ayet için 'ana kavram, istenen davranış/tutum, toplumsal sonuç' sütunlarını doldurun."),
       B("Sınıflandır", 10, "İkililer üç ayeti bireysel sorumluluk, yönetim/hukuk, toplumsal barış gibi bağlamlara yerleştirir; tek bir ayetin birden fazla bağlamı olabileceğini gerekçelendirir."),
       B("Açık Uçlu Soru", 10, "Öğrenciler 'Bu üç ayetten hangisinin bugünkü bir toplumsal probleme uygulanması daha açıklayıcı olur? Neden?' sorusuna kaynakla sınırlı, gerekçeli cevap yazar.")),
    "DKAB.10.3.4; TYMM Diyanet meali, bilgi kartları ve çalışma yaprağıyla çözümleme-sınıflandırma-açıklama önerir.")

# 4. ÜNİTE - DİN, ÇEVRE VE TEKNOLOJİ
W[24] = H(4, "Fıtrat-denge-ölçü-bütünlük; arkadaşa öğret",
    Pn("Dengeyi Gör", "cevre-kavram",
       B("Kavram Dörtlüsü", 10, "Fıtrat, denge, ölçü ve bütünlük kavramlarını çevre bağlamında dört karta yazın. Öğrenciler her kavrama doğal çevreden veya günlük tüketimden bir örnek ekler."),
       B("Bozulan Denge", 15, "Su israfı, plansız tüketim veya biyolojik çeşitlilik kaybı gibi bir sorunu seçin. Gruplar sorunun hangi dengeyi bozduğunu, insan davranışının rolünü ve ahlaki sorumluluğu kavramlarla ilişkilendirir.", ["Çevre sorunu yalnız teknik bir sorun mudur?", "Ahlaki sorumluluk hangi noktada başlar?"]),
       B("Kendi Yaşantım", 5, "Her öğrenci kendi hayatında değiştirebileceği tek bir davranışı, bunun hangi kavramla ilişkili olduğunu belirterek yazar.")),
    Pn("Arkadaşa Öğret", "akran-ogretimi",
       B("Uzmanlaş", 10, "Dört grup fıtrat, denge, ölçü ve bütünlükten birini MEB/TYMM materyalinden inceler ve çevreyle ilişkisini iki örnekle açıklar."),
       B("Yeni Gruplara Taşı", 10, "Her uzman yeni karma gruba geçer ve kavramını 2 dakikada öğretir. Dinleyenler yalnız bir netleştirme sorusu sorar."),
       B("Ortak Şema", 10, "Karma grup dört kavramı tek çevre-ahak şemasında ilişkilendirip 'insanın çevreye karşı sorumluluğu' sonucuna bağlar.")),
    "DKAB.10.4.1; TYMM fıtrat-denge-ölçü-bütünlük eksenini ve arkadaşa öğret tekniğini önerir.")

W[25] = H(4, "Konuşma halkası; balık kılçığı",
    Pn("Çevre Sorunu Balık Kılçığı", "balik-kilcigi",
       B("Sorunu Seç", 10, "Sınıf biyoçeşitlilik kaybı, çevre kirliliği veya bilinçsiz su tüketiminden birini seçer. Sorunun görünen sonucunu balık kılçığının başına yazın."),
       B("Nedenleri Ayır", 15, "Gruplar bireysel alışkanlıklar, ekonomik tercihler, teknoloji kullanımı, şehirleşme ve bilgi eksikliği gibi nedenleri oluşturur; her nedenin ahlaki sorumlulukla bağını tartışır.", ["Bir sorunun nedeni çoksa sorumluluk da dağılır mı?", "Bireysel davranış ile sistemsel sorun nasıl birlikte ele alınabilir?"]),
       B("Bir Etkili Müdahale", 5, "Her grup kendi neden ağacından en etkili müdahale noktasını seçip nedenini açıklar.")),
    Pn("Geleceğin Sesi: Konuşma Halkası", "konusma-halkasi",
       B("2036 Senaryosu", 10, "Su, atık veya biyoçeşitlilik konusunda bugünkü eğilimlerin sürdüğü 10 yıl sonraki bir mahalle/şehir senaryosu verin. Öğrenciler senaryoda kimin neyi kaybettiğini belirler."),
       B("Konuşma Halkası", 15, "Halka sırayla 'Bu sorun beni nasıl etkiler?', 'Benim payım ne olabilir?', 'Hangi sorumluluk bireysel, hangisi ortak?' sorularını konuşur. Bir öğrenci önceki cevaba bağ kurmadan yeni konu açamaz."),
       B("Sorumluluk Cümlesi", 5, "Öğrenciler çevre ahlakını yalnız 'çöp atmamak' düzeyinden çıkaran bir ilke cümlesi üretir.")),
    "DKAB.10.4.1; TYMM konuşma halkası, beyin fırtınası ve balık kılçığı diyagramını çevre sorunları için önerir.")

W[26] = H(4, "Çift sütun; örnek olay; güncel teknoloji ve mahremiyet",
    Pn("Teknoloji - Ahlak Çift Sütunu", "cift-sutun",
       B("Teknoloji Alanları", 10, "Sosyal medya, yapay zekâ, genetik/biyoteknoloji, sanal gerçeklik, dijital oyunlar ve savaş teknolojisinden üç alan seçin. Sol sütuna imkân/fayda, sağ sütuna ahlaki risk/sorumluluk yazılır."),
       B("İlişkiyi Çözümle", 15, "Gruplar bir alan için mahremiyet, kişisel veri, özgürlük, zarar vermeme ve sorumluluk unsurları arasındaki ilişkileri oklarla açıklar. 'Teknoloji nötrdür, sadece kullanıcı önemlidir' iddiasını gerekçelerle sınar.", ["Teknolojiyi geliştiren ile kullananın sorumlulukları aynı mıdır?", "Faydalı bir teknoloji mahremiyet ihlali yaparsa değerlendirme nasıl değişir?"]),
       B("Etik Kontrol", 5, "Her grup yeni bir teknoloji için üç soruluk etik kontrol listesi oluşturur.")),
    Pn("Yapay Zekâ ve Mahremiyet Vakası", "teknoloji-vaka",
       B("Vaka", 5, "Bir okul uygulaması öğrencilerin ödevlerini yapay zekâyla analiz ediyor; bunun için gereğinden fazla kişisel veri topluyor ve verilerin ne kadar süre saklanacağını açıkça bildirmiyor. Öğrenciler ilk değerlendirmeyi yazar."),
       B("Etik Kurul", 20, "Gruplar öğrenci, öğretmen, geliştirici ve veli bakışlarını ayrı ayrı değerlendirir. Mahremiyet, yarar, açık rıza, veri minimizasyonu ve başkasının özgürlüğüne saygı başlıklarında çözüm önerir.", ["İyi amaç kişisel veriyi sınırsız toplamayı haklı çıkarır mı?", "Bir teknolojiyi reddetmeden risk azaltmak mümkün mü?"]),
       B("Karar ve Şart", 5, "Grup 'kullanılsın/kullanılmasın' yerine 'şu şartlarla kullanılabilir' biçiminde ölçülü karar yazar.")),
    "DKAB.10.4.2; TYMM çift sütun ve örnek olay tekniklerini, yapay zekâ-sosyal medya-genetik gibi alanlarda mahremiyet ve kişisel veri sorumluluğunu açıkça işler.")

W[27] = H(4, "5N1K; çevre-teknoloji ilişkisinin sınıflandırılması",
    Pn("5N1K ile E-Atık", "5n1k",
       B("Sorunu Tanımla", 10, "Elektronik atık veya hızlı cihaz yenileme sorununu seçin. 5N1K ile 'ne, neden, nerede, ne zaman, nasıl, kim etkileniyor?' soruları doldurulur."),
       B("İlişkileri Sınıflandır", 15, "Cevapları üretim, tüketim, enerji, atık, doğal kaynak ve canlılar üzerindeki etki başlıklarında sınıflandırın. Her başlıkta teknolojinin hem çözüm hem sorun üretme ihtimalini konuşun.", ["Teknolojik ilerleme ile sürdürülebilirlik zorunlu olarak çatışır mı?"]),
       B("Özet Şema", 5, "Grup çevre-teknoloji ilişkisini 5 ok ve en fazla 5 kavramla şemalaştırır.")),
    Pn("Çember: Teknoloji Çözer mi?", "cember",
       B("İddia", 5, "İddia: 'Çevre sorunlarını yine teknoloji çözeceği için bireysel tüketim alışkanlıklarını değiştirmeye gerek yok.' Öğrenciler ilk konumlarını not eder."),
       B("Çember Tartışması", 20, "İç çember teknolojinin çözüm kapasitesini, dış çember yeni çevresel maliyetleri ve tüketim davranışını izler. 10. dakikada roller değişir. Her görüş en az bir karşı örnekle sınanır.", ["Bir çözüm yeni bir problem doğurursa nasıl değerlendirilmelidir?"]),
       B("Dengeli Özet", 5, "Öğrenciler 'Teknoloji çevre için hem ... hem ... olabilir; belirleyici olan ...' kalıbını kendi cümlesiyle tamamlar.")),
    "DKAB.10.4.3; TYMM çevre-teknoloji ilişkisini çözümleme, sınıflandırma ve 5N1K/çember tekniklerini önerir.")

W[28] = H(4, "Ben Olsam Ne Yapardım; konferans konuşması",
    Pn("Ben Olsam Ne Yapardım?", "ben-olsam",
       B("Yerel Sorun", 10, "Okul çevresinde su tüketimi, tek kullanımlık ürünler, enerji veya elektronik atık üzerinden gerçekçi bir durum seçin. Sorunun kimleri etkilediği ve mevcut uygulama belirlenir."),
       B("Çözüm Seçenekleri", 10, "Gruplar üç çözüm üretir ve her birini etki, maliyet/uygulanabilirlik, adalet ve sürdürülebilirlik açısından karşılaştırır. En iyi çözümün de bir maliyeti olabileceğini belirtir."),
       B("Karar Notu", 10, "Her grup okul yönetimine 5 cümlelik 'Ben olsam...' karar notu yazar; iki gerekçe ve bir olası yan etki içerir.")),
    Pn("Mini Konferans Metni", "konusma-metni",
       B("Tez Kur", 10, "TYMM performans görevi önerisinden hareketle 'Teknoloji çevre sorunlarının hem nedeni hem çözüm aracı olabilir.' tezi için öğrenciler iki destekleyici nokta ve bir karşı örnek belirler."),
       B("90 Saniyelik Konuşma", 15, "Öğrenci giriş, iki gerekçe ve sonuçtan oluşan kısa konuşma metni yazar. Kaynak/örnek belirtir; slogan yerine ilişki açıklamaya çalışır."),
       B("Akran Kontrolü", 5, "Akran yalnız üç ölçüte bakar: ilişki doğru mu, tek taraflı mı, sonuç gerekçelerden çıkıyor mu?" )),
    "DKAB.10.4.3; TYMM 'Ben Olsam Ne Yapardım' etkinliğini ve çevre-teknoloji konferans konuşma metni görevini önerir.")

W[29] = H(4, "Rum 41; bilgi kartı ve öz değerlendirme",
    Pn("Rûm 41 Sebep - Sonuç Haritası", "ayet-sebep-sonuc",
       B("Metni Çözümle", 10, "Rûm 41'i Diyanet meali üzerinden okuyun. Öğrenciler ayette insan davranışı, bozulma/fesat, sonuç ve dönüş/ibret bağlantılarını metinden işaretler."),
       B("Sebep - Sonuç Haritası", 15, "Gruplar ayetin mesajını güncel çevre sorunlarına doğrudan 'bu ayet şunu söylüyor' diye yapıştırmadan, 'ilke düzeyi' ve 'güncel örnek' olarak iki ayrı sütunda ilişkilendirir.", ["Ayetin mesajını güncel bir soruna uygularken metne söylemediği ayrıntıları eklememek neden önemlidir?"]),
       B("Öz Değerlendirme", 5, "Öğrenci 'metinden çıkardığım mesaj', 'benim yaptığım yorum', 'günlük hayata bağlantım' olmak üzere üç ayrı cümle yazar.")),
    Pn("Bilgi Kartı: İnsan - Çevre", "ayet-bilgi-karti",
       B("Ana Kavramlar", 10, "Ayet içindeki temel kavram ve fiiller için bilgi kartları hazırlayın. Her kartta 'metindeki anlam', 'ilişkili kavram', 'ana mesajdaki rolü' alanı olsun."),
       B("Kartlarla Özet", 10, "Gruplar kartları en önemli olandan destekleyici olana sıralar ve seçimi savunur. Farklı sıralamalar olduğunda ayet metnine dönerek gerekçe kontrol edilir."),
       B("Başlık ve Özet", 10, "Her öğrenci ayete bir açıklayıcı başlık koyar ve 35-50 kelimelik özet yazar.")),
    "DKAB.10.4.4; TYMM Rûm 41 için Diyanet meali, bilgi kartları ve öz değerlendirme önerir.")

# 5. ÜNİTE - İSLAM DÜŞÜNCESİNDE YORUMLAR
W[30] = H(5, "Metin inceleme; beyin fırtınası; günlük yorum farkıyla köprü",
    Pn("Aynı Olay - Farklı Yorum", "yorum-koprusu",
       B("Gündelik Örnek", 10, "Aynı kısa olay metnini okuyup öğrencilerden 'neden böyle oldu?' sorusuna bireysel cevap isteyin. Farklı cevapların hangi varsayım, deneyim veya ön bilgiye dayandığını konuşun."),
       B("Dinî Yorumun Sebepleri", 15, "MEB/TYMM materyalindeki dinî yorum farklılıklarının sebeplerini içeren metni inceleyin. Öğrenciler tarihî, siyasi, coğrafi, kültürel, insani/yorumlayıcı etkenleri metinden çıkarıp ilk gündelik örnekle köprü kurar.", ["Din ile dinin yorumu neden aynı şey değildir?", "Farklı yorumların varlığı hangi durumda zenginlik, hangi durumda sorun olabilir?"]),
       B("Sebep Haritası", 5, "Her grup sebepler arasında en az iki ilişki kuran mini bilgi haritası oluşturur.")),
    Pn("Soru Duvarı", "soru-beyin-firtinasi",
       B("Merak Soruları", 10, "Öğrenciler mezhep ve yorum farklılıkları hakkında merak ettiği soruları anonim yazar. Öğretmen soruları 'neden ortaya çıktı', 'hangi konuda farklılaştı', 'din-yorum ilişkisi' gibi kümelere ayırır."),
       B("Beyin Fırtınası", 10, "Her küme için olası sebepler serbestçe üretilir; bu aşamada doğru-yanlış müdahalesi sınırlı tutulur. Sonra MEB/TYMM metnine dönülerek fikirler doğrulanır, düzeltilir veya çıkarılır."),
       B("Bütünsel Sonuç", 10, "Gruplar 'tek sebep' açıklamalarını eleştirip yorum farklılığını en az üç etkenin birlikte rol alabildiği bir modelle açıklar.")),
    "DKAB.10.5.1; TYMM günlük farklı yorumlarla köprü kurma, metinden sebepleri çıkarma ve beyin fırtınası önerir.")

W[31] = H(5, "Zıt panel; açık fikirlilik; din-yorum ayrımı",
    Pn("Zıt Panel: Zenginlik mi Sorun mu?", "zit-panel",
       B("İki İddia", 5, "İddia A: 'Dinî yorum farklılıkları yalnız sorun üretir.' İddia B: 'Her yorum farklılığı otomatik olarak zenginliktir.' Öğrenciler iki iddianın da aşırı yönlerini fark etmeye çalışır."),
       B("Zıt Panel", 20, "Bir grup yorum çeşitliliğinin farklı çağ ve coğrafyalarda yaşanabilirlik ve fikir özgürlüğüne katkısını; diğer grup yanlış yorum, bağlamdan koparma ve çatışma risklerini tartışır. Son 5 dakikada gruplar karşı tarafın en güçlü gerekçesini kabul etmek zorundadır.", ["Dinin kendisi ile yorumdan doğan problemi nasıl ayırabiliriz?", "Açık fikirlilik her yorumu eşit derecede doğru kabul etmek midir?"]),
       B("Dengeli İlke", 5, "Sınıf 'Yorum farklılığı değerlidir; ancak...' diye başlayan ölçütlü bir sonuç cümlesi yazar.")),
    Pn("Sebep Ağı Laboratuvarı", "neden-agi",
       B("Etken Kartları", 10, "Tarih, coğrafya, siyaset, kültür, eğitim, insanın anlama biçimi gibi etkenleri kartlara yazın. Öğrenciler tek tek tanımlamak yerine aralarında neden-sonuç/etkileşim okları kurar."),
       B("Kurgusal Bölge", 15, "Bir kurgusal toplum senaryosunda coğrafya, siyasi olaylar ve eğitim geleneği gibi şartlar verilir. Gruplar bu şartların yorum farklılaşmasına nasıl zemin hazırlayabileceğini, 'kesin böyle olur' demeden olasılık diliyle açıklar."),
       B("Tek Sebep Hatası", 5, "Her grup yorum farklılığını tek bir sebebe indirgeyen bir cümleyi daha bütüncül hâle getirir.")),
    "DKAB.10.5.1; TYMM zıt panel, açık fikirlilik ve bütünsel bakış açısını önerir.")

W[32] = H(5, "İstasyon; temel kavram ve yorum türleri",
    Pn("İstasyon: Kavramlardan Yorumlara", "istasyon",
       B("İstasyonları Kur", 10, "Dört istasyon hazırlayın: (1) mezhep-fıkıh-fırka-tevil kavramları, (2) Ehlisünnet itikadi yorumları: Mâtürîdîlik-Eş'arîlik, (3) fıkhi yorumlar: Hanefîlik-Mâlikîlik-Şâfiîlik-Hanbelîlik, (4) Şia bağlamında Caferîlik. İçerikler yalnız MEB/TYMM materyalindeki çerçevede olsun."),
       B("Dönüşümlü Çalışma", 15, "Gruplar istasyonlarda 'alanı nedir, temel özelliği nedir, hangi kavramla karıştırılmamalıdır?' sorularını doldurur. Her istasyonda önceki grubun bir ifadesini kontrol edip gerektiğinde düzeltir.", ["İtikadi yorum ile fıkhi yorum aynı tür sorulara mı cevap verir?"]),
       B("Sınıflandırma Kontrolü", 5, "Öğretmen karışık 6 kart gösterir; öğrenciler kartın kavram, itikadi yorum veya fıkhi yorum olup olmadığını gerekçeyle yerleştirir.")),
    Pn("Kavram Mahkemesi", "kavram-ayirma",
       B("Karışan Kavramlar", 10, "Mezhep, fıkıh, itikat, tevil ve fırka kavramlarının kısa tanımlarını MEB/TYMM materyalinden çıkarın. Öğrenciler iki kavramı bilerek birbirine karıştıran örnek cümleleri bulur."),
       B("Düzelt ve Gerekçelendir", 10, "İkililer hatalı cümleyi düzeltir; 'bu kavramın alanı şudur, diğeri bundan farklıdır' biçiminde gerekçe yazar."),
       B("Yorum Türü Haritası", 10, "Sınıf, programda geçen yorumları 'itikadi' ve 'fıkhi' başlıklarında yapılandırır; siyasi tarih bağlamının ortaya çıkışlarda rolünü ayrı not olarak gösterir.")),
    "DKAB.10.5.2; TYMM temel kavramları açıklama ve yorumları karşılaştırmada istasyon tekniğini önerir.")

W[33] = H(5, "Karşılaştırma; yapılandırılmış grid",
    Pn("Benzerlik - Farklılık Tablosu", "mezhep-karsilastirma",
       B("Karşılaştırma Ölçütü", 10, "Öğrenciler karşılaştırma yapmadan önce ölçüt belirler: yorumun alanı (itikadi/fıkhi), tarihî-kültürel bağlam, programda vurgulanan temel özellik. 'Hangisi daha doğru?' gibi program çıktısının dışındaki yarışmacı ölçütlerden kaçının."),
       B("Programdaki Başlıkları Karşılaştır", 15, "Mâtürîdîlik ve Eş'arîlik ile Hanefîlik, Mâlikîlik, Şâfiîlik, Hanbelîlik ve Caferîliği MEB/TYMM materyalinde verilen kapsam kadar inceleyin. Önce benzerlikleri, sonra farklılıkları listeleyin. Yorum türlerini birbirine karıştırmayın.", ["Bir fıkhi yorumla itikadi yorumu doğrudan aynı ölçütte karşılaştırmak neden sorunludur?"]),
       B("Saygı İlkesi", 5, "Öğrenciler farklı yorumları tanımanın, kendi görüşünden vazgeçmek anlamına gelmeden saygı ve kültür okuryazarlığına nasıl katkı sunduğunu bir cümleyle yazar.")),
    Pn("Yapılandırılmış Grid", "grid",
       B("Grid'i Doldur", 10, "Kavramlar ve yorum adları 3x3 veya 4x3 gridde karışık verilir. Öğrenciler 'itikadi yorum', 'fıkhi yorum', 'temel kavram' sorularına uygun hücre numaralarını seçer."),
       B("Hata Analizi", 10, "Cevaplar yalnız doğru-yanlış olarak geçilmez. Yanlış eşleştirmelerde öğrenci 'hangi iki alanı karıştırdım?' sorusuna cevap verir."),
       B("Kendi Gridini Yaz", 10, "İkililer 6 kartlık yeni mini grid oluşturur ve başka ikiliye çözdürür. Soruların tek ve açık cevabı olup olmadığını birlikte kontrol eder.")),
    "DKAB.10.5.2; TYMM benzerlik-farklılık listesi, istasyon ve yapılandırılmış grid önerir.")

W[34] = H(5, "Hucurât 13; bilgi kartları; Diyanet meali",
    Pn("Hucurât 13 Mesaj Haritası", "ayet-kavram",
       B("Metni Oku", 10, "Hucurât 13'ü Diyanet meali üzerinden okuyun. Öğrenciler insanlığın ortak kökeni, farklılaşma/tanışma ve değer ölçütüyle ilgili ifadeleri ayrı işaretler."),
       B("Bilgi Kartları", 15, "Kartlarda 'metindeki ifade', 'kavram', 'ana mesaj' alanları olsun. Gruplar insan çeşitliliği, tanışma, üstünlük iddiası ve takva ölçütü arasındaki ilişkiyi metne bağlı biçimde kurar.", ["Farklılık neden otomatik olarak üstünlük veya aşağılık anlamına gelmez?", "Ayet 'tanışma' fikrini toplumsal ilişki açısından nasıl konumlandırıyor?"]),
       B("Bir Cümlelik İlke", 5, "Her öğrenci ayetin mesajını güncel kimlik tartışmalarına doğrudan sloganlaştırmadan, genel bir ilke cümlesiyle yazar.")),
    Pn("Önyargıdan Metne Dönüş", "ayet-vaka",
       B("Kısa Vaka", 10, "Bir öğrenci, başka şehirden veya kültürden gelen bir arkadaşını yalnız ait olduğu gruba göre değerlendiriyor. Sınıf ilk olarak olayda hangi varsayımın problemli olduğunu bulur."),
       B("Ayetle Sınama", 10, "Hucurât 13'ün mesajı kullanılarak vakadaki üstünlük, farklılık ve tanışma kavramları yeniden değerlendirilir. Öğrenciler 'ayet bunu doğrudan söyler' ile 'biz bu ilkeden şu yorumu yaparız' ayrımını korur."),
       B("Davranışa Çevir", 10, "Gruplar önyargıyı azaltmak ve gerçek tanışmayı artırmak için okul ortamında uygulanabilir iki davranış önerir; önerinin ayet mesajıyla bağını açıklar.")),
    "DKAB.10.5.3; TYMM Hucurât 13 için Diyanet meali, bilgi kartları, çözümleme-sınıflandırma-açıklama basamaklarını önerir.")

W[35] = H(5, "Özetleme; bilgi grafiği ve öz değerlendirme",
    Pn("Hucurât 13 Bilgi Grafiği", "bilgi-grafigi",
       B("Dört Unsur", 10, "Ayet mesajını dört unsurda düzenleyin: ortak insanlık, farklılık, tanışma, değer ölçütü. Her unsur için metin dayanağı bir kelime/ifade ve kendi açıklaması yazılır."),
       B("Grafiği Kur", 15, "Gruplar dört unsur arasındaki ilişkiyi oklarla gösteren küçük bilgi grafiği hazırlar. Görsel süs yerine kavramsal ilişkinin anlaşılır olmasına öncelik verir.", ["Ayetin mesajı hangi yanlış üstünlük anlayışlarını sorgular?"]),
       B("30 Saniyelik Sunum", 5, "Her grup grafiğini 30 saniyede açıklar; dinleyenler yalnız metne dayanmayan bir ekleme varsa sorar.")),
    Pn("Üniteyi Bağlayan Sonuç", "unit-sentez",
       B("Üç Başlık", 10, "Dinî yorum farklılıklarının sebepleri, yorum türlerinin karşılaştırılması ve Hucurât 13'ün farklılık/tanışma mesajını üç ayrı sütunda özetleyin."),
       B("Bağlantıyı Kur", 10, "Öğrenciler bu üç sütun arasında 'farklılık', 'yorum', 'saygı', 'insan değeri' kavramlarını kullanarak iki bağlantı cümlesi yazar. Dinî yorum farklılığı ile insan topluluklarının çeşitliliğini birbirine eşitlemeden yalnız ortak 'farklılıkla ilişki kurma' temasını tartışır."),
       B("Öz Değerlendirme", 10, "Öğrenci üç soruya cevap verir: 'En iyi açıkladığım kavram?', 'Hâlâ karıştırdığım kavram?', 'Bir görüşü karşılaştırırken bundan sonra hangi ölçüte dikkat edeceğim?'" )),
    "DKAB.10.5.3; TYMM öz değerlendirme ve yorum farklılıklarında bilgi grafiği yaklaşımını destekler.")

def patch_data():
    d = json.loads(J.read_text(encoding="utf-8"))
    grade10 = next(g for g in d["grades"] if g["grade"] == 10)
    course = next(c for c in grade10["courses"] if c.get("subjectId") == "dkab")
    active = []
    for page in course["pages"]:
        if page.get("type") != "week" or not page.get("outcome"):
            continue
        w = page["weekNumber"]
        if w not in W:
            raise RuntimeError(f"10. sınıf aktif hafta için v7 planı yok: {w}")
        h = W[w]
        h["sourceOutcomeCode"] = page["outcome"].split(".", 4)[0] + "." + page["outcome"].split(".", 4)[1] + "." + page["outcome"].split(".", 4)[2] + "." + page["outcome"].split(".", 4)[3]
        # Yukarıdaki split bazı metinlerde güvenli değil; regex ile düzelt.
        import re
        m = re.search(r"(DKAB\.10\.\d+\.\d+)", page["outcome"])
        h["sourceOutcomeCode"] = m.group(1) if m else ""
        page["hints"] = h
        active.append(w)
    if len(active) != 34:
        raise RuntimeError(f"Beklenen 34 aktif 10. sınıf haftası yerine {len(active)}")
    if set(active) != set(W):
        raise RuntimeError(f"Hafta kümesi uyuşmuyor: app={active}, curated={sorted(W)}")
    d["schemaVersion"] = 7
    d["generatedFor"] = "Kazanım v7"
    d.setdefault("sources", []).append("DKAB 10 İpucu v7: 2026 MEB/TYMM 10. sınıf ünite sayfaları, öğrenme çıktıları, süreç bileşenleri ve öğrenme-öğretme uygulamaları temel alınarak haftaya özgü yeniden hazırlanmıştır.")
    J.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")

def patch_ui():
    s = A.read_text(encoding="utf-8")
    s = s.replace('titleCol.addView(label("2 alternatif • 30 dk • özgün ders fikri", 12, TEXT_SECONDARY, false));',
                  'titleCol.addView(label(hintSubtitle(page), 12, TEXT_SECONDARY, false));')
    marker = "    private View buildHintPlanView(JSONObject plan) throws Exception {"
    if "private String hintSubtitle(PlanPage page)" not in s:
        helper = '''    private String hintSubtitle(PlanPage page) {
        try {
            JSONObject h = new JSONObject(page.hintsJson);
            String label = h.optString("label", "");
            if (!label.trim().isEmpty()) return label;
        } catch (Exception ignored) { }
        return "2 alternatif • 30 dk • özgün ders fikri";
    }

'''
        if marker not in s:
            raise RuntimeError("hint UI marker bulunamadı")
        s = s.replace(marker, helper + marker)
    s = s.replace('TextView badge = label("v6", 13, Color.WHITE, true);',
                  'TextView badge = label("v7", 13, Color.WHITE, true);')
    s = s.replace("Sürüm 6", "Sürüm 7")
    s = s.replace("DKAB 9–11 için her öğretim haftasında iki alternatif 30 dakikalık İpucu ders planı vardır.",
                  "DKAB 9–11 için iki alternatif 30 dakikalık İpucu planları vardır. 10. sınıf planları v7'de TYMM'nin süreç bileşenleri ve öğrenme-öğretme uygulamaları esas alınarak haftaya özgü yeniden hazırlanmıştır.")
    A.write_text(s, encoding="utf-8")

def patch_version():
    s = G.read_text(encoding="utf-8")
    s = s.replace("versionCode = 6", "versionCode = 7")
    s = s.replace('versionName = "6"', 'versionName = "7"')
    G.write_text(s, encoding="utf-8")

def validate():
    d = json.loads(J.read_text(encoding="utf-8"))
    g = next(x for x in d["grades"] if x["grade"] == 10)
    c = next(x for x in g["courses"] if x.get("subjectId") == "dkab")
    pages = [p for p in c["pages"] if p.get("type") == "week" and p.get("outcome")]
    assert len(pages) == 34
    styles = set()
    titles = set()
    for p in pages:
        h = p["hints"]
        assert h["qualityLevel"] == "curated-official-2026"
        assert h["sourceOutcomeCode"].startswith("DKAB.10.")
        assert len(h["researchBasis"]) >= 3
        assert len(h["plans"]) == 2
        assert h["plans"][0]["style"] != h["plans"][1]["style"]
        for plan in h["plans"]:
            styles.add(plan["style"])
            titles.add(plan["title"])
            assert 1 <= len(plan["blocks"]) <= 3
            assert sum(b["minutes"] for b in plan["blocks"]) == 30
            assert min(b["minutes"] for b in plan["blocks"]) >= 5
            assert all(b["title"].strip() and b["text"].strip() for b in plan["blocks"])
            assert "PLACEHOLDER" not in json.dumps(plan, ensure_ascii=False)
    assert len(styles) >= 20, len(styles)
    assert len(titles) >= 50, len(titles)
    # 18, 36, 37 okul temelli/boş plan haftaları - içerik uydurulmaz.
    for w in (18, 36, 37):
        p = next(x for x in c["pages"] if x.get("type") == "week" and x.get("weekNumber") == w)
        assert not p.get("outcome")
    print("v7 curated grade10 OK", len(pages), "weeks", len(styles), "styles", len(titles), "plan titles")

if __name__ == "__main__":
    patch_data()
    patch_ui()
    patch_version()
    validate()
