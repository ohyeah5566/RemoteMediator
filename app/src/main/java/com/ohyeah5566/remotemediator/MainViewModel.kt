package com.ohyeah5566.remotemediator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn

class MainViewModel(
) : ViewModel() {

    val mediator = MainRepository.Mediator()

    @OptIn(ExperimentalPagingApi::class)
    val flow = Pager(
        PagingConfig(
            //決定一開始要撈出多少資料
            //當param.key=null時 或 refresh時, loadSize會是initialLoadSize
            //通常會大於pageSize, 因此一開始的載入在稍微滾動之下 不會那麼快載入下一頁
            initialLoadSize = 50,
            //剩餘item <= pageSize 時, 觸發PagingSource的load
            pageSize = 20,
            enablePlaceholders = false //TODO placeholder?
        ),
        remoteMediator = mediator
    ) {
        //用room會自動觸發paging source的refresh, 或是說room有監聽db的變化?
        //不過這邊沒用room
        //所以透過添加listener 在有新資料時 通知observe
        val source = MainRepository.Source()
        mediator.addListener {
            //invalidate時
            //pagingFactory會invoke
            source.invalidate()
        }
        source
    }.flow.cachedIn(viewModelScope)
}

