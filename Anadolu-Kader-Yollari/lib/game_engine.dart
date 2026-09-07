import 'dart:math' as math;
import 'event_catalog.dart';

class KaderRng {
  KaderRng(int seed) : state = seed & 0x7fffffff;
  int state;
  int nextInt(int max) {
    if (max <= 0) throw ArgumentError.value(max, 'max');
    state = (1103515245 * state + 12345) & 0x7fffffff;
    return state % max;
  }
  double nextDouble() => nextInt(1000000) / 1000000.0;
}
int clamp100(int value) => math.max(0, math.min(100, value));

class MemoryEntry {
  MemoryEntry({required this.text,required this.day,required this.importance,required this.trust,required this.respect,required this.fear,required this.affection,required this.suspicion});
  final String text; final int day,importance,trust,respect,fear,affection,suspicion;
  Map<String,dynamic> toJson()=>{'text':text,'day':day,'importance':importance,'trust':trust,'respect':respect,'fear':fear,'affection':affection,'suspicion':suspicion};
  factory MemoryEntry.fromJson(Map<String,dynamic> j)=>MemoryEntry(text:j['text'],day:j['day'],importance:j['importance'],trust:j['trust'],respect:j['respect'],fear:j['fear'],affection:j['affection'],suspicion:j['suspicion']);
}

class RelationState {
  RelationState({this.trust=50,this.respect=50,this.fear=10,this.affection=30,this.suspicion=10,this.debt=0});
  int trust,respect,fear,affection,suspicion,debt;
  void change({int trust=0,int respect=0,int fear=0,int affection=0,int suspicion=0,int debt=0}){
    this.trust=clamp100(this.trust+trust); this.respect=clamp100(this.respect+respect); this.fear=clamp100(this.fear+fear);
    this.affection=clamp100(this.affection+affection); this.suspicion=clamp100(this.suspicion+suspicion); this.debt=math.max(-100,math.min(100,this.debt+debt));
  }
  Map<String,dynamic> toJson()=>{'trust':trust,'respect':respect,'fear':fear,'affection':affection,'suspicion':suspicion,'debt':debt};
  factory RelationState.fromJson(Map<String,dynamic> j)=>RelationState(trust:j['trust'],respect:j['respect'],fear:j['fear'],affection:j['affection'],suspicion:j['suspicion'],debt:j['debt']);
}

class NpcState {
  NpcState({required this.id,required this.name,required this.cityId,required this.profession,required this.factionId,required this.goal,required this.relation,List<MemoryEntry>? memories}):memories=memories??[];
  final String id,name,profession,factionId,goal; String cityId; final RelationState relation; final List<MemoryEntry> memories;
  void remember(MemoryEntry m){memories.add(m);relation.change(trust:m.trust,respect:m.respect,fear:m.fear,affection:m.affection,suspicion:m.suspicion);}
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'cityId':cityId,'profession':profession,'factionId':factionId,'goal':goal,'relation':relation.toJson(),'memories':memories.map((m)=>m.toJson()).toList()};
  factory NpcState.fromJson(Map<String,dynamic> j)=>NpcState(id:j['id'],name:j['name'],cityId:j['cityId'],profession:j['profession'],factionId:j['factionId'],goal:j['goal'],relation:RelationState.fromJson(Map<String,dynamic>.from(j['relation'])),memories:(j['memories'] as List).map((m)=>MemoryEntry.fromJson(Map<String,dynamic>.from(m))).toList());
}

class CityState {
  CityState({required this.id,required this.name,required this.food,required this.trade,required this.order,required this.security,required this.prosperity,required this.banditry,Map<String,int>? stock}):stock=stock??{'grain':60,'cloth':60,'salt':60,'leather':60};
  final String id,name; int food,trade,order,security,prosperity,banditry; final Map<String,int> stock;
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'food':food,'trade':trade,'order':order,'security':security,'prosperity':prosperity,'banditry':banditry,'stock':stock};
  factory CityState.fromJson(Map<String,dynamic> j)=>CityState(id:j['id'],name:j['name'],food:j['food'],trade:j['trade'],order:j['order'],security:j['security'],prosperity:j['prosperity'],banditry:j['banditry'],stock:Map<String,int>.from((j['stock'] as Map?)??{'grain':60,'cloth':60,'salt':60,'leather':60}));
}

class FactionState {
  FactionState({required this.id,required this.name,required this.reputation,required this.power});
  final String id,name; int reputation,power;
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'reputation':reputation,'power':power};
  factory FactionState.fromJson(Map<String,dynamic> j)=>FactionState(id:j['id'],name:j['name'],reputation:j['reputation'],power:j['power']);
}

class KnowledgeEntry {
  KnowledgeEntry({required this.id,required this.text,required this.source,required this.reliability,required this.day,this.confirmed=false});
  final String id,text,source; final int reliability,day; bool confirmed;
  String get label {if(confirmed)return 'Kesin bilgi';if(reliability>=80)return 'Güçlü söylenti';if(reliability>=55)return 'Söylenti';if(reliability>=30)return 'Şüpheli bilgi';return 'Propaganda / çok zayıf';}
  Map<String,dynamic> toJson()=>{'id':id,'text':text,'source':source,'reliability':reliability,'day':day,'confirmed':confirmed};
  factory KnowledgeEntry.fromJson(Map<String,dynamic> j)=>KnowledgeEntry(id:j['id'],text:j['text'],source:j['source'],reliability:j['reliability'],day:j['day'],confirmed:j['confirmed']);
}

class DelayedEffect {
  DelayedEffect({required this.id,required this.dueDay,required this.type,required this.source,this.payload=const{}});
  final String id,type,source; final int dueDay; final Map<String,dynamic> payload;
  Map<String,dynamic> toJson()=>{'id':id,'dueDay':dueDay,'type':type,'source':source,'payload':payload};
  factory DelayedEffect.fromJson(Map<String,dynamic> j)=>DelayedEffect(id:j['id'],dueDay:j['dueDay'],type:j['type'],source:j['source'],payload:Map<String,dynamic>.from(j['payload']));
}

class Injury {
  Injury({required this.id,required this.name,required this.severity,required this.acquiredDay,this.permanent=false});
  final String id,name; final int severity,acquiredDay; final bool permanent;
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'severity':severity,'acquiredDay':acquiredDay,'permanent':permanent};
  factory Injury.fromJson(Map<String,dynamic> j)=>Injury(id:j['id'],name:j['name'],severity:j['severity'],acquiredDay:j['acquiredDay'],permanent:j['permanent']??false);
}

class ConflictResult {
  ConflictResult({required this.summary,required this.success,required this.escaped,required this.damage,required this.dead,this.injury});
  final String summary; final bool success,escaped,dead; final int damage; final Injury? injury;
}

class GameState {
  GameState({
    required this.version,required this.seed,required this.rngState,required this.playerName,required this.background,
    required this.day,required this.money,required this.tension,required this.currentCityId,required this.cities,
    required this.npcs,required this.factions,required this.knowledge,required this.delayedEffects,required this.chronicle,
    Map<String,int>? inventory,int? lastMajorEventDay,Map<String,int>? attributes,Map<String,int>? skills,
    int? health,int? age,int? generation,bool? alive,List<Injury>? injuries,List<String>? lineage,String? deathCause,
    Map<String,dynamic>? eventFlags,List<String>? pendingEvents,Map<String,int>? eventLastDay
  }):inventory=inventory??{},lastMajorEventDay=lastMajorEventDay??-999,
    attributes=attributes??{'strength':40,'agility':40,'intellect':40,'rhetoric':40,'intuition':40,'willpower':40},
    skills=skills??{'trade':25,'diplomacy':25,'law':20,'medicine':15,'religion':20,'military':20,'tracking':15,'espionage':10,'leadership':20,'localCulture':30},
    health=health??100,age=age??22,generation=generation??1,alive=alive??true,injuries=injuries??[],lineage=lineage??[],deathCause=deathCause??'',
    eventFlags=eventFlags??{},pendingEvents=pendingEvents??[],eventLastDay=eventLastDay??{};

  final int version,seed;
  int rngState,day,money,tension,lastMajorEventDay,health,age,generation;
  String playerName,background,currentCityId,deathCause;
  bool alive;
  final Map<String,CityState> cities; final Map<String,NpcState> npcs; final Map<String,FactionState> factions;
  final List<KnowledgeEntry> knowledge; final List<DelayedEffect> delayedEffects; final List<String> chronicle;
  final Map<String,int> inventory,attributes,skills; final List<Injury> injuries; final List<String> lineage;
  final Map<String,dynamic> eventFlags; final List<String> pendingEvents; final Map<String,int> eventLastDay;

  CityState get city=>cities[currentCityId]!;

  Map<String,dynamic> toJson()=>{
    'version':version,'seed':seed,'rngState':rngState,'playerName':playerName,'background':background,'day':day,'money':money,
    'tension':tension,'currentCityId':currentCityId,'cities':cities.map((k,v)=>MapEntry(k,v.toJson())),
    'npcs':npcs.map((k,v)=>MapEntry(k,v.toJson())),'factions':factions.map((k,v)=>MapEntry(k,v.toJson())),
    'knowledge':knowledge.map((k)=>k.toJson()).toList(),'delayedEffects':delayedEffects.map((e)=>e.toJson()).toList(),
    'chronicle':chronicle,'inventory':inventory,'lastMajorEventDay':lastMajorEventDay,'attributes':attributes,'skills':skills,
    'health':health,'age':age,'generation':generation,'alive':alive,'injuries':injuries.map((i)=>i.toJson()).toList(),
    'lineage':lineage,'deathCause':deathCause,'eventFlags':eventFlags,'pendingEvents':pendingEvents,'eventLastDay':eventLastDay
  };

  factory GameState.fromJson(Map<String,dynamic> j)=>GameState(
    version:j['version']??4,seed:j['seed'],rngState:j['rngState'],playerName:j['playerName'],background:j['background'],
    day:j['day'],money:j['money'],tension:j['tension'],currentCityId:j['currentCityId'],
    cities:(j['cities'] as Map<String,dynamic>).map((k,v)=>MapEntry(k,CityState.fromJson(Map<String,dynamic>.from(v)))),
    npcs:(j['npcs'] as Map<String,dynamic>).map((k,v)=>MapEntry(k,NpcState.fromJson(Map<String,dynamic>.from(v)))),
    factions:(j['factions'] as Map<String,dynamic>).map((k,v)=>MapEntry(k,FactionState.fromJson(Map<String,dynamic>.from(v)))),
    knowledge:(j['knowledge'] as List).map((e)=>KnowledgeEntry.fromJson(Map<String,dynamic>.from(e))).toList(),
    delayedEffects:(j['delayedEffects'] as List).map((e)=>DelayedEffect.fromJson(Map<String,dynamic>.from(e))).toList(),
    chronicle:(j['chronicle'] as List).cast<String>(),inventory:Map<String,int>.from((j['inventory'] as Map?)??{}),
    lastMajorEventDay:j['lastMajorEventDay']??-999,attributes:Map<String,int>.from((j['attributes'] as Map?)??{}),
    skills:Map<String,int>.from((j['skills'] as Map?)??{}),health:j['health']??100,age:j['age']??22,generation:j['generation']??1,
    alive:j['alive']??true,injuries:((j['injuries'] as List?)??[]).map((e)=>Injury.fromJson(Map<String,dynamic>.from(e))).toList(),
    lineage:((j['lineage'] as List?)??[]).cast<String>(),deathCause:j['deathCause']??'',
    eventFlags:Map<String,dynamic>.from((j['eventFlags'] as Map?)??{}),
    pendingEvents:((j['pendingEvents'] as List?)??[]).cast<String>(),
    eventLastDay:Map<String,int>.from((j['eventLastDay'] as Map?)??{})
  );
}

class EventOption {const EventOption(this.id,this.title,this.hint);final String id,title,hint;}
class EventView {const EventView({required this.id,required this.title,required this.body,required this.options});final String id,title,body;final List<EventOption> options;}

class GameEngine {
  GameEngine(this.state,this.catalog):_rng=KaderRng(state.rngState);
  final GameState state; final EventCatalog catalog; final KaderRng _rng;

  static GameState newGame({required int seed,required String name,required String background}){
    final cities=<String,CityState>{
      'konya':CityState(id:'konya',name:'Konya',food:66,trade:78,order:61,security:68,prosperity:72,banditry:21,stock:{'grain':70,'cloth':55,'salt':50,'leather':60}),
      'kayseri':CityState(id:'kayseri',name:'Kayseri',food:52,trade:88,order:54,security:61,prosperity:74,banditry:24,stock:{'grain':35,'cloth':65,'salt':55,'leather':75}),
      'sivas':CityState(id:'sivas',name:'Sivas',food:69,trade:70,order:62,security:64,prosperity:63,banditry:27,stock:{'grain':62,'cloth':48,'salt':60,'leather':68}),
      'ankara':CityState(id:'ankara',name:'Ankara',food:71,trade:64,order:59,security:66,prosperity:61,banditry:23,stock:{'grain':72,'cloth':70,'salt':45,'leather':50}),
      'antalya':CityState(id:'antalya',name:'Antalya',food:76,trade:91,order:65,security:70,prosperity:81,banditry:18,stock:{'grain':80,'cloth':60,'salt':90,'leather':42})};
    final factions=<String,FactionState>{
      'yonetim':FactionState(id:'yonetim',name:'Yerel yönetim',reputation:50,power:68),
      'ahi':FactionState(id:'ahi',name:'Ahi ve zanaatkârlar',reputation:50,power:58),
      'tuccar':FactionState(id:'tuccar',name:'Tüccarlar',reputation:50,power:63),
      'medrese':FactionState(id:'medrese',name:'Medrese çevresi',reputation:50,power:52)};
    final people=[
      ['mahmud','Tüccar Mahmud','kayseri','Tüccar','tuccar','Kayseri tahıl ticaretinde üstünlük kurmak'],
      ['yusuf','Ahi Yusuf','kayseri','Debbağ','ahi','Tüccarların şehir siyasetindeki ağırlığını azaltmak'],
      ['celal','Emir Celal','konya','Yönetici','yonetim','Konya çevresinde siyasi nüfuzunu korumak'],
      ['meryem','Hekim Meryem','konya','Hekim','medrese','Salgınlara karşı şehir hazırlığını artırmak'],
      ['salih','Kadı Salih','kayseri','Kadı','medrese','Şehirde hukuki meşruiyeti korumak'],
      ['yakup','Komutan Yakup','sivas','Komutan','yonetim','Doğu yollarının güvenliğini sağlamak'],
      ['hamza','Kervancı Hamza','sivas','Kervancı','tuccar','Yeni ticaret güzergâhı açmak'],
      ['ayse','Ayşe Hatun','ankara','Dokumacı','ahi','Dokumacıların fiyat birliğini korumak'],
      ['ibrahim','İbrahim Efendi','ankara','Müderris','medrese','Medreseye yeni vakıf bulmak'],
      ['sinan','Sinan Bey','ankara','Sipahi','yonetim','Vergi gelirini artırmak'],
      ['halil','Halil Tüccar','antalya','Deniz tüccarı','tuccar','Liman ticaretini büyütmek'],
      ['fatma','Fatma Usta','antalya','Zanaatkâr','ahi','Liman esnafının haklarını korumak'],
      ['nasir','Nâsır Kâtip','konya','Kâtip','yonetim','Saray çevresinde yükselmek'],
      ['rukiyye','Rukiyye Hanım','konya','Vakıf yöneticisi','medrese','Yoksullar için vakıf gelirini artırmak'],
      ['omer','Ömer Çelebi','sivas','Tüccar','tuccar','Kayseri rakiplerini geride bırakmak'],
      ['bekir','Bekir Ahi','sivas','Demirci','ahi','Yol güvenliği için yerel milis kurmak'],
      ['selma','Selma Hatun','kayseri','Han işletmecisi','tuccar','Kervan trafiğini kendi hanına çekmek'],
      ['mustafa','Mustafa Fakih','ankara','Fakih','medrese','Yerel davalarda nüfuz kazanmak'],
      ['davud','Davud Bey','antalya','Gümrük emini','yonetim','Gümrük gelirini yükseltmek'],
      ['leyla','Leyla Hanım','antalya','Tercüman','tuccar','Yabancı tüccarlarla aracılık ağını büyütmek']];
    final npcs=<String,NpcState>{};
    for(final p in people){npcs[p[0]]=NpcState(id:p[0],name:p[1],cityId:p[2],profession:p[3],factionId:p[4],goal:p[5],relation:RelationState());}
    final attrs=<String,int>{'strength':40,'agility':40,'intellect':40,'rhetoric':40,'intuition':40,'willpower':40};
    final skills=<String,int>{'trade':25,'diplomacy':25,'law':20,'medicine':15,'religion':20,'military':20,'tracking':15,'espionage':10,'leadership':20,'localCulture':30};
    if(background=='Köylü ailesi'){attrs['willpower']=50;attrs['strength']=46;skills['localCulture']=48;skills['tracking']=32;}
    if(background=='Tüccar ailesi'){attrs['rhetoric']=48;attrs['intuition']=46;skills['trade']=52;skills['diplomacy']=38;}
    if(background=='Medrese öğrencisi'){attrs['intellect']=54;attrs['willpower']=45;skills['religion']=50;skills['law']=46;}
    if(background=='Asker ailesi'){attrs['strength']=52;attrs['agility']=46;attrs['willpower']=48;skills['military']=52;skills['leadership']=36;}
    return GameState(version:5,seed:seed,rngState:seed,playerName:name,background:background,day:1,money:background=='Tüccar ailesi'?70:50,tension:20,currentCityId:'konya',cities:cities,npcs:npcs,factions:factions,knowledge:[],delayedEffects:[],chronicle:['1. gün — $name Konya’da yolculuğuna başladı.'],inventory:{},lastMajorEventDay:-999,attributes:attrs,skills:skills,health:100,age:22,generation:1,alive:true,injuries:[],lineage:[],eventFlags:{},pendingEvents:[],eventLastDay:{});
  }

  void _sync()=>state.rngState=_rng.state;

  void advance(int days){
    for(var i=0;i<days;i++){state.day++;
      for(final c in state.cities.values){if(_rng.nextDouble()<.11)c.food=clamp100(c.food-1);c.trade=clamp100(c.trade+_rng.nextInt(3)-1);if(c.food<35)c.order=clamp100(c.order-1);if(c.order<35)c.banditry=clamp100(c.banditry+1);for(final g in c.stock.keys.toList()){var change=_rng.nextInt(3)-1;if(g=='grain'&&c.food<55)change--;c.stock[g]=math.max(5,math.min(100,c.stock[g]!+change));}}
      _runNpcGoals();_runDueEffects();}
    _sync();
  }

  void _runNpcGoals(){
    if(state.day%7!=0)return;
    final acting=state.npcs.values.elementAt(_rng.nextInt(state.npcs.length));
    final faction=state.factions[acting.factionId]!;
    if(_rng.nextDouble()<.45)faction.power=clamp100(faction.power+_rng.nextInt(3)-1);
  }

  void _runDueEffects(){
    final due=state.delayedEffects.where((e)=>e.dueDay<=state.day).toList();
    for(final effect in due){
      if(effect.type=='grain_investigation'){
        final city=state.cities['kayseri']!;
        if(city.food<60){city.order=clamp100(city.order-12);state.tension=clamp100(state.tension+12);state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation+4);state.factions['tuccar']!.reputation=clamp100(state.factions['tuccar']!.reputation-5);state.chronicle.add('${state.day}. gün — Kayseri’de tahıl soruşturması büyüdü; eski kararın yeniden gündeme geldi.');state.npcs['mahmud']!.remember(MemoryEntry(text:'Tahıl soruşturmasında oyuncunun tutumu',day:state.day,importance:72,trust:-8,respect:0,fear:3,affection:-4,suspicion:12));}
      }else if(effect.type=='crisis_profiteering'){
        final city=state.cities['kayseri']!;
        if(city.food<50){state.factions['ahi']!.reputation=clamp100(state.factions['ahi']!.reputation-8);state.npcs['yusuf']!.remember(MemoryEntry(text:'Kıtlık sırasında tahıldan çıkar sağladı',day:state.day,importance:78,trust:-12,respect:-6,fear:0,affection:-8,suspicion:16));state.chronicle.add('${state.day}. gün — Kıtlıkta yaptığın alışveriş Ahi çevrelerinde konuşulmaya başladı.');}
      }else if(effect.type=='rumor_spreads'){
        state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation-2);state.chronicle.add('${state.day}. gün — Vergi söylentisi şehirler arasında yayıldı.');
      }
      state.delayedEffects.remove(effect);
    }
  }

  int travel(String cityId){
    final from=state.city.name;final destination=state.cities[cityId]!;
    final base=3+_rng.nextInt(5);final dangerPenalty=((destination.banditry+(100-destination.security))/70).floor();final days=math.max(2,base+dangerPenalty);
    state.currentCityId=cityId;advance(days);state.chronicle.add('${state.day}. gün — $from’dan ${destination.name} şehrine ulaştı.');_sync();return days;
  }


  static const goods=<String,String>{'grain':'Tahıl','cloth':'Kumaş','salt':'Tuz','leather':'Deri'};
  static const basePrices=<String,int>{'grain':7,'cloth':12,'salt':9,'leather':11};

  int marketPrice(String good,{bool buying=true}){
    final c=state.city;final stock=c.stock[good]??60;final base=basePrices[good]??10;
    final scarcity=(120-stock)/100.0;final tradeAdjustment=(70-c.trade)/220.0;
    final raw=base*(0.62+scarcity+tradeAdjustment)*(buying?1.08:.82);
    return math.max(1,raw.round());
  }

  String buyGood(String good,[int quantity=1]){
    if(!goods.containsKey(good)||quantity<1)return 'Geçersiz ticaret.';
    final unit=marketPrice(good,buying:true);final total=unit*quantity;
    if(state.money<total)return 'Yeterli akçen yok.';
    if((state.city.stock[good]??0)<quantity*2)return 'Pazarda yeterli mal yok.';
    state.money-=total;state.inventory[good]=(state.inventory[good]??0)+quantity;
    state.city.stock[good]=math.max(0,state.city.stock[good]!-quantity*2);
    state.factions['tuccar']!.reputation=clamp100(state.factions['tuccar']!.reputation+1);
    state.chronicle.add('${state.day}. gün — ${state.city.name} pazarında $quantity ${goods[good]!.toLowerCase()} satın aldı.');
    return '$quantity ${goods[good]!.toLowerCase()} için $total akçe ödedin.';
  }

  String sellGood(String good,[int quantity=1]){
    if((state.inventory[good]??0)<quantity||quantity<1)return 'Satacak kadar malın yok.';
    final unit=marketPrice(good,buying:false);final total=unit*quantity;
    state.inventory[good]=state.inventory[good]!-quantity;state.money+=total;
    state.city.stock[good]=math.min(100,(state.city.stock[good]??60)+quantity*2);
    state.chronicle.add('${state.day}. gün — ${state.city.name} pazarında $quantity ${goods[good]!.toLowerCase()} sattı.');
    return '$quantity ${goods[good]!.toLowerCase()} satarak $total akçe kazandın.';
  }


  static const attributeNames=<String,String>{'strength':'Kuvvet','agility':'Çeviklik','intellect':'Zihin','rhetoric':'Hitabet','intuition':'Sezgi','willpower':'İrade'};
  static const skillNames=<String,String>{'trade':'Ticaret','diplomacy':'Diplomasi','law':'Hukuk','medicine':'Tıp','religion':'Dinî ilimler','military':'Askerlik','tracking':'İz sürme','espionage':'Casusluk','leadership':'Liderlik','localCulture':'Yerel kültür'};

  int checkScore(String attribute,String skill){
    final a=state.attributes[attribute]??40,s=state.skills[skill]??20;
    final score=(a*.45+s*.35+_rng.nextInt(41)*.50).round();
    _sync();return score;
  }

  ConflictResult resolveConflict(String tactic){
    if(!state.alive)return ConflictResult(summary:'Ölü bir karakter çatışmaya giremez.',success:false,escaped:false,damage:0,dead:true);
    String attr='willpower',skill='military',label='Tedbirli savunma';int modifier=0;
    if(tactic=='assault'){attr='strength';skill='military';label='Hızlı saldırı';modifier=3;}
    if(tactic=='flee'){attr='agility';skill='tracking';label='Geri çekilme';modifier=-2;}
    if(tactic=='parley'){attr='rhetoric';skill='diplomacy';label='Konuşarak çözme';modifier=-4;}
    final difficulty=42+(state.city.banditry~/3)+((100-state.city.security)~/5);
    final score=checkScore(attr,skill)+modifier;
    final margin=score-difficulty;
    int damage=0;bool success=false,escaped=false;Injury? injury;
    if(margin>=15){success=true;damage=_rng.nextInt(7);}
    else if(margin>=0){success=true;damage=7+_rng.nextInt(12);}
    else if(margin>-15){escaped=tactic=='flee'||_rng.nextInt(100)<55;damage=16+_rng.nextInt(20);}
    else{damage=34+_rng.nextInt(34);}
    if(damage>=14){injury=_makeInjury(damage);}
    _applyDamage(damage,cause:'Yol çatışması',injury:injury);
    if(success){state.skills[skill]=clamp100((state.skills[skill]??0)+1);state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation+1);}
    state.chronicle.add('${state.day}. gün — $label: skor $score / güçlük $difficulty, hasar $damage.');
    _sync();
    final summary=state.alive
      ? success?'$label başarılı oldu. $damage hasar aldın.':escaped?'Çatışmadan sıyrıldın; $damage hasar aldın.':'Taktik başarısız oldu; $damage hasar aldın.'
      :'Çatışma ölümle sonuçlandı: ${state.deathCause}.';
    return ConflictResult(summary:summary,success:success,escaped:escaped,damage:damage,dead:!state.alive,injury:injury);
  }

  Injury _makeInjury(int damage){
    final names=damage>=45?['Ağır göğüs yarası','Kafatası travması','Derin bıçak yarası']:damage>=28?['Kırık kol','Omuz çıkığı','Derin kesik']:['Kaburga ezilmesi','Bilek burkulması','Yüzeysel kesik'];
    final name=names[_rng.nextInt(names.length)];
    final severity=math.max(1,math.min(100,damage+_rng.nextInt(16)));
    final injury=Injury(id:'injury_${state.day}_${state.injuries.length}',name:name,severity:severity,acquiredDay:state.day,permanent:severity>=72);
    state.injuries.add(injury);
    return injury;
  }

  void _applyDamage(int damage,{required String cause,Injury? injury}){
    state.health=math.max(0,state.health-damage);
    if(state.health<=0)_die(cause);
  }

  void forceDamageForTest(int damage,{String cause='Ağır yaralanma'}){final injury=damage>=14?_makeInjury(damage):null;_applyDamage(damage,cause:cause,injury:injury);_sync();}

  void _die(String cause){
    if(!state.alive)return;
    state.alive=false;state.deathCause=cause;state.health=0;
    state.chronicle.add('${state.day}. gün — ${state.playerName} öldü. Sebep: $cause.');
  }

  List<String> successorOptions(){
    final pool=['Ali','Zeynep','Mehmed','Ayşe','Yusuf','Meryem','Hasan','Fatma','Ömer','Selma'];
    final start=(state.seed+state.generation*3)%pool.length;
    return List.generate(3,(i)=>pool[(start+i*2)%pool.length]);
  }

  String assumeSuccessor(String name){
    if(state.alive)return 'Halef yalnız mevcut karakter öldüğünde seçilebilir.';
    final old=state.playerName;
    state.lineage.add(old);state.playerName=name;state.background='Aile mirasçısı';state.generation++;state.age=18;state.health=100;state.alive=true;state.deathCause='';state.injuries.clear();
    state.money=(state.money*.8).round();
    for(final key in state.inventory.keys.toList()){state.inventory[key]=(state.inventory[key]!*.75).floor();}
    for(final key in state.attributes.keys.toList()){state.attributes[key]=clamp100((state.attributes[key]!*0.68+22).round());}
    for(final key in state.skills.keys.toList()){state.skills[key]=clamp100((state.skills[key]!*0.62+18).round());}
    for(final f in state.factions.values){f.reputation=((f.reputation+50)/2).round();}
    for(final n in state.npcs.values){
      n.relation.trust=((n.relation.trust+50)/2).round();n.relation.respect=((n.relation.respect+50)/2).round();
      n.relation.affection=((n.relation.affection+30)/2).round();n.relation.suspicion=((n.relation.suspicion+10)/2).round();
      n.memories.add(MemoryEntry(text:'$old karakterinin mirasçısı olarak geldi',day:state.day,importance:45,trust:0,respect:0,fear:0,affection:0,suspicion:0));
    }
    state.chronicle.add('${state.day}. gün — $name, $old karakterinin mirasını devraldı. Dünya kaldığı yerden devam ediyor.');
    return '$name ile ${state.generation}. nesil başladı.';
  }

  EventView pickEvent(){
    final c=state.city;final sinceMajor=state.day-state.lastMajorEventDay;
    if(c.id=='kayseri'&&c.food<40)return const EventView(id:'grain',title:'Kayseri’de Tahıl Meselesi',body:'Tahıl fiyatları yükseliyor. Esnaf, Tüccar Mahmud’un zahire depoladığını söylüyor; fakat söylentinin önemli kısmı rakiplerinden geliyor.',options:[EventOption('talk','Mahmud’u dinle','Bilgi kazanırsın; onun anlatısına da maruz kalırsın.'),EventOption('judge','Kadıyı haberdar et','Meşru yol; gecikmiş siyasi sonucu olabilir.'),EventOption('buy','15 akçelik tahıl al','Ekonomik fırsat; ahlaki ve sosyal bedeli belirsiz.'),EventOption('ignore','Karışma','Tarafsızlık da dünyanın gidişini değiştirebilir.')]);
    if(sinceMajor>=10&&c.id=='kayseri'&&c.food<60)return const EventView(id:'grain',title:'Kayseri’de Tahıl Meselesi',body:'Tahıl fiyatları yükseliyor. Esnaf, Tüccar Mahmud’un zahire depoladığını söylüyor; fakat söylentinin önemli kısmı rakiplerinden geliyor.',options:[EventOption('talk','Mahmud’u dinle','Bilgi kazanırsın; onun anlatısına da maruz kalırsın.'),EventOption('judge','Kadıyı haberdar et','Meşru yol; gecikmiş siyasi sonucu olabilir.'),EventOption('buy','15 akçelik tahıl al','Ekonomik fırsat; ahlaki ve sosyal bedeli belirsiz.'),EventOption('ignore','Karışma','Tarafsızlık da dünyanın gidişini değiştirebilir.')]);
    if(sinceMajor>=8&&state.tension>55)return const EventView(id:'faction_dispute',title:'Han Avlusunda Tartışma',body:'Bir Ahi ustasıyla vergi memuru sert biçimde tartışıyor. İki taraf da seni tanıyor; sessiz kalman bile yorumlanabilir.',options:[EventOption('ahi','Ahi ustasını destekle','Esnaf seni hatırlayacak.'),EventOption('official','Memuru destekle','Yönetim desteğini not edecek.'),EventOption('mediate','Arabuluculuk et','Başarısı ilişkilerine bağlı.'),EventOption('leave','Uzaklaş','Taraflar bunu çekingenlik sayabilir.')]);
    return const EventView(id:'rumor',title:'Handaki Fısıltılar',body:'Yan masadaki iki yolcu yaklaşan yeni vergilerden söz ediyor. Birinin sarhoş olduğu açık; diğerinin kaynağını bilmiyorsun.',options:[EventOption('listen','Dinlemeye devam et','Kaynağı belirsiz bir bilgi edinebilirsin.'),EventOption('verify','Başka bir kaynak ara','Daha fazla zaman karşılığında güveni yükseltebilirsin.'),EventOption('ignore','Önemseme','Yanlış bilgiden korunursun; gerçek uyarıyı kaçırabilirsin.')]);
  }

  String resolve(EventView e,String choice){if(e.id!='rumor')state.lastMajorEventDay=state.day;if(e.id=='grain')return _grain(choice);if(e.id=='rumor')return _rumor(choice);if(e.id=='faction_dispute')return _dispute(choice);return 'Sonuç oluşmadı.';}

  String _grain(String c){
    final mahmud=state.npcs['mahmud']!;
    if(c=='talk'){mahmud.remember(MemoryEntry(text:'Tahıl krizinde beni dinledi',day:state.day,importance:38,trust:7,respect:2,fear:0,affection:2,suspicion:-2));state.knowledge.add(KnowledgeEntry(id:'mahmud_claim_${state.day}',text:'Mahmud, fiyat artışının yol güvenliğinden kaynaklandığını söylüyor.',source:'Tüccar Mahmud',reliability:58,day:state.day));state.tension=clamp100(state.tension-3);return 'Mahmud fiyat artışını yol güvenliğine bağlıyor. Söylediği mümkün; fakat doğrulanmış değil.';}
    if(c=='judge'){state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation+4);mahmud.remember(MemoryEntry(text:'Tahıl meselesini kadıya taşıdı',day:state.day,importance:67,trust:-7,respect:3,fear:3,affection:-4,suspicion:10));state.delayedEffects.add(DelayedEffect(id:'grain_investigation_${state.day}',dueDay:state.day+30+_rng.nextInt(31),type:'grain_investigation',source:'grain'));state.tension=clamp100(state.tension+7);_sync();return 'Kadıya haber verdin. Mahmud bunu unutmayacak; soruşturmanın sonucu daha sonra ortaya çıkabilir.';}
    if(c=='buy'){if(state.money<15)return 'Yeterli akçen yok.';state.money-=15;state.city.food=clamp100(state.city.food-3);mahmud.relation.change(trust:5,debt:3);state.factions['tuccar']!.reputation=clamp100(state.factions['tuccar']!.reputation+3);state.delayedEffects.add(DelayedEffect(id:'crisis_profit_${state.day}',dueDay:state.day+45+_rng.nextInt(46),type:'crisis_profiteering',source:'grain'));_sync();return 'Tahıl aldın. Mahmud memnun; ancak kriz ağırlaşırsa bu alışveriş başka çevrelerde farklı hatırlanabilir.';}
    state.city.food=clamp100(state.city.food-5);state.npcs['yusuf']!.relation.change(suspicion:2);return 'Karışmadın. Şehirdeki gıda baskısı kendi başına ilerliyor.';
  }

  String _rumor(String c){
    if(c=='listen'){final rel=24+_rng.nextInt(43);state.knowledge.add(KnowledgeEntry(id:'tax_rumor_${state.day}_${state.knowledge.length}',text:'Yakında yeni bir vergi konulacağı söyleniyor.',source:'Handaki yolcular',reliability:rel,day:state.day));state.delayedEffects.add(DelayedEffect(id:'rumor_spreads_${state.day}',dueDay:state.day+10+_rng.nextInt(15),type:'rumor_spreads',source:'rumor'));_sync();return 'Söylentiyi not ettin. Güvenilirliği düşük; gerçek bilgi olarak kabul edilmemeli.';}
    if(c=='verify'){advance(1);final rel=64+_rng.nextInt(25);state.knowledge.add(KnowledgeEntry(id:'tax_check_${state.day}_${state.knowledge.length}',text:'Vergi düzenlemesi ihtimali bazı kâtipler arasında da konuşuluyor.',source:'Yerel kâtip çevresi',reliability:rel,day:state.day));_sync();return 'Bir gün harcayıp ikinci kaynak buldun. Bilgi daha güçlü, yine de kesin değil.';}
    return 'Söylentiyi kayda almadın.';
  }

  String _dispute(String c){
    final ahi=state.factions['ahi']!,gov=state.factions['yonetim']!;
    if(c=='ahi'){ahi.reputation=clamp100(ahi.reputation+8);gov.reputation=clamp100(gov.reputation-6);state.npcs['yusuf']!.remember(MemoryEntry(text:'Vergi tartışmasında Ahileri destekledi',day:state.day,importance:54,trust:9,respect:6,fear:0,affection:3,suspicion:-4));return 'Ahi ustasını destekledin. Esnaf çevresi memnun; yönetim bunu taraf seçmek olarak görüyor.';}
    if(c=='official'){gov.reputation=clamp100(gov.reputation+8);ahi.reputation=clamp100(ahi.reputation-7);state.npcs['celal']!.relation.change(trust:5,respect:4);return 'Memuru destekledin. Yönetim çevresi bunu olumlu kaydetti; Ahiler rahatsız.';}
    if(c=='mediate'){final ok=state.npcs['yusuf']!.relation.trust+_rng.nextInt(60)>75;_sync();if(ok){ahi.reputation=clamp100(ahi.reputation+4);gov.reputation=clamp100(gov.reputation+4);state.tension=clamp100(state.tension-8);return 'Tarafları geçici olarak uzlaştırdın. İki çevrede de saygınlığın arttı.';}state.tension=clamp100(state.tension+5);return 'Arabuluculuğun sonuç vermedi. İki taraf da seni diğerine fazla yakın buldu.';}
    state.npcs['yusuf']!.relation.change(respect:-2);state.npcs['celal']!.relation.change(respect:-2);return 'Uzaklaştın. Kriz senden bağımsız devam ediyor.';
  }
}
