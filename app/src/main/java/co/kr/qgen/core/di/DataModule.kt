package co.kr.qgen.core.di

import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.data.repository.QuestionRepositoryImpl
import co.kr.qgen.core.data.source.remote.QuestionRemoteDataSource
import co.kr.qgen.core.data.source.remote.QuestionRemoteDataSourceImpl
import org.koin.dsl.module

/**
 * Data layer dependency injection module
 */
val dataModule = module {
    // Database
    single {
        androidx.room.Room.databaseBuilder(
            get(),
            co.kr.qgen.core.data.source.local.QGenDatabase::class.java,
            "qgen_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    single { get<co.kr.qgen.core.data.source.local.QGenDatabase>().problemSetDao() }
    single { get<co.kr.qgen.core.data.source.local.QGenDatabase>().problemDao() }

    // Data Sources
    single<QuestionRemoteDataSource> { 
        QuestionRemoteDataSourceImpl(get()) 
    }
    
    // Repositories
    single<QuestionRepository> { 
        QuestionRepositoryImpl(get(), get(), get()) 
    }
}
