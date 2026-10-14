import 'package:flutter/material.dart';

import '../api/awn_client.dart';
import '../format.dart';
import '../models/event.dart';
import 'event_detail_page.dart';

/// 发现页：公开活动列表（已发布 / 已结束，按开始时间倒序）。
class EventListPage extends StatefulWidget {
  const EventListPage({super.key, required this.client});

  final AwnApiClient client;

  @override
  State<EventListPage> createState() => _EventListPageState();
}

class _EventListPageState extends State<EventListPage> {
  late Future<List<EventView>> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.client.listEvents();
  }

  void _reload() {
    setState(() {
      _future = widget.client.listEvents();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('麦芒网 · 发现')),
      body: FutureBuilder<List<EventView>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return _ErrorView(error: snapshot.error!, onRetry: _reload);
          }
          final events = snapshot.data ?? const <EventView>[];
          if (events.isEmpty) {
            return const Center(child: Text('暂无公开活动'));
          }
          return RefreshIndicator(
            onRefresh: () async => _reload(),
            child: ListView.separated(
              itemCount: events.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              padding: const EdgeInsets.all(16),
              itemBuilder: (context, index) {
                final event = events[index];
                return Card(
                  child: ListTile(
                    title: Text(event.name,
                        style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text(formatEventTime(event.startTime)),
                    trailing: _StatusChip(status: event.status),
                    onTap: () {
                      Navigator.of(context).push(MaterialPageRoute<void>(
                        builder: (_) =>
                            EventDetailPage(client: widget.client, event: event),
                      ));
                    },
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off, size: 40),
            const SizedBox(height: 12),
            Text('$error', textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final label = switch (status) {
      'PUBLISHED' => '在售',
      'COMPLETED' => '已结束',
      _ => status,
    };
    final color = status == 'PUBLISHED'
        ? const Color(0xFFA16207)
        : const Color(0xFF57534E);
    return Text(label,
        style: TextStyle(color: color, fontWeight: FontWeight.w600));
  }
}
