package com.rouddy.twophonesupporter

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Function

fun <T> Observable<T>.flatMapFirstCompletable(mapper: Function<T, out Completable>): Completable {
    return flatMapSingle {
        mapper.apply(it)
            .toSingleDefault(1)
    }
        .firstOrError()
        .ignoreElement()
}
