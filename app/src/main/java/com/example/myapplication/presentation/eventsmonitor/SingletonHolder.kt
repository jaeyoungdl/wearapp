///*
// * Copyright 2022 Samsung Electronics Co., Ltd. All Rights Reserved.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.samsung.android.eventsmonitor
//
//open class SingletonHolder<out T : Any, in A>(creator: (A) -> T) {
//    private var creator: ((A) -> T)? = creator
//
//    @Volatile
//    private var instance: T? = null
//
//    fun getInstance(arg: A): T {
//        val checkInstance = instance
//        if (checkInstance != null) {
//            return checkInstance
//        }
//
//        return synchronized(this) {
//            val checkInstanceAgain = instance
//            if (checkInstanceAgain != null) {
//                checkInstanceAgain
//            } else {
//                val created = creator!!(arg)
//                instance = created
//                creator = null
//                created
//            }
//        }
//    }
//}