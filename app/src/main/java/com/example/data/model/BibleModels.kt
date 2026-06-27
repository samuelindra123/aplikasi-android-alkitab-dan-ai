package com.example.data.model

data class Verse(
    val number: Int,
    val text: String
)

data class Chapter(
    val chapter: Int,
    val verses: List<Verse>
)

data class Book(
    val book: String,
    val isNewTestament: Boolean,
    val chapters: List<Chapter>
)

data class VerseRef(
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val text: String
)

sealed interface Screen {
    object Dashboard : Screen
    data class Reader(val initialBook: String = "Yohanes", val initialChapter: Int = 1, val initialVerse: Int? = null) : Screen
    object AIAssistant : Screen
    object NotesAndBookmarks : Screen
    object Settings : Screen
}
