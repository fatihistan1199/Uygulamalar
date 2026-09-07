package tr.com.gundemradari.scan

fun sourceFamily(sourceId:String):String{
    val id=sourceId.lowercase()
    return when{
        id.startsWith("trt_")->"trt"
        id.startsWith("haberturk")->"haberturk"
        id.startsWith("sozcu")->"sozcu"
        id.startsWith("bbc_")->"bbc"
        id.startsWith("dw_")->"dw"
        id.startsWith("euronews")->"euronews"
        id.startsWith("diyanet_")->"diyanet"
        id.startsWith("religion_people_google")->"religion_google"
        id.startsWith("religion_people_youtube")->"religion_youtube"
        else->id
    }
}
