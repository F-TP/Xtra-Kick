package com.xtrakick.app.ui.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.xtrakick.app.R
import com.xtrakick.app.model.ui.Stream
import com.xtrakick.app.repository.FollowedLiveStreamsRepository
import com.xtrakick.app.ui.main.MainActivity
import com.xtrakick.app.util.AppConstants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LiveChannelsWidgetEntryPoint {
    fun followedLiveStreamsRepository(): FollowedLiveStreamsRepository
}

class LiveChannelsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return LiveChannelsRemoteViewsFactory(applicationContext)
    }
}

class LiveChannelsRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context,
            LiveChannelsWidgetEntryPoint::class.java,
        )
    }

    private var streams: List<Stream> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Runs on binder background thread: safe to block for fast public API check
        streams = runCatching {
            runBlocking {
                val repo = entryPoint.followedLiveStreamsRepository()
                repo.loadLiveFollowed(forceRefresh = true).items
            }
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() {
        streams = emptyList()
    }

    override fun getCount(): Int = streams.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in streams.indices) return null
        val stream = streams[position]

        val views = RemoteViews(context.packageName, R.layout.widget_live_channel_item)
        views.setTextViewText(R.id.widgetChannelName, stream.channelName ?: stream.channelLogin.orEmpty())

        val category = stream.gameName?.takeIf { it.isNotBlank() }
        if (category != null) {
            views.setTextViewText(R.id.widgetGameName, category)
            views.setViewVisibility(R.id.widgetGameName, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widgetGameName, View.GONE)
        }

        val title = stream.title?.takeIf { it.isNotBlank() }
        if (title != null) {
            views.setTextViewText(R.id.widgetStreamTitle, title)
            views.setViewVisibility(R.id.widgetStreamTitle, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widgetStreamTitle, View.GONE)
        }

        val viewers = stream.viewerCount ?: 0
        views.setTextViewText(R.id.widgetViewerCount, formatViewerCount(viewers))

        val logoUrl = stream.channelLogo ?: stream.profileImageUrl
        if (!logoUrl.isNullOrBlank()) {
            val bitmap = runCatching {
                Glide.with(context)
                    .asBitmap()
                    .load(logoUrl)
                    .transform(CircleCrop())
                    .submit(96, 96)
                    .get(2, TimeUnit.SECONDS)
            }.getOrNull()

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widgetChannelLogo, bitmap)
            } else {
                views.setImageViewResource(R.id.widgetChannelLogo, R.drawable.bg_widget_avatar_placeholder)
            }
        } else {
            views.setImageViewResource(R.id.widgetChannelLogo, R.drawable.bg_widget_avatar_placeholder)
        }

        val fillInIntent = Intent().apply {
            action = MainActivity.INTENT_LIVE_NOTIFICATION
            putExtra(MainActivity.KEY_VIDEO, stream)
            putExtra("stream_channel_login", stream.channelLogin)
            putExtra("channel_name", stream.channelName)
            putExtra("stream_source", stream.source ?: AppConstants.KICK)
        }
        views.setOnClickFillInIntent(R.id.widgetItemContainer, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    private fun formatViewerCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
            else -> count.toString()
        }
    }
}
