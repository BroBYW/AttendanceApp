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

    @POST("/api/gps-logs/classify")
    suspend fun classifyGpsLogs(
        @Body logs: List<GpsLogClassifyRequest>
    ): Response<ApiResponse<List<GpsLogClassifyResponse>>>

    @GET("/api/admin/users/me")
    suspend fun getMyProfile(): Response<ApiResponse<UserResponse>>

    @GET("/api/attendance/my")
    suspend fun getMyAttendance(): Response<ApiResponse<Any>>

    @GET("/api/admin/office-areas/{id}/geojson")
    suspend fun getGeoJson(@Path("id") id: Long): Response<String>

    @POST("/api/admin/office-areas/check-location")
    suspend fun checkLocation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("officeAreaId") officeAreaId: Long
    ): Response<ApiResponse<Map<String, Any>>>
}
