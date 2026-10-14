import 'package:json_annotation/json_annotation.dart';

part 'product.g.dart';

/// 场次商品列表项（GET /api/v1/sessions/{sessionId}/products）。
///
/// 字段与后端 `site.awn.api.ProductListItem` 记录一一对应；
/// 金额单位分（Long），`unitPriceYuan` 为展示用元值。
@JsonSerializable()
class ProductListItem {
  const ProductListItem({
    required this.productCode,
    required this.name,
    required this.status,
    required this.unitPrice,
    required this.categoryId,
    required this.sessionId,
    required this.poolCode,
    required this.available,
  });

  final String productCode;
  final String name;

  /// DRAFT / PUBLISHED / CANCELLED；列表只含已发布商品。
  final String status;

  /// 单价，单位分。
  final int unitPrice;
  final int? categoryId;
  final int? sessionId;

  /// 数量行下单所需（POST /orders 的 lines[].poolCode）；未建池为 null，按未开售处理。
  final String? poolCode;

  /// 库存展示口径；无池时缺省（即未开售），下单时后端原子预留重校验。
  final int? available;

  /// 单价（元），供展示格式化。
  double get unitPriceYuan => unitPrice / 100;

  factory ProductListItem.fromJson(Map<String, dynamic> json) =>
      _$ProductListItemFromJson(json);

  Map<String, dynamic> toJson() => _$ProductListItemToJson(this);
}
