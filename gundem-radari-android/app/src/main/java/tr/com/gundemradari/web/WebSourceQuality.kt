package tr.com.gundemradari.web

import java.util.Locale

fun webSourceQuality(sourceName:String,url:String=""):Double{
    val source=sourceName.lowercase(Locale("tr","TR")).trim()
    val link=url.lowercase(Locale.ROOT)
    val text="$source $link"

    if(
        listOf(
            "resmî gazete","resmi gazete","cumhurbaşkanlığı","bakanlığı",
            "afad","tcmb","diyanet haber",".gov.tr"
        ).any{text.contains(it)}
    ){
        return 0.95
    }

    if(
        listOf(
            "trt haber","anadolu ajansı","aa.com.tr","bbc","dw türkçe",
            "deutsche welle","euronews","reuters","associated press",
            "ap news","afp"
        ).any{text.contains(it)}
    ){
        return 0.88
    }

    if(
        listOf(
            "ntv","cnn türk","habertürk","haberturk","sözcü","sozcu",
            "t24","medyascope","birgün","birgun","hürriyet","hurriyet",
            "milliyet","karar","gazete oksijen"
        ).any{text.contains(it)}
    ){
        return 0.72
    }

    if(
        listOf(
            "youtube","x.com","twitter","instagram","facebook","tiktok",
            "reddit"
        ).any{text.contains(it)}
    ){
        return 0.42
    }

    if(
        listOf(
            "şoke eden","görenler inanamadı","bakın ne yaptı",
            "çok konuşulacak","olay oldu"
        ).any{source.contains(it)}
    ){
        return 0.36
    }

    return 0.55
}
