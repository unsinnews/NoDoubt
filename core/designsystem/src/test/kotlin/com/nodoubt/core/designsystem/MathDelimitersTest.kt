package com.nodoubt.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class MathDelimitersTest {
    @Test fun modelMathDelimitersAreConverted() {
        assertEquals("行内 \$\$x\$\$\n\$\$x^2\$\$", normalizeMathDelimiters("行内 \\(x\\)\n\\[x^2\\]"))
    }

    @Test fun inlineAndFencedCodeKeepLiteralBackslashes() {
        val source = "`\\[literal\\]`\n```regex\n\\[a-z\\]\n```\n\\(x\\)"
        val expected = "`\\[literal\\]`\n```regex\n\\[a-z\\]\n```\n\$\$x\$\$"
        assertEquals(expected, normalizeMathDelimiters(source))
    }

    @Test fun tildesAndLongBacktickDelimitersStayLiteral() {
        val source = "~~~text\n\\(literal\\)\n~~~\n``code ` \\[``"
        assertEquals(source, normalizeMathDelimiters(source))
    }

    @Test fun singleDollarMathUsesMarkwonInlineDelimiter() {
        assertEquals("正文 \$\$x^2+y^2=z^2\$\$，\$\$y\$\$。",
            normalizeMathDelimiters("正文 \$x^2+y^2=z^2\$，\$y\$。"))
    }

    @Test fun existingDoubleDollarMathIsUnchanged() {
        val source = "正文 \$\$x + y\$\$\n\$\$\nx^2\n\$\$"
        assertEquals(source, normalizeMathDelimiters(source))
    }

    @Test fun currencyEscapesAndCodeAreNotMath() {
        val source = "价格 \$5 和 \$10；US\$5,US\$10；\\\$x\\\$；`\$x\$`\n```\n\$x\$\n```"
        assertEquals(source, normalizeMathDelimiters(source))
    }

    @Test fun codeAndUnclosedDollarDoNotConsumeFollowingMath() {
        val source = "\$5 `literal`，随后 \$x\$"
        assertEquals("\$5 `literal`，随后 \$\$x\$\$", normalizeMathDelimiters(source))
    }

    @Test fun escapedBackslashDoesNotCreateMath() {
        assertEquals("\\\\(literal\\\\)", normalizeMathDelimiters("\\\\(literal\\\\)"))
    }
}
