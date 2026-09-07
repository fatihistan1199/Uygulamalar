import 'dart:convert';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() { WidgetsFlutterBinding.ensureInitialized(); runApp(const KaderApp()); }

class City {
  City(this.id,this.name,this.food,this.trade,this.order);
  final String id; final String name; int food; int trade; int order;
  Map<String,dynamic> toJson()=>{'id':id,'name':name,'food':food,'trade':trade,'order':order};
  factory City.fromJson(Map<String,dynamic> j)=>City(j['id'],j['name'],j['food'],j['trade'],j['order']);
}

class KaderApp extends StatelessWidget {
  const KaderApp({super.key});
  @override Widget build(BuildContext context)=>MaterialApp(
    debugShowCheckedModeBanner:false,
    title:'Anadolu: Kader Yolları',
    theme:ThemeData(useMaterial3:true,colorScheme:ColorScheme.fromSeed(seedColor:const Color(0xff0e7490)),scaffoldBackgroundColor:const Color(0xfff7f0dd)),
    home:const GamePage(),
  );
}

class GamePage extends StatefulWidget { const GamePage({super.key}); @override State<GamePage> createState()=>_GamePageState(); }

class _GamePageState extends State<GamePage> {
  final rng=Random();
  final nameCtrl=TextEditingController(text:'Hasan');
  List<City> cities=[];
  String? currentCity;
  int day=1,money=50,tension=20;
  String background='Tüccar ailesi';
  String? event;
  String? outcome;
  int? delayedDay;
  bool started=false;
  final chronicle=<String>[];

  @override void initState(){ super.initState(); _hasSave(); }
  Future<void> _hasSave() async { final p=await SharedPreferences.getInstance(); if(p.containsKey('kader_save')) setState(()=>outcome='Kayıt bulundu. Menüden devam edebilirsin.'); }

  void _newGame(){
    cities=[City('konya','Konya',66,78,61),City('kayseri','Kayseri',52,88,54),City('sivas','Sivas',69,70,62),City('ankara','Ankara',71,64,59),City('antalya','Antalya',76,91,65)];
    currentCity='konya'; day=1; money=50; tension=20; event=null; delayedDay=null; chronicle..clear()..add('1. gün — ${nameCtrl.text} Konya’da yolculuğuna başladı.');
    started=true; outcome=null; setState((){}); _save();
  }

  City get city=>cities.firstWhere((c)=>c.id==currentCity);

  Future<void> _save() async {
    if(!started)return; final p=await SharedPreferences.getInstance();
    await p.setString('kader_save',jsonEncode({'day':day,'money':money,'tension':tension,'currentCity':currentCity,'background':background,'name':nameCtrl.text,'delayedDay':delayedDay,'cities':cities.map((e)=>e.toJson()).toList(),'chronicle':chronicle}));
  }

  Future<void> _load() async {
    final p=await SharedPreferences.getInstance(); final raw=p.getString('kader_save'); if(raw==null)return;
    final j=jsonDecode(raw); day=j['day']; money=j['money']; tension=j['tension']; currentCity=j['currentCity']; background=j['background']; nameCtrl.text=j['name']; delayedDay=j['delayedDay'];
    cities=(j['cities'] as List).map((e)=>City.fromJson(Map<String,dynamic>.from(e))).toList(); chronicle..clear()..addAll((j['chronicle'] as List).cast<String>());
    started=true; event=null; outcome='Aynı dünyadan devam ediyorsun.'; setState((){});
  }

  void _advance(int n){
    for(var i=0;i<n;i++){ day++; for(final c in cities){ if(rng.nextDouble()<.12)c.food=(c.food-1).clamp(0,100).toInt(); c.trade=(c.trade+rng.nextInt(3)-1).clamp(0,100).toInt(); }
      if(delayedDay!=null && day>=delayedDay!){ final k=cities.firstWhere((c)=>c.id=='kayseri'); k.order=(k.order-12).clamp(0,100).toInt(); tension=(tension+12).clamp(0,100).toInt(); outcome='Eski tahıl kararı geri döndü: Kayseri’de soruşturma büyüdü.'; chronicle.add('$day. gün — Tahıl soruşturması yeniden açıldı.'); delayedDay=null; }
    }
    outcome??='$n gün geçti. Dünya sen beklerken de değişti.'; setState((){}); _save();
  }

  void _travel(String id){ final from=city.name; currentCity=id; final d=3+rng.nextInt(5); _advance(d); chronicle.add('$day. gün — $from’dan ${city.name} şehrine ulaştı.'); outcome='$d günlük yolculuğun ardından ${city.name} şehrine vardın.'; setState((){}); _save(); }

  void _seek(){ if(city.id=='kayseri' && city.food<60){event='grain';}else{event='rumor';} setState((){}); }

  void _choose(String c){
    if(event=='grain'){
      if(c=='talk'){ outcome='Mahmud fiyat artışını yol güvenliğine bağlıyor. Doğru söylüyor olabilir.'; tension=(tension-3).clamp(0,100).toInt(); }
      if(c=='judge'){ outcome='Kadıya haber verdin. Şimdilik hiçbir şey değişmedi.'; delayedDay=day+30+rng.nextInt(31); tension=(tension+7).clamp(0,100).toInt(); }
      if(c=='buy'){ if(money>=15){money-=15; city.food=(city.food-3).clamp(0,100).toInt(); delayedDay=day+45+rng.nextInt(46); outcome='Tahıl aldın. Kârlı olabilir; fakat daha sonra hatırlanabilir.';}else{outcome='Yeterli akçen yok.';} }
      if(c=='ignore'){ city.food=(city.food-5).clamp(0,100).toInt(); outcome='Karışmadın. Kriz yine de ilerliyor.'; }
      chronicle.add('$day. gün — Kayseri tahıl meselesinde bir taraf seçti.');
    } else { outcome=c=='listen'?'Söylenti: Yeni vergiler hazırlanıyor olabilir. Kaynak güvenilir değil.':'Söylentiyi önemsemedin.'; }
    event=null; setState((){}); _save();
  }

  @override Widget build(BuildContext context){
    if(!started)return Scaffold(body:SafeArea(child:Center(child:ConstrainedBox(constraints:const BoxConstraints(maxWidth:520),child:Padding(padding:const EdgeInsets.all(24),child:Column(mainAxisAlignment:MainAxisAlignment.center,crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      const Icon(Icons.route,size:72),Text('ANADOLU',textAlign:TextAlign.center,style:Theme.of(context).textTheme.displaySmall?.copyWith(fontWeight:FontWeight.w900)),Text('Kader Yolları',textAlign:TextAlign.center,style:Theme.of(context).textTheme.headlineSmall),const SizedBox(height:24),
      TextField(controller:nameCtrl,decoration:const InputDecoration(labelText:'Ad',border:OutlineInputBorder())),const SizedBox(height:12),
      DropdownButtonFormField<String>(value:background,decoration:const InputDecoration(labelText:'Geçmiş',border:OutlineInputBorder()),items:['Köylü ailesi','Tüccar ailesi','Medrese öğrencisi','Asker ailesi'].map((x)=>DropdownMenuItem(value:x,child:Text(x))).toList(),onChanged:(v)=>setState(()=>background=v!)),
      const SizedBox(height:16),FilledButton(onPressed:_newGame,child:const Text('Yeni Oyun')),OutlinedButton(onPressed:_load,child:const Text('Devam Et')),if(outcome!=null)Padding(padding:const EdgeInsets.only(top:10),child:Text(outcome!,textAlign:TextAlign.center))
    ]))))));
    return Scaffold(appBar:AppBar(title:Text('${city.name} • $day. gün'),actions:[IconButton(onPressed:_save,icon:const Icon(Icons.save_outlined))]),drawer:Drawer(child:SafeArea(child:ListView(padding:const EdgeInsets.all(12),children:[Text('Kader Defteri',style:Theme.of(context).textTheme.headlineSmall),const Divider(),...chronicle.reversed.take(12).map((e)=>ListTile(dense:true,title:Text(e)))]))),body:SafeArea(child:ListView(padding:const EdgeInsets.all(12),children:[
      Card(child:Padding(padding:const EdgeInsets.all(14),child:Row(children:[const CircleAvatar(child:Icon(Icons.person)),const SizedBox(width:10),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(nameCtrl.text,style:Theme.of(context).textTheme.titleLarge),Text(background)])),Column(crossAxisAlignment:CrossAxisAlignment.end,children:[Text('$money akçe'),Text('Gerilim $tension')])]))),
      if(event==null)...[
        if(outcome!=null)Card(child:Padding(padding:const EdgeInsets.all(14),child:Text(outcome!))),
        Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(city.name,style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),const SizedBox(height:10),Wrap(spacing:8,children:[Chip(label:Text('Gıda ${city.food}')),Chip(label:Text('Ticaret ${city.trade}')),Chip(label:Text('Huzur ${city.order}'))])]))),
        FilledButton.icon(onPressed:_seek,icon:const Icon(Icons.forum_outlined),label:const Text('Şehirde dolaş / bilgi ara')),OutlinedButton.icon(onPressed:()=>_travelDialog(context),icon:const Icon(Icons.map_outlined),label:const Text('Seyahat et')),OutlinedButton.icon(onPressed:()=>_advance(7),icon:const Icon(Icons.calendar_month),label:const Text('Bir hafta geçir'))
      ] else _eventCard(context)
    ])));
  }

  Widget _eventCard(BuildContext context){
    final grain=event=='grain';
    final opts=grain?[['talk','Mahmud’u dinle','Bilgi kazanırsın; onun anlatısına da maruz kalırsın.'],['judge','Kadıyı haberdar et','Meşru yol; gecikmiş sonucu olabilir.'],['buy','15 akçelik tahıl al','Ekonomik fırsat; bedeli belirsiz.'],['ignore','Karışma','Tarafsızlık da sonuç üretir.']]:[['listen','Dinlemeye devam et','Şüpheli bilgi edinebilirsin.'],['ignore','Önemseme','Yanlış bilgiden korunursun; gerçek uyarıyı kaçırabilirsin.']];
    return Card(child:Padding(padding:const EdgeInsets.all(18),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Container(height:150,decoration:BoxDecoration(borderRadius:BorderRadius.circular(16),gradient:const LinearGradient(colors:[Color(0xff0e7490),Color(0xff4c1d95)])),child:const Icon(Icons.auto_stories,size:64,color:Colors.white)),const SizedBox(height:16),Text(grain?'Kayseri’de Tahıl Meselesi':'Handaki Fısıltılar',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),const SizedBox(height:8),Text(grain?'Tahıl fiyatları yükseliyor. Esnaf, Mahmud’un zahire depoladığını söylüyor; söylentinin kaynağı ise ticari rakipleri.':'Yan masadaki iki yolcu yaklaşan yeni vergilerden söz ediyor. Birinin sarhoş olduğu açık.'),const SizedBox(height:14),...opts.map((o)=>Padding(padding:const EdgeInsets.only(bottom:8),child:OutlinedButton(onPressed:()=>_choose(o[0]),child:Align(alignment:Alignment.centerLeft,child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(o[1],style:const TextStyle(fontWeight:FontWeight.bold)),Text(o[2],style:Theme.of(context).textTheme.bodySmall)])))))])));
  }

  Future<void> _travelDialog(BuildContext context) async { final id=await showDialog<String>(context:context,builder:(_)=>SimpleDialog(title:const Text('Nereye gideceksin?'),children:cities.where((c)=>c.id!=currentCity).map((c)=>SimpleDialogOption(onPressed:()=>Navigator.pop(context,c.id),child:Padding(padding:const EdgeInsets.all(8),child:Text(c.name)))).toList())); if(id!=null)_travel(id); }
}
