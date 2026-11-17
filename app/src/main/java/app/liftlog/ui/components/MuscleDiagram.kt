package app.liftlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.liftlog.data.Muscle

/*
 * A schematic body, front and back, with one region per muscle group. An exercise's main
 * muscle is drawn in the accent colour and its secondary muscles in a dimmer shade.
 *
 * All coordinates are in a 100 x 200 box per figure. Regions are drawn on the left side of
 * the body and mirrored around x = 50, unless marked as centred.
 */

enum class BodyView { FRONT, BACK }

private sealed interface Shape {
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Shape
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float) : Shape
    data class Poly(val points: List<Pair<Float, Float>>) : Shape
}

private class Region(val shape: Shape, val mirrored: Boolean = true)

private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = Shape.Oval(cx, cy, rx, ry)
private fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float) = Shape.Rect(l, t, r, b, radius)
private fun poly(vararg points: Pair<Float, Float>) = Shape.Poly(points.toList())

/** The grey silhouette underneath the muscles; already symmetric, so never mirrored. */
private val body: List<Shape> = listOf(
    oval(50f, 13f, 8.5f, 10f),
    rect(45.5f, 20f, 54.5f, 30f, 2f),
    poly(30f to 29f, 70f to 29f, 75f to 40f, 68f to 62f, 65f to 88f, 67f to 102f, 33f to 102f, 35f to 88f, 32f to 62f, 25f to 40f),
    rect(15f, 31f, 28f, 64f, 6f), rect(72f, 31f, 85f, 64f, 6f),
    rect(11.5f, 60f, 23.5f, 97f, 5.5f), rect(76.5f, 60f, 88.5f, 97f, 5.5f),
    oval(16.5f, 101f, 4f, 5f), oval(83.5f, 101f, 4f, 5f),
    rect(33f, 96f, 49.5f, 152f, 7f), rect(50.5f, 96f, 67f, 152f, 7f),
    rect(35.5f, 146f, 48f, 192f, 5.5f), rect(52f, 146f, 64.5f, 192f, 5.5f),
    oval(41f, 195f, 6f, 3.2f), oval(59f, 195f, 6f, 3.2f),
)

private val neck = Region(rect(46.5f, 21f, 53.5f, 28f, 2f), mirrored = false)
private val shoulders = Region(oval(23.5f, 36.5f, 7f, 7.5f))
private val upperArm = Region(oval(21.5f, 51f, 5f, 10.5f))
private val forearms = Region(oval(17.5f, 77f, 4.8f, 14f))

private val frontRegions: Map<Muscle, Region> = mapOf(
    Muscle.NECK to neck,
    Muscle.TRAPS to Region(poly(45f to 25f, 45f to 30f, 33f to 30.5f)),
    Muscle.SHOULDERS to shoulders,
    Muscle.CHEST to Region(rect(32.5f, 33f, 49f, 50f, 5f)),
    Muscle.BICEPS to upperArm,
    Muscle.FOREARMS to forearms,
    Muscle.ABS to Region(rect(43f, 53f, 57f, 91f, 4f), mirrored = false),
    Muscle.OBLIQUES to Region(oval(38f, 71f, 3.6f, 15f)),
    Muscle.ABDUCTORS to Region(oval(35.5f, 104f, 2.6f, 8f)),
    Muscle.ADDUCTORS to Region(oval(47.3f, 113f, 2.3f, 12f)),
    Muscle.QUADS to Region(oval(41f, 125f, 6f, 23f)),
    Muscle.CALVES to Region(oval(41.7f, 168f, 4.6f, 15f)),
)

private val backRegions: Map<Muscle, Region> = mapOf(
    Muscle.NECK to neck,
    Muscle.UPPER_BACK to Region(rect(33f, 34f, 49.2f, 56f, 4f)),
    // Drawn after the upper back so it sits on top of it.
    Muscle.TRAPS to Region(
        poly(50f to 24f, 64f to 31f, 55f to 38f, 50f to 54f, 45f to 38f, 36f to 31f),
        mirrored = false,
    ),
    Muscle.SHOULDERS to shoulders,
    Muscle.TRICEPS to upperArm,
    Muscle.FOREARMS to forearms,
    Muscle.LATS to Region(poly(31.5f to 50f, 44f to 58f, 47f to 82f, 37f to 86f, 33f to 66f)),
    Muscle.LOWER_BACK to Region(rect(44f, 60f, 56f, 92f, 4f), mirrored = false),
    Muscle.GLUTES to Region(oval(41.5f, 104f, 8f, 8.5f)),
    Muscle.ABDUCTORS to Region(oval(34f, 97f, 2.4f, 5f)),
    Muscle.HAMSTRINGS to Region(oval(41.3f, 131f, 6.2f, 18f)),
    Muscle.CALVES to Region(oval(41.7f, 166f, 5f, 14.5f)),
)

/** The view that shows [muscle] best, for when there is room for only one figure. */
fun bestViewFor(muscle: Muscle): BodyView = when (muscle) {
    Muscle.LATS, Muscle.UPPER_BACK, Muscle.LOWER_BACK, Muscle.TRAPS, Muscle.TRICEPS,
    Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.CALVES,
    -> BodyView.BACK
    else -> BodyView.FRONT
}

/** One figure. Give it a height; its width follows from the 1:2 proportions. */
@Composable
fun MuscleDiagram(
    primary: Muscle,
    secondary: List<Muscle>,
    view: BodyView,
    modifier: Modifier = Modifier,
) = MuscleDiagram(setOf(primary), secondary.toSet(), view, modifier)

/** One figure highlighting several main muscles at once, as for a whole training day. */
@Composable
fun MuscleDiagram(
    primary: Set<Muscle>,
    secondary: Set<Muscle>,
    view: BodyView,
    modifier: Modifier = Modifier,
    /** If given, each muscle is shaded by its value from 0 to 1 instead of by primary and secondary. */
    intensity: Map<Muscle, Float>? = null,
) {
    val bodyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = primaryColor.copy(alpha = 0.42f)
    val regions = if (view == BodyView.FRONT) frontRegions else backRegions

    // Height-first: callers set the height, and a width-first ratio would overflow it.
    Canvas(modifier = modifier.aspectRatio(0.5f, matchHeightConstraintsFirst = true)) {
        val scale = minOf(size.width / 100f, size.height / 200f)
        val dx = (size.width - 100f * scale) / 2f
        val dy = (size.height - 200f * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            body.forEach { draw(it, bodyColor) }
            regions.forEach { (muscle, region) ->
                val color = when {
                    intensity != null -> intensity[muscle]?.takeIf { it > 0f }
                        // Start a little above the idle shade so even one set is visible.
                        ?.let { lerp(idleColor, primaryColor, 0.25f + 0.75f * it.coerceAtMost(1f)) }
                        ?: idleColor
                    Muscle.FULL_BODY in primary || muscle in primary -> primaryColor
                    muscle in secondary -> secondaryColor
                    else -> idleColor
                }
                draw(region.shape, color)
                if (region.mirrored) draw(region.shape, color, mirrored = true)
            }
        }
    }
}

/** Front and back figures side by side, described for screen readers. */
@Composable
fun MuscleDiagramPair(
    primary: Muscle,
    secondary: List<Muscle>,
    modifier: Modifier = Modifier,
) = MuscleDiagramPair(setOf(primary), secondary.toSet(), modifier)

/** Front and back figures shaded by how much each muscle was trained, from 0 to 1. */
@Composable
fun MuscleHeatPair(intensity: Map<Muscle, Float>, description: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        MuscleDiagram(emptySet(), emptySet(), BodyView.FRONT, Modifier.fillMaxHeight(), intensity)
        MuscleDiagram(emptySet(), emptySet(), BodyView.BACK, Modifier.fillMaxHeight(), intensity)
    }
}

/** Front and back figures for several main muscles at once. */
@Composable
fun MuscleDiagramPair(
    primary: Set<Muscle>,
    secondary: Set<Muscle>,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
        append("Main muscles: ${primary.joinToString { it.label }}")
        if (secondary.isNotEmpty()) append(". Also works: ${secondary.joinToString { it.label }}")
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        MuscleDiagram(primary, secondary, BodyView.FRONT, Modifier.fillMaxHeight())
        MuscleDiagram(primary, secondary, BodyView.BACK, Modifier.fillMaxHeight())
    }
}

private fun DrawScope.draw(shape: Shape, color: Color, mirrored: Boolean = false) {
    fun x(value: Float) = if (mirrored) 100f - value else value
    when (shape) {
        is Shape.Oval -> drawOval(
            color = color,
            topLeft = Offset(x(shape.cx) - shape.rx, shape.cy - shape.ry),
            size = Size(shape.rx * 2, shape.ry * 2),
        )
        is Shape.Rect -> drawRoundRect(
            color = color,
            topLeft = Offset(minOf(x(shape.left), x(shape.right)), shape.top),
            size = Size(shape.right - shape.left, shape.bottom - shape.top),
            cornerRadius = CornerRadius(shape.radius, shape.radius),
        )
        is Shape.Poly -> {
            val path = Path()
            shape.points.forEachIndexed { index, (px, py) ->
                if (index == 0) path.moveTo(x(px), py) else path.lineTo(x(px), py)
            }
            path.close()
            drawPath(path, color)
        }
    }
}
