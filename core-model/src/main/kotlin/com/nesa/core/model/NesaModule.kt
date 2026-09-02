package com.nesa.core.model

/**
 * Optional capability areas. Stage 1 only ever produces [CORE] activities, but
 * the field exists from the start so later modules can tag their own activities
 * without a schema migration and without the scheduler needing to change.
 */
enum class NesaModule {
    CORE,
    LIFE,
    FITNESS,
    FOCUS,
    LEARNING,
    AI,
    PERSONALIZE
}
