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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.*
import org.blokada.R
import ui.Command
import ui.executeCommand
import ui.utils.cause
import ui.utils.openInBrowser
import utils.Logger
import utils.NotificationChannels
import utils.RepoMessageNotification
import utils.UpdateNotification
import java.net.URI
import java.time.LocalDate

object UpdateService {

    private val log = Logger("Update")
    private val alert = AlertDialogService
    private val env = EnvironmentService
    private val notification = NotificationService
    private val context = ContextService
    private val persistence = PersistenceService
    private val scope = GlobalScope

    private var updateInfo: BlockaRepoUpdate? = null
    private var messageInfo: BlockaRepoMessage? = null
    private var libreModeForUpdate = false
    private var updateDownloadInProgress = false
    private var messageDeferredForExternalAction = false

    fun checkForUpdate(config: BlockaRepoConfig): Boolean {
        this.updateInfo = null
        if (config.update == null) return false
        val updateInfo = config.update
        val versionCode = versionToVersionCode(updateInfo.newest)
        log.v("Repo newest version code is: $versionCode")
        return if (versionCode > env.getVersionCode()) {
            if (hasUserSeenThisUpdate(updateInfo)) {
                log.w("Ignoring this update, user has already seen it")
                false
            } else {
                log.w("New version is available")
                this.updateInfo = updateInfo
                true
            }
        } else false
    }

    fun checkForMessage(config: BlockaRepoConfig) {
        val message = config.message
        when (repoMessageEligibility(
            message = message,
            seenId = persistence.load(BlockaRepoMessage::class).id,
            today = LocalDate.now()
        )) {
            RepoMessageEligibility.ELIGIBLE -> {
                log.v("Repo message pending: ${message!!.id}")
                messageInfo = message
            }
            RepoMessageEligibility.INVALID_EXPIRY -> {
                log.w("Could not parse message expiry: ${message?.expires}")
                messageInfo = null
                cancelRepoMessageNotification()
            }
            else -> {
                messageInfo = null
                cancelRepoMessageNotification()
            }
        }
    }

    fun handleUpdateFlow(
        onOpenDonate: () -> Unit,
        onOpenMore: () -> Unit,
        onOpenChangelog: () -> Unit,
        libreMode: Boolean
    ) {
        libreModeForUpdate = libreMode
        val appVersion = EnvironmentService.getVersionCode()
        if (!hasUserSeenAfterUpdateDialog(appVersion)) {
            val shown = showThankYouAlert(onOpenDonate, onOpenMore, onOpenChangelog) {
                showUpdateAlertIfNecessary()
            }
            when (shown) {
                AlertShowResult.SHOWN -> markUserSeenAfterUpdateDialog(appVersion)
                AlertShowResult.BUSY -> alert.runAfterCurrentDialog {
                    handleUpdateFlow(onOpenDonate, onOpenMore, onOpenChangelog, libreMode)
                }
                AlertShowResult.FAILED -> Unit
            }
        } else {
            // This is in else branch to make sure only one dialog can show at once
            showUpdateAlertIfNecessary()
        }
    }

    fun showUpdateNotificationIfNecessary() {
        updateInfo?.let {
            notification.show(UpdateNotification(it.newest))
        }
    }

    fun showBackgroundNotificationsIfNecessary() {
        showUpdateNotificationIfNecessary()
        showRepoMessageNotificationIfNecessary()
    }

    @Synchronized
    private fun showRepoMessageNotificationIfNecessary() {
        val msg = messageInfo ?: return
        val seenId = persistence.load(BlockaRepoMessage::class).id
        val notifiedId = persistence.load(BlockaRepoMessageNotification::class).messageId
        if (!shouldNotifyRepoMessage(msg, seenId, notifiedId, LocalDate.now())) return
        if (!notification.hasPermissions(NotificationChannels.ANNOUNCEMENT)) {
            log.w("Announcement notifications are disabled")
            return
        }

        try {
            notification.show(RepoMessageNotification(msg))
            persistence.save(BlockaRepoMessageNotification(msg.id))
        } catch (ex: Exception) {
            log.e("Could not show repo message notification".cause(ex))
        }
    }

    fun showUpdateAlertIfNecessary(libreMode: Boolean? = null) {
        libreMode?.let { libreModeForUpdate = it }
        if (!hasUserSeenAfterUpdateDialog(env.getVersionCode())) return
        updateInfo?.let {
            val ctx = context.requireContext()
            alert.showAlert(
                message = ctx.getString(R.string.alert_update_body, "5"), // Blokada 5
                title = ctx.getString(R.string.notification_update_header),
                positiveAction = ctx.getString(R.string.universal_action_download) to {
                    updateDownloadInProgress = true
                    showUpdatingAlert(it.infoUrl)
                    scope.launch {
                        if (libreModeForUpdate) deactivateBeforeDownload()
                        UpdateDownloaderService.installUpdate(it.mirrors) { succeeded ->
                            scope.launch(Dispatchers.Main) {
                                updateDownloadInProgress = false
                                messageDeferredForExternalAction =
                                    messageDeferredForExternalAction || succeeded
                                alert.dismiss()
                                if (!succeeded && !messageDeferredForExternalAction) {
                                    showPendingMessageIfNecessary()
                                }
                            }
                        }
                    }
                },
                additionalAction = ctx.getString(R.string.universal_action_hide) to {
                    markUpdateAsSeen(it)
                },
                onDismiss = {
                    notification.cancel(UpdateNotification(it.newest))
                    updateInfo = null
                    if (!updateDownloadInProgress) showPendingMessageIfNecessary()
                }
            ).also { result ->
                if (result == AlertShowResult.BUSY) {
                    alert.runAfterCurrentDialog { showUpdateAlertIfNecessary() }
                }
            }
        }
        if (updateInfo == null && hasUserSeenAfterUpdateDialog(env.getVersionCode())) {
            showPendingMessageIfNecessary()
        }
    }

    private fun showPendingMessageIfNecessary(): Boolean {
        if (messageDeferredForExternalAction) return false
        val msg = messageInfo ?: return false
        if (repoMessageEligibility(
                message = msg,
                seenId = persistence.load(BlockaRepoMessage::class).id,
                today = LocalDate.now()
            ) != RepoMessageEligibility.ELIGIBLE
        ) {
            messageInfo = null
            return false
        }
        val ctx = context.requireContext()
        val url = validRepoMessageUrl(msg.url)
        val shown = alert.showAlert(
            message = msg.body,
            title = msg.title,
            positiveAction = if (url != null) {
                ctx.getString(R.string.universal_action_learn_more) to {
                    openInBrowser(url)
                }
            } else null
        )
        when (shown) {
            AlertShowResult.SHOWN -> {
                persistence.save(msg)
                cancelRepoMessageNotification()
                if (messageInfo == msg) messageInfo = null
            }
            AlertShowResult.BUSY -> alert.runAfterCurrentDialog { showPendingMessageIfNecessary() }
            AlertShowResult.FAILED -> Unit
        }
        return shown == AlertShowResult.SHOWN
    }

    fun onAppResumed() {
        messageDeferredForExternalAction = false
        if (!hasUserSeenAfterUpdateDialog(env.getVersionCode())) return
        if (updateInfo != null) {
            showUpdateAlertIfNecessary()
        } else {
            showPendingMessageIfNecessary()
        }
    }

    private suspend fun deactivateBeforeDownload() {
        log.v("Deactivating before downloading the update")
        executeCommand(Command.OFF)
        delay(2000)
    }

    private fun showUpdatingAlert(url: Uri) {
        val ctx = context.requireContext()
        alert.showAlert(
            message = ctx.getString(R.string.update_downloading_description),
            title = ctx.getString(R.string.universal_status_processing),
            positiveAction = ctx.getString(R.string.universal_action_cancel) to {
                UpdateDownloaderService.cancelUpdate()
            },
            additionalAction = ctx.getString(R.string.universal_action_open_in_browser) to {
                UpdateDownloaderService.cancelUpdate()
                messageDeferredForExternalAction = true
                updateDownloadInProgress = false
                openInBrowser(url)
            },
            onDismiss = {
                UpdateDownloaderService.cancelUpdate()
                if (updateDownloadInProgress) {
                    updateDownloadInProgress = false
                    showPendingMessageIfNecessary()
                }
            }
        )
    }

    private fun showThankYouAlert(
        onOpenDonate: () -> Unit,
        onOpenMore: () -> Unit,
        onOpenChangelog: () -> Unit,
        onDismiss: () -> Unit
    ): AlertShowResult {
        val ctx = context.requireContext()
        val showDonate = EnvironmentService.isLibre() && !EnvironmentService.isSlim()
        return alert.showAlert(
            message = ctx.getString(
                if (showDonate) R.string.update_desc_updated else R.string.update_desc_updated_nodon
            ),
            title = ctx.getString(R.string.update_label_updated),
            positiveAction =
                if (showDonate) ctx.getString(R.string.universal_action_donate) to onOpenDonate
                else ctx.getString(R.string.universal_action_close) to {},
            additionalAction =
                if (showDonate) ctx.getString(R.string.universal_action_learn_more) to onOpenMore
                else ctx.getString(R.string.universal_action_learn_more) to onOpenChangelog,
            onDismiss = onDismiss
        )
    }

    private fun hasUserSeenThisUpdate(update: BlockaRepoUpdate): Boolean {
        val seen = persistence.load(BlockaRepoUpdate::class)
        return seen.newest == update.newest
    }

    private fun markUpdateAsSeen(update: BlockaRepoUpdate) {
        log.v("Marking update ${update.newest} as seen")
        persistence.save(update)
    }

    fun resetSeenUpdate() {
        log.v("Resetting seen update and message marks")
        persistence.save(Defaults.noSeenUpdate())
        persistence.save(Defaults.noSeenMessage())
        persistence.save(Defaults.noNotifiedMessage())
        cancelRepoMessageNotification()
    }

    private fun cancelRepoMessageNotification() {
        notification.cancel(RepoMessageNotification(Defaults.noSeenMessage()))
    }

    private fun hasUserSeenAfterUpdateDialog(appVersion: Int): Boolean {
        val seen = persistence.load(BlockaAfterUpdate::class)
        if (seen.dialogShownForVersion == null) {
            // Null is used to not show the dialog right on first install
            markUserSeenAfterUpdateDialog(appVersion)
            return true
        }
        return seen.dialogShownForVersion  == appVersion
    }

    private fun markUserSeenAfterUpdateDialog(appVersion: Int) {
        log.v("Marking user seen after update dialog for version: $appVersion")
        persistence.save(BlockaAfterUpdate(dialogShownForVersion = appVersion))
    }

    private fun versionToVersionCode(version: String): Int {
        val parts = version
            .replaceAfter("-", "")
            .replaceAfter("_", "")
            .split(".")

        return try {
            when(parts.size) {
                3 -> {
                    val major = parts[0].toInt()
                    val minor = parts[1].toInt()
                    val patch = parts[2].toInt()
                    "%d%02d%06d".format(major, minor, patch).toInt()
                }
                2 -> {
                    val major = parts[0].toInt()
                    val minor = parts[1].toInt()
                    "%d%02d000000".format(major, minor).toInt()
                }
                1 -> parts[0].toInt()
                else -> throw BlokadaException("Unknown version format")
            }
        } catch (ex: Exception) {
            log.w("Could not parse version: $version".cause(ex))
            0
        }
    }

}

internal enum class RepoMessageEligibility {
    ELIGIBLE,
    MISSING_FIELDS,
    ALREADY_SEEN,
    EXPIRED,
    INVALID_EXPIRY
}

internal fun repoMessageEligibility(
    message: BlockaRepoMessage?,
    seenId: String,
    today: LocalDate
): RepoMessageEligibility {
    if (message == null || message.id.isBlank() || message.title.isBlank() || message.body.isBlank()) {
        return RepoMessageEligibility.MISSING_FIELDS
    }
    if (message.id == seenId) return RepoMessageEligibility.ALREADY_SEEN
    val expires = message.expires ?: return RepoMessageEligibility.ELIGIBLE
    val expiry = try {
        LocalDate.parse(expires)
    } catch (_: Exception) {
        return RepoMessageEligibility.INVALID_EXPIRY
    }
    return if (expiry.isBefore(today)) RepoMessageEligibility.EXPIRED
    else RepoMessageEligibility.ELIGIBLE
}

internal fun validRepoMessageUrl(url: Uri?): Uri? {
    if (url == null) return null
    return try {
        val parsed = URI(url)
        if (parsed.scheme.equals("https", ignoreCase = true) &&
            !parsed.host.isNullOrBlank() && parsed.userInfo == null
        ) url else null
    } catch (_: Exception) {
        null
    }
}

internal fun shouldNotifyRepoMessage(
    message: BlockaRepoMessage?,
    seenId: String,
    notifiedId: String,
    today: LocalDate
): Boolean {
    return repoMessageEligibility(message, seenId, today) == RepoMessageEligibility.ELIGIBLE &&
        message!!.id != notifiedId
}
