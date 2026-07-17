package com.rk.shellix.ui.screens.downloader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.libcommons.toast
import com.rk.shellix.ui.activities.terminal.MainActivity
import com.rk.shellix.ui.screens.terminal.Settings
import com.rk.shellix.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

private val USERNAME_RE = Regex("^[a-z_][a-z0-9_-]*$")

private fun generatePassword(length: Int = 12): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val rng = SecureRandom()
    return (1..length).map { chars[rng.nextInt(chars.length)] }.joinToString("")
}

@Composable
fun SetupWizard(
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current

    var username by remember { mutableStateOf("shellix") }
    var password by remember { mutableStateOf(generatePassword()) }
    var confirm by remember { mutableStateOf(password) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val usernameValid = USERNAME_RE.matches(username)
    val passwordValid = password.length >= 4
    val confirmValid = password == confirm && password.isNotEmpty()
    val formValid = usernameValid && passwordValid && confirmValid && !busy

    if (done) {
        TerminalScreen(mainActivity = mainActivity, navController = navController)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create your user", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            isError = username.isNotEmpty() && !usernameValid,
            supportingText = {
                if (username.isNotEmpty() && !usernameValid) {
                    Text("Lowercase letters, digits, _ or -; must start with a letter or _")
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            isError = password.isNotEmpty() && !passwordValid,
            supportingText = {
                if (password.isNotEmpty() && !passwordValid) Text("At least 4 characters")
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            singleLine = true,
            isError = confirm.isNotEmpty() && !confirmValid,
            supportingText = {
                if (confirm.isNotEmpty() && !confirmValid) Text("Passwords do not match")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Save this password! sudo user: $username / $password",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                busy = true
                error = null
                mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val scriptFile = context.filesDir.child("setup-user.sh")
                        context.assets.open("setup-user.sh").bufferedReader().use {
                            scriptFile.writeText(it.readText())
                        }
                        // Password is written to a private file so MkSession can
                        // forward it to init.sh via SETUP_PASS on first boot.
                        context.filesDir.child("setup-pass.txt").writeText(password)

                        // Store username in Settings. On first boot, MkSession
                        // forwards SETUP_USER/SETUP_PASS to init.sh which runs
                        // setup-user.sh to actually create the user inside proot.
                        Settings.ubuntu_user = username
                        Settings.setup_user_done = false

                        withContext(Dispatchers.Main) {
                            busy = false
                            done = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            busy = false
                            error = e.message
                            toast(e)
                        }
                    }
                }
            },
            enabled = formValid
        ) {
            Text(if (busy) "Setting up..." else "Finish")
        }
    }
}
