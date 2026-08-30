package com.keavors.gallery.data

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Whether this phone can be asked to prove who is holding it.
 *
 * False on a device with no screen lock at all, where there is nothing to ask —
 * and where offering to lock the gallery would be a promise the phone cannot
 * keep.
 */
fun Activity.canAuthenticate(): Boolean {
    val keyguard = getSystemService<KeyguardManager>() ?: return false
    if (keyguard.isDeviceSecure) return true

    val biometrics = getSystemService<BiometricManager>() ?: return false
    return biometrics.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

/**
 * Asks the phone to confirm who is holding it.
 *
 * The device's own credential rather than a passcode of this app's own. A PIN
 * kept here would have to be stored as a hash that can be attacked offline at
 * whatever speed the attacker likes; the system credential is rate-limited by
 * the operating system, backed by hardware, and leaves nothing here to steal.
 * A fingerprint is accepted where one is enrolled, the screen lock where it is
 * not — which is exactly "a PIN or a fingerprint", only somebody else's to keep.
 */
fun Activity.authenticate(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onCancelled: () -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(this)
    val prompt = BiometricPrompt.Builder(this)
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .setConfirmationRequired(false)
        .build()

    prompt.authenticate(
        CancellationSignal(),
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                // Errors and cancellation are the same outcome here: the door
                // stays shut. Telling the two apart would only be useful for
                // explaining why, and the prompt has already explained.
                onCancelled()
            }
        },
    )
}
