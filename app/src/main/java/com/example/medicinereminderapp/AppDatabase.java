package com.example.medicinereminderapp;

import android.content.Context;
import android.util.Log;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Medicine.class}, version = 4, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static final String TAG = "AppDatabase";
    private static volatile AppDatabase INSTANCE;

    public abstract MedicineDao medicineDao();

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "medicine-db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS medicine (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL DEFAULT '', dosage TEXT NOT NULL DEFAULT '', " +
                        "hour INTEGER NOT NULL DEFAULT 0, minute INTEGER NOT NULL DEFAULT 0)");
            } catch (Exception e) {
                Log.e(TAG, "MIGRATION_1_2 failed", e);
            }
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS medicine_new (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL DEFAULT '', dosage TEXT NOT NULL DEFAULT '', " +
                        "type TEXT NOT NULL DEFAULT 'TABLET', totalStock REAL NOT NULL DEFAULT 0, " +
                        "dailyConsumption REAL NOT NULL DEFAULT 0, hour INTEGER NOT NULL DEFAULT 0, " +
                        "minute INTEGER NOT NULL DEFAULT 0)");
                database.execSQL("INSERT OR IGNORE INTO medicine_new (id, name, dosage, hour, minute) " +
                        "SELECT id, name, dosage, hour, minute FROM medicine");
                database.execSQL("DROP TABLE IF EXISTS medicine");
                database.execSQL("ALTER TABLE medicine_new RENAME TO medicine");
            } catch (Exception e) {
                Log.e(TAG, "MIGRATION_2_3 failed", e);
            }
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE medicine ADD COLUMN times TEXT NOT NULL DEFAULT ''");
            database.execSQL("UPDATE medicine SET times = printf('%02d:%02d', hour, minute) WHERE times = ''");
        }
    };
}
