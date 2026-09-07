package tr.com.gundemradari.scan

fun sanitizeNewsText(text:String):String =
    text
        .replace(
            Regex("""\[(?:OBJ|OBJECT|object\s+Object)\]""",RegexOption.IGNORE_CASE),
            " "
        )
        .replace(
            Regex("""\[object\s+Object\]""",RegexOption.IGNORE_CASE),
            " "
        )
        .replace('\u00a0',' ')
        .replace(Regex("[ \\t]+")," ")
        .replace(Regex("\\s+([,.;:!?])"),"$1")
        .replace(Regex("\\n{3,}"),"\n\n")
        .trim()
