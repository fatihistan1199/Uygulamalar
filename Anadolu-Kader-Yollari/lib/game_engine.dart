import 'dart:math' as math;
import 'event_catalog.dart';
import 'story_catalog.dart';

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
  MemoryEntry({required this.text,required this.day,required this.importance,required this.trust,required this.respect,required this.fear,required this.affection,required this.suspicion,this.source='doğrudan',this.kind='event'});
  final String text,source,kind; final int day,importance,trust,respect,fear,affection,suspicion;
  Map<String,dynamic> toJson()=>{'text':text,'day':day,'importance':importance,'trust':trust,'respect':respect,'fear':fear,'affection':affection,'suspicion':suspicion,'source':source,'kind':kind};
  factory MemoryEntry.fromJson(Map<String,dynamic> j)=>MemoryEntry(text:j['text'],day:j['day'],importance:j['importance'],trust:j['trust'],respect:j['respect'],fear:j['fear'],affection:j['affection'],suspicion:j['suspicion'],source:j['source']??'doğrudan',kind:j['kind']??'event');
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
  NpcState({required this.id,required this.name,required this.cityId,required this.profession,required this.factionId,required this.goal,required this.relation,List<MemoryEntry>? memories,int? age,this.gender='unknown',this.alive=true,this.spouseId,this.parentIds=const[],List<String>? childIds,Map<String,RelationState>? npcRelations}):memories=memories??[],age=age??30,childIds=childIds??[],npcRelations=npcRelations??{};
  final String id,name,profession,factionId,goal; String cityId; final RelationState relation; final List<MemoryEntry> memories;
  int age; String gender; bool alive; String? spouseId; final List<String> parentIds,childIds; final Map<String,RelationState> npcRelations;
  void remember(MemoryEntry m){memories.add(m);relation.change(trust:m.trust,respect:m.respect,fear:m.fear,affection:m.affection,suspicion:m.suspicion);}
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'cityId':cityId,'profession':profession,'factionId':factionId,'goal':goal,'relation':relation.toJson(),'memories':memories.map((m)=>m.toJson()).toList(),'age':age,'gender':gender,'alive':alive,'spouseId':spouseId,'parentIds':parentIds,'childIds':childIds,'npcRelations':npcRelations.map((k,v)=>MapEntry(k,v.toJson()))};
  factory NpcState.fromJson(Map<String,dynamic> j)=>NpcState(id:j['id'],name:j['name'],cityId:j['cityId'],profession:j['profession'],factionId:j['factionId'],goal:j['goal'],relation:RelationState.fromJson(Map<String,dynamic>.from(j['relation'])),memories:((j['memories'] as List?)??[]).map((m)=>MemoryEntry.fromJson(Map<String,dynamic>.from(m))).toList(),age:j['age']??30,gender:j['gender']??'unknown',alive:j['alive']??true,spouseId:j['spouseId'],parentIds:((j['parentIds'] as List?)??[]).cast<String>(),childIds:((j['childIds'] as List?)??[]).cast<String>(),npcRelations:((j['npcRelations'] as Map?)??{}).map((k,v)=>MapEntry(k.toString(),RelationState.fromJson(Map<String,dynamic>.from(v)))));
}

/// Oyuncu hanesinin, NPC dizininden bağımsız kalıcı soy kaydı.
class FamilyMember {
  FamilyMember({required this.id,required this.name,required this.age,required this.cityId,this.gender='unknown',this.alive=true,this.spouseId,this.parentIds=const[],List<String>? childIds,this.isPlayerLine=false,Map<String,RelationState>? relations}):childIds=childIds??[],relations=relations??{};
  final String id; String name,cityId,gender; int age; bool alive,isPlayerLine; String? spouseId; final List<String> parentIds,childIds; final Map<String,RelationState> relations;
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'age':age,'cityId':cityId,'gender':gender,'alive':alive,'spouseId':spouseId,'parentIds':parentIds,'childIds':childIds,'isPlayerLine':isPlayerLine,'relations':relations.map((k,v)=>MapEntry(k,v.toJson()))};
  factory FamilyMember.fromJson(Map<String,dynamic> j)=>FamilyMember(id:j['id'],name:j['name'],age:j['age']??18,cityId:j['cityId']??'konya',gender:j['gender']??'unknown',alive:j['alive']??true,spouseId:j['spouseId'],parentIds:((j['parentIds'] as List?)??[]).cast<String>(),childIds:((j['childIds'] as List?)??[]).cast<String>(),isPlayerLine:j['isPlayerLine']??false,relations:((j['relations'] as Map?)??{}).map((k,v)=>MapEntry(k.toString(),RelationState.fromJson(Map<String,dynamic>.from(v)))));
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
  KnowledgeEntry({required this.id,required this.text,required this.source,required this.reliability,required this.day,this.confirmed=false,this.refuted=false,this.factId});
  final String id,text,source; final String? factId; int reliability; final int day; bool confirmed,refuted;
  String get label {if(refuted)return 'Yanlışlandı';if(confirmed)return 'Doğrulandı';if(reliability>=80)return 'Güçlü söylenti';if(reliability>=55)return 'Söylenti';if(reliability>=30)return 'Şüpheli bilgi';return 'Propaganda / çok zayıf';}
  Map<String,dynamic> toJson()=>{'id':id,'text':text,'source':source,'reliability':reliability,'day':day,'confirmed':confirmed,'refuted':refuted,'factId':factId};
  factory KnowledgeEntry.fromJson(Map<String,dynamic> j)=>KnowledgeEntry(id:j['id'],text:j['text'],source:j['source'],reliability:j['reliability'],day:j['day'],confirmed:j['confirmed']??false,refuted:j['refuted']??false,factId:j['factId']);
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

enum ActionOutcome { criticalFailure, failure, partial, success, criticalSuccess }

class ActionRollResult {
  const ActionRollResult({required this.roll,required this.chance,required this.outcome});
  final int roll,chance;
  final ActionOutcome outcome;
  bool get succeeded=>outcome==ActionOutcome.success||outcome==ActionOutcome.criticalSuccess;
}

class StoryView {
  const StoryView({required this.id,required this.kicker,required this.title,required this.body,required this.continueLabel});
  final String id,kicker,title,body,continueLabel;
}

class LifeGoal {
  const LifeGoal({required this.title,required this.description,required this.complete});
  final String title,description;
  final bool complete;
}

class GameState {
  GameState({
    required this.version,required this.seed,required this.rngState,required this.playerName,required this.background,
    required this.day,required this.money,required this.tension,required this.currentCityId,required this.cities,
    required this.npcs,required this.factions,required this.knowledge,required this.delayedEffects,required this.chronicle,
    Map<String,int>? inventory,int? lastMajorEventDay,Map<String,int>? attributes,Map<String,int>? skills,
    int? health,int? age,int? generation,bool? alive,List<Injury>? injuries,List<String>? lineage,String? deathCause,
    Map<String,dynamic>? eventFlags,List<String>? pendingEvents,Map<String,int>? eventLastDay,
    Map<String,FamilyMember>? family,String? playerFamilyId,int? nextLifeId,String? playerGender,
    Map<String,bool>? worldFacts,List<String>? recentEventIds,List<String>? narrativeQueue,List<String>? visitedCities
  }):inventory=inventory??{},lastMajorEventDay=lastMajorEventDay??-999,
    attributes=attributes??{'strength':40,'agility':40,'intellect':40,'rhetoric':40,'intuition':40,'willpower':40},
    skills=skills??{'trade':25,'diplomacy':25,'law':20,'medicine':15,'religion':20,'military':20,'tracking':15,'espionage':10,'leadership':20,'localCulture':30},
    health=health??100,age=age??22,generation=generation??1,alive=alive??true,injuries=injuries??[],lineage=lineage??[],deathCause=deathCause??'',
    eventFlags=eventFlags??{},pendingEvents=pendingEvents??[],eventLastDay=eventLastDay??{},
    family=family??{},playerFamilyId=playerFamilyId??'player',nextLifeId=nextLifeId??1,playerGender=playerGender??'unknown',
    worldFacts=worldFacts??{},recentEventIds=recentEventIds??[],narrativeQueue=narrativeQueue??[],visitedCities=visitedCities??[currentCityId] {
      // v0.5 kayıtlarında soy alanı yoktu; dünya verisine dokunmadan ince bir başlangıç ağacı üret.
      if(this.family.isEmpty){
        this.family['player']=FamilyMember(id:'player',name:playerName,age:this.age,cityId:currentCityId,gender:this.playerGender,isPlayerLine:true);
      }
    }

  final int version,seed;
  int rngState,day,money,tension,lastMajorEventDay,health,age,generation;
  String playerName,background,currentCityId,deathCause,playerGender;
  bool alive;
  final Map<String,CityState> cities; final Map<String,NpcState> npcs; final Map<String,FactionState> factions;
  final List<KnowledgeEntry> knowledge; final List<DelayedEffect> delayedEffects; final List<String> chronicle;
  final Map<String,int> inventory,attributes,skills; final List<Injury> injuries; final List<String> lineage;
  final Map<String,dynamic> eventFlags; final List<String> pendingEvents; final Map<String,int> eventLastDay;
  final Map<String,FamilyMember> family; String playerFamilyId; int nextLifeId;
  final Map<String,bool> worldFacts; final List<String> recentEventIds,narrativeQueue,visitedCities;

  CityState get city=>cities[currentCityId]!;

  Map<String,dynamic> toJson()=>{
    'version':version,'seed':seed,'rngState':rngState,'playerName':playerName,'background':background,'day':day,'money':money,
    'tension':tension,'currentCityId':currentCityId,'cities':cities.map((k,v)=>MapEntry(k,v.toJson())),
    'npcs':npcs.map((k,v)=>MapEntry(k,v.toJson())),'factions':factions.map((k,v)=>MapEntry(k,v.toJson())),
    'knowledge':knowledge.map((k)=>k.toJson()).toList(),'delayedEffects':delayedEffects.map((e)=>e.toJson()).toList(),
    'chronicle':chronicle,'inventory':inventory,'lastMajorEventDay':lastMajorEventDay,'attributes':attributes,'skills':skills,
    'health':health,'age':age,'generation':generation,'alive':alive,'injuries':injuries.map((i)=>i.toJson()).toList(),
    'lineage':lineage,'deathCause':deathCause,'eventFlags':eventFlags,'pendingEvents':pendingEvents,'eventLastDay':eventLastDay,
    'family':family.map((k,v)=>MapEntry(k,v.toJson())),'playerFamilyId':playerFamilyId,'nextLifeId':nextLifeId,'playerGender':playerGender,
    'worldFacts':worldFacts,'recentEventIds':recentEventIds,'narrativeQueue':narrativeQueue,'visitedCities':visitedCities
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
    eventLastDay:Map<String,int>.from((j['eventLastDay'] as Map?)??{}),
    family:((j['family'] as Map?)??{}).map((k,v)=>MapEntry(k.toString(),FamilyMember.fromJson(Map<String,dynamic>.from(v)))),
    playerFamilyId:j['playerFamilyId']??'player',nextLifeId:j['nextLifeId']??1,playerGender:j['playerGender']??'unknown',
    worldFacts:Map<String,bool>.from((j['worldFacts'] as Map?)??{}),
    recentEventIds:((j['recentEventIds'] as List?)??[]).cast<String>(),
    narrativeQueue:((j['narrativeQueue'] as List?)??[]).cast<String>(),
    visitedCities:((j['visitedCities'] as List?)??[j['currentCityId']]).cast<String>()
  );
}

class EventOption {const EventOption(this.id,this.title,this.hint);final String id,title,hint;}
class EventView {const EventView({required this.id,required this.title,required this.body,required this.options});final String id,title,body;final List<EventOption> options;}

class GameEngine {
  GameEngine(this.state,this.catalog):_rng=KaderRng(state.rngState);
  final GameState state; final EventCatalog catalog; final KaderRng _rng;

  static GameState newGame({required int seed,required String name,required String background,String gender='unknown'}){
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
    for(var i=0;i<people.length;i++){
      final p=people[i];final female=<String>{'meryem','ayse','fatma','rukiyye','selma','leyla'}.contains(p[0]);
      npcs[p[0]]=NpcState(id:p[0],name:p[1],cityId:p[2],profession:p[3],factionId:p[4],goal:p[5],relation:RelationState(),age:24+((seed+i*11)%29),gender:female?'female':'male');
    }
    // Başlangıç bağları deterministiktir; oyuncuya ait ilişki puanlarından ayrıdır.
    void link(String a,String b,{int affection=45,int trust=50}){npcs[a]!.npcRelations[b]=RelationState(trust:trust,affection:affection);npcs[b]!.npcRelations[a]=RelationState(trust:trust,affection:affection);}
    link('mahmud','selma',affection:62,trust:58);link('yusuf','ayse',affection:51,trust:65);link('meryem','salih',affection:48,trust:60);link('hamza','leyla',affection:55,trust:52);link('halil','fatma',affection:41,trust:46);
    final attrs=<String,int>{'strength':40,'agility':40,'intellect':40,'rhetoric':40,'intuition':40,'willpower':40};
    final skills=<String,int>{'trade':25,'diplomacy':25,'law':20,'medicine':15,'religion':20,'military':20,'tracking':15,'espionage':10,'leadership':20,'localCulture':30};
    if(background=='Köylü ailesi'){attrs['willpower']=50;attrs['strength']=46;skills['localCulture']=48;skills['tracking']=32;}
    if(background=='Tüccar ailesi'){attrs['rhetoric']=48;attrs['intuition']=46;skills['trade']=52;skills['diplomacy']=38;}
    if(background=='Medrese öğrencisi'){attrs['intellect']=54;attrs['willpower']=45;skills['religion']=50;skills['law']=46;}
    if(background=='Asker ailesi'){attrs['strength']=52;attrs['agility']=46;attrs['willpower']=48;skills['military']=52;skills['leadership']=36;}
    final family=<String,FamilyMember>{
      'parent_1':FamilyMember(id:'parent_1',name:'Hüseyin',age:48,cityId:'konya',gender:'male',isPlayerLine:true),
      'parent_2':FamilyMember(id:'parent_2',name:'Emine',age:45,cityId:'konya',gender:'female',isPlayerLine:true,spouseId:'parent_1'),
      'player':FamilyMember(id:'player',name:name,age:22,cityId:'konya',gender:gender,parentIds:['parent_1','parent_2'],isPlayerLine:true),
      'sibling_1':FamilyMember(id:'sibling_1',name:gender=='female'?'Mehmet':'Zehra',age:19,cityId:'konya',gender:gender=='female'?'male':'female',parentIds:['parent_1','parent_2'],isPlayerLine:true),
    };
    family['parent_1']!.spouseId='parent_2';family['parent_1']!.childIds.addAll(['player','sibling_1']);family['parent_2']!.childIds.addAll(['player','sibling_1']);
    void linkFamily(String a,String b,{int trust=65,int affection=60,int respect=55}){
      family[a]!.relations[b]=RelationState(trust:trust,affection:affection,respect:respect);
      family[b]!.relations[a]=RelationState(trust:trust,affection:affection,respect:respect);
    }
    linkFamily('player','parent_1',trust:72,affection:68,respect:66);linkFamily('player','parent_2',trust:75,affection:72,respect:64);linkFamily('player','sibling_1',trust:61,affection:58,respect:48);linkFamily('parent_1','parent_2',trust:70,affection:66,respect:61);linkFamily('sibling_1','parent_1',trust:68,affection:65,respect:60);linkFamily('sibling_1','parent_2',trust:72,affection:70,respect:59);
    final worldFacts=<String,bool>{
      'tax_plan':seed%3==0,
      'mahmud_stockpiling':false,
      'ahi_countermove':false,
      'road_bandit_network':seed%5==0,
      'epidemic_prepared':false,
    };
    final backgroundScene=switch(background){
      'Köylü ailesi'=>'background_koylu',
      'Medrese öğrencisi'=>'background_medrese',
      'Asker ailesi'=>'background_asker',
      _=>'background_tuccar',
    };
    return GameState(version:10,seed:seed,rngState:seed,playerName:name,background:background,day:1,money:background=='Tüccar ailesi'?70:50,tension:20,currentCityId:'konya',cities:cities,npcs:npcs,factions:factions,knowledge:[],delayedEffects:[],chronicle:['1. gün — $name Konya’da yolculuğuna başladı.'],inventory:{},lastMajorEventDay:-999,attributes:attrs,skills:skills,health:100,age:22,generation:1,alive:true,injuries:[],lineage:[],eventFlags:{},pendingEvents:[],eventLastDay:{},family:family,playerFamilyId:'player',nextLifeId:1,playerGender:gender,worldFacts:worldFacts,recentEventIds:[],narrativeQueue:['intro_identity',backgroundScene,'city_konya','intro_path'],visitedCities:['konya']);
  }

  void _sync()=>state.rngState=_rng.state;

  void advance(int days){
    for(var i=0;i<days;i++){state.day++;
      for(final c in state.cities.values){if(_rng.nextDouble()<.11)c.food=clamp100(c.food-1);c.trade=clamp100(c.trade+_rng.nextInt(3)-1);if(c.food<35)c.order=clamp100(c.order-1);if(c.order<35)c.banditry=clamp100(c.banditry+1);for(final g in c.stock.keys.toList()){var change=_rng.nextInt(3)-1;if(g=='grain'&&c.food<55)change--;c.stock[g]=math.max(5,math.min(100,c.stock[g]!+change));}}
      _runNpcGoals();_runLivingGeneration();_runDueEffects();}
    _sync();
  }

  void _runNpcGoals(){
    if(state.day%7!=0)return;
    final actors=state.npcs.values.where((n)=>n.alive&&n.age>=16).toList()..sort((a,b)=>a.id.compareTo(b.id));
    if(actors.isEmpty)return;
    _applyNpcGoalAction(actors[_rng.nextInt(actors.length)]);
  }

  void runNpcGoalForTest(String npcId){
    final npc=state.npcs[npcId];
    if(npc!=null&&npc.alive)_applyNpcGoalAction(npc);
    _sync();
  }

  void _applyNpcGoalAction(NpcState acting){
    final faction=state.factions[acting.factionId]!;
    final city=state.cities[acting.cityId]!;
    switch(acting.factionId){
      case 'tuccar':
        city.trade=clamp100(city.trade+1);
        if(_rng.nextInt(100)<45)city.prosperity=clamp100(city.prosperity+1);
        faction.power=clamp100(faction.power+(_rng.nextInt(100)<58?1:0));
      case 'ahi':
        city.order=clamp100(city.order+1);
        if(_rng.nextInt(100)<40)city.trade=clamp100(city.trade+1);
        faction.power=clamp100(faction.power+(_rng.nextInt(100)<52?1:0));
      case 'yonetim':
        city.security=clamp100(city.security+1);
        if(city.banditry>0&&_rng.nextInt(100)<55)city.banditry=clamp100(city.banditry-1);
        faction.power=clamp100(faction.power+(_rng.nextInt(100)<48?1:0));
      case 'medrese':
        if(_rng.nextInt(100)<55)city.order=clamp100(city.order+1);
        if(_rng.nextInt(100)<38)city.prosperity=clamp100(city.prosperity+1);
        faction.power=clamp100(faction.power+(_rng.nextInt(100)<42?1:0));
    }

    switch(acting.id){
      case 'mahmud':
        if(city.id=='kayseri'&&city.food<60){
          city.stock['grain']=math.max(5,(city.stock['grain']??50)-3);
          city.trade=clamp100(city.trade+1);
          state.tension=clamp100(state.tension+2);
          state.worldFacts['mahmud_stockpiling']=true;
          state.eventFlags['mahmud_stockpiling']=true;
          state.eventFlags['mahmud_stockpile_day']=state.day;
        }
      case 'yusuf':
        if(city.id=='kayseri'&&state.worldFacts['mahmud_stockpiling']==true){
          state.worldFacts['ahi_countermove']=true;
          state.eventFlags['ahi_countermove']=true;
          state.factions['ahi']!.power=clamp100(state.factions['ahi']!.power+2);
          city.order=clamp100(city.order-1);
          state.tension=clamp100(state.tension+2);
        }
      case 'sinan':
        state.worldFacts['tax_plan']=true;
        state.eventFlags['tax_policy_active']=true;
        state.tension=clamp100(state.tension+2);
        city.prosperity=clamp100(city.prosperity-1);
      case 'meryem':
        state.worldFacts['epidemic_prepared']=true;
        state.eventFlags['epidemic_prepared']=true;
        city.order=clamp100(city.order+1);
      case 'yakup':
        city.security=clamp100(city.security+2);
        city.banditry=clamp100(city.banditry-2);
      case 'hamza':
        city.trade=clamp100(city.trade+2);
        city.prosperity=clamp100(city.prosperity+1);
      case 'davud':
        city.trade=clamp100(city.trade+1);
        state.tension=clamp100(state.tension+1);
      default:
        if(acting.goal.contains('güvenli')||acting.goal.contains('güvenliğini')){
          city.security=clamp100(city.security+1);
          city.banditry=clamp100(city.banditry-1);
        }
        if(acting.goal.contains('vergi')){
          state.worldFacts['tax_plan']=true;
          state.tension=clamp100(state.tension+1);
          city.prosperity=clamp100(city.prosperity-1);
        }
    }
    state.eventFlags['npc_goal_'+acting.id+'_last_day']=state.day;
    if(state.day%28==0){
      state.chronicle.add('${state.day}. gün — ${acting.name}, ${city.name} içinde kendi hedefleri doğrultusunda hareket etti.');
    }
  }

  /// Dünya takvimi: ayda bir sosyal bağlar, yılda bir yaşlanma çözülür.
  /// Tüm rastlantı yalnızca kayıtlı RNG'den geldiği için save/load sonrası aynen sürer.
  void _runLivingGeneration(){
    if(state.day%360==0){
      state.age++;
      for(final member in state.family.values){if(member.alive)member.age++;}
      for(final npc in state.npcs.values){if(npc.alive)npc.age++;}
      final familyElders=state.family.values.where((m)=>m.alive&&m.age>=70).toList()..sort((a,b)=>a.id.compareTo(b.id));
      for(final elder in familyElders){
        if(_rng.nextInt(100)<math.min(45,elder.age-64)){
          elder.alive=false;
          if(elder.spouseId!=null)state.family[elder.spouseId!]?.spouseId=null;
          state.chronicle.add('${state.day}. gün — Aileden ${elder.name} yaşlılık nedeniyle hayata veda etti.');
        }
      }
      final elders=state.npcs.values.where((n)=>n.alive&&n.age>=70).toList()..sort((a,b)=>a.id.compareTo(b.id));
      for(final elder in elders){
        if(_rng.nextInt(100)<math.min(45,elder.age-64)){
          elder.alive=false;
          state.chronicle.add('${state.day}. gün — ${elder.name} yaşlılık nedeniyle hayata veda etti.');
        }
      }
    }
    if(state.day%30!=0)return;
    final adults=state.npcs.values.where((n)=>n.alive&&n.age>=18&&n.age<=55).toList()..sort((a,b)=>a.id.compareTo(b.id));
    // Mevcut karşılıklı bağlar evliliğe dönüşebilir.
    for(final a in adults){
      if(a.spouseId!=null)continue;
      final candidates=adults.where((b)=>b.id!=a.id&&b.spouseId==null&&b.gender!=a.gender&&b.cityId==a.cityId&&(a.npcRelations[b.id]?.affection??0)>=52&&(b.npcRelations[a.id]?.trust??0)>=45).toList();
      if(candidates.isNotEmpty&&_rng.nextInt(100)<18){
        final b=candidates[_rng.nextInt(candidates.length)];
        a.spouseId=b.id;b.spouseId=a.id;
        state.chronicle.add('${state.day}. gün — ${a.name} ile ${b.name} evlendi.');
      }
    }
    final couples=state.npcs.values.where((n)=>n.alive&&n.spouseId!=null&&n.id.compareTo(n.spouseId!)<0).toList()..sort((a,b)=>a.id.compareTo(b.id));
    for(final a in couples){
      final b=state.npcs[a.spouseId!];
      if(b==null||!b.alive||a.age>48||b.age>48||_rng.nextInt(100)>=10)continue;
      _addNpcChild(a,b);
    }
    // Birbirini tanıyan NPC'lerin bağları, oyuncunun dışında da şekillenir.
    for(final a in adults){
      for(final entry in a.npcRelations.entries){
        if(_rng.nextInt(100)<12)entry.value.change(trust:_rng.nextInt(3)-1,affection:_rng.nextInt(3)-1,suspicion:_rng.nextInt(3)-1);
      }
    }
    _runFamilyHousehold();
  }

  void _runFamilyHousehold(){
    final adults=state.family.values.where((m)=>m.alive&&m.age>=18&&m.age<=48).toList()..sort((a,b)=>a.id.compareTo(b.id));
    for(final member in adults){
      if(state.family.length>=30)break;
      if(member.id==state.playerFamilyId||!member.isPlayerLine||member.spouseId!=null)continue;
      if(_rng.nextInt(100)<4)_addFamilySpouse(member);
    }
    final couples=state.family.values.where((m)=>m.alive&&m.spouseId!=null&&m.id.compareTo(m.spouseId!)<0).toList()..sort((a,b)=>a.id.compareTo(b.id));
    for(final a in couples){
      if(state.family.length>=30)break;
      final b=state.family[a.spouseId!];
      if(b==null||!b.alive||a.age>45||b.age>45)continue;
      if(a.id==state.playerFamilyId||b.id==state.playerFamilyId)continue;
      if(a.childIds.length>=4||b.childIds.length>=4)continue;
      if(_rng.nextInt(100)<3)_addFamilyChild(a,b);
    }
  }

  FamilyMember _addFamilySpouse(FamilyMember member){
    final id='family_spouse_${state.nextLifeId++}';
    final spouseGender=member.gender=='female'?'male':'female';
    final names=spouseGender=='female'?['Aysel','Hatice','Meryem','Safiye','Zehra']:['Ali','Mehmed','Yusuf','İlyas','Ömer'];
    final spouse=FamilyMember(id:id,name:names[_rng.nextInt(names.length)],age:math.max(18,member.age-3+_rng.nextInt(7)),cityId:member.cityId,gender:spouseGender,spouseId:member.id,isPlayerLine:false);
    state.family[id]=spouse;member.spouseId=id;
    member.relations[id]=RelationState(trust:58,affection:60,respect:52);spouse.relations[member.id]=RelationState(trust:58,affection:60,respect:52);
    state.chronicle.add('${state.day}. gün — ${member.name} ile ${spouse.name} evlendi.');
    return spouse;
  }

  FamilyMember? _addFamilyChild(FamilyMember a,FamilyMember b){
    if(state.family.length>=30)return null;
    final id='family_child_${state.nextLifeId++}';
    final female=_rng.nextInt(2)==0;
    final names=female?['Elif','Hatice','Zehra','Safiye','Ayla']:['Mehmed','Yusuf','Ali','Musa','Ömer'];
    final child=FamilyMember(id:id,name:names[_rng.nextInt(names.length)],age:0,cityId:a.cityId,gender:female?'female':'male',parentIds:[a.id,b.id],isPlayerLine:a.isPlayerLine||b.isPlayerLine);
    state.family[id]=child;a.childIds.add(id);b.childIds.add(id);
    a.relations[id]=RelationState(trust:78,affection:85,respect:45);b.relations[id]=RelationState(trust:78,affection:85,respect:45);
    child.relations[a.id]=RelationState(trust:78,affection:85,respect:45);child.relations[b.id]=RelationState(trust:78,affection:85,respect:45);
    state.chronicle.add('${state.day}. gün — ${a.name} ve ${b.name} ailesine ${child.name} katıldı.');
    return child;
  }

  void _addNpcChild(NpcState a,NpcState b){
    final id='born_${state.nextLifeId++}';
    final female=_rng.nextInt(2)==0;
    final names=female?['Ayla','Hatice','Elif','Gül','Safiye']:['Kemal','İlyas','Musa','Orhan','Murat'];
    final child=NpcState(id:id,name:names[_rng.nextInt(names.length)],cityId:a.cityId,profession:'Çocuk',factionId:a.factionId,goal:'Ailesinin gölgesinde kendi yolunu bulmak',relation:RelationState(),age:0,gender:female?'female':'male',parentIds:[a.id,b.id]);
    state.npcs[id]=child;a.childIds.add(id);b.childIds.add(id);
    a.npcRelations[id]=RelationState(trust:75,affection:80);b.npcRelations[id]=RelationState(trust:75,affection:80);
    child.npcRelations[a.id]=RelationState(trust:75,affection:80);child.npcRelations[b.id]=RelationState(trust:75,affection:80);
    state.chronicle.add('${state.day}. gün — ${a.name} ve ${b.name} ailesine ${child.name} katıldı.');
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
      }else if(effect.type=='social_reputation'){
        final origin=state.npcs[effect.payload['originNpc']];
        final carrier=state.npcs[effect.payload['carrierNpc']];
        final signal=(effect.payload['signal'] as num?)?.toInt()??0;
        final hop=(effect.payload['hop'] as num?)?.toInt()??0;
        final reliability=(effect.payload['reliability'] as num?)?.toInt()??70;
        if(origin!=null&&carrier!=null&&signal!=0&&reliability>=25&&_rng.nextInt(100)<reliability){
          final sign=signal>0?1:-1;
          final rawStrength=math.max(1,signal.abs()~/6);
          final strength=math.max(1,math.min(4,rawStrength-hop));
          if(hop<=1){
            final faction=state.factions[carrier.factionId];
            if(faction!=null&&_rng.nextInt(100)<70)faction.reputation=clamp100(faction.reputation+sign*strength);
          }
          final contacts=carrier.npcRelations.entries.where((e)=>(e.value.trust>=45||e.value.affection>=45)&&state.npcs[e.key]?.alive==true&&e.key!=origin.id).toList()..sort((a,b)=>a.key.compareTo(b.key));
          var propagated=false;
          for(final contact in contacts.take(2)){
            if(_rng.nextInt(100)>=math.max(24,reliability-18))continue;
            final target=state.npcs[contact.key]!;
            target.relation.change(trust:sign*math.max(1,strength~/2),respect:sign,suspicion:sign<0?strength:-1);
            target.memories.add(MemoryEntry(
              text:'${carrier.name}, ${origin.name} kaynaklı oyuncu haberini aktardı',
              day:state.day,importance:18+strength*5,trust:0,respect:0,fear:0,affection:0,suspicion:0,
              source:carrier.name,kind:'rumor',
            ));
            if(!propagated&&hop<2){
              final nextReliability=reliability-24;
              final nextSignal=(signal*.65).round();
              if(nextReliability>=25&&nextSignal.abs()>=3){
                state.delayedEffects.add(DelayedEffect(
                  id:'social_${origin.id}_${target.id}_${state.day}_${state.delayedEffects.length}',
                  dueDay:state.day+2+_rng.nextInt(6),type:'social_reputation',source:'social_network',
                  payload:{'originNpc':origin.id,'carrierNpc':target.id,'signal':nextSignal,'reason':effect.payload['reason'],'hop':hop+1,'reliability':nextReliability},
                ));
                propagated=true;
              }
            }
          }
          state.chronicle.add('${state.day}. gün — ${origin.name} kaynaklı bir haber sosyal ağda ${hop+1}. halkaya ulaştı.');
        }
      }else if(effect.type=='family_echo'){
        final source=state.family[effect.payload['sourceFamily']];
        final signal=(effect.payload['signal'] as num?)?.toInt()??0;
        final current=state.family[state.playerFamilyId];
        if(source!=null&&current!=null&&signal!=0){
          final sign=signal>0?1:-1;
          final listeners=state.family.values.where((m)=>m.alive&&m.id!=source.id&&m.id!=current.id&&(source.relations[m.id]?.trust??0)>=45).toList()..sort((a,b)=>a.id.compareTo(b.id));
          if(listeners.isNotEmpty&&_rng.nextInt(100)<70){
            final listener=listeners[_rng.nextInt(listeners.length)];
            final bond=listener.relations.putIfAbsent(current.id,()=>RelationState());
            current.relations.putIfAbsent(listener.id,()=>RelationState());
            bond.change(trust:sign*2,affection:sign,suspicion:sign<0?2:-1);
            current.relations[listener.id]!.change(trust:sign*2,affection:sign,suspicion:sign<0?2:-1);
          }
        }
      }
      state.delayedEffects.remove(effect);
    }
  }

  int travel(String cityId){
    final from=state.city.name;final destination=state.cities[cityId]!;
    final firstVisit=!state.visitedCities.contains(cityId);
    final base=3+_rng.nextInt(5);final dangerPenalty=((destination.banditry+(100-destination.security))/70).floor();final days=math.max(2,base+dangerPenalty);
    state.currentCityId=cityId;advance(days);state.chronicle.add('${state.day}. gün — $from’dan ${destination.name} şehrine ulaştı.');
    if(firstVisit){
      state.visitedCities.add(cityId);
      state.narrativeQueue.add('city_$cityId');
    }else{
      state.narrativeQueue.add('city_return');
    }
    _sync();return days;
  }


  String cityMoodText(){
    final c=state.city;
    if(c.food<40)return 'Pazarda erzak kaygısı yüzlerden okunuyor; insanlar fiyatları ve ambarları konuşuyor.';
    if(c.order<40)return 'Sokaklarda huzursuzluk var; küçük tartışmalar bile hızla kalabalık topluyor.';
    if(c.security<45||c.banditry>45)return 'Şehir kapılarında yol güvenliği konuşuluyor; dışarıdan gelenlerin çoğu eşkıya haberleri taşıyor.';
    if(c.trade>82)return 'Hanlar ve çarşı hareketli; yeni gelen kervanlar fiyatları ve haberleri sürekli değiştiriyor.';
    if(c.prosperity>75)return 'Şehrin refahı görünür durumda; yeni yapılar, kalabalık dükkânlar ve iş arayan insanlar dikkat çekiyor.';
    return 'Şehir görünürde dengeli; fakat çarşı, aileler ve güç çevreleri kendi hesaplarını sessizce sürdürüyor.';
  }

  StoryView? currentStory(StoryCatalog stories){
    if(state.narrativeQueue.isEmpty)return null;
    final id=state.narrativeQueue.first;
    final def=stories[id];
    if(def==null)return StoryView(id:id,kicker:'Hikâye',title:'Yol Devam Ediyor',body:'Hayatında yeni bir dönem başlıyor.',continueLabel:'Devam et');
    var title=_renderText(def.title).replaceAll('{{age}}','${state.age}').replaceAll('{{city_mood}}',cityMoodText());
    var body=_renderText(def.body).replaceAll('{{age}}','${state.age}').replaceAll('{{city_mood}}',cityMoodText());
    return StoryView(id:id,kicker:_renderText(def.kicker),title:title,body:body,continueLabel:def.continueLabel);
  }

  void completeStory(){
    if(state.narrativeQueue.isNotEmpty)state.narrativeQueue.removeAt(0);
  }

  List<LifeGoal> lifeGoals(){
    final player=state.family[state.playerFamilyId];
    return [
      LifeGoal(title:'Geçimini sağla',description:'Şehirde bir iş bul ve ilk kazancını elde et.',complete:state.eventFlags['first_work_done']==true),
      LifeGoal(title:'Bir meseleye karış',description:'Bir görev veya şehir olayında karar ver.',complete:state.eventFlags['first_task_done']==true),
      LifeGoal(title:'Bir yuva kur',description:'İstersen evlen ve hayatını başka biriyle birleştir.',complete:player?.spouseId!=null),
      LifeGoal(title:'Yeni nesil',description:'Bir çocuğun olsun ve soyunun geleceğini şekillendir.',complete:player?.childIds.isNotEmpty==true),
    ];
  }

  String doLocalWork(){
    final c=state.city;
    late String title,attribute,skill;
    late int difficulty;
    if(c.id=='kayseri'){title='Bir handa kervan yüklerinin hesabına yardım ettin';attribute='intellect';skill='trade';difficulty=35;}
    else if(c.id=='sivas'){title='Yola çıkacak bir kervanın hazırlığında çalıştın';attribute='willpower';skill='tracking';difficulty=37;}
    else if(c.id=='ankara'){title='Çarşıdaki bir dokumacı grubunun teslimat işine yardım ettin';attribute='agility';skill='localCulture';difficulty=34;}
    else if(c.id=='antalya'){title='Limanda gelen malların boşaltma ve kayıt işine katıldın';attribute='strength';skill='trade';difficulty=38;}
    else {title='Konya’da bir hanın günlük işlerine yardım ettin';attribute='willpower';skill='localCulture';difficulty=34;}
    final check=rollAction(attribute:attribute,skill:skill,difficulty:difficulty);
    final pay=switch(check.outcome){
      ActionOutcome.criticalSuccess=>10,
      ActionOutcome.success=>7,
      ActionOutcome.partial=>4,
      ActionOutcome.failure=>1,
      ActionOutcome.criticalFailure=>0,
    };
    state.money+=pay;
    if(check.succeeded)state.skills[skill]=clamp100((state.skills[skill]??0)+1);
    advance(1);
    final first=state.eventFlags['first_work_done']!=true;
    state.eventFlags['first_work_done']=true;
    state.eventFlags['work_count']=((state.eventFlags['work_count'] as int?)??0)+1;
    if(first&&!state.narrativeQueue.contains('milestone_first_work'))state.narrativeQueue.add('milestone_first_work');
    state.chronicle.add('${state.day}. gün — $title; $pay akçe kazandı.');
    _sync();
    final ending=switch(check.outcome){
      ActionOutcome.criticalSuccess=>'İşi beklenenden iyi yaptın; seni yeniden çağırabileceklerini söylediler.',
      ActionOutcome.success=>'İşini düzgün tamamladın ve emeğinin karşılığını aldın.',
      ActionOutcome.partial=>'İş tamamlandı ama birkaç aksaklık yüzünden kazancın sınırlı kaldı.',
      ActionOutcome.failure=>'İşte zorlandın; yine de günün sonunda küçük bir ödeme aldın.',
      ActionOutcome.criticalFailure=>'İş ters gitti ve bu kez para kazanamadın. Yine de neyin yanlış gittiğini gördün.',
    };
    return '$title. $ending';
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
    final result=rollAction(attribute:attribute,skill:skill,difficulty:35);
    return result.chance-result.roll+50;
  }

  int actionChance({
    required String attribute,
    required String skill,
    int difficulty=35,
    int modifier=0,
    int? opponentAttribute,
    int? opponentSkill,
    String? secondarySkill,
  }){
    final a=state.attributes[attribute]??40;
    final s=state.skills[skill]??20;
    final secondary=secondarySkill==null?0:(state.skills[secondarySkill]??0);
    final actor=a*.62+s*.33+secondary*.05;
    final opposition=opponentAttribute==null
      ? difficulty.toDouble()
      : opponentAttribute*.65+(opponentSkill??20)*.35;
    return math.max(5,math.min(95,(50+(actor-opposition)*3.5+modifier).round()));
  }

  ActionRollResult rollAction({
    required String attribute,
    required String skill,
    int difficulty=35,
    int modifier=0,
    int? opponentAttribute,
    int? opponentSkill,
    String? secondarySkill,
  }){
    final chance=actionChance(attribute:attribute,skill:skill,difficulty:difficulty,modifier:modifier,opponentAttribute:opponentAttribute,opponentSkill:opponentSkill,secondarySkill:secondarySkill);
    final roll=1+_rng.nextInt(100);
    late ActionOutcome outcome;
    final criticalSuccessLimit=math.max(2,math.min(8,chance~/10));
    if(roll<=criticalSuccessLimit)outcome=ActionOutcome.criticalSuccess;
    else if(roll<=chance)outcome=ActionOutcome.success;
    else if(roll<=math.min(96,chance+12))outcome=ActionOutcome.partial;
    else if(roll>=97)outcome=ActionOutcome.criticalFailure;
    else outcome=ActionOutcome.failure;
    _sync();
    return ActionRollResult(roll:roll,chance:chance,outcome:outcome);
  }

  String chanceLabel(int chance){
    if(chance>=80)return 'Çok avantajlı';
    if(chance>=65)return 'Avantajlı';
    if(chance>=45)return 'Dengeli';
    if(chance>=25)return 'Zor';
    return 'Çok zor';
  }

  ({String attribute,String skill,int modifier,int opponentAttribute,int opponentSkill,String label}) _conflictProfile(String tactic){
    var attribute='willpower',skill='military',modifier=0,label='Tedbirli savunma';
    if(tactic=='assault'){attribute='strength';skill='military';modifier=2;label='Hızlı saldırı';}
    if(tactic=='flee'){attribute='agility';skill='tracking';modifier=4;label='Geri çekilme';}
    if(tactic=='parley'){attribute='rhetoric';skill='diplomacy';modifier=-2;label='Konuşarak çözme';}
    final opponentAttribute=20+(state.city.banditry*.55).round();
    final opponentSkill=18+((100-state.city.security)*.45).round();
    return(attribute:attribute,skill:skill,modifier:modifier,opponentAttribute:opponentAttribute,opponentSkill:opponentSkill,label:label);
  }

  String conflictRiskLabel(String tactic){
    final p=_conflictProfile(tactic);
    return chanceLabel(actionChance(attribute:p.attribute,skill:p.skill,modifier:p.modifier,opponentAttribute:p.opponentAttribute,opponentSkill:p.opponentSkill));
  }

  ConflictResult resolveConflict(String tactic){
    if(!state.alive)return ConflictResult(summary:'Ölü bir karakter çatışmaya giremez.',success:false,escaped:false,damage:0,dead:true);
    final p=_conflictProfile(tactic);
    final check=rollAction(attribute:p.attribute,skill:p.skill,modifier:p.modifier,opponentAttribute:p.opponentAttribute,opponentSkill:p.opponentSkill);
    int damage=0;bool success=false,escaped=false;Injury? injury;
    switch(check.outcome){
      case ActionOutcome.criticalSuccess:
        success=true;damage=_rng.nextInt(4);
      case ActionOutcome.success:
        success=true;damage=3+_rng.nextInt(9);
      case ActionOutcome.partial:
        escaped=tactic=='flee'||tactic=='parley';damage=10+_rng.nextInt(14);
      case ActionOutcome.failure:
        escaped=tactic=='flee'&&_rng.nextInt(100)<35;damage=20+_rng.nextInt(18);
      case ActionOutcome.criticalFailure:
        damage=38+_rng.nextInt(28);
    }
    if(damage>=14)injury=_makeInjury(damage);
    _applyDamage(damage,cause:'Yol çatışması',injury:injury);
    if(success){
      state.skills[p.skill]=clamp100((state.skills[p.skill]??0)+1);
      state.factions['yonetim']!.reputation=clamp100(state.factions['yonetim']!.reputation+1);
    }
    state.chronicle.add('${state.day}. gün — ${p.label}: zar ${check.roll}, hasar $damage.');
    _sync();
    final summary=state.alive
      ? switch(check.outcome){
          ActionOutcome.criticalSuccess=>'${p.label} olağanüstü başarılı oldu. $damage hasar aldın.',
          ActionOutcome.success=>'${p.label} başarılı oldu. $damage hasar aldın.',
          ActionOutcome.partial=>escaped?'Tam sonuç alamadın ama çatışmadan sıyrıldın; $damage hasar aldın.':'Üstünlük kuramadın; yine de ayakta kaldın. $damage hasar aldın.',
          ActionOutcome.failure=>'Taktik başarısız oldu; $damage hasar aldın.',
          ActionOutcome.criticalFailure=>'Taktik ağır biçimde ters tepti; $damage hasar aldın.',
        }
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
    final kin=state.family.values.where((m)=>m.alive&&m.id!=state.playerFamilyId&&m.age>=16&&m.isPlayerLine).toList()..sort((a,b)=>a.id.compareTo(b.id));
    if(kin.isNotEmpty)return kin.take(3).map((m)=>m.name).toList();
    final pool=['Ali','Zeynep','Mehmed','Ayşe','Yusuf','Meryem','Hasan','Fatma','Ömer','Selma'];
    final start=(state.seed+state.generation*3)%pool.length;
    return List.generate(3,(i)=>pool[(start+i*2)%pool.length]);
  }

  String assumeSuccessor(String name){
    if(state.alive)return 'Halef yalnız mevcut karakter öldüğünde seçilebilir.';
    final old=state.playerName;
    FamilyMember? heir;
    for(final member in state.family.values){if(member.name==name&&member.alive&&member.age>=16&&member.id!=state.playerFamilyId){heir=member;break;}}
    if(heir==null){
      final id='heir_${state.nextLifeId++}';
      final femaleNames=<String>{'Zeynep','Ayşe','Meryem','Fatma','Selma'};
      heir=FamilyMember(id:id,name:name,age:18,cityId:state.currentCityId,gender:femaleNames.contains(name)?'female':'male',parentIds:[state.playerFamilyId],isPlayerLine:true);
      state.family[id]=heir;state.family[state.playerFamilyId]?.childIds.add(id);
    }
    final former=state.family[state.playerFamilyId];
    if(former!=null){former.alive=false;former.cityId=state.currentCityId;}
    heir.isPlayerLine=true;heir.cityId=state.currentCityId;
    state.lineage.add(old);state.playerFamilyId=heir.id;state.playerName=heir.name;state.playerGender=heir.gender;state.background='Aile mirasçısı';state.generation++;state.age=heir.age;state.health=100;state.alive=true;state.deathCause='';state.injuries.clear();
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

  List<FamilyMember> _familyRoleCandidates(String role){
    final current=state.family[state.playerFamilyId];
    if(current==null)return [];
    final all=state.family.values.where((m)=>m.alive&&m.id!=current.id).toList()..sort((a,b)=>a.id.compareTo(b.id));
    switch(role){
      case 'spouse':
        final id=current.spouseId;return id!=null&&state.family[id]?.alive==true?[state.family[id]!]:[];
      case 'parent':
        return current.parentIds.map((id)=>state.family[id]).whereType<FamilyMember>().where((m)=>m.alive).toList()..sort((a,b)=>a.id.compareTo(b.id));
      case 'elder_parent':
        return current.parentIds.map((id)=>state.family[id]).whereType<FamilyMember>().where((m)=>m.alive&&m.age>=45).toList()..sort((a,b)=>a.id.compareTo(b.id));
      case 'sibling':
        return all.where((m)=>m.parentIds.any(current.parentIds.contains)).toList();
      case 'child':
        return current.childIds.map((id)=>state.family[id]).whereType<FamilyMember>().where((m)=>m.alive).toList()..sort((a,b)=>a.id.compareTo(b.id));
      case 'adult_child':
        return current.childIds.map((id)=>state.family[id]).whereType<FamilyMember>().where((m)=>m.alive&&m.age>=16).toList()..sort((a,b)=>a.id.compareTo(b.id));
      case 'adult_relative':
        return all.where((m)=>m.age>=16&&m.isPlayerLine).toList();
    }
    return [];
  }

  FamilyMember? _familyRole(String role){final list=_familyRoleCandidates(role);return list.isEmpty?null:list.first;}

  RelationState _familyBond(FamilyMember other){
    final current=state.family[state.playerFamilyId]!;
    final a=current.relations.putIfAbsent(other.id,()=>RelationState());
    other.relations.putIfAbsent(current.id,()=>RelationState());
    return a;
  }

  int _relationAxis(RelationState relation,String axis){
    switch(axis){
      case 'trust':return relation.trust;
      case 'respect':return relation.respect;
      case 'fear':return relation.fear;
      case 'affection':return relation.affection;
      case 'suspicion':return relation.suspicion;
      case 'debt':return relation.debt;
    }
    return 0;
  }

  KnowledgeEntry? _latestUnverifiedKnowledge([String? factId]){
    final candidates=state.knowledge.where((k)=>!k.confirmed&&!k.refuted&&(factId==null||k.factId==factId)).toList();
    if(candidates.isEmpty)return null;
    candidates.sort((a,b)=>b.day.compareTo(a.day));
    return candidates.first;
  }

  String _renderText(String text){
    var result=text.replaceAll('{{player}}',state.playerName).replaceAll('{{city}}',state.city.name);
    final rumor=_latestUnverifiedKnowledge();
    result=result.replaceAll('{{rumor}}',rumor?.text??'duyduğun söylenti');
    result=result.replaceAll('{{rumor_source}}',rumor?.source??'belirsiz kaynak');
    result=result.replaceAll('{{verification_result}}',(state.eventFlags['last_verification_result'] as String?)??'Bilginin doğruluğu hâlâ kesinleşmedi.');
    for(final role in ['spouse','parent','elder_parent','sibling','child','adult_child']){
      result=result.replaceAll('{{$role}}',_familyRole(role)?.name??'yakının');
    }
    return result;
  }

  String storyDirectorMode(){
    if(state.day-state.lastMajorEventDay<6)return 'recovery';
    if(state.tension>=70||state.city.order<35)return 'pressure';
    if(state.tension>=40)return 'build';
    return 'calm';
  }

  int directorWeightForTest(String eventId){
    final def=catalog[eventId];
    return def==null?0:_directorWeight(def);
  }

  int _directorWeight(EventDefinition def){
    var weight=math.max(1,def.weight).toDouble();
    final mode=storyDirectorMode();
    final tags=def.tags.toSet();
    if(state.recentEventIds.contains(def.id))weight*=.28;
    if(mode=='recovery'){
      if(tags.any({'family','social','knowledge','memory','travel'}.contains))weight*=1.55;
      if(tags.any({'crisis','danger','politics'}.contains))weight*=.48;
    }else if(mode=='pressure'){
      if(tags.any({'crisis','danger','politics','faction'}.contains))weight*=1.5;
      if(tags.any({'knowledge','rumor'}.contains))weight*=1.18;
    }else if(mode=='build'){
      if(tags.any({'economy','faction','rumor','secret'}.contains))weight*=1.25;
    }else{
      if(tags.any({'social','knowledge','family','travel','memory'}.contains))weight*=1.32;
      if(tags.contains('crisis'))weight*=.72;
    }
    if(_latestUnverifiedKnowledge()!=null&&tags.any({'knowledge','rumor','secret'}.contains))weight*=1.35;
    if(def.chainId!=null&&state.eventLastDay.keys.any((id)=>catalog[id]?.chainId==def.chainId))weight*=1.45;
    return math.max(1,weight.round());
  }

  bool _choiceRequirementsMet(List<Map<String,dynamic>> requirements){
    for(final requirement in requirements){
      final type=requirement['type'] as String? ?? '';
      if(type=='skill_at_least'||type=='attribute_at_least')continue;
      if(!_conditionsMet([requirement]))return false;
    }
    return true;
  }

  int _challengeChance(Map<String,dynamic> challenge){
    return actionChance(
      attribute:challenge['attribute'] as String? ?? 'willpower',
      skill:challenge['skill'] as String? ?? 'localCulture',
      difficulty:(challenge['difficulty'] as num?)?.toInt()??35,
      modifier:(challenge['modifier'] as num?)?.toInt()??0,
      opponentAttribute:(challenge['opponentAttribute'] as num?)?.toInt(),
      opponentSkill:(challenge['opponentSkill'] as num?)?.toInt(),
      secondarySkill:challenge['secondarySkill'] as String?,
    );
  }

  String _publicChoiceHint(EventChoiceDefinition choice){
    final hint=_renderText(choice.hint);
    if(choice.challenge==null)return hint;
    final label=chanceLabel(_challengeChance(choice.challenge!));
    if(hint.isEmpty)return 'Başarı şansı: $label';
    return '$hint • Başarı şansı: $label';
  }

  void _scheduleSocialPropagation(NpcState npc,int signal,String reason){
    if(signal.abs()<4)return;
    final due=state.day+3+_rng.nextInt(10);
    state.delayedEffects.add(DelayedEffect(
      id:'social_${npc.id}_${state.day}_${state.delayedEffects.length}',
      dueDay:due,type:'social_reputation',source:'npc_memory',
      payload:{'originNpc':npc.id,'carrierNpc':npc.id,'signal':signal,'reason':reason,'hop':0,'reliability':92},
    ));
  }

  void _scheduleFamilyEcho(FamilyMember member,int signal){
    if(signal.abs()<5)return;
    final due=state.day+2+_rng.nextInt(8);
    state.delayedEffects.add(DelayedEffect(
      id:'family_echo_${member.id}_${state.day}_${state.delayedEffects.length}',
      dueDay:due,type:'family_echo',source:'family_memory',
      payload:{'sourceFamily':member.id,'signal':signal},
    ));
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
      final weights={for(final event in candidates)event.id:_directorWeight(event)};
      final total=weights.values.fold<int>(0,(sum,w)=>sum+w);
      var roll=_rng.nextInt(total);
      chosen=candidates.last;
      for(final event in candidates){
        roll-=weights[event.id]!;
        if(roll<0){chosen=event;break;}
      }
      _sync();
    }
    return _toView(chosen);
  }

  bool _eventEligible(EventDefinition def){
    if(def.scheduledOnly)return false;
    if(!_conditionsMet(def.conditions))return false;
    final last=state.eventLastDay[def.id];
    if(last!=null&&state.day-last<def.cooldownDays)return false;
    if(def.major&&!def.emergency&&state.day-state.lastMajorEventDay<8)return false;
    return true;
  }

  EventView _toView(EventDefinition def){
    final options=def.choices.where((choice)=>_choiceRequirementsMet(choice.requirements)).map(
      (choice)=>EventOption(choice.id,_renderText(choice.title),_publicChoiceHint(choice))
    ).toList();
    return EventView(id:def.id,title:_renderText(def.title),body:_renderText(def.body),options:options);
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
        case 'player_age_at_least':
          if(state.age<((value as num).toInt()))return false;
        case 'player_age_at_most':
          if(state.age>((value as num).toInt()))return false;
        case 'player_has_spouse':
          if(state.family[state.playerFamilyId]?.spouseId==null)return false;
        case 'player_no_spouse':
          if(state.family[state.playerFamilyId]?.spouseId!=null)return false;
        case 'family_living_count_at_least':
          if(state.family.values.where((m)=>m.alive).length<((value as num).toInt()))return false;
        case 'family_role_exists':
          if(_familyRoleCandidates(condition['role'] as String).isEmpty)return false;
        case 'family_role_age_at_least':
          final member=_familyRole(condition['role'] as String);if(member==null||member.age<((value as num).toInt()))return false;
        case 'family_relation_at_least':
          final member=_familyRole(condition['role'] as String);if(member==null||_relationAxis(_familyBond(member),condition['axis'] as String)<((value as num).toInt()))return false;
        case 'family_relation_below':
          final member=_familyRole(condition['role'] as String);if(member==null||_relationAxis(_familyBond(member),condition['axis'] as String)>=((value as num).toInt()))return false;
        case 'flag_equals':
          if(state.eventFlags[condition['key']]!=value)return false;
        case 'flag_not_set':
          if(state.eventFlags.containsKey(condition['key']))return false;
        case 'knowledge_unverified_exists':
          if(_latestUnverifiedKnowledge()==null)return false;
        case 'knowledge_fact_unverified':
          if(_latestUnverifiedKnowledge(condition['factId'] as String?)==null)return false;
        case 'world_fact_equals':
          if(state.worldFacts[condition['key']]!=value)return false;
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
    if(!_choiceRequirementsMet(choice.requirements))return 'Bu seçenek için gereken somut şartları artık karşılamıyorsun.';

    if(def.major)state.lastMajorEventDay=state.day;
    state.eventLastDay[def.id]=state.day;
    state.pendingEvents.remove(def.id);
    final firstTask=state.eventFlags['first_task_done']!=true;
    state.eventFlags['first_task_done']=true;
    if(firstTask&&!state.narrativeQueue.contains('milestone_first_task'))state.narrativeQueue.add('milestone_first_task');
    state.recentEventIds.remove(def.id);
    state.recentEventIds.add(def.id);
    while(state.recentEventIds.length>6)state.recentEventIds.removeAt(0);

    ActionRollResult? rolled;
    var resultText=_renderText(choice.resultText);
    if(choice.challenge==null){
      for(final effect in choice.effects)_applyEventEffect(effect,def.id);
    }else{
      final challenge=choice.challenge!;
      for(final effect in ((challenge['alwaysEffects'] as List?)??const[])){
        _applyEventEffect(Map<String,dynamic>.from(effect as Map),def.id);
      }
      rolled=rollAction(
        attribute:challenge['attribute'] as String? ?? 'willpower',
        skill:challenge['skill'] as String? ?? 'localCulture',
        difficulty:(challenge['difficulty'] as num?)?.toInt()??35,
        modifier:(challenge['modifier'] as num?)?.toInt()??0,
        opponentAttribute:(challenge['opponentAttribute'] as num?)?.toInt(),
        opponentSkill:(challenge['opponentSkill'] as num?)?.toInt(),
        secondarySkill:challenge['secondarySkill'] as String?,
      );
      List<dynamic> outcomeEffects;
      String? alternativeText;
      switch(rolled.outcome){
        case ActionOutcome.criticalSuccess:
          outcomeEffects=[...choice.effects,...((challenge['criticalSuccessEffects'] as List?)??const[])];
          alternativeText=challenge['criticalSuccessText'] as String?;
        case ActionOutcome.success:
          outcomeEffects=choice.effects;
          alternativeText=challenge['successText'] as String?;
        case ActionOutcome.partial:
          outcomeEffects=((challenge['partialEffects'] as List?)??const[]);
          alternativeText=challenge['partialText'] as String?;
        case ActionOutcome.failure:
          outcomeEffects=((challenge['failureEffects'] as List?)??const[]);
          alternativeText=challenge['failureText'] as String?;
        case ActionOutcome.criticalFailure:
          final critical=((challenge['criticalFailureEffects'] as List?)??const[]);
          outcomeEffects=critical.isEmpty?((challenge['failureEffects'] as List?)??const[]):critical;
          alternativeText=(challenge['criticalFailureText'] as String?)??challenge['failureText'] as String?;
      }
      for(final effect in outcomeEffects)_applyEventEffect(Map<String,dynamic>.from(effect as Map),def.id);
      if(rolled.succeeded){
        final skill=challenge['skill'] as String?;
        final alreadyProgresses=skill!=null&&choice.effects.any((e)=>e['type']=='skill'&&e['skill']==skill);
        if(skill!=null&&!alreadyProgresses)state.skills[skill]=clamp100((state.skills[skill]??0)+1);
      }
      if(alternativeText!=null)resultText=_renderText(alternativeText);
    }

    final rollNote=rolled==null?'':' • zar ${rolled.roll}';
    state.chronicle.add('${state.day}. gün — ${_renderText(def.title)}: ${_renderText(choice.title)}$rollNote.');
    _sync();
    if(rolled==null)return resultText;
    final outcomeLabel=switch(rolled.outcome){
      ActionOutcome.criticalSuccess=>'Kritik başarı',
      ActionOutcome.success=>'Başarı',
      ActionOutcome.partial=>'Kısmi sonuç',
      ActionOutcome.failure=>'Başarısızlık',
      ActionOutcome.criticalFailure=>'Kritik başarısızlık',
    };
    return 'Zar ${rolled.roll} — $outcomeLabel\n$resultText';
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
          text:_renderText(effect['text'] as String),
          source:_renderText(effect['source'] as String),
          reliability:(effect['reliability'] as num).toInt(),
          day:state.day,
          factId:effect['factId'] as String?,
        ));
      case 'knowledge_random':
        final min=(effect['minReliability'] as num).toInt();
        final max=(effect['maxReliability'] as num).toInt();
        final reliability=min+_rng.nextInt(math.max(1,max-min+1));
        state.knowledge.add(KnowledgeEntry(
          id:'${effect['id']}_${state.day}_${state.knowledge.length}',
          text:_renderText(effect['text'] as String),
          source:_renderText(effect['source'] as String),
          reliability:reliability,
          day:state.day,
          factId:effect['factId'] as String?,
        ));
      case 'knowledge_adjust_recent':
        final factId=effect['factId'] as String?;
        final entry=_latestUnverifiedKnowledge(factId);
        if(entry!=null)entry.reliability=clamp100(entry.reliability+amount);
      case 'knowledge_verify_recent':
        final requested=effect['factId'] as String?;
        final entry=_latestUnverifiedKnowledge(requested);
        final factId=requested??entry?.factId;
        if(entry!=null&&factId!=null&&state.worldFacts.containsKey(factId)){
          final truth=state.worldFacts[factId]!;
          entry.confirmed=truth;
          entry.refuted=!truth;
          entry.reliability=100;
          state.eventFlags['last_verification_result']=truth
            ?'Söylenti güvenilir kanıtlarla doğrulandı.'
            :'Söylenti güvenilir kanıtlarla yanlışlandı.';
        }else{
          state.eventFlags['last_verification_result']='Bilgiyi kesinleştirecek bağımsız bir gerçek kaydı bulunamadı.';
        }
      case 'npc_relation':
        final npc=state.npcs[effect['npc']];
        if(npc!=null){
          final memory=_renderText((effect['memory'] as String?)??'Bu olayda oyuncunun tavrını hatırlıyor');
          final trust=(effect['trust'] as num?)?.toInt()??0;
          final respect=(effect['respect'] as num?)?.toInt()??0;
          final fear=(effect['fear'] as num?)?.toInt()??0;
          final affection=(effect['affection'] as num?)?.toInt()??0;
          final suspicion=(effect['suspicion'] as num?)?.toInt()??0;
          final debt=(effect['debt'] as num?)?.toInt()??0;
          npc.remember(MemoryEntry(
            text:memory,day:state.day,importance:(effect['importance'] as num?)?.toInt()??30,
            trust:trust,respect:respect,fear:fear,affection:affection,suspicion:suspicion,
          ));
          npc.relation.change(debt:debt);
          _scheduleSocialPropagation(npc,trust+respect+affection-suspicion+(debt~/2),memory);
        }
      case 'family_relation':
        final member=_familyRole(effect['role'] as String);
        if(member!=null){
          final current=state.family[state.playerFamilyId]!;
          final a=_familyBond(member);final b=member.relations.putIfAbsent(current.id,()=>RelationState());
          final trust=(effect['trust'] as num?)?.toInt()??0,respect=(effect['respect'] as num?)?.toInt()??0,fear=(effect['fear'] as num?)?.toInt()??0,affection=(effect['affection'] as num?)?.toInt()??0,suspicion=(effect['suspicion'] as num?)?.toInt()??0,debt=(effect['debt'] as num?)?.toInt()??0;
          a.change(trust:trust,respect:respect,fear:fear,affection:affection,suspicion:suspicion,debt:debt);b.change(trust:trust,respect:respect,fear:fear,affection:affection,suspicion:suspicion,debt:-debt);
          _scheduleFamilyEcho(member,trust+respect+affection-suspicion+(debt~/2));
        }
      case 'family_add_spouse':
        final current=state.family[state.playerFamilyId];
        if(current!=null&&current.spouseId==null){final spouse=_addFamilySpouse(current);state.playerGender=current.gender;spouse.isPlayerLine=false;}
      case 'family_add_child':
        final current=state.family[state.playerFamilyId];
        final spouse=current?.spouseId==null?null:state.family[current!.spouseId!];
        if(current!=null&&spouse!=null)_addFamilyChild(current,spouse);
      case 'family_move':
        final member=_familyRole(effect['role'] as String);if(member!=null)member.cityId=state.currentCityId;
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
        if(text!=null)state.chronicle.add('${state.day}. gün — ${_renderText(text)}');
    }
  }

}
