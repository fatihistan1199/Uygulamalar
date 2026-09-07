import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:anadolu_kader_yollari/game_engine.dart';
import 'package:anadolu_kader_yollari/event_catalog.dart';

void main(){
  TestWidgetsFlutterBinding.ensureInitialized();
  late EventCatalog catalog;
  setUpAll(() async { catalog=await EventCatalog.loadDefault(); });
  test('aynı seed aynı başlangıç dünyasını üretir',(){
    final a=GameEngine.newGame(seed:472918321,name:'Hasan',background:'Tüccar ailesi');
    final b=GameEngine.newGame(seed:472918321,name:'Hasan',background:'Tüccar ailesi');
    expect(jsonEncode(a.toJson()),jsonEncode(b.toJson()));
  });

  test('save load deterministik RNG durumunu korur',(){
    final s=GameEngine.newGame(seed:12345,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    e.advance(12);
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    final e2=GameEngine(restored,catalog);
    e.advance(10);
    e2.advance(10);
    expect(jsonEncode(s.toJson()),jsonEncode(restored.toJson()));
  });

  test('NPC hafızası ilişki değerlerini değiştirir',(){
    final s=GameEngine.newGame(seed:8,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final event=e.pickEvent();
    expect(event.id,'grain');
    final before=s.npcs['mahmud']!.relation.trust;
    e.resolve(event,'talk');
    expect(s.npcs['mahmud']!.relation.trust,greaterThan(before));
    expect(s.npcs['mahmud']!.memories,isNotEmpty);
    expect(s.knowledge,isNotEmpty);
  });

  test('gecikmiş sonuç kuyrukta kalır ve zamanı gelince çözülür',(){
    final s=GameEngine.newGame(seed:99,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final event=e.pickEvent();
    expect(event.id,'grain');
    e.resolve(event,'judge');
    expect(s.delayedEffects,isNotEmpty);
    final due=s.delayedEffects.firstWhere((x)=>x.type=='event_followup'&&x.payload['eventId']=='grain_followup').dueDay;
    e.advance(due-s.day);
    expect(s.delayedEffects.any((x)=>x.type=='event_followup'&&x.payload['eventId']=='grain_followup'),isFalse);
    expect(s.pendingEvents,contains('grain_followup'));
  });

  test('20 önemli NPC ve 4 fraksiyon başlangıçta bulunur',(){
    final s=GameEngine.newGame(seed:1,name:'Hasan',background:'Asker ailesi');
    expect(s.npcs.length,20);
    expect(s.factions.length,4);
    expect(s.cities.length,5);
  });

  test('kıt stok fiyatı yükseltir ve pazar envanteri değiştirir',(){
    final s=GameEngine.newGame(seed:77,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.cities['kayseri']!.stock['grain']=30;
    final scarce=e.marketPrice('grain');
    s.cities['kayseri']!.stock['grain']=90;
    final abundant=e.marketPrice('grain');
    expect(scarce,greaterThan(abundant));
    final beforeMoney=s.money;
    final result=e.buyGood('grain');
    expect(result,contains('ödedin'));
    expect(s.inventory['grain'],1);
    expect(s.money,lessThan(beforeMoney));
  });

  test('hikâye yönetmeni olağan büyük olayları art arda yığmaz',(){
    final s=GameEngine.newGame(seed:91,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final major=e.pickEvent();
    expect(major.id,'grain');
    e.resolve(major,'talk');
    s.currentCityId='sivas';
    s.tension=80;
    s.cities['sivas']!.trade=60;
    final next=e.pickEvent();
    expect(next.id,isNot('faction_dispute'));
    expect(s.lastMajorEventDay,s.day);
  });

  test('nitelik beceri kontrolü aynı seed ile deterministiktir',(){
    final a=GameEngine.newGame(seed:404,name:'Hasan',background:'Asker ailesi');
    final b=GameEngine.newGame(seed:404,name:'Hasan',background:'Asker ailesi');
    final ea=GameEngine(a,catalog),eb=GameEngine(b,catalog);
    expect(ea.checkScore('strength','military'),eb.checkScore('strength','military'));
    expect(ea.checkScore('rhetoric','diplomacy'),eb.checkScore('rhetoric','diplomacy'));
  });

  test('çatışma sonucu aynı seed ve durumda tekrarlanabilir',(){
    final a=GameEngine.newGame(seed:505,name:'Hasan',background:'Asker ailesi');
    final b=GameEngine.newGame(seed:505,name:'Hasan',background:'Asker ailesi');
    final ra=GameEngine(a,catalog).resolveConflict('defend');
    final rb=GameEngine(b,catalog).resolveConflict('defend');
    expect(ra.success,rb.success);
    expect(ra.escaped,rb.escaped);
    expect(ra.damage,rb.damage);
    expect(ra.dead,rb.dead);
    expect(ra.injury?.name,rb.injury?.name);
  });

  test('yaralanma save load ile korunur',(){
    final s=GameEngine.newGame(seed:606,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    e.forceDamageForTest(30);
    expect(s.injuries,isNotEmpty);
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.health,s.health);
    expect(restored.injuries.length,s.injuries.length);
    expect(restored.injuries.first.name,s.injuries.first.name);
    expect(restored.injuries.first.severity,s.injuries.first.severity);
  });

  test('ölüm dünyayı sıfırlamaz',(){
    final s=GameEngine.newGame(seed:707,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.cities['kayseri']!.food=37;
    s.factions['ahi']!.power=71;
    final npcCount=s.npcs.length;
    e.forceDamageForTest(200,cause:'Test ölümü');
    expect(s.alive,isFalse);
    expect(s.currentCityId,'kayseri');
    expect(s.cities['kayseri']!.food,37);
    expect(s.factions['ahi']!.power,71);
    expect(s.npcs.length,npcCount);
  });

  test('halef aynı dünya üzerinde yeni nesille devam eder',(){
    final s=GameEngine.newGame(seed:808,name:'Hasan',background:'Medrese öğrencisi');
    final e=GameEngine(s,catalog);
    s.currentCityId='sivas';
    s.cities['sivas']!.trade=83;
    s.factions['yonetim']!.power=76;
    final mahmudCity=s.npcs['mahmud']!.cityId;
    e.forceDamageForTest(200,cause:'Test ölümü');
    final next=e.successorOptions().first;
    final message=e.assumeSuccessor(next);
    expect(message,contains('2. nesil'));
    expect(s.alive,isTrue);
    expect(s.generation,2);
    expect(s.currentCityId,'sivas');
    expect(s.cities['sivas']!.trade,83);
    expect(s.factions['yonetim']!.power,76);
    expect(s.npcs['mahmud']!.cityId,mahmudCity);
    expect(s.lineage,contains('Hasan'));
  });

  test('aile soy ağı save load ile bütün bağlarını korur',(){
    final s=GameEngine.newGame(seed:812,name:'Hasan',background:'Köylü ailesi');
    expect(s.family['player']!.parentIds,containsAll(['parent_1','parent_2']));
    expect(s.family['parent_1']!.childIds,contains('player'));
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.playerFamilyId,'player');
    expect(restored.family['player']!.name,'Hasan');
    expect(restored.family['sibling_1']!.parentIds,contains('parent_1'));
  });

  test('NPC-NPC ilişkileri ve yaşayan nesil deterministiktir',(){
    final a=GameEngine.newGame(seed:1922,name:'Hasan',background:'Tüccar ailesi');
    final b=GameEngine.newGame(seed:1922,name:'Hasan',background:'Tüccar ailesi');
    final ea=GameEngine(a,catalog),eb=GameEngine(b,catalog);
    expect(a.npcs['mahmud']!.npcRelations['selma']!.affection,62);
    ea.advance(720);eb.advance(720);
    expect(jsonEncode(a.toJson()),jsonEncode(b.toJson()));
    expect(a.age,24);
    expect(a.npcs['mahmud']!.age,greaterThanOrEqualTo(26));
  });

  test('halef öncelikle kaydedilmiş aile üyesinden seçilir',(){
    final s=GameEngine.newGame(seed:813,name:'Hasan',background:'Medrese öğrencisi');
    final e=GameEngine(s,catalog);
    e.forceDamageForTest(200,cause:'Test ölümü');
    final candidate=e.successorOptions().first;
    expect(s.family.values.any((m)=>m.name==candidate&&m.alive),isTrue);
    e.assumeSuccessor(candidate);
    expect(s.playerFamilyId,isNot('player'));
    expect(s.family[s.playerFamilyId]!.name,candidate);
  });


  test('haricî olay kataloğu yüklenir ve çekirdek olayları içerir',(){
    expect(catalog.events.length,greaterThanOrEqualTo(14));
    expect(catalog['grain'],isNotNull);
    expect(catalog['grain_followup']?.chainId,'kayseri_grain');
  });

  test('gizli beceri yetersizliği seçeneği kilitlemez, yalnız şansı değiştirir',(){
    final s=GameEngine.newGame(seed:909,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    s.pendingEvents.add('lost_letter');
    final first=e.pickEvent();
    expect(first.id,'lost_letter');
    final weak=first.options.firstWhere((o)=>o.id=='open');
    expect(weak.hint,contains('Başarı şansı'));
    expect(weak.hint,contains('Çok zor'));
    s.skills['espionage']=60;
    final second=e.pickEvent();
    final strong=second.options.firstWhere((o)=>o.id=='open');
    expect(strong.hint,contains('Dengeli'));
  });

  test('veri odaklı olay zinciri gecikerek devam eder',(){
    final s=GameEngine.newGame(seed:1001,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final grain=e.pickEvent();
    e.resolve(grain,'judge');
    final followup= s.delayedEffects.firstWhere((x)=>x.type=='event_followup');
    e.advance(followup.dueDay-s.day);
    expect(s.pendingEvents,contains('grain_followup'));
    final next=e.pickEvent();
    expect(next.id,'grain_followup');
  });

  test('olay bayrakları kuyruk ve cooldown save load ile korunur',(){
    final s=GameEngine.newGame(seed:1111,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final event=e.pickEvent();
    e.resolve(event,'talk');
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.eventFlags['grain_position'],'listened');
    expect(restored.eventLastDay['grain'],s.day);
    expect(restored.delayedEffects.any((x)=>x.type=='event_followup'),isTrue);
  });


  test('oyuncu cinsiyeti ve aile bağları save load ile korunur',(){
    final s=GameEngine.newGame(seed:1401,name:'Ayşe',background:'Tüccar ailesi',gender:'female');
    expect(s.playerGender,'female');
    expect(s.family['player']!.gender,'female');
    expect(s.family['player']!.relations['sibling_1']!.trust,61);
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.playerGender,'female');
    expect(restored.family['player']!.relations['sibling_1']!.affection,58);
  });

  test('aile evlilik olayı eş ve gecikmiş çocuk zinciri üretir',(){
    final s=GameEngine.newGame(seed:1402,name:'Hasan',background:'Tüccar ailesi',gender:'male');
    final e=GameEngine(s,catalog);
    s.pendingEvents.add('family_marriage_offer');
    final marriage=e.pickEvent();
    expect(marriage.id,'family_marriage_offer');
    final before=s.family.length;
    e.resolve(marriage,'accept');
    final player=s.family[s.playerFamilyId]!;
    expect(player.spouseId,isNotNull);
    expect(s.family.length,before+1);
    expect(s.delayedEffects.any((x)=>x.payload['eventId']=='family_child_birth'),isTrue);
    final due=s.delayedEffects.firstWhere((x)=>x.payload['eventId']=='family_child_birth').dueDay;
    e.advance(due-s.day);
    expect(s.pendingEvents,contains('family_child_birth'));
    final birth=e.pickEvent();
    expect(birth.id,'family_child_birth');
    final childrenBefore=player.childIds.length;
    e.resolve(birth,'family_help');
    expect(player.childIds.length,childrenBefore+1);
    expect(s.family[player.childIds.last]!.parentIds,contains(player.id));
  });

  test('akrabalık şablonları gerçek isimlerle render edilir ve bağ etkisi uygulanır',(){
    final s=GameEngine.newGame(seed:1403,name:'Hasan',background:'Köylü ailesi',gender:'male');
    final e=GameEngine(s,catalog);
    s.pendingEvents.add('family_sibling_debt');
    final event=e.pickEvent();
    expect(event.id,'family_sibling_debt');
    expect(event.title,contains('Zehra'));
    expect(event.body,contains('Zehra'));
    expect(event.body, isNot(contains('{{sibling}}')));
    final before=s.family['player']!.relations['sibling_1']!.trust;
    e.resolve(event,'refuse');
    expect(s.family['player']!.relations['sibling_1']!.trust,before-9);
    expect(s.family['sibling_1']!.relations['player']!.trust,before-9);
  });

  test('aile olayı koşulları eş durumuna göre seçenek havuzunu değiştirir',(){
    final s=GameEngine.newGame(seed:1404,name:'Hasan',background:'Asker ailesi',gender:'male');
    final e=GameEngine(s,catalog);
    s.pendingEvents.add('family_marriage_offer');
    final marriage=e.pickEvent();
    expect(marriage.id,'family_marriage_offer');
    e.resolve(marriage,'accept');
    s.pendingEvents.add('family_marriage_offer');
    final next=e.pickEvent();
    expect(next.id,isNot('family_marriage_offer'));
  });

  test('halef seçildiğinde cinsiyet de gerçek aile üyesinden devralınır',(){
    final s=GameEngine.newGame(seed:1405,name:'Hasan',background:'Medrese öğrencisi',gender:'male');
    final e=GameEngine(s,catalog);
    e.forceDamageForTest(200,cause:'Test ölümü');
    expect(e.successorOptions(),contains('Zehra'));
    e.assumeSuccessor('Zehra');
    expect(s.playerGender,'female');
    expect(s.family[s.playerFamilyId]!.gender,'female');
  });


  test('10 güce karşı 20 güçte düşük ama sıfır olmayan kazanma şansı vardır',(){
    final s=GameEngine.newGame(seed:7,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    s.attributes['strength']=10;
    s.skills['military']=20;
    final chance=e.actionChance(attribute:'strength',skill:'military',opponentAttribute:20,opponentSkill:20);
    expect(chance,greaterThanOrEqualTo(5));
    expect(chance,lessThan(50));
    final result=e.rollAction(attribute:'strength',skill:'military',opponentAttribute:20,opponentSkill:20);
    expect(result.chance,chance);
    expect(result.succeeded,isTrue);
  });

  test('aynı kayıt durumu aynı riskli seçimde aynı zarı üretir',(){
    final a=GameEngine.newGame(seed:1502,name:'Hasan',background:'Köylü ailesi');
    final b=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(a.toJson()))));
    final ea=GameEngine(a,catalog),eb=GameEngine(b,catalog);
    a.pendingEvents.add('warehouse_shadow');b.pendingEvents.add('warehouse_shadow');
    final va=ea.pickEvent(),vb=eb.pickEvent();
    expect(va.id,'warehouse_shadow');expect(vb.id,'warehouse_shadow');
    final ra=ea.resolve(va,'hide');
    final rb=eb.resolve(vb,'hide');
    expect(ra,rb);
    expect(jsonEncode(a.toJson()),jsonEncode(b.toJson()));
  });

  test('saklanma seçeneği düşük casuslukta da görünür ve zarla çözülür',(){
    final s=GameEngine.newGame(seed:1503,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    s.skills['espionage']=1;s.attributes['agility']=15;
    s.pendingEvents.add('warehouse_shadow');
    final event=e.pickEvent();
    final hide=event.options.firstWhere((o)=>o.id=='hide');
    expect(hide.hint,contains('Başarı şansı'));
    final result=e.resolve(event,'hide');
    expect(result,startsWith('Zar '));
  });

  test('NPC hedefleri oyuncudan bağımsız somut dünya eylemleri üretir',(){
    final s=GameEngine.newGame(seed:1504,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    e.advance(28);
    expect(s.chronicle.any((x)=>x.contains('kendi hedefleri doğrultusunda hareket etti')),isTrue);
  });

  test('güçlü NPC deneyimi gecikmeli sosyal yayılım kuyruğu oluşturur',(){
    final s=GameEngine.newGame(seed:1505,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final event=e.pickEvent();
    e.resolve(event,'talk');
    expect(s.delayedEffects.any((x)=>x.type=='social_reputation'&&x.payload['originNpc']=='mahmud'&&x.payload['carrierNpc']=='mahmud'),isTrue);
  });


  test('Story Director büyük olay sonrası toparlanma moduna geçer',(){
    final s=GameEngine.newGame(seed:1601,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.lastMajorEventDay=s.day;
    expect(e.storyDirectorMode(),'recovery');
    expect(e.directorWeightForTest('rumor'),greaterThan(catalog['rumor']!.weight));
    expect(e.directorWeightForTest('market_fire'),lessThan(catalog['market_fire']!.weight));
  });

  test('dünya gerçeği oyuncu bilgisinden ayrı tutulur ve save load korunur',(){
    final s=GameEngine.newGame(seed:1602,name:'Hasan',background:'Köylü ailesi');
    expect(s.worldFacts.containsKey('tax_plan'),isTrue);
    expect(s.knowledge.where((k)=>k.factId=='tax_plan'),isEmpty);
    final e=GameEngine(s,catalog);
    s.pendingEvents.add('rumor');
    final rumor=e.pickEvent();
    e.resolve(rumor,'listen');
    expect(s.knowledge.any((k)=>k.factId=='tax_plan'),isTrue);
    expect(s.recentEventIds,contains('rumor'));
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.worldFacts['tax_plan'],s.worldFacts['tax_plan']);
    expect(restored.recentEventIds,contains('rumor'));
  });

  test('vergi söylentisi gerçek dünya olgusuna göre yanlışlanabilir',(){
    final s=GameEngine.newGame(seed:1,name:'Hasan',background:'Medrese öğrencisi');
    final e=GameEngine(s,catalog);
    expect(s.worldFacts['tax_plan'],isFalse);
    s.attributes['intuition']=100;
    s.skills['localCulture']=100;
    s.knowledge.add(KnowledgeEntry(id:'tax_test',text:'Yeni vergi hazırlanıyor.',source:'Test kaynağı',reliability:60,day:s.day,factId:'tax_plan'));
    s.pendingEvents.add('tax_rumor_verification');
    final event=e.pickEvent();
    expect(event.id,'tax_rumor_verification');
    final result=e.resolve(event,'cross_check');
    expect(result,contains('yanlışlandı'));
    final entry=s.knowledge.firstWhere((k)=>k.id=='tax_test');
    expect(entry.refuted,isTrue);
    expect(entry.confirmed,isFalse);
    expect(entry.reliability,100);
  });

  test('Mahmud ve Yusuf hedefleri oyuncudan bağımsız zincir oluşturur',(){
    final s=GameEngine.newGame(seed:1604,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.cities['kayseri']!.food=48;
    e.runNpcGoalForTest('mahmud');
    expect(s.worldFacts['mahmud_stockpiling'],isTrue);
    expect(s.eventFlags['mahmud_stockpiling'],isTrue);
    e.runNpcGoalForTest('yusuf');
    expect(s.worldFacts['ahi_countermove'],isTrue);
    expect(s.eventFlags['ahi_countermove'],isTrue);
    s.currentCityId='kayseri';
    s.pendingEvents.add('kayseri_network_pressure');
    expect(e.pickEvent().id,'kayseri_network_pressure');
  });

  test('sosyal yayılım kaynağı taşıyıcıyı ve mesafeyi kaydeder',(){
    final s=GameEngine.newGame(seed:1605,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.pendingEvents.add('grain');
    final event=e.pickEvent();
    e.resolve(event,'talk');
    final social=s.delayedEffects.firstWhere((x)=>x.type=='social_reputation');
    expect(social.payload['originNpc'],'mahmud');
    expect(social.payload['carrierNpc'],'mahmud');
    expect(social.payload['hop'],0);
    expect((social.payload['reliability'] as int),greaterThan(80));
  });

}
