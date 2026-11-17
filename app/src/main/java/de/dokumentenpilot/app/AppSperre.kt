package de.dokumentenpilot.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.hardware.biometrics.BiometricPrompt

object AppSperre {

    fun entsperren(
        activity: Activity,
        beiErfolg: () -> Unit
    ) {
        val keyguardManager =
            activity.getSystemService(
                Context.KEYGUARD_SERVICE
            ) as KeyguardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("DokumentenPilot entsperren")
                .setSubtitle(
                    "Fingerabdruck oder Geräte-PIN verwenden"
                )
                .setAllowedAuthenticators(
                    android.hardware.biometrics
                        .BiometricManager.Authenticators
                        .BIOMETRIC_STRONG or
                    android.hardware.biometrics
                        .BiometricManager.Authenticators
                        .DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        beiErfolg()
                    }
                }
            )

        } else {
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    "DokumentenPilot entsperren",
                    "Bitte Geräte-PIN bestätigen"
                )

            if (intent != null) {
                activity.startActivityForResult(intent, 9001)
            }
        }
    }
}
