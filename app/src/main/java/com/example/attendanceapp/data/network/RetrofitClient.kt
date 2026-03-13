package com.example.attendanceapp.data.network

import android.content.Context
import com.example.attendanceapp.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Depending on emulator and server config. If testing locally:
    // "http://10.0.2.2:3060/" for Android emulator connecting to localhost.
    // Replace if deploying to a physical device on local wifi with your IPv4 address (e.g. "http://192.168.1.100:3060/").
    private const val BASE_URL = "https://attendance2.tecyla.top/"

    private fun getOkHttpClient(context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            
            val sessionManager = SessionManager(context)
            sessionManager.fetchAuthToken()?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }

            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    return Dns.SYSTEM.lookup(hostname).sortedBy { address ->
                        if (address is Inet4Address) 0 else 1
                    }
                }
            })
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getApiService(context: Context): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getOkHttpClient(context))
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        return retrofit.create(ApiService::class.java)
    }
}
