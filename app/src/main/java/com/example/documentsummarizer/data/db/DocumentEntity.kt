package com.example.documentsummarizer.data.db

import androidx.room.*
import java.time.Instant

enum class SummaryStatus { PENDING, READY, ERROR }

@Entity(
    tableName = "documents",
    indices = [Index(value = ["createdAt"]), Index(value = ["title"])]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val sourceText: String,
    val summaryText: String = "",
    val summaryStatus: SummaryStatus = SummaryStatus.PENDING, // <— NEW
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)

@Fts4(contentEntity = DocumentEntity::class, tokenizer = FtsOptions.TOKENIZER_PORTER)
@Entity(tableName = "documents_fts")
data class DocumentFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val sourceText: String,
    val summaryText: String
)

@Entity(
    tableName = "document_images",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId"), Index(value = ["documentId","position"], unique = true)]
)
data class DocumentImageEntity(
    @PrimaryKey(autoGenerate = true) val imageId: Long = 0L,
    val documentId: Long,
    val position: Int,
    val imageUri: String,
    val thumbnail: ByteArray? = null
)