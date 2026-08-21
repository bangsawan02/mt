package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.io.StringWriter
import java.util.regex.Pattern
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

/**
 * High-performance Syntax Highlighter with pre-compiled patterns and extensive language support.
 */
object SyntaxHighlighter {

    // Centralized Syntax Color Palette
    private val KeywordColor = Color(0xFFFF5370)   // Coral Red
    private val TypeColor = Color(0xFFFFCB6B)      // Golden Yellow
    private val StringColor = Color(0xFFC3E88D)    // Soft Green
    private val NumberColor = Color(0xFFF78C6C)    // Peach Orange
    private val CommentColor = Color(0xFF75859B)   // Slate Gray
    private val TagColor = Color(0xFF82AAFF)       // Soft Blue
    private val AttrColor = Color(0xFFC792EA)      // Lavender Purple
    private val DirectiveColor = Color(0xFF89DDFF) // Cyan
    private val RegisterColor = Color(0xFFFF9800)  // Vivid Orange
    private val AnnotationColor = Color(0xFFFFD54F)// Light Yellow
    private val SearchMatchColor = Color(0xFF512DA8)// Highlight Purple

    // Precompiled Regex Patterns for High Performance
    private val STRING_PATTERN = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
    private val LINE_COMMENT_SLASH = Pattern.compile("//.*")
    private val LINE_COMMENT_HASH = Pattern.compile("#.*")
    private val LINE_COMMENT_SEMICOLON = Pattern.compile(";.*")
    private val BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*[\\s\\S]*?\\*/")
    private val NUMBER_PATTERN = Pattern.compile("\\b(0x[0-9a-fA-F]+|0b[01]+|\\d+(\\.\\d+)?)[fFLuU]?\\b")
    private val ANNOTATION_PATTERN = Pattern.compile("@[a-zA-Z0-9_.]+")

    // XML/HTML patterns
    private val XML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z0-9_:-]+", Pattern.MULTILINE)
    private val XML_ATTR_PATTERN = Pattern.compile("\\s+([a-zA-Z0-9_:-]+)(?=\\=)", Pattern.MULTILINE)
    private val XML_COMMENT_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->")

    // Smali patterns
    private val SMALI_REGISTER_PATTERN = Pattern.compile("\\b[vp]\\d+\\b")
    private val SMALI_DIRECTIVE_PATTERN = Pattern.compile("^\\s*\\.[a-zA-Z0-9_-]+", Pattern.MULTILINE)
    private val SMALI_DESCRIPTOR_PATTERN = Pattern.compile("L[a-zA-Z0-9_/$]+;")

    // Keywords sets
    private val KOTLIN_JAVA_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "private", "protected", "public", "internal", "final", "open", "abstract",
        "sealed", "data", "override", "if", "else", "when", "for", "while", "do",
        "return", "break", "continue", "try", "catch", "finally", "throw", "null",
        "true", "false", "this", "super", "is", "as", "in", "out", "by", "get", "set",
        "new", "extends", "implements", "static", "void", "boolean", "int", "long",
        "float", "double", "char", "byte", "short", "const", "enum", "typealias",
        "inline", "noinline", "crossinline", "suspend", "tailrec", "operator", "infix",
        "reified", "companion", "constructor", "init", "actual", "expect", "value"
    )

    private val TYPES_SET = setOf(
        "String", "Int", "Long", "Float", "Double", "Boolean", "Char", "Byte", "Short",
        "Unit", "Any", "Nothing", "Array", "List", "Set", "Map", "MutableList", "MutableMap",
        "StateFlow", "MutableStateFlow", "Flow", "Context", "Activity", "Modifier", "Composable"
    )

    private val SMALI_KEYWORDS = setOf(
        ".class", ".super", ".source", ".field", ".method", ".end", ".registers",
        ".locals", ".param", ".line", ".annotation", ".enum", ".subannotation",
        ".implements", ".prologue", ".epilogue", ".catch", ".catchall",
        "invoke-virtual", "invoke-direct", "invoke-static", "invoke-super", "invoke-interface",
        "invoke-virtual/range", "invoke-direct/range", "invoke-static/range", "invoke-super/range",
        "const", "const/4", "const/16", "const-wide", "const-wide/16", "const-wide/32",
        "const-string", "const-string/jumbo", "const-class", "return-void", "return", "return-wide",
        "return-object", "move-result", "move-result-wide", "move-result-object", "move-exception",
        "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le", "if-eqz", "if-nez",
        "if-ltz", "if-gez", "if-gtz", "if-lez", "goto", "goto/16", "goto/32",
        "check-cast", "instance-of", "new-instance", "new-array", "filled-new-array",
        "sget", "sget-wide", "sget-object", "sget-boolean", "sget-byte", "sget-char", "sget-short",
        "sput", "sput-wide", "sput-object", "sput-boolean", "sput-byte", "sput-char", "sput-short",
        "iget", "iget-wide", "iget-object", "iget-boolean", "iget-byte", "iget-char", "iget-short",
        "iput", "iput-wide", "iput-object", "iput-boolean", "iput-byte", "iput-char", "iput-short",
        "aget", "aget-wide", "aget-object", "aget-boolean", "aget-byte", "aput", "aput-object",
        "array-length", "throw", "monitor-enter", "monitor-exit", "nop"
    )

    private val SHELL_KEYWORDS = setOf(
        "echo", "exit", "if", "then", "else", "elif", "fi", "for", "in", "do", "done",
        "case", "esac", "while", "until", "function", "return", "export", "local", "alias",
        "chmod", "chown", "mkdir", "rm", "cp", "mv", "ls", "grep", "cat", "su", "sudo",
        "sed", "awk", "find", "tar", "gzip", "curl", "wget", "source", "read", "test"
    )

    private val JS_TS_KEYWORDS = setOf(
        "function", "const", "let", "var", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "default", "import", "export", "from",
        "class", "extends", "constructor", "new", "this", "super", "async", "await",
        "try", "catch", "finally", "throw", "typeof", "instanceof", "interface", "type",
        "enum", "namespace", "declare", "as", "null", "undefined", "true", "false"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue",
        "import", "from", "as", "try", "except", "finally", "raise", "with", "pass", "yield",
        "async", "await", "lambda", "global", "nonlocal", "assert", "del", "True", "False", "None"
    )

    private val C_CPP_KEYWORDS = setOf(
        "int", "long", "short", "char", "float", "double", "void", "bool", "auto",
        "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "default",
        "return", "class", "struct", "union", "enum", "typedef", "sizeof", "const", "static",
        "extern", "volatile", "unsigned", "signed", "public", "private", "protected",
        "virtual", "override", "template", "typename", "namespace", "using", "new", "delete",
        "try", "catch", "throw", "include", "define", "ifdef", "ifndef", "endif"
    )

    // CSS patterns
    private val CSS_SELECTOR_PATTERN = Pattern.compile("(^[a-zA-Z0-9_#.-]+|\\.[a-zA-Z0-9_-]+|#[a-zA-Z0-9_-]+)(?=\\s*\\{)", Pattern.MULTILINE)
    private val CSS_PROPERTY_PATTERN = Pattern.compile("(?<=\\{|;|\\s)([a-zA-Z0-9_-]+)(?=\\s*:)", Pattern.MULTILINE)
    private val CSS_VALUE_UNIT_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?(px|rem|em|vh|vw|%|pt|ms|s|deg|fr)\\b")
    private val CSS_COLOR_HEX_PATTERN = Pattern.compile("#[0-9a-fA-F]{3,8}\\b")

    // SQL Keywords
    private val SQL_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE", "CREATE", "TABLE",
        "DROP", "ALTER", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY",
        "ORDER", "HAVING", "LIMIT", "AND", "OR", "NOT", "NULL", "PRIMARY", "KEY", "FOREIGN",
        "REFERENCES", "VALUES", "SET", "AS", "DATABASE", "INDEX", "select", "from", "where",
        "insert", "into", "update", "delete", "create", "table", "drop", "alter"
    )

    /**
     * Auto detects code syntax language based on extension (e.g. .js, .jd, .css, .dex, .java)
     * and falls back to content sniffing if extension is ambiguous or missing.
     */
    fun detectLanguage(extension: String, content: String = ""): String {
        val ext = extension.lowercase().trim()

        when (ext) {
            "js", "jd", "jsx", "ts", "tsx", "mjs", "cjs" -> return "js"
            "css", "scss", "less" -> return "css"
            "dex", "smali" -> return "smali"
            "java" -> return "java"
            "kt", "kts" -> return "kt"
            "xml", "html", "htm", "svg", "plist" -> return "xml"
            "json" -> return "json"
            "py" -> return "py"
            "sh", "bash", "zsh" -> return "sh"
            "sql" -> return "sql"
            "c", "cpp", "h", "hpp", "cc" -> return "c"
            "yml", "yaml", "properties", "ini", "conf", "env" -> return "config"
        }

        // Content sniffing if extension is missing, unknown or txt
        if (content.isNotEmpty()) {
            val sample = content.take(1000).trim()
            if (sample.contains(".class public") || sample.contains(".method public") || sample.contains(".super L") || sample.contains("invoke-")) {
                return "smali"
            }
            if (sample.startsWith("<?xml") || sample.contains("<!DOCTYPE html") || sample.contains("<html") || (sample.startsWith("<") && sample.contains(">") && sample.contains("</"))) {
                return "xml"
            }
            if ((sample.startsWith("{") && sample.contains("\":")) || (sample.startsWith("[") && sample.contains("{"))) {
                return "json"
            }
            if (sample.contains("import java.") || sample.contains("public class ") || sample.contains("public static void main")) {
                return "java"
            }
            if (sample.contains("package ") && (sample.contains("fun ") || sample.contains("val ") || sample.contains("var "))) {
                return "kt"
            }
            if (sample.contains("function ") || sample.contains("const ") || sample.contains("let ") || sample.contains("console.log")) {
                return "js"
            }
            if (sample.contains("body {") || sample.contains("@media") || (sample.contains("{") && sample.contains("color:") && sample.contains("}"))) {
                return "css"
            }
            if (sample.startsWith("#!/") || sample.contains("echo ") || sample.contains("export ")) {
                return "sh"
            }
            if (sample.contains("SELECT ", ignoreCase = true) && sample.contains("FROM ", ignoreCase = true)) {
                return "sql"
            }
        }

        return if (ext.isNotEmpty()) ext else "txt"
    }

    /**
     * Highlights text according to file extension.
     * For extremely large files (>150KB), limits full scan to prevent UI frame drops.
     */
    fun highlight(
        text: String,
        fileExtension: String,
        searchQuery: String = "",
        isCaseSensitive: Boolean = false
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val detectedLang = detectLanguage(fileExtension, text)

        return buildAnnotatedString {
            append(text)

            // Fast safety limit for gigantic files (e.g. >150k chars)
            val isHuge = text.length > 150_000

            if (!isHuge) {
                when (detectedLang) {
                    "xml" -> highlightXml(text)
                    "json" -> highlightJson(text)
                    "smali" -> highlightSmali(text)
                    "java" -> highlightCode(text, KOTLIN_JAVA_KEYWORDS, true)
                    "kt" -> highlightCode(text, KOTLIN_JAVA_KEYWORDS, true)
                    "js" -> highlightCode(text, JS_TS_KEYWORDS, false)
                    "css" -> highlightCss(text)
                    "sql" -> highlightSql(text)
                    "sh" -> highlightShell(text)
                    "py" -> highlightPython(text)
                    "c" -> highlightCode(text, C_CPP_KEYWORDS, false)
                    "config" -> highlightConfig(text)
                    else -> highlightGeneric(text)
                }
            } else {
                highlightGeneric(text)
            }

            // Highlight search query matches
            if (searchQuery.isNotEmpty() && text.length < 250_000) {
                highlightSearchMatches(text, searchQuery, isCaseSensitive)
            }
        }
    }

    private fun AnnotatedString.Builder.highlightSearchMatches(
        text: String,
        query: String,
        isCaseSensitive: Boolean
    ) {
        val src = if (isCaseSensitive) text else text.lowercase()
        val target = if (isCaseSensitive) query else query.lowercase()
        var idx = 0
        while (idx < src.length) {
            val pos = src.indexOf(target, idx)
            if (pos != -1) {
                addStyle(
                    SpanStyle(
                        background = Color(0xFF6A1B9A),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    pos,
                    pos + target.length
                )
                idx = pos + target.length.coerceAtLeast(1)
            } else break
        }
    }

    private fun AnnotatedString.Builder.highlightXml(text: String) {
        // Tags
        applyPattern(XML_TAG_PATTERN, text, SpanStyle(color = TagColor, fontWeight = FontWeight.Bold))
        // Attributes
        applyPattern(XML_ATTR_PATTERN, text, SpanStyle(color = AttrColor))
        // Strings
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        // Comments
        applyPattern(XML_COMMENT_PATTERN, text, SpanStyle(color = CommentColor, fontWeight = FontWeight.Normal))
    }

    private fun AnnotatedString.Builder.highlightJson(text: String) {
        // Keys & Strings
        val keyPattern = Pattern.compile("\"[^\"]*\"(?=\\s*:)")
        applyPattern(keyPattern, text, SpanStyle(color = TagColor, fontWeight = FontWeight.SemiBold))

        val strPattern = Pattern.compile(":\\s*(\"[^\"]*\")")
        val strMatcher = strPattern.matcher(text)
        while (strMatcher.find()) {
            addStyle(SpanStyle(color = StringColor), strMatcher.start(1), strMatcher.end(1))
        }

        // Numbers & Booleans
        val numPattern = Pattern.compile(":\\s*(-?\\d+(\\.\\d+)?|true|false|null)\\b")
        val numMatcher = numPattern.matcher(text)
        while (numMatcher.find()) {
            addStyle(SpanStyle(color = NumberColor, fontWeight = FontWeight.Bold), numMatcher.start(1), numMatcher.end(1))
        }
    }

    private fun AnnotatedString.Builder.highlightSmali(text: String) {
        // Directives (.class, .method, etc.)
        applyPattern(SMALI_DIRECTIVE_PATTERN, text, SpanStyle(color = DirectiveColor, fontWeight = FontWeight.Bold))
        // Registers (v0, p1, etc.)
        applyPattern(SMALI_REGISTER_PATTERN, text, SpanStyle(color = RegisterColor, fontWeight = FontWeight.SemiBold))
        // Descriptors Ljava/lang/String;
        applyPattern(SMALI_DESCRIPTOR_PATTERN, text, SpanStyle(color = TypeColor))
        // Strings
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        // Numbers
        applyPattern(NUMBER_PATTERN, text, SpanStyle(color = NumberColor))
        // Comments
        applyPattern(LINE_COMMENT_HASH, text, SpanStyle(color = CommentColor))

        // Keywords
        highlightKeywords(text, SMALI_KEYWORDS, KeywordColor)
    }

    private fun AnnotatedString.Builder.highlightCode(
        text: String,
        keywords: Set<String>,
        includeAnnotations: Boolean
    ) {
        // Strings
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        // Numbers
        applyPattern(NUMBER_PATTERN, text, SpanStyle(color = NumberColor))
        // Line Comments
        applyPattern(LINE_COMMENT_SLASH, text, SpanStyle(color = CommentColor))
        // Block Comments
        applyPattern(BLOCK_COMMENT_PATTERN, text, SpanStyle(color = CommentColor))

        if (includeAnnotations) {
            applyPattern(ANNOTATION_PATTERN, text, SpanStyle(color = AnnotationColor, fontWeight = FontWeight.SemiBold))
            highlightKeywords(text, TYPES_SET, TypeColor)
        }

        // Keywords
        highlightKeywords(text, keywords, KeywordColor)
    }

    private fun AnnotatedString.Builder.highlightCss(text: String) {
        applyPattern(CSS_SELECTOR_PATTERN, text, SpanStyle(color = TagColor, fontWeight = FontWeight.Bold))
        applyPattern(CSS_PROPERTY_PATTERN, text, SpanStyle(color = AttrColor, fontWeight = FontWeight.Medium))
        applyPattern(CSS_VALUE_UNIT_PATTERN, text, SpanStyle(color = NumberColor, fontWeight = FontWeight.Bold))
        applyPattern(CSS_COLOR_HEX_PATTERN, text, SpanStyle(color = RegisterColor, fontWeight = FontWeight.Bold))
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        applyPattern(BLOCK_COMMENT_PATTERN, text, SpanStyle(color = CommentColor))
    }

    private fun AnnotatedString.Builder.highlightSql(text: String) {
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        applyPattern(NUMBER_PATTERN, text, SpanStyle(color = NumberColor))
        applyPattern(LINE_COMMENT_HASH, text, SpanStyle(color = CommentColor))
        applyPattern(LINE_COMMENT_SLASH, text, SpanStyle(color = CommentColor))
        highlightKeywords(text, SQL_KEYWORDS, KeywordColor)
    }

    private fun AnnotatedString.Builder.highlightShell(text: String) {
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        applyPattern(NUMBER_PATTERN, text, SpanStyle(color = NumberColor))
        applyPattern(LINE_COMMENT_HASH, text, SpanStyle(color = CommentColor))
        // Variables $VAR or ${VAR}
        val varPattern = Pattern.compile("\\$\\{?[a-zA-Z_0-9]+\\}?")
        applyPattern(varPattern, text, SpanStyle(color = RegisterColor, fontWeight = FontWeight.Bold))
        highlightKeywords(text, SHELL_KEYWORDS, KeywordColor)
    }

    private fun AnnotatedString.Builder.highlightPython(text: String) {
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        applyPattern(NUMBER_PATTERN, text, SpanStyle(color = NumberColor))
        applyPattern(LINE_COMMENT_HASH, text, SpanStyle(color = CommentColor))
        applyPattern(ANNOTATION_PATTERN, text, SpanStyle(color = AnnotationColor))
        highlightKeywords(text, PYTHON_KEYWORDS, KeywordColor)
    }

    private fun AnnotatedString.Builder.highlightConfig(text: String) {
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        applyPattern(LINE_COMMENT_HASH, text, SpanStyle(color = CommentColor))
        applyPattern(LINE_COMMENT_SEMICOLON, text, SpanStyle(color = CommentColor))
        // Keys before = or :
        val keyPattern = Pattern.compile("^[a-zA-Z0-9_.-]+(?=\\s*[:=])", Pattern.MULTILINE)
        applyPattern(keyPattern, text, SpanStyle(color = TagColor, fontWeight = FontWeight.Bold))
    }

    private fun AnnotatedString.Builder.highlightGeneric(text: String) {
        applyPattern(STRING_PATTERN, text, SpanStyle(color = StringColor))
        applyPattern(LINE_COMMENT_SLASH, text, SpanStyle(color = CommentColor))
        applyPattern(LINE_COMMENT_HASH, text, SpanStyle(color = CommentColor))
        applyPattern(NUMBER_PATTERN, text, SpanStyle(color = NumberColor))
    }

    private fun AnnotatedString.Builder.applyPattern(
        pattern: Pattern,
        text: String,
        style: SpanStyle
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            addStyle(style, matcher.start(), matcher.end())
        }
    }

    private fun AnnotatedString.Builder.highlightKeywords(
        text: String,
        keywords: Set<String>,
        color: Color
    ) {
        val wordPattern = Pattern.compile("\\b[a-zA-Z0-9_.-]+\\b")
        val matcher = wordPattern.matcher(text)
        val style = SpanStyle(color = color, fontWeight = FontWeight.Bold)
        while (matcher.find()) {
            if (keywords.contains(matcher.group())) {
                addStyle(style, matcher.start(), matcher.end())
            }
        }
    }
}

/**
 * Visual Transformation for Jetpack Compose OutlinedTextField / BasicTextField
 */
class CodeSyntaxVisualTransformation(
    private val fileExtension: String,
    private val searchQuery: String = "",
    private val isCaseSensitive: Boolean = false
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(
            text = text.text,
            fileExtension = fileExtension,
            searchQuery = searchQuery,
            isCaseSensitive = isCaseSensitive
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

/**
 * Intelligent Code Formatter supporting JSON, XML, HTML, and basic C-style code.
 */
object CodeFormatter {

    fun format(content: String, fileExtension: String): Pair<Boolean, String> {
        val ext = fileExtension.lowercase()
        return try {
            when {
                ext in listOf("json") -> {
                    val trimmed = content.trim()
                    val formatted = if (trimmed.startsWith("[")) {
                        JSONArray(trimmed).toString(2)
                    } else {
                        JSONObject(trimmed).toString(2)
                    }
                    Pair(true, formatted)
                }
                ext in listOf("xml", "html", "svg", "plist") -> {
                    val transformer = TransformerFactory.newInstance().newTransformer().apply {
                        setOutputProperty(OutputKeys.INDENT, "yes")
                        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                    }
                    val result = StringWriter()
                    transformer.transform(StreamSource(StringReader(content)), StreamResult(result))
                    Pair(true, result.toString())
                }
                else -> {
                    // Indentation Formatter for C, Java, Kotlin, Smali, JS, etc.
                    val lines = content.lines()
                    var indent = 0
                    val sb = StringBuilder()
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("}") || trimmed.startsWith("</") || trimmed.startsWith(".end")) {
                            indent = (indent - 1).coerceAtLeast(0)
                        }
                        val prefix = "  ".repeat(indent)
                        sb.append(prefix).append(trimmed).append("\n")
                        if ((trimmed.endsWith("{") || (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>") && !trimmed.startsWith("<?")) || trimmed.startsWith(".method") || trimmed.startsWith(".class")) && !trimmed.endsWith("}")) {
                            indent++
                        }
                    }
                    Pair(true, sb.toString().trimEnd())
                }
            }
        } catch (e: Exception) {
            Pair(false, "Format gagal: ${e.message}")
        }
    }
}

/**
 * Smart Text Operation Utilities: Line indent/unindent, comment toggles, duplicate line, line numbers calculation.
 */
object EditorOperations {

    fun indentOrUnindent(
        value: TextFieldValue,
        unindent: Boolean = false,
        spaces: String = "  "
    ): TextFieldValue {
        val text = value.text
        val sel = value.selection
        if (sel.collapsed) {
            if (!unindent) {
                // Insert tab at cursor
                val newText = text.substring(0, sel.start) + spaces + text.substring(sel.start)
                return TextFieldValue(newText, TextRange(sel.start + spaces.length))
            } else {
                // Remove spaces before cursor if present
                val start = (sel.start - spaces.length).coerceAtLeast(0)
                if (text.substring(start, sel.start) == spaces) {
                    val newText = text.substring(0, start) + text.substring(sel.start)
                    return TextFieldValue(newText, TextRange(start))
                }
                return value
            }
        }

        // Multi-line selection indentation
        val startLineIdx = text.substring(0, sel.min).count { it == '\n' }
        val endLineIdx = text.substring(0, sel.max).count { it == '\n' }
        val lines = text.lines().toMutableList()

        for (i in startLineIdx..endLineIdx.coerceAtMost(lines.lastIndex)) {
            val line = lines[i]
            if (unindent) {
                if (line.startsWith(spaces)) {
                    lines[i] = line.removePrefix(spaces)
                } else if (line.startsWith("\t")) {
                    lines[i] = line.removePrefix("\t")
                } else if (line.startsWith(" ")) {
                    lines[i] = line.removePrefix(" ")
                }
            } else {
                lines[i] = spaces + line
            }
        }

        val newText = lines.joinToString("\n")
        return TextFieldValue(newText, TextRange(sel.min, (sel.max + (if (unindent) -spaces.length else spaces.length)).coerceIn(0, newText.length)))
    }

    fun toggleLineComment(value: TextFieldValue, fileExt: String): TextFieldValue {
        val commentPrefix = when (fileExt.lowercase()) {
            "sh", "bash", "py", "yml", "yaml", "properties", "ini" -> "# "
            "smali" -> "# "
            "xml", "html" -> "<!-- " // simple
            else -> "// "
        }

        val text = value.text
        val sel = value.selection
        val startLineIdx = text.substring(0, sel.min).count { it == '\n' }
        val endLineIdx = text.substring(0, sel.max).count { it == '\n' }
        val lines = text.lines().toMutableList()

        for (i in startLineIdx..endLineIdx.coerceAtMost(lines.lastIndex)) {
            val line = lines[i]
            val trimmed = line.trimStart()
            val leadingSpaces = line.substring(0, line.length - trimmed.length)
            if (trimmed.startsWith(commentPrefix.trim())) {
                val cleaned = if (trimmed.startsWith(commentPrefix)) trimmed.removePrefix(commentPrefix) else trimmed.removePrefix(commentPrefix.trim())
                lines[i] = leadingSpaces + cleaned
            } else {
                lines[i] = leadingSpaces + commentPrefix + trimmed
            }
        }

        val newText = lines.joinToString("\n")
        return TextFieldValue(newText, TextRange(sel.min.coerceIn(0, newText.length)))
    }

    fun duplicateCurrentLine(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val sel = value.selection
        val cursor = sel.start
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val currentLine = text.substring(lineStart, lineEnd)

        val newText = text.substring(0, lineEnd) + "\n" + currentLine + text.substring(lineEnd)
        return TextFieldValue(newText, TextRange(lineEnd + 1 + currentLine.length))
    }

    fun calculateCursorMetrics(text: String, cursor: Int): Pair<Int, Int> {
        val safeCursor = cursor.coerceIn(0, text.length)
        val textBefore = text.substring(0, safeCursor)
        val line = textBefore.count { it == '\n' } + 1
        val lastNewline = textBefore.lastIndexOf('\n')
        val col = if (lastNewline == -1) safeCursor + 1 else (safeCursor - lastNewline)
        return Pair(line, col)
    }
}

/**
 * Intelligent Undo/Redo history manager with burst batching
 */
class UndoRedoManager(private val maxHistory: Int = 40) {
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var lastPushTime = 0L

    fun pushState(currentText: String) {
        val now = System.currentTimeMillis()
        if (undoStack.isNotEmpty() && undoStack.last() == currentText) return

        // If edits happen within 800ms and length diff is 1 char, update latest state to prevent flooding
        if (undoStack.isNotEmpty() && now - lastPushTime < 800L && Math.abs(undoStack.last().length - currentText.length) <= 1) {
            undoStack[undoStack.lastIndex] = currentText
        } else {
            undoStack.add(currentText)
            if (undoStack.size > maxHistory) {
                undoStack.removeAt(0)
            }
        }
        lastPushTime = now
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.size > 1

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(currentText: String): String? {
        if (!canUndo()) return null
        val top = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(top)
        return undoStack.lastOrNull()
    }

    fun redo(): String? {
        if (!canRedo()) return null
        val text = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(text)
        return text
    }

    fun clear(initialText: String) {
        undoStack.clear()
        redoStack.clear()
        undoStack.add(initialText)
        lastPushTime = System.currentTimeMillis()
    }
}
