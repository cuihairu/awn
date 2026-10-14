import 'package:flutter/material.dart';

import '../api/awn_client.dart';
import '../format.dart';
import '../models/event.dart';
import '../models/session.dart';
import 'session_products_page.dart';

/// 活动页：活动信息 + 公开场次列表（选场次入口）。
class EventDetailPage extends StatelessWidget {
  const EventDetailPage({super.key, required this.client, required this.event});

  final AwnApiClient client;
  final EventView event;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(event.name)),
      body: FutureBuilder<List<SessionView>>(
        future: client.listEventSessions(event.id),
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
          final sessions = snapshot.data ?? const <SessionView>[];
          if (sessions.isEmpty) {
            return const Center(child: Text('暂无场次'));
          }
          return ListView.separated(
            itemCount: sessions.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            padding: const EdgeInsets.all(16),
            itemBuilder: (context, index) {
              final session = sessions[index];
              return Card(
                child: ListTile(
                  title: Text(session.name,
                      style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text(formatEventTime(session.startTime)),
                  onTap: () {
                    Navigator.of(context).push(MaterialPageRoute<void>(
                      builder: (_) => SessionProductsPage(
                          client: client, session: session),
                    ));
                  },
                ),
              );
            },
          );
        },
      ),
    );
  }
}
