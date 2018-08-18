package com.droidfeed.data.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import android.databinding.ObservableBoolean
import com.droidfeed.data.db.AppDatabase
import com.droidfeed.ui.adapter.diff.Diffable

@Entity(tableName = AppDatabase.SOURCE_TABLE_NAME)
data class Source(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Int,

    @ColumnInfo(name = "name")
    var name: String,

    @ColumnInfo(name = "url")
    var url: String

) : Diffable {

    @ColumnInfo(name = "is_active")
    var isActive: Boolean = true
        set(value) {
            isEnabled.set(value)
            field = value
        }

    @Ignore
    val isEnabled = ObservableBoolean()

    override fun isSame(item: Diffable): Boolean = (item as Source).url.contentEquals(url)

    override fun isContentSame(item: Diffable): Boolean {
        return false
    }
}