package com.example.twitterapptest

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
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


class MainActivity : ComponentActivity() {
    companion object {
        const val CALLBACK_URL = "app://"
        const val SCOPE = "offline.access tweet.read users.read bookmark.read"
        const val SECRET_STATE = "state"
        const val TWITTER_OAUTH2_CLIENT_ID = "Developer Portalで取得したCLIENT ID"
        const val TWITTER_OAUTH2_CLIENT_SECRET = "Developer Portalで取得したCLIENT SECRET ID"
    }

    private lateinit var credentials: TwitterCredentialsOAuth2
    private lateinit var service: TwitterOAuth20Service
    private lateinit var pkce: PKCE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("TwitterOauth", MODE_PRIVATE)

        credentials = TwitterCredentialsOAuth2(
            TWITTER_OAUTH2_CLIENT_ID,
            TWITTER_OAUTH2_CLIENT_SECRET,
            prefs.getString("OauthToken", ""),
            prefs.getString("OauthRefreshToken", "")
        ).apply {
            isOAUth2AutoRefreshToken = true // トークンの自動更新有効化
        }

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
        val code = uri!!.getQueryParameter("code")

        lifecycleScope.launch(Dispatchers.IO) {
            code?.let {
                getAccessToken(it)
            }
        }

    }

    /**
     * アクセストークンの取得
     */
    private fun getAccessToken(code: String?) {

        val accessToken = service.getAccessToken(pkce, code)

        storeToken(accessToken)

        Log.i("onNewIntent", credentials.twitterOauth2AccessToken)
        Log.i("onNewIntent", credentials.twitterOauth2RefreshToken)
    }

    /**
     * アクセストークンの保管
     */
    private fun storeToken(accessToken: OAuth2AccessToken) {

        val prefs = getSharedPreferences("TwitterOauth", MODE_PRIVATE)
        prefs.edit().apply {
            putString("OauthToken", accessToken.accessToken)
            putString("OauthRefreshToken", accessToken.refreshToken)
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
    private fun getBookmark() {
        Log.i("getBookMark", credentials.twitterOauth2AccessToken)
        Log.i("getBookMark", credentials.twitterOauth2RefreshToken)

        val apiInstance = TwitterApi(credentials)

        runCatching {
            apiInstance.bookmarks().getUsersIdBookmarks("2942060174").execute()
        }.onSuccess {
            Log.i("BookMark_Success", "$it")
        }.onFailure {
            // 再認証が必要なダイアログなどを出す
            Log.i("BookMark_Failure_local", it.localizedMessage)
            Log.i("BookMark_Failure_message", "${it.message}")
            Log.i("BookMark_Failure_cause", "${it.cause}")
        }
    }


    /**
     * ログアウト処理
     * （取得したトークンを失効させる処理）
     */
    private fun revokeToken() {

        service.revokeToken(credentials.twitterOauth2AccessToken, TokenTypeHint.ACCESS_TOKEN)

        val prefs = getSharedPreferences("TwitterOauth", MODE_PRIVATE)
        prefs.edit().apply {
            putString("OauthToken", "")
            putString("OauthRefreshToken", "")
        }.apply()

        credentials.apply {
            twitterOauth2AccessToken = ""
            twitterOauth2RefreshToken = ""
        }

    }
}
