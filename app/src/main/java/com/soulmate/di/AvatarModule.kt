package com.soulmate.di

import com.soulmate.core.data.avatar.IAvatarDriver
import com.soulmate.data.avatar.OrbAvatarDriver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AvatarModule {

    @Binds
    @Singleton
    abstract fun bindAvatarDriver(
        orbAvatarDriver: OrbAvatarDriver
    ): IAvatarDriver
}
