package com.example.documentsummarizer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class DocumentListItem(
    @Embedded
    val doc: DocumentEntity,
    @ColumnInfo(name = "coverUri")
    val coverUri: String?,
    @ColumnInfo(name = "coverThumb")
    val coverThumb: ByteArray?
)

data class DocumentWithImages(
    @Embedded val doc: DocumentEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId", entity = DocumentImageEntity::class)
    val images: List<DocumentImageEntity>
)

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(doc: DocumentEntity): Long
    @Update suspend fun update(doc: DocumentEntity)

    @Query("UPDATE documents SET summaryText=:summary, summaryStatus=:status, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updateSummary(id: Long, summary: String, status: SummaryStatus, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteById(id: Long)

    @Insert suspend fun insertImages(images: List<DocumentImageEntity>)
    @Query("DELETE FROM document_images WHERE documentId = :docId") suspend fun clearImages(docId: Long)

    @Transaction @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDoc(id: Long): Flow<DocumentEntity?>

    @Transaction @Query("SELECT * FROM documents WHERE id = :id")
    fun observeWithImages(id: Long): Flow<DocumentWithImages?>

    @Transaction
    @Query("""
        SELECT d.*,
               (SELECT imageUri  FROM document_images di WHERE di.documentId=d.id ORDER BY di.position ASC LIMIT 1) AS coverUri,
               (SELECT thumbnail FROM document_images di WHERE di.documentId=d.id ORDER BY di.position ASC LIMIT 1) AS coverThumb
          FROM documents d
         ORDER BY d.createdAt DESC
    """)
    fun observeListItems(): Flow<List<DocumentListItem>>

    @Transaction
    @Query("""
        SELECT d.*,
               (SELECT imageUri  FROM document_images di WHERE di.documentId=d.id ORDER BY di.position ASC LIMIT 1) AS coverUri,
               (SELECT thumbnail FROM document_images di WHERE di.documentId=d.id ORDER BY di.position ASC LIMIT 1) AS coverThumb
          FROM documents AS d
          JOIN documents_fts AS f ON d.id = f.rowid
         WHERE documents_fts MATCH :query
         ORDER BY d.createdAt DESC
    """)
    fun searchListItems(query: String): Flow<List<DocumentListItem>>

    @Query("""
        INSERT OR REPLACE INTO documents_fts(rowid, title, sourceText, summaryText)
        SELECT id, title, sourceText, summaryText FROM documents WHERE id = :id
    """)
    suspend fun reindexFtsFor(id: Long)
}