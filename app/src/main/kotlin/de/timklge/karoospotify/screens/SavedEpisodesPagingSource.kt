package de.timklge.karoospotify.screens

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.timklge.karoospotify.spotify.WebAPIClient
import de.timklge.karoospotify.spotify.model.TrackObject

class SavedEpisodesPagingSource(val ctx: Context, val webAPIClient: WebAPIClient) : PagingSource<Int, TrackObject>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrackObject> {
        return try {
            val nextPageNumber = params.key ?: 0
            val response = webAPIClient.getSavedEpisodes(nextPageNumber * 50)
            val responseItemCount = response?.items?.size ?: 0

            LoadResult.Page(
                data = response?.items ?: emptyList(),
                prevKey = null,
                nextKey = if (responseItemCount < 50) null else nextPageNumber + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TrackObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}