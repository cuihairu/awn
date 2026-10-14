import 'package:json_annotation/json_annotation.dart';

part 'session.g.dart';

/// 公开场次视图（GET /api/v1/events/{eventId}/sessions、GET /api/v1/sessions/{sessionId}）。
///
/// 字段与后端 `site.awn.event.api.SessionView` 记录一一对应；
/// 场次可见性 = 父活动可见性，父活动未公开时接口一律 404。
@JsonSerializable()
class SessionView {
  const SessionView({
    required this.id,
    required this.sessionCode,
    required this.eventId,
    required this.name,
    required this.status,
    required this.startTime,
    required this.endTime,
  });

  final int id;
  final String sessionCode;
  final int eventId;
  final String name;

  /// DRAFT / PUBLISHED / CANCELLED（不单独设场次发布流，见 f348026 代决①）。
  final String status;
  final DateTime startTime;
  final DateTime endTime;

  factory SessionView.fromJson(Map<String, dynamic> json) =>
      _$SessionViewFromJson(json);

  Map<String, dynamic> toJson() => _$SessionViewToJson(this);
}
