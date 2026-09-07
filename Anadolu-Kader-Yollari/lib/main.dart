import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'game_engine.dart';
import 'event_catalog.dart';

void main(){WidgetsFlutterBinding.ensureInitialized();runApp(const KaderApp());}

class KaderApp extends StatelessWidget{
  const KaderApp({super.key});
  @override Widget build(BuildContext context)=>MaterialApp(
    debugShowCheckedModeBanner:false,
    title:'Anadolu: Kader Yolları',
    theme:ThemeData(
      useMaterial3:true,
      colorScheme:ColorScheme.fromSeed(seedColor:const Color(0xff087f8c)),
      scaffoldBackgroundColor:const Color(0xfff7f0dd),
      cardTheme:const CardThemeData(margin:EdgeInsets.symmetric(vertical:6)),
    ),
    home:const GamePage(),
  );
}

class GamePage extends StatefulWidget{
  const GamePage({super.key});
  @override State<GamePage> createState()=>_GamePageState();
}

class _GamePageState extends State<GamePage>{
  static const saveKey='kader_save_v2';
  final nameCtrl=TextEditingController(text:'Hasan');
  final seedCtrl=TextEditingController();
  String background='Tüccar ailesi';
  String gender='Erkek';
  GameState? state;
  GameEngine? engine;
  EventCatalog? catalog;
  EventView? activeEvent;
  String? outcome;
  int tab=0;
  bool hasSave=false;
  bool contentLoading=true;
  String? contentError;

  @override void initState(){super.initState();_bootstrap();}
  @override void dispose(){nameCtrl.dispose();seedCtrl.dispose();super.dispose();}

  Future<void> _bootstrap()async{
    try{
      catalog=await EventCatalog.loadDefault();
      final p=await SharedPreferences.getInstance();
      hasSave=p.containsKey(saveKey);
    }catch(e){
      contentError='Olay kataloğu yüklenemedi: $e';
    }
    contentLoading=false;
    if(mounted)setState((){});
  }

  void _newGame(){
    final parsed=int.tryParse(seedCtrl.text.trim());
    final seed=parsed??(DateTime.now().millisecondsSinceEpoch&0x7fffffff);
    state=GameEngine.newGame(seed:seed,name:nameCtrl.text.trim().isEmpty?'Hasan':nameCtrl.text.trim(),background:background,gender:gender=='Kadın'?'female':'male');
    if(catalog==null)return;
    engine=GameEngine(state!,catalog!);activeEvent=null;outcome='Seed: $seed';tab=0;
    setState((){});_save();
  }

  Future<void> _save()async{
    if(state==null)return;
    final p=await SharedPreferences.getInstance();
    await p.setString(saveKey,jsonEncode(state!.toJson()));
    if(mounted)setState(()=>hasSave=true);
  }

  Future<void> _load()async{
    final p=await SharedPreferences.getInstance();final raw=p.getString(saveKey);
    if(raw==null)return;
    try{
      state=GameState.fromJson(Map<String,dynamic>.from(jsonDecode(raw) as Map));
      if(catalog==null)throw StateError('Olay kataloğu hazır değil');
      engine=GameEngine(state!,catalog!);nameCtrl.text=state!.playerName;background=state!.background;gender=state!.playerGender=='female'?'Kadın':'Erkek';
      activeEvent=null;outcome='Kayıt yüklendi. Dünya ${state!.day}. günden devam ediyor.';tab=0;
      if(mounted)setState((){});
    }catch(_){
      if(mounted)setState(()=>outcome='Kayıt bu sürümle uyumlu değil. Yeni oyun başlat.');
    }
  }

  void _advance(int days){
    engine!.advance(days);outcome='$days gün geçti. Dünya ve fraksiyonlar senden bağımsız hareket etti.';
    setState((){});_save();
  }

  void _seek(){activeEvent=engine!.pickEvent();outcome=null;setState((){});}

  void _choose(EventOption option){
    outcome=engine!.resolve(activeEvent!,option.id);
    activeEvent=null;setState((){});_save();
  }

  Future<void> _travel()async{
    final id=await showDialog<String>(context:context,builder:(context)=>SimpleDialog(
      title:const Text('Nereye gideceksin?'),
      children:state!.cities.values.where((c)=>c.id!=state!.currentCityId).map((c)=>SimpleDialogOption(
        onPressed:()=>Navigator.pop(context,c.id),
        child:Padding(padding:const EdgeInsets.symmetric(vertical:8),child:Row(children:[
          Expanded(child:Text(c.name,style:const TextStyle(fontWeight:FontWeight.w600))),
          Text('Güvenlik ${c.security}'),
        ])),
      )).toList(),
    ));
    if(id==null)return;
    final from=state!.city.name;final days=engine!.travel(id);
    outcome='$from → ${state!.city.name}: $days gün. Yol riski güvenlik ve eşkıyalığa göre hesaplandı.';
    setState((){});_save();
  }

  @override Widget build(BuildContext context){
    if(state==null)return _menu(context);
    return Scaffold(
      appBar:AppBar(
        title:Text('${state!.city.name} • ${state!.day}. gün'),
        actions:[IconButton(tooltip:'Kaydet',onPressed:_save,icon:const Icon(Icons.save_outlined))],
      ),
      drawer:_chronicleDrawer(context),
      bottomNavigationBar:NavigationBar(
        selectedIndex:tab,
        onDestinationSelected:(i)=>setState(()=>tab=i),
        destinations:const[
          NavigationDestination(icon:Icon(Icons.auto_stories_outlined),selectedIcon:Icon(Icons.auto_stories),label:'Oyun'),
          NavigationDestination(icon:Icon(Icons.groups_outlined),selectedIcon:Icon(Icons.groups),label:'Kişiler'),
          NavigationDestination(icon:Icon(Icons.account_tree_outlined),selectedIcon:Icon(Icons.account_tree),label:'Aile'),
          NavigationDestination(icon:Icon(Icons.menu_book_outlined),selectedIcon:Icon(Icons.menu_book),label:'Bilgi'),
          NavigationDestination(icon:Icon(Icons.account_balance_outlined),selectedIcon:Icon(Icons.account_balance),label:'Çevreler'),
        ],
      ),
      body:SafeArea(child:IndexedStack(index:tab,children:[_gameTab(context),_peopleTab(context),_familyTab(context),_knowledgeTab(context),_factionsTab(context)])),
    );
  }

  Widget _menu(BuildContext context)=>Scaffold(
    body:SafeArea(child:Center(child:ConstrainedBox(
      constraints:const BoxConstraints(maxWidth:520),
      child:ListView(shrinkWrap:true,padding:const EdgeInsets.all(24),children:[
        const Icon(Icons.route,size:72),
        Text('ANADOLU',textAlign:TextAlign.center,style:Theme.of(context).textTheme.displaySmall?.copyWith(fontWeight:FontWeight.w900)),
        Text('Kader Yolları',textAlign:TextAlign.center,style:Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height:24),
        TextField(controller:nameCtrl,decoration:const InputDecoration(labelText:'Ad',border:OutlineInputBorder())),
        const SizedBox(height:12),
        DropdownButtonFormField<String>(
          initialValue:background,
          decoration:const InputDecoration(labelText:'Geçmiş',border:OutlineInputBorder()),
          items:['Köylü ailesi','Tüccar ailesi','Medrese öğrencisi','Asker ailesi'].map((x)=>DropdownMenuItem(value:x,child:Text(x))).toList(),
          onChanged:(v)=>setState(()=>background=v!),
        ),
        const SizedBox(height:12),
        DropdownButtonFormField<String>(
          initialValue:gender,
          decoration:const InputDecoration(labelText:'Cinsiyet',border:OutlineInputBorder()),
          items:['Erkek','Kadın'].map((x)=>DropdownMenuItem(value:x,child:Text(x))).toList(),
          onChanged:(v)=>setState(()=>gender=v!),
        ),
        const SizedBox(height:8),
        ExpansionTile(
          tilePadding:EdgeInsets.zero,
          title:const Text('Gelişmiş'),
          subtitle:const Text('İstersen dünya seed değerini belirleyebilirsin.'),
          children:[Padding(
            padding:const EdgeInsets.only(bottom:12),
            child:TextField(controller:seedCtrl,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Dünya seed değeri',helperText:'Boş bırakırsan otomatik oluşturulur.',border:OutlineInputBorder())),
          )],
        ),
        const SizedBox(height:8),
        if(contentLoading)const Padding(padding:EdgeInsets.symmetric(vertical:8),child:LinearProgressIndicator()),
        if(contentError!=null)Padding(padding:const EdgeInsets.only(bottom:8),child:Text(contentError!,textAlign:TextAlign.center)),
        FilledButton(onPressed:catalog!=null?_newGame:null,child:const Text('Yeni Oyun')),
        OutlinedButton(onPressed:catalog!=null&&hasSave?_load:null,child:const Text('Devam Et')),
        if(catalog!=null)Padding(padding:const EdgeInsets.only(top:6),child:Text('${catalog!.events.length} olay modülü yüklendi.',textAlign:TextAlign.center,style:Theme.of(context).textTheme.bodySmall)),
        if(outcome!=null)Padding(padding:const EdgeInsets.only(top:10),child:Text(outcome!,textAlign:TextAlign.center)),
      ]),
    ))),
  );

  Widget _chronicleDrawer(BuildContext context)=>Drawer(child:SafeArea(child:ListView(
    padding:const EdgeInsets.all(12),
    children:[
      Text('Kader Defteri',style:Theme.of(context).textTheme.headlineSmall),
      Text('Seed ${state!.seed} • ${state!.background}',style:Theme.of(context).textTheme.bodySmall),
      const Divider(),
      ...state!.chronicle.reversed.take(30).map((e)=>ListTile(dense:true,leading:const Icon(Icons.history,size:18),title:Text(e))),
    ],
  )));

  Widget _gameTab(BuildContext context)=>ListView(padding:const EdgeInsets.all(12),children:[
    Card(child:Padding(padding:const EdgeInsets.all(14),child:Row(children:[
      const CircleAvatar(child:Icon(Icons.person)),
      const SizedBox(width:10),
      Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        Text(state!.playerName,style:Theme.of(context).textTheme.titleLarge),
        Text(state!.background),
      ])),
      Column(crossAxisAlignment:CrossAxisAlignment.end,children:[
        Text('${state!.money} akçe',style:const TextStyle(fontWeight:FontWeight.bold)),
        Text('Gerilim ${state!.tension}'),
      ]),
    ]))),
    _characterCard(context),
    if(!state!.alive)_successorPanel(context)else if(activeEvent!=null)_eventCard(context)else...[
      if(outcome!=null)Card(child:Padding(padding:const EdgeInsets.all(14),child:Text(outcome!))),
      _cityCard(context),
      FilledButton.icon(onPressed:_seek,icon:const Icon(Icons.forum_outlined),label:const Text('Şehirde dolaş / bilgi ara')),
      OutlinedButton.icon(onPressed:_marketSheet,icon:const Icon(Icons.storefront_outlined),label:const Text('Pazara git')),
      OutlinedButton.icon(onPressed:_conflictSheet,icon:const Icon(Icons.shield_outlined),label:const Text('Riskli yol görevine katıl')),
      OutlinedButton.icon(onPressed:_travel,icon:const Icon(Icons.map_outlined),label:const Text('Seyahat et')),
      OutlinedButton.icon(onPressed:()=>_advance(7),icon:const Icon(Icons.calendar_month),label:const Text('Bir hafta geçir')),
      if(state!.delayedEffects.isNotEmpty)Padding(
        padding:const EdgeInsets.only(top:8),
        child:Text('Arka planda ${state!.delayedEffects.length} çözülmemiş sonuç var.',style:Theme.of(context).textTheme.bodySmall),
      ),
    ],
  ]);

  Widget _cityCard(BuildContext context){
    final c=state!.city;
    return Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      Text(c.name,style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
      const SizedBox(height:10),
      Wrap(spacing:7,runSpacing:7,children:[
        Chip(label:Text('Gıda ${c.food}')),Chip(label:Text('Ticaret ${c.trade}')),Chip(label:Text('Huzur ${c.order}')),
        Chip(label:Text('Güvenlik ${c.security}')),Chip(label:Text('Refah ${c.prosperity}')),Chip(label:Text('Eşkıyalık ${c.banditry}')),
      ]),
      if(state!.inventory.values.any((q)=>q>0))...[
        const Divider(),const Text('Heybe',style:TextStyle(fontWeight:FontWeight.bold)),
        Wrap(spacing:6,children:state!.inventory.entries.where((e)=>e.value>0).map((e)=>Chip(label:Text('${GameEngine.goods[e.key]} ×${e.value}'))).toList()),
      ],
    ])));
  }



  Widget _characterCard(BuildContext context)=>Card(child:ExpansionTile(
    leading:CircleAvatar(child:Text('${state!.generation}')),
    title:Text('${state!.playerName} • ${state!.age} yaş'),
    subtitle:Text('${state!.playerGender=='female'?'Kadın':state!.playerGender=='male'?'Erkek':'Belirsiz'} • Sağlık ${state!.health}/100 • Nesil ${state!.generation}'),
    children:[Padding(padding:const EdgeInsets.fromLTRB(16,0,16,14),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      const Text('Nitelikler',style:TextStyle(fontWeight:FontWeight.bold)),
      Wrap(spacing:6,runSpacing:4,children:state!.attributes.entries.map((e)=>Chip(label:Text('${GameEngine.attributeNames[e.key]} ${e.value}'))).toList()),
      if(state!.injuries.isNotEmpty)...[
        const Divider(),const Text('Yaralanmalar',style:TextStyle(fontWeight:FontWeight.bold)),
        ...state!.injuries.reversed.map((i)=>ListTile(
          dense:true,contentPadding:EdgeInsets.zero,
          leading:Icon(i.permanent?Icons.warning_amber:Icons.healing),
          title:Text(i.name),
          subtitle:Text('Şiddet ${i.severity}/100 • ${i.acquiredDay}. gün${i.permanent?' • kalıcı iz':''}'),
        )),
      ],
      if(state!.lineage.isNotEmpty)...[
        const Divider(),Text('Önceki nesiller: ${state!.lineage.join(' → ')}'),
      ],
    ]))],
  ));

  Widget _successorPanel(BuildContext context)=>Card(child:Padding(
    padding:const EdgeInsets.all(18),
    child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      const Icon(Icons.account_tree_outlined,size:54),
      const SizedBox(height:8),
      Text('${state!.playerName} öldü',textAlign:TextAlign.center,style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
      Text('Sebep: ${state!.deathCause}',textAlign:TextAlign.center),
      const SizedBox(height:6),
      const Text('Dünya sıfırlanmadı. Şehirler, NPC hafızaları, fraksiyon güçleri ve ekonomik durum aynı kaldı.',textAlign:TextAlign.center),
      const SizedBox(height:14),
      ...engine!.successorOptions().map((name)=>Padding(
        padding:const EdgeInsets.only(bottom:7),
        child:FilledButton.tonal(onPressed:(){
          outcome=engine!.assumeSuccessor(name);setState((){});_save();
        },child:Text('$name ile devam et')),
      )),
    ]),
  ));

  Future<void> _conflictSheet()async{
    await showModalBottomSheet<void>(
      context:context,
      builder:(sheetContext)=>SafeArea(child:Padding(
        padding:const EdgeInsets.all(16),
        child:Column(mainAxisSize:MainAxisSize.min,crossAxisAlignment:CrossAxisAlignment.stretch,children:[
          Text('Yol Çatışması',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
          const SizedBox(height:4),
          const Text('Her taktik zarla çözülür. Kişisel yeteneklerin ve koşullar şansı arka planda değiştirir.'),
          const SizedBox(height:12),
          _conflictButton(sheetContext,'defend','Tedbirli savun'),
          _conflictButton(sheetContext,'assault','Hızlı saldır'),
          _conflictButton(sheetContext,'flee','Geri çekil'),
          _conflictButton(sheetContext,'parley','Konuşarak çöz'),
        ]),
      )),
    );
  }

  Widget _conflictButton(BuildContext sheetContext,String tactic,String title)=>Padding(
    padding:const EdgeInsets.only(bottom:7),
    child:OutlinedButton(
      onPressed:(){
        final result=engine!.resolveConflict(tactic);
        outcome=result.summary;
        Navigator.pop(sheetContext);
        setState((){});_save();
      },
      child:Align(alignment:Alignment.centerLeft,child:Padding(
        padding:const EdgeInsets.symmetric(vertical:5),
        child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
          Text(title,style:const TextStyle(fontWeight:FontWeight.bold)),
          Text('Başarı şansı: ${engine!.conflictRiskLabel(tactic)}',style:Theme.of(context).textTheme.bodySmall),
        ]),
      )),
    ),
  );

  Future<void> _marketSheet()async{
    await showModalBottomSheet<void>(
      context:context,
      isScrollControlled:true,
      builder:(sheetContext)=>StatefulBuilder(builder:(context,setSheetState)=>SafeArea(child:Padding(
        padding:const EdgeInsets.all(16),
        child:Column(mainAxisSize:MainAxisSize.min,crossAxisAlignment:CrossAxisAlignment.stretch,children:[
          Row(children:[Expanded(child:Text('${state!.city.name} Pazarı',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold))),Text('${state!.money} akçe',style:const TextStyle(fontWeight:FontWeight.bold))]),
          const SizedBox(height:6),
          Text('Fiyatlar şehir stokuna ve ticaret durumuna göre değişir.',style:Theme.of(context).textTheme.bodySmall),
          const SizedBox(height:10),
          ...GameEngine.goods.entries.map((g){
            final buy=engine!.marketPrice(g.key,buying:true),sell=engine!.marketPrice(g.key,buying:false);
            final have=state!.inventory[g.key]??0,stock=state!.city.stock[g.key]??0;
            return Card(child:Padding(padding:const EdgeInsets.all(10),child:Row(children:[
              Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
                Text(g.value,style:const TextStyle(fontWeight:FontWeight.bold)),
                Text('Pazar stoku $stock • Heybende $have',style:Theme.of(context).textTheme.bodySmall),
              ])),
              Column(children:[
                FilledButton.tonal(onPressed:(){
                  outcome=engine!.buyGood(g.key);setSheetState((){});setState((){});_save();
                },child:Text('Al $buy')),
                SizedBox(height:4),
                OutlinedButton(onPressed:have>0?(){
                  outcome=engine!.sellGood(g.key);setSheetState((){});setState((){});_save();
                }:null,child:Text('Sat $sell')),
              ]),
            ])));
          }),
          const SizedBox(height:4),
          FilledButton(onPressed:()=>Navigator.pop(sheetContext),child:const Text('Pazardan ayrıl')),
        ]),
      ))),
    );
  }

  Widget _eventCard(BuildContext context)=>Card(child:Padding(
    padding:const EdgeInsets.all(18),
    child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      Container(height:135,decoration:BoxDecoration(borderRadius:BorderRadius.circular(16),gradient:const LinearGradient(colors:[Color(0xff087f8c),Color(0xff5b2387)])),child:const Icon(Icons.auto_stories,size:60,color:Colors.white)),
      const SizedBox(height:16),
      Text(activeEvent!.title,style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
      const SizedBox(height:8),Text(activeEvent!.body),
      const SizedBox(height:14),
      ...activeEvent!.options.map((o)=>Padding(padding:const EdgeInsets.only(bottom:8),child:OutlinedButton(
        onPressed:()=>_choose(o),
        child:Align(alignment:Alignment.centerLeft,child:Padding(padding:const EdgeInsets.symmetric(vertical:5),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
          Text(o.title,style:const TextStyle(fontWeight:FontWeight.bold)),Text(o.hint,style:Theme.of(context).textTheme.bodySmall),
        ]))),
      ))),
    ]),
  ));

  Widget _peopleTab(BuildContext context){
    final people=state!.npcs.values.toList()..sort((a,b){
      final ac=a.cityId==state!.currentCityId?0:1,bc=b.cityId==state!.currentCityId?0:1;
      if(ac!=bc)return ac.compareTo(bc);return a.name.compareTo(b.name);
    });
    return ListView(padding:const EdgeInsets.all(12),children:[
      Text('Kişiler',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
      const Text('İlişki tek puan değildir. Güven, saygı, korku, sevgi ve şüphe ayrı tutulur.'),
      const SizedBox(height:8),
      ...people.map((n)=>Card(child:ExpansionTile(
        leading:CircleAvatar(child:Text(n.name.characters.first)),
        title:Text(n.name),
        subtitle:Text('${n.profession} • ${n.age} yaş • ${state!.cities[n.cityId]!.name} • ${state!.factions[n.factionId]!.name}'),
        children:[Padding(padding:const EdgeInsets.fromLTRB(16,0,16,14),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
          Text('Hedef: ${n.goal}',style:const TextStyle(fontStyle:FontStyle.italic)),
          if(n.spouseId!=null)Text('Eşi: ${state!.npcs[n.spouseId]?.name??'bilinmiyor'} • Çocuk: ${n.childIds.length}'),
          const SizedBox(height:8),
          Wrap(spacing:6,runSpacing:4,children:[
            Chip(label:Text('Güven ${n.relation.trust}')),Chip(label:Text('Saygı ${n.relation.respect}')),Chip(label:Text('Korku ${n.relation.fear}')),
            Chip(label:Text('Sevgi ${n.relation.affection}')),Chip(label:Text('Şüphe ${n.relation.suspicion}')),Chip(label:Text('Borç ${n.relation.debt}')),
          ]),
          if(n.memories.isNotEmpty)...[
            const Divider(),const Text('Seni neden böyle görüyor?',style:TextStyle(fontWeight:FontWeight.bold)),
            ...n.memories.reversed.take(5).map((m)=>ListTile(dense:true,contentPadding:EdgeInsets.zero,title:Text(m.text),subtitle:Text('${m.day}. gün • önem ${m.importance}'))),
          ],
        ]))],
      ))),
    ]);
  }

  Widget _knowledgeTab(BuildContext context)=>ListView(padding:const EdgeInsets.all(12),children:[
    Text('Bilgi ve Söylentiler',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
    const Text('Oyuncunun bildiği ile dünyanın gerçeği aynı şey değildir.'),
    const SizedBox(height:8),
    if(state!.knowledge.isEmpty)const Card(child:Padding(padding:EdgeInsets.all(18),child:Text('Henüz kayıtlı bilgi yok. Hanlarda dinle, insanlarla konuş veya bilgiyi doğrulat.'))),
    ...state!.knowledge.reversed.map((k)=>Card(child:ListTile(
      leading:Icon(k.refuted?Icons.block:k.confirmed?Icons.verified:Icons.hearing),
      title:Text(k.text),
      subtitle:Text('${k.label} • Kaynak: ${k.source} • ${k.day}. gün'),
    ))),
  ]);

  Widget _familyTab(BuildContext context){
    final members=state!.family.values.toList()..sort((a,b)=>a.id.compareTo(b.id));
    final current=state!.family[state!.playerFamilyId];
    return ListView(padding:const EdgeInsets.all(12),children:[
      Text('Aile ve Soy',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
      const Text('Akrabalık artık yalnız soy kaydı değildir: güven, yakınlık, şüphe ve yükümlülükler olay seçeneklerini değiştirebilir.'),
      const SizedBox(height:8),
      ...members.map((m){
        final parents=m.parentIds.map((id)=>state!.family[id]?.name??id).join(', ');
        final children=m.childIds.map((id)=>state!.family[id]?.name??id).join(', ');
        final spouse=m.spouseId==null?'':(state!.family[m.spouseId!]?.name??m.spouseId!);
        final bond=current?.relations[m.id];
        final sex=m.gender=='female'?'Kadın':m.gender=='male'?'Erkek':'Belirsiz';
        return Card(child:ListTile(
          leading:CircleAvatar(child:Icon(m.id==state!.playerFamilyId?Icons.person:Icons.account_tree_outlined)),
          title:Text('${m.name}${m.id==state!.playerFamilyId?' (oynanan kişi)':''}'),
          subtitle:Text('$sex • ${m.age} yaş • ${m.alive?'hayatta':'vefat etti'}${spouse.isEmpty?'':'\nEş: $spouse'}${parents.isEmpty?'':'\nEbeveyn: $parents'}${children.isEmpty?'':'\nÇocuk: $children'}${bond==null?'':'\nBağ: güven ${bond.trust} • yakınlık ${bond.affection} • şüphe ${bond.suspicion} • borç ${bond.debt}'}'),
        ));
      }),
    ]);
  }

  Widget _factionsTab(BuildContext context)=>ListView(padding:const EdgeInsets.all(12),children:[
    Text('Toplumsal Çevreler',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.bold)),
    const Text('Bir çevreyle yakınlaşmak başka bir çevrede maliyet yaratabilir. Güç değerleri oyuncudan bağımsız da değişir.'),
    const SizedBox(height:8),
    ...state!.factions.values.map((f)=>Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      Text(f.name,style:Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight:FontWeight.bold)),
      const SizedBox(height:8),Text('Sana bakış: ${f.reputation}/100'),LinearProgressIndicator(value:f.reputation/100),
      const SizedBox(height:8),Text('Dünya gücü: ${f.power}/100'),LinearProgressIndicator(value:f.power/100),
    ])))),
  ]);
}
