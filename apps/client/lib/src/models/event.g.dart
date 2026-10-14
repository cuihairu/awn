// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventView _$EventViewFromJson(Map<String, dynamic> json) => EventView(
  id: (json['id'] as num).toInt(),
  eventCode: json['eventCode'] as String,
  name: json['name'] as String,
  status: json['status'] as String,
  startTime: DateTime.parse(json['startTime'] as String),
  endTime: DateTime.parse(json['endTime'] as String),
  venueId: (json['venueId'] as num?)?.toInt(),
);

Map<String, dynamic> _$EventViewToJson(EventView instance) => <String, dynamic>{
  'id': instance.id,
  'eventCode': instance.eventCode,
  'name': instance.name,
  'status': instance.status,
  'startTime': instance.startTime.toIso8601String(),
  'endTime': instance.endTime.toIso8601String(),
  'venueId': instance.venueId,
};
