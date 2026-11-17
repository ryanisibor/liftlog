package app.liftlog.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liftlog.data.db.WorkoutExerciseWithSets
import app.liftlog.ui.components.MuscleDiagram
import app.liftlog.ui.components.bestViewFor

/** A tile per exercise, showing its muscle map; the selected one is outlined in the accent. */
@Composable
internal fun ExerciseStrip(
    exercises: List<WorkoutExerciseWithSets>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, exercises.size) {
        if (selectedIndex in exercises.indices) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(exercises, key = { _, item -> item.workoutExercise.id }) { index, item ->
            val selected = index == selectedIndex
            val allDone = item.sets.isNotEmpty() && item.sets.all { it.completed }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.size(STRIP_TILE_WIDTH, STRIP_TILE_HEIGHT).clickable { onSelect(index) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MuscleDiagram(
                        primary = item.exercise.primaryMuscle,
                        secondary = item.exercise.secondaryMuscles,
                        view = bestViewFor(item.exercise.primaryMuscle),
                        modifier = Modifier.height(STRIP_TILE_HEIGHT - 12.dp),
                    )
                    if (allDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "All sets done",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge)
                                .padding(2.dp),
                        )
                    }
                }
            }
        }
        item(key = "add") {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(STRIP_TILE_WIDTH, STRIP_TILE_HEIGHT).clickable(onClick = onAdd),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Add exercise")
                }
            }
        }
    }
}

@Composable
internal fun EmptyWorkout(onAddExercise: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            "Add your first exercise to get started.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddExercise) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add exercise", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private val STRIP_TILE_WIDTH = 60.dp
private val STRIP_TILE_HEIGHT = 72.dp
