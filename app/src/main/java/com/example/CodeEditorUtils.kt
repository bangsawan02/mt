package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
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
 * Utility for Syntax Highlighting in Code Editor
 */
object SyntaxHighlighter {

    // Define syntax color scheme
    private val KeywordColor = Color(0xFFCF6679) // Pinkish Red
    private val StringColor = Color(0xFF03DAC6)  // Cyan Teal
    private val CommentColor = Color(0xFF888888) // Muted Gray
    private val NumberColor = Color(0xFFFFB74D)  // Light Orange
    private val TagColor = Color(0xFF64B5F6)     // Light Blue
    private val AttrColor = Color(0xFFBA68C8)    // Purple
    private val FunctionColor = Color(0xFFFFD54F)// Yellow

    private val KotlinJavaKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "private", "protected", "public", "internal", "final", "open", "abstract",
        "sealed", "data", "override", "if", "else", "when", "for", "while", "do",
        "return", "break", "continue", "try", "catch", "finally", "throw", "null",
        "true", "false", "this", "super", "is", "as", "in", "out", "by", "get", "set",
        "new", "extends", "implements", "static", "void", "boolean", "int", "long",
        "float", "double", "char", "byte", "short", "const"
    )

    private val SmaliKeywords = setOf(
        ".class", ".super", ".source", ".field", ".method", ".end", ".registers",
        ".locals", ".param", ".line", ".annotation", ".enum", "invoke-virtual",
        "invoke-direct", "invoke-static", "invoke-super", "invoke-interface",
        "const", "const/4", "const/16", "const-string", "return-void", "return",
        "move-result", "move-result-object", "if-eq", "if-ne", "goto", "check-cast",
        "instance-of", "new-instance", "sget-object", "sput-object", "iget-object", "iput-object"
    )

    private val ShellKeywords = setOf(
        "echo", "exit", "if", "then", "else", "fi", "for", "in", "do", "done",
        "case", "esac", "while", "function", "return", "export", "local", "alias",
        "chmod", "chown", "mkdir", "rm", "cp", "mv", "ls", "grep", "cat", "su"
    )

    fun highlight(text: String, fileExtension: String, isDark: Boolean = true): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            if (text.isEmpty()) return@buildAnnotatedString

            val ext = fileExtension.lowercase()
            when {
                ext in listOf("xml", "html", "plist") -> highlightXml(text)
                ext in listOf("json") -> highlightJson(text)
                ext in listOf("kt", "kts", "java") -> highlightCode(text, KotlinJavaKeywords)
                ext in listOf("smali") -> highlightCode(text, SmaliKeywords)
                ext in listOf("sh", "bash") -> highlightCode(text, ShellKeywords)
                else -> highlightGeneric(text)
            }
        }
    }

    private fun AnnotatedString.Builder.highlightXml(text: String) {
        // XML Tags
        val tagPattern = Pattern.compile("</?[a-zA-Z0-9_:-]+", Pattern.MULTILINE)
        val tagMatcher = tagPattern.matcher(text)
        while (tagMatcher.find()) {
            addStyle(SpanStyle(color = TagColor, fontWeight = FontWeight.Bold), tagMatcher.start(), tagMatcher.end())
        }

        // XML Attribute Names
        val attrPattern = Pattern.compile("\\s+[a-zA-Z0-9_:-]+(?=\\=)", Pattern.MULTILINE)
        val attrMatcher = attrPattern.matcher(text)
        while (attrMatcher.find()) {
            addStyle(SpanStyle(color = AttrColor), attrMatcher.start(), attrMatcher.end())
        }

        // Strings in quotes
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
        val stringMatcher = stringPattern.matcher(text)
        while (stringMatcher.find()) {
            addStyle(SpanStyle(color = StringColor), stringMatcher.start(), stringMatcher.end())
        }

        // Comments <!-- -->
        val commentPattern = Pattern.compile("<!--[\\s\\S]*?-->")
        val commentMatcher = commentPattern.matcher(text)
        while (commentMatcher.find()) {
            addStyle(SpanStyle(color = CommentColor), commentMatcher.start(), commentMatcher.end())
        }
    }

    private fun AnnotatedString.Builder.highlightJson(text: String) {
        // Keys
        val keyPattern = Pattern.compile("\"[^\"]*\"(?=\\s*:)")
        val keyMatcher = keyPattern.matcher(text)
        while (keyMatcher.find()) {
            addStyle(SpanStyle(color = TagColor, fontWeight = FontWeight.Bold), keyMatcher.start(), keyMatcher.end())
        }

        // String values
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

    private fun AnnotatedString.Builder.highlightCode(text: String, keywords: Set<String>) {
        // Match word tokens
        val wordPattern = Pattern.compile("\\b[a-zA-Z0-9_.$-]+\\b")
        val wordMatcher = wordPattern.matcher(text)
        while (wordMatcher.find()) {
            val word = wordMatcher.group()
            if (keywords.contains(word)) {
                addStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold), wordMatcher.start(), wordMatcher.end())
            }
        }

        // Strings
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
        val stringMatcher = stringPattern.matcher(text)
        while (stringMatcher.find()) {
            addStyle(SpanStyle(color = StringColor), stringMatcher.start(), stringMatcher.end())
        }

        // Numbers
        val numPattern = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)[fFL]?\\b")
        val numMatcher = numPattern.matcher(text)
        while (numMatcher.find()) {
            addStyle(SpanStyle(color = NumberColor), numMatcher.start(), numMatcher.end())
        }

        // Line comments
        val commentPattern = Pattern.compile("//.*|#.*")
        val commentMatcher = commentPattern.matcher(text)
        while (commentMatcher.find()) {
            addStyle(SpanStyle(color = CommentColor), commentMatcher.start(), commentMatcher.end())
        }

        // Block comments
        val blockCommentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
        val blockCommentMatcher = blockCommentPattern.matcher(text)
        while (blockCommentMatcher.find()) {
            addStyle(SpanStyle(color = CommentColor), blockCommentMatcher.start(), blockCommentMatcher.end())
        }
    }

    private fun AnnotatedString.Builder.highlightGeneric(text: String) {
        // Strings
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
        val stringMatcher = stringPattern.matcher(text)
        while (stringMatcher.find()) {
            addStyle(SpanStyle(color = StringColor), stringMatcher.start(), stringMatcher.end())
        }

        // Comments
        val commentPattern = Pattern.compile("//.*|#.*")
        val commentMatcher = commentPattern.matcher(text)
        while (commentMatcher.find()) {
            addStyle(SpanStyle(color = CommentColor), commentMatcher.start(), commentMatcher.end())
        }
    }
}

/**
 * Visual Transformation wrapper for Jetpack Compose TextField
 */
class CodeSyntaxVisualTransformation(
    private val fileExtension: String,
    private val isDark: Boolean = true
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(text.text, fileExtension, isDark)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

/**
 * Helper to auto-format XML / JSON code
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
                ext in listOf("xml", "html") -> {
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
                    // Basic indentation formatter for generic code/smali
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
 * Simple Undo/Redo state stack manager for text editing
 */
class UndoRedoManager(maxHistory: Int = 30) {
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun pushState(currentText: String) {
        if (undoStack.isNotEmpty() && undoStack.last() == currentText) return
        undoStack.add(currentText)
        if (undoStack.size > 30) {
            undoStack.removeAt(0)
        }
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
    }
}
