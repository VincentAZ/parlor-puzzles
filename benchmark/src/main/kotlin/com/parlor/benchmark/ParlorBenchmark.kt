package com.parlor.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The isolated .benchmark package is reset; personal/debug/production saves are never touched. */
@RunWith(AndroidJUnit4::class)
class ParlorBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val target = "com.parlor.puzzles.benchmark"
    private val smoke = InstrumentationRegistry.getArguments().getString("parlor.smoke") == "true"
    private val compilation: CompilationMode get() = if (smoke) CompilationMode.None() else
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3)

    @Before fun prepareReturningPlayer() {
        device.executeShellCommand("pm clear $target")
        device.executeShellCommand("am start -n $target/com.parlor.puzzles.MainActivity")
        onboard()
    }

    @Test fun coldStartToUsableHome() = benchmark.measureRepeated(
        packageName = target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = if (smoke) 1 else 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.text("Find your next puzzle")), 10_000))
    }

    @Test fun trafficMovesAndUndoFrames() = benchmark.measureRepeated(
        packageName = target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = if (smoke) 1 else 5,
        setupBlock = {
            device.executeShellCommand("pm clear $target")
            startActivityAndWait()
            onboard()
            clickText("Find your next puzzle")
            clickText("Level 1")
            click(By.descStartsWith("Piece A,"))
            click(By.descStartsWith("Piece B,"))
            click(By.desc("Move piece B to row 4"))
            click(By.descStartsWith("Piece A,"))
            click(By.desc("Slide piece A through the exit"))
            clickText("Begin a puzzle")
            check(device.wait(Until.hasObject(By.descStartsWith("Piece C,")), 10_000))
        },
    ) {
        repeat(6) {
            click(By.descStartsWith("Piece C,"))
            click(By.desc("Move piece C to column 3"))
            clickText("Undo")
        }
        check(device.wait(Until.hasObject(By.text("12 moves")), 10_000)) { "Move/undo loop did not commit all actions" }
    }

    private fun onboard() {
        clickText("Continue")
        clickText("Continue")
        clickText("Step inside")
        check(device.wait(Until.hasObject(By.text("Find your next puzzle")), 10_000))
    }
    private fun clickText(text: String) {
        if (!device.wait(Until.hasObject(By.text(text)), 3_000)) {
            check(device.wait(Until.hasObject(By.scrollable(true)), 10_000)) { "No screen available for $text" }
            UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text)
        }
        click(By.text(text))
    }
    private fun click(selector: BySelector) {
        val node = requireNotNull(device.wait(Until.findObject(selector), 10_000)) { "Missing control: $selector" }
        node.click()
        device.waitForIdle()
    }
}
