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

package repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import model.BlockaRepo
import service.EnvironmentService
import service.HttpService
import service.JsonSerializationService
import utils.Logger

object BlockaRepoRepository {

    private val log = Logger("BlockaRepo")
    private val serializer = JsonSerializationService
    private val http = HttpService
    private val env = EnvironmentService

    suspend fun fetch(): BlockaRepo {
        return coroutineScope {
            async(Dispatchers.IO) {
                log.v("Fetching Blocka repo to check for updates and configuration")
                val content = http.makeRequest(env.getRepoUrl())
                serializer.deserialize(
                    content,
                    BlockaRepo::class
                )
            }
        }.await()
    }

}
