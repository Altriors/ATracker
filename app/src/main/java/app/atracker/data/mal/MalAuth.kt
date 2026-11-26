package app.atracker.data.mal

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * MyAnimeList OAuth2 (Authorization Code with PKCE). MAL accepts only the "plain" challenge method, so the
 * challenge equals the verifier. The user supplies their own free MAL Client ID (created at
 * myanimelist.net/apiconfig with redirect URI [REDIRECT_URI]); no secret is needed or stored.
 */
object MalAuth {
    const val REDIRECT_URI = "atracker://mal-auth"
    private const val AUTHORIZE = "https://myanimelist.net/v1/oauth2/authorize"
    private const val TOKEN = "https://myanimelist.net/v1/oauth2/token"

    class AuthException(message: String) : Exception(message)

    data class Token(val access: String, val refresh: String, val expiresAtMs: Long)

    fun newVerifier(): String {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        // 43..128 chars from [A-Za-z0-9-._~]
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).take(96)
    }

    fun authorizeUrl(clientId: String, verifier: String, state: String): String =
        AUTHORIZE.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("code_challenge", verifier)
            .addQueryParameter("code_challenge_method", "plain")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .build().toString()

    suspend fun exchange(clientId: String, code: String, verifier: String, client: OkHttpClient = defaultClient): Token =
        tokenRequest(client, FormBody.Builder()
            .add("client_id", clientId).add("grant_type", "authorization_code").add("code", code)
            .add("code_verifier", verifier).add("redirect_uri", REDIRECT_URI).build())

    suspend fun refresh(clientId: String, refreshToken: String, client: OkHttpClient = defaultClient): Token =
        tokenRequest(client, FormBody.Builder()
            .add("client_id", clientId).add("grant_type", "refresh_token").add("refresh_token", refreshToken).build())

    private suspend fun tokenRequest(client: OkHttpClient, body: FormBody): Token = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(TOKEN).post(body).header("User-Agent", "ATracker/0.2 (personal Android app)").build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                throw AuthException("MyAnimeList refused the sign-in (HTTP ${resp.code}${msg?.let { ": $it" } ?: ""}). Check the Client ID and redirect URI.")
            }
            val o = Json.parseToJsonElement(text).jsonObject
            val access = o["access_token"]?.jsonPrimitive?.content ?: throw AuthException("MyAnimeList sent no access token.")
            val refresh = o["refresh_token"]?.jsonPrimitive?.content ?: ""
            val seconds = o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
            Token(access, refresh, System.currentTimeMillis() + seconds * 1000)
        }
    }

    private val defaultClient by lazy { OkHttpClient() }
}

/** Stores the OAuth token encrypted with an Android Keystore key (AES-GCM). Never written in plain text. */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mal_token", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
        )
        return gen.generateKey()
    }

    fun save(t: MalAuth.Token) {
        val plain = "${t.access}\n${t.refresh}\n${t.expiresAtMs}".toByteArray()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(plain)
        prefs.edit()
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    fun load(): MalAuth.Token? = runCatching {
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val data = Base64.decode(prefs.getString("data", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val parts = String(c.doFinal(data)).split('\n')
        MalAuth.Token(parts[0], parts[1], parts[2].toLong())
    }.getOrNull()

    fun clear() { prefs.edit().clear().apply() }

    private companion object { const val ALIAS = "atracker_mal_token" }
}
