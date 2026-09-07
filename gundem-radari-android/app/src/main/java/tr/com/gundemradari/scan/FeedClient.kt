package tr.com.gundemradari.scan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import tr.com.gundemradari.data.SourceEntity
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class FetchedItem(val source:SourceEntity,val url:String,val title:String,val summary:String,val publishedAt:Long?=null)
class FeedClient {
 suspend fun fetch(source:SourceEntity):List<FetchedItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
  val conn=(URL(source.endpoint).openConnection() as HttpURLConnection).apply { connectTimeout=15000; readTimeout=15000; setRequestProperty("User-Agent","GundemRadari/0.1 Android"); instanceFollowRedirects=true }
  try { if(conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}"); parse(conn.inputStream.bufferedReader().readText(),source) } finally { conn.disconnect() }
 }
 private fun parse(xml:String,source:SourceEntity):List<FetchedItem>{
  val parser=Xml.newPullParser(); parser.setInput(xml.reader()); val out=mutableListOf<FetchedItem>(); var tag=""; var title="";var link="";var summary="";var inEntry=false
  while(parser.eventType!=XmlPullParser.END_DOCUMENT){ when(parser.eventType){
   XmlPullParser.START_TAG->{tag=parser.name.lowercase();if(tag=="item"||tag=="entry"){inEntry=true;title="";link="";summary=""};if(inEntry&&tag=="link"){parser.getAttributeValue(null,"href")?.let{link=it}}}
   XmlPullParser.TEXT-> if(inEntry){when(tag){"title"->title+=parser.text;"link"->if(link.isBlank())link+=parser.text;"description","summary","content"->summary+=parser.text}}
   XmlPullParser.END_TAG->if(parser.name.lowercase()=="item"||parser.name.lowercase()=="entry"){if(title.isNotBlank()&&link.isNotBlank())out+=FetchedItem(source,link,title.trim(),stripHtml(summary).take(700));inEntry=false}
  };parser.next() }
  return out
 }
 private fun stripHtml(s:String)=s.replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ").trim()
}
fun exactHash(title:String,url:String):String=MessageDigest.getInstance("SHA-256").digest((title.lowercase()+"|"+url.substringBefore('?')).toByteArray()).joinToString(""){"%02x".format(it)}
