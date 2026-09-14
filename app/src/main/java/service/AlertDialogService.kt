/*
 * This file is part of Blokada.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright © 2021 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package service

import android.app.AlertDialog
import android.content.DialogInterface
import android.widget.TextView
import android.graphics.Typeface
import android.widget.Toast
import org.blokada.R
import utils.Logger
import java.util.ArrayDeque

object AlertDialogService {

    private val log = Logger("Dialog")
    private val context = ContextService

    private var displayedDialog: AlertDialog? = null
    private val onAvailable = ArrayDeque<() -> Unit>()

    fun runAfterCurrentDialog(action: () -> Unit) {
        check(displayedDialog != null) { "No dialog is currently displayed" }
        onAvailable.addLast(action)
    }

    fun showAlert(
        message: Int,
        title: Int? = null,
        onDismiss: () -> Unit = {},
        additionalAction: Pair<String, () -> Unit>? = null
    ): AlertShowResult {
        val ctx = context.requireContext()
        return showAlert(
            message = ctx.getString(message),
            title = title?.let { ctx.getString(it) },
            onDismiss = onDismiss,
            additionalAction = additionalAction
        )
    }

    fun showAlert(
        message: String,
        title: String? = null,
        onDismiss: () -> Unit = {},
        additionalAction: Pair<String, () -> Unit>? = null,
        positiveAction: Pair<String, () -> Unit>? = null
    ): AlertShowResult {
        if (displayedDialog != null) {
            log.w("Ignoring new dialog request, one is already being displayed")
            return AlertShowResult.BUSY
        }

        val ctx = context.requireContext()
        val builder = AlertDialog.Builder(ctx)
        var afterDismiss: (() -> Unit)? = null
        builder.setTitle(title ?: ctx.getString(R.string.alert_error_header))
        builder.setMessage(message)

        if (positiveAction == null) {
            builder.setPositiveButton(ctx.getString(R.string.universal_action_close)) { _, _ ->
                dismiss()
            }
        } else {
            builder.setPositiveButton(positiveAction.first) { dialog, _ ->
                afterDismiss = positiveAction.second
                dismiss(dialog)
            }
            builder.setNeutralButton(ctx.getString(R.string.universal_action_close)) { _, _ ->
                dismiss()
            }
        }

        additionalAction?.run {
            builder.setNeutralButton(first) { dialog, _ ->
                afterDismiss = second
                dismiss(dialog)
            }
        }

        builder.setOnDismissListener {
            if (displayedDialog == it) displayedDialog = null
            afterDismiss?.invoke()
            onDismiss()
            while (displayedDialog == null && onAvailable.isNotEmpty()) {
                onAvailable.removeFirst().invoke()
            }
        }

        displayedDialog = builder.showButNotCrash()
        return if (displayedDialog != null) AlertShowResult.SHOWN else AlertShowResult.FAILED
    }

    fun dismiss(dialog: DialogInterface? = displayedDialog) {
        displayedDialog?.let {
            if (it == dialog) {
                it.dismiss()
                displayedDialog = null
            }
        }
    }

    private fun AlertDialog.Builder.showButNotCrash(): AlertDialog? {
        return try { this.show() } catch (ex: Exception) {
            log.e("Could not show dialog, ignoring: ${ex.message}")
            null
        }
    }

}

enum class AlertShowResult {
    SHOWN,
    BUSY,
    FAILED
}
