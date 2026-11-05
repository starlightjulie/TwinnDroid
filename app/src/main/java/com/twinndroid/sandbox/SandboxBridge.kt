package com.twinndroid.sandbox

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import com.twinndroid.R
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SandboxException(message: String) : Exception(message)

/**
 * Talks to the other copy of TwinnDroid across the profile boundary. Apps can't call into another
 * profile directly, but Android forwards intents that the profile owner has allowed, so each command
 * is one short-lived invisible activity on the far side with a [ResultReceiver] to answer on.
 */
object SandboxBridge {
    // One action per direction: if a single action could cross both ways, it would match on both
    // sides of the boundary at once and Android would ask the user which profile they meant.
    const val ACTION_INTO_SANDBOX = "com.twinndroid.sandbox.action.INTO_SANDBOX"
    const val ACTION_OUT_OF_SANDBOX = "com.twinndroid.sandbox.action.OUT_OF_SANDBOX"

    const val EXTRA_COMMAND = "com.twinndroid.sandbox.COMMAND"
    const val EXTRA_REPLY = "com.twinndroid.sandbox.REPLY"

    const val REPLY_DONE = 0
    const val REPLY_FAILED = 1
    /** A batch of a long answer. Bundles over ~1 MB can't cross Binder, so lists arrive in pieces. */
    const val REPLY_PART = 2

    private const val DEFAULT_TIMEOUT = 20_000L

    private fun action(context: Context) =
        if (SandboxPolicy.isOwner(context)) ACTION_OUT_OF_SANDBOX else ACTION_INTO_SANDBOX

    /** Android's forwarding activity into the other profile, or null if there's no bridge to cross. */
    fun forwarder(context: Context): ComponentName? {
        val query = Intent(action(context)).addCategory(Intent.CATEGORY_DEFAULT)
        return context.packageManager.queryIntentActivities(query, 0)
            .firstOrNull { it.activityInfo.packageName != context.packageName }
            ?.activityInfo?.let { ComponentName(it.packageName, it.name) }
    }

    /**
     * Runs [command] on the far side. [uris] are readable there until the command finishes.
     * Pass null for [timeoutMs] when the far side has to wait for the user, like an install prompt.
     */
    suspend fun call(
        context: Context,
        command: String,
        args: Bundle = Bundle.EMPTY,
        uris: List<Uri> = emptyList(),
        timeoutMs: Long? = DEFAULT_TIMEOUT,
        onPart: (Bundle) -> Unit = {},
    ): Bundle {
        val target = forwarder(context) ?: throw SandboxException("The sandbox isn't available")
        val send: suspend () -> Bundle = {
            suspendCancellableCoroutine { cont ->
                val reply = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(code: Int, data: Bundle?) {
                        when (code) {
                            REPLY_PART -> data?.let(onPart)
                            REPLY_DONE -> if (cont.isActive) cont.resume(data ?: Bundle.EMPTY)
                            else -> if (cont.isActive) {
                                cont.resumeWithException(SandboxException(data?.getString("error") ?: "The sandbox didn't answer"))
                            }
                        }
                    }
                }
                val intent = Intent(action(context))
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtras(args)
                    .putExtra(EXTRA_COMMAND, command)
                    .putExtra(EXTRA_REPLY, plain(reply))
                if (uris.isNotEmpty()) {
                    val clip = ClipData.newRawUri("apks", uris.first())
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    intent.clipData = clip
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
        if (timeoutMs == null) return send()
        return try {
            withTimeout(timeoutMs) { send() }
        } catch (_: TimeoutCancellationException) {
            throw SandboxException(context.getString(R.string.sandbox_no_answer))
        }
    }

    /**
     * Strips our subclass off a [ResultReceiver], so the system process that forwards the intent
     * doesn't try to load a class that only exists in this app.
     */
    private fun plain(receiver: ResultReceiver): ResultReceiver {
        val parcel = Parcel.obtain()
        try {
            receiver.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return ResultReceiver.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
