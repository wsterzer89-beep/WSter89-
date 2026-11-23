package de.dokumentenpilot.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

object AppSperre {

    const val PIN_ANFRAGE = 9001

    fun entsperren(
        activity: Activity,
        beiErfolg: () -> Unit,
        beiAbbruch: () -> Unit
    ) {
        val keyguard = activity.getSystemService(
            Context.KEYGUARD_SERVICE
        ) as KeyguardManager

        if (!keyguard.isDeviceSecure) {
            beiAbbruch()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authentifizierer =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(activity.getString(R.string.ui_text_071))
                .setSubtitle(
                    activity.getString(R.string.ui_text_072)
                )
                .setAllowedAuthenticators(authentifizierer)
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

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        beiAbbruch()
                    }
                }
            )
        } else {
            val intent: Intent? =
                keyguard.createConfirmDeviceCredentialIntent(
                    activity.getString(R.string.ui_text_071),
                    activity.getString(R.string.ui_text_073)
                )

            if (intent != null) {
                activity.startActivityForResult(
                    intent,
                    PIN_ANFRAGE
                )
            } else {
                beiAbbruch()
            }
        }
    }
}
