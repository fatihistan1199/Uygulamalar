#!/usr/bin/env python3
import copy, json, os, re, shutil, ssl, sys, tempfile, unicodedata, urllib.request, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

PROJECT = Path(sys.argv[1] if len(sys.argv)>1 else 'buildsrc/Kazanim_v4').resolve()
NS='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
NSREL='http://schemas.openxmlformats.org/officeDocument/2006/relationships'

TDB1_URL='https://www.izzeteker.com/api/contents/download/23106'
TDB2_URL='https://www.izzeteker.com/api/contents/download/23105'
HEALTH_URL='https://www.biyolojihikayesi.com/tutanaklar/9-sinif-saglik-bilgisi-ve-trafik-kulturu-taslak-yillik-plani-20260902142227-4264.xlsx'

def clean(x, multi=False):
    if x is None: return ''
    s=unicodedata.normalize('NFC',str(x)).replace('\uf020',' ').replace('\u00a0',' ').replace('\r','\n')
    if multi:
        return '\n'.join(re.sub(r'[ \t]+',' ',line).strip() for line in s.split('\n') if line.strip())
    return re.sub(r'\s+',' ',s).strip()

def download(url,path,insecure=False):
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Referer':'https://www.izzeteker.com/'})
    ctx=ssl._create_unverified_context() if insecure else None
    with urllib.request.urlopen(req,timeout=90,context=ctx) as r, open(path,'wb') as f:
        shutil.copyfileobj(r,f)
    with zipfile.ZipFile(path) as z:
        if 'xl/workbook.xml' not in z.namelist(): raise RuntimeError(f'Geçersiz XLSX: {path}')

def raw_sheets(path):
    z=zipfile.ZipFile(path); shared=[]
    if 'xl/sharedStrings.xml' in z.namelist():
        root=ET.fromstring(z.read('xl/sharedStrings.xml'))
        shared=[''.join((t.text or '') for t in si.iter(f'{{{NS}}}t')) for si in root.findall(f'{{{NS}}}si')]
    wr=ET.fromstring(z.read('xl/workbook.xml')); rr=ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
    rel={r.attrib['Id']:r.attrib['Target'] for r in rr}; out={}
    for sh in wr.find(f'{{{NS}}}sheets'):
        name=sh.attrib['name']; target=rel[sh.attrib[f'{{{NSREL}}}id']]
        sp=target.lstrip('/') if target.startswith('/') else 'xl/'+target.lstrip('/')
        root=ET.fromstring(z.read(sp)); vals={}; mr=mc=0
        for c in root.iter(f'{{{NS}}}c'):
            m=re.match(r'([A-Z]+)(\d+)',c.attrib.get('r',''))
            if not m: continue
            letters,rs=m.groups(); row=int(rs); col=0
            for ch in letters: col=col*26+ord(ch)-64
            typ=c.attrib.get('t'); v=c.find(f'{{{NS}}}v'); ins=c.find(f'{{{NS}}}is'); value=None
            if typ=='s' and v is not None: value=shared[int(v.text)]
            elif typ=='inlineStr' and ins is not None: value=''.join((t.text or '') for t in ins.iter(f'{{{NS}}}t'))
            elif v is not None: value=v.text
            if value is not None: vals[(row,col)]=value; mr=max(mr,row); mc=max(mc,col)
        out[name]=(vals,mr,mc)
    return out

def parse_week_number(text):
    m=re.search(r'(\d+)\s*\.?\s*HAFTA',clean(text).upper())
    if not m: m=re.search(r'(\d+)\s*\.\s*Hafta',clean(text),re.I)
    return int(m.group(1)) if m else None

def parse_tdb(path, islam1):
    sheets=raw_sheets(path); sh=next(iter(sheets.values())); vals,mr,mc=sh
    data={w:{'unit':'','topic':'','outcome':'','special':None} for w in range(1,38)}
    last={'unit':'','topic':'','outcome':''}
    for r in range(1,mr+1):
        w=parse_week_number(vals.get((r,2)))
        if not w or not 1<=w<=37: continue
        rowtxt=' '.join(clean(vals.get((r,c))) for c in range(1,min(mc,14)+1)).upper()
        special='Okul Temelli Planlama' if 'OKUL TEMELL' in rowtxt else ('Sosyal Etkinlik' if 'SOSYAL ETK' in rowtxt else None)
        unit=clean(vals.get((r,4)))
        topic=clean(vals.get((r,5 if islam1 else 6)),multi=True)
        outcome=clean(vals.get((r,7)),multi=True)
        if special and not (unit or topic or outcome):
            data[w]={'unit':'','topic':'','outcome':'','special':special}; continue
        for k,v in [('unit',unit),('topic',topic),('outcome',outcome)]:
            if v: last[k]=v
        data[w]={'unit':unit or last['unit'],'topic':topic or last['topic'],'outcome':outcome or last['outcome'],'special':None}
    if any(not data[w]['unit'] and not data[w]['special'] for w in range(1,36)):
        raise RuntimeError(f'TDB parse eksik: {path}')
    return data

def parse_health(path):
    sheets=raw_sheets(path); sh=sheets.get('SağlıkBilgisi') or next(iter(sheets.values())); vals,mr,mc=sh
    data={w:{'unit':'','topic':'','outcome':'','special':None} for w in range(1,38)}
    for r in range(1,mr+1):
        w=parse_week_number(vals.get((r,2)))
        if not w or not 1<=w<=37: continue
        hours=clean(vals.get((r,3))).upper(); topic=clean(vals.get((r,4)),multi=True); outcome=clean(vals.get((r,5)),multi=True)
        special=None
        if 'OKUL TEMELL' in hours: special='Okul Temelli Planlama'
        elif 'SOSYAL ETK' in hours: special='Sosyal Etkinlik'
        if special:
            data[w]={'unit':'','topic':'','outcome':'','special':special}; continue
        unit='2. Trafik Kültürü' if topic.startswith('2.') else '1. Sağlık Bilgisi'
        data[w]={'unit':unit,'topic':topic,'outcome':outcome,'special':None}
    if any(not data[w]['topic'] and not data[w]['special'] for w in range(1,36)):
        raise RuntimeError('Sağlık planı parse eksik')
    return data

def make_course(sid,sname,grade,records,calendar):
    pages=[]
    for base in calendar:
        p=copy.deepcopy(base)
        if p['type']=='holiday': p.update(unit='',topic='',outcome='',special=None)
        else:
            x=records[p['weekNumber']]; p.update(unit=x['unit'],topic=x['topic'],outcome=x['outcome'],special=x['special'])
        pages.append(p)
    return {'id':f'{sid}-{grade}','subjectId':sid,'subjectName':sname,'name':sname,'shortName':sname,'teachingWeekCount':37,'pages':pages}

def patch_data(tdb1,tdb2,health):
    jp=PROJECT/'app/src/main/assets/curriculum_2026_2027.json'; data=json.loads(jp.read_text(encoding='utf-8'))
    by_grade={g['grade']:g for g in data['grades']}; calendar=copy.deepcopy(by_grade[10]['courses'][0]['pages'])
    for grade in (9,10,11,12):
        by_grade[grade]['courses'].append(make_course('tdb1','Temel Dini Bilgiler (İslam 1)',grade,tdb1,calendar))
        by_grade[grade]['courses'].append(make_course('tdb2','Temel Dini Bilgiler (İslam 2)',grade,tdb2,calendar))
    by_grade[9]['courses'].append(make_course('health','Sağlık Bilgisi ve Trafik Kültürü',9,health,calendar))
    data['schemaVersion']=5; data['generatedFor']='Kazanım v5'
    data.setdefault('sources',[])
    data['sources'] += ['İzzet Eker – 2026–2027 Lise Temel Dini Bilgiler (İslam 1-2) yıllık planları','Biyoloji Hikayesi – 2026–2027 9. Sınıf Sağlık Bilgisi ve Trafik Kültürü yıllık planı']
    jp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    ids=[c['id'] for g in data['grades'] for c in g['courses']]
    if len(ids)!=47 or len(set(ids))!=47: raise RuntimeError(f'Beklenen 47 ders-sınıf kaydı yok: {len(ids)}')

def patch_ui():
    p=PROJECT/'app/src/main/java/com/kazanim/app/MainActivity.java'; s=p.read_text(encoding='utf-8')
    s=s.replace('        renderHome();\n    }\n\n    @Override\n    public void onBackPressed()', '        renderFavorites();\n    }\n\n    @Override\n    public void onBackPressed()',1)
    s=s.replace('''        List<String> subjectIds = new ArrayList<>();\n        for (Course c : allCourses) {\n            if (c.subjectId != null && !subjectIds.contains(c.subjectId)) subjectIds.add(c.subjectId);\n        }''','''        List<String> subjectIds = new ArrayList<>();\n        String[] preferredOrder = {\n                "dkab", "tdb1", "tdb2", "english", "history", "math", "tde",\n                "physics", "chemistry", "biology", "geography", "philosophy", "health"\n        };\n        for (String id : preferredOrder) {\n            for (Course c : allCourses) {\n                if (id.equals(c.subjectId)) { subjectIds.add(id); break; }\n            }\n        }''')
    s=s.replace('Dersi seçin, ardından 9–12. sınıflardan birini açın.', 'Dersi seçin, ardından mevcut sınıf düzeylerinden birini açın.')
    a=s.index('    private View subjectButton(String subjectId, String title) {'); b=s.index('    private String subjectSymbol(String subjectId) {',a)
    block=s[a:b].replace('TextView icon = label(subjectSymbol(subjectId), 28, PRIMARY_DARK, true);\n        icon.setGravity(Gravity.CENTER);\n        button.addView(icon);\n        addSpace(button, 7);\n        TextView name = label(title, 17, TEXT, true);','TextView icon = label(subjectSymbol(subjectId), 25, PRIMARY_DARK, true);\n        icon.setGravity(Gravity.CENTER);\n        icon.setBackground(roundRect(PRIMARY_SOFT, 16, Color.TRANSPARENT, 0));\n        button.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(42)));\n        addSpace(button, 6);\n        int titleSize = title.length() > 22 ? 13 : (title.length() > 15 ? 15 : 16);\n        TextView name = label(title, titleSize, TEXT, true);')
    s=s[:a]+block+s[b:]
    a=s.index('    private String subjectSymbol(String subjectId) {'); b=s.index('    private void renderSubject(String subjectId) {',a)
    methods='''    private String subjectSymbol(String subjectId) {\n        if ("dkab".equals(subjectId)) return "📖";\n        if ("tdb1".equals(subjectId) || "tdb2".equals(subjectId)) return "🕌";\n        if ("english".equals(subjectId)) return "🌐";\n        if ("history".equals(subjectId)) return "🏛";\n        if ("math".equals(subjectId)) return "➗";\n        if ("tde".equals(subjectId)) return "✒";\n        if ("physics".equals(subjectId)) return "⚛";\n        if ("chemistry".equals(subjectId)) return "⚗";\n        if ("biology".equals(subjectId)) return "🌿";\n        if ("geography".equals(subjectId)) return "🌍";\n        if ("philosophy".equals(subjectId)) return "💭";\n        if ("health".equals(subjectId)) return "🩺";\n        return "●";\n    }\n\n    private String subjectTitle(String subjectId) {\n        if ("dkab".equals(subjectId)) return "Din Kültürü";\n        if ("tdb1".equals(subjectId)) return "Temel Dini Bilgiler (İslam 1)";\n        if ("tdb2".equals(subjectId)) return "Temel Dini Bilgiler (İslam 2)";\n        if ("health".equals(subjectId)) return "Sağlık Bilgisi ve Trafik Kültürü";\n        if ("english".equals(subjectId)) return "İngilizce";\n        if ("history".equals(subjectId)) return "Tarih";\n        if ("math".equals(subjectId)) return "Matematik";\n        if ("tde".equals(subjectId)) return "Türk Dili ve Edebiyatı";\n        if ("physics".equals(subjectId)) return "Fizik";\n        if ("chemistry".equals(subjectId)) return "Kimya";\n        if ("biology".equals(subjectId)) return "Biyoloji";\n        if ("geography".equals(subjectId)) return "Coğrafya";\n        if ("philosophy".equals(subjectId)) return "Felsefe";\n        for (Course c : allCourses) if (subjectId.equals(c.subjectId)) return c.subjectName;\n        return "Ders";\n    }\n\n'''
    s=s[:a]+methods+s[b:]
    s=s.replace('TextView badge = label("v4", 13, Color.WHITE, true);','TextView badge = label("v5", 13, Color.WHITE, true);')
    s=s.replace('''                String title = p.isSpecialWeek()\n                        ? p.weekNumber + ". Hafta • " + p.special\n                        : p.weekNumber + ". Hafta • " + p.topic;''','''                String searchTopic = p.isSpecialWeek() ? p.special : p.topic;\n                if (searchTopic == null || searchTopic.trim().isEmpty()) searchTopic = "—";\n                String title = p.weekNumber + ". Hafta - " + p.dateLabel + " - " + searchTopic;''')
    s=s.replace('MEB Türkiye Yüzyılı Maarif Modeli – Ortaöğretim Taslak Çerçeve Planları (2026–2027).\\n\\n" +','MEB Türkiye Yüzyılı Maarif Modeli – Ortaöğretim Taslak Çerçeve Planları (2026–2027).\\n\\nTemel Dini Bilgiler (İslam 1-2):\\nİzzet Eker sitesindeki 2026–2027 MEB çerçeve yıllık planları.\\n\\nSağlık Bilgisi ve Trafik Kültürü:\\n2026–2027 9. sınıf yıllık planı; MEB öğretim programı esaslı.\\n\\n" +')
    s=s.replace('Sürüm 4\\n\\nLise derslerinde haftalık ünite/tema, konu ve öğrenme çıktıları. Din Kültürü, İngilizce, Tarih, Matematik, Türk Dili ve Edebiyatı, Fizik, Kimya, Biyoloji, Coğrafya ve Felsefe.', 'Sürüm 5\\n\\nLise derslerinde haftalık ünite/tema, konu ve öğrenme çıktıları. Temel Dini Bilgiler (İslam 1-2) ve 9. sınıf Sağlık Bilgisi ve Trafik Kültürü de dahildir.')
    p.write_text(s,encoding='utf-8')
    g=PROJECT/'app/build.gradle.kts'; t=g.read_text(encoding='utf-8').replace('versionCode = 4','versionCode = 5').replace('versionName = "4"','versionName = "5"'); g.write_text(t,encoding='utf-8')

def main():
    with tempfile.TemporaryDirectory() as td:
        td=Path(td); t1=td/'tdb1.xlsx'; t2=td/'tdb2.xlsx'; hp=td/'health.xlsx'
        local1=os.environ.get('V5_TDB1_XLSX'); local2=os.environ.get('V5_TDB2_XLSX'); localh=os.environ.get('V5_HEALTH_XLSX')
        if local1: shutil.copy(local1,t1)
        else: download(TDB1_URL,t1)
        if local2: shutil.copy(local2,t2)
        else: download(TDB2_URL,t2)
        if localh: shutil.copy(localh,hp)
        else: download(HEALTH_URL,hp,True)
        patch_data(parse_tdb(t1,True),parse_tdb(t2,False),parse_health(hp))
    patch_ui()
    print('Kazanım v5 hazır:',PROJECT)

if __name__=='__main__': main()
