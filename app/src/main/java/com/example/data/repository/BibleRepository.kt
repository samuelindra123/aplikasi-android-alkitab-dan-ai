package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Verse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class MasterBookInfo(
    val name: String,
    val totalChapters: Int,
    val isNewTestament: Boolean
)

class BibleRepository(
    private val context: Context,
    private val bibleDao: BibleDao,
    private val geminiRepository: GeminiRepository
) {
    // Local preloaded books parsed from JSON
    private var preloadedBooks: List<Book> = emptyList()

    // Master list of all 66 Protestant Bible books
    val masterBooks: List<MasterBookInfo> = listOf(
        // Perjanjian Lama (Old Testament)
        MasterBookInfo("Kejadian", 50, false),
        MasterBookInfo("Keluaran", 40, false),
        MasterBookInfo("Imamat", 27, false),
        MasterBookInfo("Bilangan", 36, false),
        MasterBookInfo("Ulangan", 34, false),
        MasterBookInfo("Yosua", 24, false),
        MasterBookInfo("Hakim-hakim", 21, false),
        MasterBookInfo("Rut", 4, false),
        MasterBookInfo("1 Samuel", 31, false),
        MasterBookInfo("2 Samuel", 24, false),
        MasterBookInfo("1 Raja-raja", 22, false),
        MasterBookInfo("2 Raja-raja", 25, false),
        MasterBookInfo("1 Tawarikh", 29, false),
        MasterBookInfo("2 Tawarikh", 36, false),
        MasterBookInfo("Ezra", 10, false),
        MasterBookInfo("Nehemia", 13, false),
        MasterBookInfo("Ester", 10, false),
        MasterBookInfo("Ayub", 42, false),
        MasterBookInfo("Mazmur", 150, false),
        MasterBookInfo("Amsal", 31, false),
        MasterBookInfo("Pengkhotbah", 12, false),
        MasterBookInfo("Kidung Agung", 8, false),
        MasterBookInfo("Yesaya", 66, false),
        MasterBookInfo("Yeremia", 52, false),
        MasterBookInfo("Ratapan", 5, false),
        MasterBookInfo("Yehezkiel", 48, false),
        MasterBookInfo("Daniel", 12, false),
        MasterBookInfo("Hosea", 14, false),
        MasterBookInfo("Yoel", 3, false),
        MasterBookInfo("Amos", 9, false),
        MasterBookInfo("Obaja", 1, false),
        MasterBookInfo("Yunus", 4, false),
        MasterBookInfo("Mikha", 7, false),
        MasterBookInfo("Nahum", 3, false),
        MasterBookInfo("Habakuk", 3, false),
        MasterBookInfo("Zefanya", 3, false),
        MasterBookInfo("Hagai", 2, false),
        MasterBookInfo("Zakharia", 14, false),
        MasterBookInfo("Maleakhi", 4, false),

        // Perjanjian Baru (New Testament)
        MasterBookInfo("Matius", 28, true),
        MasterBookInfo("Markus", 16, true),
        MasterBookInfo("Lukas", 24, true),
        MasterBookInfo("Yohanes", 21, true),
        MasterBookInfo("Kisah Para Rasul", 28, true),
        MasterBookInfo("Roma", 16, true),
        MasterBookInfo("1 Korintus", 16, true),
        MasterBookInfo("2 Korintus", 13, true),
        MasterBookInfo("Galatia", 6, true),
        MasterBookInfo("Efesus", 6, true),
        MasterBookInfo("Filipi", 4, true),
        MasterBookInfo("Kolose", 4, true),
        MasterBookInfo("1 Tesalonika", 5, true),
        MasterBookInfo("2 Tesalonika", 3, true),
        MasterBookInfo("1 Timotius", 6, true),
        MasterBookInfo("2 Timotius", 4, true),
        MasterBookInfo("Titus", 3, true),
        MasterBookInfo("Filemon", 1, true),
        MasterBookInfo("Ibrani", 13, true),
        MasterBookInfo("Yakobus", 5, true),
        MasterBookInfo("1 Petrus", 5, true),
        MasterBookInfo("2 Petrus", 3, true),
        MasterBookInfo("1 Yohanes", 5, true),
        MasterBookInfo("2 Yohanes", 1, true),
        MasterBookInfo("3 Yohanes", 1, true),
        MasterBookInfo("Yudas", 1, true),
        MasterBookInfo("Wahyu", 22, true)
    )

    init {
        loadPreloadedCSV()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val currentField = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString())
                currentField.setLength(0)
            } else {
                currentField.append(c)
            }
            i++
        }
        result.add(currentField.toString())
        return result
    }

    private fun loadPreloadedCSV() {
        try {
            val startTime = System.currentTimeMillis()
            val booksMap = mutableMapOf<Int, MutableMap<Int, MutableList<Verse>>>()
            
            context.assets.open("ayt.csv").bufferedReader().useLines { lines ->
                var isFirst = true
                lines.forEach { line ->
                    if (isFirst) {
                        isFirst = false
                        return@forEach
                    }
                    if (line.trim().isEmpty()) return@forEach
                    val parts = parseCsvLine(line)
                    if (parts.size >= 6) {
                        val bookId = parts[1].toIntOrNull() ?: return@forEach
                        val chapterId = parts[3].toIntOrNull() ?: return@forEach
                        val verseId = parts[4].toIntOrNull() ?: return@forEach
                        var text = parts[5].trim()
                        // Clean XML/HTML formatting tags
                        text = text.replace(Regex("<[^>]*>"), "").trim()
                        
                        val bookMap = booksMap.getOrPut(bookId) { mutableMapOf() }
                        val chapterList = bookMap.getOrPut(chapterId) { mutableListOf() }
                        
                        chapterList.add(Verse(verseId, text))
                    }
                }
            }
            
            val booksList = mutableListOf<Book>()
            booksMap.forEach { (bookId, chaptersMap) ->
                val masterInfo = masterBooks.getOrNull(bookId - 1)
                val bookName = masterInfo?.name ?: "Kitab $bookId"
                val isNT = masterInfo?.isNewTestament ?: (bookId >= 40)
                
                val chaptersList = chaptersMap.map { (chapterId, verses) ->
                    Chapter(chapterId, verses.sortedBy { it.number })
                }.sortedBy { it.chapter }
                
                booksList.add(Book(bookName, isNT, chaptersList))
            }
            
            preloadedBooks = booksList
            val duration = System.currentTimeMillis() - startTime
            Log.d("BibleRepository", "Preloaded Bible loaded from ayt.csv: ${preloadedBooks.size} books in ${duration}ms.")
        } catch (e: Exception) {
            Log.e("BibleRepository", "Failed to load preloaded Bible from CSV, falling back to JSON: ${e.message}", e)
            loadPreloadedJSON()
        }
    }

    private fun loadPreloadedJSON() {
        try {
            val jsonString = context.assets.open("alkitab_tb.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val listType = Types.newParameterizedType(List::class.java, Book::class.java)
            val adapter = moshi.adapter<List<Book>>(listType)
            preloadedBooks = adapter.fromJson(jsonString) ?: emptyList()
            Log.d("BibleRepository", "Preloaded Bible loaded with ${preloadedBooks.size} books from JSON.")
        } catch (e: Exception) {
            Log.e("BibleRepository", "Failed to load preloaded Bible: ${e.message}", e)
        }
    }

    fun getPreloadedBooks(): List<Book> = preloadedBooks

    /**
     * Checks if a book and chapter are available in the local preloaded offline database.
     */
    fun isChapterPreloaded(bookName: String, chapterNumber: Int): Boolean {
        val book = preloadedBooks.find { it.book.equals(bookName, ignoreCase = true) }
        return book?.chapters?.any { it.chapter == chapterNumber } ?: false
    }

    /**
     * Gets chapter verses. It attempts:
     * 1. Offline JSON Asset
     * 2. Local SQLite Room Cache (verses downloaded via AI previously)
     */
    suspend fun getChapterVerses(bookName: String, chapterNumber: Int): List<Verse> = withContext(Dispatchers.IO) {
        // 1. Try preloaded local books first
        val preloadedBook = preloadedBooks.find { it.book.equals(bookName, ignoreCase = true) }
        val preloadedChapter = preloadedBook?.chapters?.find { it.chapter == chapterNumber }
        if (preloadedChapter != null) {
            return@withContext preloadedChapter.verses
        }

        // 2. Try Room cache (verses downloaded via AI previously)
        val cached = bibleDao.getCachedChapter(bookName, chapterNumber)
        if (cached.isNotEmpty()) {
            return@withContext cached.map { Verse(it.verseNumber, it.text) }
        }

        // Return empty list if not found (needs to be fetched from AI)
        return@withContext emptyList()
    }

    /**
     * Fetches chapter from Gemini AI and saves it to local SQLite Cache.
     */
    suspend fun downloadChapterViaAI(bookName: String, chapterNumber: Int): List<Verse> = withContext(Dispatchers.IO) {
        // Check if book is New Testament
        val bookInfo = masterBooks.find { it.name.equals(bookName, ignoreCase = true) }
        val isNewTestament = bookInfo?.isNewTestament ?: true

        // Call Gemini
        val verses = geminiRepository.fetchChapterFromAI(bookName, chapterNumber)

        if (verses.isNotEmpty()) {
            // Map to Entity and insert in Room
            val entities = verses.map {
                CachedVerseEntity(
                    book = bookName,
                    chapter = chapterNumber,
                    verseNumber = it.number,
                    text = it.text,
                    isNewTestament = isNewTestament
                )
            }
            bibleDao.insertCachedVerses(entities)
        }

        return@withContext verses
    }

    // --- DAOs / Local persistence triggers ---
    val allBookmarks: Flow<List<BookmarkEntity>> = bibleDao.getAllBookmarks()
    val allNotes: Flow<List<NoteEntity>> = bibleDao.getAllNotes()
    val allHighlights: Flow<List<HighlightEntity>> = bibleDao.getAllHighlights()
    val cachedBooksFlow: Flow<List<String>> = bibleDao.getCachedBooksFlow()

    fun isBookmarkedFlow(book: String, chapter: Int, verseNumber: Int): Flow<Boolean> {
        return bibleDao.isBookmarkedFlow(book, chapter, verseNumber)
    }

    suspend fun toggleBookmark(book: String, chapter: Int, verseNumber: Int, text: String, isBookmarked: Boolean) {
        withContext(Dispatchers.IO) {
            if (isBookmarked) {
                bibleDao.deleteBookmark(book, chapter, verseNumber)
            } else {
                bibleDao.insertBookmark(
                    BookmarkEntity(
                        book = book,
                        chapter = chapter,
                        verseNumber = verseNumber,
                        text = text
                    )
                )
            }
        }
    }

    suspend fun saveNote(book: String, chapter: Int, verseNumber: Int, verseText: String, noteText: String) {
        withContext(Dispatchers.IO) {
            bibleDao.insertNote(
                NoteEntity(
                    book = book,
                    chapter = chapter,
                    verseNumber = verseNumber,
                    text = verseText,
                    noteText = noteText
                )
            )
        }
    }

    suspend fun deleteNote(id: Int) {
        withContext(Dispatchers.IO) {
            bibleDao.deleteNoteById(id)
        }
    }

    suspend fun saveHighlight(book: String, chapter: Int, verseNumber: Int, colorHex: String) {
        withContext(Dispatchers.IO) {
            bibleDao.insertHighlight(
                HighlightEntity(
                    refId = "${book}_${chapter}_$verseNumber",
                    book = book,
                    chapter = chapter,
                    verseNumber = verseNumber,
                    colorHex = colorHex
                )
            )
        }
    }

    suspend fun removeHighlight(book: String, chapter: Int, verseNumber: Int) {
        withContext(Dispatchers.IO) {
            bibleDao.deleteHighlight("${book}_${chapter}_$verseNumber")
        }
    }
}
