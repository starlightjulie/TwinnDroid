package com.twinndroid.sandbox

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Makes TwinnDroid eligible to own a work profile. Android delivers this inside the new profile. */
class SandboxAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        SandboxPolicy.enforce(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // A new version may need different rules, and the copy in the sandbox updates with the main one.
            if (SandboxPolicy.isOwner(context)) SandboxPolicy.enforce(context)
            return
        }
        super.onReceive(context, intent)
    }
}

/** Android opens this inside the profile once setup finishes; the profile stays off until we enable it. */
class SandboxFinalizeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SandboxPolicy.enforce(this)
        setResult(RESULT_OK)
        finish()
    }
}
