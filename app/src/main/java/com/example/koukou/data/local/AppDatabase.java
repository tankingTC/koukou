package com.example.koukou.data.local;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.koukou.data.local.dao.ConversationDao;
import com.example.koukou.data.local.dao.FriendRequestDao;
import com.example.koukou.data.local.dao.MessageDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.data.local.entity.UserEntity;

@Database(entities = {UserEntity.class, MessageEntity.class, ConversationEntity.class, FriendEntity.class, FriendRequestEntity.class}, version = 7, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `friend_requests` (`requestId` TEXT NOT NULL, `fromUserId` TEXT, `fromNickname` TEXT, `fromAvatar` TEXT, `toUserId` TEXT, `message` TEXT, `status` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`requestId`))");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            if (!hasColumn(database, "conversations", "isMuted")) {
                database.execSQL("ALTER TABLE `conversations` ADD COLUMN `isMuted` INTEGER NOT NULL DEFAULT 0");
            }
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            if (!hasColumn(database, "messages", "clientMessageId")) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `clientMessageId` TEXT");
            }
            if (!hasColumn(database, "messages", "serverMessageId")) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `serverMessageId` TEXT");
            }
            if (!hasColumn(database, "messages", "isRead")) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn(database, "messages", "retryCount")) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn(database, "messages", "serverTimestamp")) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `serverTimestamp` INTEGER NOT NULL DEFAULT 0");
            }
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_clientMessageId` ON `messages` (`clientMessageId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_serverMessageId` ON `messages` (`serverMessageId`)");
        }
    };

    public abstract UserDao userDao();
    public abstract MessageDao messageDao();
    public abstract ConversationDao conversationDao();
    public abstract FriendDao friendDao();
    public abstract FriendRequestDao friendRequestDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "koukou_database")
                            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static boolean hasColumn(SupportSQLiteDatabase database, String table, String column) {
        Cursor cursor = database.query("PRAGMA table_info(`" + table + "`)");
        try {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }
}
