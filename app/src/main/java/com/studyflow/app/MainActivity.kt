package com.studyflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.studyflow.app.ui.*
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.StudyViewModel
import com.studyflow.app.viewmodel.TimerViewModel

class MainActivity : ComponentActivity() {
    private val timerViewModel: TimerViewModel by viewModels()
    private val studyViewModel: StudyViewModel  by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyFlowTheme {
                RequestPermissions()
                StudyFlowApp(timerViewModel, studyViewModel)
            }
        }
    }
}

@Composable
private fun RequestPermissions() {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

@Composable
fun StudyFlowApp(timerViewModel: TimerViewModel, studyViewModel: StudyViewModel) {
    val navController  = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    val topLevel       = setOf("timer", "study", "weekly")

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (currentRoute in topLevel) {
                BottomNav(currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = "study",
            modifier         = Modifier.padding(padding),
        ) {
            composable("timer")  { TimerScreen(timerViewModel) }
            composable("study")  {
                StudyScreen(
                    viewModel          = studyViewModel,
                    onHistoryClick     = { navController.navigate("history") },
                    onDailyBoardClick  = { navController.navigate("dailyboard") },
                )
            }
            composable("weekly") { WeeklyScreen(studyViewModel) }
            composable("history") {
                Column(Modifier.fillMaxSize().background(Background)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Background)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("←", color = Primary, fontSize = 22.sp,
                            modifier = Modifier.clickable { navController.popBackStack() })
                        Text("History", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    HistoryScreen(studyViewModel)
                }
            }
            composable("dailyboard") {
                Column(Modifier.fillMaxSize().background(Background)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Background)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("←", color = Primary, fontSize = 22.sp,
                            modifier = Modifier.clickable { navController.popBackStack() })
                        Text("📋  Daily Board", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DailyBoardScreen(
                        viewModel = studyViewModel,
                        onStartStudySession = { item ->
                            studyViewModel.startStudySessionForItem(item)
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNav(currentRoute: String?, onNav: (String) -> Unit) {
    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = currentRoute == "timer",
            onClick  = { onNav("timer") },
            icon     = { Text("⏳", fontSize = 20.sp) },
            label    = { Text("Timer") },
            colors   = navColors(),
        )
        NavigationBarItem(
            selected = currentRoute == "study" || currentRoute == null,
            onClick  = { onNav("study") },
            icon     = { Text("📚", fontSize = 20.sp) },
            label    = { Text("Study") },
            colors   = navColors(),
        )
        NavigationBarItem(
            selected = currentRoute == "weekly",
            onClick  = { onNav("weekly") },
            icon     = { Text("📈", fontSize = 20.sp) },
            label    = { Text("Stats") },
            colors   = navColors(),
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = Primary,
    selectedTextColor   = Primary,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor      = Primary.copy(alpha = 0.15f),
)
