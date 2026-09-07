import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

class StoryDefinition {
  const StoryDefinition({required this.id,required this.title,required this.body,this.kicker='',this.continueLabel='Devam et'});
  final String id,title,body,kicker,continueLabel;
  factory StoryDefinition.fromJson(Map<String,dynamic> j)=>StoryDefinition(
    id:j['id'] as String,
    title:j['title'] as String,
    body:j['body'] as String,
    kicker:j['kicker'] as String? ?? '',
    continueLabel:j['continueLabel'] as String? ?? 'Devam et',
  );
}

class StoryCatalog {
  StoryCatalog(this.scenes);
  final Map<String,StoryDefinition> scenes;
  StoryDefinition? operator [](String id)=>scenes[id];

  static Future<StoryCatalog> loadDefault()async{
    final raw=await rootBundle.loadString('assets/data/story_v1.json');
    final root=Map<String,dynamic>.from(jsonDecode(raw) as Map);
    final scenes=<String,StoryDefinition>{};
    for(final item in (root['scenes'] as List)){
      final scene=StoryDefinition.fromJson(Map<String,dynamic>.from(item as Map));
      if(scenes.containsKey(scene.id))throw FormatException('Tekrarlanan hikâye sahnesi: ${scene.id}');
      scenes[scene.id]=scene;
    }
    return StoryCatalog(scenes);
  }
}
