package com.example.koukou.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.koukou.data.local.dao.ConversationDao;
import com.example.koukou.data.local.dao.MessageDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.data.local.entity.UserEntity;

@Database(entities = {UserEntity.class, MessageEntity.class, ConversationEntity.class, FriendEntity.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();
    public abstract MessageDao messageDao();
    public abstract ConversationDao conversationDao();
    public abstract FriendDao friendDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "koukou_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}