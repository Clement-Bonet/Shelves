package com.katachilabs.shelves.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Un regroupement de livres créé par l'utilisateur (« étagère »). */
@Serializable
@Entity(tableName = "shelves")
data class Shelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Une série (ou collection) de volumes, par exemple les tomes d'un manga.
 * L'identifiant vaut `bnf:<n° de la notice d'ensemble>` quand la BnF la connaît,
 * sinon `t:<titre>|<éditeur>` normalisés.
 */
@Serializable
@Entity(tableName = "series")
data class Series(
    @PrimaryKey val id: String,
    val title: String,
    val publisher: String? = null,
    val bnfRecordId: String? = null,
    val lastSyncedAt: Long? = null,
)

/**
 * Un livre connu de l'application : possédé, ou simplement connu comme volume d'une série.
 * L'ISBN est absent pour certains volumes anciens récupérés à la BnF.
 */
@Serializable
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["isbn"], unique = true),
        Index(value = ["bnfArk"], unique = true),
        Index(value = ["seriesId"]),
    ],
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isbn: String? = null,
    val title: String,
    val subtitle: String? = null,
    val authors: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val summary: String? = null,
    val coverUrl: String? = null,
    val seriesId: String? = null,
    val volumeNumber: Double? = null,
    val volumeLabel: String? = null,
    val bnfArk: String? = null,
    val sources: String? = null,
    val owned: Boolean = false,
    val loanedTo: String? = null,
    val loanedContactUri: String? = null,
    val loanedAt: Long? = null,
    val notes: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "shelf_books",
    primaryKeys = ["shelfId", "bookId"],
    foreignKeys = [
        ForeignKey(entity = Shelf::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["bookId"])],
)
data class ShelfBook(
    val shelfId: Long,
    val bookId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

data class ShelfWithCount(
    @Embedded val shelf: Shelf,
    val bookCount: Int,
)

/** Avancement d'une série : volumes possédés sur volumes connus. */
data class SeriesStats(
    val seriesId: String,
    val total: Int,
    val owned: Int,
)
