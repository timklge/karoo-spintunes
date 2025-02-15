package de.timklge.karoospintunes.screens

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.timklge.karoospintunes.spotify.WebAPIClient
import de.timklge.karoospintunes.spotify.model.PlaylistsItem

class ShowsPagingSource(val ctx: Context, val webAPIClient: WebAPIClient) : PagingSource<Int, PlaylistsItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PlaylistsItem> {
        return try {
            val nextPageNumber = params.key ?: 0
            val response = webAPIClient.getSavedShows(nextPageNumber * WebAPIClient.PAGE_SIZE)
            val responseItemCount = response?.items?.size ?: 0

            LoadResult.Page(
                data = response?.items?.mapNotNull { it.show } ?: emptyList(),
                prevKey = null,
                nextKey = if (responseItemCount < WebAPIClient.PAGE_SIZE) null else nextPageNumber + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, PlaylistsItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}