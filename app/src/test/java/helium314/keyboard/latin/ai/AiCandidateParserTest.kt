// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiCandidateParserTest {
    @Test fun parsesJsonCandidatesAndKeepsFirstThree() {
        val parsed = AiCandidateParser.parse(
            """
            {
              "candidates": [
                {"action": "REPLY", "text": " first "},
                {"action": "REWRITE", "text": "second"},
                {"action": "INSPIRE", "text": "third"},
                {"action": "REPLY", "text": "fourth"}
              ]
            }
            """.trimIndent(),
            fallbackAction = AiCandidateAction.REPLY
        ).getOrThrow()

        assertEquals(3, parsed.size)
        assertEquals(AiCandidateAction.REPLY, parsed[0].action)
        assertEquals("first", parsed[0].text)
        assertEquals(AiCandidateAction.REWRITE, parsed[1].action)
        assertEquals(AiCandidateAction.INSPIRE, parsed[2].action)
    }

    @Test fun parsesPlainTextLinesWithFallbackAction() {
        val parsed = AiCandidateParser.parse(
            """
            candidate one

            candidate two
            candidate three
            candidate four
            """.trimIndent(),
            fallbackAction = AiCandidateAction.INSPIRE
        ).getOrThrow()

        assertEquals(3, parsed.size)
        assertEquals(listOf("candidate one", "candidate two", "candidate three"), parsed.map { it.text })
        assertEquals(AiCandidateAction.INSPIRE, parsed[0].action)
    }

    @Test fun rejectsEmptyCandidates() {
        assertFailsWith<IllegalArgumentException> {
            AiCandidateParser.parse("""{"candidates": [{"text": ""}]}""", AiCandidateAction.REPLY).getOrThrow()
        }
    }
}
