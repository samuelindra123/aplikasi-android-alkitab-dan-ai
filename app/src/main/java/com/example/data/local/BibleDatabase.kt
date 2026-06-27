package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val text: String,
    val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val refId: String, // format: "Book_Chapter_Verse"
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val colorHex: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_verses", primaryKeys = ["book", "chapter", "verseNumber"])
data class CachedVerseEntity(
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val text: String,
    val isNewTestament: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BibleDao {
    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE book = :book AND chapter = :chapter AND verseNumber = :verseNumber")
    suspend fun deleteBookmark(book: String, chapter: Int, verseNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE book = :book AND chapter = :chapter AND verseNumber = :verseNumber LIMIT 1)")
    fun isBookmarkedFlow(book: String, chapter: Int, verseNumber: Int): Flow<Boolean>

    // Notes
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)

    @Query("SELECT * FROM notes WHERE book = :book AND chapter = :chapter AND verseNumber = :verseNumber")
    fun getNotesForVerse(book: String, chapter: Int, verseNumber: Int): Flow<List<NoteEntity>>

    // Highlights
    @Query("SELECT * FROM highlights")
    fun getAllHighlights(): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE refId = :refId")
    suspend fun deleteHighlight(refId: String)

    // Cached Verses (Gemini Infinite Bible Cache)
    @Query("SELECT * FROM cached_verses WHERE book = :book AND chapter = :chapter ORDER BY verseNumber ASC")
    suspend fun getCachedChapter(book: String, chapter: Int): List<CachedVerseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedVerses(verses: List<CachedVerseEntity>)

    @Query("SELECT DISTINCT book FROM cached_verses")
    fun getCachedBooksFlow(): Flow<List<String>>
}

@Database(
    entities = [BookmarkEntity::class, NoteEntity::class, HighlightEntity::class, CachedVerseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
}
