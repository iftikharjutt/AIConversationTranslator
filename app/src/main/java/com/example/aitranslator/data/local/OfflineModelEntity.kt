package com.example.aitranslator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aitranslator.domain.model.OfflineModel
import com.example.aitranslator.domain.model.OfflineModelStatus

@Entity(tableName = "offline_models")
data class OfflineModelEntity(
    @PrimaryKey
    val modelId: String,
    val modelName: String,
    val version: String,
    val localPath: String,
    val status: OfflineModelStatus,
    val totalSize: Long,
    val downloadedSize: Long,
    val sha256: String,
    val supportedLanguages: String, // Comma-separated list e.g. "msa_Latn,zsm_Latn,urd_Arab"
    val license: String,
    val sourceUrl: String,
    val runtime: String,
    val installedAt: Long,
    val lastVerifiedAt: Long
) {
    fun toDomain(): OfflineModel = OfflineModel(
        modelId = modelId,
        modelName = modelName,
        version = version,
        localPath = localPath,
        status = status,
        totalSize = totalSize,
        downloadedSize = downloadedSize,
        sha256 = sha256,
        supportedLanguages = supportedLanguages.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        license = license,
        sourceUrl = sourceUrl,
        runtime = runtime,
        installedAt = installedAt,
        lastVerifiedAt = lastVerifiedAt
    )

    companion object {
        fun fromDomain(model: OfflineModel): OfflineModelEntity = OfflineModelEntity(
            modelId = model.modelId,
            modelName = model.modelName,
            version = model.version,
            localPath = model.localPath,
            status = model.status,
            totalSize = model.totalSize,
            downloadedSize = model.downloadedSize,
            sha256 = model.sha256,
            supportedLanguages = model.supportedLanguages.joinToString(","),
            license = model.license,
            sourceUrl = model.sourceUrl,
            runtime = model.runtime,
            installedAt = model.installedAt,
            lastVerifiedAt = model.lastVerifiedAt
        )
    }
}
