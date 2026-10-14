import 'package:flutter/material.dart';

import '../api/awn_client.dart';
import '../availability.dart';
import '../format.dart';
import '../models/session.dart';

/// 票档页：场次商品只读列表（名称 + 单价 + 可售状态派生）。
///
/// 只读链路终点；下单 / 支付为后续增量（购买页全流程待接）。
class SessionProductsPage extends StatelessWidget {
  const SessionProductsPage({super.key, required this.client, required this.session});

  final AwnApiClient client;
  final SessionView session;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(session.name)),
      body: FutureBuilder(
        future: client.getSessionProducts(session.id),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text('${snapshot.error}'),
            ));
          }
          final products = snapshot.data ?? const [];
          if (products.isEmpty) {
            return const Center(child: Text('暂无票档'));
          }
          return ListView.separated(
            itemCount: products.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            padding: const EdgeInsets.all(16),
            itemBuilder: (context, index) {
              final product = products[index];
              final availability = deriveAvailability(product.available);
              return Card(
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16, vertical: 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(product.name,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w600)),
                            const SizedBox(height: 4),
                            _AvailabilityBadge(availability: availability),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text('¥${formatYuan(product.unitPriceYuan)}',
                          style: const TextStyle(
                              fontWeight: FontWeight.w700,
                              color: Color(0xFFA16207))),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

class _AvailabilityBadge extends StatelessWidget {
  const _AvailabilityBadge({required this.availability});

  final Availability availability;

  @override
  Widget build(BuildContext context) {
    final color = switch (availability) {
      Availability.selling => const Color(0xFF15803D),
      Availability.lowStock => const Color(0xFFB45309),
      Availability.soldOut => const Color(0xFFB91C1C),
      Availability.notOnSale => const Color(0xFF57534E),
    };
    return Text(
      availabilityLabel(availability),
      style: TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.w600),
    );
  }
}
