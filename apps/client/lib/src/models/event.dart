import 'package:json_annotation/json_annotation.dart';

part 'event.g.dart';

/// 公开活动视图（GET /api/v1/events、GET /api/v1/events/{eventId}）。
///
/// 字段与后端 `site.awn.event.api.EventView` 记录一一对应
/// （docs/api/contracts.md「活动与场次为公开只读」）；Long 字段在 JSON 中为数字。
@JsonSerializable()
class EventView {
  const EventView({
    required this.id,
    required this.eventCode,
    required this.name,
    required this.status,
    required this.startTime,
    required this.endTime,
    required this.venueId,
  });

  final int id;
  final String eventCode;
  final String name;

  /// DRAFT / PUBLISHED / CANCELLED / COMPLETED；公开列表只含 PUBLISHED / COMPLETED。
  final String status;
  final DateTime startTime;
  final DateTime endTime;

  /// 场馆 ID；地理字段后端未落地前为 null（todo 125），不臆造展示。
  final int? venueId;

  bool get isPublished => status == 'PUBLISHED';
  bool get isCompleted => status == 'COMPLETED';

  factory EventView.fromJson(Map<String, dynamic> json) =>
      _$EventViewFromJson(json);

  Map<String, dynamic> toJson() => _$EventViewToJson(this);
}
