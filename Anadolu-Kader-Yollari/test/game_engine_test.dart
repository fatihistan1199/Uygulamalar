import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:anadolu_kader_yollari/game_engine.dart';
import 'package:anadolu_kader_yollari/event_catalog.dart';
import 'package:anadolu_kader_yollari/story_catalog.dart';

void main(){
  TestWidgetsFlutterBinding.ensureInitialized();
  late EventCatalog catalog;
  late StoryCatalog stories;
  setUpAll(() async { catalog=await EventCatalog.loadDefault();stories=await StoryCatalog.loadDefault(); });
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


  test('yeni oyun hikâye ile açılır ve oyuncuyu tanıtır',(){
    final s=GameEngine.newGame(seed:1701,name:'Hasan',background:'Köylü ailesi',gender:'male');
    final e=GameEngine(s,catalog);
    expect(s.narrativeQueue,['intro_identity','background_koylu','city_konya','hook_konya','intro_path']);
    final story=e.currentStory(stories)!;
    expect(story.title,'Sen Kimsin?');
    expect(story.body,contains('Hasan'));
    expect(story.body,contains('22 yaşında'));
    expect(story.body,contains('Konya'));
  });

  test('ilk şehir ziyareti özel tanıtım, tekrar ziyaret dönüş sahnesi üretir',(){
    final s=GameEngine.newGame(seed:1702,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.narrativeQueue.clear();
    e.travel('kayseri');
    expect(s.visitedCities,contains('kayseri'));
    expect(s.narrativeQueue,['city_kayseri','hook_kayseri']);
    s.narrativeQueue.clear();
    e.travel('konya');
    expect(s.narrativeQueue.last,'city_return');
  });

  test('hikâye kuyruğu ve ziyaret edilen şehirler save load ile korunur',(){
    final s=GameEngine.newGame(seed:1703,name:'Ayşe',background:'Medrese öğrencisi',gender:'female');
    final e=GameEngine(s,catalog);
    e.completeStory();
    s.visitedCities.add('ankara');
    s.narrativeQueue.add('city_ankara');
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.narrativeQueue,s.narrativeQueue);
    expect(restored.visitedCities,containsAll(['konya','ankara']));
  });

  test('iş yapmak yaşam yolunu ilerletir ve anlatı kilometre taşı üretir',(){
    final s=GameEngine.newGame(seed:1704,name:'Hasan',background:'Asker ailesi');
    final e=GameEngine(s,catalog);
    s.narrativeQueue.clear();
    final before=s.money;
    final result=e.doLocalWork();
    expect(result,isNotEmpty);
    expect(s.eventFlags['first_work_done'],isTrue);
    expect(s.eventFlags['work_count'],1);
    expect(s.money,greaterThanOrEqualTo(before));
    expect(s.narrativeQueue,contains('milestone_first_work'));
    expect(e.lifeGoals().first.complete,isTrue);
  });

  test('ilk şehir olayı yaşam yolu görev kilometre taşını açar',(){
    final s=GameEngine.newGame(seed:1705,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.narrativeQueue.clear();
    s.pendingEvents.add('rumor');
    final event=e.pickEvent();
    e.resolve(event,'listen');
    expect(s.eventFlags['first_task_done'],isTrue);
    expect(s.narrativeQueue,contains('milestone_first_task'));
    expect(e.lifeGoals()[1].complete,isTrue);
  });

  test('şehir atmosferi gerçek şehir durumuna göre anlatı değiştirir',(){
    final s=GameEngine.newGame(seed:1706,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    s.cities['konya']!.food=30;
    expect(e.cityMoodText(),contains('erzak'));
    s.cities['konya']!.food=70;
    s.cities['konya']!.security=35;
    expect(e.cityMoodText(),contains('güvenliği'));
  });


  test('geçici anılar zayıflar, kalıcı anılar korunur',(){
    final s=GameEngine.newGame(seed:1801,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    final npc=s.npcs['selma']!;
    npc.memories.add(MemoryEntry(text:'Geçici olay',day:s.day,importance:40,trust:2,respect:0,fear:0,affection:1,suspicion:0,decay:3));
    npc.memories.add(MemoryEntry(text:'Kalıcı olay',day:s.day,importance:80,trust:8,respect:4,fear:0,affection:6,suspicion:0,decay:5,permanent:true));
    e.decayMemoriesForTest();
    expect(npc.memories[0].importance,37);
    expect(npc.memories[1].importance,80);
    expect(npc.memories[1].permanent,isTrue);
    expect(npc.memories[1].deltaSummary,contains('güven +8'));
  });

  test('üç işten sonra başlangıçta seçilmeyen doğal geçim yolu oluşur',(){
    final s=GameEngine.newGame(seed:1802,name:'Hasan',background:'Asker ailesi');
    final e=GameEngine(s,catalog);
    s.narrativeQueue.clear();
    e.doLocalWork();e.doLocalWork();e.doLocalWork();
    expect(s.eventFlags['work_count'],3);
    expect(s.storyThreads['livelihood'],isNotNull);
    expect(s.narrativeQueue,contains('milestone_work_identity'));
    expect(s.pendingEvents,contains('work_patron_offer'));
    expect(e.workIdentity(),isNot('henüz belirginleşmemiş'));
  });

  test('hikâye iplikleri save load ile korunur',(){
    final s=GameEngine.newGame(seed:1803,name:'Hasan',background:'Tüccar ailesi');
    s.storyThreads['selma_friendship']='dostluk';
    s.storyThreads['nasir_relation']='rekabet';
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.storyThreads['selma_friendship'],'dostluk');
    expect(restored.storyThreads['nasir_relation'],'rekabet');
  });

  test('Selma hikâyesi ilişki nedeni ve aylar sonrası takip üretir',(){
    final s=GameEngine.newGame(seed:11,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.eventFlags['first_task_done']=true;
    s.attributes['rhetoric']=100;s.skills['leadership']=100;s.skills['localCulture']=100;
    s.pendingEvents.add('selma_han_acquaintance');
    final event=e.pickEvent();
    expect(event.id,'selma_han_acquaintance');
    final result=e.resolve(event,'help');
    expect(result,contains('Başarı'));
    expect(s.storyThreads['selma_friendship'],'tanışıklık derinleşiyor');
    expect(s.npcs['selma']!.memories.any((m)=>m.text.contains('Han karıştığında')),isTrue);
    expect(s.delayedEffects.where((x)=>x.type=='event_followup'&&x.payload['eventId']=='selma_han_return').length,1);
    expect(s.narrativeQueue,contains('story_selma_bond'));
  });

  test('Nâsır rekabet kararı kalıcı hafıza ve gecikmiş karşı hamle üretir',(){
    final s=GameEngine.newGame(seed:1805,name:'Hasan',background:'Medrese öğrencisi');
    final e=GameEngine(s,catalog);
    s.currentCityId='konya';
    s.eventFlags['first_task_done']=true;
    s.pendingEvents.add('nasir_offer');
    final event=e.pickEvent();
    expect(event.id,'nasir_offer');
    e.resolve(event,'refuse');
    expect(s.storyThreads['nasir_relation'],'rekabet');
    expect(s.npcs['nasir']!.memories.last.permanent,isTrue);
    expect(s.delayedEffects.any((x)=>x.payload['eventId']=='nasir_rivalry_return'),isTrue);
    expect(s.narrativeQueue,contains('story_nasir_rivalry'));
  });

  test('NPC ile evlilik oyuncu ailesine ve toplumsal ağa bağlanır',(){
    final s=GameEngine.newGame(seed:1806,name:'Hasan',background:'Köylü ailesi',gender:'male');
    final e=GameEngine(s,catalog);
    final before=s.factions['tuccar']!.reputation;
    final spouse=e.marryNpcForTest('leyla');
    expect(spouse,isNotNull);
    expect(spouse!.linkedNpcId,'leyla');
    expect(spouse.networkFactionId,'tuccar');
    expect(s.family[s.playerFamilyId]!.spouseId,spouse.id);
    expect(s.npcs['leyla']!.spouseId,startsWith('family:'));
    expect(s.factions['tuccar']!.reputation,before+4);
    expect(e.householdNetworkSummary(),contains('Tüccarlar'));
  });

  test('kişisel hikâye olayları katalogda uzun zincir olarak bulunur',(){
    expect(catalog['selma_han_acquaintance']?.chainId,'selma_personal');
    expect(catalog['selma_old_favor']?.chainStage,3);
    expect(catalog['nasir_rivalry_return']?.scheduledOnly,isTrue);
    expect(catalog['leyla_courtship']?.tags,contains('romance'));
    expect(catalog.events.length,greaterThanOrEqualTo(36));
    expect(stories.scenes.containsKey('milestone_work_identity'),isTrue);
  });


  test('geçici anılar eskir, kalıcı anılar ilişkiyi sabitler',(){
    final s=GameEngine.newGame(seed:1801,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    final selma=s.npcs['selma']!;
    selma.remember(MemoryEntry(text:'Geçici bir yardım',day:s.day,importance:20,trust:5,respect:0,fear:0,affection:2,suspicion:0,decay:3));
    expect(selma.relation.trust,55);
    e.decayMemoriesForTest();
    expect(selma.memories.last.importance,17);
    expect(selma.relation.trust,54);

    selma.remember(MemoryEntry(text:'Hayat boyu unutmayacağı bir davranış',day:s.day,importance:80,trust:4,respect:3,fear:0,affection:5,suspicion:0,decay:0,permanent:true,tags:['permanent']));
    final trustAfter=selma.relation.trust;
    e.decayMemoriesForTest();
    expect(selma.memories.last.importance,80);
    expect(selma.memories.last.permanent,isTrue);
    expect(selma.relation.trust,trustAfter);
  });

  test('tekrarlanan işler doğal geçim kimliği ve hikâye ipliği oluşturur',(){
    final s=GameEngine.newGame(seed:1802,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    s.narrativeQueue.clear();
    e.doLocalWork();e.doLocalWork();e.doLocalWork();
    expect(s.eventFlags['work_count'],3);
    expect(e.workIdentity(),isNot('henüz belirginleşmemiş'));
    expect(s.storyThreads['livelihood'],e.workIdentity());
    expect(s.pendingEvents,contains('work_patron_offer'));
    expect(s.narrativeQueue,contains('milestone_work_identity'));
  });

  test('düzenli iş ağı aylar sonra daha büyük sorumlulukla geri döner',(){
    final s=GameEngine.newGame(seed:1803,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.eventFlags['work_count']=3;
    s.storyThreads['livelihood']='han ve şehir işlerinde eli alışmış biri';
    s.pendingEvents.add('work_patron_offer');
    final offer=e.pickEvent();
    expect(offer.id,'work_patron_offer');
    e.resolve(offer,'accept');
    final delayed=s.delayedEffects.firstWhere((x)=>x.type=='event_followup'&&x.payload['eventId']=='work_patron_return');
    expect(delayed.dueDay-s.day,inInclusiveRange(120,210));
    e.advance(delayed.dueDay-s.day);
    expect(s.pendingEvents,contains('work_patron_return'));
    expect(e.pickEvent().id,'work_patron_return');
  });

  test('Selma dostluğu kalıcı anıya ve yaklaşık iki yıl sonraki sonuca uzanır',(){
    final s=GameEngine.newGame(seed:1,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s,catalog);
    s.currentCityId='kayseri';
    s.eventFlags['first_task_done']=true;
    s.attributes['rhetoric']=100;s.skills['leadership']=100;s.skills['localCulture']=100;
    s.pendingEvents.add('selma_han_acquaintance');
    final first=e.pickEvent();
    expect(first.id,'selma_han_acquaintance');
    e.resolve(first,'help');
    expect(s.storyThreads['selma_friendship'],'tanışıklık derinleşiyor');
    final returns=s.delayedEffects.where((x)=>x.payload['eventId']=='selma_han_return').toList();
    expect(returns.length,1);

    e.advance(returns.single.dueDay-s.day);
    final second=e.pickEvent();
    expect(second.id,'selma_han_return');
    e.resolve(second,'stand_by');
    expect(s.storyThreads['selma_friendship'],'dostluk');
    expect(s.npcs['selma']!.memories.any((m)=>m.permanent&&m.tags.contains('friendship')),isTrue);

    final oldFavor=s.delayedEffects.firstWhere((x)=>x.payload['eventId']=='selma_old_favor');
    e.advance(oldFavor.dueDay-s.day);
    final third=e.pickEvent();
    expect(third.id,'selma_old_favor');
    e.resolve(third,'waive');
    expect(s.storyThreads['selma_friendship'],'yakın dostluk');

    final years=s.delayedEffects.firstWhere((x)=>x.payload['eventId']=='selma_years_later');
    expect(years.dueDay-s.day,inInclusiveRange(600,900));
    e.advance(years.dueDay-s.day);
    expect(e.pickEvent().id,'selma_years_later');
  });

  test('adı olan NPC ile evlilik aileyi gerçek toplumsal ağa bağlar',(){
    final s=GameEngine.newGame(seed:1,name:'Hasan',background:'Tüccar ailesi',gender:'male');
    s.currentCityId='antalya';
    s.storyThreads['leyla_romance']='yakınlık';
    s.attributes['rhetoric']=100;s.skills['diplomacy']=100;
    s.pendingEvents.add('leyla_courtship');
    final e=GameEngine(s,catalog);
    final event=e.pickEvent();
    expect(event.id,'leyla_courtship');
    final beforeRep=s.factions['tuccar']!.reputation;
    e.resolve(event,'marry');
    final player=s.family[s.playerFamilyId]!;
    expect(player.spouseId,isNotNull);
    final spouse=s.family[player.spouseId!]!;
    expect(spouse.linkedNpcId,'leyla');
    expect(spouse.networkFactionId,'tuccar');
    expect(s.npcs['leyla']!.spouseId,startsWith('family:'));
    expect(s.storyThreads['leyla_romance'],'evlilik');
    expect(s.factions['tuccar']!.reputation,greaterThan(beforeRep));
    expect(e.householdNetworkSummary(),contains('Tüccarlar'));
    expect(s.narrativeQueue,contains('story_named_marriage'));
  });

  test('hikâye iplikleri ve kalıcı hafıza save load ile korunur',(){
    final s=GameEngine.newGame(seed:1806,name:'Hasan',background:'Asker ailesi');
    s.storyThreads['nasir_relation']='açık rekabet';
    s.npcs['nasir']!.remember(MemoryEntry(text:'Eski rekabeti unutmadı',day:s.day,importance:88,trust:-4,respect:5,fear:0,affection:0,suspicion:7,permanent:true,decay:0,tags:['rivalry']));
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    expect(restored.storyThreads['nasir_relation'],'açık rekabet');
    final memory=restored.npcs['nasir']!.memories.last;
    expect(memory.permanent,isTrue);
    expect(memory.tags,contains('rivalry'));
    expect(memory.deltaSummary,contains('şüphe +7'));
  });

}
