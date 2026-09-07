import 'package:flutter_test/flutter_test.dart';
import 'package:anadolu_kader_yollari/main.dart';

void main() {
  testWidgets('ana menü açılır', (tester) async {
    await tester.pumpWidget(const KaderApp());
    await tester.pump();
    expect(find.text('ANADOLU'), findsOneWidget);
    expect(find.text('Yeni Oyun'), findsOneWidget);
  });
}
