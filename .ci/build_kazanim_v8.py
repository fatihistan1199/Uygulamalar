# -*- coding: utf-8 -*-
#!/usr/bin/env python3
import copy, io, json, re, shutil, sys, unicodedata, urllib.request, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

PROJECT = Path(sys.argv[1]).resolve()
JSON_PATH = PROJECT / "app/src/main/assets/curriculum_2026_2027.json"
MAIN_PATH = PROJECT / "app/src/main/java/com/kazanim/app/MainActivity.java"
GRADLE_PATH = PROJECT / "app/build.gradle.kts"

FEN_URL = "https://tymm.meb.gov.tr/assets/file/fen-bilimleri-dersi-taslak-yillik-planlar_20260903_122025_920.zip"
SOS_URL = "https://tymm.meb.gov.tr/assets/file/sosyal-bilgiler-dersi-yillik-planlar_20260903_120026_880.zip"
ITA_URL = "https://tymm.meb.gov.tr/assets/file/tc-inkilap-tarihi-ve-ataturkculuk-dersi-taslak-yillik-planlar_20260903_120043_922.zip"
NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
RNS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

def clean(v):
    if v is None:
        return ""
    s = unicodedata.normalize("NFC", str(v)).replace("\u00a0", " ").replace("\r", "\n")
    return re.sub(r"\s+", " ", s).strip()

def download(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

def col_num(s):
    n = 0
    for ch in s:
        n = n * 26 + ord(ch) - 64
    return n

def split_ref(ref):
    m = re.match(r"([A-Z]+)(\d+)", ref)
    if not m:
        raise ValueError(ref)
    return int(m.group(2)), col_num(m.group(1))

def xlsx_sheets(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        root = ET.fromstring(z.read("xl/sharedStrings.xml"))
        shared = ["".join(t.text or "" for t in si.iter(f"{{{NS}}}t"))
                  for si in root.findall(f"{{{NS}}}si")]
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    relroot = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    rel = {r.attrib["Id"]: r.attrib["Target"] for r in relroot}
    out = {}
    for sh in wb.find(f"{{{NS}}}sheets"):
        name = sh.attrib["name"]
        rid = sh.attrib[f"{{{RNS}}}id"]
        target = rel[rid]
        path = target.lstrip("/") if target.startswith("/") else "xl/" + target.lstrip("/")
        root = ET.fromstring(z.read(path))
        vals = {}
        max_row = 0
        for c in root.iter(f"{{{NS}}}c"):
            ref = c.attrib.get("r")
            if not ref:
                continue
            rc = split_ref(ref)
            typ = c.attrib.get("t")
            v = c.find(f"{{{NS}}}v")
            ins = c.find(f"{{{NS}}}is")
            value = ""
            if typ == "s" and v is not None:
                value = shared[int(v.text)]
            elif typ == "inlineStr" and ins is not None:
                value = "".join(t.text or "" for t in ins.iter(f"{{{NS}}}t"))
            elif v is not None:
                value = v.text or ""
            vals[rc] = clean(value)
            max_row = max(max_row, rc[0])
        merges = root.find(f"{{{NS}}}mergeCells")
        if merges is not None:
            for mc in merges:
                ref = mc.attrib["ref"]
                if ":" not in ref:
                    continue
                a, b = ref.split(":")
                r1, c1 = split_ref(a)
                r2, c2 = split_ref(b)
                base = vals.get((r1, c1), "")
                if not base:
                    continue
                for rr in range(r1, r2 + 1):
                    for cc in range(c1, c2 + 1):
                        vals.setdefault((rr, cc), base)
        out[name] = (vals, max_row)
    return out

def archive_workbook(url):
    arch = zipfile.ZipFile(io.BytesIO(download(url)))
    names = [n for n in arch.namelist() if n.lower().endswith(".xlsx")]
    if not names:
        raise RuntimeError("Arşivde XLSX yok: " + url)
    return xlsx_sheets(arch.read(names[0]))

def find_sheet(sheets, grade, kind):
    if kind == "fen":
        patterns = [f"FEN BİLİMLERİ {grade}"]
    elif kind == "sos":
        patterns = [f"{grade}. SINIF YILLIK PLAN"]
    else:
        patterns = ["8. SINIF YILLIK PLAN"]
    for name, value in sheets.items():
        if all(p.upper() in name.upper() for p in patterns):
            return value
    raise RuntimeError(f"Sayfa bulunamadı: {kind} {grade}; {list(sheets)}")

def week_number(text):
    m = re.search(r"(\d+)\s*\.\s*Hafta", clean(text), re.I)
    return int(m.group(1)) if m else None

ITA_TOPICS = {
    "İTA.8.1.1": "XX. Yüzyıl Başlarında Osmanlı Devleti",
    "İTA.8.1.2": "Mustafa Kemal’in Çocukluk ve Öğrenim Hayatı",
    "İTA.8.1.3": "Mustafa Kemal’in Fikir Hayatını Etkileyen Kişi ve Olaylar",
    "İTA.8.1.4": "Mustafa Kemal’in Askerlik Hayatı",
    "İTA.8.2.1": "Birinci Dünya Savaşı’nın Sebepleri ve Başlaması",
    "İTA.8.2.2": "Birinci Dünya Savaşı’nda Osmanlı Devleti",
    "İTA.8.2.3": "Mondros Ateşkes Antlaşması ve Tepkiler",
    "İTA.8.2.4": "Kuvâ-yı Millîye ve Cemiyetler",
    "İTA.8.2.5": "Millî Mücadele’nin Hazırlık Dönemi",
    "İTA.8.2.6": "Misakımillî ve Büyük Millet Meclisinin Açılması",
    "İTA.8.2.7": "BMM’ye Karşı Ayaklanmalar ve Alınan Tedbirler",
    "İTA.8.2.8": "Sevr Antlaşması’na Tepkiler",
    "İTA.8.3.1": "Doğu ve Güney Cepheleri",
    "İTA.8.3.2": "Batı Cephesi",
    "İTA.8.3.3": "Maarif Kongresi",
    "İTA.8.3.4": "Tekâlif-i Millîye Emirleri",
    "İTA.8.3.5": "Sakarya Meydan Savaşı ve Büyük Taarruz",
    "İTA.8.3.6": "Lozan Antlaşması",
    "İTA.8.3.7": "Millî Mücadele’nin Sanat ve Edebiyata Yansımaları",
    "İTA.8.4.1": "Atatürk İlkeleri",
    "İTA.8.4.2": "Siyasi Alandaki İnkılaplar",
    "İTA.8.4.3": "Hukuk Alanındaki İnkılaplar",
    "İTA.8.4.4": "Eğitim ve Kültür Alanındaki İnkılaplar",
    "İTA.8.4.5": "Toplumsal Alandaki İnkılaplar",
    "İTA.8.4.6": "Ekonomi Alanındaki Gelişmeler",
    "İTA.8.4.7": "Sağlık Alanındaki Çalışmalar",
    "İTA.8.4.8": "Cumhuriyet’in Kazanımları ve Atatürk’ün Hedefleri",
    "İTA.8.4.9": "Atatürk İlke ve İnkılaplarının Temel Esasları",
    "İTA.8.5.1": "Demokratikleşme Yolunda Atılan Adımlar",
    "İTA.8.5.2": "Mustafa Kemal’e Suikast Girişimi",
    "İTA.8.5.3": "Cumhuriyetin İlk Yıllarında Tehditler",
    "İTA.8.6.1": "Atatürk Dönemi Türk Dış Politikasının İlkeleri",
    "İTA.8.6.2": "Atatürk Dönemi Dış Politika Gelişmeleri",
    "İTA.8.6.3": "Hatay’ın Anavatana Katılması",
    "İTA.8.7.1": "Atatürk’ün Ölümünün Yansımaları",
    "İTA.8.7.2": "Atatürk’ün Eserleri",
    "İTA.8.7.3": "İkinci Dünya Savaşı Öncesi Atatürk’ün Tespitleri",
    "İTA.8.7.4": "İkinci Dünya Savaşı’nın Türkiye’ye Etkileri",
    "İTA.8.7.5": "Çok Partili Siyasi Hayata Geçiş",
}

def ita_topic(outcome):
    codes = re.findall(r"İTA\.8\.\d+\.\d+", outcome, flags=re.I)
    titles = []
    for c in codes:
        key = "İTA" + c[3:]
        title = ITA_TOPICS.get(key)
        if title and title not in titles:
            titles.append(title)
    if titles:
        return " / ".join(titles)
    s = re.sub(r"^İTA\.8\.\d+\.\d+\.?", "", outcome, flags=re.I).strip()
    return s[:110] if s else "T.C. İnkılap Tarihi ve Atatürkçülük"

def parse_records(sheet, kind, grade):
    vals, max_row = sheet
    data = {}
    for r in range(1, max_row + 1):
        w = week_number(vals.get((r, 2), ""))
        if not w or not 1 <= w <= 37:
            continue
        rowtxt = " ".join(vals.get((r, c), "") for c in range(1, 16)).upper()
        if kind == "ita":
            unit = clean(vals.get((r, 4)))
            outcome = clean(vals.get((r, 5)))
            process = clean(vals.get((r, 6)))
            topic = ita_topic(outcome) if outcome else ""
            evidence = clean(vals.get((r, 7)))
        else:
            unit = clean(vals.get((r, 4)))
            topic = clean(vals.get((r, 5)))
            outcome = clean(vals.get((r, 6)))
            process = clean(vals.get((r, 7)))
            evidence = clean(vals.get((r, 8)))
        special = None
        if "SOSYAL ETKİNLİK" in rowtxt and not outcome:
            special = "Sosyal Etkinlik"
        elif "OKUL TEMELLİ PLANLAMA" in rowtxt and not outcome:
            special = "Okul Temelli Planlama"
        elif "LABORATUVAR GÜVENLİĞİ" in rowtxt and not outcome:
            special = "Laboratuvar Güvenliği"
        elif "YIL SONU BİLİM ŞENLİĞİ" in rowtxt and not outcome:
            special = "Yıl Sonu Bilim Şenliği"
        elif not outcome:
            visible = topic or unit
            special = visible if visible else "Planlama / Etkinlik"
        data[w] = {
            "unit": unit,
            "topic": topic or (special or ""),
            "outcome": outcome,
            "process": process,
            "evidence": evidence,
            "special": special,
        }
    if set(data) != set(range(1, 38)):
        missing = sorted(set(range(1, 38)) - set(data))
        raise RuntimeError(f"{kind}-{grade} haftaları eksik: {missing}")
    return data

def short_topic(topic):
    s = clean(topic)
    return s if len(s) <= 88 else s[:85].rstrip() + "…"

def block(title, minutes, text, questions=None):
    return {"title": title, "minutes": minutes, "text": text, "questions": questions or []}

def plan(title, style, blocks):
    return {"title": title, "style": style, "blocks": blocks}

def followup_plans(domain, topic, phase):
    if phase % 2:
        a = plan("Hata Avı ve Kanıt", f"{domain}-hata-avı", [
            block("Hatalı Örnek", 10, f"{topic} konusunda doğru görünen fakat bir kavramı yanlış kullanan iki örnek hazırlayın. Öğrenciler hatayı bulup hangi bilgiyle düzelttiklerini yazsın."),
            block("Kanıtla Savun", 15, "İkililer bir düzeltmeyi seçip ders kitabı, öğretmen tarafından verilen veri/görsel veya resmî materyaldeki dayanağıyla savunsun. Diğer ikili yalnız karşı soru sorsun.", ["Düzeltmeyi hangi kanıt güçlü kılıyor?", "Başka bir açıklama mümkün mü?"]),
            block("Tek Cümlelik İlke", 5, "Her öğrenci yanlış anlamayı önleyen bir ilke cümlesi yazar.")
        ])
        b = plan("Akran İstasyonları", f"{domain}-istasyon", [
            block("İstasyonları Kur", 10, f"{topic} için üç istasyon oluşturun: kavram, örnek/kanıt ve uygulama. Gruplar ilk istasyonda kendi cevabını bırakır."),
            block("Dön ve Geliştir", 10, "Gruplar istasyon değiştirip önceki grubun cevabına bir doğrulama, düzeltme veya yeni kanıt ekler."),
            block("Sınıf Sentezi", 10, "Son istasyondaki ürünler karşılaştırılır; sınıf en açıklayıcı örnek ve gerekçeyi seçer.")
        ])
    else:
        a = plan("Örnekleri Yeniden Yorumla", f"{domain}-yeniden-yorum", [
            block("İki Örnek", 10, f"{topic} ile ilgili iki farklı örnek/veri/görsel verin. Öğrenciler önce benzer ve farklı yönleri ayırır."),
            block("Yeni Soru", 15, "Gruplar örneklerden doğan yeni bir soru üretip mevcut bilgilerle hangi sonuca ulaşılabileceğini, hangi sonucun ise ek kanıt gerektirdiğini belirler.", ["Hangi sonuç doğrudan kanıta dayanıyor?", "Neyi henüz bilmiyoruz?"]),
            block("Sınırını Yaz", 5, "Öğrenci 'Bu verilerden çıkarabilirim / çıkaramam' biçiminde iki kısa cümle yazar.")
        ])
        b = plan("Mini Ders Tasarımı", f"{domain}-akran-ogretimi", [
            block("Öğretilecek Fikir", 10, f"Üçlü gruplar {topic} konusunda bir arkadaşına öğretilecek en kritik fikri ve en sık karıştırılabilecek noktayı seçer."),
            block("3 Dakikalık Akran Öğretimi", 10, "Gruplar karşılıklı mini anlatım yapar; dinleyen grup yalnız bir açıklama sorusu ve bir kontrol sorusu sorar."),
            block("Anlamayı Sına", 10, "Her grup başka grubun hazırladığı kısa problem/örneği çözer; yanlış cevap varsa kavramın hangi kısmının anlaşılmadığını belirler.")
        ])
    return a, b

def fen_plans(topic, outcome, process, evidence, phase):
    t = (topic + " " + outcome + " " + process).lower()
    if phase > 0:
        return followup_plans("fen", topic, phase)
    safe = "Deney gerekiyorsa yalnız MEB materyalindeki veya öğretmenin güvenli bulduğu sınıf düzeyine uygun basit düzenek kullanılsın; sonuç önceden verilmesin."
    if "model oluştur" in t or "model tasarla" in t or "model üzerinde" in t:
        a = plan("Model Kur – Sına – Yenile", "fen-model", [
            block("Model Taslağı", 10, f"Öğrenciler {topic} için hangi parçaların ve ilişkilerin modelde mutlaka bulunması gerektiğini belirleyip hızlı bir taslak oluşturur."),
            block("Modeli Sına", 15, "Gruplar modellerini ders kitabındaki veri/görsel veya öğretmenin verdiği ölçütlerle karşılaştırır. Modelin açıklamadığı bir durumu bulup bir düzeltme yapar.", ["Model gerçeğin hangi yönünü temsil ediyor?", "Modelin sınırı nedir?"]),
            block("Yenilenen Model", 5, "Her grup yaptığı tek önemli değişikliği ve gerekçesini açıklar.")
        ])
        b = plan("Model Dedektifi", "fen-model-elestiri", [
            block("İki Model", 10, f"{topic} için biri eksik/yanlış, biri daha güçlü iki şema ya da öğrenci modeli gösterin. Öğrenciler farkları işaretlesin."),
            block("Kanıtla Eleştir", 10, "İkililer hangi modelin daha açıklayıcı olduğunu ölçüt ve kanıtla savunur; yalnız görsel olarak güzel olmayı ölçüt saymaz."),
            block("Kendi Ölçütün", 10, "Öğrenciler iyi bir bilimsel model için üç ölçüt yazıp kendi ilk taslaklarını bu ölçütlerle puanlar.")
        ])
    elif any(k in t for k in ["deney yap", "deneyerek", "gözlem", "hipotez", "tahmin edebilme", "tahminlerini test"]):
        a = plan("Tahmin – Test – Kanıt", "fen-deney", [
            block("Tahmin ve Değişken", 10, f"{topic} için araştırılabilir bir soru belirleyin. Öğrenci ne olacağını tahmin etsin ve değiştirilecek/ölçülecek değişkenleri ayırsın. {safe}"),
            block("Gözlem / Test", 15, "Öğrenciler güvenli düzeneği uygular veya öğretmenin sağladığı deney verisini inceler. Gözlenen/ölçülen sonucu tahminden ayrı kaydeder.", ["Hangi gözlem tahminini destekliyor?", "Kontrol edilmesi gereken değişken hangisi?"]),
            block("Kanıta Dayalı Sonuç", 5, "Sonuç 'Verimiz … gösterdiği için … sonucuna ulaşıyorum.' biçiminde yazılır.")
        ])
        b = plan("Değişken Dedektifi", "fen-degisken", [
            block("Düzeneği Oku", 10, f"{topic} ile ilgili bir deney şeması veya veri tablosu verin. Öğrenciler bağımsız, bağımlı ve kontrol edilen değişkenleri belirlesin. {safe}"),
            block("Adil Test mi?", 10, "Gruplar düzeneğin adil test olup olmadığını tartışıp tek bir iyileştirme önerir."),
            block("Sonuç mu, Yorum mu?", 10, "Öğrenciler üç cümleyi 'gözlem/veri', 'çıkarım' ve 'kanıtsız iddia' olarak sınıflandırır.")
        ])
    elif "sınıflandır" in t:
        a = plan("Özellik Kartlarıyla Sınıflandır", "fen-siniflandirma", [
            block("Ölçütü Belirle", 10, f"{topic} ile ilgili örnek kartları dağıtın. Öğrenciler önce hangi gözlenebilir/öğrenilmiş özelliklerin sınıflandırma ölçütü olabileceğini çıkarır."),
            block("Grupla ve Etiketle", 15, "Gruplar kartları kendi ölçütleriyle ayırır, her gruba isim verir ve sınırda kalan bir örneği tartışır.", ["Aynı örnek farklı ölçütle başka gruba girebilir mi?", "Sınıflandırmayı hangi özellik belirledi?"]),
            block("Ölçüt Kontrolü", 5, "Her grup sınıflandırmasının tutarlı olup olmadığını tek bir yeni örnekle test eder.")
        ])
        b = plan("Sınır Örnekleri", "fen-sinir-ornek", [
            block("Kolay Örnekler", 5, f"{topic} için açık biçimde farklı gruplara ait birkaç örnekle hızlı yerleştirme yapın."),
            block("Zor Örnekler", 20, "Öğrencilere ilk bakışta kararsız bırakacak örnekler verin. Hangi özelliğin belirleyici olduğunu kaynak/veriyle savunsunlar.", ["Bir örneği gruba sokmak için kaç özellik gerekir?", "Etiket mi, ölçüt mü daha önemlidir?"]),
            block("Kuralı Yaz", 5, "Her grup kullandığı sınıflandırma kuralını bir cümlede açıklar.")
        ])
    elif "karşılaştır" in t:
        a = plan("Benzerlik – Farklılık Laboratuvarı", "fen-karsilastirma", [
            block("Özellik Havuzu", 10, f"{topic} için karşılaştırılacak kavram/nesnelerin özelliklerini karışık kartlar hâlinde verin."),
            block("Kanıtlı Karşılaştır", 15, "Gruplar özellikleri benzerlik ve farklılık olarak düzenler; her yerleştirmeyi ders materyalindeki bilgiyle kontrol eder.", ["En ayırt edici özellik hangisi?", "Yüzeysel benzerlik yanıltıcı olabilir mi?"]),
            block("Bir Cümlede Fark", 5, "Öğrenci iki kavramı karıştırmayı önleyecek tek cümlelik fark yazar.")
        ])
        b = plan("Yanlış Eşleştirmeyi Düzelt", "fen-eslestirme", [
            block("Hatalı Tablo", 10, f"{topic} konusunda bazı özellikleri bilerek yanlış sütuna yerleştirilmiş bir tablo verin."),
            block("Düzelt ve Gerekçelendir", 10, "İkililer yanlışları düzeltip her düzeltmenin gerekçesini yazar."),
            block("Yeni Kontrol Sorusu", 10, "Her ikili başka ikilinin cevaplayacağı, iki kavramı gerçekten ayırt etmeyi gerektiren bir soru üretir.")
        ])
    elif any(k in t for k in ["bilgi topla", "araştır", "sorgulayabilme"]):
        a = plan("Kaynak Avı", "fen-kaynak", [
            block("Soruyu Daralt", 10, f"{topic} hakkında araştırılabilir bir soru üretin; 'her şeyi anlat' türü geniş soruları daraltın."),
            block("Bul – Doğrula – Kaydet", 15, "MEB ders materyali ve öğretmenin seçtiği güvenilir kaynaklardan iki bilgi bulun. Kaynak, bulgu ve doğrulama notu ayrı yazılsın.", ["İki kaynak çelişirse ne yaparsın?", "Bilgi ile yorum nasıl ayrılır?"]),
            block("30 Saniyelik Rapor", 5, "Her grup yalnız doğrulayabildiği bir bulguyu ve kaynağını kısa biçimde sunar.")
        ])
        b = plan("Bilimsel Haber Editörü", "fen-haber", [
            block("İddia Kartı", 10, f"{topic} hakkında kaynağı belirtilmiş/belirtilmemiş kısa iddiaları karşılaştırın. Öğrenciler hangi ek bilgiyi arayacağını yazsın."),
            block("Editör Kurulu", 10, "Gruplar iddiaları 'yayınlanabilir', 'kaynak gerekir', 'yanlış/yanıltıcı' olarak ayırır ve ölçütünü açıklar."),
            block("Düzeltme Metni", 10, "En zayıf iddia, kanıta dayalı ve ölçülü bir bilimsel cümleye dönüştürülür.")
        ])
    elif any(k in t for k in ["problem çöz", "çözebilme", "tasarla", "öneri sun"]):
        a = plan("Mühendislik Problemi", "fen-problem", [
            block("İhtiyacı Tanımla", 10, f"{topic} bağlamında günlük hayattan küçük bir problem verin. Öğrenciler kullanıcı ihtiyacı, kısıt ve başarı ölçütünü ayırsın."),
            block("Üç Çözüm", 10, "Gruplar üç çözüm üretip etki, uygulanabilirlik ve güvenlik ölçütleriyle karşılaştırır."),
            block("Seç ve Geliştir", 10, "En uygun çözüm seçilir; bir zayıf yönü bulunup tasarım/öneri geliştirilir.")
        ])
        b = plan("Çözüm Matrisi", "fen-karar-matrisi", [
            block("Ölçütleri Kur", 10, f"{topic} problemi için maliyet, etki, güvenlik ve sürdürülebilirlik gibi uygun ölçütleri öğrenciler belirlesin."),
            block("Seçenekleri Puanla", 15, "Gruplar seçenekleri aynı ölçütlerle karşılaştırır; puanların gerekçesini veri veya bilimsel ilkeyle açıklar."),
            block("Karar Notu", 5, "Grup 'şu çözümü seçiyoruz, çünkü…; ancak şu sınırlaması var…' biçiminde sonuç yazar.")
        ])
    elif any(k in t for k in ["çıkarım", "akıl yürüt", "kanıt kullan"]):
        a = plan("Veri – Kanıt – Çıkarım", "fen-cikarim", [
            block("Veriyi Ayır", 10, f"{topic} ile ilgili kısa veri, gözlem veya görsel seti verin. Öğrenciler yalnız görülen/verilen bilgileri yazar."),
            block("Çıkarımı Kur", 15, "Gruplar her çıkarımın yanına onu destekleyen veriyi koyar; veriden çıkmayan aşırı genellemeleri çıkarır.", ["Hangi çıkarım en güçlü kanıta dayanıyor?", "Aynı verinin başka açıklaması olabilir mi?"]),
            block("Kanıt Cümlesi", 5, "Öğrenci '… verisi nedeniyle … sonucuna ulaşıyorum.' cümlesini tamamlar.")
        ])
        b = plan("Karşı Örnekle Sına", "fen-karsi-ornek", [
            block("İlk Genelleme", 10, f"{topic} hakkında örneklerden hareketle bir genelleme önerin."),
            block("Karşı Örnek", 10, "Gruplar genellemeyi sınayan gerçekçi bir karşı örnek/veri arar veya öğretmenin verdiği karşı örneği yorumlar."),
            block("Daha Güçlü Genelleme", 10, "İlk cümle, karşı örneği de kapsayacak biçimde daha ölçülü ve bilimsel hâle getirilir.")
        ])
    else:
        a = plan("Kavram Ağı", "fen-kavram", [
            block("Ana Kavramlar", 10, f"{topic} için ders materyalinden 5-7 temel kavram seçin."),
            block("İlişkiyi Kur", 15, "Gruplar kavramları oklarla bağlayıp her okun üzerine ilişkiyi açıklayan fiil yazar: etkiler, oluşur, dönüşür, bağlıdır, içerir gibi.", ["En merkezi kavram hangisi ve neden?"]),
            block("Ağı Test Et", 5, "Öğretmen yeni bir örnek verir; grup ağı kullanarak örneği açıklar.")
        ])
        b = plan("Örnek – Örnek Değil", "fen-ornek", [
            block("İlk Kartlar", 10, f"{topic} kavramına uygun olan ve olmayan örnekleri karışık verin."),
            block("Kuralı Keşfet", 10, "İkililer örnekleri ayırıp hangi özellikleri kullandığını açıklar."),
            block("Kendi Sınama Kartın", 10, "Öğrenci sınıf arkadaşını düşündürecek yeni bir örnek veya örnek olmayan durum üretir ve cevabını gerekçelendirir.")
        ])
    return a, b

def social_plans(topic, outcome, process, evidence, phase):
    t = (topic + " " + outcome + " " + process).lower()
    if phase > 0:
        return followup_plans("sosyal", topic, phase)
    if any(k in t for k in ["konum", "harita", "coğraf", "bölge", "dünya", "ülke"]):
        a = plan("Harita Dedektifleri", "sosyal-harita", [
            block("Haritayı Oku", 10, f"{topic} için MEB materyalindeki uygun harita/şema üzerinde başlık, yön, konum, sembol ve ölçek gibi gerekli unsurları inceleyin. Öğrenciler doğrudan görülen bilgiyi yazar."),
            block("İlişkiyi Bul", 15, "Gruplar haritadaki iki-üç unsur arasında mekânsal ilişki kurar; 'nerede?' sorusundan 'neden burada / neyle ilişkili?' sorusuna geçer.", ["Harita hangi bilgiyi gösteriyor, hangisini göstermiyor?", "Göreceli konum hangi referansa göre değişiyor?"]),
            block("Kanıtlı Cümle", 5, "Her grup haritadan kanıt gösteren tek bir çıkarım cümlesi kurar.")
        ])
        b = plan("Mekânsal Karar", "sosyal-mekansal-karar", [
            block("Yer Seçimi", 10, f"{topic} bağlamında iki farklı konum/yer seçeneği sunun. Öğrenciler karar için hangi coğrafi ölçütlere ihtiyaç duyduğunu belirlesin."),
            block("Karşılaştır ve Seç", 10, "İkililer seçenekleri aynı ölçütlerle karşılaştırır ve birini seçer."),
            block("Karşı İtiraz", 10, "Başka ikili seçime itiraz eder; ilk grup kararını harita/veriyle savunur veya değiştirir.")
        ])
    elif any(k in t for k in ["osmanlı", "türk devlet", "miras", "tarih", "geçmiş", "medeniyet", "yenilik"]):
        a = plan("Kaynak – Zaman – Neden", "sosyal-tarihsel-kanit", [
            block("Zamanı Yerleştir", 10, f"{topic} ile ilgili MEB materyalindeki olay/olgu kartlarını kronolojik veya dönemsel sıraya yerleştirin."),
            block("Kaynakla Açıkla", 15, "Gruplar bir görsel, kısa kaynak parçası, harita veya veri üzerinden 'ne biliyoruz?' ve 'bundan ne çıkarıyoruz?' sorularını ayrı cevaplar.", ["Olaydan önce hangi şartlar vardı?", "Bu gelişmenin tek bir nedeni olduğunu söylemek doğru mu?"]),
            block("Neden – Sonuç Oku", 5, "Her grup bir neden-sonuç bağlantısını okla gösterip gerekçesini yazar.")
        ])
        b = plan("Tarihsel Perspektif", "sosyal-perspektif", [
            block("Dönemin Şartları", 10, f"{topic} için öğrenciler bugünün bilgisini geçmişe taşımadan dönemin insanlarının bildiği şartları listeler."),
            block("Karar Anı", 15, "Kurgusal fakat tarihî olguları değiştirmeyen bir karar sorusu verin. Öğrenciler seçenekleri dönemin şartlarına göre değerlendirir; gerçek tarihî kişi ağzından uydurma söz yazmaz.", ["O dönemde hangi bilgi bilinmiyordu?", "Kararı etkileyen siyasi, ekonomik veya toplumsal unsur neydi?"]),
            block("Bugünden Farkı", 5, "Öğrenci 'Bugünden bakınca kolay görünen fakat o dönemde belirsiz olan…' cümlesini tamamlar.")
        ])
    elif any(k in t for k in ["demokrasi", "hak", "sorumluluk", "eşit", "yönetim", "vatandaş"]):
        a = plan("Vatandaşlık Vakası", "sosyal-vaka", [
            block("Durumu Oku", 5, f"{topic} ile ilgili okul/yerel yaşamdan gerçekçi bir karar veya hak-sorumluluk durumu verin. Öğrenciler ilk değerlendirmeyi bireysel yazar."),
            block("Hak – Sorumluluk Kurulu", 20, "Gruplar tarafları, hakları, sorumlulukları ve kararın muhtemel sonuçlarını ayırır. Kararı yalnız çoğunluk isteğine değil ilgili ilke ve kurallara göre gerekçelendirir.", ["Adil karar ile herkesin istediği karar aynı şey midir?", "Katılım kararın niteliğini nasıl etkiler?"]),
            block("Gerekçeli Karar", 5, "Grup iki cümlelik karar ve dayandığı ilkeyi yazar.")
        ])
        b = plan("Dört Köşe", "sosyal-dort-kose", [
            block("İddia", 5, f"{topic} hakkında tek doğru cevabı olmayan fakat kazanımla ilişkili bir iddia verin. Öğrenciler katılıyorum / büyük ölçüde / az / katılmıyorum köşelerinden birini seçer."),
            block("Gerekçe ve İtiraz", 20, "Her köşe iki gerekçe hazırlar ve başka köşenin bir sorusuna cevap verir. Öğrenciler görüş değiştirirse gerekçesini açıklar."),
            block("Ortak İlke", 5, "Farklı görüşlerin üzerinde uzlaşabildiği bir vatandaşlık/demokrasi ilkesi yazılır.")
        ])
    elif any(k in t for k in ["ekonomi", "üretim", "tüketim", "meslek", "pazarlama", "kaynak"]):
        a = plan("Ekonomi Zinciri", "sosyal-ekonomi", [
            block("Başlangıç Verisi", 10, f"{topic} için bir ürün, kaynak veya ekonomik faaliyet seçin. Öğrenciler üretim-dağıtım-tüketim aşamalarını ve ilgili meslekleri çıkarır."),
            block("Bir Halka Değişirse", 15, "Gruplar zincirin bir halkasında maliyet, ulaşım, talep veya kaynak değişikliği olduğunu varsayar; diğer halkalara etkisini neden-sonuçla açıklar.", ["Bir ekonomik karar kimleri farklı biçimde etkiler?", "Kaynak ile ekonomik faaliyet arasında nasıl bağ kurulur?"]),
            block("Öngörü", 5, "Her grup veriye dayalı bir 'eğer… ise…' öngörüsü yazar.")
        ])
        b = plan("Yatırım / Tercih Kurulu", "sosyal-karar-matrisi", [
            block("Ölçütleri Belirle", 10, f"{topic} bağlamında iki seçenek sunun. Öğrenciler maliyet, ihtiyaç, sürdürülebilirlik, yerel kaynak ve toplumsal etki gibi uygun ölçütleri belirler."),
            block("Seçenekleri Tart", 10, "Gruplar seçenekleri aynı ölçütlerle puanlar; puanların gerekçesini açıklar."),
            block("Karar Notu", 10, "Seçim, bir avantaj ve bir risk içeren kısa öneri metnine dönüştürülür.")
        ])
    elif any(k in t for k in ["teknoloji", "bilim", "gelecek", "iletişim"]):
        a = plan("Gelecek Senaryosu", "sosyal-gelecek", [
            block("Bugünkü Eğilim", 10, f"{topic} için bugün gözlenen bir bilimsel/teknolojik gelişmeyi ve toplumsal etkisini belirleyin."),
            block("2035 Senaryosu", 15, "Gruplar 'aynı eğilim sürerse' bir fırsat ve bir risk üretir. Öngörüyü hayal ile kanıta dayalı çıkarım olarak ayırır.", ["Öngörünün dayandığı bugünkü kanıt ne?", "Teknoloji herkesi aynı biçimde etkiler mi?"]),
            block("Koşullu Öngörü", 5, "Öğrenci 'Eğer … eğilimi sürerse … olabilir; çünkü …' cümlesini yazar.")
        ])
        b = plan("Sosyal Bilimci Masası", "sosyal-bilimci", [
            block("Soruyu Seç", 10, f"{topic} ile ilgili toplumsal bir soru belirleyin. Hangi sosyal bilim dalının bu soruya hangi veriyle yaklaşabileceğini tartışın."),
            block("Yöntemi Eşleştir", 10, "İkililer gözlem, görüşme, belge, istatistik veya harita gibi veri türlerini uygun sorularla eşleştirir."),
            block("Mini Araştırma Taslağı", 10, "Öğrenciler soru, veri kaynağı ve beklenen bulgu türünü üç maddede yazar.")
        ])
    elif any(k in t for k in ["sorgula", "çözümle", "yorumla", "çıkarım", "öngörü", "kanıta"]):
        a = plan("İddia – Kanıt – Gerekçe", "sosyal-ikg", [
            block("İddiayı Kur", 10, f"{topic} hakkında öğrenciler MEB materyalindeki veri/metin/görselden hareketle cevaplanabilir bir iddia yazar."),
            block("Kanıtı Tart", 15, "Gruplar iddiayı destekleyen kanıtı seçer, kanıtın iddiayla bağını açıklar ve bir karşı kanıt/alternatif açıklama olup olmadığını düşünür.", ["Kanıt iddiayı gerçekten destekliyor mu?", "Yorum ile bilgi nerede ayrılıyor?"]),
            block("Ölçülü Sonuç", 5, "İddia, kanıtın izin verdiği ölçüde yeniden yazılır; aşırı genelleme varsa çıkarılır.")
        ])
        b = plan("Örnek Olay Çemberi", "sosyal-ornek-olay", [
            block("Olayı Ayır", 10, f"{topic} ile ilişkili kısa bir toplumsal olay verin. Öğrenciler olgu, görüş, sebep ve sonuç ifadelerini ayırır."),
            block("Küçük Grup Sorgusu", 10, "Üçlü gruplar 'neden oldu, kim etkilendi, hangi kanıt gerekir, başka açıklama var mı?' sorularını tartışır."),
            block("Sınıf Sonucu", 10, "Her grup en güçlü çıkarımını ve bu çıkarımın dayanağını paylaşır.")
        ])
    else:
        a = plan("Kavram ve Örnek Atölyesi", "sosyal-kavram", [
            block("Kavramları Seç", 10, f"{topic} için 5 temel kavramı MEB materyalinden seçin ve öğrencilerin kendi cümleleriyle açıklamasını isteyin."),
            block("Örneklerle Bağla", 10, "Gruplar her kavrama gündelik, tarihsel veya mekânsal uygun bir örnek ekler; örneğin neden uygun olduğunu açıklar."),
            block("İlişki Ağı", 10, "Kavramlar arasındaki iki önemli ilişki oklarla gösterilir.")
        ])
        b = plan("Soru Atölyesi", "sosyal-soru", [
            block("Soru Üret", 10, f"{topic} hakkında 'neden, nasıl, ne değişirdi, hangi kanıt' kalıplarıyla üç soru üretin."),
            block("Soruyu Süz", 10, "Soruları bilgi sorusu, yorum sorusu ve araştırma sorusu olarak ayırın; en güçlü bir soruyu seçin."),
            block("Cevap İskeleti", 10, "Seçilen soruya cevap vermek için gereken bilgi ve kanıt türleri listelenir.")
        ])
    return a, b

def ita_plans(topic, outcome, process, phase):
    t = (topic + " " + outcome + " " + process).lower()
    if phase > 0:
        return followup_plans("inkilap", topic, phase)
    if any(k in t for k in ["savaş", "cephe", "millî mücadele", "kongre", "genelge", "antlaşma", "mondros", "sevr", "lozan"]):
        a = plan("Kronoloji – Harita – Neden", "inkilap-kronoloji", [
            block("Zaman Şeridi", 10, f"{topic} ile ilgili resmî yıllık plan/MEB ders materyalinde yer alan olay kartlarını kronolojik sıraya koyun. Aynı hafta birden çok olay varsa aralarındaki öncelik-sonralık ilişkisini işaretleyin."),
            block("Neden – Sonuç Ağı", 15, "Gruplar olaylardan birini seçip siyasi, askerî ve toplumsal sebepleri ayrı başlıklarda değerlendirir. Cephe/yer bilgisi varsa MEB materyalindeki harita kullanılır.", ["Hangi gelişme bir sonraki adımın şartlarını değiştirdi?", "Tek nedenli açıklama neden yetersiz kalabilir?"]),
            block("Kritik Dönüm Noktası", 5, "Her grup en kritik gördüğü dönüm noktasını, kanıt/gerekçesiyle bir cümlede açıklar.")
        ])
        b = plan("Tarihsel Karar Masası", "inkilap-karar", [
            block("Bilinenler – Bilinmeyenler", 10, f"{topic} için dönemin aktörlerinin o anda bilebileceği bilgileri ve henüz bilemeyeceği sonraki gelişmeleri ayırın."),
            block("Seçenekleri Tart", 15, "Öğrenciler dönemin gerçek şartlarına dayalı iki-üç seçenek üzerinde düşünür. Tarihî kişilere uydurma söz söyletmeden, seçeneği şartlar ve hedefler açısından değerlendirir.", ["Bugünün sonucunu bilmek kararımızı nasıl çarpıtabilir?", "O anda en büyük belirsizlik neydi?"]),
            block("Gerekçeli Tercih", 5, "Öğrenci seçtiği seçeneği dönemin koşullarından iki gerekçeyle savunur.")
        ])
    elif any(k in t for k in ["inkılap", "ilke", "çağdaş", "hukuk", "eğitim", "ekonomi", "sağlık", "toplumsal"]):
        a = plan("Değişim – Süreklilik Tablosu", "inkilap-degisim", [
            block("Önce / Sonra", 10, f"{topic} ile ilgili MEB materyalindeki bilgi ve belgelerden hareketle değişen kurum, kural veya uygulamaları 'önce-sonra' tablosuna yerleştirin."),
            block("Gerekçe ve Katkı", 15, "Gruplar her değişimin hangi ihtiyaca cevap verdiğini ve toplum/devlet yapısına beklenen katkısını neden-sonuçla açıklar.", ["Değişim yalnız kanun çıkarmakla tamamlanır mı?", "Hangi unsurda süreklilik de görülebilir?"]),
            block("İlkeyle Bağla", 5, "Uygunsa gelişme bir Atatürk ilkesiyle ilişkilendirilir; ilişki yalnız isim eşleştirme değil gerekçeyle açıklanır.")
        ])
        b = plan("İnkılap Dosyası", "inkilap-kaynak", [
            block("Kaynak Seti", 10, f"{topic} için MEB ders kitabındaki güvenilir kısa belge, fotoğraf, tablo veya resmî açıklama örneklerinden bir set kullanın. Öğrenciler kaynakta doğrudan görülen bilgiyi çıkarır."),
            block("Kaynak Ne Kanıtlıyor?", 10, "İkililer kaynağın hangi değişim veya hedef hakkında kanıt sunduğunu, hangi konuda tek başına yeterli olmadığını yazar."),
            block("Sentez", 10, "İki farklı kaynaktan gelen bilgi tek bir neden-katkı-sonuç paragrafında birleştirilir; uydurma alıntı kullanılmaz.")
        ])
    elif any(k in t for k in ["dış politika", "hatay", "musul", "mübadele", "montrö", "sadabat", "balkan"]):
        a = plan("Dış Politika Karar Matrisi", "inkilap-dis-politika", [
            block("İlkeleri Çıkar", 10, f"{topic} için tam bağımsızlık, gerçekçilik, barış, mütekabiliyet ve millî menfaat gibi ilgili ilkeleri MEB materyalinden belirleyin."),
            block("Gelişmeyi Analiz Et", 15, "Gruplar olayın taraflarını, Türkiye'nin hedefini, kullanılan yöntemi ve sonucu tabloya yazar. Kararın hangi ilkeyle daha güçlü ilişkilendiğini gerekçelendirir.", ["Aynı ilke farklı olaylarda farklı yöntemlere yol açabilir mi?"]),
            block("Diplomasi Notu", 5, "Her grup gelişmeyi 'hedef-yöntem-sonuç' biçiminde üç kısa ifadeyle özetler.")
        ])
        b = plan("Harita ve Belge Eşleştirme", "inkilap-harita-belge", [
            block("Mekânı Bul", 10, f"{topic} ile ilgili MEB materyalindeki harita üzerinde olayın coğrafi bağlamını belirleyin."),
            block("Belgeyle Tamamla", 10, "Harita bilgisini kısa resmî metin/antlaşma özeti veya ders kitabındaki belgeyle birleştirip hangi yeni bilginin elde edildiğini yazın."),
            block("İki Kaynak, Bir Sonuç", 10, "Öğrenci harita ve metnin birlikte desteklediği bir tarihsel çıkarım kurar.")
        ])
    elif any(k in t for k in ["mustafa kemal", "atatürk", "çocukluk", "fikir hayat", "askerlik", "ölüm", "eser"]):
        a = plan("Biyografik Kanıt Zinciri", "inkilap-biyografi", [
            block("Olay – Özellik", 10, f"{topic} için MEB materyalindeki yaşam olayı/görev/ürünleri kronolojik kartlara ayırın."),
            block("Kanıttan Özelliğe", 15, "Gruplar bir kişilik özelliği veya düşünsel yön için hangi olayın gerçekten kanıt sayılabileceğini tartışır. Sonradan uydurulmuş sözler veya kaynaksız alıntılar kullanılmaz.", ["Bir başarı tek başına hangi özelliği kanıtlar, hangisini kanıtlamaz?"]),
            block("Ölçülü Çıkarım", 5, "Öğrenci 'Bu olay … özelliğine işaret eder; çünkü …' cümlesini yazar.")
        ])
        b = plan("Dönemin Etki Haritası", "inkilap-etki-haritasi", [
            block("Çevreyi Kur", 10, f"{topic} bağlamında kişi, okul/şehir, savaş/siyasi olay ve fikir akımlarını MEB materyalinden ayrı kartlara yazın."),
            block("Etkiyi Gerekçelendir", 10, "İkililer kartlar arasında 'etkiledi, fırsat verdi, karşılaştırma sağladı, sorumluluk yükledi' gibi ilişkiler kurar."),
            block("En Güçlü Etki", 10, "Her ikili en belirgin etkiyi seçip neden diğerlerinden daha açıklayıcı olduğunu savunur.")
        ])
    else:
        a = plan("Tarihsel Kaynak Atölyesi", "inkilap-kaynak-genel", [
            block("Kaynağı Tanı", 10, f"{topic} için MEB ders materyalindeki uygun bir fotoğraf, harita, resmî metin, gazete haberi veya tabloyu inceleyin. Kaynağın türü, zamanı ve doğrudan verdiği bilgi yazılır."),
            block("Kanıt ve Yorum", 15, "Gruplar 'kaynak neyi kanıtlıyor?' ve 'biz bundan ne yorumluyoruz?' sütunlarını ayrı doldurur.", ["Kaynağın söylemediği ne var?", "Tek kaynakla kesin hüküm vermek ne zaman sakıncalıdır?"]),
            block("Tarihsel Sonuç", 5, "Öğrenci kanıtın izin verdiği ölçüde bir sonuç cümlesi kurar.")
        ])
        b = plan("Neden – Sonuç Çemberi", "inkilap-neden-sonuc", [
            block("Unsurları Ayır", 10, f"{topic} ile ilgili olay, aktör, sebep ve sonucu karışık kartlarda verin."),
            block("Bağlantıyı Kur", 10, "Gruplar kartları neden-sonuç ağına yerleştirip her okun gerekçesini açıklasın."),
            block("Ağı Sınat", 10, "Başka grup bir bağlantıya 'bu olmasaydı ne değişirdi?' sorusu sorar; ilk grup ağı revize eder veya savunur.")
        ])
    return a, b

def make_hint(kind, grade, week, rec, phase):
    if kind == "fen":
        a, b = fen_plans(rec["topic"], rec["outcome"], rec["process"], rec["evidence"], phase)
        label = ("TYMM" if grade in (5, 6, 7) else "MEB 2026–2027") + " • Fen • 2 alternatif • 30 dk"
        source = FEN_URL
    elif kind == "sos":
        a, b = social_plans(rec["topic"], rec["outcome"], rec["process"], rec["evidence"], phase)
        label = "TYMM • Sosyal • 2 alternatif • 30 dk"
        source = SOS_URL
    else:
        a, b = ita_plans(rec["topic"], rec["outcome"], rec["process"], phase)
        label = "MEB 2026–2027 • İnkılap • 2 alternatif • 30 dk"
        source = ITA_URL
    codes = re.findall(r"(?:FB|F|SB|İTA|ITA)[ .]?\d+\.\d+\.\d+(?:\.\d+)?", rec["outcome"])
    return {
        "sourceOutcomeCode": " / ".join(codes),
        "totalMinutes": 30,
        "plans": [a, b],
        "verificationStatus": "official-plan-aligned",
        "qualityLevel": "v8-middle-school",
        "label": label,
        "researchBasis": [
            source,
            "2026–2027 MEB resmî taslak çerçeve yıllık planı",
            "Haftanın resmî konu/öğrenme çıktısı ve süreç bileşenleri"
        ]
    }

def enrich_hints(records, kind, grade):
    seen = {}
    for w in range(1, 38):
        rec = records[w]
        if not rec["outcome"]:
            continue
        key = rec["outcome"]
        phase = seen.get(key, 0)
        seen[key] = phase + 1
        rec["hints"] = make_hint(kind, grade, w, rec, phase)
    return records

def make_course(subject_id, subject_name, grade, records, calendar):
    pages = []
    for base in calendar:
        p = copy.deepcopy(base)
        if p["type"] == "holiday":
            p.update(unit="", topic="", outcome="", special=None)
        else:
            rec = records[p["weekNumber"]]
            p.update(
                unit=rec["unit"], topic=rec["topic"], outcome=rec["outcome"],
                special=rec["special"]
            )
            if "hints" in rec:
                p["hints"] = rec["hints"]
        pages.append(p)
    return {
        "id": f"{subject_id}-{grade}",
        "subjectId": subject_id,
        "subjectName": subject_name,
        "name": subject_name,
        "shortName": subject_name,
        "teachingWeekCount": 37,
        "pages": pages,
    }

def patch_data():
    fen_sheets = archive_workbook(FEN_URL)
    sos_sheets = archive_workbook(SOS_URL)
    ita_sheets = archive_workbook(ITA_URL)
    records = {}
    for grade in (5, 6, 7, 8):
        records[("science", grade)] = enrich_hints(
            parse_records(find_sheet(fen_sheets, grade, "fen"), "fen", grade), "fen", grade)
    for grade in (5, 6, 7):
        records[("social", grade)] = enrich_hints(
            parse_records(find_sheet(sos_sheets, grade, "sos"), "sos", grade), "sos", grade)
    records[("social", 8)] = enrich_hints(
        parse_records(find_sheet(ita_sheets, 8, "ita"), "ita", 8), "ita", 8)

    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    by_grade = {g["grade"]: g for g in data["grades"]}
    calendar = copy.deepcopy(by_grade[10]["courses"][0]["pages"])
    for grade in (5, 6, 7, 8):
        g = by_grade.get(grade)
        if g is None:
            g = {"grade": grade, "courses": []}
            data["grades"].append(g)
            by_grade[grade] = g
        g["courses"] = [c for c in g["courses"] if c.get("subjectId") not in ("science", "social")]
        g["courses"].append(make_course("science", "Fen Bilimleri", grade, records[("science", grade)], calendar))
        social_name = "T.C. İnkılap Tarihi ve Atatürkçülük" if grade == 8 else "Sosyal Bilgiler"
        g["courses"].append(make_course("social", social_name, grade, records[("social", grade)], calendar))
    data["grades"].sort(key=lambda x: x["grade"])
    data["schemaVersion"] = 8
    data["generatedFor"] = "Kazanım v8"
    data.setdefault("sources", []).extend([
        "MEB/TYMM – 2026–2027 Fen Bilimleri (3–8) Taslak Çerçeve Yıllık Planı",
        "MEB/TYMM – 2026–2027 Sosyal Bilgiler (4–7) Taslak Çerçeve Yıllık Planı",
        "MEB – 2026–2027 T.C. İnkılap Tarihi ve Atatürkçülük 8. Sınıf Taslak Çerçeve Yıllık Planı",
        "2026–2027'de TYMM ortaokul 5–7. sınıflarda uygulanır; 8. sınıf Fen ve İnkılap kayıtlarında o yılın resmî yürürlükteki yıllık planı esas alınmıştır."
    ])
    JSON_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return records

def patch_ui():
    s = MAIN_PATH.read_text(encoding="utf-8")
    old = '''        String[] preferredOrder = {
                "dkab", "tdb1", "tdb2", "english", "history", "math", "tde",
                "physics", "chemistry", "biology", "geography", "philosophy", "health"
        };'''
    new = '''        String[] preferredOrder = {
                "dkab", "science", "social", "tdb1", "tdb2", "english", "history", "math", "tde",
                "physics", "chemistry", "biology", "geography", "philosophy", "health"
        };'''
    if old not in s:
        raise RuntimeError("preferredOrder marker bulunamadı")
    s = s.replace(old, new)
    s = s.replace('        if ("english".equals(subjectId)) return "🌐";',
                  '        if ("science".equals(subjectId)) return "🧪";\n        if ("social".equals(subjectId)) return "🧭";\n        if ("english".equals(subjectId)) return "🌐";')
    s = s.replace('        if ("english".equals(subjectId)) return "İngilizce";',
                  '        if ("science".equals(subjectId)) return "Fen Bilimleri";\n        if ("social".equals(subjectId)) return "Sosyal Bilgiler";\n        if ("english".equals(subjectId)) return "İngilizce";')
    s = s.replace('TextView badge = label("v7", 13, Color.WHITE, true);',
                  'TextView badge = label("v8", 13, Color.WHITE, true);')
    s = s.replace("Sürüm 7", "Sürüm 8")
    about_old = "DKAB 9–11 için iki alternatif 30 dakikalık İpucu planları vardır. 10. sınıf planları v7'de TYMM'nin süreç bileşenleri ve öğrenme-öğretme uygulamaları esas alınarak haftaya özgü yeniden hazırlanmıştır."
    about_new = "DKAB İpucu planlarına ek olarak Fen Bilimleri 5–8 ve Sosyal Bilgiler 5–7 için haftalık 30 dakikalık iki alternatif ders planı bulunur. Sosyal Bilgiler menüsündeki 8. sınıf T.C. İnkılap Tarihi ve Atatürkçülük dersidir."
    s = s.replace(about_old, about_new)
    source_anchor = "MEB Türkiye Yüzyılı Maarif Modeli – Ortaöğretim Taslak Çerçeve Planları (2026–2027)."
    if source_anchor in s and "Temel Eğitim Taslak Çerçeve Planları" not in s:
        s = s.replace(source_anchor, source_anchor + "\\n\\nOrtaokul Fen Bilimleri ve Sosyal Bilgiler:\\nMEB/TYMM Temel Eğitim Taslak Çerçeve Planları (2026–2027).\\n\\n8. sınıf T.C. İnkılap Tarihi ve Atatürkçülük:\\nMEB 2026–2027 resmî taslak çerçeve yıllık planı.")
    MAIN_PATH.write_text(s, encoding="utf-8")

def patch_version():
    s = GRADLE_PATH.read_text(encoding="utf-8")
    s = s.replace("versionCode = 7", "versionCode = 8")
    s = s.replace('versionName = "7"', 'versionName = "8"')
    GRADLE_PATH.write_text(s, encoding="utf-8")

def validate(records):
    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    ids = {c["id"] for g in data["grades"] for c in g["courses"]}
    expected = {f"science-{g}" for g in (5,6,7,8)} | {f"social-{g}" for g in (5,6,7,8)}
    if not expected.issubset(ids):
        raise RuntimeError("Ortaokul ders kayıtları eksik: " + str(expected - ids))
    total_hints = 0
    for grade in (5,6,7,8):
        g = next(x for x in data["grades"] if x["grade"] == grade)
        for sid in ("science", "social"):
            c = next(x for x in g["courses"] if x["subjectId"] == sid)
            if grade == 8 and sid == "social":
                assert c["subjectName"] == "T.C. İnkılap Tarihi ve Atatürkçülük"
            normal = 0
            specials = 0
            styles = set()
            for p in c["pages"]:
                if p.get("type") != "week":
                    continue
                if p.get("outcome"):
                    normal += 1
                    h = p.get("hints")
                    assert h and len(h["plans"]) == 2
                    assert h["plans"][0]["style"] != h["plans"][1]["style"]
                    for pl in h["plans"]:
                        assert 1 <= len(pl["blocks"]) <= 3
                        assert sum(b["minutes"] for b in pl["blocks"]) == 30
                        assert min(b["minutes"] for b in pl["blocks"]) >= 5
                        assert all(b["title"].strip() and b["text"].strip() for b in pl["blocks"])
                        styles.add(pl["style"])
                    total_hints += 1
                else:
                    specials += 1
                    assert not p.get("hints")
            assert 30 <= normal <= 36, (grade, sid, normal)
            assert len(styles) >= 4, (grade, sid, len(styles))
            print("v8", grade, sid, "normal", normal, "special/no-outcome", specials, "styles", len(styles))
    assert total_hints >= 250, total_hints
    print("v8 middle-school validation OK; hint weeks =", total_hints)

def main():
    records = patch_data()
    patch_ui()
    patch_version()
    validate(records)
    print("Kazanım v8 hazır:", PROJECT)

if __name__ == "__main__":
    main()
