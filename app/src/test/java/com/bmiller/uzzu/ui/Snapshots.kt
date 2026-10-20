package com.bmiller.uzzu.ui

import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * Roborazzi goldens. `./gradlew recordRoborazziDebug` (re)writes the committed reference PNGs in
 * app/src/test/snapshots/; `./gradlew verifyRoborazziDebug` fails on a visual change and writes
 * *_compare.png diffs to app/build/outputs/roborazzi/. A plain `testDebugUnitTest` (CI) renders
 * but does not compare: the goldens are recorded on Windows and Linux font rasterization differs.
 */
object Snapshots {
    private const val DIR = "src/test/snapshots"

    fun path(name: String) = "$DIR/$name.png"

    // JVM rendering is deterministic on one OS; the small threshold only absorbs antialiasing noise
    // (0.1 % of a 1920x1080 frame is ~2000 px, below one changed word).
    val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))
}
