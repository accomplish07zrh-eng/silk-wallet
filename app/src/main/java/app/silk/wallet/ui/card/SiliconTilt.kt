package app.silk.wallet.ui.card

import androidx.compose.ui.geometry.Offset
import kotlin.math.exp

/** A quick response for the illustration and a slower one for liquids, fabric and dust. */
internal class SiliconTilt {
    var angle = Offset.Zero
        private set
    var momentum = Offset.Zero
        private set

    private var delayed = Offset.Zero
    private var lastFrame = Float.NaN

    fun update(time: Float?, input: Offset) {
        val elapsed = if (time == null) Float.NaN else time - lastFrame
        if (!elapsed.isFinite() || elapsed < 0f || elapsed > 0.18f) {
            angle = input
            delayed = input
            momentum = Offset.Zero
        } else if (elapsed > 0f) {
            angle += (input - angle) * (1f - exp(-elapsed * 24f))
            delayed += (input - delayed) * (1f - exp(-elapsed * 6f))
            val difference = (angle - delayed) * 2.1f
            momentum = Offset(difference.x.coerceIn(-1f, 1f), difference.y.coerceIn(-1f, 1f))
        }
        lastFrame = time ?: Float.NaN
    }
}
