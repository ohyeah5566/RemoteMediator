package com.ohyeah5566.remotemediator

import android.util.Log
import androidx.paging.*
import kotlinx.coroutines.delay

class MainRepository {
    companion object {
        //模擬local 一開始就有50筆資料
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
            Log.d("Mediator", "list.size: ${list.size}")

            return when(loadType){
                LoadType.REFRESH -> {
                    //adapter剛被init的時候 會觸發一次load, type==REFRESH
                    //refresh不做事
                    MediatorResult.Success(false)
                }
                LoadType.PREPEND -> {
                    //這次的功能沒有prepend return下面的表示沒資料了
                    MediatorResult.Success(true)
                }
                LoadType.APPEND -> {
                    Log.d("Mediator", "start load remote data")
                    delay(1000) //模擬load data的時間
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

        /**
         * initLoad 跟 資料庫有變動後 都會getRefreshKey
         */
        override fun getRefreshKey(state: PagingState<Int, Item>): Int? {
            //Most recently accessed index in the list, including placeholders.
            //state.anchorPosition 最近一次存取的position or index 不太確定這邊的position指的是什麼,應該不是RecyclerView.Adapter的position 因為一直往下滑有時position反而會變小
            //自己瞎猜可能是跟RecyclerView的recycle機制有關係吧 anchorPosition印出來看就是20-70之間不斷循環的int
            Log.d(TAG, "anchorPosition: ${state.anchorPosition}")

            //可以用 state.closestItemToPosition(anchorPosition) 取得最接近該position的item
            //取得的item的id可以當作key來使用
            return state.anchorPosition?.let {
                Log.d(TAG, "closestItemToPosition.id: ${state.closestItemToPosition(it)?.id?.toInt()}")
                state.closestItemToPosition(it)?.id?.toInt()
            }
        }

        /**
         *  @param params LoadParams 是一個sealed class  有 Refresh,Append,Prepend 三種狀態
         *  在Refresh時 loadSize 會是 initialLoadSize
         *  在Append,Prepend時 loadSize 會是 pageSize
         */
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Item> {
            Log.d(TAG, "param.key:${params.key},  params.loadSize:${params.loadSize}")
            val loadSize = params.loadSize
            var key = params.key ?: 0

            return when(params){
                is LoadParams.Append -> {
                    //Append的情況下 可以直接拿 key位置之後LoadSize的資料量
                    Log.d(TAG, "Append")
                    val newList = mutableListOf<Item>()
                    var noMoreDataInLocal = false
                    val prevKey = if (key >= loadSize) key - loadSize else if (key == 0) null else 0

                    for (i in key until key + loadSize) {
                        val v = list.getOrNull(i)
                        if (v == null) noMoreDataInLocal = true
                        else newList.add(v)
                    }
                    if (noMoreDataInLocal) {
                        //local沒有更多的資料了，nextKey=null，讓RemoteMediator的load觸發
                        Log.d(TAG, "prevKey:$prevKey nextkey:null")
                        LoadResult.Page(newList, prevKey, null)
                    } else {
                        Log.d(TAG, "prevKey:$prevKey nextkey:${key + newList.size}")
                        LoadResult.Page(newList, prevKey, key + newList.size)
                    }
                }
                is LoadParams.Prepend -> {
                    Log.d(TAG, "Prepend")
                    // 如果key 為30的話 第一筆資料會是 xxx 30 往後是29,28...
                    // prevkey 要依序為 10, 0  才能從30以前的資料撈出來顯示
                    // prevKey 若為null 在refresh後 就不會有30以前的資料
                    val prevKey = if (key >= loadSize) key - loadSize else if (key == 0) null else 0
                    val newList = mutableListOf<Item>()

                    for (i in (prevKey ?: 0) until key) {
                        val v = list.getOrNull(i) ?: break
                        newList.add(v)
                    }
                    Log.d(TAG, "prevKey:$prevKey nextkey:$key")
                    LoadResult.Page(newList, prevKey, key)
                }
                is LoadParams.Refresh -> {
                    Log.d(TAG, "Refresh")
                    //資料不能從params.key開始拿,否則在refresh後 畫面上的第一筆資料會是position key, 不是refresh前的畫面
                    //ex原本第一筆資料顯示是item30 , reload-> anchorPosition=35, key=anchorPosition=35 使用者的畫面會跑到item35
                    //如果是從key開始拿資料, 那麼return page後 畫面的第一筆資料會變成item35
                    //                         25    25
                    //所以要從大約 要從 prevKey<----key---->nextKey 拿取這段的資料
                    //               50
                    //如果key=0  key----->nextKey
                    val halfLoadSize = loadSize/2 //從key的位置前後各拿一半
                    val newList = mutableListOf<Item>()
                    var noMoreDataInLocal = false
                    val prevKey = if (key >= halfLoadSize) key - halfLoadSize else if (key == 0) null else 0
                    val startFrom = (key - halfLoadSize).coerceAtLeast(0) //因為init的狀態也是Refresh 所以初始位置可能從0開始

                    for (i in startFrom until key + halfLoadSize) {
                        val v = list.getOrNull(i)
                        if (v == null) noMoreDataInLocal = true
                        else newList.add(v)
                    }
                    if (noMoreDataInLocal) {
                        Log.d(TAG, "prevKey:$prevKey nextkey:null")
                        LoadResult.Page(newList, prevKey, null)
                    } else {
                        Log.d(TAG, "prevKey:$prevKey nextkey:${key + halfLoadSize}")
                        LoadResult.Page(newList, prevKey, key + halfLoadSize)
                    }
                }
            }
        }
    }
}
