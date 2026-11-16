// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import kotlin.test.Test
import kotlin.test.assertEquals

class NavBarOverlapTest {
    @Test fun `view under the navigation bar reserves its full height`() {
        // edge to edge: the view ends at the bottom of the screen
        assertEquals(144, navBarOverlap(reportedInset = 144, viewBottom = 2400, screenHeight = 2400))
    }

    @Test fun `view above the navigation bar reserves nothing`() {
        // the window leaves room for the navigation bar itself, the reported inset must not be added again
        assertEquals(0, navBarOverlap(reportedInset = 144, viewBottom = 2256, screenHeight = 2400))
    }

    @Test fun `window that ends above the navigation bar reserves nothing`() {
        // the keyboard window itself stops at the top of the navigation bar (Samsung, Android 12): the view ends at the
        // bottom of its window, but on the screen it is above the navigation bar
        assertEquals(0, navBarOverlap(reportedInset = 144, viewBottom = 2256, screenHeight = 2400))
    }

    @Test fun `view partly under the navigation bar reserves the overlap`() {
        assertEquals(44, navBarOverlap(reportedInset = 144, viewBottom = 2300, screenHeight = 2400))
    }

    @Test fun `never more than the reported inset or less than nothing`() {
        assertEquals(48, navBarOverlap(reportedInset = 48, viewBottom = 2500, screenHeight = 2400))
        assertEquals(0, navBarOverlap(reportedInset = 0, viewBottom = 2400, screenHeight = 2400))
    }
}
