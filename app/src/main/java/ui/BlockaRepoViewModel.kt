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

package ui

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import model.BlockaRepo
import model.BlockaRepoConfig
import repository.BlockaRepoRepository
import service.ContextService
import service.EnvironmentService
import service.PersistenceService
import ui.utils.cause
import ui.utils.now
import utils.Logger

class BlockaRepoViewModel: ViewModel() {

    private val log = Logger("BlockaRepo")
    private val repository = BlockaRepoRepository
    private val env = EnvironmentService
    private val persistence = PersistenceService

    private val _repoConfig = MutableLiveData<BlockaRepoConfig>()
    val repoConfig: LiveData<BlockaRepoConfig> = _repoConfig.distinctUntilChanged()

    fun maybeRefreshRepo() {
        viewModelScope.launch {
            try {
                val config = persistence.load(BlockaRepoConfig::class)
                if (now() > config.lastRefresh + REPO_REFRESH_MILLIS) {
                    log.w("Repo config is stale, refreshing")
                    refreshRepo()
                } else {
                    _repoConfig.value = config
                }
            } catch (ex: Exception) {
                log.w("Could not load repo config, ignoring".cause(ex))
            }
        }
    }

    fun refreshRepo() {
        viewModelScope.launch {
            try {
                val config = processConfig(repository.fetch()).copy(
                    lastRefresh = now()
                )
                _repoConfig.value = config
                persistence.save(config)
                UpdateCheckJob.schedule()
            } catch (ex: Exception) {
                log.w("Could not refresh repo, ignoring".cause(ex))
            }
        }
    }

    private fun applyConfig(repo: BlockaRepo): BlockaRepoConfig {
        val config = processConfig(repo)
        log.v("cfg: $config")
        return config
    }

    private fun processConfig(repo: BlockaRepo): BlockaRepoConfig {
        log.v("Processing config")
        val common = repo.common
        val mine = repo.buildConfigs.firstOrNull { it.forBuild == env.getBuildName() }
        return if (mine == null) {
            log.w("No build config matched, using only common config")
            common
        } else {
            log.v("Using config: ${mine.name}")
            return mine.combine(common)
        }
    }

}

private const val REPO_REFRESH_MILLIS = 12 * 60 * 60 * 1000

class UpdateCheckJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Logger.w("UpdateCheck", "Received update check job")
        val blockaRepoVM = ViewModelProvider(app()).get(BlockaRepoViewModel::class.java)
        blockaRepoVM.maybeRefreshRepo()
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?) = true

    companion object {
        fun schedule() {
            try {
                val ctx = ContextService.requireContext()
                val scheduler = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val serviceComponent = ComponentName(ctx, UpdateCheckJob::class.java)
                val builder = JobInfo.Builder(69, serviceComponent)
                builder.setPeriodic(REPO_REFRESH_MILLIS + 5000L) // Add delay to make sure we don't do it just too early
                scheduler.schedule(builder.build())
                Logger.v("UpdateCheck", "Scheduled update check job")
            } catch (ex: Exception) {
                Logger.e("UpdateCheck", "Could not schedule update check job".cause(ex))
            }
        }
    }
}
