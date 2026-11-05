package pl.openpayments

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import pl.openpayments.ui.AppRoot
import pl.openpayments.ui.LockScreen
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.theme.OpenPaymentsTheme

class MainActivity : FragmentActivity() {
    private val repo get() = (application as OpenPaymentsApp).repository
    private val vm: MainViewModel by viewModels { MainViewModel.Factory(application, repo) }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Blokada aplikacji: odblokowanie przy starcie i po powrocie z tła (po 60 s). */
    private var locked by mutableStateOf(false)
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = repo.prefs.appLock && canAuthenticate()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val theme by vm.theme.collectAsState()
            val dynamic by vm.dynamicColor.collectAsState()
            OpenPaymentsTheme(theme, dynamic) {
                if (locked) LockScreen(::authenticate) else AppRoot(vm, ::canAuthenticate)
            }
        }
        if (locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = System.currentTimeMillis()
    }

    override fun onRestart() {
        super.onRestart()
        if (repo.prefs.appLock && canAuthenticate() && System.currentTimeMillis() - backgroundAt > 60_000) {
            locked = true; authenticate()
        }
    }

    fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Odblokuj OpenPayments")
                .setSubtitle("Twoje finanse są chronione")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }
}
