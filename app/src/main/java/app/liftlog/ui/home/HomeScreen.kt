package app.liftlog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.liftlog.ui.appContainer

/** Everything the Home screen can navigate to. */
class HomeNavigation(
    val openActiveWorkout: (Long) -> Unit,
    val openHistory: () -> Unit,
    val openProgram: (Long) -> Unit,
    val openPrograms: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigation: HomeNavigation) {
    val container = appContainer()
    val viewModel = viewModel { HomeViewModel(container.workoutDao, container.programDao, container.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.program?.name ?: "LiftLog") },
                actions = {
                    if (plan != null) {
                        IconButton(onClick = { navigation.openProgram(plan.program.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit program")
                        }
                    }
                    IconButton(onClick = navigation.openHistory) {
                        Icon(Icons.Default.DateRange, contentDescription = "Workout history")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.activeWorkout == null) "Start empty workout" else "Resume workout") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.startEmptyOrResume(navigation.openActiveWorkout)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> Unit
                plan == null || plan.days.isEmpty() -> NoPlan(
                    hasProgram = plan != null,
                    resuming = state.activeWorkout != null,
                    onSetUp = { if (plan != null) navigation.openProgram(plan.program.id) else navigation.openPrograms() },
                    onStartEmpty = { viewModel.startEmptyOrResume(navigation.openActiveWorkout) },
                )
                else -> PlanPager(
                    plan = plan,
                    state = state,
                    onStart = { dayId -> viewModel.startDayOrResume(dayId, navigation.openActiveWorkout) },
                )
            }
        }
    }
}

@Composable
private fun PlanPager(plan: PlanState, state: HomeState, onStart: (Long) -> Unit) {
    val dayCount by rememberUpdatedState(plan.days.size)
    val pagerState = rememberPagerState { dayCount }

    // Show the workout that is due: when the screen first opens, and again whenever the
    // due day changes because a workout was finished. In between, the user's swiping wins.
    var shownNextIndex by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(plan.nextDayIndex) {
        if (shownNextIndex != plan.nextDayIndex) {
            if (shownNextIndex == -1) {
                pagerState.scrollToPage(plan.nextDayIndex)
            } else {
                pagerState.animateScrollToPage(plan.nextDayIndex)
            }
            shownNextIndex = plan.nextDayIndex
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text("Workout plan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.days.forEachIndexed { index, day ->
                    val trained = day.day.id in plan.trainedThisWeek
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 14.dp else 10.dp)
                            .background(
                                color = when {
                                    trained -> MaterialTheme.colorScheme.primary
                                    index == pagerState.currentPage -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                key = { plan.days.getOrNull(it)?.day?.id ?: it },
                pageSpacing = 12.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val day = plan.days.getOrNull(page) ?: return@HorizontalPager
                PlanDayPage(
                    day = day,
                    isNext = page == plan.nextDayIndex,
                    trainedThisWeek = day.day.id in plan.trainedThisWeek,
                    lastWeights = plan.lastWeights,
                    unit = state.unit,
                )
            }

            val currentDay = plan.days.getOrNull(pagerState.currentPage)
            Button(
                onClick = { currentDay?.let { onStart(it.day.id) } },
                enabled = state.activeWorkout != null || currentDay?.items?.isNotEmpty() == true,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    if (state.activeWorkout != null) "Resume workout in progress" else "Start workout",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun NoPlan(hasProgram: Boolean, resuming: Boolean, onSetUp: () -> Unit, onStartEmpty: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            if (hasProgram) {
                "Your program has no days yet. Add your training days to see your week here."
            } else {
                "Set up a program to see your training week here, with each workout ready to start."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSetUp) { Text(if (hasProgram) "Add days" else "Create a program") }
        OutlinedButton(onClick = onStartEmpty) { Text(if (resuming) "Resume workout" else "Start empty workout") }
    }
}
