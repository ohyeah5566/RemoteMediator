package com.ohyeah5566.remotemediator

import android.util.Log
import androidx.paging.*
import kotlinx.coroutines.delay
import java.net.URL
import kotlin.math.min

class MainRepository {
    companion object {
        //模擬local 有50筆資料
        val list = mutableListOf<Item>().apply {
            for (i in 0..50) {
                add(
                    Item(
                        "$i",
                        "local data $i",
                        "desc"
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    class Mediator : RemoteMediator<Int, Item>() {
        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, Item>
        ): MediatorResult {

            Log.d("Mediator", "type: ${loadType.name}")
            Log.d("Mediator", "key: ${list.size}")

            return when(loadType){
                LoadType.REFRESH -> {
                    //adapter剛被init的時候 會觸發一次load, type==REFRESH
                    //refresh不做事
                    //如果local端沒資料, 之後就不會不斷的觸發load, tpye==append
                    MediatorResult.Success(false)
                }
                LoadType.PREPEND -> {
                    //這次的功能沒有prepend return下面的表示沒資料了
                    MediatorResult.Success(true)
                }
                LoadType.APPEND -> {
                    delay(3000) //模擬load data的時間
                    //產生n筆資料, 並加入至local data
                    list.addAll(ItemGenerator.getRemoteData(list.size))
                    //手動觸發local data已更新的訊息
                    notifyListeners()
                    MediatorResult.Success(false)
                }
            }
        }

        //用room會自動觸發paging source的refresh
        //不過這邊沒用room
        //所以透過添加listener 有新資料時 通知observe
        private var listeners = arrayListOf<() -> Unit>()
        fun addListener(listener: () -> Unit) {
            listeners.add(listener)
        }

        private fun removeListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        //因為source invalidate時會再次addListener，所以這邊要移除
        private fun notifyListeners() {
            ArrayList(listeners).forEach {
                it()
                removeListener(it)
            }
        }
    }


    class Source(
    ) : PagingSource<Int, Item>() {
        val TAG = "Source"
        override fun getRefreshKey(state: PagingState<Int, Item>): Int? {
            Log.d(TAG, "anchorPosition: ${state.anchorPosition}")
            //單純return anchorPosition 似乎會造成抖動的問題
            //原本在item0, loadMore之後位置會跑到item5
            return state.anchorPosition
        }

        //TODO RRRRRR
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Item> {
            Log.d(TAG, "param.key:${params.key},  params.loadSize:${params.loadSize}")
            val loadSize = params.loadSize
            var nextKey = params.key ?: 0
            val prevKey =
                if (nextKey >= loadSize) nextKey - loadSize else if (nextKey == 0) null else 0
            return try {
                val newList = mutableListOf<Item>()
                for (i in nextKey until nextKey + loadSize) {
                    val v = list.getOrNull(i) ?: break
                    newList.add(v)
                }
                Log.d(TAG, "prevKey:${prevKey} nextkey:${nextKey + newList.size}")
                if(newList.size<loadSize)
                    LoadResult.Page(newList, prevKey, null)
                else
                    LoadResult.Page(newList, prevKey, nextKey + newList.size)
            } catch (ex: IndexOutOfBoundsException) {
                Log.d(TAG, "nextkey:null")
                LoadResult.Page(emptyList(), null, null)
            }
        }
    }
}


class SourceWithoutRemote(
) : PagingSource<Int, Item>() {
    val TAG = "SourceWithoutRemote"
    override fun getRefreshKey(state: PagingState<Int, Item>): Int? {
        return state.anchorPosition
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Item> {
        Log.d(TAG, "load param.key:${params.key}")
        var nk = params.key ?: 0
        // 因為refreshkey 如果設為30的話 第一筆資料會是 xxx 30
        // prevkey 要依序為 10, 0  才能從30以前的資料撈出來顯示
        // prevKey 若為null 在refresh後 就不會有30以前的資料
        val prevkey = if (nk >= 20) nk - 20 else if (nk == 0) null else 0
        val newList = mutableListOf<Item>()
        for (i in 0..20) {
            newList.add(
                Item(
                    "${nk++}",
                    "remote data${nk}",
                    "desc"
                )
            )
        }

        return LoadResult.Page(newList, prevkey, nk)
    }

}