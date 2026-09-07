package tr.com.gundemradari.scan

import java.util.Locale

object TurkishNewsPolisher {
    private val englishSimplifications=listOf(
        Regex("\\bfull[- ]scale war\\b",RegexOption.IGNORE_CASE) to "large-scale war",
        Regex("\\brunning short\\b",RegexOption.IGNORE_CASE) to "becoming scarce",
        Regex("\\bwhy it matters\\b",RegexOption.IGNORE_CASE) to "why this is important",
        Regex("\\breassert itself\\b",RegexOption.IGNORE_CASE) to "become influential again",
        Regex("\\bdrill cuts\\b",RegexOption.IGNORE_CASE) to "reductions in military exercises",
        Regex("\\bmilitary drills?\\b",RegexOption.IGNORE_CASE) to "military exercises",
        Regex("\\bamid\\b",RegexOption.IGNORE_CASE) to "during",
        Regex("\\bslams\\b",RegexOption.IGNORE_CASE) to "strongly criticizes",
        Regex("\\bbacks\\b",RegexOption.IGNORE_CASE) to "supports",
        Regex("\\beyes\\b",RegexOption.IGNORE_CASE) to "considers",
        Regex("\\bmulls\\b",RegexOption.IGNORE_CASE) to "considers",
        Regex("\\bset to\\b",RegexOption.IGNORE_CASE) to "expected to",
        Regex("\\bcrackdown\\b",RegexOption.IGNORE_CASE) to "strict measures"
    )

    fun simplifyEnglish(text:String):String {
        var s=text
            .replace('’','\'')
            .replace('“','"')
            .replace('”','"')
            .replace(Regex("\\s+")," ")
            .trim()
        englishSimplifications.forEach{(pattern,replacement)->s=pattern.replace(s,replacement)}
        return s
    }

    fun polish(original:String,translated:String,isTitle:Boolean):String {
        var s=translated
            .replace("tam ölçekli savaş","geniş çaplı savaş",ignoreCase=true)
            .replace("tam ölçekli","geniş çaplı",ignoreCase=true)
            .replace("askeri sondajlar","askeri tatbikatlar",ignoreCase=true)
            .replace("askeri sondaj","askeri tatbikat",ignoreCase=true)
            .replace("askerî sondajlar","askerî tatbikatlar",ignoreCase=true)
            .replace("askerî sondaj","askerî tatbikat",ignoreCase=true)
            .replace("neden bu önemlidir","bu neden önemli",ignoreCase=true)
            .replace("neden bu önemli","bu neden önemli",ignoreCase=true)
            .replace("kıtlaşıyor","azalıyor",ignoreCase=true)
            .replace("kıt hale geliyor","azalıyor",ignoreCase=true)
            .replace(Regex("\\s+([,.;:!?])"),"$1")
            .replace(Regex("[ \\t]{2,}")," ")
            .replace(Regex("\\n{3,}"),"\n\n")
            .trim()

        if(original.contains("why it matters",ignoreCase=true) && !s.endsWith("?")){
            s=s.trimEnd('.','!')+"?"
        }
        if(isTitle && s.isNotEmpty()){
            s=s.replaceFirstChar{c->if(c.isLowerCase())c.titlecase(Locale("tr","TR")) else c.toString()}
        }
        return s
    }
}
