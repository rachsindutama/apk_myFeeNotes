package com.example.data.repository

import com.example.data.db.UserDao
import com.example.data.model.User

class UserRepository(private val userDao: UserDao) {
    suspend fun getUserByEmail(email: String): User? =
        userDao.getUserByEmail(email)

    suspend fun insertUser(user: User) =
        userDao.insertUser(user)

    suspend fun updateSyncTime(email: String, syncTime: Long) =
        userDao.updateSyncTime(email, syncTime)
}
