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
}
