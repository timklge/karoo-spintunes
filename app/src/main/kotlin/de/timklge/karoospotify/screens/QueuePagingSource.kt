package de.timklge.karoospotify.screens

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.timklge.karoospotify.spotify.APIClient
import de.timklge.karoospotify.spotify.LocalClient
import de.timklge.karoospotify.spotify.WebAPIClient
import de.timklge.karoospotify.spotify.model.ITrackObject
import de.timklge.karoospotify.spotify.model.Item
import de.timklge.karoospotify.spotify.model.ItemWrapper

class QueuePagingSource(
    val ctx: Context, val apiClient: APIClient, val webAPIClient: WebAPIClient
): PagingSource<Int, ITrackObject>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ITrackObject> {
        return try {
            // Queue list is not provided with the local client
            val response = webAPIClient.getPlayerQueue(ctx)
            val currentlyPlaying = if (apiClient is WebAPIClient){
                listOfNotNull(response?.currentlyPlaying).map { ItemWrapper(it) }
            } else if (apiClient is LocalClient){
                val item = apiClient.getPlayerState()?.track?.let { Item.fromSpotifyTrack(it) }

                if (item != null){
                    listOf(ItemWrapper(item))
                } else {
                    emptyList()
                }
            } else {
                error("Unknown API client")
            }
            val queue = currentlyPlaying + (response?.queue?.map { ItemWrapper(it) } ?: emptyList())

            LoadResult.Page(
                data = queue,
                prevKey = null,
                nextKey = null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ITrackObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}