import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import 'src/api/awn_client.dart';
import 'src/pages/event_list_page.dart';

/// API 域名：`flutter run --dart-define=AWN_API_HOST=https://...` 覆盖；
/// 默认本机后端（docs/api/contracts.md，统一前缀 /api/v1）。
const String apiHost = String.fromEnvironment(
  'AWN_API_HOST',
  defaultValue: 'http://localhost:8080',
);

void main() {
  runApp(AwnClientApp(
    client: AwnApiClient(
      dio: Dio(BaseOptions(baseUrl: '$apiHost/api/v1')),
    ),
  ));
}

/// AWN 客户端：只读活动 / 票档链路。
///
/// 接口契约见 docs/api/contracts.md；品牌色与 design-system/awn/MASTER.md 一致
/// （ink #1C1917 / gold #D59819 / goldDeep #A16207 / bg #FAFAF9）。
/// 客户端经构造注入，测试用 StubHttpClientAdapter 替换传输层。
class AwnClientApp extends StatelessWidget {
  const AwnClientApp({super.key, required this.client});

  final AwnApiClient client;

  @override
  Widget build(BuildContext context) {
    const gold = Color(0xFFD59819);
    const goldDeep = Color(0xFFA16207);
    const ink = Color(0xFF1C1917);
    const bg = Color(0xFFFAFAF9);

    return MaterialApp(
      title: '麦芒网',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: gold, primary: goldDeep),
        scaffoldBackgroundColor: bg,
        appBarTheme: const AppBarTheme(
          backgroundColor: bg,
          foregroundColor: ink,
          elevation: 0,
          centerTitle: true,
        ),
        useMaterial3: true,
      ),
      home: EventListPage(client: client),
    );
  }
}
