package com.example.attendanceapp.data.network

import com.example.attendanceapp.data.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @Multipart
    @POST("/api/attendance/clock-in")
    suspend fun clockIn(
        @Part("data") data: RequestBody,
        @Part selfie: MultipartBody.Part?,
        @Part document: MultipartBody.Part?
    ): Response<ApiResponse<AttendanceResponse>>

    @Multipart
    @POST("/api/attendance/clock-out")
    suspend fun clockOut(
        @Part("data") data: RequestBody,
        @Part selfie: MultipartBody.Part?
    ): Response<ApiResponse<AttendanceResponse>>

    @POST("/api/gps-logs/batch")
    suspend fun submitGpsLogs(@Body logs: List<GpsLogDto>): Response<ApiResponse<String>>

    @GET("/api/attendance/my")
    suspend fun getMyAttendance(): Response<ApiResponse<Any>>
}
