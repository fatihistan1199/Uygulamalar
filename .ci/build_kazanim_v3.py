#!/usr/bin/env python3
import copy, json, os, re, shutil, subprocess, sys, tempfile, unicodedata, urllib.request, zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

PROJECT = Path(sys.argv[1] if len(sys.argv) > 1 else 'buildsrc/Kazanim_v2').resolve()
NS_MAIN='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
NS_REL='http://schemas.openxmlformats.org/officeDocument/2006/relationships'

def patch_sources():
    (PROJECT/'app/src/main/java/com/kazanim/app/model/Course.java').write_text('''package com.kazanim.app.model;

import java.util.ArrayList;
import java.util.List;

public class Course {
    public String id;
    public String subjectId;
    public String subjectName;
    public String name;
    public String shortName;
    public int grade;
    public int teachingWeekCount;
    public final List<PlanPage> pages = new ArrayList<>();
}
''', encoding='utf-8')

    repo=PROJECT/'app/src/main/java/com/kazanim/app/data/PlanRepository.java'
    s=repo.read_text(encoding='utf-8')
    s=s.replace('course.id = c.getString("id");\n                course.name = c.getString("name");',
'''course.id = c.getString("id");
                course.subjectId = c.optString("subjectId", course.id.contains("-") ? course.id.substring(0, course.id.indexOf('-')) : course.id);
                course.subjectName = c.optString("subjectName", c.getString("name"));
                course.name = c.getString("name");''')
    repo.write_text(s,encoding='utf-8')

    gradle=PROJECT/'app/build.gradle.kts'
    s=gradle.read_text(encoding='utf-8').replace('versionCode = 2','versionCode = 3').replace('versionName = "2"','versionName = "3"')
    gradle.write_text(s,encoding='utf-8')

    p=PROJECT/'app/src/main/java/com/kazanim/app/MainActivity.java'
    s=p.read_text(encoding='utf-8')
    s=s.replace('    private Course course;\n    private LinearLayout screenRoot;', '    private Course course;\n    private String selectedSubjectId;\n    private LinearLayout screenRoot;')
    s=s.replace('    private enum Screen { HOME, FAVORITES, COURSE }', '    private enum Screen { HOME, SUBJECT, FAVORITES, COURSE }')
    s=s.replace('''    @Override
    public void onBackPressed() {
        if (currentScreen == Screen.COURSE || currentScreen == Screen.FAVORITES) {
            renderHome();
        } else {
            super.onBackPressed();
        }
    }''','''    @Override
    public void onBackPressed() {
        if (currentScreen == Screen.COURSE && course != null) {
            renderSubject(course.subjectId);
        } else if (currentScreen == Screen.SUBJECT || currentScreen == Screen.FAVORITES) {
            renderHome();
        } else {
            super.onBackPressed();
        }
    }''')

    start=s.index('    private void renderHome() {')
    end=s.index('    private void renderFavorites() {')
    home='''    private void renderHome() {
        currentScreen = Screen.HOME;
        selectedSubjectId = null;
        screenRoot = verticalRoot();
        screenRoot.addView(topBar("Kazanım"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content, matchWrap());

        content.addView(label("2026–2027 Eğitim Öğretim Yılı", 14, TEXT_SECONDARY, false));
        addSpace(content, 8);
        content.addView(label("Ders Seç", 24, TEXT, true));
        addSpace(content, 14);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setBaselineAligned(false);
        row1.addView(subjectButton("dkab", "Din Kültürü"), new LinearLayout.LayoutParams(0, dp(108), 1f));
        addHorizontalSpace(row1, 12);
        row1.addView(subjectButton("english", "İngilizce"), new LinearLayout.LayoutParams(0, dp(108), 1f));
        content.addView(row1, matchWrap());
        addSpace(content, 12);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setBaselineAligned(false);
        row2.addView(subjectButton("history", "Tarih"), new LinearLayout.LayoutParams(0, dp(108), 1f));
        addHorizontalSpace(row2, 12);
        row2.addView(subjectButton("math", "Matematik"), new LinearLayout.LayoutParams(0, dp(108), 1f));
        content.addView(row2, matchWrap());

        addSpace(content, 22);
        TextView info = label("Dersi seçin, ardından 9–12. sınıflardan birini açın. Yıldızla işaretlediğiniz ders-sınıf kombinasyonları Favoriler'de hızlı erişim için saklanır.", 13, TEXT_SECONDARY, false);
        info.setLineSpacing(0, 1.15f);
        content.addView(info);

        screenRoot.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        screenRoot.addView(bottomNav(Screen.HOME));
        setContentView(screenRoot);
    }

    private View subjectButton(String subjectId, String title) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(14), dp(12), dp(14));
        button.setBackground(roundRect(SURFACE, 20, DIVIDER, 1));
        applyElevation(button, dp(2));
        TextView icon = label(subjectSymbol(subjectId), 28, PRIMARY_DARK, true);
        icon.setGravity(Gravity.CENTER);
        button.addView(icon);
        addSpace(button, 7);
        TextView name = label(title, 17, TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        button.addView(name);
        button.setOnClickListener(v -> renderSubject(subjectId));
        return button;
    }

    private String subjectSymbol(String subjectId) {
        if ("dkab".equals(subjectId)) return "◈";
        if ("english".equals(subjectId)) return "A";
        if ("history".equals(subjectId)) return "⌛";
        if ("math".equals(subjectId)) return "∑";
        return "•";
    }

    private String subjectTitle(String subjectId) {
        if ("dkab".equals(subjectId)) return "Din Kültürü";
        if ("english".equals(subjectId)) return "İngilizce";
        if ("history".equals(subjectId)) return "Tarih";
        if ("math".equals(subjectId)) return "Matematik";
        return "Ders";
    }

    private void renderSubject(String subjectId) {
        currentScreen = Screen.SUBJECT;
        selectedSubjectId = subjectId;
        screenRoot = verticalRoot();
        screenRoot.addView(subjectTopBar(subjectTitle(subjectId)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content, matchWrap());
        content.addView(label("Sınıf Seç", 24, TEXT, true));
        addSpace(content, 6);
        content.addView(label("Yıldız simgesiyle sınıfı favorilere ekleyebilirsiniz.", 13, TEXT_SECONDARY, false));
        addSpace(content, 14);
        for (Course c : allCourses) {
            if (subjectId.equals(c.subjectId)) {
                content.addView(courseCard(c, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                addSpace(content, 12);
            }
        }
        screenRoot.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        screenRoot.addView(bottomNav(Screen.SUBJECT));
        setContentView(screenRoot);
    }

    private View subjectTopBar(String titleText) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(12), 0);
        bar.setBackgroundColor(PRIMARY);
        bar.setMinimumHeight(dp(66));
        TextView back = label("‹", 42, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> renderHome());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(58)));
        TextView title = label(titleText, 23, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(66), 1f));
        TextView badge = label("v3", 13, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(Color.argb(35, 255, 255, 255), 12, Color.TRANSPARENT, 0));
        bar.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(34)));
        return bar;
    }

'''
    s=s[:start]+home+s[end:]
    s=s.replace('Ana sayfadaki yıldız simgesine dokunarak dersleri favorilere ekleyebilirsiniz.','Ders seçip sınıf ekranındaki yıldız simgesine dokunarak favorilere ekleyebilirsiniz.')
    old='''        TextView open = label("Haftalık kazanımları aç  ›", 14, PRIMARY_DARK, true);
        open.setGravity(Gravity.END);
        open.setPadding(0, dp(7), dp(5), 0);
        card.addView(open);

        View.OnClickListener go = v -> renderCourse(c);
        card.setOnClickListener(go);
        open.setOnClickListener(go);
        return card;'''
    s=s.replace(old,'''        View.OnClickListener go = v -> renderCourse(c);
        card.setOnClickListener(go);
        return card;''')
    s=s.replace('''    private void renderCourse(Course selectedCourse) {
        course = selectedCourse;''','''    private void renderCourse(Course selectedCourse) {
        course = selectedCourse;
        selectedSubjectId = selectedCourse.subjectId;''')
    pos=s.index('    private View courseTopBar() {')
    tail=s[pos:].replace('back.setOnClickListener(v -> renderHome());','back.setOnClickListener(v -> renderSubject(course.subjectId));',1)
    s=s[:pos]+tail
    a=s.index('    private void showSources() {'); b=s.index('    private void showAbout() {')
    sources='''    private void showSources() {
        new AlertDialog.Builder(this)
                .setTitle("Kaynaklar")
                .setMessage("2026–2027 haftalık dağılımlar:\\nMillî Eğitim Bakanlığı resmî çerçeve yıllık planları.\\n\\nDin Kültürü ve Ahlak Bilgisi:\\nMEB Din Öğretimi Genel Müdürlüğü ve Türkiye Yüzyılı Maarif Modeli.\\n\\nİngilizce, Tarih ve Matematik:\\nMEB Türkiye Yüzyılı Maarif Modeli – Ortaöğretim Taslak Çerçeve Planları (2026–2027).\\n\\n12. sınıflarda 2026–2027 için resmî planlarda yer alan yürürlükteki program kullanılmıştır.\\n\\nUygulama verileri çevrimdışı olarak cihazda tutulur.")
                .setPositiveButton("Tamam", null).show();
    }

'''
    s=s[:a]+sources+s[b:]
    a=s.index('    private void showAbout() {'); b=s.index('    private boolean isFavorite(Course c) {')
    about='''    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Kazanım")
                .setMessage("Sürüm 3\\n\\n9–12. sınıflarda Din Kültürü, İngilizce, Tarih ve Matematik haftalık ünite/tema, konu ve öğrenme çıktıları.\\n\\nİnternet bağlantısı gerektirmez.")
                .setPositiveButton("Tamam", null).show();
    }

'''
    s=s[:a]+about+s[b:]
    s=s.replace('TextView badge = label("v2", 13, Color.WHITE, true);','TextView badge = label("v3", 13, Color.WHITE, true);')
    marker='    private void addSpace(LinearLayout parent, int dp) {'
    s=s.replace(marker,'    private void addHorizontalSpace(LinearLayout parent, int dp) {\n        Space s = new Space(this);\n        parent.addView(s, new LinearLayout.LayoutParams(dp(dp), 1));\n    }\n\n'+marker)
    p.write_text(s,encoding='utf-8')

def download(url,path):
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
    with urllib.request.urlopen(req,timeout=90) as r, open(path,'wb') as f: shutil.copyfileobj(r,f)

def choose_xlsx(folder,needle):
    xs=[p for p in Path(folder).rglob('*.xlsx') if needle.upper() in unicodedata.normalize('NFC',p.name).upper()]
    if not xs: raise RuntimeError('XLSX bulunamadı: '+needle)
    xs.sort(key=lambda p:(len(str(p)),str(p)))
    return xs[0]

def acquire(tmp):
    em,eh,ee=os.environ.get('V3_MATH_XLSX'),os.environ.get('V3_HISTORY_XLSX'),os.environ.get('V3_ENGLISH_XLSX')
    if em and eh and ee: return Path(em),Path(eh),Path(ee)
    base='https://tymm.meb.gov.tr/assets/file/'
    mz=tmp/'m.zip'; hz=tmp/'h.zip'; er=tmp/'e.rar'
    download(base+'matematik-dersi-taslak-yillik-planlar_20260827_083834_941.zip',mz)
    download(base+'tarih-dersi-taslak-yillik-planlar_20260827_083900_546.zip',hz)
    download(base+'ingilizce-dersi-taslak-yillik-planlar_20260827_111530_896.rar',er)
    md,hd,ed=tmp/'m',tmp/'h',tmp/'e'; md.mkdir();hd.mkdir();ed.mkdir()
    zipfile.ZipFile(mz).extractall(md); zipfile.ZipFile(hz).extractall(hd)
    q=subprocess.run(['unrar-free','-x',str(er),str(ed)+'/'])
    if q.returncode: q=subprocess.run(['unrar-free','--extract',str(er),str(ed)+'/'])
    if q.returncode: raise RuntimeError('İngilizce RAR çıkarılamadı')
    return choose_xlsx(md,'ANADOLU'),choose_xlsx(hd,'ANADOLU'),choose_xlsx(ed,'HAZIRLIK SINIFI BULUNMAYAN')

def raw_sheets(path):
    z=zipfile.ZipFile(path); shared=[]
    if 'xl/sharedStrings.xml' in z.namelist():
        root=ET.fromstring(z.read('xl/sharedStrings.xml'))
        shared=[''.join((t.text or '') for t in si.iter(f'{{{NS_MAIN}}}t')) for si in root.findall(f'{{{NS_MAIN}}}si')]
    wr=ET.fromstring(z.read('xl/workbook.xml')); rr=ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
    rel={r.attrib['Id']:r.attrib['Target'] for r in rr}; out={}
    for sh in wr.find(f'{{{NS_MAIN}}}sheets'):
        name=sh.attrib['name']; target=rel[sh.attrib[f'{{{NS_REL}}}id']]; sp=target.lstrip('/') if target.startswith('/') else 'xl/'+target.lstrip('/')
        root=ET.fromstring(z.read(sp)); vals={}; mr=mc=0
        for c in root.iter(f'{{{NS_MAIN}}}c'):
            m=re.match(r'([A-Z]+)(\d+)',c.attrib.get('r',''))
            if not m: continue
            letters,rs=m.groups(); row=int(rs); col=0
            for ch in letters: col=col*26+ord(ch)-64
            typ=c.attrib.get('t'); v=c.find(f'{{{NS_MAIN}}}v'); ins=c.find(f'{{{NS_MAIN}}}is'); value=None
            if typ=='s' and v is not None: value=shared[int(v.text)]
            elif typ=='inlineStr' and ins is not None: value=''.join((t.text or '') for t in ins.iter(f'{{{NS_MAIN}}}t'))
            elif v is not None: value=v.text
            if value is not None: vals[(row,col)]=value; mr=max(mr,row);mc=max(mc,col)
        out[name]=(vals,mr,mc)
    return out

def clean(x,multi=False):
    if x is None:return ''
    s=unicodedata.normalize('NFC',str(x)).replace('\uf020',' ').replace('\u00a0',' ').replace('\r','\n')
    if multi:return '\n'.join(re.sub(r'[ \t]+',' ',x).strip() for x in s.split('\n') if x.strip())
    return re.sub(r'\s+',' ',s).strip()

def parse_sheet(sh):
    vals,mr,mc=sh; data=defaultdict(lambda:{'u':[],'t':[],'o':[],'s':[]}); cur=None
    for r in range(1,mr+1):
        row=[vals.get((r,c)) for c in range(1,7)]; joined=' '.join(clean(x) for x in row).upper()
        if 'ARA TATİL' in joined or 'YARIYIL TATİL' in joined: cur=None; continue
        m=re.search(r'(\d+)\s*\.\s*Hafta',clean(row[1]),re.I)
        if m: cur=int(m.group(1))
        if not cur or cur>37: continue
        c3,u,t,o=clean(row[2]),clean(row[3]),clean(row[4]),clean(row[5],True)
        sp='Okul Temelli Planlama' if 'OKUL TEMELL' in c3.upper() else ('Sosyal Etkinlik' if ('SOSYAL ETK' in c3.upper() or 'SOSYAL AKT' in c3.upper()) else '')
        if sp and sp not in data[cur]['s']:data[cur]['s'].append(sp)
        for k,v in [('u',u),('t',t),('o',o)]:
            if v and v not in data[cur][k]:data[cur][k].append(v)
    out={w:{'unit':'\n'.join(data[w]['u']),'topic':'\n'.join(data[w]['t']),'outcome':'\n\n'.join(data[w]['o']),'specials':data[w]['s']} for w in range(1,38)}
    last={'unit':'','topic':'','outcome':''}
    for w in range(1,38):
        x=out[w]; special=bool(x['specials']) and not (x['unit'] or x['topic'] or x['outcome'])
        if not special:
            for k in last:
                if x[k]:last[k]=x[k]
                else:x[k]=last[k]
    return out

def make_course(sid,sname,grade,name,short,records,calendar):
    pages=[]
    for base in calendar:
        p=copy.deepcopy(base)
        if p['type']=='holiday':p.update(unit='',topic='',outcome='',special=None)
        else:
            x=records[p['weekNumber']];p['unit']=x['unit'];p['topic']=x['topic'];p['outcome']=x['outcome'];p['special']=x['specials'][0] if x['specials'] and not (x['unit'] or x['topic'] or x['outcome']) else None
        pages.append(p)
    return {'id':f'{sid}-{grade}','subjectId':sid,'subjectName':sname,'name':name,'shortName':short,'teachingWeekCount':37,'pages':pages}

def generate(mathf,histf,engf):
    jp=PROJECT/'app/src/main/assets/curriculum_2026_2027.json';old=json.loads(jp.read_text(encoding='utf-8'));dk={g['grade']:copy.deepcopy(g['courses'][0]) for g in old['grades']}
    for c in dk.values():c['subjectId']='dkab';c['subjectName']='Din Kültürü ve Ahlak Bilgisi'
    cal=copy.deepcopy(dk[10]['pages']);rm,rh,re_=raw_sheets(mathf),raw_sheets(histf),raw_sheets(engf);rec={}
    for g,n in [(9,'9. SINIF'),(10,'10. SINIF'),(11,'11. SINIF'),(12,'12. SINIF')]:rec['m',g]=parse_sheet(rm[n]);rec['e',g]=parse_sheet(re_[n])
    for g,n in [(9,'9. SINIF TARİH'),(10,'10. SINIF TARİH'),(11,'11. SINIF TARİH'),(12,'12. SINIF T.C. İNKILAP TAR.')]:rec['h',g]=parse_sheet(rh[n])
    grades=[]
    for g in (9,10,11,12):
        hn='Tarih' if g<12 else 'T.C. İnkılap Tarihi ve Atatürkçülük'
        grades.append({'grade':g,'courses':[dk[g],make_course('english','İngilizce',g,'İngilizce','İngilizce',rec['e',g],cal),make_course('history','Tarih',g,hn,'Tarih',rec['h',g],cal),make_course('math','Matematik',g,'Matematik','Matematik',rec['m',g],cal)]})
    new={'schemaVersion':3,'academicYear':'2026-2027','generatedFor':'Kazanım v3','sources':['MEB TYMM – Ortaöğretim Taslak Çerçeve Planları (2026–2027)','MEB Din Öğretimi Genel Müdürlüğü – DKAB Çerçeve Yıllık Planları (2026–2027)','MEB 2026–2027 Eğitim ve Öğretim Yılı Çalışma Takvimi'],'grades':grades}
    jp.write_text(json.dumps(new,ensure_ascii=False,indent=2),encoding='utf-8')
    ids=[c['id'] for g in grades for c in g['courses']]
    assert len(ids)==16 and len(set(ids))==16
    assert all(len(c['pages'])==40 and len([p for p in c['pages'] if p['type']=='week'])==37 for g in grades for c in g['courses'])

def main():
    patch_sources()
    with tempfile.TemporaryDirectory() as td:
        m,h,e=acquire(Path(td));generate(m,h,e)
    print('Kazanım v3 hazır:',PROJECT)

if __name__=='__main__':main()
