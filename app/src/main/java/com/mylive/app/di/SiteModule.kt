package com.mylive.app.di

import com.mylive.app.core.site.LiveSite
import com.mylive.app.core.site.bilibili.BiliBiliSite
import com.mylive.app.core.site.douyu.DouyuSite
import com.mylive.app.core.site.douyin.DouyinSite
import com.mylive.app.core.site.huya.HuyaSite
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet

@Module
@InstallIn(SingletonComponent::class)
object SiteModule {

    @Provides
    @ElementsIntoSet
    fun provideBiliBiliSites(bilibiliSite: BiliBiliSite): Set<LiveSite> = setOf(bilibiliSite)

    @Provides
    @ElementsIntoSet
    fun provideDouyuSites(douyuSite: DouyuSite): Set<LiveSite> = setOf(douyuSite)

    @Provides
    @ElementsIntoSet
    fun provideHuyaSites(huyaSite: HuyaSite): Set<LiveSite> = setOf(huyaSite)

    @Provides
    @ElementsIntoSet
    fun provideDouyinSites(douyinSite: DouyinSite): Set<LiveSite> = setOf(douyinSite)
}
