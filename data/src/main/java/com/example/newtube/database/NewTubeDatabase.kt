package com.example.newtube.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.newtube.database.ChannelEntity
import com.example.newtube.database.VideoEntity

@Database(entities = [VideoEntity::class, ChannelEntity::class], version = 1, exportSchema = false)
abstract class NewTubeDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun channelDao(): ChannelDao
}
