#!/usr/bin/env python3
import copy, json, os, re, shutil, sys, tempfile, unicodedata, urllib.request, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

PROJECT = Path(sys.argv[1] if len(sys.argv) > 1 else 'buildsrc/Kazanim_v2').resolve()
NS='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
NSREL='http://schemas.openxmlformats.org/officeDocument/2006/relationships'

URLS = {
    'biology': 'https://tymm.meb.gov.tr/assets/file/biyoloji-dersi-taslak-yillik-planlar_20260831_190639_106.zip',
    'geography': 'https://tymm.meb.gov.tr/assets/file/cografya-dersi-taslak-yillik-planlar_20260827_083607_902.zip',
    'philosophy': 'https://tymm.meb.gov.tr/assets/file/felsefe-dersi-taslak-yillik-planlar_20260827_123428_620.zip',
    'physics': 'https://tymm.meb.gov.tr/assets/file/fizik-dersi-taslak-yillik-planlar_20260827_083757_124.zip',
    'chemistry': 'https://tymm.meb.gov.tr/assets/file/kimya-dersi-taslak-yillik-planlar_20260827_083820_533.zip',
    'tde': 'https://tymm.meb.gov.tr/assets/file/turk-dili-ve-edebiyati-dersi-taslak-yillik-planlar_20260827_142401_066.zip',
}

def clean(x, multi=False):
    if x is None: return ''
    s=unicodedata.normalize('NFC',str(x)).replace('\uf020',' ').replace('\u00a0',' ').replace('\r','\n')
    if multi:
        return '\n'.join(re.sub(r'[ \t]+',' ',line).strip() for line in s.split('\n') if line.strip())
    return re.sub(r'\s+',' ',s).strip()

def download(url, path):
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
    with urllib.request.urlopen(req,timeout=90) as r, open(path,'wb') as f:
        shutil.copyfileobj(r,f)

def acquire_plans(tmp):
    local=os.environ.get('V4_PLAN_ROOT')
    if local:
        root=Path(local)
        if root.exists(): return root
    root=tmp/'xlsx'; root.mkdir(parents=True,exist_ok=True)
    for sid,url in URLS.items():
        zpath=tmp/f'{sid}.zip'; download(url,zpath)
        out=root/sid; out.mkdir(exist_ok=True)
        with zipfile.ZipFile(zpath) as z:
            for n in z.namelist():
                if n.lower().endswith('.xlsx'):
                    dest=out/Path(n).name
                    with z.open(n) as src, open(dest,'wb') as f: shutil.copyfileobj(src,f)
    return root

def raw_sheets(path):
    z=zipfile.ZipFile(path)
    shared=[]
    if 'xl/sharedStrings.xml' in z.namelist():
        root=ET.fromstring(z.read('xl/sharedStrings.xml'))
        shared=[''.join((t.text or '') for t in si.iter(f'{{{NS}}}t')) for si in root.findall(f'{{{NS}}}si')]
    wr=ET.fromstring(z.read('xl/workbook.xml'))
    rr=ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
    rel={r.attrib['Id']:r.attrib['Target'] for r in rr}
    out={}
    for sh in wr.find(f'{{{NS}}}sheets'):
        name=sh.attrib['name']
        target=rel[sh.attrib[f'{{{NSREL}}}id']]
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
            if value is not None:
                vals[(row,col)]=value; mr=max(mr,row); mc=max(mc,col)
        out[name]=(vals,mr,mc)
    return out

def parse_sheet(sh):
    vals,mr,mc=sh
    data={w:{'u':[],'t':[],'o':[],'special':None} for w in range(1,38)}
    cur=None
    for r in range(1,mr+1):
        row={c:clean(vals.get((r,c)), multi=(c==6)) for c in range(1,min(mc,12)+1)}
        joined=' '.join(row.values()); upper=joined.upper()
        if 'ARA TATİL' in upper or 'YARIYIL TATİL' in upper:
            cur=None; continue
        m=re.search(r'(\d+)\s*\.\s*Hafta',clean(row.get(2,'')),re.I)
        new_week=False
        if m:
            w=int(m.group(1)); cur=w if 1<=w<=37 else None; new_week=cur is not None
        if cur is None: continue
        unit=clean(row.get(4,'')); topic=clean(row.get(5,'')); outcome=clean(row.get(6,''),multi=True)
        special=None
        if 'OKUL TEMELL' in upper: special='Okul Temelli Planlama'
        elif 'SOSYAL ETK' in upper or 'SOSYAL AKT' in upper: special='Sosyal Etkinlik'
        if new_week and special and not (unit or topic or outcome): data[cur]['special']=special
        if special and not (unit or topic or outcome): continue
        for key,val in [('u',unit),('t',topic),('o',outcome)]:
            if val and val not in data[cur][key]: data[cur][key].append(val)
    out={}; last={'unit':'','topic':'','outcome':''}
    for w in range(1,38):
        x=data[w]; unit='\n'.join(x['u']); topic='\n'.join(x['t']); outcome='\n\n'.join(x['o']); special=x['special']
        if special and not (unit or topic or outcome):
            out[w]={'unit':'','topic':'','outcome':'','special':special}; continue
        for k,v in [('unit',unit),('topic',topic),('outcome',outcome)]:
            if v: last[k]=v
        out[w]={'unit':unit or last['unit'],'topic':topic or last['topic'],'outcome':outcome or last['outcome'],'special':None}
    return out

def find_anadolu(root, subject, contains=None):
    files=list((root/subject).glob('*.xlsx'))
    cand=[p for p in files if 'ANADOLU' in p.name.upper()]
    if contains: cand=[p for p in cand if contains.upper() in p.name.upper()]
    if not cand: raise RuntimeError(f'Anadolu XLSX bulunamadı: {subject} {contains or ""}')
    return sorted(cand,key=lambda p:(len(p.name),p.name))[0]

def make_course(sid,sname,grade,cname,records,calendar):
    pages=[]
    for base in calendar:
        p=copy.deepcopy(base)
        if p['type']=='holiday': p.update(unit='',topic='',outcome='',special=None)
        else:
            x=records[p['weekNumber']]; p.update(unit=x['unit'],topic=x['topic'],outcome=x['outcome'],special=x['special'])
        pages.append(p)
    return {'id':f'{sid}-{grade}','subjectId':sid,'subjectName':sname,'name':cname,'shortName':sname,'teachingWeekCount':37,'pages':pages}

def build_data(root):
    jp=PROJECT/'app/src/main/assets/curriculum_2026_2027.json'
    data=json.loads(jp.read_text(encoding='utf-8'))
    by_grade={g['grade']:copy.deepcopy(g['courses']) for g in data['grades']}
    calendar=copy.deepcopy(by_grade[10][0]['pages'])
    selected={
      'tde':(find_anadolu(root,'tde'), {'9':'9.SINIF','10':'10.SINIF','11':'11.SINIF','12':'12. SINIF'}),
      'physics':(find_anadolu(root,'physics'), {'9':'9. SINIF','10':'10. SINIF','11':'11. SINIF','12':'12. SINIF'}),
      'chemistry':(find_anadolu(root,'chemistry'), {'9':'9. SINIF','10':'10. SINIF','11':'11. SINIF','12':'12. SINIF'}),
      'biology':(find_anadolu(root,'biology'), {'9':'9. SINIF','10':'10. SINIF','11':'11. SINIF','12':'12. SINIF'}),
      'geography':(find_anadolu(root,'geography'), {'9':'9.SINIF ','10':'10.SINIF','11':'11. SINIF 2 SAAT','12':'12.SINIF 2 SAAT'}),
      'philosophy':(find_anadolu(root,'philosophy','FELSEFE'), {'10':'10. SINIF','11':'11. SINIF'}),
    }
    meta={
      'tde':('Türk Dili ve Edebiyatı','Türk Dili ve Edebiyatı'), 'physics':('Fizik','Fizik'),
      'chemistry':('Kimya','Kimya'), 'biology':('Biyoloji','Biyoloji'),
      'geography':('Coğrafya','Coğrafya'), 'philosophy':('Felsefe','Felsefe'),
    }
    parsed={}
    for sid,(path,sheets) in selected.items():
        raw=raw_sheets(path)
        for gs,sheet in sheets.items():
            if sheet not in raw: raise RuntimeError(f'{sid}: sayfa bulunamadı: {sheet}; var={list(raw)}')
            parsed[sid,int(gs)]=parse_sheet(raw[sheet])
    for grade in (9,10,11,12):
        for sid in ('tde','physics','chemistry','biology','geography','philosophy'):
            if (sid,grade) not in parsed: continue
            sname,cname=meta[sid]
            by_grade[grade].append(make_course(sid,sname,grade,cname,parsed[sid,grade],calendar))
    new={'schemaVersion':4,'academicYear':'2026-2027','generatedFor':'Kazanım v4','sources':[
      'MEB TYMM – Ortaöğretim Taslak Çerçeve Planları (2026–2027)',
      'MEB Din Öğretimi Genel Müdürlüğü – DKAB Çerçeve Yıllık Planları (2026–2027)',
      'MEB 2026–2027 Eğitim ve Öğretim Yılı Çalışma Takvimi'],
      'grades':[{'grade':g,'courses':by_grade[g]} for g in (9,10,11,12)]}
    jp.write_text(json.dumps(new,ensure_ascii=False,indent=2),encoding='utf-8')
    ids=[c['id'] for g in new['grades'] for c in g['courses']]
    if len(ids)!=38 or len(set(ids))!=38: raise RuntimeError(f'Beklenen 38 ders-sınıf kaydı bulunamadı: {len(ids)}')
    return new

def patch_ui():
    p=PROJECT/'app/src/main/java/com/kazanim/app/MainActivity.java'; s=p.read_text(encoding='utf-8')
    start=s.index('        LinearLayout row1 = new LinearLayout(this);'); end=s.index('        addSpace(content, 22);', start)
    block='''        List<String> subjectIds = new ArrayList<>();\n        for (Course c : allCourses) {\n            if (c.subjectId != null && !subjectIds.contains(c.subjectId)) subjectIds.add(c.subjectId);\n        }\n        for (int i = 0; i < subjectIds.size(); i += 2) {\n            LinearLayout row = new LinearLayout(this);\n            row.setOrientation(LinearLayout.HORIZONTAL);\n            row.setBaselineAligned(false);\n            String leftId = subjectIds.get(i);\n            row.addView(subjectButton(leftId, subjectTitle(leftId)), new LinearLayout.LayoutParams(0, dp(106), 1f));\n            addHorizontalSpace(row, 12);\n            if (i + 1 < subjectIds.size()) {\n                String rightId = subjectIds.get(i + 1);\n                row.addView(subjectButton(rightId, subjectTitle(rightId)), new LinearLayout.LayoutParams(0, dp(106), 1f));\n            } else {\n                Space filler = new Space(this);\n                row.addView(filler, new LinearLayout.LayoutParams(0, dp(106), 1f));\n            }\n            content.addView(row, matchWrap());\n            addSpace(content, 12);\n        }\n\n'''
    s=s[:start]+block+s[end:]
    a=s.index('    private String subjectSymbol(String subjectId) {'); b=s.index('    private void renderSubject(String subjectId) {',a)
    methods='''    private String subjectSymbol(String subjectId) {\n        if ("dkab".equals(subjectId)) return "◈";\n        if ("english".equals(subjectId)) return "A";\n        if ("history".equals(subjectId)) return "⌛";\n        if ("math".equals(subjectId)) return "∑";\n        if ("tde".equals(subjectId)) return "T";\n        if ("physics".equals(subjectId)) return "F";\n        if ("chemistry".equals(subjectId)) return "K";\n        if ("biology".equals(subjectId)) return "B";\n        if ("geography".equals(subjectId)) return "⌖";\n        if ("philosophy".equals(subjectId)) return "Φ";\n        return "•";\n    }\n\n    private String subjectTitle(String subjectId) {\n        if ("dkab".equals(subjectId)) return "Din Kültürü";\n        if ("english".equals(subjectId)) return "İngilizce";\n        if ("history".equals(subjectId)) return "Tarih";\n        if ("math".equals(subjectId)) return "Matematik";\n        if ("tde".equals(subjectId)) return "Türk Dili ve Edebiyatı";\n        if ("physics".equals(subjectId)) return "Fizik";\n        if ("chemistry".equals(subjectId)) return "Kimya";\n        if ("biology".equals(subjectId)) return "Biyoloji";\n        if ("geography".equals(subjectId)) return "Coğrafya";\n        if ("philosophy".equals(subjectId)) return "Felsefe";\n        for (Course c : allCourses) if (subjectId.equals(c.subjectId)) return c.subjectName;\n        return "Ders";\n    }\n\n'''
    s=s[:a]+methods+s[b:]
    s=s.replace('TextView badge = label("v3", 13, Color.WHITE, true);','TextView badge = label("v4", 13, Color.WHITE, true);')
    s=s.replace('Sürüm 3','Sürüm 4')
    s=s.replace('9–12. sınıflarda Din Kültürü, İngilizce, Tarih ve Matematik haftalık ünite/tema, konu ve öğrenme çıktıları.',
                'Lise derslerinde haftalık ünite/tema, konu ve öğrenme çıktıları. Din Kültürü, İngilizce, Tarih, Matematik, Türk Dili ve Edebiyatı, Fizik, Kimya, Biyoloji, Coğrafya ve Felsefe.')
    s=s.replace('İngilizce, Tarih ve Matematik:', 'İngilizce, Tarih, Matematik ve diğer lise dersleri:')
    p.write_text(s,encoding='utf-8')
    gradle=PROJECT/'app/build.gradle.kts'; g=gradle.read_text(encoding='utf-8').replace('versionCode = 3','versionCode = 4').replace('versionName = "3"','versionName = "4"'); gradle.write_text(g,encoding='utf-8')

def main():
    with tempfile.TemporaryDirectory() as td:
        root=acquire_plans(Path(td)); data=build_data(root)
    patch_ui()
    print('Kazanım v4 hazır:', PROJECT, 'course records=', sum(len(g['courses']) for g in data['grades']))

if __name__=='__main__': main()
