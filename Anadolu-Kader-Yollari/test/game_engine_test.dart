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
    final event=e.pickEvent();
    e.resolve(event,'judge');
    expect(s.delayedEffects,isNotEmpty);
    final due=s.delayedEffects.first.dueDay;
    e.advance(due-s.day);
    expect(s.delayedEffects,isEmpty);
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

  test('seçenek gereksinimleri veri üzerinden filtrelenir',(){
    final s=GameEngine.newGame(seed:909,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s,catalog);
    s.pendingEvents.add('lost_letter');
    final first=e.pickEvent();
    expect(first.id,'lost_letter');
    expect(first.options.any((o)=>o.id=='open'),isFalse);
    s.skills['espionage']=30;
    final second=e.pickEvent();
    expect(second.options.any((o)=>o.id=='open'),isTrue);
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

}
