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

package model

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class BlockaRepo(
    val syntaxVersion: Int,
    val common: BlockaRepoConfig,
    val buildConfigs: List<BlockaRepoConfig>
)

@JsonClass(generateAdapter = true)
data class BlockaRepoConfig(
    val name: String,
    val forBuild: String,
    val supportedLanguages: List<String>? = null,
    val update: BlockaRepoUpdate? = null,
    val message: BlockaRepoMessage? = null,
    val payload: BlockaRepoPayload? = null,
    val lastRefresh: Long = 0L
) {

    fun supportedLanguages() = supportedLanguages ?: emptyList()

    fun combine(common: BlockaRepoConfig) = copy(
        supportedLanguages = supportedLanguages ?: common.supportedLanguages,
        update = update ?: common.update,
        message = message ?: common.message
    )

}

@JsonClass(generateAdapter = true)
data class BlockaRepoUpdate(
    val mirrors: List<Uri>,
    val infoUrl: Uri,
    val newest: String
)

@JsonClass(generateAdapter = true)
data class BlockaRepoMessage(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val url: Uri? = null,
    val expires: String? = null // yyyy-MM-dd, device local; unparseable counts as expired
)

@JsonClass(generateAdapter = true)
data class BlockaRepoMessageNotification(
    val messageId: String = ""
)

@JsonClass(generateAdapter = true)
data class BlockaAfterUpdate(
    val dialogShownForVersion: Int? = null
)

@JsonClass(generateAdapter = true)
data class BlockaRepoPayload(
    val cmd: String,
    val version: Int? = null
)
