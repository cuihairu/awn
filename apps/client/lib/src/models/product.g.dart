// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'product.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProductListItem _$ProductListItemFromJson(Map<String, dynamic> json) =>
    ProductListItem(
      productCode: json['productCode'] as String,
      name: json['name'] as String,
      status: json['status'] as String,
      unitPrice: (json['unitPrice'] as num).toInt(),
      categoryId: (json['categoryId'] as num?)?.toInt(),
      sessionId: (json['sessionId'] as num?)?.toInt(),
      poolCode: json['poolCode'] as String?,
      available: (json['available'] as num?)?.toInt(),
    );

Map<String, dynamic> _$ProductListItemToJson(ProductListItem instance) =>
    <String, dynamic>{
      'productCode': instance.productCode,
      'name': instance.name,
      'status': instance.status,
      'unitPrice': instance.unitPrice,
      'categoryId': instance.categoryId,
      'sessionId': instance.sessionId,
      'poolCode': instance.poolCode,
      'available': instance.available,
    };
