// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsJumpTest {
    // a screen: a title (Int), settings, a fine-tune section with two settings, another closed section
    private val closedScreen = listOf(1, "a", "expand_fine_tune_x", "b", "expand_fine_tune_y")
    private val allOpenScreen = listOf(1, "a", "expand_fine_tune_x", "x1", "x2", "b", "expand_fine_tune_y", "y1")

    private fun shown(settings: List<Any?>) = settings.filterIsInstance<String>()

    @Test
    fun visibleSettingIsShownWithoutOpeningAnything() {
        assertEquals(JumpStep.Show(emptySet()), nextJumpStep("b", closedScreen, shown(closedScreen), emptySet()) { false })
    }

    @Test
    fun settingInAClosedSectionOpensTheClosedSectionsThenKeepsOnlyItsOwn() {
        val step = nextJumpStep("y1", closedScreen, shown(closedScreen), emptySet()) { false }
        assertEquals(JumpStep.OpenSections(setOf("expand_fine_tune_x", "expand_fine_tune_y")), step)
        // the screen now shows the settings of both sections
        val opened = (step as JumpStep.OpenSections).sections
        assertEquals(JumpStep.Show(setOf("expand_fine_tune_y")), nextJumpStep("y1", allOpenScreen, shown(allOpenScreen), opened) { false })
    }

    @Test
    fun sectionsTheUserOpenedAreNotTouched() {
        // x is open as saved by the user, only y is opened for the jump
        val screen = listOf(1, "a", "expand_fine_tune_x", "x1", "x2", "b", "expand_fine_tune_y")
        val step = nextJumpStep("y1", screen, shown(screen), emptySet()) { it == "expand_fine_tune_x" }
        assertEquals(JumpStep.OpenSections(setOf("expand_fine_tune_y")), step)
        assertEquals(JumpStep.Show(setOf("expand_fine_tune_y")),
            nextJumpStep("y1", allOpenScreen, shown(allOpenScreen), setOf("expand_fine_tune_y")) { it == "expand_fine_tune_x" })
    }

    @Test
    fun settingThatIsHiddenForAnotherReasonGivesUp() {
        // not in any section, e.g. it needs another setting to be on
        val step = nextJumpStep("z", closedScreen, shown(closedScreen), emptySet()) { false }
        assertEquals(JumpStep.OpenSections(setOf("expand_fine_tune_x", "expand_fine_tune_y")), step)
        assertEquals(JumpStep.GiveUp, nextJumpStep("z", allOpenScreen, shown(allOpenScreen), (step as JumpStep.OpenSections).sections) { false })
        // and right away if there are no closed sections
        assertEquals(JumpStep.GiveUp, nextJumpStep("z", listOf("a", "b"), listOf("a", "b"), emptySet()) { false })
    }
}
