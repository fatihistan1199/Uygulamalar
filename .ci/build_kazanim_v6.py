#!/usr/bin/env python3
import json, re, sys
from pathlib import Path

PROJECT = Path(sys.argv[1] if len(sys.argv) > 1 else '/mnt/data/_kazv5/Kazanim_v5').resolve()


def outcome_code(text):
    m = re.search(r'(DKAB\.\d+\.\d+\.\d+)', text or '')
    return m.group(1) if m else ''


def focus_questions(topic, outcome, unit):
    t = f"{topic} {outcome}".lower()
    if 'kur’an’dan mesajlar' in t or "kur'an’dan mesajlar" in t or 'suresi' in (outcome or '').lower():
        return [
            'Metindeki ana mesajı hangi ifade veya kavramlar destekliyor?',
            'Aynı metinden farklı bir çıkarım yapılabilir mi? Gerekçesi nedir?',
            'Bu mesaj günlük hayatta hangi tutum veya karara dönüşebilir?'
        ]
    if 'yaratılış' in t:
        return [
            'İnsanı diğer varlıklardan ayıran özelliklerden hangisi sorumlulukla en yakından ilişkilidir?',
            'Akıl, irade ve sorumluluk arasında nasıl bir bağ kurulabilir?',
            'Bir özelliğin varlığı insana hangi imkânı ve hangi sorumluluğu yükler?'
        ]
    if 'bilgi' in t or 'doğruyu arayan' in t:
        return [
            'Bir bilginin güvenilir olduğuna karar verirken hangi ölçütler kullanılmalıdır?',
            'Çok kişinin aynı şeyi söylemesi onu doğru yapar mı? Neden?',
            'Akıl, duyu, haber ve vahyin bilgiye ulaşmadaki imkân ve sınırları nelerdir?'
        ]
    if 'dua' in t or 'ibadet' in t:
        return [
            'İbadet veya dua insanın davranışlarında nasıl bir karşılık bulabilir?',
            'Dua ile çaba birbirinin alternatifi midir, tamamlayıcısı mıdır?',
            'Bir davranışın ibadet değeri taşımasında amaç ve tutumun etkisi nedir?'
        ]
    if 'iman' in t or 'inanç esas' in t:
        return [
            'İman yalnızca bir bilgi midir, yoksa insanın tutumlarını da etkiler mi?',
            'İman esasları birbirinden bağımsız mı, ilişkili bir bütün mü oluşturur?',
            'Bir inancın birey ve toplum hayatında görünür hâle gelmesi ne demektir?'
        ]
    if 'ahlak' in t or 'ahlaki' in t:
        return [
            'Bir davranışı ahlaki yapan şey sonuç, niyet, ilke veya bunların birlikte değerlendirilmesi midir?',
            'Aynı davranış farklı şartlarda farklı biçimde değerlendirilebilir mi?',
            'Bir ahlaki ilkeyi günlük hayata taşımak neden bazen zorlaşır?'
        ]
    if 'muhammed' in t or 'ehlibeyt' in t:
        return [
            'Bir kişiyi örnek almak ile onu yalnızca taklit etmek arasında ne fark vardır?',
            'Hz. Muhammed’in beşerî yönü ile peygamberlik görevi nasıl birlikte düşünülmelidir?',
            'Bir örnek davranışı bugünün şartlarında uygulamak için hangi ilkeyi anlamak gerekir?'
        ]
    if 'kader' in t or 'irade' in t or 'sorumluluk' in t:
        return [
            'Bir insan hangi durumlarda gerçekten seçim yapmış sayılır?',
            'Sorumluluk ile irade arasında nasıl bir ilişki vardır?',
            'Başına gelen her şeyi kadere bağlamak insanın sorumluluğunu nasıl etkiler?'
        ]
    if 'allah-âlem' in t or 'allah-âlem' in t or 'varlığının delilleri' in t:
        return [
            'Bir gözlem ne zaman delil olarak kullanılabilir?',
            'Düzen, sebep ve anlam arasında nasıl bir ilişki kurulabilir?',
            'Aynı olguya bakan iki insanın farklı sonuçlara ulaşması mümkün müdür? Neden?'
        ]
    if 'isim ve sıfat' in t:
        return [
            'Allah’ın bir ismini bilmek ile o ismin anlamını hayata yansıtmak arasında ne fark vardır?',
            'İsim ve sıfatlar Allah tasavvurunu nasıl şekillendirir?',
            'Bir kavramı davranışa dönüştürmek için önce hangi anlam bağını kurmak gerekir?'
        ]
    if 'adalet' in t or 'eşitlik' in t:
        return [
            'Herkese aynı davranmak her zaman adil midir?',
            'Eşitlik ile adaletin aynı olmadığı bir örnek kurulabilir mi?',
            'Bir kararın adil olduğunu hangi ölçütlerle savunabiliriz?'
        ]
    if 'barış' in t:
        return [
            'Barış yalnızca çatışmanın olmaması mıdır?',
            'Bir anlaşmazlıkta adalet sağlanmadan kalıcı barış kurulabilir mi?',
            'Barışı güçlendiren bireysel davranışlara hangi örnekler verilebilir?'
        ]
    if 'çevre' in t or 'teknoloji' in t:
        return [
            'Yapabiliyor olmak, yapmamız gerektiği anlamına gelir mi?',
            'Bir teknolojinin faydası ile doğurabileceği zarar nasıl birlikte değerlendirilir?',
            'Çevreye karşı sorumluluk yalnız bireysel tercihlerle sınırlı mıdır?'
        ]
    if 'yorum farklılık' in t or 'itikadi' in t or 'fıkhi' in t or 'siyasi' in t:
        return [
            'Aynı temel kaynağa bağlı insanlar neden farklı yorumlara ulaşabilir?',
            'Yorum farklılığı ile temel inanç farklılığı aynı şey midir?',
            'Bir yorumun oluşmasında tarih, kültür ve yöntem nasıl etkili olabilir?'
        ]
    if 'felsefe' in t or 'bilim' in t:
        return [
            'Aynı soruya din, felsefe ve bilim neden farklı yöntemlerle yaklaşabilir?',
            'Farklı yöntem kullanmak mutlaka çatışma anlamına gelir mi?',
            'Bir görüşün hangi soruya cevap verdiğini belirlemek neden önemlidir?'
        ]
    if 'sanat' in t:
        return [
            'Bir sanat eserinde inanç veya dünya görüşünün etkisi nasıl fark edilebilir?',
            'Sanat yalnız estetik bir ürün müdür, anlam taşıyan bir ifade biçimi midir?',
            'Aynı sembol farklı kültürlerde farklı anlamlar taşıyabilir mi?'
        ]
    if 'medeniyet' in t or 'gönül coğrafya' in t:
        return [
            'Bir medeniyeti yalnız yapılar ve eserlerle tanımlamak yeterli midir?',
            'Bilgi, değer, kurum ve sanat bir medeniyetin oluşumunda nasıl birlikte etkili olur?',
            'Geçmişten kalan bir miras bugünün sorunlarına nasıl katkı sağlayabilir?'
        ]
    if 'kötülük problemi' in t:
        return [
            'İnsanların sebep olduğu kötülüklerle doğal olaylardan doğan acılar aynı biçimde mi değerlendirilmelidir?',
            'Özgür irade tartışması kötülük problemine nasıl bağlanır?',
            'Bir problemin zor olması onun hakkında düşünmeyi bırakmak için yeterli midir?'
        ]
    if 'istismar' in t or 'yeni dinî hareket' in t:
        return [
            'Bir söylemin dinî kavramlar kullanması onu güvenilir kılmaya yeter mi?',
            'İnsanları sorgulamaktan uzaklaştıran hangi yöntemler risk işareti olabilir?',
            'Bilgi kaynağını ve otorite iddiasını sorgulamak neden önemlidir?'
        ]
    if 'yahudilik' in t or 'hristiyanlık' in t:
        return [
            'Bir dini kendi tarihî bağlamı ve kaynakları içinde incelemek neden önemlidir?',
            'Benzerlikleri belirlemek kadar farklılıkları doğru tanımlamak neden gereklidir?',
            'Bir din hakkında genelleme yapmadan önce hangi tür bilgiler kontrol edilmelidir?'
        ]
    return [
        f'{topic} konusunda temel kavramlar arasında nasıl bir ilişki kurulabilir?',
        'Bu konuyla ilgili bir görüşü güçlü yapan gerekçe nedir?',
        'Öt�ğrenilen ilke günlükhayatta hangi durumda sınanabilir?'
    ]


def scenario_for(topic, week=0, phase=0):
    t=(topic or '').lower()
    choices=[]
    if 'yaratılış' in t:
        choices=[
            'Bir öğrenci, kurallara kusursuz uyan bir robotun da insan kadar ahlaki sorumluluk taşıyabileceğini savunuyor; arkadaşı ise seçim yapma ve iradenin belirleyici olduğunu söylüyor.',
            'Bir grup “İnsanı değerli yapan şey zeziâtır.” derb�n başka bir grup “Asıl belirleyici olan sorumlululuk alabilmesidir.” görüşünü savunuyor.'
        ]
    elif 'doğruyu arayan' in t or 'bilgi' in t:
        choices=[
            'Sınıf grubuna kaynağı belirtilmeyen fakat yüzlerce kez paylaşılmış bir bilgi geliyor. Bir öğrenci “Bu kadar kişi paylaşııysa doğrudur.” diyor; diğeri kaynağ ı görmeden kabul etmiyor.',
            'İki öğrenci aynı olay hakkında farklı videolar izliyor ve ikisi de kendi videosunun gerçei tam gösterdiğini düşünüyor. Hangisinin daha sağlam bilgiye ulaştıpıgı belli değil.'
        ]
    elif 'dua' in t:
        choices=[
            'Bir öğrenci sınavdan önce dua ediyor fakat hiçhazırlık zirlik yapmıyor; arkadaşı “Dua ediyorsan çalışmana gerek yok.” diyor. Başka bir öğrenci dua ile çabanın birlikte olması gerektiğini savunuyor.',
            'Bir öğrenci değiştiremeyeceği bir olay hç�vl teredüt ederken, değiştirebileceği davranışlar k�nusunda hhiçbir adım atmıyor. Arkadaşları dua ve sorumlululuk iliykişisini tartışıyor.'
        ]
    elif 'ibadet' in t:
        choices=[
            'Bir öğrenci ibadetlerini düzenli yaptığını söylüyor fakat arkadaşlarına karı'�