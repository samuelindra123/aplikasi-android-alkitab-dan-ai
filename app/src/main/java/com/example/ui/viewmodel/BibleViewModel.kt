package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.BibleRepository
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    object Loading : ReaderUiState
    data class Success(val verses: List<Verse>) : ReaderUiState
    data class ChapterNeededAI(val book: String, val chapter: Int) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

class BibleViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bible_app_prefs", android.content.Context.MODE_PRIVATE)

    var userName by mutableStateOf(prefs.getString("user_name", "") ?: "")
        private set

    var appThemePreference by mutableStateOf(prefs.getString("theme_preference", "system") ?: "system")
        private set

    fun updateUserName(name: String) {
        userName = name
        prefs.edit().putString("user_name", name).apply()
    }

    fun updateThemePreference(preference: String) {
        appThemePreference = preference
        prefs.edit().putString("theme_preference", preference).apply()
    }

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "alkitab_ai_db"
    ).build()

    private val geminiRepository = GeminiRepository()
    private val bibleRepository = BibleRepository(application, db.bibleDao(), geminiRepository)

    // Routing
    var currentScreen by mutableStateOf<Screen>(Screen.Dashboard)
        private set

    // Reader Selection
    var selectedBook by mutableStateOf("Yohanes")
        private set
    var selectedChapter by mutableStateOf(1)
        private set
    var targetVerseScroll by mutableStateOf<Int?>(null)
        private set

    fun clearTargetVerseScroll() {
        targetVerseScroll = null
    }

    suspend fun getChapterVerses(bookName: String, chapter: Int): List<Verse> {
        return bibleRepository.getChapterVerses(bookName, chapter)
    }

    // Reader UI State
    private val _readerUiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val readerUiState: StateFlow<ReaderUiState> = _readerUiState.asStateFlow()

    // Room Observed Data
    val bookmarks: StateFlow<List<BookmarkEntity>> = bibleRepository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = bibleRepository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val highlights: StateFlow<List<HighlightEntity>> = bibleRepository.allHighlights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedBooks: StateFlow<List<String>> = bibleRepository.cachedBooksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Study Chat
    private val _chatMessages = MutableStateFlow<List<Pair<String, String>>>(
        listOf(
            "assistant" to "Halo! Saya adalah Asisten Alkitab AI Anda. Saya siap membantu Anda mendalami Alkitab Terjemahan Baru (TB), memahami makna teologis, menjelaskan latar belakang sejarah kitab, atau merenungkan firman Tuhan hari ini. Apa yang ingin Anda tanyakan atau diskusikan?"
        )
    )
    val chatMessages: StateFlow<List<Pair<String, String>>> = _chatMessages.asStateFlow()

    var isChatLoading by mutableStateOf(false)
        private set

    // AI Verse Explanation State
    var activeVerseExplanation by mutableStateOf<String?>(null)
        private set
    var isExplanationLoading by mutableStateOf(false)
        private set

    // Search Feature
    var searchQuery by mutableStateOf("")
    private val _searchResults = MutableStateFlow<List<VerseRef>>(emptyList())
    val searchResults: StateFlow<List<VerseRef>> = _searchResults.asStateFlow()
    var isSearching by mutableStateOf(false)
        private set

    // Verse of the Day (Ayat Hari Ini)
    var verseOfTheDay by mutableStateOf(
        VerseRef(
            "Yohanes", 3, 16,
            "Karena begitu besar kasih Allah akan dunia ini, sehingga Ia telah mengaruniakan Anak-Nya yang tunggal, supaya setiap orang yang percaya kepada-Nya tidak binasa, melainkan beroleh hidup yang kekal."
        )
    )
        private set

    init {
        // Load initial chapter
        loadChapter(selectedBook, selectedChapter)
        setupRandomVerseOfTheDay()
    }

    fun navigateTo(screen: Screen) {
        currentScreen = screen
        if (screen is Screen.Reader) {
            val sameChapter = selectedBook == screen.initialBook && selectedChapter == screen.initialChapter
            selectedBook = screen.initialBook
            selectedChapter = screen.initialChapter
            if (!sameChapter || screen.initialVerse != null || _readerUiState.value !is ReaderUiState.Success) {
                loadChapter(selectedBook, selectedChapter, screen.initialVerse)
            }
        }
    }

    /**
     * Loads chapter verses: checks local preloaded and cache, triggers ChapterNeededAI if not found.
     */
    fun loadChapter(bookName: String, chapter: Int, targetVerse: Int? = null) {
        selectedBook = bookName
        selectedChapter = chapter
        targetVerseScroll = targetVerse
        _readerUiState.value = ReaderUiState.Loading

        viewModelScope.launch {
            try {
                val verses = bibleRepository.getChapterVerses(bookName, chapter)
                if (verses.isNotEmpty()) {
                    _readerUiState.value = ReaderUiState.Success(verses)
                } else {
                    // Check if the book chapter is valid in Protestant canon
                    val masterInfo = bibleRepository.masterBooks.find { it.name.equals(bookName, ignoreCase = true) }
                    if (masterInfo != null && chapter <= masterInfo.totalChapters) {
                        _readerUiState.value = ReaderUiState.ChapterNeededAI(bookName, chapter)
                    } else {
                        _readerUiState.value = ReaderUiState.Error("Pasal $chapter tidak ditemukan untuk Kitab $bookName.")
                    }
                }
            } catch (e: Exception) {
                _readerUiState.value = ReaderUiState.Error("Gagal memuat ayat: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Downloads chapter via Gemini API and updates Reader state.
     */
    fun downloadChapterViaGemini() {
        _readerUiState.value = ReaderUiState.Loading
        viewModelScope.launch {
            try {
                val verses = bibleRepository.downloadChapterViaAI(selectedBook, selectedChapter)
                if (verses.isNotEmpty()) {
                    _readerUiState.value = ReaderUiState.Success(verses)
                } else {
                    _readerUiState.value = ReaderUiState.Error("Asisten AI gagal menghasilkan teks ayat. Pastikan kunci API Gemini Anda valid.")
                }
            } catch (e: Exception) {
                _readerUiState.value = ReaderUiState.Error("Gagal memuat via AI: ${e.localizedMessage}. Silakan periksa koneksi internet Anda.")
            }
        }
    }

    // Master books info
    fun getMasterBooks() = bibleRepository.masterBooks

    // Check if Gemini API is available
    fun isGeminiAvailable() = geminiRepository.isApiKeyAvailable()

    // --- Bookmarks, Highlights, Notes ---
    fun isBookmarked(book: String, chapter: Int, verseNumber: Int): Flow<Boolean> {
        return bibleRepository.isBookmarkedFlow(book, chapter, verseNumber)
    }

    fun toggleBookmark(book: String, chapter: Int, verseNumber: Int, text: String, isBookmarked: Boolean) {
        viewModelScope.launch {
            bibleRepository.toggleBookmark(book, chapter, verseNumber, text, isBookmarked)
        }
    }

    fun saveHighlight(book: String, chapter: Int, verseNumber: Int, colorHex: String) {
        viewModelScope.launch {
            bibleRepository.saveHighlight(book, chapter, verseNumber, colorHex)
        }
    }

    fun removeHighlight(book: String, chapter: Int, verseNumber: Int) {
        viewModelScope.launch {
            bibleRepository.removeHighlight(book, chapter, verseNumber)
        }
    }

    fun saveNote(book: String, chapter: Int, verseNumber: Int, verseText: String, noteText: String) {
        viewModelScope.launch {
            bibleRepository.saveNote(book, chapter, verseNumber, verseText, noteText)
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            bibleRepository.deleteNote(id)
        }
    }

    // --- Search ---
    fun performSearch(query: String) {
        searchQuery = query
        if (query.trim().length < 3) {
            _searchResults.value = emptyList()
            return
        }

        isSearching = true
        viewModelScope.launch {
            try {
                val results = mutableListOf<VerseRef>()
                
                // 1. Search preloaded offline books dynamically
                bibleRepository.getPreloadedBooks().forEach { book ->
                    book.chapters.forEach { chapter ->
                        chapter.verses.forEach { verse ->
                            if (verse.text.contains(query, ignoreCase = true)) {
                                results.add(VerseRef(book.book, chapter.chapter, verse.number, verse.text))
                            }
                        }
                    }
                }

                _searchResults.value = results
            } catch (e: Exception) {
                Log.e("BibleViewModel", "Search error: ${e.message}")
            } finally {
                isSearching = false
            }
        }
    }

    // --- AI Chat Study Companion ---
    fun sendChatMessage(message: String) {
        if (message.trim().isEmpty()) return

        val currentList = _chatMessages.value.toMutableList()
        currentList.add("user" to message)
        _chatMessages.value = currentList

        isChatLoading = true
        viewModelScope.launch {
            try {
                val history = currentList.subList(1, currentList.size - 1) // omit introductory assistant message and latest user message
                val response = geminiRepository.askBibleAssistant(message, history)
                val newList = _chatMessages.value.toMutableList()
                newList.add("assistant" to response)
                _chatMessages.value = newList
            } catch (e: Exception) {
                val newList = _chatMessages.value.toMutableList()
                newList.add("assistant" to "Maaf, asisten AI mengalami kendala: ${e.localizedMessage}")
                _chatMessages.value = newList
            } finally {
                isChatLoading = false
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            "assistant" to "Halo! Saya adalah Asisten Alkitab AI Anda. Saya siap membantu Anda mendalami Alkitab Terjemahan Baru (TB), memahami makna teologis, menjelaskan latar belakang sejarah kitab, atau merenungkan firman Tuhan hari ini. Apa yang ingin Anda tanyakan atau diskusikan?"
        )
    }

    // --- Verse Explanation ---
    fun explainVerse(book: String, chapter: Int, verseNumber: Int, text: String) {
        activeVerseExplanation = null
        isExplanationLoading = true
        viewModelScope.launch {
            try {
                val explanation = geminiRepository.explainVerse(book, chapter, verseNumber, text)
                activeVerseExplanation = explanation
            } catch (e: Exception) {
                activeVerseExplanation = "Gagal memuat penjelasan AI: ${e.localizedMessage}"
            } finally {
                isExplanationLoading = false
            }
        }
    }

    fun dismissExplanation() {
        activeVerseExplanation = null
        isExplanationLoading = false
    }

    // --- Random Verse Of the Day ---
    private fun setupRandomVerseOfTheDay() {
        val comfortingVerses = listOf(
            VerseRef("Yohanes", 3, 16, "Karena begitu besar kasih Allah akan dunia ini, sehingga Ia telah mengaruniakan Anak-Nya yang tunggal, supaya setiap orang yang percaya kepada-Nya tidak binasa, melainkan beroleh hidup yang kekal."),
            VerseRef("Mazmur", 23, 1, "Mazmur Daud. TUHAN adalah gembalaku, takkan kekurangan aku."),
            VerseRef("Mazmur", 23, 4, "Sekalipun aku berjalan dalam lembah kekelaman, aku tidak takut bahaya, sebab Engkau besertaku; gada-Mu dan tongkat-Mu, itulah yang menghibur aku."),
            VerseRef("Amsal", 3, 5, "Percayalah kepada TUHAN dengan segenap hatimu, dan janganlah bersandar kepada pengertianmu sendiri."),
            VerseRef("Amsal", 3, 6, "Akuilah Dia dalam segala lakumu, maka Ia akan meluruskan jalanmu."),
            VerseRef("Yohanes", 14, 6, "Kata Yesus kepadanya: \"Akulah jalan dan kebenaran dan hidup. Tidak ada seorang pun yang datang kepada Bapa, kalau tidak melalui Aku.\""),
            VerseRef("Yohanes", 14, 27, "Damai sejahtera Kutinggalkan bagimu. Damai sejahtera-Ku Kuberikan kepadamu, dan apa yang Kuberikan tidak seperti yang diberikan oleh dunia kepadamu. Janganlah gelisah dan gentar hatimu."),
            VerseRef("Matius", 6, 33, "But carilah dahulu Kerajaan Allah dan kebenarannya, maka semuanya itu akan ditambahkan kepadamu."),
            VerseRef("Roma", 12, 2, "Janganlah kamu menjadi serupa dengan dunia ini, tetapi berubahlah oleh pembaruan budimu, sehingga kamu dapat membedakan manakah kehendak Allah: apa yang baik, yang berkenan kepada Allah dan yang sempurna."),
            VerseRef("1 Korintus", 13, 13, "Demikianlah tinggal ketiga hal ini, yaitu iman, pengharapan dan kasih, dan yang paling besar di antaranya ialah kasih."),
            VerseRef("Yosua", 1, 9, "Bukankah telah Kuperintahkan kepadamu: kuatkan dan teguhkanlah hatimu? Janganlah kecut dan tawar hati, sebab TUHAN, Allahmu, menyertai engkau, ke mana pun engkau pergi."),
            VerseRef("Filipi", 4, 13, "Segala perkara dapat kutanggung di dalam Dia yang memberi kekuatan kepadaku."),
            VerseRef("Mazmur", 119, 105, "Firman-Mu itu pelita bagi kakiku dan terang bagi jalanku."),
            VerseRef("Yesaya", 40, 31, "tetapi orang-orang yang menanti-nantikan TUHAN mendapat kekuatan baru: mereka seumpama rajawali yang naik terbang dengan kekuatan sayapnya; mereka berlari dan tidak menjadi lesu, mereka berjalan dan tidak menjadi lelah."),
            VerseRef("Yohanes", 16, 33, "Semuanya itu Kukatakan kepadamu, supaya kamu beroleh damai sejahtera dalam Aku. Dalam dunia kamu menderita penganiayaan, tetapi kuatkanlah hatimu, Aku telah mengalahkan dunia.")
        )
        
        // Offset by -3 hours so that the date-based selection changes at exactly 3:00 AM instead of midnight
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.HOUR_OF_DAY, -3)
        val year = calendar.get(java.util.Calendar.YEAR)
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        val seed = (year * 1000) + dayOfYear
        val index = (seed % comfortingVerses.size + comfortingVerses.size) % comfortingVerses.size
        verseOfTheDay = comfortingVerses[index]
    }
}
