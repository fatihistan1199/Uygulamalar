package tr.com.gundemradari.scan
import tr.com.gundemradari.data.SourceEntity
import tr.com.gundemradari.data.EventEntity
import kotlin.math.min
private val important=mapOf("deprem" to 38,"earthquake" to 38,"savaş" to 35,"war" to 35,"çatışma" to 25,"seçim" to 25,"election" to 25,"merkez bankası" to 32,"faiz" to 25,"yangın" to 22,"fire" to 22,"uçak" to 25,"plane" to 25,"gemi" to 20,"ship" to 20,"afet" to 30,"mevzuat" to 25,"yasa" to 25,"regulation" to 25)
private val noisy=mapOf("magazin" to 45,"influencer" to 55,"ünlü" to 35,"viral" to 45,"sosyal medya" to 28,"görenler inanamadı" to 70,"ikiye bölündü" to 65)
fun scores(title:String,source:SourceEntity,sourceCount:Int=1):Pair<Double,Double>{val t=title.lowercase();val i=important.filterKeys{t.contains(it)}.values.sum()+(if(source.groupName=="official")18 else 0)+(if(t.contains("türkiye")||t.contains("turkey"))10 else 0);val n=noisy.filterKeys{t.contains(it)}.values.sum();return min(100.0,18+i+sourceCount*4-n*.3) to min(100.0,n.toDouble())}
fun similarity(a:String,b:String):Double{val x=a.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>2}.toSet();val y=b.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>2}.toSet();return if(x.isEmpty()||y.isEmpty())0.0 else x.intersect(y).size.toDouble()/x.union(y).size}
fun isSameEvent(item:FetchedItem,event:EventEntity)=similarity(item.title,event.title)>=.58
