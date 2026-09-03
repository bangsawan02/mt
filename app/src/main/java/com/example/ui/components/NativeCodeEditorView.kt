package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.SyntaxHighlighter
import java.util.regex.Pattern

/**
 * High-performance Native Android EditText specifically engineered for Code & Text Editing:
 * - Direct native text buffer management avoiding Compose recomposition overhead on keystrokes.
 * - Hardware accelerated native scroll container with support for horizontal and vertical fling.
 * - Integrated native gutter with line numbers and dynamic active line highlight.
 * - Built-in native span-based syntax highlighter for smooth 60fps typing without lag.
 * - Native touch selection handles, cursor styling, and IME options.
 */
class NativeCodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val gutterView: LineNumberGutterView
    val editText: CodeEditText
    private val horizontalScrollView: HorizontalScrollView
    private val verticalScrollView: ScrollView

    var onTextChangedListener: ((String) -> Unit)? = null
    var onCursorPositionChangedListener: ((line: Int, column: Int, selectionLen: Int) -> Unit)? = null
    var onGutterClickListener: (() -> Unit)? = null

    private var isUpdatingInternally = false

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // 1. Line numbers gutter
        gutterView = LineNumberGutterView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            setOnClickListener { onGutterClickListener?.invoke() }
        }
        addView(gutterView)

        // 2. Editor input with dual-direction native scrolling
        editText = CodeEditText(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setPadding(16, 20, 32, 40)
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            isFocusable = true
            isFocusableInTouchMode = true
        }

        gutterView.bindToEditText(editText)

        // Two scroll configurations: with WordWrap (Vertical only) or NoWrap (Horizontal + Vertical)
        verticalScrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            isFillViewport = true
            isVerticalScrollBarEnabled = true
        }

        horizontalScrollView = HorizontalScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = false
            isHorizontalScrollBarEnabled = true
        }

        horizontalScrollView.addView(editText)
        verticalScrollView.addView(horizontalScrollView)
        addView(verticalScrollView)

        // Text change listener
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingInternally) {
                    onTextChangedListener?.invoke(s?.toString() ?: "")
                }
                gutterView.postInvalidate()
            }
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingInternally && s != null) {
                    editText.applySyntaxHighlight(s)
                }
            }
        })

        editText.onSelectionChangedCallback = { selStart, selEnd ->
            val text = editText.text?.toString() ?: ""
            val cursor = selStart.coerceIn(0, text.length)
            val textBefore = text.substring(0, cursor)
            val line = textBefore.count { it == '\n' } + 1
            val lastNewline = textBefore.lastIndexOf('\n')
            val col = if (lastNewline == -1) cursor + 1 else (cursor - lastNewline)
            val selLen = kotlin.math.abs(selEnd - selStart)
            onCursorPositionChangedListener?.invoke(line, col, selLen)
            gutterView.currentActiveLine = line
            gutterView.postInvalidate()
        }
    }

    fun setTextContent(content: String) {
        if (editText.text?.toString() == content) return
        isUpdatingInternally = true
        val prevStart = editText.selectionStart
        val prevEnd = editText.selectionEnd
        editText.setText(content)
        val safeStart = prevStart.coerceIn(0, content.length)
        val safeEnd = prevEnd.coerceIn(0, content.length)
        try {
            editText.setSelection(safeStart, safeEnd)
        } catch (_: Exception) {}
        editText.applySyntaxHighlight(editText.text)
        isUpdatingInternally = false
        gutterView.postInvalidate()
    }

    fun getTextContent(): String = editText.text?.toString() ?: ""

    fun setReadOnly(readOnly: Boolean) {
        editText.isFocusable = !readOnly
        editText.isFocusableInTouchMode = !readOnly
        editText.isCursorVisible = !readOnly
        editText.keyListener = if (readOnly) null else EditText(context).keyListener
    }

    fun setWordWrap(enabled: Boolean) {
        if (enabled) {
            // Unwrap from HorizontalScrollView
            if (horizontalScrollView.childCount > 0) {
                horizontalScrollView.removeView(editText)
            }
            if (verticalScrollView.childCount > 0) {
                verticalScrollView.removeView(horizontalScrollView)
            }
            editText.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            editText.setHorizontallyScrolling(false)
            verticalScrollView.addView(editText)
        } else {
            // Wrap in HorizontalScrollView
            if (verticalScrollView.childCount > 0) {
                verticalScrollView.removeView(editText)
            }
            if (horizontalScrollView.childCount == 0) {
                editText.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                editText.setHorizontallyScrolling(true)
                horizontalScrollView.addView(editText)
            }
            if (verticalScrollView.childCount == 0) {
                verticalScrollView.addView(horizontalScrollView)
            }
        }
        gutterView.postInvalidate()
    }

    fun setEditorFontSize(sp: Float) {
        editText.textSize = sp
        gutterView.setTextSize(sp)
        gutterView.postInvalidate()
    }

    fun setEditorTheme(backgroundColor: Int, textColor: Int, gutterBg: Int, gutterTextColor: Int, activeLineColor: Int) {
        setBackgroundColor(backgroundColor)
        editText.setBackgroundColor(backgroundColor)
        editText.setTextColor(textColor)
        gutterView.setColors(gutterBg, gutterTextColor, activeLineColor)
    }

    fun setLanguage(fileExtension: String) {
        editText.fileExtension = fileExtension
        editText.applySyntaxHighlight(editText.text)
    }

    fun setSearchHighlight(query: String, isCaseSensitive: Boolean) {
        editText.searchQuery = query
        editText.isCaseSensitive = isCaseSensitive
        editText.applySyntaxHighlight(editText.text)
    }

    fun insertTextAtCursor(textToInsert: String) {
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)
        val selStart = minOf(start, end)
        val selEnd = maxOf(start, end)
        editText.text?.replace(selStart, selEnd, textToInsert)
        val newCursor = selStart + textToInsert.length
        editText.setSelection(newCursor.coerceIn(0, editText.text?.length ?: 0))
    }

    fun goToLine(lineNumber: Int) {
        val content = editText.text?.toString() ?: return
        val lines = content.split("\n")
        val targetLine = lineNumber.coerceIn(1, lines.size.coerceAtLeast(1))
        var charOffset = 0
        for (i in 0 until (targetLine - 1)) {
            charOffset += lines[i].length + 1
        }
        val safeOffset = charOffset.coerceIn(0, content.length)
        editText.setSelection(safeOffset)
        editText.requestFocus()

        // Scroll into view
        val layout = editText.layout
        if (layout != null) {
            val y = layout.getLineTop((targetLine - 1).coerceIn(0, layout.lineCount - 1))
            verticalScrollView.smoothScrollTo(0, y)
        }
    }

    fun selectRange(start: Int, end: Int) {
        val len = editText.text?.length ?: 0
        val safeStart = start.coerceIn(0, len)
        val safeEnd = end.coerceIn(0, len)
        editText.setSelection(safeStart, safeEnd)
        editText.requestFocus()

        val layout = editText.layout
        if (layout != null) {
            val line = layout.getLineForOffset(safeStart)
            val y = layout.getLineTop(line)
            verticalScrollView.smoothScrollTo(0, (y - 100).coerceAtLeast(0))
        }
    }
}

/**
 * Native Line Number Gutter View that draws line numbers aligned with EditText line baselines.
 */
class LineNumberGutterView(context: Context) : View(context) {

    private var editText: CodeEditText? = null
    var currentActiveLine = 1

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
    }

    private val activeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 14f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
    }

    private val bgPaint = Paint().apply {
        color = AndroidColor.parseColor("#1E1E2E")
    }

    private val dividerPaint = Paint().apply {
        color = AndroidColor.parseColor("#313244")
        strokeWidth = 2f
    }

    fun bindToEditText(editor: CodeEditText) {
        this.editText = editor
    }

    fun setColors(bgColor: Int, textColor: Int, activeColor: Int) {
        bgPaint.color = bgColor
        textPaint.color = textColor
        activeLinePaint.color = activeColor
        dividerPaint.color = AndroidColor.argb(50, AndroidColor.red(textColor), AndroidColor.green(textColor), AndroidColor.blue(textColor))
        invalidate()
    }

    fun setTextSize(sp: Float) {
        val px = sp * resources.displayMetrics.scaledDensity
        textPaint.textSize = px
        activeLinePaint.textSize = px
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val editor = editText
        val lineCount = editor?.lineCount ?: 1
        val digits = maxOf(3, lineCount.toString().length)
        val textWidth = textPaint.measureText("9".repeat(digits))
        val padding = 24f * resources.displayMetrics.density
        val totalWidth = (textWidth + padding).toInt()
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(totalWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawLine(width - 1f, 0f, width - 1f, height.toFloat(), dividerPaint)

        val editor = editText ?: return
        val layout = editor.layout ?: return
        val totalLines = editor.lineCount
        val paddingTop = editor.paddingTop
        val text = editor.text?.toString() ?: ""

        val rightMargin = width - (10f * resources.displayMetrics.density)

        var logicalLine = 1
        for (i in 0 until totalLines) {
            val baseline = paddingTop + layout.getLineBaseline(i).toFloat()
            val startOffset = layout.getLineStart(i)

            // Only show line number if this line is the start of a logical line (preceded by newline or offset 0)
            val isStartOfLogicalLine = startOffset == 0 || (startOffset > 0 && text.getOrNull(startOffset - 1) == '\n')

            if (isStartOfLogicalLine) {
                val isCurrent = logicalLine == currentActiveLine
                val paint = if (isCurrent) activeLinePaint else textPaint
                canvas.drawText(logicalLine.toString(), rightMargin, baseline, paint)
                logicalLine++
            }
        }
    }
}

/**
 * Custom android.widget.EditText supporting native span-based syntax highlighting and cursor callbacks.
 */
class CodeEditText(context: Context) : EditText(context) {

    var onSelectionChangedCallback: ((start: Int, end: Int) -> Unit)? = null
    var fileExtension: String = "txt"
    var searchQuery: String = ""
    var isCaseSensitive: Boolean = false

    private var isHighlighting = false

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedCallback?.invoke(selStart, selEnd)
    }

    /**
     * Fast native syntax highlighting using Android's native ForegroundColorSpan.
     * Prevents Compose re-renders and executes efficiently on the editable text.
     */
    fun applySyntaxHighlight(editable: Editable?) {
        if (editable == null || isHighlighting) return
        val len = editable.length
        if (len == 0 || len > 200_000) return // Skip for extremely large files to prevent UI freeze

        isHighlighting = true
        try {
            // Remove old syntax spans
            val oldSpans = editable.getSpans(0, len, ForegroundColorSpan::class.java)
            for (span in oldSpans) {
                editable.removeSpan(span)
            }
            val oldStyleSpans = editable.getSpans(0, len, StyleSpan::class.java)
            for (span in oldStyleSpans) {
                editable.removeSpan(span)
            }

            val content = editable.toString()
            val detectedLang = SyntaxHighlighter.detectLanguage(fileExtension, content)

            // Highlight according to detected language
            when (detectedLang) {
                "kt", "java" -> {
                    applyPattern(editable, content, KEYWORDS_KT_JAVA, AndroidColor.parseColor("#FF5370"), true)
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, COMMENT_LINE_SLASH, AndroidColor.parseColor("#75859B"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                    applyPattern(editable, content, ANNOTATION_REGEX, AndroidColor.parseColor("#FFD54F"))
                }
                "js" -> {
                    applyPattern(editable, content, KEYWORDS_JS, AndroidColor.parseColor("#FF5370"), true)
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, COMMENT_LINE_SLASH, AndroidColor.parseColor("#75859B"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                }
                "xml" -> {
                    applyPattern(editable, content, XML_TAG_REGEX, AndroidColor.parseColor("#82AAFF"), true)
                    applyPattern(editable, content, XML_ATTR_REGEX, AndroidColor.parseColor("#C792EA"))
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, XML_COMMENT_REGEX, AndroidColor.parseColor("#75859B"))
                }
                "json" -> {
                    applyPattern(editable, content, JSON_KEY_REGEX, AndroidColor.parseColor("#82AAFF"), true)
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                    applyPattern(editable, content, JSON_BOOL_REGEX, AndroidColor.parseColor("#FF5370"), true)
                }
                "smali" -> {
                    applyPattern(editable, content, SMALI_DIRECTIVE_REGEX, AndroidColor.parseColor("#89DDFF"), true)
                    applyPattern(editable, content, SMALI_REGISTER_REGEX, AndroidColor.parseColor("#FF9800"), true)
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, COMMENT_LINE_HASH, AndroidColor.parseColor("#75859B"))
                }
                "sh" -> {
                    applyPattern(editable, content, KEYWORDS_SHELL, AndroidColor.parseColor("#FF5370"), true)
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, COMMENT_LINE_HASH, AndroidColor.parseColor("#75859B"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                }
                "py" -> {
                    applyPattern(editable, content, KEYWORDS_PYTHON, AndroidColor.parseColor("#FF5370"), true)
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, COMMENT_LINE_HASH, AndroidColor.parseColor("#75859B"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                }
                "css" -> {
                    applyPattern(editable, content, CSS_PROP_REGEX, AndroidColor.parseColor("#C792EA"))
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                    applyPattern(editable, content, COMMENT_LINE_SLASH, AndroidColor.parseColor("#75859B"))
                }
                else -> {
                    applyPattern(editable, content, STRING_REGEX, AndroidColor.parseColor("#C3E88D"))
                    applyPattern(editable, content, COMMENT_LINE_SLASH, AndroidColor.parseColor("#75859B"))
                    applyPattern(editable, content, COMMENT_LINE_HASH, AndroidColor.parseColor("#75859B"))
                    applyPattern(editable, content, NUMBER_REGEX, AndroidColor.parseColor("#F78C6C"))
                }
            }

            // Search query highlight
            if (searchQuery.isNotEmpty()) {
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                try {
                    val searchPattern = Pattern.compile(Pattern.quote(searchQuery), flags)
                    val matcher = searchPattern.matcher(content)
                    while (matcher.find()) {
                        editable.setSpan(
                            ForegroundColorSpan(AndroidColor.parseColor("#FFD54F")),
                            matcher.start(),
                            matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        editable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            matcher.start(),
                            matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                } catch (_: Exception) {}
            }
        } finally {
            isHighlighting = false
        }
    }

    private fun applyPattern(editable: Editable, text: String, pattern: Pattern, color: Int, isBold: Boolean = false) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            editable.setSpan(
                ForegroundColorSpan(color),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (isBold) {
                editable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    companion object {
        private val STRING_REGEX = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
        private val COMMENT_LINE_SLASH = Pattern.compile("//.*")
        private val COMMENT_LINE_HASH = Pattern.compile("#.*")
        private val NUMBER_REGEX = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)[fFLuU]?\\b")
        private val ANNOTATION_REGEX = Pattern.compile("@[a-zA-Z0-9_.]+")

        private val XML_TAG_REGEX = Pattern.compile("</?[a-zA-Z0-9_:-]+")
        private val XML_ATTR_REGEX = Pattern.compile("\\s+([a-zA-Z0-9_:-]+)(?=\\=)")
        private val XML_COMMENT_REGEX = Pattern.compile("<!--[\\s\\S]*?-->")

        private val JSON_KEY_REGEX = Pattern.compile("\"([^\"]+)\"\\s*:")
        private val JSON_BOOL_REGEX = Pattern.compile("\\b(true|false|null)\\b")

        private val SMALI_DIRECTIVE_REGEX = Pattern.compile("^\\s*\\.[a-zA-Z0-9_-]+", Pattern.MULTILINE)
        private val SMALI_REGISTER_REGEX = Pattern.compile("\\b[vp]\\d+\\b")

        private val CSS_PROP_REGEX = Pattern.compile("(?<=\\{|;|\\s)([a-zA-Z0-9_-]+)(?=\\s*:)")

        private val KEYWORDS_KT_JAVA = Pattern.compile(
            "\\b(package|import|class|interface|object|fun|val|var|private|protected|public|internal|" +
                    "final|open|abstract|sealed|data|override|if|else|when|for|while|do|return|break|continue|" +
                    "try|catch|finally|throw|null|true|false|this|super|is|as|in|out|by|new|extends|implements|" +
                    "static|void|boolean|int|long|float|double|char|byte|short|const|enum|suspend)\\b"
        )

        private val KEYWORDS_JS = Pattern.compile(
            "\\b(function|const|let|var|return|if|else|for|while|do|switch|case|break|continue|default|" +
                    "import|export|from|class|extends|constructor|new|this|super|async|await|try|catch|finally|" +
                    "throw|typeof|instanceof|interface|type|null|undefined|true|false)\\b"
        )

        private val KEYWORDS_SHELL = Pattern.compile(
            "\\b(echo|exit|if|then|else|elif|fi|for|in|do|done|case|esac|while|until|function|return|" +
                    "export|local|alias|chmod|chown|mkdir|rm|cp|mv|ls|grep|cat|su|sudo)\\b"
        )

        private val KEYWORDS_PYTHON = Pattern.compile(
            "\\b(def|class|return|if|elif|else|for|while|break|continue|import|from|as|try|except|finally|" +
                    "raise|with|pass|yield|async|await|lambda|global|True|False|None)\\b"
        )
    }
}

/**
 * Compose wrapper bridge for NativeCodeEditorView.
 */
@Composable
fun NativeCodeEditorComposable(
    content: String,
    fileExtension: String,
    searchQuery: String,
    isCaseSensitive: Boolean,
    wordWrap: Boolean,
    fontSizeSp: Int,
    isReadOnly: Boolean,
    showGutter: Boolean,
    onContentChange: (String) -> Unit,
    onCursorMetricsChange: (line: Int, col: Int, selLen: Int) -> Unit,
    onGutterClick: () -> Unit,
    editorRef: (NativeCodeEditorView) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gutterBg = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val gutterText = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val activeLineColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { ctx ->
            NativeCodeEditorView(ctx).apply {
                this.gutterView.visibility = if (showGutter) View.VISIBLE else View.GONE
                this.setEditorTheme(backgroundColor, textColor, gutterBg, gutterText, activeLineColor)
                this.setEditorFontSize(fontSizeSp.toFloat())
                this.setWordWrap(wordWrap)
                this.setReadOnly(isReadOnly)
                this.setLanguage(fileExtension)
                this.setSearchHighlight(searchQuery, isCaseSensitive)
                this.setTextContent(content)
                this.onTextChangedListener = onContentChange
                this.onCursorPositionChangedListener = onCursorMetricsChange
                this.onGutterClickListener = onGutterClick
                editorRef(this)
            }
        },
        update = { view ->
            view.gutterView.visibility = if (showGutter) View.VISIBLE else View.GONE
            view.setEditorTheme(backgroundColor, textColor, gutterBg, gutterText, activeLineColor)
            view.setEditorFontSize(fontSizeSp.toFloat())
            view.setWordWrap(wordWrap)
            view.setReadOnly(isReadOnly)
            view.setLanguage(fileExtension)
            view.setSearchHighlight(searchQuery, isCaseSensitive)
            view.onGutterClickListener = onGutterClick
            view.setTextContent(content)
        },
        modifier = modifier.testTag("native_code_editor_view")
    )
}
