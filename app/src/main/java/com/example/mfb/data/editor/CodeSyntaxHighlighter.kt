package com.example.mfb.data.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.mfb.domain.model.EditorLanguage

data class EditorSyntaxPalette(
    val text: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val tag: Color,
    val attributeName: Color,
    val attributeValue: Color,
    val entity: Color,
    val property: Color,
    val function: Color,
    val operator: Color,
    val booleanOrNull: Color,
    val searchHighlightBg: Color,
    val activeSearchHighlightBg: Color,
    val bracketHighlightBg: Color
) {
    companion object {
        fun dark(): EditorSyntaxPalette = EditorSyntaxPalette(
            text = Color(0xFFE6EDF3),
            keyword = Color(0xFFC678DD),
            string = Color(0xFF98C379),
            number = Color(0xFFD19A66),
            comment = Color(0xFF7F848E),
            tag = Color(0xFFE06C75),
            attributeName = Color(0xFFD19A66),
            attributeValue = Color(0xFF98C379),
            entity = Color(0xFF56B6C2),
            property = Color(0xFF61AFEF),
            function = Color(0xFF61AFEF),
            operator = Color(0xFF56B6C2),
            booleanOrNull = Color(0xFFD19A66),
            searchHighlightBg = Color(0x55E5C07B),
            activeSearchHighlightBg = Color(0x99FF9800),
            bracketHighlightBg = Color(0x446C63FF)
        )

        fun light(): EditorSyntaxPalette = EditorSyntaxPalette(
            text = Color(0xFF1F2328),
            keyword = Color(0xFF8250DF),
            string = Color(0xFF116329),
            number = Color(0xFF953800),
            comment = Color(0xFF6E7781),
            tag = Color(0xFFCF222E),
            attributeName = Color(0xFF953800),
            attributeValue = Color(0xFF0A3069),
            entity = Color(0xFF0550AE),
            property = Color(0xFF0550AE),
            function = Color(0xFF0550AE),
            operator = Color(0xFF0550AE),
            booleanOrNull = Color(0xFF0550AE),
            searchHighlightBg = Color(0x55FFD54F),
            activeSearchHighlightBg = Color(0x99FB8C00),
            bracketHighlightBg = Color(0x336C63FF)
        )
    }
}

class CodeEditorVisualTransformation(
    private val language: EditorLanguage,
    private val syntaxEnabled: Boolean,
    private val palette: EditorSyntaxPalette,
    private val searchMatches: List<IntRange> = emptyList(),
    private val activeMatchIndex: Int = -1,
    private val matchingBracketPair: Pair<Int, Int>? = null
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = CodeSyntaxHighlighter.highlight(
            source = raw,
            language = language,
            syntaxEnabled = syntaxEnabled,
            palette = palette,
            searchMatches = searchMatches,
            activeMatchIndex = activeMatchIndex,
            matchingBracketPair = matchingBracketPair
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

object CodeSyntaxHighlighter {

    private val HTML_COMMENT_REGEX = Regex("<!--([\\s\\S]*?)-->")
    private val HTML_DOCTYPE_REGEX = Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE)
    private val HTML_ENTITY_REGEX = Regex("&(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]+);")
    private val HTML_TAG_REGEX = Regex("</?([a-zA-Z][a-zA-Z0-9:-]*)([^>]*)>")
    private val HTML_ATTR_REGEX = Regex("([a-zA-Z_:][a-zA-Z0-9_.:-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')")

    private val CSS_COMMENT_REGEX = Regex("/\\*([\\s\\S]*?)\\*/")
    private val CSS_AT_RULE_REGEX = Regex("@[a-zA-Z-]+")
    private val CSS_PROPERTY_REGEX = Regex("([a-zA-Z-]+)\\s*(?=:)")
    private val CSS_COLOR_HEX_REGEX = Regex("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b")
    private val CSS_NUMBER_UNIT_REGEX = Regex("\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|vmin|vmax|deg|rad|s|ms|fr)?\\b")
    private val CSS_SELECTOR_REGEX = Regex("(?<=^|\\}|\\n)\\s*([^{}/\\n]+)(?=\\{)")

    private val JS_TOKEN_REGEX = Regex(
        "(//[^\\n]*)|" +
            "(/\\*[\\s\\S]*?\\*/)|" +
            "(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)|" +
            "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|return|super|switch|this|throw|try|typeof|var|void|while|with|yield|async|await|of|from|as|static)\\b|" +
            "\\b(true|false|null|undefined|NaN|Infinity)\\b|" +
            "\\b(0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b|" +
            "\\b([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?=\\()|" +
            "([+\\-*/%=!<>&|^~?:]+)"
    )

    private val JSON_TOKEN_REGEX = Regex(
        "(\"(?:\\\\.|[^\"\\\\])*\")\\s*:|" +
            "(\"(?:\\\\.|[^\"\\\\])*\")|" +
            "(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|" +
            "\\b(true|false|null)\\b"
    )

    private val MD_HEADING_REGEX = Regex("(?m)^#{1,6}\\s+.*$")
    private val MD_CODE_REGEX = Regex("`[^`\\n]+`")

    fun highlight(
        source: String,
        language: EditorLanguage,
        syntaxEnabled: Boolean,
        palette: EditorSyntaxPalette,
        searchMatches: List<IntRange> = emptyList(),
        activeMatchIndex: Int = -1,
        matchingBracketPair: Pair<Int, Int>? = null
    ): AnnotatedString {
        return buildAnnotatedString {
            append(source)

            if (syntaxEnabled && source.isNotEmpty() && language.supportsSyntaxHighlighting) {
                when (language) {
                    EditorLanguage.HTML, EditorLanguage.XML -> highlightHtml(source, palette)
                    EditorLanguage.CSS -> highlightCss(source, palette)
                    EditorLanguage.JAVASCRIPT -> highlightJs(source, palette)
                    EditorLanguage.JSON -> highlightJson(source, palette)
                    EditorLanguage.MARKDOWN -> highlightMarkdown(source, palette)
                    else -> {}
                }
            }

            // Overlay matching brackets
            matchingBracketPair?.let { (first, second) ->
                val bracketStyle = SpanStyle(
                    background = palette.bracketHighlightBg,
                    fontWeight = FontWeight.Bold
                )
                if (first in source.indices) {
                    addStyle(bracketStyle, first, first + 1)
                }
                if (second in source.indices) {
                    addStyle(bracketStyle, second, second + 1)
                }
            }

            // Overlay search matches
            searchMatches.forEachIndexed { idx, range ->
                val start = range.first.coerceIn(0, source.length)
                val end = (range.last + 1).coerceIn(start, source.length)
                if (start < end) {
                    val bg = if (idx == activeMatchIndex) {
                        palette.activeSearchHighlightBg
                    } else {
                        palette.searchHighlightBg
                    }
                    addStyle(SpanStyle(background = bg), start, end)
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightHtml(source: String, palette: EditorSyntaxPalette) {
        HTML_ENTITY_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.entity), m.range.first, m.range.last + 1)
        }
        HTML_DOCTYPE_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        HTML_TAG_REGEX.findAll(source).forEach { tagMatch ->
            val tagGroup = tagMatch.groups[1]
            val attrsGroup = tagMatch.groups[2]
            addStyle(SpanStyle(color = palette.operator), tagMatch.range.first, tagMatch.range.first + 1)
            addStyle(SpanStyle(color = palette.operator), tagMatch.range.last, tagMatch.range.last + 1)
            if (tagGroup != null) {
                addStyle(
                    SpanStyle(color = palette.tag, fontWeight = FontWeight.SemiBold),
                    tagGroup.range.first,
                    tagGroup.range.last + 1
                )
            }
            if (attrsGroup != null) {
                val attrsOffset = attrsGroup.range.first
                HTML_ATTR_REGEX.findAll(attrsGroup.value).forEach { attrMatch ->
                    val nameGroup = attrMatch.groups[1]
                    val valGroup = attrMatch.groups[2]
                    if (nameGroup != null) {
                        addStyle(
                            SpanStyle(color = palette.attributeName),
                            attrsOffset + nameGroup.range.first,
                            attrsOffset + nameGroup.range.last + 1
                        )
                    }
                    if (valGroup != null) {
                        addStyle(
                            SpanStyle(color = palette.attributeValue),
                            attrsOffset + valGroup.range.first,
                            attrsOffset + valGroup.range.last + 1
                        )
                    }
                }
            }
        }
        HTML_COMMENT_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightCss(source: String, palette: EditorSyntaxPalette) {
        CSS_SELECTOR_REGEX.findAll(source).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.tag, fontWeight = FontWeight.SemiBold), g.range.first, g.range.last + 1)
        }
        CSS_AT_RULE_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        CSS_PROPERTY_REGEX.findAll(source).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.property), g.range.first, g.range.last + 1)
        }
        CSS_COLOR_HEX_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.number, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
        }
        CSS_NUMBER_UNIT_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
        }
        CSS_COMMENT_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightJs(source: String, palette: EditorSyntaxPalette) {
        JS_TOKEN_REGEX.findAll(source).forEach { m ->
            when {
                m.groups[1] != null || m.groups[2] != null -> {
                    addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
                }
                m.groups[3] != null -> {
                    addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
                }
                m.groups[4] != null -> {
                    addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
                }
                m.groups[5] != null -> {
                    addStyle(SpanStyle(color = palette.booleanOrNull, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
                }
                m.groups[6] != null -> {
                    addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
                }
                m.groups[7] != null -> {
                    val g = m.groups[7]!!
                    addStyle(SpanStyle(color = palette.function), g.range.first, g.range.last + 1)
                }
                m.groups[8] != null -> {
                    addStyle(SpanStyle(color = palette.operator), m.range.first, m.range.last + 1)
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightJson(source: String, palette: EditorSyntaxPalette) {
        JSON_TOKEN_REGEX.findAll(source).forEach { m ->
            when {
                m.groups[1] != null -> {
                    val g = m.groups[1]!!
                    addStyle(SpanStyle(color = palette.property, fontWeight = FontWeight.Medium), g.range.first, g.range.last + 1)
                }
                m.groups[2] != null -> {
                    addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
                }
                m.groups[3] != null -> {
                    addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
                }
                m.groups[4] != null -> {
                    addStyle(SpanStyle(color = palette.booleanOrNull, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightMarkdown(source: String, palette: EditorSyntaxPalette) {
        MD_HEADING_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
        }
        MD_CODE_REGEX.findAll(source).forEach { m ->
            addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
        }
    }
}
