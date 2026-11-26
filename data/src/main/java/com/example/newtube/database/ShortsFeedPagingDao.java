package com.example.newtube.database;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Query;

/**
 * Room reads Java DAO methods directly. PagingSource is a Kotlin
 * multiplatform `expect` class, which kapt's Kotlin-to-Java stub generator
 * maps to `error.NonExistentClass`; declaring this one query in Java keeps
 * Room's processor from seeing that broken stub.
 */
@Dao
public interface ShortsFeedPagingDao {
    @Query("SELECT * FROM shorts_feed ORDER BY position ASC")
    PagingSource<Integer, ShortsFeedEntity> pagingSource();
}
