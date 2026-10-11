import 'package:flutter/material.dart';

void main() {
  runApp(const AwnApp());
}

/// AWN 客户端骨架。
///
/// Phase 02 接入统一商品 API 与专场渲染；接口契约见 docs/api/contracts.md。
/// 品牌色与 design-system/awn/MASTER.md 保持一致。
class AwnApp extends StatelessWidget {
  const AwnApp({super.key});

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
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('麦芒网')),
      body: const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _TicketLogo(),
              SizedBox(height: 16),
              Text(
                '票务交易与情报平台',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
              SizedBox(height: 8),
              Text(
                '客户端骨架（Flutter）· Phase 02 接入活动与专场',
                style: TextStyle(color: Color(0xFF57534E)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TicketLogo extends StatelessWidget {
  const _TicketLogo();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 84,
      height: 54,
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFD59819), width: 4),
        borderRadius: BorderRadius.circular(14),
      ),
    );
  }
}
