package com.example.twitterapptest

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.twitterapptest.ui.theme.TwitterAppTestTheme
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.pkce.PKCE
import com.github.scribejava.core.pkce.PKCECodeChallengeMethod
import com.github.scribejava.core.revoke.TokenTypeHint
import com.twitter.clientlib.TwitterCredentialsOAuth2
import com.twitter.clientlib.api.TwitterApi
import com.twitter.clientlib.auth.TwitterOAuth20Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    companion object {
        const val CALLBACK_URL = "app://"
        const val SCOPE = "offline.access tweet.read users.read bookmark.read"
        const val SECRET_STATE = "state"
        const val TWITTER_OAUTH2_CLIENT_ID = "Developer Portalで取得したCLIENT ID"
        const val TWITTER_OAUTH2_CLIENT_SECRET = "Developer Portalで取得したCLIENT SECRET ID"
        // 1時間半でトークンを更新(トークンの有効期限は2時間だが余裕を見るため)
        const val EXPIRY_TIME = 5400000
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var credentials: TwitterCredentialsOAuth2
    private lateinit var service: TwitterOAuth20Service
    private lateinit var pkce: PKCE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("TwitterOauth", MODE_PRIVATE)

        credentials = TwitterCredentialsOAuth2(
            TWITTER_OAUTH2_CLIENT_ID,
            TWITTER_OAUTH2_CLIENT_SECRET,
            prefs.getString("OauthToken", ""),
            prefs.getString("OauthRefreshToken", "")
        )

        service = TwitterOAuth20Service(
            credentials.twitterOauth2ClientId,
            credentials.twitterOAuth2ClientSecret,
            CALLBACK_URL,
            SCOPE
        )

        pkce = PKCE().apply {
            codeChallenge = "challenge"
            codeChallengeMethod = PKCECodeChallengeMethod.PLAIN
            codeVerifier = "challenge"
        }

        setContent {
            TwitterAppTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val scope = rememberCoroutineScope()
                    Column() {

                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    openOAuthURL()
                                }
                            }
                        ) {
                            Text(text = "認証")
                        }

                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    revokeToken()
                                }
                            }
                        ) {
                            Text(text = "ログアウト")
                        }

                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    refreshToken()
                                }
                            }
                        ) {
                            Text(text = "トークンの更新")
                        }
                        
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    getBookmark()
                                }
                            }
                        ) {
                            Text(text = "ブックマーク取得")
                        }
                    }
                }
            }
        }
    }

    /**
     * コールバックによって得られた認可コードを取得
     * アクセストークンを取得し格納
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Log.i("onNewIntent", "onNewIntent")

        val uri = intent?.data
        val code = uri?.getQueryParameter("code")

        // 認可コードが正常に取得できていたらトークンを取得
        lifecycleScope.launch(Dispatchers.IO) {
            if(code != null) {
                getAccessToken(code)
            }
        }

    }

    /**
     * 初回起動時、バックグラウンドから復帰時に
     * 現在時間とトークン取得時間を比較し
     * 有効期限が切れていたらトークンを更新する
     */
    override fun onResume() {
        super.onResume()

        val getTokenTime = prefs.getLong("getTokenTime", 0)
        val now = System.currentTimeMillis()
        val elapsedTime = now - getTokenTime

        if (elapsedTime > EXPIRY_TIME) {
            lifecycleScope.launch(Dispatchers.IO) {
                refreshToken()
            }
        }
    }

    /**
     * アクセストークンの取得
     */
    private suspend fun getAccessToken(code: String) {

        val accessToken = service.getAccessToken(pkce, code)
        val getTokenTime = System.currentTimeMillis()

        storeToken(accessToken, getTokenTime)

        withContext(Dispatchers.Main) {
            val toast = Toast.makeText(applicationContext, "認証完了", Toast.LENGTH_SHORT)
            toast.show()
        }

        Log.i("1refreshToken", credentials.twitterOauth2AccessToken)
        Log.i("2refreshToken", credentials.twitterOauth2RefreshToken)
    }

    /**
     * アクセストークンの保管
     */
    private fun storeToken(accessToken: OAuth2AccessToken, getTokenTime: Long) {

//        val prefs = getSharedPreferences("TwitterOauth", MODE_PRIVATE)
        prefs.edit().apply {
            putString("OauthToken", accessToken.accessToken)
            putString("OauthRefreshToken", accessToken.refreshToken)
            putLong("getTokenTime", getTokenTime)
        }.apply()

        credentials.apply {
            twitterOauth2AccessToken = accessToken.accessToken
            twitterOauth2RefreshToken = accessToken.refreshToken
        }
    }

    /**
     * 認証処理
     * デフォルトブラウザを使って認証ページへ遷移
     */
    private fun openOAuthURL() {

        val authUrl = service.getAuthorizationUrl(pkce, SECRET_STATE)

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(authUrl)
        )
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
        val twitterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/"))

        val existsDefBrows =
            packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)

        // 端末にツイッターアプリが入っているか確認
        val existsTwitterApp =
            packageManager.resolveActivity(twitterIntent, PackageManager.MATCH_DEFAULT_ONLY)

        runCatching {
            if (existsTwitterApp != null) {
                // ブラウザアプリがインストールされていたら
                if (existsDefBrows != null) {

                    intent.setPackage(existsDefBrows.activityInfo.packageName)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                    startActivity(intent)
                }
            } else {
                startActivity(intent)
            }
        }.getOrElse {
            Log.i("openOAuthURL_Error", "${it.localizedMessage}")
        }

    }

    /**
     * ブックマークの取得
     */
    private suspend fun getBookmark() {

        val apiInstance = TwitterApi(credentials)
        Log.i("getBookMark", credentials.twitterOauth2AccessToken)
        Log.i("getBookMark", credentials.twitterOauth2RefreshToken)

        runCatching {
            apiInstance.bookmarks().getUsersIdBookmarks("認証したユーザーのIDを入力").execute()
        }.onSuccess {
            withContext(Dispatchers.Main) {
                val toast = Toast.makeText(applicationContext, "ブックマーク取得成功！", Toast.LENGTH_SHORT)
                toast.show()
            }
            Log.i("BookMark_Success", "Success")
        }.onFailure {
            withContext(Dispatchers.Main) {
                val toast = Toast.makeText(applicationContext, "ブックマーク取得失敗", Toast.LENGTH_SHORT)
                toast.show()
            }
            Log.i("BookMark_Failure_local", it.localizedMessage)
            Log.i("BookMark_Failure_message", "${it.message}")
            Log.i("BookMark_Failure_cause", "${it.cause}")
        }
    }

    /**
     * ログアウト処理
     * （取得したトークンを失効させる処理）
     */
    private suspend fun revokeToken() {

        service.revokeToken(credentials.twitterOauth2AccessToken, TokenTypeHint.ACCESS_TOKEN)


        prefs.edit().apply {
            putString("OauthToken", "")
            putString("OauthRefreshToken", "")
        }.apply()

        credentials.apply {
            twitterOauth2AccessToken = ""
            twitterOauth2RefreshToken = ""
        }

        withContext(Dispatchers.Main) {
            val toast = Toast.makeText(applicationContext, "ログアウト", Toast.LENGTH_SHORT)
            toast.show()
        }

    }

    /**
     * トークンの更新
     */
    private suspend fun refreshToken() {

        val apiInstance = TwitterApi(credentials)

        apiInstance.addCallback {
            credentials.apply {
                twitterOauth2AccessToken = it.accessToken
                twitterOauth2RefreshToken = it.refreshToken
            }

            val refreshTokenTime = System.currentTimeMillis()

            prefs.edit().apply {
                putString("OauthToken", it.accessToken)
                putString("OauthRefreshToken", it.refreshToken)
                putLong("getTokenTime", refreshTokenTime)
            }.apply()

        }

        runCatching {
            apiInstance.refreshToken()
            withContext(Dispatchers.Main) {
                val toast = Toast.makeText(applicationContext, "トークン更新成功！", Toast.LENGTH_SHORT)
                toast.show()
            }
        }.getOrElse {
            withContext(Dispatchers.Main) {
                val toast = Toast.makeText(applicationContext, "トークン更新失敗", Toast.LENGTH_SHORT)
                toast.show()
            }
            Log.i("refreshToken_Fail", "$it")
        }
    }
}
