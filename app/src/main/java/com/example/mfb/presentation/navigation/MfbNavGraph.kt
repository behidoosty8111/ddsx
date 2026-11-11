package com.example.mfb.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mfb.MfbApplication
import com.example.mfb.navigation.MfbScreen
import com.example.mfb.navigation.bottomNavItems
import com.example.mfb.presentation.about.AboutScreen
import com.example.mfb.presentation.build.AssemblerViewModel
import com.example.mfb.presentation.build.BuildPipelineScreen
import com.example.mfb.presentation.changelog.UpdateLogScreen
import com.example.mfb.presentation.dashboard.DashboardScreen
import com.example.mfb.presentation.editor.CodeEditorScreen
import com.example.mfb.presentation.editor.CodeEditorViewModel
import com.example.mfb.presentation.import_screen.ImportHtmlPlaceholderScreen
import com.example.mfb.presentation.import_screen.ImportScreen
import com.example.mfb.presentation.import_screen.ImportZipPlaceholderScreen
import com.example.mfb.presentation.preview.LivePreviewScreen
import com.example.mfb.presentation.preview.LivePreviewViewModel
import com.example.mfb.presentation.projects.ProjectDetailsScreen
import com.example.mfb.presentation.projects.ProjectsScreen
import com.example.mfb.presentation.settings.SettingsScreen
import com.example.mfb.presentation.splash.SplashScreen
import com.example.mfb.presentation.templates.TemplatesScreen
import com.example.mfb.presentation.viewmodel.MfbViewModel

@Composable
fun MfbAppRoot(
    viewModel: MfbViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isBottomBarVisible = currentRoute in listOf(
        MfbScreen.Dashboard.route,
        MfbScreen.Projects.route,
        MfbScreen.Templates.route,
        MfbScreen.Settings.route
    )

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_navigation_bar")
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_${item.title.lowercase()}")
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MfbScreen.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Splash Screen
            composable(MfbScreen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(MfbScreen.Dashboard.route) {
                            popUpTo(MfbScreen.Splash.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            // 2. Dashboard Screen
            composable(MfbScreen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToImport = {
                        navController.navigate(MfbScreen.Import.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToProjects = {
                        navController.navigate(MfbScreen.Projects.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTemplates = {
                        navController.navigate(MfbScreen.Templates.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(MfbScreen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProjectDetails = { projectId ->
                        navController.navigate(MfbScreen.ProjectDetails.createRoute(projectId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToUpdateLog = {
                        navController.navigate(MfbScreen.UpdateLog.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 3. Projects Screen
            composable(MfbScreen.Projects.route) {
                ProjectsScreen(
                    viewModel = viewModel,
                    onNavigateToImport = {
                        navController.navigate(MfbScreen.Import.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToProjectDetails = { projectId ->
                        navController.navigate(MfbScreen.ProjectDetails.createRoute(projectId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 4. Templates Screen
            composable(MfbScreen.Templates.route) {
                TemplatesScreen()
            }

            // 5. Settings Screen
            composable(MfbScreen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAbout = {
                        navController.navigate(MfbScreen.About.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToUpdateLog = {
                        navController.navigate(MfbScreen.UpdateLog.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 6. Import Screen (Primary selection between Import ZIP and Import HTML)
            composable(MfbScreen.Import.route) {
                ImportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToImportZip = {
                        navController.navigate(MfbScreen.ImportZip.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToImportHtml = {
                        navController.navigate(MfbScreen.ImportHtml.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 7. Import ZIP Screen
            composable(MfbScreen.ImportZip.route) {
                BackHandler {
                    navController.popBackStack()
                }
                ImportZipPlaceholderScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    viewModel = viewModel,
                    onNavigateToProjects = {
                        navController.navigate(MfbScreen.Projects.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToProjectDetails = { projectId ->
                        navController.navigate(MfbScreen.ProjectDetails.createRoute(projectId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 8. Import HTML Screen
            composable(MfbScreen.ImportHtml.route) {
                BackHandler {
                    navController.popBackStack()
                }
                ImportHtmlPlaceholderScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    viewModel = viewModel,
                    onNavigateToProjects = {
                        navController.navigate(MfbScreen.Projects.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToProjectDetails = { projectId ->
                        navController.navigate(MfbScreen.ProjectDetails.createRoute(projectId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 9. Project Details Screen
            composable(
                route = MfbScreen.ProjectDetails.route,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                ProjectDetailsScreen(
                    projectId = projectId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenFileInEditor = { filePath ->
                        navController.navigate(MfbScreen.CodeEditor.createRoute(projectId, filePath)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenLivePreview = { entryPath ->
                        navController.navigate(MfbScreen.LivePreview.createRoute(projectId, entryPath)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenBuildPipeline = {
                        navController.navigate(MfbScreen.BuildPipeline.createRoute(projectId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 9b. Code Editor Screen (Phase 04)
            composable(
                route = MfbScreen.CodeEditor.route,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("filePath") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                val filePath = backStackEntry.arguments?.getString("filePath")
                val appContainer = (LocalContext.current.applicationContext as MfbApplication).container
                val editorViewModel: CodeEditorViewModel = viewModel(
                    key = "code_editor_$projectId",
                    factory = CodeEditorViewModel.provideFactory(appContainer, projectId, filePath)
                )
                CodeEditorScreen(
                    viewModel = editorViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenLivePreview = { entryPath ->
                        navController.navigate(MfbScreen.LivePreview.createRoute(projectId, entryPath)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 9c. Live Preview Screen (Phase 05)
            composable(
                route = MfbScreen.LivePreview.route,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("entryPath") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                val entryPath = backStackEntry.arguments?.getString("entryPath")
                val appContainer = (LocalContext.current.applicationContext as MfbApplication).container
                val previewViewModel: LivePreviewViewModel = viewModel(
                    key = "live_preview_${projectId}_${entryPath.orEmpty()}",
                    factory = LivePreviewViewModel.provideFactory(appContainer, projectId, entryPath)
                )
                LivePreviewScreen(
                    viewModel = previewViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onReturnToFileBrowser = {
                        navController.popBackStack()
                    }
                )
            }

            // 9d. Real APK Build Pipeline Screen
            composable(
                route = MfbScreen.BuildPipeline.route,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                val appContainer = (LocalContext.current.applicationContext as MfbApplication).container
                val assemblerViewModel: AssemblerViewModel = viewModel(
                    key = "build_pipeline_$projectId",
                    factory = AssemblerViewModel.provideFactory(appContainer, projectId)
                )
                BuildPipelineScreen(
                    viewModel = assemblerViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToSettings = {
                        navController.navigate(MfbScreen.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 10. About Screen
            composable(MfbScreen.About.route) {
                BackHandler {
                    navController.popBackStack()
                }
                AboutScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToUpdateLog = {
                        navController.navigate(MfbScreen.UpdateLog.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 11. Update Log Screen
            composable(MfbScreen.UpdateLog.route) {
                UpdateLogScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
