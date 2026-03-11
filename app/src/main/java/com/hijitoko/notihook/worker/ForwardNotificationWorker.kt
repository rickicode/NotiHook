package com.hijitoko.notihook.worker

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hijitoko.notihook.data.ForwardStatus
import com.hijitoko.notihook.data.GlobalSettingsEntity
import com.hijitoko.notihook.data.HttpMethod
import com.hijitoko.notihook.data.NotificationRepository
import com.hijitoko.notihook.data.PayloadType
import com.hijitoko.notihook.forward.AdditionalValuesCodec
import com.hijitoko.notihook.forward.ForwardPayloadBuilder
import com.hijitoko.notihook.forward.ForwardPayloadInput
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ForwardNotificationWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = NotificationRepository.fromWorker(this)
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val notificationId = inputData.getLong(KEY_NOTIFICATION_ID, -1L)
        if (notificationId <= 0) return Result.failure()

        val record = repository.getNotificationById(notificationId) ?: return Result.failure()
        val appConfig = repository.getAppConfig(record.packageName)
        if (appConfig == null || !appConfig.enabled || !appConfig.forwardEnabled || appConfig.apiUrl.isBlank()) {
            repository.updateForwardResult(record.id, ForwardStatus.SKIPPED, "No app forward config", now())
            return Result.success()
        }
        val global = repository.getGlobalSettingsSync()

        val payload = ForwardPayloadBuilder.buildPayloadMap(
            input = ForwardPayloadInput(
                title = record.title,
                text = record.text,
                bigText = record.bigText,
                subText = record.subText,
                infoText = record.infoText,
                name = record.appName,
                pkg = record.packageName
            ),
            additionalValues = AdditionalValuesCodec.decode(appConfig.additionalValuesJson)
        )

        return try {
            val response = client.newCall(
                buildRequestSpec(
                    url = appConfig.apiUrl,
                    method = appConfig.httpMethod,
                    payloadType = appConfig.payloadType,
                    payload = payload,
                    userAgent = global.userAgent
                ).toRequest()
            ).execute()
            if (response.isSuccessful) {
                repository.updateForwardResult(record.id, ForwardStatus.SUCCESS, "", now())
                Result.success()
            } else {
                val error = "HTTP ${response.code}"
                if (response.code >= 500) {
                    Result.retry()
                } else {
                    repository.updateForwardResult(record.id, ForwardStatus.FAILED, error, now())
                    Result.failure()
                }
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (e: Exception) {
            repository.updateForwardResult(record.id, ForwardStatus.FAILED, e.message ?: "Unknown error", now())
            Result.failure()
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val KEY_NOTIFICATION_ID = "notification_id"

        internal fun resolveUserAgent(userAgent: String): String {
            return userAgent.trim().ifBlank { GlobalSettingsEntity.DEFAULT_USER_AGENT }
        }

        internal fun buildRequestSpec(
            url: String,
            method: String,
            payloadType: String,
            payload: Map<String, String>,
            userAgent: String
        ): RequestSpec {
            val resolvedUserAgent = resolveUserAgent(userAgent)
            return when (method) {
                HttpMethod.GET -> RequestSpec(
                    url = ForwardPayloadBuilder.buildGetUrl(url, payload),
                    method = HttpMethod.GET,
                    body = null,
                    userAgent = resolvedUserAgent,
                    contentType = null
                )

                else -> {
                    if (payloadType == PayloadType.JSON) {
                        RequestSpec(
                            url = url,
                            method = HttpMethod.POST,
                            body = ForwardPayloadBuilder.toJsonBody(payload)
                                .toRequestBody(JSON_MEDIA_TYPE.toMediaType()),
                            userAgent = resolvedUserAgent,
                            contentType = JSON_MEDIA_TYPE
                        )
                    } else {
                        val formBuilder = FormBody.Builder()
                        payload.forEach { (key, value) -> formBuilder.add(key, value) }
                        val formBody = formBuilder.build()
                        RequestSpec(
                            url = url,
                            method = HttpMethod.POST,
                            body = formBody,
                            userAgent = resolvedUserAgent,
                            contentType = formBody.contentType().toString()
                        )
                    }
                }
            }
        }

        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    }
}

internal data class RequestSpec(
    val url: String,
    val method: String,
    val body: okhttp3.RequestBody?,
    val userAgent: String,
    val contentType: String?
) {
    fun toRequest(): Request {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)

        if (!contentType.isNullOrBlank()) {
            requestBuilder.header("Content-Type", contentType)
        }

        return when (method) {
            HttpMethod.GET -> requestBuilder.get().build()
            else -> requestBuilder.post(requireNotNull(body)).build()
        }
    }
}
