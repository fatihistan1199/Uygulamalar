import 'dart:convert';
import 'package:flutter/services.dart';

class EventChoiceDefinition {
  EventChoiceDefinition({
    required this.id,
    required this.title,
    required this.hint,
    required this.resultText,
    required this.effects,
    required this.requirements,
    this.challenge,
  });

  final String id;
  final String title;
  final String hint;
  final String resultText;
  final List<Map<String, dynamic>> effects;
  final List<Map<String, dynamic>> requirements;
  final Map<String, dynamic>? challenge;

  factory EventChoiceDefinition.fromJson(Map<String, dynamic> j) =>
      EventChoiceDefinition(
        id: j['id'] as String,
        title: j['title'] as String,
        hint: j['hint'] as String? ?? '',
        resultText: j['resultText'] as String? ?? '',
        effects: ((j['effects'] as List?) ?? const [])
            .map((e) => Map<String, dynamic>.from(e as Map))
            .toList(),
        requirements: ((j['requirements'] as List?) ?? const [])
            .map((e) => Map<String, dynamic>.from(e as Map))
            .toList(),
        challenge: j['challenge'] == null ? null : Map<String, dynamic>.from(j['challenge'] as Map),
      );
}

class EventDefinition {
  EventDefinition({
    required this.id,
    required this.category,
    required this.title,
    required this.body,
    required this.weight,
    required this.major,
    required this.cooldownDays,
    required this.conditions,
    required this.choices,
    required this.tags,
    this.chainId,
    this.chainStage,
    this.emergency = false,
    this.scheduledOnly = false,
  });

  final String id;
  final String category;
  final String title;
  final String body;
  final int weight;
  final bool major;
  final int cooldownDays;
  final List<Map<String, dynamic>> conditions;
  final List<EventChoiceDefinition> choices;
  final List<String> tags;
  final String? chainId;
  final int? chainStage;
  final bool emergency;
  final bool scheduledOnly;

  factory EventDefinition.fromJson(Map<String, dynamic> j) => EventDefinition(
        id: j['id'] as String,
        category: j['category'] as String? ?? 'general',
        title: j['title'] as String,
        body: j['body'] as String,
        weight: j['weight'] as int? ?? 50,
        major: j['major'] as bool? ?? false,
        cooldownDays: j['cooldownDays'] as int? ?? 7,
        conditions: ((j['conditions'] as List?) ?? const [])
            .map((e) => Map<String, dynamic>.from(e as Map))
            .toList(),
        choices: (j['choices'] as List)
            .map((e) =>
                EventChoiceDefinition.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        tags: ((j['tags'] as List?) ?? const []).cast<String>(),
        chainId: j['chainId'] as String?,
        chainStage: j['chainStage'] as int?,
        emergency: j['emergency'] as bool? ?? false,
        scheduledOnly: j['scheduledOnly'] as bool? ?? false,
      );
}

class EventCatalog {
  EventCatalog(List<EventDefinition> events)
      : events = {for (final event in events) event.id: event};

  final Map<String, EventDefinition> events;

  EventDefinition? operator [](String id) => events[id];

  static EventCatalog fromJsonString(String raw) {
    final root = Map<String, dynamic>.from(jsonDecode(raw) as Map);
    final list = (root['events'] as List)
        .map((e) => EventDefinition.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
    return EventCatalog(list);
  }

  static Future<EventCatalog> loadDefault() async {
    final raw = await rootBundle.loadString('assets/data/events_v1.json');
    return fromJsonString(raw);
  }
}
