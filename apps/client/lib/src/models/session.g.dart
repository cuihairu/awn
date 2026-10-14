// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionView _$SessionViewFromJson(Map<String, dynamic> json) => SessionView(
  id: (json['id'] as num).toInt(),
  sessionCode: json['sessionCode'] as String,
  eventId: (json['eventId'] as num).toInt(),
  name: json['name'] as String,
  status: json['status'] as String,
  startTime: DateTime.parse(json['startTime'] as String),
  endTime: DateTime.parse(json['endTime'] as String),
);

Map<String, dynamic> _$SessionViewToJson(SessionView instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionCode': instance.sessionCode,
      'eventId': instance.eventId,
      'name': instance.name,
      'status': instance.status,
      'startTime': instance.startTime.toIso8601String(),
      'endTime': instance.endTime.toIso8601String(),
    };
