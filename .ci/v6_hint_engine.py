# -*- coding: utf-8 -*-
import json, re, sys
from pathlib import Path

P=Path(sys.argv[1])
J=P/"app/src/main/assets/curriculum_2026_2027.json"

def code(o):
    m=re.search(r"(DKAB\.\d+\.\d+\.\d+)",o or "")
    return m.group(1) if m else ""

def pick(topic):
    t=(topic or "").lower()
    table=[
      (("yaratılış",),[
        "İnsanı diğer varlıklardan ayıran özelliklerden hangisi sorumlulukla en yakından ilişkilidir?",
        "Akıl, irade ve sorumluluk arasında nasıl bir bağ kurulabilir?",
        "Bir özelliğin varlığı insana hangi imkânı ve hangi sorumluluğu yükler?"
      ],"Bir öğrenci, kurallara kusursuz uyan bir robotun da insan kadar ahlaki sorumluluk taşıyabileceğini savunuyor; arkadaşı ise seçim yapma ve iradenin belirleyici olduğunu söylüyor."),
      (("doğruyu arayan","bilgi ve bilginin"),[
        "Bir bilginin güvenilir olduğuna karar verirken hangi ölçütler kullanılmalıdır?",
        "Çok kişinin aynı şeyi söylemesi onu doğru yapar mı? Neden?",
        "Akıl, duyu, haber ve vahyin bilgiye ulaşmadaki imkân ve sınırları nelerdir?"
      ],"Sınıf grubuna kaynağı belirtilmeyen fakat yüzlerce kez paylaşılmış bir bilgi geliyor. Bir öğrenci “Bu kadar kişi paylaştıysa doğrudur.” diyor; diğeri kaynağı görmeden kabul etmiyor."),
      (("dua",),[
        "Dua ile çaba birbirinin alternatifi midir, tamamlayıcısı mıdır?",
        "İnsan değiştirebildiği ve değiştiremediği durumları nasıl ayırabilir?",
        "Dua insanın sorumluluk bilincini nasıl etkileyebilir?"
      ],"Bir öğrenci sınavdan önce dua ediyor fakat hiç hazırlık yapmıyor. Arkadaşı dua ile çabanın birlikte düşünülmesi gerektiğini savunuyor."),
      (("ibadet",),[
        "İbadetin insanın davranışlarında nasıl bir karşılık bulması beklenir?",
        "Amaç, niyet ve davranış arasında nasıl bir ilişki kurulabilir?",
        "İbadetin bireysel ve toplumsal etkileri nasıl ayırt edilebilir?"
      ],"Bir öğrenci ibadetlerini düzenli yaptığını söylüyor fakat arkadaşlarına karşı sürekli kırıcı davranıyor. Sınıfta “İbadetin davranışa yansıması beklenir mi?” sorusu doğuyor."),
      (("iman","inanç esas"),[
        "İman yalnızca bir bilgi midir, yoksa insanın tutumlarını da etkiler mi?",
        "İman esasları birbirinden bağımsız mı, ilişkili bir bütün mü oluşturur?",
        "Bir inancın birey ve toplum hayatında görünür hâle gelmesi ne demektir?"
      ],"Bir öğrenci “İnanç insanın içindedir, davranışlara yansıması gerekmez.” diyor; arkadaşı ise inancın tutum ve seçimleri etkilemesi gerektiğini düşünüyor."),
      (("ahlak",),[
        "Bir davranışı ahlaki yapan şey sonuç, niyet, ilke veya bunların birlikte değerlendirilmesi midir?",
        "Aynı davranış farklı şartlarda farklı biçimde değerlendirilebilir mi?",
        "Bir ahlaki ilkeyi günlük hayata taşımak neden bazen zorlaşır?"
      ],"Bir öğrenci arkadaşının özel mesajını, “onu korumak” amacıyla başka birine gösteriyor. Niyet iyi olsa bile davranışın ahlaki olup olmadığı tartışılıyor."),
      (("muhammed","ehlibeyt"),[
        "Bir kişiyi örnek almak ile onu yalnızca taklit etmek arasında ne fark vardır?",
        "Hz. Muhammed’in beşerî yönü ile peygamberlik görevi nasıl birlikte düşünülmelidir?",
        "Bir örnek davranışı bugünün şartlarında uygulamak için hangi ilkeyi anlamak gerekir?"
      ],"Bir öğrenci “Örnek almak, geçmişteki davranışı aynen kopyalamaktır.” diyor; diğeri davranışın arkasındaki ilkeyi anlayıp bugüne taşımak gerektiğini savunuyor."),
      (("kader","irade","sorumluluk"),[
        "Bir insan hangi durumlarda gerçekten seçim yapmış sayılır?",
        "Sorumluluk ile irade arasında nasıl bir ilişki vardır?",
        "Başına gelen her şeyi kadere bağlamak insanın sorumluluğunu nasıl etkiler?"
      ],"Bir öğrenci sınava hiç çalışmadıktan sonra düşük not alınca “Kaderimde bu varmış.” diyor. Arkadaşları kader, tercih ve sorumluluk kavramlarını kullanarak bu açıklamayı değerlendiriyor."),
      (("allah-âlem","varlığının delilleri"),[
        "Bir gözlem ne zaman delil olarak kullanılabilir?",
        "Düzen, sebep ve anlam arasında nasıl bir ilişki kurulabilir?",
        "Aynı olguya bakan iki insanın farklı sonuçlara ulaşması mümkün müdür? Neden?"
      ],"İki öğrenci doğadaki düzenli bir olguyu gözlemliyor; biri bunu yalnızca işleyen sebeplerle açıklamanın yeterli olduğunu, diğeri bunun daha geniş bir anlam sorusu doğurduğunu söylüyor."),
      (("isim ve sıfat",),[
        "Allah’ın bir ismini bilmek ile o ismin anlamını hayata yansıtmak arasında ne fark vardır?",
        "İsim ve sıfatlar Allah tasavvurunu nasıl şekillendirir?",
        "Bir kavramı davranışa dönüştürmek için önce hangi anlam bağını kurmak gerekir?"
      ],"Bir öğrenci Allah’ın isimlerini ezberlemenin yeterli olduğunu söylerken diğeri bu isimlerin insanda sorumluluk ve davranış bilinci oluşturması gerektiğini düşünüyor."),
      (("adalet","eşitlik"),[
        "Herkese aynı davranmak her zaman adil midir?",
        "Eşitlik ile adaletin aynı olmadığı bir örnek kurulabilir mi?",
        "Bir kararın adil olduğunu hangi ölçütlerle savunabiliriz?"
      ],"Bir yardım kampanyasında herkese aynı miktarda destek verilmesi öneriliyor; başka bir grup ihtiyacı daha fazla olana daha fazla destek verilmesinin daha adil olduğunu savunuyor."),
      (("barış",),[
        "Barış yalnızca çatışmanın olmaması mıdır?",
        "Bir anlaşmazlıkta adalet sağlanmadan kalıcı barış kurulabilir mi?",
        "Barışı güçlendiren bireysel davranışlara hangi örnekler verilebilir?"
      ],"İki grup arasındaki tartışma, taraflar konuşmayı bıraktığı için sona eriyor. Bir öğrenci bunun barış olduğunu, diğeri sorun çözülmediği için yalnızca sessizlik olduğunu savunuyor."),
      (("çevre","teknoloji"),[
        "Yapabiliyor olmak, yapmamız gerektiği anlamına gelir mi?",
        "Bir teknolojinin faydası ile doğurabileceği zarar nasıl birlikte değerlendirilir?",
        "Çevreye karşı sorumluluk yalnız bireysel tercihlerle sınırlı mıdır?"
      ],"Bir uygulama insanların hayatını kolaylaştırıyor fakat kullanıcıların özel verilerini gereğinden fazla topluyor. “Yapılabiliyor olması yapılması gerektiği anlamına gelir mi?” sorusu tartışılıyor."),
      (("yorum farklılık","itikadi","fıkhi","siyasi"),[
        "Aynı temel kaynağa bağlı insanlar neden farklı yorumlara ulaşabilir?",
        "Yorum farklılığı ile temel inanç farklılığı aynı şey midir?",
        "Bir yorumun oluşmasında tarih, kültür ve yöntem nasıl etkili olabilir?"
      ],"Aynı temel metni okuyan iki kişi tarihî bağlam ve yöntem konusunda farklı öncelikler kullanarak farklı sonuçlara ulaşıyor. Sınıf farklılığın hangi aşamada oluştuğunu bulmaya çalışıyor."),
      (("felsefe","bilim"),[
        "Aynı soruya din, felsefe ve bilim neden farklı yöntemlerle yaklaşabilir?",
        "Farklı yöntem kullanmak mutlaka çatışma anlamına gelir mi?",
        "Bir görüşün hangi soruya cevap verdiğini belirlemek neden önemlidir?"
      ],"Aynı “insan nedir?” sorusuna bir bilim insanı, filozof ve din araştırmacısının farklı türde cevaplar vermesi öğrenciler arasında “hangisi doğru?” tartışması doğuruyor."),
      (("sanat",),[
        "Bir sanat eserinde inanç veya dünya görüşünün etkisi nasıl fark edilebilir?",
        "Sanat yalnız estetik bir ürün müdür, anlam taşıyan bir ifade biçimi midir?",
        "Aynı sembol farklı kültürlerde farklı anlamlar taşıyabilir mi?"
      ],"İki kişi aynı mimari esere bakıyor; biri yalnız estetik biçimi, diğeri eserdeki sembol ve inanç dünyasını önemsiyor."),
      (("medeniyet",),[
        "Bir medeniyeti yalnız yapılar ve eserlerle tanımlamak yeterli midir?",
        "Bilgi, değer, kurum ve sanat bir medeniyetin oluşumunda nasıl birlikte etkili olur?",
        "Geçmişten kalan bir miras bugünün sorunlarına nasıl katkı sağlayabilir?"
      ],"Bir şehirde görkemli yapılar var fakat eğitim, hukuk, dayanışma ve bilgi üretimi zayıf. Öğrenciler yalnız büyük yapılarla “medeniyet” kurulup kurulamayacağını tartışıyor."),
      (("kötülük problemi",),[
        "İnsanların sebep olduğu kötülüklerle doğal olaylardan doğan acılar aynı biçimde mi değerlendirilmelidir?",
        "Özgür irade tartışması kötülük problemine nasıl bağlanır?",
        "Bir problemin zor olması onun hakkında düşünmeyi bırakmak için yeterli midir?"
      ],"Aynı şehirde bir doğal afet ve insanların ihmali sonucu oluşan ayrı bir felaket yaşanıyor. Öğrenciler bu iki tür acının sorumluluk bakımından aynı değerlendirilip değerlendirilemeyeceğini tartışıyor."),
      (("istismar","yeni dinî hareket"),[
        "Bir söylemin dinî kavramlar kullanması onu güvenilir kılmaya yeter mi?",
        "İnsanları sorgulamaktan uzaklaştıran hangi yöntemler risk işareti olabilir?",
        "Bilgi kaynağını ve otorite iddiasını sorgulamak neden önemlidir?"
      ],"Bir topluluk lideri üyelerinden kendisini sorgulamamalarını, dış kaynak okumamalarını ve sürekli para vermelerini istiyor; bunu da dinî kavramlarla gerekçelendiriyor."),
      (("yahudilik","hristiyanlık"),[
        "Bir dini kendi tarihî bağlamı ve kaynakları içinde incelemek neden önemlidir?",
        "Benzerlikleri belirlemek kadar farklılıkları doğru tanımlamak neden gereklidir?",
        "Bir din hakkında genelleme yapmadan önce hangi tür bilgiler kontrol edilmelidir?"
      ],"İki internet sitesi aynı din hakkında birbirinden çok farklı bilgiler veriyor. Biri o dinin kendi kaynaklarına dayanıyor, diğeri kaynaksız genellemeler yapıyor.")
    ]
    for keys,qs,sc in table:
        if any(k in t for k in keys): return qs,sc
    return [f"{topic} konusunda temel kavramlar arasında nasıl bir ilişki kurulabilir?","Bu konuyla ilgili bir görüşü güçlü yapan gerekçe nedir?","Öğrenilen ilke günlük hayatta hangi durumda sınanabilir?"], f"İki öğrenci {topic} hakkında farklı gerekçelere dayanan iki görüş savunuyor."

def cat(o,t):
    x=(o+" "+t).lower()
    if "yahudilik" in x or "hristiyanlık" in x:return "religions"
    if "kur’an’dan mesajlar" in x or "suresi" in (o or "").lower():return "text"
    if "karşılaştır" in x or "mukayese" in x:return "compare"
    if "sorgula" in x or "eleştirel düşün" in x or "akıl yürü" in x:return "critical"
    if "sınıflandır" in x or "sentez" in x or "yapılandır" in x:return "structure"
    if "özet" in x:return "summary"
    if "bilgi topla" in x or "başvur" in x:return "research"
    if "çözümle" in x or "tahlil" in x:return "analysis"
    return "interpret"

def B(title,m,text,q=[]):return {"title":title,"minutes":m,"text":text,"questions":q}
def P(title,style,blocks):return {"title":title,"style":style,"blocks":blocks}

def primary(kind,topic,outcome,qs,sc):
    if kind=="text":
        return P("Metin Üzerinden Düşünme","metin",[B("Metni İncele",10,f"MEB/TYMM materyalindeki {topic} bölümünü bireysel okutun. Tekrar eden kavramlar ve ana ifadeler işaretlensin.",[qs[0]]),B("Mesajı Gerekçelendir",15,"3–4 kişilik gruplar iki ana mesaj çıkarsın ve her mesaj için metinden dayanak göstersin.",[qs[1],qs[2]]),B("İki Cümlelik Sonuç",5,"Her öğrenci metnin ana fikrini en fazla iki cümleyle yazsın.")])
    if kind=="compare":
        return P("Karşılaştırma Masası","karsilastirma",[B("Ölçütleri Kur",10,f"{topic} için 3–4 karşılaştırma ölçütünü öğrenciler oluştursun.",[qs[0]]),B("Kanıtlı Karşılaştır",15,"Gruplar benzerlik ve farklılıkları MEB/TYMM materyalinden dayanak göstererek doldursun.",[qs[1],qs[2]]),B("En Belirgin Ayrım",5,"Her grup en önemli benzerliği ve farkı birer cümleyle açıklasın.")])
    if kind=="critical":
        return P("İddia – Gerekçe – Karşı Soru","sorgu",[B("İlk Görüş",5,f"Tahtaya şu soruyu yazın: “{qs[0]}” Öğrenciler ilk cevaplarını ve tek cümle gerekçelerini yazsın."),B("Soru Zinciri",20,"Gruplar görüşlerini en az iki gerekçeyle savunsun. Diğer grup yalnız karşı soru sorsun; öğretmen hazır cevap vermeden “Neden?” ve “Karşı örnek var mı?” sorularını kullansın.",[qs[1],qs[2]]),B("Görüş Güncelle",5,"Öğrenci ilk fikrini korur veya değiştirir; etkili gerekçeyi tek cümleyle yazar.")])
    if kind=="structure":
        return P("Kavramları Yapılandır","kavram",[B("Kavram Kartları",10,f"{topic} içindeki ana kavramları kartlara yazıp anlamca gruplandırın.",[qs[0]]),B("İlişki Ağı",15,"Gruplar kavramlar arasına oklar çizip ilişkiyi bir fiille açıklasın: etkiler, gerektirir, farklılaşır gibi.",[qs[1],qs[2]]),B("Ağı Test Et",5,"En merkezi kavramı seçip nedenini açıklasınlar.")])
    if kind=="summary":
        return P("Editör Masası","ozet",[B("Ana–Yan Bilgi",10,f"{topic} bölümünde ana fikir, destekleyici bilgi ve örnekleri ayırın.",[qs[0]]),B("50 Kelimelik Özet",15,"Öğrenciler en fazla 50 kelimelik özet yazıp akranıyla gereksiz ayrıntıları ayıklasın.",[qs[1]]),B("Başlık Koy",5,"Özete tek başlık koyup neden ana fikri yansıttığını açıklasın.",[qs[2]])])
    if kind=="religions":
        return P("Kaynak – Zaman – Özellik","dinler",[B("Bilgiyi Ayır",10,f"MEB/TYMM materyalindeki {topic} bölümünde tarihî bilgi, temel kavram ve özellikleri üç başlıkta işaretletin.",[qs[0]]),B("Zaman ve Kavram Haritası",15,"Gruplar küçük bir zaman çizgisi ve yanında temel kavram kartları oluştursun.",[qs[1]]),B("Genellemeyi Kontrol Et",5,"Bir cümleyi aşırı genelleme içerip içermediği açısından düzeltin.",[qs[2]])])
    if kind in ("analysis","research"):
        return P("Kaynak ve İlişki Atölyesi","kaynak",[B("Soruyu Kur",10,f"{topic} için üç araştırma/çözümleme sorusu üretin.",[qs[0]]),B("Kaynakla Cevapla",15,"Gruplar yalnız MEB/TYMM materyali ve doğrulanmış temel kaynaklardan cevap bulup dayanağı not etsin.",[qs[1],qs[2]]),B("Bulgu–Yorum",5,"Bir bulguyu ve bu bulgudan yapılan yorumu ayrı cümlelerle söylesinler.")])
    return P("Örnek Olay Laboratuvarı","vaka",[B("Durumu Oku",5,sc+" Öğrenciler önce bireysel olarak asıl sorunu yazsın.",[qs[0]]),B("Vakayı İncele",20,"3–4 kişilik gruplar seçenekleri, gerekçeleri ve muhtemel sonuçları konuşsun. Öğretmen karşı örnek ve “neden?” sorularıyla derinleştirsin.",[qs[1],qs[2]]),B("İlke Cümlesi",5,"Her grup tartışmadan çıkardığı ilkeyi tek cümleyle yazsın.")])

def alt(topic,qs,sc,n,avoid):
    styles=["yazma","grup","dort","soru","esles","karar"]
    st=styles[n%len(styles)]
    if st==avoid: st=styles[(n+1)%len(styles)]
    if st=="yazma":return P("Yaz – Değiştir – Yeniden Yaz",st,[B("Bireysel Metin",10,f"“{qs[0]}” sorusuna 5–7 cümlelik gerekçeli görüş yazılsın."),B("Akran Editörlüğü",10,"Metinler değiştirilsin; en güçlü gerekçe ve açıklanması gereken cümle işaretlensin.",[qs[1]]),B("Yeniden Yaz",10,"Öğrenci bir karşı görüş cümlesi ekleyip kendi sonucunu geliştirsin.",[qs[2]])])
    if st=="grup":return P("Uzman Grupları",st,[B("Görev Grupları",10,f"{topic} için grupları ana kavram, kaynak/dayanak, günlük hayat örneği ve yanlış anlaşılabilecek nokta görevlerine ayırın."),B("Karma Gruplar",10,"Her ilk gruptan bir öğrenci yeni gruba geçip kendi sonucunu iki dakikada anlatsın."),B("Ortak Çerçeve",10,"Karma grup parçaları birleştirip uzlaşılan ve tartışmalı noktaları yazsın.",qs[1:])])
    if st=="dort":return P("Dört Köşe",st,[B("Konum Seç",5,f"Tahtaya “{qs[0]}” sorusunu yazın. Cevap eğilimlerine göre dört köşe oluşturun."),B("Gerekçe ve İtiraz",20,"Her köşe üç gerekçe hazırlayıp başka bir köşeden gelen bir itiraza cevap versin.",qs[1:]),B("Değişen Fikir",5,"Öğrenci gerekçelerden sonra isterse köşe değiştirip nedenini söylesin.")])
    if st=="soru":return P("Soru Atölyesi",st,[B("Soru Üret",10,f"{topic} hakkında neden, nasıl, hangi durumda, neye göre kalıplarıyla üç düşünme sorusu üretin."),B("Soruyu Süz",10,"Soruları hemen cevaplanabilir / kaynak gerekir / tartışma gerekir diye ayırıp en güçlü bir soruyu seçin.",[qs[0],qs[1]]),B("Gerekçeyi Kur",10,"İki olası cevabın gerekçelerini ve hangi bilginin gerekli olduğunu açıklayın.",[qs[2]])])
    if st=="esles":return P("Düşün – Eşleş – Paylaş",st,[B("Bireysel Düşün",10,f"“{qs[0]}” sorusuna herkes tek başına cevap ve gerekçe yazsın."),B("Eşleş",10,"İkililer ortak ve ayrışan birer noktayı belirleyip ayrışmanın hangi varsayımdan kaynaklandığını bulsun.",[qs[1]]),B("Paylaş",10,"Her ikili yalnız en dikkat çekici ayrışmayı sınıfa taşısın.",[qs[2]])])
    return P("Karar Veren Grup",st,[B("Vaka",5,sc+" Öğrenciler ilk değerlendirmelerini tek başına yazsın.",[qs[0]]),B("Karar Kurulu",20,"Dörtlü grupta biri gerekçeleri, biri itirazları, biri sonuçları, biri kavramları kaydetsin.",qs[1:]),B("Azınlık Görüşü",5,"Grup kararına katılmayan varsa görüşünü tek cümleyle açıklasın.")])

d=json.loads(J.read_text(encoding="utf-8"))
count=0
for g in d["grades"]:
    if g["grade"] not in (9,10,11):continue
    for c in g["courses"]:
        if c.get("subjectId")!="dkab":continue
        seen={}
        for p in c["pages"]:
            if p.get("type")!="week" or p.get("special") or not p.get("outcome"):continue
            o=p["outcome"]; phase=seen.get(o,0);seen[o]=phase+1
            qs,sc=pick(p.get("topic","")); kind=cat(o,p.get("topic",""))
            a=primary(kind,p.get("topic",""),o,qs,sc) if phase==0 else alt(p.get("topic",""),qs,sc,p["weekNumber"]+phase*7,"")
            b=alt(p.get("topic",""),qs,sc,p["weekNumber"]+phase*7+2,a["style"])
            p["hints"]={"sourceOutcomeCode":code(o),"totalMinutes":30,"plans":[a,b],"verificationStatus":"validated-structure"}
            count+=1

assert count==102,count
for g in d["grades"]:
  for c in g["courses"]:
    for p in c["pages"]:
      h=p.get("hints")
      if not h:continue
      assert c.get("subjectId")=="dkab" and g["grade"] in (9,10,11)
      assert len(h["plans"])==2 and h["plans"][0]["style"]!=h["plans"][1]["style"]
      for pl in h["plans"]:
        assert len(pl["blocks"])==3
        assert sum(x["minutes"] for x in pl["blocks"])==30
        assert min(x["minutes"] for x in pl["blocks"])>=5

d["schemaVersion"]=6
d["generatedFor"]="Kazanım v6"
d.setdefault("sources",[]).append("DKAB 9–11 İpucu: MEB/TYMM haftalık konu ve öğrenme çıktısına bağlı özgün 30 dakikalık ders işleniş önerileri.")
J.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding="utf-8")
print("v6 hints",count)
