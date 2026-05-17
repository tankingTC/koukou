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
import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.dao.FriendRequestDao;
import com.example.koukou.data.local.dao.MessageDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.data.local.entity.UserEntity;

@Database(
        entities = {
                UserEntity.class,
                MessageEntity.class,
                ConversationEntity.class,
                FriendEntity.class,
                FriendRequestEntity.class
        },
        version = 8,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_1_8 = createFullMigration(1);
    private static final Migration MIGRATION_2_8 = createFullMigration(2);
    private static final Migration MIGRATION_3_8 = createFullMigration(3);
    private static final Migration MIGRATION_4_8 = createFullMigration(4);
    private static final Migration MIGRATION_5_8 = createFullMigration(5);
    private static final Migration MIGRATION_6_8 = createFullMigration(6);
    private static final Migration MIGRATION_7_8 = createFullMigration(7);

    public abstract UserDao userDao();

    public abstract MessageDao messageDao();

    public abstract ConversationDao conversationDao();

    public abstract FriendDao friendDao();

    public abstract FriendRequestDao friendRequestDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "koukou_database"
                            )
                            .addMigrations(
                                    MIGRATION_1_8,
                                    MIGRATION_2_8,
                                    MIGRATION_3_8,
                                    MIGRATION_4_8,
                                    MIGRATION_5_8,
                                    MIGRATION_6_8,
                                    MIGRATION_7_8
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static Migration createFullMigration(int startVersion) {
        return new Migration(startVersion, 8) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                ensureLatestSchema(database);
            }
        };
    }

    private static void ensureLatestSchema(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `users` (`userId` TEXT NOT NULL, `account` TEXT, `password` TEXT, `nickname` TEXT, `avatarUrl` TEXT, `signature` TEXT, PRIMARY KEY(`userId`))");
        database.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`messageId` TEXT NOT NULL, `clientMessageId` TEXT, `serverMessageId` TEXT, `conversationId` TEXT, `senderId` TEXT, `receiverId` TEXT, `content` TEXT, `msgType` TEXT, `localPath` TEXT, `timestamp` INTEGER NOT NULL DEFAULT 0, `chatType` TEXT, `status` TEXT, `lastErrorCode` TEXT, `lastErrorMessage` TEXT, `isRead` INTEGER NOT NULL DEFAULT 0, `retryCount` INTEGER NOT NULL DEFAULT 0, `serverTimestamp` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`messageId`))");
        database.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`conversationId` TEXT NOT NULL, `ownerId` TEXT, `targetId` TEXT, `targetName` TEXT, `targetAvatarUrl` TEXT, `lastMessage` TEXT, `lastMessageTime` INTEGER NOT NULL DEFAULT 0, `unreadCount` INTEGER NOT NULL DEFAULT 0, `isPinned` INTEGER NOT NULL DEFAULT 0, `isMuted` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`conversationId`))");
        database.execSQL("CREATE TABLE IF NOT EXISTS `friends` (`ownerId` TEXT NOT NULL, `friendId` TEXT NOT NULL, PRIMARY KEY(`ownerId`, `friendId`))");
        database.execSQL("CREATE TABLE IF NOT EXISTS `friend_requests` (`requestId` TEXT NOT NULL, `fromUserId` TEXT, `fromNickname` TEXT, `fromAvatar` TEXT, `toUserId` TEXT, `message` TEXT, `status` TEXT, `createdAt` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`requestId`))");

        ensureColumn(database, "users", "account", "ALTER TABLE `users` ADD COLUMN `account` TEXT");
        ensureColumn(database, "users", "password", "ALTER TABLE `users` ADD COLUMN `password` TEXT");
        ensureColumn(database, "users", "nickname", "ALTER TABLE `users` ADD COLUMN `nickname` TEXT");
        ensureColumn(database, "users", "avatarUrl", "ALTER TABLE `users` ADD COLUMN `avatarUrl` TEXT");
        ensureColumn(database, "users", "signature", "ALTER TABLE `users` ADD COLUMN `signature` TEXT");

        ensureColumn(database, "messages", "clientMessageId", "ALTER TABLE `messages` ADD COLUMN `clientMessageId` TEXT");
        ensureColumn(database, "messages", "serverMessageId", "ALTER TABLE `messages` ADD COLUMN `serverMessageId` TEXT");
        ensureColumn(database, "messages", "conversationId", "ALTER TABLE `messages` ADD COLUMN `conversationId` TEXT");
        ensureColumn(database, "messages", "senderId", "ALTER TABLE `messages` ADD COLUMN `senderId` TEXT");
        ensureColumn(database, "messages", "receiverId", "ALTER TABLE `messages` ADD COLUMN `receiverId` TEXT");
        ensureColumn(database, "messages", "content", "ALTER TABLE `messages` ADD COLUMN `content` TEXT");
        ensureColumn(database, "messages", "msgType", "ALTER TABLE `messages` ADD COLUMN `msgType` TEXT");
        ensureColumn(database, "messages", "localPath", "ALTER TABLE `messages` ADD COLUMN `localPath` TEXT");
        ensureColumn(database, "messages", "timestamp", "ALTER TABLE `messages` ADD COLUMN `timestamp` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "messages", "chatType", "ALTER TABLE `messages` ADD COLUMN `chatType` TEXT");
        ensureColumn(database, "messages", "status", "ALTER TABLE `messages` ADD COLUMN `status` TEXT");
        ensureColumn(database, "messages", "lastErrorCode", "ALTER TABLE `messages` ADD COLUMN `lastErrorCode` TEXT");
        ensureColumn(database, "messages", "lastErrorMessage", "ALTER TABLE `messages` ADD COLUMN `lastErrorMessage` TEXT");
        ensureColumn(database, "messages", "isRead", "ALTER TABLE `messages` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "messages", "retryCount", "ALTER TABLE `messages` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "messages", "serverTimestamp", "ALTER TABLE `messages` ADD COLUMN `serverTimestamp` INTEGER NOT NULL DEFAULT 0");

        ensureColumn(database, "conversations", "ownerId", "ALTER TABLE `conversations` ADD COLUMN `ownerId` TEXT");
        ensureColumn(database, "conversations", "targetId", "ALTER TABLE `conversations` ADD COLUMN `targetId` TEXT");
        ensureColumn(database, "conversations", "targetName", "ALTER TABLE `conversations` ADD COLUMN `targetName` TEXT");
        ensureColumn(database, "conversations", "targetAvatarUrl", "ALTER TABLE `conversations` ADD COLUMN `targetAvatarUrl` TEXT");
        ensureColumn(database, "conversations", "lastMessage", "ALTER TABLE `conversations` ADD COLUMN `lastMessage` TEXT");
        ensureColumn(database, "conversations", "lastMessageTime", "ALTER TABLE `conversations` ADD COLUMN `lastMessageTime` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "conversations", "unreadCount", "ALTER TABLE `conversations` ADD COLUMN `unreadCount` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "conversations", "isPinned", "ALTER TABLE `conversations` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "conversations", "isMuted", "ALTER TABLE `conversations` ADD COLUMN `isMuted` INTEGER NOT NULL DEFAULT 0");

        ensureColumn(database, "friend_requests", "fromUserId", "ALTER TABLE `friend_requests` ADD COLUMN `fromUserId` TEXT");
        ensureColumn(database, "friend_requests", "fromNickname", "ALTER TABLE `friend_requests` ADD COLUMN `fromNickname` TEXT");
        ensureColumn(database, "friend_requests", "fromAvatar", "ALTER TABLE `friend_requests` ADD COLUMN `fromAvatar` TEXT");
        ensureColumn(database, "friend_requests", "toUserId", "ALTER TABLE `friend_requests` ADD COLUMN `toUserId` TEXT");
        ensureColumn(database, "friend_requests", "message", "ALTER TABLE `friend_requests` ADD COLUMN `message` TEXT");
        ensureColumn(database, "friend_requests", "status", "ALTER TABLE `friend_requests` ADD COLUMN `status` TEXT");
        ensureColumn(database, "friend_requests", "createdAt", "ALTER TABLE `friend_requests` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0");
        ensureColumn(database, "friend_requests", "updatedAt", "ALTER TABLE `friend_requests` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0");

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_clientMessageId` ON `messages` (`clientMessageId`)");
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_serverMessageId` ON `messages` (`serverMessageId`)");
    }

    private static void ensureColumn(SupportSQLiteDatabase database, String table, String column, String sql) {
        if (!hasColumn(database, table, column)) {
            database.execSQL(sql);
        }
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
