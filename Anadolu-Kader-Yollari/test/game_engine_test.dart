import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:anadolu_kader_yollari/game_engine.dart';

void main(){
  test('aynı seed aynı başlangıç dünyasını üretir',(){
    final a=GameEngine.newGame(seed:472918321,name:'Hasan',background:'Tüccar ailesi');
    final b=GameEngine.newGame(seed:472918321,name:'Hasan',background:'Tüccar ailesi');
    expect(jsonEncode(a.toJson()),jsonEncode(b.toJson()));
  });

  test('save load deterministik RNG durumunu korur',(){
    final s=GameEngine.newGame(seed:12345,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s);
    e.advance(12);
    final restored=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(jsonEncode(s.toJson()))));
    final e2=GameEngine(restored);
    e.advance(10);
    e2.advance(10);
    expect(jsonEncode(s.toJson()),jsonEncode(restored.toJson()));
  });

  test('NPC hafızası ilişki değerlerini değiştirir',(){
    final s=GameEngine.newGame(seed:8,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s);
    s.currentCityId='kayseri';
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
    final e=GameEngine(s);
    s.currentCityId='kayseri';
    final event=e.pickEvent();
    e.resolve(event,'judge');
    expect(s.delayedEffects,isNotEmpty);
    final due=s.delayedEffects.first.dueDay;
    e.advance(due-s.day);
    expect(s.delayedEffects,isEmpty);
    expect(s.chronicle.any((x)=>x.contains('tahıl soruşturması')),isTrue);
  });

  test('20 önemli NPC ve 4 fraksiyon başlangıçta bulunur',(){
    final s=GameEngine.newGame(seed:1,name:'Hasan',background:'Asker ailesi');
    expect(s.npcs.length,20);
    expect(s.factions.length,4);
    expect(s.cities.length,5);
  });

  test('kıt stok fiyatı yükseltir ve pazar envanteri değiştirir',(){
    final s=GameEngine.newGame(seed:77,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s);
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

  test('hikâye yönetmeni büyük olayları art arda yığmaz',(){
    final s=GameEngine.newGame(seed:91,name:'Hasan',background:'Tüccar ailesi');
    final e=GameEngine(s);
    s.currentCityId='kayseri';
    final major=e.pickEvent();
    expect(major.id,'grain');
    e.resolve(major,'talk');
    final next=e.pickEvent();
    expect(next.id,'rumor');
    e.advance(10);
    expect(e.pickEvent().id,'grain');
  });

  test('nitelik beceri kontrolü aynı seed ile deterministiktir',(){
    final a=GameEngine.newGame(seed:404,name:'Hasan',background:'Asker ailesi');
    final b=GameEngine.newGame(seed:404,name:'Hasan',background:'Asker ailesi');
    final ea=GameEngine(a),eb=GameEngine(b);
    expect(ea.checkScore('strength','military'),eb.checkScore('strength','military'));
    expect(ea.checkScore('rhetoric','diplomacy'),eb.checkScore('rhetoric','diplomacy'));
  });

  test('çatışma sonucu aynı seed ve durumda tekrarlanabilir',(){
    final a=GameEngine.newGame(seed:505,name:'Hasan',background:'Asker ailesi');
    final b=GameEngine.newGame(seed:505,name:'Hasan',background:'Asker ailesi');
    final ra=GameEngine(a).resolveConflict('defend');
    final rb=GameEngine(b).resolveConflict('defend');
    expect(ra.success,rb.success);
    expect(ra.escaped,rb.escaped);
    expect(ra.damage,rb.damage);
    expect(ra.dead,rb.dead);
    expect(ra.injury?.name,rb.injury?.name);
  });

  test('yaralanma save load ile korunur',(){
    final s=GameEngine.newGame(seed:606,name:'Hasan',background:'Köylü ailesi');
    final e=GameEngine(s);
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
    final e=GameEngine(s);
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
    final e=GameEngine(s);
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

}
