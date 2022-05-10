package com.ohyeah5566.remotemediator

data class Item(
    val id: String = "",
    val name: String = "",
    val desc: String = ""
)


object ItemGenerator {
    fun getRemoteData(startIndex:Int) : List<Item>{
        val list = mutableListOf<Item>()
        for (i in 0..20) {
            list.add(
                Item(
                    "${startIndex+i}",
                    "remote data${startIndex+i}",
                    "desc"
                )
            )
        }
        return list
    }
}

