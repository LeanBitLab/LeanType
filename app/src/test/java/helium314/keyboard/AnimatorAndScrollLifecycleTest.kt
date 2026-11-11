// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.keyboard.emoji.EmojiPalettesView
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ocr.OcrCameraView
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.suggestions.VoiceVisualizerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimatorAndScrollLifecycleTest {

    private lateinit var latinIme: LatinIME
    private lateinit var activity: Activity
    private lateinit var hostRoot: FrameLayout

    @Before
    fun setUp() {
        latinIme = Robolectric.setupService(LatinIME::class.java)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        hostRoot = FrameLayout(latinIme)
        activity.setContentView(hostRoot)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun suggestionStripLoadingAnimatorStopsOnWindowHiddenAndDetach() {
        val strip = SuggestionStripView(latinIme, null)
        hostRoot.addView(strip)
        shadowOf(Looper.getMainLooper()).idle()
        setWindowVisibility(hostRoot, View.VISIBLE)
        assertTrue("strip attached", strip.isAttachedToWindow)
        assertEquals("strip window visible", View.VISIBLE, strip.windowVisibility)

        strip.showLoadingAnimation()
        val animBeforeHide = getField<ValueAnimator?>(strip, "loadingAnimator")
        assertTrue("loading active", getField<Boolean>(strip, "isLoadingAnimationActive"))
        assertNotNull("loadingAnimator started", animBeforeHide)
        assertTrue("loadingAnimator running", animBeforeHide!!.isRunning)

        setWindowVisibility(hostRoot, View.GONE)
        assertFalse("animator stopped on window hide", animBeforeHide.isRunning)
        assertFalse("animator ended on window hide", animBeforeHide.isStarted)
        assertNull(getField<ValueAnimator?>(strip, "loadingAnimator"))
        assertFalse(getField<Boolean>(strip, "isLoadingAnimationActive"))

        setWindowVisibility(hostRoot, View.VISIBLE)
        strip.showLoadingAnimation()
        val animBeforeDetach = getField<ValueAnimator?>(strip, "loadingAnimator")
        assertNotNull(animBeforeDetach)
        assertTrue(animBeforeDetach!!.isRunning)

        hostRoot.removeView(strip)
        assertFalse("animator stopped on detach", animBeforeDetach.isRunning)
        assertFalse("animator ended on detach", animBeforeDetach.isStarted)
        assertNull(getField<ValueAnimator?>(strip, "loadingAnimator"))
        assertFalse(getField<Boolean>(strip, "isLoadingAnimationActive"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun ocrCameraLoadingAnimatorStopsOnWindowHiddenAndDetach() {
        val ocrView = OcrCameraView(latinIme)
        hostRoot.addView(ocrView)
        shadowOf(Looper.getMainLooper()).idle()
        setWindowVisibility(hostRoot, View.VISIBLE)
        assertTrue("ocrView attached", ocrView.isAttachedToWindow)
        assertEquals("ocrView window visible", View.VISIBLE, ocrView.windowVisibility)

        val showLoading = OcrCameraView::class.java.getDeclaredMethod(
            "showLoading",
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }

        showLoading.invoke(ocrView, true)
        val animBeforeHide = getField<ValueAnimator?>(ocrView, "loadingAnimator")
        assertTrue("ocr loading active", getField<Boolean>(ocrView, "isLoadingAnimationActive"))
        assertNotNull("ocr loadingAnimator started", animBeforeHide)
        assertTrue("ocr loadingAnimator running", animBeforeHide!!.isRunning)

        setWindowVisibility(hostRoot, View.GONE)
        assertFalse("ocr animator stopped on window hide", animBeforeHide.isRunning)
        assertFalse("ocr animator ended on window hide", animBeforeHide.isStarted)
        assertNull(getField<ValueAnimator?>(ocrView, "loadingAnimator"))
        assertFalse(getField<Boolean>(ocrView, "isLoadingAnimationActive"))

        setWindowVisibility(hostRoot, View.VISIBLE)
        showLoading.invoke(ocrView, true)
        val animBeforeDetach = getField<ValueAnimator?>(ocrView, "loadingAnimator")
        assertNotNull(animBeforeDetach)
        assertTrue(animBeforeDetach!!.isRunning)

        hostRoot.removeView(ocrView)
        assertFalse("ocr animator stopped on detach", animBeforeDetach.isRunning)
        assertFalse("ocr animator ended on detach", animBeforeDetach.isStarted)
        assertNull(getField<ValueAnimator?>(ocrView, "loadingAnimator"))
        assertFalse(getField<Boolean>(ocrView, "isLoadingAnimationActive"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun voiceVisualizerAnimatorStopsOnWindowHiddenAndDetach() {
        val visualizer = VoiceVisualizerView(latinIme)
        hostRoot.addView(visualizer)
        shadowOf(Looper.getMainLooper()).idle()
        setWindowVisibility(hostRoot, View.VISIBLE)
        assertTrue("visualizer attached", visualizer.isAttachedToWindow)
        assertEquals("visualizer window visible", View.VISIBLE, visualizer.windowVisibility)

        visualizer.setMode(VoiceVisualizerView.Mode.RECORDING)
        val animBeforeHide = getField<ValueAnimator?>(visualizer, "animator")
        assertNotNull("visualizer animator started", animBeforeHide)
        assertTrue("visualizer animator running", animBeforeHide!!.isRunning)

        setWindowVisibility(hostRoot, View.GONE)
        assertFalse("visualizer animator stopped on window hide", animBeforeHide.isRunning)
        assertFalse("visualizer animator ended on window hide", animBeforeHide.isStarted)
        assertNull(getField<ValueAnimator?>(visualizer, "animator"))

        setWindowVisibility(hostRoot, View.VISIBLE)
        val animBeforeDetach = getField<ValueAnimator?>(visualizer, "animator")
        assertNotNull("visualizer animator auto-resumed on visible", animBeforeDetach)
        assertTrue("visualizer animator running after resume", animBeforeDetach!!.isRunning)

        hostRoot.removeView(visualizer)
        assertFalse("visualizer animator stopped on detach", animBeforeDetach.isRunning)
        assertFalse("visualizer animator ended on detach", animBeforeDetach.isStarted)
        assertNull(getField<ValueAnimator?>(visualizer, "animator"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun emojiScrollToTabDoesNotSpinOnZeroWidthAndClearsOnHideAndDetach() {
        val emojiView = LayoutInflater.from(latinIme).inflate(R.layout.emoji_palettes_view, hostRoot, false)
            as EmojiPalettesView
        val tabStrip = LinearLayout(latinIme)
        val scrollView = HorizontalScrollView(latinIme).apply {
            visibility = View.VISIBLE
            addView(tabStrip)
        }
        val tabView = TextView(latinIme).apply { visibility = View.VISIBLE }
        tabStrip.addView(tabView)
        hostRoot.addView(scrollView)
        hostRoot.addView(emojiView)
        shadowOf(Looper.getMainLooper()).idle()
        setWindowVisibility(hostRoot, View.VISIBLE)

        setField(emojiView, "mTabStrip", tabStrip)

        val scrollToTab = EmojiPalettesView::class.java.getDeclaredMethod(
            "scrollToTab",
            View::class.java,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }

        // Width is 0 before layout: should register a single-shot layout listener, never spin Looper
        scrollToTab.invoke(emojiView, tabView, false)
        assertNotNull(getField<View.OnLayoutChangeListener?>(emojiView, "pendingTabScrollListener"))
        shadowOf(Looper.getMainLooper()).idle()

        // Hiding the window clears any pending scroll listener and cancels cursor blink callbacks
        setWindowVisibility(hostRoot, View.GONE)
        assertNull(getField<View.OnLayoutChangeListener?>(emojiView, "pendingTabScrollListener"))

        // Re-show and queue pending layout scroll, then detach: listener must be cleared
        setWindowVisibility(hostRoot, View.VISIBLE)
        scrollToTab.invoke(emojiView, tabView, false)
        assertNotNull(getField<View.OnLayoutChangeListener?>(emojiView, "pendingTabScrollListener"))

        hostRoot.removeView(emojiView)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(getField<View.OnLayoutChangeListener?>(emojiView, "pendingTabScrollListener"))

        // Detaching scrollView as well leaves main looper completely idle with no reposting
        hostRoot.removeView(scrollView)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        scrollToTab.invoke(emojiView, tabView, false)
        assertNull(getField<View.OnLayoutChangeListener?>(emojiView, "pendingTabScrollListener"))
    }

    /** Replicates ViewRootImpl updating AttachInfo.mWindowVisibility and dispatching visibility synchronously. */
    private fun setWindowVisibility(root: View, visibility: Int) {
        val attachInfoField = View::class.java.getDeclaredField("mAttachInfo").apply { isAccessible = true }
        val attachInfo = attachInfoField.get(root)
        assertNotNull("View must be attached before dispatching window visibility", attachInfo)
        val winVisField = attachInfo!!.javaClass.getDeclaredField("mWindowVisibility").apply { isAccessible = true }
        winVisField.setInt(attachInfo, visibility)
        root.dispatchWindowVisibilityChanged(visibility)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, name: String): T {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        return field.get(target) as T
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field.set(target, value)
    }
}
