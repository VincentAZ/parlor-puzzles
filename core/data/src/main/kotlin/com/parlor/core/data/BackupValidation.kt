package com.parlor.core.data

import com.parlor.core.model.*
import com.parlor.core.progress.BackupRules

/** Validation has no dependency on the database that the file may be needed to recover. */
internal object BackupValidation {
    fun validate(file: ProgressEnvelope, catalog: Catalog, schedule: DailySchedule) {
        BackupRules.validate(file, catalog, schedule)
        file.attempts.forEach {
            require(ParlorJson.encodeToString(it).toByteArray(Charsets.UTF_8).size <= 1_048_576) { "Attempt history is too large" }
        }
        playerSize(file.ledger, file.profile)
        require(file.photos.sumOf { PhotoCodec.validate(it).size.toLong() } <= PhotoLimits.TOTAL_BYTES) { "Too many saved photos" }
    }

    fun playerSize(ledger: RewardLedger, profile: PlayerProfile) {
        require(ParlorJson.encodeToString(ledger).toByteArray(Charsets.UTF_8).size +
            ParlorJson.encodeToString(profile).toByteArray(Charsets.UTF_8).size <= 1_048_576) { "Player records are too large" }
    }
}
