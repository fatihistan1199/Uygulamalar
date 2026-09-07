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
      if(effect.type=='event_followup'){
        final eventId=effect.payload['eventId'] as String?;
        if(eventId!=null&&!state.pendingEvents.contains(eventId))state.pendingEvents.add(eventId);
      }else if(effect.type=='grain_investigation'){
        final city=state.cities['kayseri']!;
        if(city.food<60){
          city.order=clamp100(city.order-12);
          state.tension=clamp100(state.tension+12);
          state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation+4);
          state.factions['tuccar']!.reputation=clamp100(state.factions['tuccar']!.reputation-5);
        }
      }else if(effect.type=='crisis_profiteering'){
        final city=state.cities['kayseri']!;
        if(city.food<50)state.factions['ahi']!.reputation=clamp100(state.factions['ahi']!.reputation-8);
      }else if(effect.type=='rumor_spreads'){
        state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation-2);
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
    for(final id in List<String>.from(state.pendingEvents)){
      final def=catalog[id];
      if(def!=null&&_conditionsMet(def.conditions)){
        final view=_toView(def);
        if(view.options.isNotEmpty)return view;
      }
    }

    final candidates=catalog.events.values.where((def){
      if(!_eventEligible(def))return false;
      return _toView(def).options.isNotEmpty;
    }).toList()..sort((a,b)=>a.id.compareTo(b.id));

    EventDefinition chosen;
    if(candidates.isEmpty){
      chosen=catalog['rumor']??catalog.events.values.first;
    }else{
      final total=candidates.fold<int>(0,(sum,e)=>sum+math.max(1,e.weight));
      var roll=_rng.nextInt(total);
      chosen=candidates.last;
      for(final event in candidates){
        roll-=math.max(1,event.weight);
        if(roll<0){chosen=event;break;}
      }
      _sync();
    }
    return _toView(chosen);
  }

  bool _eventEligible(EventDefinition def){
    if(!_conditionsMet(def.conditions))return false;
    final last=state.eventLastDay[def.id];
    if(last!=null&&state.day-last<def.cooldownDays)return false;
    if(def.major&&!def.emergency&&state.day-state.lastMajorEventDay<8)return false;
    return true;
  }

  EventView _toView(EventDefinition def){
    final options=def.choices.where((choice)=>_conditionsMet(choice.requirements)).map(
      (choice)=>EventOption(choice.id,choice.title,choice.hint)
    ).toList();
    return EventView(id:def.id,title:def.title,body:def.body,options:options);
  }

  bool _conditionsMet(List<Map<String,dynamic>> conditions){
    for(final condition in conditions){
      final type=condition['type'] as String? ?? '';
      final value=condition['value'];
      switch(type){
        case 'city_is':
          if(state.currentCityId!=value)return false;
        case 'city_stat_below':
          if(_cityStat(condition['stat'] as String)>=((value as num).toInt()))return false;
        case 'city_stat_above':
          if(_cityStat(condition['stat'] as String)<=((value as num).toInt()))return false;
        case 'tension_above':
          if(state.tension<=((value as num).toInt()))return false;
        case 'tension_below':
          if(state.tension>=((value as num).toInt()))return false;
        case 'skill_at_least':
          if((state.skills[condition['skill']]??0)<((value as num).toInt()))return false;
        case 'attribute_at_least':
          if((state.attributes[condition['attribute']]??0)<((value as num).toInt()))return false;
        case 'money_at_least':
          if(state.money<((value as num).toInt()))return false;
        case 'health_below':
          if(state.health>=((value as num).toInt()))return false;
        case 'generation_at_least':
          if(state.generation<((value as num).toInt()))return false;
        case 'faction_rep_above':
          if((state.factions[condition['faction']]?.reputation??0)<=((value as num).toInt()))return false;
        case 'faction_rep_below':
          if((state.factions[condition['faction']]?.reputation??0)>=((value as num).toInt()))return false;
        case 'flag_equals':
          if(state.eventFlags[condition['key']]!=value)return false;
        case 'flag_not_set':
          if(state.eventFlags.containsKey(condition['key']))return false;
      }
    }
    return true;
  }

  int _cityStat(String stat){
    final city=state.city;
    switch(stat){
      case 'food':return city.food;
      case 'trade':return city.trade;
      case 'order':return city.order;
      case 'security':return city.security;
      case 'prosperity':return city.prosperity;
      case 'banditry':return city.banditry;
    }
    return 0;
  }

  void _changeCityStat(String stat,int amount){
    final city=state.city;
    switch(stat){
      case 'food':city.food=clamp100(city.food+amount);
      case 'trade':city.trade=clamp100(city.trade+amount);
      case 'order':city.order=clamp100(city.order+amount);
      case 'security':city.security=clamp100(city.security+amount);
      case 'prosperity':city.prosperity=clamp100(city.prosperity+amount);
      case 'banditry':city.banditry=clamp100(city.banditry+amount);
    }
  }

  String resolve(EventView event,String choiceId){
    final def=catalog[event.id];
    if(def==null)return 'Olay verisi bulunamadı.';
    EventChoiceDefinition? choice;
    for(final candidate in def.choices){
      if(candidate.id==choiceId){choice=candidate;break;}
    }
    if(choice==null)return 'Seçenek bulunamadı.';
    if(!_conditionsMet(choice.requirements))return 'Bu seçenek için gereken şartları artık karşılamıyorsun.';

    if(def.major)state.lastMajorEventDay=state.day;
    state.eventLastDay[def.id]=state.day;
    state.pendingEvents.remove(def.id);

    for(final effect in choice.effects)_applyEventEffect(effect,def.id);
    state.chronicle.add('${state.day}. gün — ${def.title}: ${choice.title}.');
    _sync();
    return choice.resultText;
  }

  void _applyEventEffect(Map<String,dynamic> effect,String sourceEventId){
    final type=effect['type'] as String? ?? '';
    final amount=(effect['amount'] as num?)?.toInt()??0;
    switch(type){
      case 'money':
        state.money=math.max(0,state.money+amount);
      case 'tension':
        state.tension=clamp100(state.tension+amount);
      case 'inventory':
        final item=effect['item'] as String;
        state.inventory[item]=math.max(0,(state.inventory[item]??0)+amount);
      case 'city_stat':
        _changeCityStat(effect['stat'] as String,amount);
      case 'faction_reputation':
        final faction=state.factions[effect['faction']];
        if(faction!=null)faction.reputation=clamp100(faction.reputation+amount);
      case 'skill':
        final skill=effect['skill'] as String;
        state.skills[skill]=clamp100((state.skills[skill]??0)+amount);
      case 'attribute':
        final attribute=effect['attribute'] as String;
        state.attributes[attribute]=clamp100((state.attributes[attribute]??0)+amount);
      case 'health':
        state.health=clamp100(state.health+amount);
        if(state.health<=0)_die('Olay sonucu ağır yaralanma');
      case 'set_flag':
        state.eventFlags[effect['key'] as String]=effect['value'];
      case 'knowledge':
        state.knowledge.add(KnowledgeEntry(
          id:'${effect['id']}_${state.day}_${state.knowledge.length}',
          text:effect['text'] as String,
          source:effect['source'] as String,
          reliability:(effect['reliability'] as num).toInt(),
          day:state.day,
        ));
      case 'knowledge_random':
        final min=(effect['minReliability'] as num).toInt();
        final max=(effect['maxReliability'] as num).toInt();
        final reliability=min+_rng.nextInt(math.max(1,max-min+1));
        state.knowledge.add(KnowledgeEntry(
          id:'${effect['id']}_${state.day}_${state.knowledge.length}',
          text:effect['text'] as String,
          source:effect['source'] as String,
          reliability:reliability,
          day:state.day,
        ));
      case 'npc_relation':
        final npc=state.npcs[effect['npc']];
        if(npc!=null){
          final memory=(effect['memory'] as String?)??'Bu olayda oyuncunun tavrını hatırlıyor';
          npc.remember(MemoryEntry(
            text:memory,day:state.day,importance:(effect['importance'] as num?)?.toInt()??30,
            trust:(effect['trust'] as num?)?.toInt()??0,
            respect:(effect['respect'] as num?)?.toInt()??0,
            fear:(effect['fear'] as num?)?.toInt()??0,
            affection:(effect['affection'] as num?)?.toInt()??0,
            suspicion:(effect['suspicion'] as num?)?.toInt()??0,
          ));
          npc.relation.change(debt:(effect['debt'] as num?)?.toInt()??0);
        }
      case 'schedule_event':
        final min=(effect['minDays'] as num?)?.toInt()??1;
        final max=(effect['maxDays'] as num?)?.toInt()??min;
        final due=state.day+min+_rng.nextInt(math.max(1,max-min+1));
        final eventId=effect['eventId'] as String;
        state.delayedEffects.add(DelayedEffect(
          id:'followup_${eventId}_${state.day}_${state.delayedEffects.length}',
          dueDay:due,type:'event_followup',source:sourceEventId,payload:{'eventId':eventId},
        ));
      case 'advance_days':
        if(amount>0)advance(amount);
      case 'chronicle':
        final text=effect['text'] as String?;
        if(text!=null)state.chronicle.add('${state.day}. gün — $text');
    }
  }

}
