package com.example.medicinereminderapp;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface MedicineDao {
    @Query("SELECT * FROM medicine")
    List<Medicine> getAll();

    @Query("SELECT * FROM medicine WHERE active = 1 ORDER BY name COLLATE NOCASE")
    List<Medicine> getActiveMedicines();

    @Insert
    long insert(Medicine medicine);

    @Update
    void update(Medicine medicine);

    @Delete
    void delete(Medicine medicine);

    @Query("SELECT * FROM medicine WHERE id = :id")
    Medicine getById(int id);

    @Query("UPDATE medicine SET totalStock = MAX(0, totalStock - 1) WHERE id = :id")
    void decreaseStock(int id);
}
