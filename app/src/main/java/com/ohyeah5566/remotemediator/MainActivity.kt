package com.ohyeah5566.remotemediator

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ohyeah5566.remotemediator.databinding.ActivityMainBinding
import com.ohyeah5566.remotemediator.databinding.ItemViewBinding
import kotlinx.coroutines.flow.collect

class MainActivity : AppCompatActivity() {
    private val viewModel : MainViewModel by viewModels()
    val adapter = ListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.listView.adapter = adapter
        lifecycleScope.launchWhenCreated {
            viewModel.flow.collect {
                adapter.submitData(it)
            }
        }
    }

    class ListAdapter : PagingDataAdapter<Item, ListAdapter.ViewHolder>(Diff()) {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.name.text = getItem(position)?.name
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemViewBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        class ViewHolder(val binding: ItemViewBinding) : RecyclerView.ViewHolder(binding.root)

        class Diff : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem.id == newItem.id
            }
            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem == newItem
            }
        }
    }
}

//Add example for custom implementation of PagingSource + Paging3 Remote Mediator
//https://github.com/android/architecture-components-samples/issues/889

//https://developer.android.com/topic/libraries/architecture/paging/v3-network-db
//https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data#pagingsource
//https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data