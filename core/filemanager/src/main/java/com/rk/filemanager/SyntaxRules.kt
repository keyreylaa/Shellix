package com.rk.filemanager

/**
 * Lightweight, dependency-free tokenizer used to drive syntax highlighting in the
 * built-in editor. This is deliberately NOT a full grammar engine (see ponytail
 * note below) — it recognizes keywords, line/block comments, strings, and numbers,
 * which covers the common case for reading/editing config and source files.
 *
 * ponytail: heuristic per-token highlighter, not TextMate/TreeSitter grammar.
 * Upgrade path: swap in Sora `TextMateLanguage` with bundled .tmLanguage.json +
 * theme assets when full-fidelity highlighting (nested scopes, embedded langs) is
 * needed. Kept simple to avoid shipping/maintaining large grammar bundles.
 */

enum class TokenType { PLAIN, KEYWORD, COMMENT, STRING, NUMBER }

data class Token(val start: Int, val end: Int, val type: TokenType)

/** Per-language config: keyword set + comment/string syntax. */
data class LangSpec(
    val keywords: Set<String>,
    val lineComment: String?,      // e.g. "//" or "#"
    val blockCommentOpen: String? = null,
    val blockCommentClose: String? = null,
    val stringQuotes: Set<Char> = setOf('"', '\'')
) {
    companion object {
        private val C_LIKE = setOf(
            "if","else","for","while","do","switch","case","break","continue","return",
            "class","interface","enum","void","int","long","float","double","boolean",
            "char","new","try","catch","finally","throw","throws","public","private",
            "protected","static","final","abstract","import","package","extends","implements","this","super","null","true","false"
        )
        private val KOTLIN = C_LIKE + setOf("fun","val","var","when","object","companion","data","sealed","suspend","override","init","is","in","as","by","lateinit","const","internal")
        private val JS = setOf("function","var","let","const","if","else","for","while","do","switch","case","break","continue","return","class","new","try","catch","finally","throw","typeof","instanceof","this","null","true","false","undefined","async","await","yield","import","export","from","default","extends","super","of","in")
        private val PYTHON = setOf("def","class","if","elif","else","for","while","try","except","finally","with","as","import","from","return","yield","lambda","pass","break","continue","global","nonlocal","raise","assert","and","or","not","in","is","None","True","False","async","await","self")
        private val SHELL = setOf("if","then","elif","else","fi","for","in","do","done","while","until","case","esac","function","return","export","local","readonly","declare","echo","exit","break","continue","source")
        private val C = setOf("auto","break","case","char","const","continue","default","do","double","else","enum","extern","float","for","goto","if","int","long","register","return","short","signed","sizeof","static","struct","switch","typedef","union","unsigned","void","volatile","while","include","define","ifdef","ifndef","endif","pragma")
        private val CPP = C + setOf("class","namespace","template","typename","public","private","protected","virtual","override","new","delete","try","catch","throw","using","nullptr","true","false","bool","this","operator","friend","inline","explicit","constexpr","auto","std")
        private val RUST = setOf("as","break","const","continue","crate","dyn","else","enum","extern","false","fn","for","if","impl","in","let","loop","match","mod","move","mut","pub","ref","return","self","Self","static","struct","super","trait","true","type","unsafe","use","where","while","async","await","dyn")
        private val GO = setOf("break","case","chan","const","continue","default","defer","else","fallthrough","for","func","go","goto","if","import","interface","map","package","range","return","select","struct","switch","type","var","nil","true","false","iota")
        private val PHP = setOf("abstract","and","array","as","break","callable","case","catch","class","clone","const","continue","declare","default","do","echo","else","elseif","empty","enddeclare","endfor","endforeach","endif","endswitch","endwhile","extends","final","finally","fn","for","foreach","function","global","if","implements","include","instanceof","interface","isset","list","namespace","new","or","print","private","protected","public","require","return","static","switch","throw","trait","try","unset","use","var","while","yield","null","true","false")
        private val RUBY = setOf("begin","break","case","class","def","defined?","do","else","elsif","end","ensure","false","for","if","in","module","next","nil","not","or","redo","rescue","retry","return","self","super","then","true","unless","until","when","while","yield","and","attr_accessor","require","require_relative","puts")
        private val SWIFT = setOf("associatedtype","class","deinit","enum","extension","func","import","init","inout","let","operator","protocol","struct","subscript","typealias","var","break","case","continue","default","defer","do","else","fallthrough","for","guard","if","in","repeat","return","switch","where","while","as","catch","is","nil","rethrows","super","self","throw","throws","try","true","false","some","any","async","await","actor")
        private val SQL = setOf("select","from","where","insert","into","values","update","set","delete","create","table","drop","alter","add","primary","key","foreign","references","join","inner","left","right","outer","on","group","by","order","having","limit","offset","distinct","as","and","or","not","null","is","in","like","between","union","index","view","null","default","and")
        private val CSS = setOf("important","inherit","initial","unset","none","auto","flex","grid","block","inline","absolute","relative","fixed","static","hidden","visible")
        private val TOML_INI = setOf("true","false")
        private val LUA = setOf("and","break","do","else","elseif","end","false","for","function","goto","if","in","local","nil","not","or","repeat","return","then","true","until","while")
        private val DART = setOf("abstract","as","assert","async","await","break","case","catch","class","const","continue","default","do","dynamic","else","enum","export","extends","extension","external","factory","false","final","finally","for","Function","get","if","implements","import","in","interface","is","late","library","mixin","new","null","on","operator","part","required","rethrow","return","set","static","super","switch","sync","this","throw","true","try","typedef","var","void","while","with","yield")

        private val PLAIN_SPEC = LangSpec(emptySet(), null)

        /** Resolve a spec from a file name/extension. */
        fun forFile(name: String): LangSpec {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "kt", "kts" -> LangSpec(KOTLIN, "//", "/*", "*/")
                "java" -> LangSpec(C_LIKE, "//", "/*", "*/")
                "js","jsx","ts","tsx","mjs","cjs" -> LangSpec(JS, "//", "/*", "*/")
                "py","pyw" -> LangSpec(PYTHON, "#", stringQuotes = setOf('"','\''))
                "sh","bash","zsh","fish" -> LangSpec(SHELL, "#")
                "c","h" -> LangSpec(C, "//", "/*", "*/")
                "cpp","cc","cxx","hpp","hh","hxx" -> LangSpec(CPP, "//", "/*", "*/")
                "rs" -> LangSpec(RUST, "//", "/*", "*/")
                "go" -> LangSpec(GO, "//", "/*", "*/")
                "php" -> LangSpec(PHP, "//", "/*", "*/")
                "rb" -> LangSpec(RUBY, "#", "=begin", "=end")
                "swift" -> LangSpec(SWIFT, "//", "/*", "*/")
                "sql" -> LangSpec(SQL, "--", "/*", "*/")
                "css","scss","less" -> LangSpec(CSS, "//", "/*", "*/")
                "lua" -> LangSpec(LUA, "--", "--[[", "]]")
                "dart" -> LangSpec(DART, "//", "/*", "*/")
                "json","jsonc" -> LangSpec(emptySet(), null, stringQuotes = setOf('"'))
                "yml","yaml" -> LangSpec(setOf("true","false","null","yes","no"), "#", stringQuotes = setOf('"','\''))
                "toml","ini","cfg","conf","properties","env" -> LangSpec(TOML_INI, "#", stringQuotes = setOf('"','\''))
                "md","markdown" -> PLAIN_SPEC
                "xml","html","htm","vue","svelte" -> LangSpec(emptySet(), null, "<!--", "-->", setOf('"','\''))
                else -> PLAIN_SPEC
            }
        }
    }
}

/**
 * Tokenize a single line given the incoming block-comment state. Returns the
 * tokens plus whether the line ends still inside a block comment (so the next
 * line continues the comment). Keeps things line-oriented for cheap analysis.
 */
fun tokenizeLine(line: String, spec: LangSpec, startInBlockComment: Boolean): Pair<List<Token>, Boolean> {
    val tokens = mutableListOf<Token>()
    var i = 0
    var inBlock = startInBlockComment
    val n = line.length
    val bOpen = spec.blockCommentOpen
    val bClose = spec.blockCommentClose

    while (i < n) {
        // continue / detect block comment
        if (inBlock && bClose != null) {
            val end = line.indexOf(bClose, i)
            if (end >= 0) { tokens.add(Token(i, end + bClose.length, TokenType.COMMENT)); i = end + bClose.length; inBlock = false; continue }
            tokens.add(Token(i, n, TokenType.COMMENT)); i = n; continue
        }
        if (bOpen != null && line.startsWith(bOpen, i)) {
            val closeAt = if (bClose != null) line.indexOf(bClose, i + bOpen.length) else -1
            if (closeAt >= 0) { tokens.add(Token(i, closeAt + bClose!!.length, TokenType.COMMENT)); i = closeAt + bClose.length; continue }
            tokens.add(Token(i, n, TokenType.COMMENT)); i = n; inBlock = true; continue
        }
        // line comment
        if (spec.lineComment != null && line.startsWith(spec.lineComment, i)) {
            tokens.add(Token(i, n, TokenType.COMMENT)); i = n; continue
        }
        // string
        if (line[i] in spec.stringQuotes) {
            val quote = line[i]; var j = i + 1
            while (j < n) { if (line[j] == '\\') { j += 2; continue }; if (line[j] == quote) { j++; break }; j++ }
            tokens.add(Token(i, minOf(j, n), TokenType.STRING)); i = minOf(j, n); continue
        }
        // number
        if (line[i].isDigit()) {
            var j = i + 1
            while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
            tokens.add(Token(i, j, TokenType.NUMBER)); i = j; continue
        }
        // identifier / keyword
        if (line[i].isLetter() || line[i] == '_') {
            var j = i + 1
            while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
            val word = line.substring(i, j)
            if (word in spec.keywords) tokens.add(Token(i, j, TokenType.KEYWORD))
            i = j; continue
        }
        i++
    }
    return tokens to inBlock
}
