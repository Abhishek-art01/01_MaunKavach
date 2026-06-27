package com.maunkavach.navigation

import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maunkavach.network.AuthSession
import com.maunkavach.ui.screens.*

object Routes {
    const val LOGIN = "login"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{contactId}"
    const val SETTINGS = "settings"
    const val VAULT_KEY = "vault_key"
    const val CONTACT_KEY_MANAGEMENT = "contact_key_mgmt/{contactId}"
    const val QR_SHARING = "qr_sharing/{contactId}"
    const val SECURITY_SETTINGS = "security_settings"
    const val SECURITY_DASHBOARD = "security_dashboard"

    fun chat(contactId: String) = "chat/$contactId"
    fun contactKeyMgmt(contactId: String) = "contact_key_mgmt/$contactId"
    fun qrSharing(contactId: String) = "qr_sharing/$contactId"
}

@Composable
fun MaunKavachNavHost(activity: FragmentActivity) {
    val navController: NavHostController = rememberNavController()
    var session by remember { mutableStateOf<AuthSession?>(null) }
    val setSession: (AuthSession?) -> Unit = { session = it }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                setSession(it)
                navController.navigate(Routes.CHAT_LIST) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.CHAT_LIST) {
            val activeSession = session
            if (activeSession == null) {
                LoginScreen(onLoggedIn = {
                    setSession(it)
                    navController.navigate(Routes.CHAT_LIST) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            } else {
                ChatListScreen(
                    onOpenChat = { contactId -> navController.navigate(Routes.chat(contactId)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenContactKeyMgmt = { contactId -> navController.navigate(Routes.contactKeyMgmt(contactId)) },
                    username = activeSession.username
                )
            }
        }

        composable(Routes.CHAT) { backStackEntry ->
            val activeSession = session
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            if (activeSession == null) {
                LoginScreen(onLoggedIn = {
                    setSession(it)
                    navController.navigate(Routes.CHAT_LIST) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            } else {
                ChatScreen(
                    contactId = contactId,
                    session = activeSession,
                    onOpenContactKeyMgmt = { navController.navigate(Routes.contactKeyMgmt(contactId)) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenVaultKey = { navController.navigate(Routes.VAULT_KEY) },
                onOpenSecuritySettings = { navController.navigate(Routes.SECURITY_SETTINGS) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VAULT_KEY) {
            VaultKeyScreen(
                activity = activity,
                onOpenContactKeyMgmt = { contactId -> navController.navigate(Routes.contactKeyMgmt(contactId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CONTACT_KEY_MANAGEMENT) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            ContactKeyManagementScreen(
                contactId = contactId,
                onOpenQr = { navController.navigate(Routes.qrSharing(contactId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.QR_SHARING) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            QrKeySharingScreen(contactId = contactId, onBack = { navController.popBackStack() })
        }

        composable(Routes.SECURITY_SETTINGS) {
            SecuritySettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDashboard = { navController.navigate(Routes.SECURITY_DASHBOARD) }
            )
        }

        composable(Routes.SECURITY_DASHBOARD) {
            SecurityDashboardScreen(onBack = { navController.popBackStack() })
        }
    }
}
